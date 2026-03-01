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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import com.monkopedia.kodemirror.state.EditorState

/** Extension to select the draw-selection extension. */
val drawSelection: com.monkopedia.kodemirror.state.Extension =
    com.monkopedia.kodemirror.state.ExtensionList(emptyList())

/**
 * Draw the cursor and selection ranges as a per-line [Modifier] overlay.
 *
 * Only draws the portion of the selection that intersects the given line,
 * using coordinates local to the line's composable.
 *
 * @param state  Current editor state.
 * @param lineFrom Document offset of the start of this line.
 * @param lineTo  Document offset of the end of this line.
 * @param theme  Editor theme for colors.
 * @param textLayoutResult Layout result for accurate character positioning.
 */
@Composable
fun Modifier.drawSelectionOverlay(
    state: EditorState,
    lineFrom: Int,
    lineTo: Int,
    theme: EditorTheme,
    textLayoutResult: TextLayoutResult? = null
): Modifier = this.drawWithContent {
    drawLineSelection(state, lineFrom, lineTo, theme, textLayoutResult)
    drawContent()
}

private fun DrawScope.drawLineSelection(
    state: EditorState,
    lineFrom: Int,
    lineTo: Int,
    theme: EditorTheme,
    textLayoutResult: TextLayoutResult?
) {
    val selection = state.selection
    for (range in selection.ranges) {
        if (range.empty) {
            // Draw cursor if it falls on this line
            if (range.head in lineFrom..lineTo) {
                drawLineCursor(range.head - lineFrom, theme.cursor, textLayoutResult)
            }
        } else {
            // Draw selection highlight if it overlaps this line
            val selFrom = maxOf(range.from, lineFrom)
            val selTo = minOf(range.to, lineTo)
            if (selFrom < selTo || (selFrom == selTo && selFrom > lineFrom)) {
                val extendsToNextLine = range.to > lineTo
                drawLineSelectionRange(
                    selFrom - lineFrom,
                    selTo - lineFrom,
                    lineTo - lineFrom,
                    theme.selection,
                    textLayoutResult,
                    extendsToNextLine
                )
            }
        }
    }
}

private fun DrawScope.drawLineCursor(
    offsetInLine: Int,
    cursorColor: Color,
    textLayoutResult: TextLayoutResult?
) {
    val lineHeight = size.height
    val textLen = textLayoutResult?.layoutInput?.text?.length
    val x = if (textLayoutResult != null && textLen != null && offsetInLine <= textLen) {
        textLayoutResult.getHorizontalPosition(offsetInLine, true)
    } else {
        0f
    }
    drawLine(
        color = cursorColor,
        start = Offset(x, 0f),
        end = Offset(x, lineHeight),
        strokeWidth = 2f
    )
}

private fun DrawScope.drawLineSelectionRange(
    fromOffset: Int,
    toOffset: Int,
    lineLength: Int,
    selectionColor: Color,
    textLayoutResult: TextLayoutResult?,
    extendsToNextLine: Boolean
) {
    val lineHeight = size.height
    val textLen = textLayoutResult?.layoutInput?.text?.length ?: lineLength

    val x: Float
    val endX: Float

    if (textLayoutResult != null) {
        x = textLayoutResult.getHorizontalPosition(
            fromOffset.coerceAtMost(textLen),
            true
        )
        endX = if (extendsToNextLine) {
            // Selection continues to next line — extend to full container width
            size.width
        } else {
            textLayoutResult.getHorizontalPosition(
                toOffset.coerceAtMost(textLen),
                true
            )
        }
    } else {
        // Fallback: fraction-based (inaccurate but better than nothing)
        val startFraction = if (lineLength > 0) fromOffset.toFloat() / lineLength else 0f
        val endFraction = if (lineLength > 0) toOffset.toFloat() / lineLength else 1f
        x = startFraction * size.width
        endX = if (extendsToNextLine) size.width else endFraction * size.width
    }

    val w = endX - x
    drawRect(
        color = selectionColor,
        topLeft = Offset(x, 0f),
        size = Size(w.coerceAtLeast(1f), lineHeight)
    )
}
