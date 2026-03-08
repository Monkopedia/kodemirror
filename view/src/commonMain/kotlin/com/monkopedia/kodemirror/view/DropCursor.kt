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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder

/**
 * The [ViewPlugin] that manages the drop cursor. Used internally by the
 * composable to call [DropCursorPlugin.moveTo] during drag events.
 */
internal val dropCursorViewPlugin: ViewPlugin<DropCursorPlugin> = ViewPlugin.define(
    create = { _ -> DropCursorPlugin() },
    configure = {
        copy(
            decorations = { plugin ->
                (plugin as? DropCursorPlugin)?.decos ?: RangeSet.empty()
            }
        )
    }
)

/**
 * Extension that shows a cursor at the position where a drag is currently
 * hovering, to indicate where a drop would land.
 *
 * Port of `dropcursor.ts` from CodeMirror 6.
 */
val dropCursor: Extension = dropCursorViewPlugin.asExtension()

internal class DropCursorPlugin : PluginValue {
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
    @Composable
    override fun Content() {
        val theme = LocalEditorTheme.current
        val lineHeight = with(LocalDensity.current) {
            theme.contentTextStyle.lineHeight.toDp()
        }
        val cursorColor = theme.cursor
        Canvas(
            modifier = Modifier
                .width(2.dp)
                .height(lineHeight)
        ) {
            drawLine(
                color = cursorColor,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 2f
            )
        }
    }

    override fun equals(other: Any?): Boolean = other is DropCursorWidget

    override fun hashCode(): Int = this::class.hashCode()
}
