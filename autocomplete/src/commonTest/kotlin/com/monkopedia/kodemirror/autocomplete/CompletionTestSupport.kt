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

import com.monkopedia.kodemirror.state.ChangeByRangeResult
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.ViewUpdate
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * A session that runs the completion plugin, as the composable's plugin host does, so
 * commands and typing query the sources. Its coroutines run on [coroutineScope],
 * inline by default.
 */
internal class PluginSession(
    initial: EditorState,
    override val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    timeSource: TimeSource = TimeSource.Monotonic,
    private val base: EditorSession = EditorSession(initial)
) : EditorSession by base {
    private val plugin = CompletionPlugin(this, timeSource)

    override fun dispatch(vararg specs: TransactionSpec) = dispatchTransaction(state.update(*specs))

    override fun dispatchTransaction(tr: Transaction) {
        base.dispatchTransaction(tr)
        plugin.update(ViewUpdate(this, tr.state, listOf(tr)))
    }
}

/** A state at [doc] with the cursor at [cursor], completing with [config]. */
internal fun completionState(
    doc: String,
    cursor: Int = doc.length,
    config: CompletionConfig = CompletionConfig(),
    extensions: List<Extension> = emptyList()
): EditorState = EditorState.create(
    EditorStateConfig(
        doc = doc.asDoc(),
        selection = SelectionSpec.CursorSpec(DocPos(cursor)),
        extensions = ExtensionList(
            listOf(completionConfig.of(config), completionStateField) + extensions
        )
    )
)

/** A [PluginSession] at [doc] with the cursor at [cursor], completing with [config]. */
internal fun completionSession(
    doc: String,
    cursor: Int = doc.length,
    config: CompletionConfig = CompletionConfig(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    extensions: List<Extension> = emptyList(),
    timeSource: TimeSource = TimeSource.Monotonic
): EditorSession =
    PluginSession(completionState(doc, cursor, config, extensions), scope, timeSource)

/** A source that always returns [result]. */
internal fun fixed(result: CompletionResult): CompletionSource = { result }

/** Query every source as typing does (an implicit start). */
internal fun EditorSession.startImplicit() =
    dispatch(TransactionSpec(effects = listOf(startCompletionEffect.of(false))))

/** Insert [text] at every cursor, tagged [userEvent], as the editor's input path does. */
internal fun EditorSession.typeText(text: String, userEvent: String = "input.type") = dispatch(
    state.changeByRange { r ->
        ChangeByRangeResult(
            changes = ChangeSpec.Single(r.head, r.head, InsertContent.StringContent(text)),
            range = EditorSelection.cursor(r.head + text.length)
        )
    }.copy(userEvent = userEvent)
)

/** Delete the character before every cursor, as Backspace does. */
internal fun EditorSession.backspace() = dispatch(
    state.changeByRange { r ->
        ChangeByRangeResult(
            changes = ChangeSpec.Single(r.head - 1, r.head, InsertContent.StringContent("")),
            range = EditorSelection.cursor(r.head - 1)
        )
    }.copy(userEvent = "delete.backward")
)

/** The completion status and the labels shown, the shape the upstream probes print. */
internal fun shown(s: EditorSession) =
    completionStatus(s.state) to currentCompletions(s.state).map { it.label }
