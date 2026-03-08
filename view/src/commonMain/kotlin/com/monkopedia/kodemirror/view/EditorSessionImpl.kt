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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec

/**
 * Internal implementation of [EditorSession].
 */
internal class EditorSessionImpl(
    initialState: EditorState,
    val onUpdate: (Transaction) -> Unit = {}
) : EditorSession {
    /** The current editor state, backed by Compose snapshot state for recomposition. */
    override var state: EditorState by mutableStateOf(initialState)
        internal set

    /** Internal plugin host (initialised by the composable). */
    internal var pluginHost: ViewPluginHost? = null

    /** Layout cache for coordinate queries (initialised by the composable). */
    internal var lineLayoutCache: LineLayoutCache? = null

    /** Tracking fields for ViewUpdate flags — updated by the composable. */
    internal var lastFirstVisibleItem: Int = 0
    internal var lastLayoutHeight: Float = 0f
    internal var hasFocus: Boolean = false
    private var lastHasFocus: Boolean = false

    /** Dispatch one or more transaction specs against the current state. */
    override fun dispatch(vararg specs: TransactionSpec) {
        val tr = state.update(*specs)
        dispatchTransaction(tr)
    }

    /** Dispatch a fully-built transaction. */
    override fun dispatchTransaction(tr: Transaction) {
        val focusChanged = hasFocus != lastHasFocus
        lastHasFocus = hasFocus
        val update = ViewUpdate(
            session = this,
            state = tr.state,
            transactions = listOf(tr),
            focusChanged = focusChanged
        )
        pluginHost?.update(update)
        val oldState = state
        state = tr.state
        pluginHost?.syncToState(tr.state, oldState)
        onUpdate(tr)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V : PluginValue> plugin(plugin: ViewPlugin<V>): V? = pluginHost?.plugin(plugin)

    override fun coordsAtPos(pos: Int, side: Int): Rect? =
        lineLayoutCache?.coordsAtPos(pos, side, state)

    override fun posAtCoords(x: Float, y: Float): Int? = lineLayoutCache?.posAtCoords(x, y, state)

    override val editable: Boolean
        get() = state.facet(com.monkopedia.kodemirror.view.editable)

    override val textDirection: Direction
        get() {
            val doc = state.doc
            val firstLine = if (doc.lines > 0) doc.line(1).text else ""
            return autoDirection(firstLine, 0, firstLine.length)
        }

    override fun textDirectionAt(pos: Int): Direction {
        if (!state.facet(perLineTextDirection)) return textDirection
        val line = state.doc.lineAt(pos)
        return autoDirection(line.text, 0, line.text.length)
    }

    override fun bidiSpans(line: com.monkopedia.kodemirror.state.Line): List<BidiSpan> {
        return computeOrder(line.text, textDirectionAt(line.from))
    }

    override fun phrase(phrase: String, vararg insert: Any): String = state.phrase(phrase, *insert)
}
