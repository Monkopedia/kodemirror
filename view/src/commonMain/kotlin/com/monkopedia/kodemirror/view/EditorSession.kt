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
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.transactionSpec

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
