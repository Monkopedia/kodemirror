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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState

/**
 * Cached layout result for a single document line.
 *
 * @param lineNumber 1-based line number.
 * @param lineFrom Document offset of the first character of this line.
 * @param topPx Y coordinate of the top of this line in the editor.
 * @param result Compose text-layout result for measuring cursor positions.
 */
data class LineLayout(
    val lineNumber: Int,
    val lineFrom: Int,
    val topPx: Float,
    val leftPx: Float,
    val result: TextLayoutResult
) {
    val heightPx: Float get() = result.size.height.toFloat()
    val bottomPx: Float get() = topPx + heightPx
}

/**
 * Cache of [TextLayoutResult] objects for every currently-rendered line.
 *
 * The composable stores results here via [store]; the [EditorSession] uses
 * [coordsAtPos] and [posAtCoords] for coordinate queries.
 */
class LineLayoutCache {
    private val cache = mutableMapOf<Int, LineLayout>()

    /** Store (or replace) the layout result for a line. */
    fun store(
        lineNumber: Int,
        lineFrom: Int,
        topPx: Float,
        leftPx: Float,
        result: TextLayoutResult
    ) {
        cache[lineNumber] = LineLayout(lineNumber, lineFrom, topPx, leftPx, result)
    }

    /** Return the layout for a given 1-based line number, or null. */
    fun forLine(lineNumber: Int): LineLayout? = cache[lineNumber]

    /** Evict cached entries that are no longer in the visible set. */
    fun evict(visibleLineNumbers: Set<Int>) {
        val toRemove = cache.keys.filter { it !in visibleLineNumbers }
        toRemove.forEach { cache.remove(it) }
    }

    /** Clear all cached entries. */
    fun clear() = cache.clear()

    /**
     * Return the document coordinates of the cursor at [pos].
     *
     * @param pos Document character offset.
     * @param side -1 for the left edge, 1 for the right edge of the character.
     * @param state Current editor state (used to look up the line).
     */
    fun coordsAtPos(pos: Int, side: Int, state: EditorState): Rect? {
        val line = state.doc.lineAt(DocPos(pos))
        val layout = cache[line.number.value] ?: return null
        val offsetInLine = pos - line.from.value
        val clampedOffset = offsetInLine.coerceIn(0, line.text.length)
        val cursorRect = layout.result.getCursorRect(clampedOffset)
        val top = layout.topPx + cursorRect.top
        val bottom = layout.topPx + cursorRect.bottom
        val xRaw = if (side >= 0) cursorRect.right else cursorRect.left
        val xOffset = xRaw + layout.leftPx
        return Rect(xOffset, top, xOffset, bottom)
    }

    /**
     * Return the document position for an on-screen coordinate pair.
     */
    fun posAtCoords(x: Float, y: Float, state: EditorState): Int? {
        // Find the line whose vertical range contains y
        for (layout in cache.values.sortedBy { it.topPx }) {
            if (y >= layout.topPx && y <= layout.bottomPx) {
                val localX = (x - layout.leftPx).coerceAtLeast(0f)
                val offsetInLine = layout.result.getOffsetForPosition(
                    Offset(localX, y - layout.topPx)
                )
                return layout.lineFrom +
                    offsetInLine.coerceIn(0, layout.result.layoutInput.text.length)
            }
        }
        // y is outside any line's exact range — find the best matching line
        val sorted = cache.values.sortedBy { it.topPx }
        if (sorted.isEmpty()) return null

        // When y falls in a gap between two lines, prefer the line below
        // (whose top is closest). This ensures downward vertical navigation
        // crosses to the next line rather than snapping back to the current.
        val closest = sorted.firstOrNull { it.topPx > y }
            ?: sorted.lastOrNull { it.bottomPx < y }
            ?: sorted.first()
        val localX = (x - closest.leftPx).coerceAtLeast(0f)
        val localY = (y - closest.topPx).coerceIn(0f, closest.heightPx)
        val offsetInLine = closest.result.getOffsetForPosition(
            Offset(localX, localY)
        )
        return closest.lineFrom +
            offsetInLine.coerceIn(0, closest.result.layoutInput.text.length)
    }

    /**
     * Return block info for the line containing [pos].
     */
    fun blockAtPos(pos: Int, state: EditorState): BlockInfo? {
        val line = state.doc.lineAt(DocPos(pos))
        val layout = cache[line.number.value] ?: return null
        return BlockInfo(
            from = line.from.value,
            to = line.to.value,
            top = layout.topPx,
            height = layout.heightPx,
            type = BlockType.Text
        )
    }
}
