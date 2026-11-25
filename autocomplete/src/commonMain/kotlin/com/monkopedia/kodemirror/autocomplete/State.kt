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
package com.monkopedia.kodemirror.autocomplete

import com.monkopedia.kodemirror.state.Annotation
import com.monkopedia.kodemirror.state.AnnotationType
import com.monkopedia.kodemirror.state.ChangeDesc
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.TouchesResult
import com.monkopedia.kodemirror.state.Transaction

/**
 * Transaction annotation that tags a transaction where a completion was
 * accepted. Extensions can read this to react to completion events.
 */
val pickedCompletion: AnnotationType<Completion> = Annotation.define()

/** Effect to set the selected completion index. */
val setSelectedCompletion: StateEffectType<Int> = StateEffect.define()

/** Starts a query of every source; the value says whether it was explicit. */
internal val startCompletionEffect: StateEffectType<Boolean> = StateEffect.define()

/** Effect to close the completion list. */
internal val closeCompletionEffect: StateEffectType<Unit> = StateEffect.define()

/**
 * A completion source as the state tracks it. Two are equal when they wrap the same
 * function, as upstream compares sources by identity.
 */
internal sealed interface QuerySource

internal data class SyncSource(val query: CompletionSource) : QuerySource

internal data class AsyncSource(val query: SuspendCompletionSource) : QuerySource

/**
 * The sources of [this] config, in the order they are queried. An [asyncCompletionSource]
 * among the [override] sources is run as the suspend source it wraps.
 */
internal val CompletionConfig.sources: List<QuerySource>
    get() = override.orEmpty().map {
        (it as? SuspendingCompletionSource)?.let { s -> AsyncSource(s.source) }
            ?: SyncSource(it)
    } +
        asyncOverride.orEmpty().map(::AsyncSource)

/** The position completion happens at: upstream's `cur`, the main selection's start. */
internal fun cur(state: EditorState): DocPos = state.selection.main.from

/** Upstream's `State`: what a source is doing in the current session. */
internal enum class SourceState { Inactive, Pending, Result }

// Upstream's `UpdateType` flags: how a transaction affects the sources.
internal const val UPDATE_NONE = 0
internal const val UPDATE_TYPING = 1
internal const val UPDATE_BACKSPACING = 2
internal const val UPDATE_SIMPLE_INTERACTION = UPDATE_TYPING or UPDATE_BACKSPACING
internal const val UPDATE_ACTIVATE = 4
internal const val UPDATE_RESET = 8
internal const val UPDATE_RESET_IF_TOUCHING = 16

/** Whether this update type has [flag] set. */
internal infix fun Int.has(flag: Int): Boolean = this and flag != 0

/**
 * Upstream's `getUpdateType`. Only `input.type` activates completion; a paste, drop or
 * other input does not. (The port has no `activateOnCompletion` option, so an accepted
 * completion never activates it.)
 */
internal fun getUpdateType(tr: Transaction, conf: CompletionConfig): Int {
    val typing = tr.isUserEvent("input.type")
    return when {
        typing && conf.activateOnTyping -> UPDATE_ACTIVATE or UPDATE_TYPING
        typing -> UPDATE_TYPING
        tr.isUserEvent("delete.backward") -> UPDATE_BACKSPACING
        tr.selection != null -> UPDATE_RESET
        tr.docChanged -> UPDATE_RESET_IF_TOUCHING
        else -> UPDATE_NONE
    }
}

/**
 * One source's part in the completion session, as upstream's `ActiveSource`: inactive,
 * or pending a query (with whether it was started explicitly). [ActiveResult] holds a
 * source's result.
 */
internal open class ActiveSource(
    val source: QuerySource,
    val state: SourceState,
    val explicit: Boolean = false
) {
    open val hasResult: Boolean get() = false

    val isPending: Boolean get() = state == SourceState.Pending

    fun update(tr: Transaction, conf: CompletionConfig): ActiveSource {
        val type = getUpdateType(tr, conf)
        var value = this
        if (type has UPDATE_RESET || type has UPDATE_RESET_IF_TOUCHING && touches(tr)) {
            value = ActiveSource(source, SourceState.Inactive)
        }
        if (type has UPDATE_ACTIVATE && value.state == SourceState.Inactive) {
            value = ActiveSource(source, SourceState.Pending)
        }
        value = value.updateFor(tr, type)
        for (effect in tr.effects) {
            val start = effect.asType(startCompletionEffect)
            val setActive = effect.asType(setActiveEffect)
            when {
                start != null -> value = ActiveSource(source, SourceState.Pending, start.value)
                effect.asType(closeCompletionEffect) != null ->
                    value = ActiveSource(source, SourceState.Inactive)
                setActive != null -> for (active in setActive.value) {
                    if (active.source == source) value = active
                }
            }
        }
        return value
    }

    open fun updateFor(tr: Transaction, type: Int): ActiveSource = map(tr.changes)

    open fun map(changes: ChangeDesc): ActiveSource = this

    open fun touches(tr: Transaction): Boolean =
        tr.changes.touchesRange(cur(tr.state)) != TouchesResult.No
}

