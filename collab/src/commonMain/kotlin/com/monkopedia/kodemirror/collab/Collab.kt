/*
 * Copyright 2026 Jason Monk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Originally based on CodeMirror 6 by Marijn Haverbeke, licensed under MIT.
 * See NOTICE file for details.
 */
package com.monkopedia.kodemirror.collab

import com.monkopedia.kodemirror.state.Annotation
import com.monkopedia.kodemirror.state.ChangeDesc
import com.monkopedia.kodemirror.state.ChangeSet
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import kotlin.random.Random

/**
 * An update is a set of changes and effects.
 */
data class Update(
    val changes: ChangeSet,
    val clientID: String,
    val effects: List<StateEffect<*>> = emptyList()
)

/**
 * A sendable update that also carries the originating transaction.
 */
data class SendableUpdate(
    val changes: ChangeSet,
    val clientID: String,
    val effects: List<StateEffect<*>> = emptyList(),
    val origin: Transaction
)

/**
 * Configuration for the collaborative editing extension.
 */
data class CollabConfig(
    val startVersion: Int = 0,
    val clientID: String? = null,
    val sharedEffects: ((Transaction) -> List<StateEffect<*>>)? = null
)

internal data class ResolvedCollabConfig(
    val startVersion: Int,
    val clientID: String,
    val sharedEffects: (Transaction) -> List<StateEffect<*>>
)

internal class LocalUpdate(
    val origin: Transaction,
    val changes: ChangeSet,
    val effects: List<StateEffect<*>>,
    val clientID: String
)

internal class CollabState(
    val version: Int,
    val unconfirmed: List<LocalUpdate>
)

private val collabConfig =
    Facet.define<ResolvedCollabConfig, ResolvedCollabConfig>(
        combine = { configs ->
            configs.firstOrNull() ?: ResolvedCollabConfig(
                startVersion = 0,
                clientID = "",
                sharedEffects = { emptyList() }
            )
        }
    )

private val collabReceive = Annotation.define<CollabState>()

private val collabField = StateField.define(
    StateFieldSpec(
        create = { state ->
            val config = state.facet(collabConfig)
            CollabState(config.startVersion, emptyList())
        },
        update = { collab, tr ->
            val isSync = tr.annotation(collabReceive)
            if (isSync != null) return@StateFieldSpec isSync
            val config = tr.startState.facet(collabConfig)
            val effects = config.sharedEffects(tr)
            if (effects.isNotEmpty() || !tr.changes.empty) {
                CollabState(
                    collab.version,
                    collab.unconfirmed + LocalUpdate(
                        tr,
                        tr.changes,
                        effects,
                        config.clientID
                    )
                )
            } else {
                collab
            }
        }
    )
)

/**
 * Create an instance of the collaborative editing plugin.
 */
fun collab(config: CollabConfig = CollabConfig()): Extension {
    val generatedID = Random.nextInt(0, 1_000_000_000).toString(36)
    val resolved = ResolvedCollabConfig(
        startVersion = config.startVersion,
        clientID = config.clientID ?: generatedID,
        sharedEffects = config.sharedEffects ?: { emptyList() }
    )
    return ExtensionList(listOf(collabField, collabConfig.of(resolved)))
}

/**
 * Create a transaction that represents a set of new updates received
 * from the authority. Applying this transaction moves the state
 * forward to adjust to the authority's view of the document.
 */
fun receiveUpdates(state: EditorState, updates: List<Update>): TransactionSpec {
    var version = state.field(collabField).version
    var unconfirmed = state.field(collabField).unconfirmed
    val clientID = state.facet(collabConfig).clientID

    version += updates.size
    var effects: List<StateEffect<*>> = emptyList()
    var changes: ChangeSet? = null

    var own = 0
    for (update in updates) {
        val ours = if (own < unconfirmed.size) unconfirmed[own] else null
        if (ours != null && ours.clientID == update.clientID) {
            if (changes != null) changes = changes.map(ours.changes, true)
            effects = StateEffect.mapEffects(effects, update.changes)
            own++
        } else {
            effects = StateEffect.mapEffects(effects, update.changes)
            effects = effects + update.effects
            changes =
                if (changes != null) changes.compose(update.changes) else update.changes
        }
    }

    if (own > 0) unconfirmed = unconfirmed.subList(own, unconfirmed.size)
    if (unconfirmed.isNotEmpty()) {
        if (changes != null) {
            var ch = changes
            unconfirmed = unconfirmed.map { update ->
                val updateChanges = update.changes.map(ch!!)
                ch = ch!!.map(update.changes, true)
                LocalUpdate(
                    update.origin,
                    updateChanges,
                    StateEffect.mapEffects(update.effects, ch!!),
                    clientID
                )
            }
            changes = ch
        }
        if (effects.isNotEmpty()) {
            val composed = unconfirmed.fold(
                ChangeSet.empty(unconfirmed[0].changes.length)
            ) { ch, u -> ch.compose(u.changes) }
            effects = StateEffect.mapEffects(effects, composed)
        }
    }

    if (changes == null) {
        return TransactionSpec(
            annotations = listOf(
                collabReceive.of(CollabState(version, unconfirmed))
            )
        )
    }

    return TransactionSpec(
        changes = ChangeSpec.Set(changes),
        effects = effects,
        annotations = listOf(
            Transaction.addToHistory.of(false),
            Transaction.remote.of(true),
            collabReceive.of(CollabState(version, unconfirmed))
        ),
        filter = false
    )
}

/**
 * Returns the set of locally made updates that still have to be sent
 * to the authority.
 */
fun sendableUpdates(state: EditorState): List<SendableUpdate> {
    return state.field(collabField).unconfirmed.map {
        SendableUpdate(
            changes = it.changes,
            clientID = it.clientID,
            effects = it.effects,
            origin = it.origin
        )
    }
}

/**
 * Get the version up to which the collab plugin has synced with the
 * central authority.
 */
fun getSyncedVersion(state: EditorState): Int {
    return state.field(collabField).version
}

/**
 * Get this editor's collaborative editing client ID.
 */
fun getClientID(state: EditorState): String {
    return state.facet(collabConfig).clientID
}

/**
 * Rebase and deduplicate an array of client-submitted updates that
 * came in with an out-of-date version number. `over` should hold the
 * updates that were accepted since the given version. Will return an
 * array of updates that has updates already accepted filtered out,
 * and has been moved over the other changes so that they apply to
 * the current document version.
 */
fun rebaseUpdates(updates: List<Update>, over: List<Update>): List<Update> {
    if (over.isEmpty() || updates.isEmpty()) return updates
    var changes: ChangeDesc? = null
    var skip = 0
    for (update in over) {
        val other = if (skip < updates.size) updates[skip] else null
        if (other != null && other.clientID == update.clientID) {
            if (changes != null) changes = changes.mapDesc(other.changes, true)
            skip++
        } else {
            changes =
                if (changes != null) changes.composeDesc(update.changes) else update.changes
        }
    }

    var result = if (skip > 0) updates.subList(skip, updates.size) else updates
    if (changes == null) return result
    var ch = changes
    return result.map { update ->
        val updateChanges = update.changes.map(ch!!)
        ch = ch!!.mapDesc(update.changes, true)
        Update(
            changes = updateChanges,
            clientID = update.clientID,
            effects = StateEffect.mapEffects(update.effects, ch!!)
        )
    }
}
