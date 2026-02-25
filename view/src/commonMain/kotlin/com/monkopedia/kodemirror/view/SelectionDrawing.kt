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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.monkopedia.kodemirror.state.EditorState

/** Extension to select the draw-selection extension. */
val drawSelection: com.monkopedia.kodemirror.state.Extension =
    com.monkopedia.kodemirror.state.ExtensionList(emptyList())

/**
 * Draw the cursor blink and selection ranges as a [Modifier] overlay.
 *
 * Call [Modifier.drawSelection] on the composable that renders the editor
 * content to have cursor and selection drawn on top via Canvas.
 */
@Composable
fun Modifier.drawSelectionOverlay(
    state: EditorState,
    cache: LineLayoutCache,
    theme: EditorTheme
): Modifier = this.drawWithContent {
    drawContent()
    drawSelectionLayer(state, cache, theme)
}

private fun DrawScope.drawSelectionLayer(
    state: EditorState,
    cache: LineLayoutCache,
    theme: EditorTheme
) {
    val selection = state.selection
    for (range in selection.ranges) {
        if (range.empty) {
            // Draw cursor
            drawCursor(range.head, state, cache, theme.cursor)
        } else {
            // Draw selection highlight
            drawSelectionRange(range.from, range.to, state, cache, theme.selection)
        }
    }
}

private fun DrawScope.drawCursor(
    pos: Int,
    state: EditorState,
    cache: LineLayoutCache,
    cursorColor: Color
) {
    val rect = cache.coordsAtPos(pos, 1, state) ?: return
    drawLine(
        color = cursorColor,
        start = androidx.compose.ui.geometry.Offset(rect.left, rect.top),
        end = androidx.compose.ui.geometry.Offset(rect.left, rect.bottom),
        strokeWidth = 2f
    )
}

private fun DrawScope.drawSelectionRange(
    from: Int,
    to: Int,
    state: EditorState,
    cache: LineLayoutCache,
    selectionColor: Color
) {
    val doc = state.doc
    val fromLine = doc.lineAt(from)
    val toLine = doc.lineAt(to)

    if (fromLine.number == toLine.number) {
        // Single-line selection
        val startRect = cache.coordsAtPos(from, 1, state) ?: return
        val endRect = cache.coordsAtPos(to, -1, state) ?: return
        drawRect(
            color = selectionColor,
            topLeft = androidx.compose.ui.geometry.Offset(startRect.left, startRect.top),
            size = androidx.compose.ui.geometry.Size(
                endRect.right - startRect.left,
                startRect.height
            )
        )
    } else {
        // Multi-line selection — draw each line segment
        for (lineNum in fromLine.number..toLine.number) {
            val line = doc.line(lineNum)
            val selFrom = if (lineNum == fromLine.number) from else line.from
            val selTo = if (lineNum == toLine.number) to else line.to
            val startRect = cache.coordsAtPos(selFrom, 1, state) ?: continue
            val endRect = cache.coordsAtPos(selTo, -1, state) ?: continue
            drawRect(
                color = selectionColor,
                topLeft = androidx.compose.ui.geometry.Offset(startRect.left, startRect.top),
                size = androidx.compose.ui.geometry.Size(
                    endRect.right - startRect.left,
                    startRect.height
                )
            )
        }
    }
}