/**
 * A source's [result] in the open session, as upstream's `ActiveResult`: [from] and
 * [to] are the range it completes, mapped through every edit since the query ran, and
 * backspacing the cursor below [limit] drops it.
 */
internal class ActiveResult(
    source: QuerySource,
    explicit: Boolean,
    val limit: DocPos,
    val result: CompletionResult,
    val from: DocPos,
    val to: DocPos
) : ActiveSource(source, SourceState.Result, explicit) {
    override val hasResult: Boolean get() = true

    override fun updateFor(tr: Transaction, type: Int): ActiveSource {
        if (!(type has UPDATE_SIMPLE_INTERACTION)) return map(tr.changes)
        val from = tr.changes.mapPos(from)
        val to = tr.changes.mapPos(to, 1)
        val pos = cur(tr.state)
        if (pos > to ||
            type has UPDATE_BACKSPACING &&
            (cur(tr.startState) == this.from || pos < limit)
        ) {
            return ActiveSource(
                source,
                if (type has UPDATE_ACTIVATE) SourceState.Pending else SourceState.Inactive
            )
        }
        val limit = tr.changes.mapPos(limit)
        if (checkValid(result.validFor, tr.state, from, to)) {
            return ActiveResult(source, explicit, limit, result, from, to)
        }
        return ActiveSource(source, SourceState.Pending, explicit)
    }

    override fun map(changes: ChangeDesc): ActiveSource = if (changes.empty) {
        this
    } else {
        ActiveResult(
            source,
            explicit,
            changes.mapPos(limit),
            result,
            changes.mapPos(from),
            changes.mapPos(to, 1)
        )
    }

    override fun touches(tr: Transaction): Boolean =
        tr.changes.touchesRange(from, to) != TouchesResult.No

    companion object {
        /**
         * The [result] of a query that ran with the cursor at [pos], as upstream's plugin
         * `accept` builds it: an implicit result may be backspaced back to its `from`
         * when it starts at the cursor, and an unset `to` is the cursor.
         */
        fun arrived(source: QuerySource, explicit: Boolean, pos: DocPos, result: CompletionResult) =
            ActiveResult(
                source,
                explicit,
                minOf(pos, result.from + if (explicit) 0 else 1),
                result,
                result.from,
                result.to ?: pos
            )
    }
}

/** Whether [validFor] fully matches the text between [from] and [to] (upstream `checkValid`). */
private fun checkValid(validFor: Regex?, state: EditorState, from: DocPos, to: DocPos): Boolean =
    validFor != null && validFor.matches(state.sliceDoc(from, to))

/** Replaces the active sources it lists, as a finished query does. */
internal val setActiveEffect: StateEffectType<List<ActiveSource>> =
    StateEffect.define { sources, mapping -> sources.map { it.map(mapping) } }

/**
 * The completion list on screen, as upstream's `CompletionDialog`: the merged
 * [options], the [selected] index (-1 for none) and the popup's anchor [pos]. A
 * [disabled] dialog still shows its options while a source re-queries, but they can't
 * be selected or accepted.
 */
