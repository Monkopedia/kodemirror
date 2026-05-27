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
import androidx.compose.ui.platform.ClipboardManager
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import kotlinx.coroutines.CoroutineScope

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

    /** Composition-scoped coroutine scope (initialised by the composable). */
    internal var backingCoroutineScope: CoroutineScope? = null

    /** Clipboard manager provided by the composition (initialised by the composable). */
    internal var clipboardManager: ClipboardManager? = null

    /**
     * Internal clipboard buffer for copy/paste within the editor.
     *
     * On wasmJs the browser clipboard API is asynchronous, so Compose's
     * [ClipboardManager.getText] may return null even after a successful
     * [ClipboardManager.setText]. This buffer ensures that text copied with
     * Ctrl+C / Ctrl+X can always be pasted with Ctrl+V within the same
     * session.
     */
    internal var internalClipboard: String? = null

    override val coroutineScope: CoroutineScope
        get() = backingCoroutineScope
            ?: error("EditorSession is not attached to a KodeMirror composable")

    /** Tracking fields for ViewUpdate flags — updated by the composable. */
    internal var lastFirstVisibleItem: Int = 0

    /** Number of items currently laid out in the viewport (for tests/diagnostics). */
    internal var lastVisibleItemCount: Int = 0

    /**
     * Current horizontal scroll offset of the content area, in pixels
     * (for tests/diagnostics). Updated by the composable as the shared
     * horizontal scroll state changes; drives the horizontal scroll-into-view
     * assertions in tests.
     */
    internal var lastHorizontalScrollPx: Int = 0
    internal var lastLayoutHeight: Float = 0f
    internal var hasFocus: Boolean = false
    private var lastHasFocus: Boolean = false

    /**
     * Pending scroll-into-view request, observed by the composable.
     *
     * Set whenever a dispatched transaction carries [Transaction.scrollIntoView].
     * The composable observes this via Compose snapshot state and scrolls the
     * line list so the [ScrollRequest.target] document position becomes visible.
     *
     * A monotonically increasing [ScrollRequest.token] guarantees that two
     * consecutive jumps to the *same* position (e.g. vim `n` landing on the same
     * column) still trigger a fresh scroll, since the value is observed by
     * identity/equality.
     */
    internal var scrollRequest: ScrollRequest? by mutableStateOf(null)
        private set

    private var scrollRequestToken: Long = 0

    /** Dispatch one or more transaction specs against the current state. */
    override fun dispatch(vararg specs: TransactionSpec) {
        val tr = state.update(*specs)
        dispatchTransaction(tr)
    }

    /** Dispatch a fully-built transaction. */
    override fun dispatchTransaction(tr: Transaction) {
        val focusChanged = hasFocus != lastHasFocus
        lastHasFocus = hasFocus
        val oldState = state
        // Set state before updating plugins so that plugins accessing
        // session.state during their update() callback see the new state.
        state = tr.state
        val update = ViewUpdate(
            session = this,
            state = tr.state,
            transactions = listOf(tr),
            focusChanged = focusChanged
        )
        pluginHost?.update(update)
        pluginHost?.syncToState(tr.state, oldState)
        // Honor scrollIntoView: queue a request for the composable to reveal
        // the primary selection head. This drives caret-reveal on cursor moves
        // (#33) and the search/jump reveal in vim `n`/`N` (#58).
        if (tr.scrollIntoView) {
            scrollRequest = ScrollRequest(
                target = tr.state.selection.main.head.value,
                token = scrollRequestToken++
            )
        }
        onUpdate(tr)
    }

    /** Clear the pending scroll request once the composable has handled it. */
    internal fun consumeScrollRequest(request: ScrollRequest) {
        if (scrollRequest === request) {
            scrollRequest = null
        }
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
            val firstLine = if (doc.lines > 0) doc.line(LineNumber.FIRST).text else ""
            return autoDirection(firstLine, 0, firstLine.length)
        }

    override fun textDirectionAt(pos: Int): Direction {
        if (!state.facet(perLineTextDirection)) return textDirection
        val line = state.doc.lineAt(DocPos(pos))
        return autoDirection(line.text, 0, line.text.length)
    }

    override fun bidiSpans(line: com.monkopedia.kodemirror.state.Line): List<BidiSpan> {
        return computeOrder(line.text, textDirectionAt(line.from.value))
    }

    override fun phrase(phrase: String, vararg insert: Any): String = state.phrase(phrase, *insert)
}

/**
 * A pending request to scroll a document position into the visible viewport.
 *
 * @param target document offset to reveal (the primary selection head).
 * @param token  monotonically increasing id, so repeated requests to the same
 *   [target] still register as distinct values.
 */
internal data class ScrollRequest(val target: Int, val token: Long)
