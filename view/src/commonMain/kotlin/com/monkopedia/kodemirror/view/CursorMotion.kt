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
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.SelectionRange
import com.monkopedia.kodemirror.state.endPos

/**
 * Find the character group (word/space/punctuation) at the given position.
 *
 * @param state Current editor state.
 * @param pos   Document position to query.
 * @param side  -1 = look at the character before pos, 1 = look at char at pos.
 */
fun groupAt(state: EditorState, pos: DocPos, side: Int = 1): CharCategory {
    val doc = state.doc
    if (doc.length == 0) return CharCategory.Space
    val queryPos = if (side < 0) {
        (pos - 1).coerceAtLeast(DocPos.ZERO)
    } else {
        pos.coerceAtMost(doc.endPos - 1)
    }
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
    val head = (sel.head + dir).coerceIn(DocPos.ZERO, state.doc.endPos)
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
 * When starting on whitespace, the movement skips the whitespace and
 * then continues through the adjacent word/punctuation group, matching
 * the CodeMirror 6 behavior where Ctrl-Right from a space skips to the
 * end of the next word.
 */
fun moveByGroup(
    state: EditorState,
    sel: SelectionRange,
    forward: Boolean,
    extend: Boolean = false
): SelectionRange {
    val doc = state.doc
    val endPos = doc.endPos
    var pos = sel.head
    if (pos < DocPos.ZERO || pos > endPos) return sel

    var currentGroup = groupAt(state, pos, if (forward) 1 else -1)
    val dir = if (forward) 1 else -1

    while (true) {
        val next = pos + dir
        if (next < DocPos.ZERO || next > endPos) break
        val group = groupAt(state, next, -dir)
        // When starting on Space, update the target group to the first
        // non-space category we encounter (skip whitespace then continue).
        if (currentGroup == CharCategory.Space && group != CharCategory.Space) {
            currentGroup = group
        } else if (group != currentGroup) {
            break
        }
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
    val endPos = doc.endPos
    var pos = sel.head
    if (pos < DocPos.ZERO || pos > endPos) return sel

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
        if (next < DocPos.ZERO || next > endPos) break
        val group = groupAt(state, next, -dir)
        if (group != CharCategory.Word) break

        // Check camelCase boundary
        val checkPos = if (forward) next else next
        val charAtCheck = doc.sliceString(
            checkPos.coerceAtMost(endPos - 1),
            (checkPos + 1).coerceAtMost(endPos)
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
    val doc = view.state.doc
    val currentLine = doc.lineAt(sel.head)

    // Preserve goalColumn across consecutive vertical moves so that moving down
    // through a short line and back up remembers the original column. The goal
    // column is the REMEMBERED horizontal position; it is captured on the first
    // vertical move and must NOT be overwritten by subsequent vertical moves.
    val goalCol = sel.goalColumn
        ?: (sel.head.value - currentLine.from.value)

    val coords = view.coordsAtPos(sel.head.value, if (forward) 1 else -1) ?: return sel

    // Vertical motion targets the adjacent visual row at the REMEMBERED goal
    // column's x, not the current head's x. When a stored goalColumn differs
    // from the head's column (e.g. after End landed the cursor short on a short
    // line), resolve the goal column to an x on the current logical line and use
    // that as the horizontal target so wrap-aware posAtCoords lands at the goal.
    val headCol = sel.head.value - currentLine.from.value
    val goalX = if (goalCol != headCol) {
        val goalPos = (currentLine.from.value + goalCol)
            .coerceAtMost(currentLine.to.value)
        view.coordsAtPos(goalPos, 1)?.centerX ?: coords.centerX
    } else {
        coords.centerX
    }
    val targetY = if (forward) {
        coords.bottom + 1f
    } else {
        coords.top - 1f
    }
    val rawPos = view.posAtCoords(goalX, targetY)

    // posAtCoords is wrap-aware: targetY lands on the adjacent VISUAL row, which
    // may be another wrapped row of the SAME logical line. Honor that result
    // directly so vertical motion steps by visual row (this is what gj/gk and
    // the default cursor up/down rely on for wrapped lines).
    if (rawPos != null && rawPos != sel.head.value) {
        val resultLine = doc.lineAt(DocPos(rawPos))
        // When the target visual row is a DIFFERENT logical line, re-project the
        // remembered goal column onto that line (clamped) rather than trusting the
        // x-derived offset. This is what restores column memory: posAtCoords can
        // only resolve goalX as far as the current (possibly short) line extends,
        // so on a short intermediate line goalX under-reports the goal. Snapping
        // to the clamped goal column keeps the cursor at the remembered column and
        // lets it spring back out to the goal on the next, longer line.
        // Within the SAME wrapped logical line, honor posAtCoords so motion stays
        // by visual row.
        val newPos = if (resultLine.number != currentLine.number) {
            val targetCol = goalCol.coerceAtMost(resultLine.text.length)
            DocPos(resultLine.from.value + targetCol)
        } else {
            DocPos(rawPos)
        }
        val anchor = if (extend) sel.anchor else newPos
        return if (extend) {
            EditorSelection.range(anchor, newPos, goalColumn = goalCol)
        } else {
            EditorSelection.cursor(newPos, goalColumn = goalCol)
        }
    }

    // Fallback: posAtCoords couldn't move (no layout geometry, or it snapped
    // back to the same offset across an inter-line gap). Step by a whole logical
    // line using the preserved goal column.
    val targetLineNum = if (forward) {
        currentLine.number.value + 1
    } else {
        currentLine.number.value - 1
    }

    if (targetLineNum !in 1..doc.lines) return sel

    val targetLine = doc.line(LineNumber(targetLineNum))
    val targetCol = goalCol.coerceAtMost(targetLine.text.length)
    val newPos = DocPos(targetLine.from.value + targetCol)

    val anchor = if (extend) sel.anchor else newPos
    return if (extend) {
        EditorSelection.range(anchor, newPos, goalColumn = goalCol)
    } else {
        EditorSelection.cursor(newPos, goalColumn = goalCol)
    }
}