internal class CompletionDialog(
    val options: List<FilterResult>,
    val pos: DocPos,
    val selected: Int,
    val disabled: Boolean
) {
    fun setSelected(selected: Int): CompletionDialog =
        if (selected == this.selected || selected >= options.size) {
            this
        } else {
            CompletionDialog(options, pos, selected, disabled)
        }

    fun map(changes: ChangeDesc) =
        CompletionDialog(options, changes.mapPos(pos), selected, disabled)

    fun setDisabled() = CompletionDialog(options, pos, selected, true)

    companion object {
        fun build(
            active: List<ActiveSource>,
            state: EditorState,
            prev: CompletionDialog?,
            conf: CompletionConfig,
            didSetActive: Boolean
        ): CompletionDialog? {
            val pending = active.any { it.isPending }
            if (prev != null && !didSetActive && pending) return prev.setDisabled()
            val options = mergeCompletions(active, state)
            if (options.isEmpty()) return if (prev != null && pending) prev.setDisabled() else null
            var selected = if (conf.selectOnOpen) 0 else -1
            if (prev != null && prev.selected != selected && prev.selected != -1) {
                val selectedValue = prev.options[prev.selected].completion
                val index = options.indexOfFirst { it.completion === selectedValue }
                if (index >= 0) selected = index
            }
            val pos = active.filterIsInstance<ActiveResult>().minOf { it.from }
            return CompletionDialog(options, pos, selected, false)
        }
    }
}

/**
 * The completion session, as upstream's `CompletionState`: one [ActiveSource] per
 * configured source, and the [open] dialog, if any.
 */
internal class CompletionState(val active: List<ActiveSource>, val open: CompletionDialog?) {
    fun update(tr: Transaction): CompletionState {
        val state = tr.state
        val conf = state.facet(completionConfig)
        // A source added while completion is active starts pending.
        val initial = if (this.active.any { it.state != SourceState.Inactive }) {
            SourceState.Pending
        } else {
            SourceState.Inactive
        }
        var active = conf.sources.map { source ->
            (
                this.active.find {
                    it.source == source
                } ?: ActiveSource(source, initial)
                ).update(tr, conf)
        }
        if (active.size == this.active.size &&
            active.indices.all { active[it] === this.active[it] }
        ) {
            active = this.active
        }

        var open = open
        val didSet = tr.effects.any { it.asType(setActiveEffect) != null }
        if (open != null && tr.docChanged) open = open.map(tr.changes)
        if (tr.selection != null ||
            active.any { it is ActiveResult && it.touches(tr) } ||
            !sameResults(active, this.active) ||
            didSet
        ) {
            open = CompletionDialog.build(active, state, open, conf, didSet)
        } else if (open != null && open.disabled && active.none { it.isPending }) {
            open = null
        }
        if (open == null && active.none { it.isPending } && active.any { it.hasResult }) {
            active =
                active.map {
                    if (it.hasResult) ActiveSource(it.source, SourceState.Inactive) else it
                }
        }
        for (effect in tr.effects) {
            effect.asType(setSelectedCompletion)?.let { open = open?.setSelected(it.value) }
        }
        if (active === this.active && open === this.open) return this
        return CompletionState(active, open)
    }

    companion object {
        val start = CompletionState(emptyList(), null)
    }
}

/** Whether [a] and [b] hold the same results, in order (upstream `sameResults`). */
private fun sameResults(a: List<ActiveSource>, b: List<ActiveSource>): Boolean {
    if (a === b) return true
    val resultsA = a.filterIsInstance<ActiveResult>()
    val resultsB = b.filterIsInstance<ActiveResult>()
    return resultsA.size == resultsB.size &&
        resultsA.indices.all { resultsA[it].result === resultsB[it].result }
}

/** State field that tracks the active completion state. */
internal val completionStateField: StateField<CompletionState> = StateField.define(
    StateFieldSpec(
        create = { CompletionState.start },
        update = { value, tr -> value.update(tr) }
    )
)

/**
 * Get the completion status: "pending" while a source is being queried, "active" when
 * completions are shown, null otherwise.
 */
fun completionStatus(state: EditorState): String? {
    val cs = state.field(completionStateField, require = false) ?: return null
    return when {
        cs.active.any { it.isPending } -> "pending"
        cs.active.any { it.state != SourceState.Inactive } -> "active"
        else -> null
    }
}

/** The open dialog, unless it is disabled while a source re-queries. */
private fun enabledDialog(state: EditorState): CompletionDialog? =
    state.field(completionStateField, require = false)?.open?.takeUnless { it.disabled }

/** Get the current list of filtered completions. */
fun currentCompletions(state: EditorState): List<Completion> =
    enabledDialog(state)?.options?.map { it.completion }.orEmpty()

/** Get the currently selected completion. */
fun selectedCompletion(state: EditorState): Completion? {
    val open = enabledDialog(state) ?: return null
    return if (open.selected >= 0) open.options[open.selected].completion else null
}

/** Get the index of the currently selected completion, or -1 when there is none. */
fun selectedCompletionIndex(state: EditorState): Int = enabledDialog(state)?.selected ?: -1
