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

import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder

/**
 * Extension that shows a cursor at the position where a drag is currently
 * hovering, to indicate where a drop would land.
 *
 * Port of `dropcursor.ts` from CodeMirror 6.
 */
val dropCursor: Extension = ViewPlugin.define(
    create = { _ -> DropCursorPlugin() },
    configure = {
        copy(
            decorations = { plugin ->
                (plugin as? DropCursorPlugin)?.decos ?: RangeSet.empty()
            }
        )
    }
).asExtension()

private class DropCursorPlugin : PluginValue {
    /** Document position of the current drag target, or null if not dragging. */
    var pos: Int? = null
    var decos: DecorationSet = RangeSet.empty()

    fun moveTo(newPos: Int?) {
        if (newPos == pos) return
        pos = newPos
        decos = if (newPos != null) {
            val builder = RangeSetBuilder<Decoration>()
            val widget = DropCursorWidget()
            builder.add(newPos, newPos, Decoration.widget(WidgetDecorationSpec(widget = widget)))
            builder.finish()
        } else {
            RangeSet.empty()
        }
    }
}

private class DropCursorWidget : WidgetType() {
    @Suppress("EmptyFunctionBlock")
    @androidx.compose.runtime.Composable
    override fun Content() {
        // Draws a visual cursor line via Canvas overlay in SelectionDrawing
    }

    override fun eq(other: WidgetType): Boolean = other is DropCursorWidget
}
