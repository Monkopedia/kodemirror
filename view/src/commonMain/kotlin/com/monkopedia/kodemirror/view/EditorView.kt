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

import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec

/**
 * The EditorView class holds the editor state and dispatches transactions.
 *
 * This class is not itself a Composable; callers embed the editor via
 * [EditorViewComposable]. The [onUpdate] callback receives every dispatched
 * [Transaction] so the caller can feed the new [EditorState] back in.
 */
class EditorView(
    initialState: EditorState,
    val onUpdate: (Transaction) -> Unit = {}
) {
    /** The current editor state. Updated on every dispatched transaction. */
    var state: EditorState = initialState
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
    fun dispatch(vararg specs: TransactionSpec) {
        val tr = state.update(*specs)
        dispatchTransaction(tr)
    }

    /** Dispatch a fully-built transaction. */
    fun dispatchTransaction(tr: Transaction) {
        val focusChanged = hasFocus != lastHasFocus
        lastHasFocus = hasFocus
        val update = ViewUpdate(
            view = this,
            state = tr.state,
            transactions = listOf(tr),
            focusChanged = focusChanged
        )
        pluginHost?.update(update)
        state = tr.state
        onUpdate(tr)
    }

    /**
     * Get the current value of a view plugin, or null if the plugin is not
     * active in the current configuration.
     */
    @Suppress("UNCHECKED_CAST")
    fun <V : PluginValue> plugin(plugin: ViewPlugin<V>): V? = pluginHost?.plugin(plugin)

    /**
     * Get the document coordinates of a position.
     * Returns null when the position is outside the current viewport.
     */
    fun coordsAtPos(pos: Int, side: Int = 1): Rect? = lineLayoutCache?.coordsAtPos(pos, side, state)

    /**
     * Get the document position for a set of on-screen coordinates.
     * Returns null when the coordinates are outside the editor.
     */
    fun posAtCoords(x: Float, y: Float): Int? = lineLayoutCache?.posAtCoords(x, y, state)

    /** Whether the editor is in editable mode. */
    val editable: Boolean
        get() = state.facet(com.monkopedia.kodemirror.view.editable)

    companion object {
        /** Facet that registers update listeners called after every transaction. */
        val updateListener: Facet<(ViewUpdate) -> Unit, List<(ViewUpdate) -> Unit>> =
            Facet.define()

        /** Facet that lets extensions add per-line text-direction computation. */
        val perLineTextDirection: Facet<Boolean, Boolean> =
            com.monkopedia.kodemirror.view.perLineTextDirection

        /** Facet for editor theme. */
        val theme: Facet<EditorTheme, EditorTheme>
            get() = editorTheme
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
