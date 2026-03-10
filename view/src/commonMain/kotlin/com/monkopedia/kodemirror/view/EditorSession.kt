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
package com.monkopedia.kodemirror.view

import androidx.compose.runtime.compositionLocalOf
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asInsert
import com.monkopedia.kodemirror.state.endPos
import com.monkopedia.kodemirror.state.transactionSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * An editor session holds the editor state and dispatches transactions.
 *
 * Create instances via the [EditorSession] factory function or the
 * [rememberEditorSession] composable.
 */
interface EditorSession {
    /** The current editor state. */
    val state: EditorState

    /** Dispatch one or more transaction specs against the current state. */
    fun dispatch(vararg specs: TransactionSpec)

    /** Dispatch a fully-built transaction. */
    fun dispatchTransaction(tr: Transaction)

    /** Get the current value of a view plugin, or null if the plugin is not active. */
    fun <V : PluginValue> plugin(plugin: ViewPlugin<V>): V?

    /** Get the document coordinates of a position. Returns null when outside the viewport. */
    fun coordsAtPos(pos: Int, side: Int = 1): Rect?

    /** Get the document position for on-screen coordinates. Returns null when outside the editor. */
    fun posAtCoords(x: Float, y: Float): Int?

    /** Whether the editor is in editable mode. */
    val editable: Boolean

    /** The overall text direction of the editor content. */
    val textDirection: Direction

    /** Get the text direction at a specific position. */
    fun textDirectionAt(pos: Int): Direction

    /** Get the bidirectional text spans for a line. */
    fun bidiSpans(line: com.monkopedia.kodemirror.state.Line): List<BidiSpan>

    /** Translate a phrase, optionally inserting values. */
    fun phrase(phrase: String, vararg insert: Any): String

    companion object {
        /** Facet that registers update listeners called after every transaction. */
        val updateListener: Facet<(ViewUpdate) -> Unit, List<(ViewUpdate) -> Unit>> =
            Facet.define()

        /** Facet that lets extensions add per-line text-direction computation. */
        val perLineTextDirection: Facet<Boolean, Boolean>
            get() = com.monkopedia.kodemirror.view.perLineTextDirection

        /** Facet for editor theme. */
        val theme: Facet<EditorTheme, EditorTheme>
            get() = editorTheme
    }
}

/** CompositionLocal that provides the current [EditorSession] to panel and tooltip composables. */
val LocalEditorSession = compositionLocalOf<EditorSession> {
    error("No EditorSession provided")
}

/** Create an [EditorSession] with the given initial state and optional update callback. */
fun EditorSession(initialState: EditorState, onUpdate: (Transaction) -> Unit = {}): EditorSession =
    EditorSessionImpl(initialState, onUpdate)

/**
 * Dispatch a transaction built via DSL.
 *
 * ```kotlin
 * session.dispatch {
 *     insert(0, "Hello")
 *     selection(5)
 *     scrollIntoView()
 * }
 * ```
 */
fun EditorSession.dispatch(
    block: com.monkopedia.kodemirror.state.TransactionSpecBuilder.() -> Unit
) {
    dispatch(transactionSpec(block))
}

/**
 * Replace the entire document content with [text].
 */
fun EditorSession.setDoc(text: String) {
    dispatch(
        TransactionSpec(
            changes = ChangeSpec.Single(
                from = DocPos.ZERO,
                to = state.doc.endPos,
                insert = text.asInsert()
            )
        )
    )
}

/**
 * Insert [text] at the given [pos] in the document.
 */
fun EditorSession.insertAt(pos: DocPos, text: String) {
    dispatch(
        TransactionSpec(
            changes = ChangeSpec.Single(from = pos, insert = text.asInsert()),
            selection = SelectionSpec.CursorSpec(pos + text.length)
        )
    )
}

/**
 * Delete the text between [from] and [to].
 */
fun EditorSession.deleteRange(from: DocPos, to: DocPos) {
    dispatch(TransactionSpec(changes = ChangeSpec.Single(from = from, to = to)))
}

/**
 * Set the selection to a range from [anchor] to [head].
 * If [head] is not provided, creates a cursor at [anchor].
 */
fun EditorSession.select(anchor: DocPos, head: DocPos = anchor) {
    dispatch(
        TransactionSpec(
            selection = SelectionSpec.CursorSpec(anchor = anchor, head = head)
        )
    )
}

/**
 * Select the entire document.
 */
fun EditorSession.selectAll() {
    dispatch(
        TransactionSpec(
            selection = SelectionSpec.EditorSelectionSpec(
                EditorSelection.single(anchor = DocPos.ZERO, head = state.doc.endPos)
            )
        )
    )
}

/**
 * Create an extension that calls [callback] with the full document text
 * whenever the document changes.
 *
 * ```kotlin
 * val session = rememberEditorSession(
 *     doc = "Hello",
 *     extensions = onChange { text -> println("New text: $text") }
 * )
 * ```
 */
fun onChange(callback: (String) -> Unit): Extension = EditorSession.updateListener.of { update ->
    if (update.docChanged) {
        callback(update.state.doc.toString())
    }
}

/**
 * Create an extension that calls [callback] with the current selection
 * whenever the selection changes.
 *
 * ```kotlin
 * val session = rememberEditorSession(
 *     doc = "Hello",
 *     extensions = onSelection { selection -> println("Cursor at: ${selection.main.head}") }
 * )
 * ```
 */
fun onSelection(callback: (EditorSelection) -> Unit): Extension =
    EditorSession.updateListener.of { update ->
        if (update.selectionSet) {
            callback(update.state.selection)
        }
    }

/**
 * Create an extension that launches [callback] in a coroutine whenever the
 * document changes.
 *
 * The coroutine scope is tied to the plugin lifecycle and is cancelled when
 * the editor is destroyed. Previous coroutines are NOT cancelled when new
 * changes arrive — use your own [Job] tracking if you need cancellation.
 *
 * ```kotlin
 * val session = rememberEditorSession(
 *     extensions = onChangeAsync { text -> saveToServer(text) }
 * )
 * ```
 */
fun onChangeAsync(callback: suspend CoroutineScope.(String) -> Unit): Extension = ViewPlugin.define(
    create = { _ -> AsyncChangePlugin(callback) }
).asExtension()

/**
 * Create an extension that launches [callback] in a coroutine whenever the
 * selection changes.
 */
fun onSelectionAsync(callback: suspend CoroutineScope.(EditorSelection) -> Unit): Extension =
    ViewPlugin.define(
        create = { _ -> AsyncSelectionPlugin(callback) }
    ).asExtension()

private class AsyncChangePlugin(
    private val callback: suspend CoroutineScope.(String) -> Unit
) : PluginValue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun update(update: ViewUpdate) {
        if (update.docChanged) {
            val text = update.state.doc.toString()
            scope.launch { callback(text) }
        }
    }

    override fun destroy() {
        scope.cancel()
    }
}

private class AsyncSelectionPlugin(
    private val callback: suspend CoroutineScope.(EditorSelection) -> Unit
) : PluginValue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun update(update: ViewUpdate) {
        if (update.selectionSet) {
            val selection = update.state.selection
            scope.launch { callback(selection) }
        }
    }

    override fun destroy() {
        scope.cancel()
    }
}

/** A simple axis-aligned rectangle used for coordinate results. */
data class Rect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}
