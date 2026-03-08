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

import com.monkopedia.kodemirror.state.CharCategory
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.SelectionRange

/**
 * Find the character group (word/space/punctuation) at the given position.
 *
 * @param state Current editor state.
 * @param pos   Document position to query.
 * @param side  -1 = look at the character before pos, 1 = look at char at pos.
 */
fun groupAt(state: EditorState, pos: Int, side: Int = 1): CharCategory {
    val doc = state.doc
    if (doc.length == 0) return CharCategory.Space
    val queryPos = if (side < 0) (pos - 1).coerceAtLeast(0) else pos.coerceAtMost(doc.length - 1)
    val char = doc.sliceString(queryPos, queryPos + 1)
    if (char.isEmpty()) return CharCategory.Space
    val categorizer = state.charCategorizer(queryPos)
    return categorizer(char)
}

/**
 * Move a selection range one character in [forward] direction,
 * extending if [extend] is true, otherwise collapsing.
 */
fun moveByChar(
    state: EditorState,
    sel: SelectionRange,
    forward: Boolean,
    extend: Boolean = false
): SelectionRange {
    val dir = if (forward) 1 else -1
    val anchor = if (extend) sel.anchor else sel.head
    val head = (sel.head + dir).coerceIn(0, state.doc.length)
    return if (extend) {
        EditorSelection.range(anchor, head)
    } else {
        EditorSelection.cursor(head)
    }
}

/**
 * Move a selection range by one word group in [forward] direction.
 *
 * A "word" here is any maximal run of the same [CharCategory].
 */
fun moveByGroup(
    state: EditorState,
    sel: SelectionRange,
    forward: Boolean,
    extend: Boolean = false
): SelectionRange {
    val doc = state.doc
    val len = doc.length
    var pos = sel.head
    if (pos < 0 || pos > len) return sel

    val startGroup = groupAt(state, pos, if (forward) 1 else -1)
    val dir = if (forward) 1 else -1

    while (true) {
        val next = pos + dir
        if (next < 0 || next > len) break
        val group = groupAt(state, next, -dir)
        if (group != startGroup) break
        pos = next
    }

    val anchor = if (extend) sel.anchor else pos
    return if (extend) EditorSelection.range(anchor, pos) else EditorSelection.cursor(pos)
}

/**
 * Move a selection range by one subword in [forward] direction.
 *
 * Subwords are delimited by camelCase boundaries (transitions from
 * lowercase to uppercase within a Word run) in addition to the normal
 * word/space/punctuation boundaries used by [moveByGroup].
 */
fun moveBySubword(
    state: EditorState,
    sel: SelectionRange,
    forward: Boolean,
    extend: Boolean = false
): SelectionRange {
    val doc = state.doc
    val len = doc.length
    var pos = sel.head
    if (pos < 0 || pos > len) return sel

    val startGroup = groupAt(state, pos, if (forward) 1 else -1)
    val dir = if (forward) 1 else -1

    // If not in a Word category, fall back to moveByGroup
    if (startGroup != CharCategory.Word) {
        return moveByGroup(state, sel, forward, extend)
    }

    // Move through word chars, stopping at camelCase boundaries
    var sawLower = false
    while (true) {
        val next = pos + dir
        if (next < 0 || next > len) break
        val group = groupAt(state, next, -dir)
        if (group != CharCategory.Word) break

        // Check camelCase boundary
        val checkPos = if (forward) next else next
        val charAtCheck = doc.sliceString(
            checkPos.coerceAtMost(len - 1),
            (checkPos + 1).coerceAtMost(len)
        )
        if (charAtCheck.isNotEmpty()) {
            val ch = charAtCheck[0]
            if (forward) {
                if (sawLower && ch.isUpperCase()) break
                sawLower = ch.isLowerCase()
            } else {
                if (ch.isUpperCase() && sawLower) break
                sawLower = ch.isLowerCase()
            }
        }
        pos = next
    }

    val anchor = if (extend) sel.anchor else pos
    return if (extend) EditorSelection.range(anchor, pos) else EditorSelection.cursor(pos)
}

/**
 * Move a selection range vertically by one line, keeping an approximate
 * horizontal position.
 *
 * Uses [EditorSession.coordsAtPos] to find current coordinates and
 * [EditorSession.posAtCoords] to project to the target line.
 *
 * @param view    The editor view (for coordinate queries).
 * @param sel     The current selection range.
 * @param forward If true, move down; if false, move up.
 * @param extend  If true, extend the selection rather than move the cursor.
 */
fun moveVertically(
    view: EditorSession,
    sel: SelectionRange,
    forward: Boolean,
    extend: Boolean = false
): SelectionRange {
    val coords = view.coordsAtPos(sel.head, if (forward) 1 else -1) ?: return sel
    val targetY = if (forward) {
        coords.bottom + 1f
    } else {
        coords.top - 1f
    }
    val newPos = view.posAtCoords(coords.centerX, targetY) ?: return sel
    val anchor = if (extend) sel.anchor else newPos
    return if (extend) EditorSelection.range(anchor, newPos) else EditorSelection.cursor(newPos)
}
