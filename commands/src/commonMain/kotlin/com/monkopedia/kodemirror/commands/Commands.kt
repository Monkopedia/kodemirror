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
package com.monkopedia.kodemirror.commands

import com.monkopedia.kodemirror.language.getIndentation
import com.monkopedia.kodemirror.language.indentString
import com.monkopedia.kodemirror.language.matchBrackets
import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.state.ChangeByRangeResult
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.CharCategory
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.SelectionRange
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.endPos
import com.monkopedia.kodemirror.state.findClusterBreak
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.groupAt
import com.monkopedia.kodemirror.view.moveByChar
import com.monkopedia.kodemirror.view.moveByGroup
import com.monkopedia.kodemirror.view.moveBySubword
import com.monkopedia.kodemirror.view.moveVertically

/**
 * Map over all selection ranges, dispatch the new selection.
 */
private fun updateSel(
    view: EditorSession,
    how: (SelectionRange, EditorSession) -> SelectionRange
): Boolean {
    val state = view.state
    val newRanges = state.selection.ranges.map { how(it, view) }
    val newSel = EditorSelection.create(newRanges, state.selection.mainIndex)
    if (newSel == state.selection) return false
    view.dispatch(
        TransactionSpec(
            selection = SelectionSpec.EditorSelectionSpec(newSel),
            scrollIntoView = true,
            userEvent = "select"
        )
    )
    return true
}

// --- Cursor movement commands ---

/** Move cursor one character to the left. */
val cursorCharLeft: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ -> moveByChar(view.state, sel, forward = false) }
}

/** Move cursor one character to the right. */
val cursorCharRight: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ -> moveByChar(view.state, sel, forward = true) }
}

/**
 * Move cursor one character forward (logical direction).
 *
 * In left-to-right text this is the same as [cursorCharRight].
 * The distinction matters in bidirectional text where forward
 * follows document order rather than visual order.
 */
val cursorCharForward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ -> moveByChar(view.state, sel, forward = true) }
}

/**
 * Move cursor one character backward (logical direction).
 *
 * @see cursorCharForward
 */
val cursorCharBackward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ -> moveByChar(view.state, sel, forward = false) }
}

/**
 * Move cursor one character forward by logical string index,
 * using grapheme cluster breaks for boundary detection.
 */
val cursorCharForwardLogical: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        byCharLogical(view.state, sel, forward = true, extend = false)
    }
}

/**
 * Move cursor one character backward by logical string index,
 * using grapheme cluster breaks for boundary detection.
 */
val cursorCharBackwardLogical: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        byCharLogical(view.state, sel, forward = false, extend = false)
    }
}

/** Move cursor one word group to the left. */
val cursorGroupLeft: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ -> moveByGroup(view.state, sel, forward = false) }
}

/** Move cursor one word group to the right. */
val cursorGroupRight: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ -> moveByGroup(view.state, sel, forward = true) }
}

/**
 * Move cursor one word group forward (logical direction).
 *
 * @see cursorGroupLeft
 */
val cursorGroupForward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ -> moveByGroup(view.state, sel, forward = true) }
}

/**
 * Move cursor one word group backward (logical direction).
 *
 * @see cursorGroupLeft
 */
val cursorGroupBackward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ -> moveByGroup(view.state, sel, forward = false) }
}

/**
 * Move cursor to the start of the next word group (Windows
 * convention). Unlike [cursorGroupForward] which stops at the
 * end of the current group, this skips past trailing whitespace
 * and stops at the beginning of the next word.
 */
val cursorGroupForwardWin: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveToGroupStart(view.state, sel, forward = true, extend = false)
    }
}

/**
 * Move cursor to the nearest syntax boundary in the left
 * direction. Tries bracket matching first; if no bracket
 * match is found, walks the syntax tree to find the nearest
 * enclosing node boundary.
 */
val cursorSyntaxLeft: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveBySyntax(view.state, sel, forward = false)
    }
}

/**
 * Move cursor to the nearest syntax boundary in the right
 * direction.
 *
 * @see cursorSyntaxLeft
 */
val cursorSyntaxRight: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveBySyntax(view.state, sel, forward = true)
    }
}

/** Move cursor one line up. */
val cursorLineUp: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, v -> moveVertically(v, sel, forward = false) }
}

/** Move cursor one line down. */
val cursorLineDown: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, v -> moveVertically(v, sel, forward = true) }
}

/** Move cursor to the start of the current line. */
val cursorLineStart: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        EditorSelection.cursor(line.from)
    }
}

/** Move cursor to the end of the current line. */
val cursorLineEnd: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        EditorSelection.cursor(line.to)
    }
}

/**
 * Move cursor to the line boundary in the forward direction.
 * In the absence of soft line wrapping, this is equivalent to
 * [cursorLineEnd].
 */
val cursorLineBoundaryForward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        EditorSelection.cursor(line.to)
    }
}

/**
 * Move cursor to the line boundary in the backward direction.
 *
 * Implements "smart home": if the cursor is not already at the first
 * non-whitespace character on the line, it moves there instead of to
 * column 0. If the cursor IS at the first non-whitespace character
 * (or the line has no leading whitespace), it moves to column 0.
 */
val cursorLineBoundaryBackward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        // Count leading whitespace
        val lineText = view.state.doc.sliceString(
            line.from,
            DocPos(minOf(line.from.value + 100, line.to.value))
        )
        val indent = lineText.takeWhile { it == ' ' || it == '\t' }.length
        if (indent > 0 && sel.head != line.from + indent) {
            EditorSelection.cursor(line.from + indent)
        } else {
            EditorSelection.cursor(line.from)
        }
    }
}

/**
 * Move cursor to the line boundary on the left side (visual
 * direction). Same as [cursorLineBoundaryBackward] in
 * left-to-right text.
 */
val cursorLineBoundaryLeft: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        EditorSelection.cursor(line.from)
    }
}

/**
 * Move cursor to the line boundary on the right side (visual
 * direction). Same as [cursorLineBoundaryForward] in
 * left-to-right text.
 */
val cursorLineBoundaryRight: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        EditorSelection.cursor(line.to)
    }
}

/** Move cursor to the start of the document. */
val cursorDocStart: (EditorSession) -> Boolean = { view ->
    updateSel(view) { _, _ -> EditorSelection.cursor(DocPos.ZERO) }
}

/** Move cursor to the end of the document. */
val cursorDocEnd: (EditorSession) -> Boolean = { view ->
    updateSel(view) { _, _ -> EditorSelection.cursor(view.state.doc.endPos) }
}

/** Move cursor one page up. */
val cursorPageUp: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, v ->
        moveByPage(v, sel, forward = false)
    }
}

/** Move cursor one page down. */
val cursorPageDown: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, v ->
        moveByPage(v, sel, forward = true)
    }
}

/**
 * Move the cursor by one page worth of lines.
 *
 * Computes the visible line count from the first and last visible line
 * coordinates. Falls back to a default of 20 lines when coordinates
 * are unavailable.
 */
private fun moveByPage(
    view: EditorSession,
    sel: SelectionRange,
    forward: Boolean,
    extend: Boolean = false
): SelectionRange {
    val doc = view.state.doc
    val currentLine = doc.lineAt(sel.head)
    val goalCol = sel.goalColumn
        ?: (sel.head.value - currentLine.from.value)

    // Try to compute page size from editor coordinates
    val pageLines = estimatePageSize(view, sel) ?: DEFAULT_PAGE_SIZE
    val dir = if (forward) 1 else -1
    val targetLineNum = (currentLine.number.value + dir * pageLines)
        .coerceIn(1, doc.lines)
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

/**
 * Estimate the number of lines visible in one "page" of the editor.
 *
 * Uses coordinates of two lines to determine line height, then divides
 * the total visible area by line height.
 */
private fun estimatePageSize(view: EditorSession, sel: SelectionRange): Int? {
    val doc = view.state.doc
    val line = doc.lineAt(sel.head)
    val coords = view.coordsAtPos(sel.head.value, 1) ?: return null
    val lineHeight = coords.bottom - coords.top
    if (lineHeight <= 0f) return null

    // Get the position of the first line to compute visible area
    val firstLineCoords = view.coordsAtPos(0, 1) ?: return null
    val lastLine = doc.line(LineNumber(doc.lines))
    val lastLineCoords = view.coordsAtPos(lastLine.from.value, 1) ?: return null

    val totalHeight = lastLineCoords.bottom - firstLineCoords.top
    val visibleLines = (totalHeight / lineHeight).toInt().coerceAtLeast(1)
    // Use the smaller of visible lines and document lines as page size
    // Subtract a margin (5 lines like CM6 does: "height - 5")
    return (visibleLines - 1).coerceAtLeast(1)
}

private const val DEFAULT_PAGE_SIZE = 20

// --- Selection commands ---

/** Extend selection one character to the left. */
val selectCharLeft: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveByChar(view.state, sel, forward = false, extend = true)
    }
}

/** Extend selection one character to the right. */
val selectCharRight: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveByChar(view.state, sel, forward = true, extend = true)
    }
}

/** Extend selection one character forward (logical direction). */
val selectCharForward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveByChar(view.state, sel, forward = true, extend = true)
    }
}

/** Extend selection one character backward (logical direction). */
val selectCharBackward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveByChar(view.state, sel, forward = false, extend = true)
    }
}

/**
 * Extend selection one character forward by logical string
 * index, using grapheme cluster breaks.
 */
val selectCharForwardLogical: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        byCharLogical(view.state, sel, forward = true, extend = true)
    }
}

/**
 * Extend selection one character backward by logical string
 * index, using grapheme cluster breaks.
 */
val selectCharBackwardLogical: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        byCharLogical(view.state, sel, forward = false, extend = true)
    }
}

/** Extend selection one word group to the left. */
val selectGroupLeft: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveByGroup(view.state, sel, forward = false, extend = true)
    }
}

/** Extend selection one word group to the right. */
val selectGroupRight: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveByGroup(view.state, sel, forward = true, extend = true)
    }
}

/** Extend selection one word group forward (logical direction). */
val selectGroupForward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveByGroup(view.state, sel, forward = true, extend = true)
    }
}

/** Extend selection one word group backward (logical direction). */
val selectGroupBackward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveByGroup(view.state, sel, forward = false, extend = true)
    }
}

/**
 * Extend selection to the start of the next word group
 * (Windows convention).
 *
 * @see cursorGroupForwardWin
 */
val selectGroupForwardWin: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveToGroupStart(view.state, sel, forward = true, extend = true)
    }
}

/** Extend selection to the nearest syntax boundary on the left. */
val selectSyntaxLeft: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveBySyntax(view.state, sel, forward = false, extend = true)
    }
}

/** Extend selection to the nearest syntax boundary on the right. */
val selectSyntaxRight: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveBySyntax(view.state, sel, forward = true, extend = true)
    }
}

/** Extend selection one line up. */
val selectLineUp: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, v ->
        moveVertically(v, sel, forward = false, extend = true)
    }
}

/** Extend selection one line down. */
val selectLineDown: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, v ->
        moveVertically(v, sel, forward = true, extend = true)
    }
}

/** Extend selection to the start of the current line. */
val selectLineStart: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        EditorSelection.range(sel.anchor, line.from)
    }
}

/** Extend selection to the end of the current line. */
val selectLineEnd: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        EditorSelection.range(sel.anchor, line.to)
    }
}

/** Extend selection to the line boundary in the forward direction. */
val selectLineBoundaryForward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        EditorSelection.range(sel.anchor, line.to)
    }
}

/**
 * Extend selection to the line boundary in the backward direction.
 *
 * Uses the same "smart home" logic as [cursorLineBoundaryBackward].
 */
val selectLineBoundaryBackward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        val lineText = view.state.doc.sliceString(
            line.from,
            DocPos(minOf(line.from.value + 100, line.to.value))
        )
        val indent = lineText.takeWhile { it == ' ' || it == '\t' }.length
        val target = if (indent > 0 && sel.head != line.from + indent) {
            line.from + indent
        } else {
            line.from
        }
        EditorSelection.range(sel.anchor, target)
    }
}

/** Extend selection to the line boundary on the left side (visual). */
val selectLineBoundaryLeft: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        EditorSelection.range(sel.anchor, line.from)
    }
}

/** Extend selection to the line boundary on the right side (visual). */
val selectLineBoundaryRight: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        EditorSelection.range(sel.anchor, line.to)
    }
}

/** Extend selection one page up. */
val selectPageUp: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, v ->
        moveByPage(v, sel, forward = false, extend = true)
    }
}

/** Extend selection one page down. */
val selectPageDown: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, v ->
        moveByPage(v, sel, forward = true, extend = true)
    }
}

/** Extend selection to the start of the document. */
val selectDocStart: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        EditorSelection.range(sel.anchor, DocPos.ZERO)
    }
}

/** Extend selection to the end of the document. */
val selectDocEnd: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        EditorSelection.range(sel.anchor, view.state.doc.endPos)
    }
}

/** Select the entire document. */
val selectAll: (EditorSession) -> Boolean = { view ->
    view.dispatch(
        TransactionSpec(
            selection = SelectionSpec.EditorSelectionSpec(
                EditorSelection.single(DocPos.ZERO, view.state.doc.endPos)
            ),
            userEvent = "select"
        )
    )
    true
}

/** Expand selection to cover full lines. */
val selectLine: (EditorSession) -> Boolean = { view ->
    val state = view.state
    val newRanges = state.selection.ranges.map { sel ->
        val startLine = state.doc.lineAt(sel.from)
        val endLine = state.doc.lineAt(sel.to)
        val to = if (endLine.number.value < state.doc.lines) {
            endLine.to + 1 // include line break
        } else {
            endLine.to
        }
        EditorSelection.range(startLine.from, to)
    }
    view.dispatch(
        TransactionSpec(
            selection = SelectionSpec.EditorSelectionSpec(
                EditorSelection.create(newRanges, state.selection.mainIndex)
            ),
            userEvent = "select"
        )
    )
    true
}

// --- Deletion commands ---

/** Delete one character backward (Backspace). */
val deleteCharBackward: (EditorSession) -> Boolean = { view ->
    deleteBy(view, forward = false) { state, sel ->
        val line = state.doc.lineAt(sel.head)
        val lineOffset = sel.head - line.from
        val prevOffset = findClusterBreak(line.text, lineOffset, forward = false)
        line.from + prevOffset
    }
}

/** Delete one character forward (Delete key). */
val deleteCharForward: (EditorSession) -> Boolean = { view ->
    deleteBy(view, forward = true) { state, sel ->
        val line = state.doc.lineAt(sel.head)
        val lineOffset = sel.head - line.from
        if (lineOffset >= line.text.length) {
            // At end of line, delete the line break
            (sel.head + 1).coerceAtMost(state.doc.endPos)
        } else {
            val nextOffset = findClusterBreak(line.text, lineOffset, forward = true)
            line.from + nextOffset
        }
    }
}

/**
 * Delete one character backward without indent-unit awareness.
 *
 * Unlike [deleteCharBackward], which may delete an entire indent
 * unit of whitespace, this always deletes exactly one grapheme
 * cluster.
 */
val deleteCharBackwardStrict: (EditorSession) -> Boolean = { view ->
    deleteBy(view, forward = false) { state, sel ->
        val line = state.doc.lineAt(sel.head)
        val lineOffset = sel.head - line.from
        val prevOffset = findClusterBreak(line.text, lineOffset, forward = false)
        line.from + prevOffset
    }
}

/** Delete one word group backward. */
val deleteGroupBackward: (EditorSession) -> Boolean = { view ->
    deleteByGroup(view, forward = false)
}

/** Delete one word group forward. */
val deleteGroupForward: (EditorSession) -> Boolean = { view ->
    deleteByGroup(view, forward = true)
}

/**
 * Shared implementation for [deleteGroupBackward] and [deleteGroupForward].
 * Matches the upstream CM6 `deleteByGroup` logic, using [findClusterBreak]
 * for proper Unicode cluster handling and skipping groups of whitespace
 * only when they consist of a single space.
 */
private fun deleteByGroup(view: EditorSession, forward: Boolean): Boolean =
    deleteBy(view, forward) { state, sel ->
        var pos = sel.head
        val line = state.doc.lineAt(pos)
        val categorize = state.charCategorizer(pos)
        var cat: CharCategory? = null
        while (true) {
            if (pos == (if (forward) line.to else line.from)) {
                if (pos == sel.head &&
                    line.number != (
                        if (forward) LineNumber(state.doc.lines) else LineNumber.FIRST
                        )
                ) {
                    pos += if (forward) 1 else -1
                }
                break
            }
            val next = DocPos(
                findClusterBreak(line.text, (pos - line.from), forward) +
                    line.from.value
            )
            val nextCharFrom = minOf(pos, next)
            val nextCharTo = maxOf(pos, next)
            val nextChar = line.text.substring(
                (nextCharFrom - line.from),
                (nextCharTo - line.from)
            )
            val nextCat = categorize(nextChar)
            if (cat != null && nextCat != cat) break
            if (nextChar != " " || pos != sel.head) cat = nextCat
            pos = next
        }
        pos
    }

/**
 * Delete to the start of the next word group (Windows
 * convention). Deletes through trailing whitespace to the
 * beginning of the next word.
 *
 * @see cursorGroupForwardWin
 */
val deleteGroupForwardWin: (EditorSession) -> Boolean = { view ->
    deleteBy(view, forward = true) { state, sel ->
        moveToGroupStart(state, sel, forward = true, extend = false).head
    }
}

/**
 * Delete to the line boundary in the backward direction.
 * In the absence of soft line wrapping, this is equivalent
 * to [deleteToLineStart].
 */
val deleteLineBoundaryBackward: (EditorSession) -> Boolean = { view ->
    deleteBy(view, forward = false) { state, sel ->
        state.doc.lineAt(sel.head).from
    }
}

/**
 * Delete to the line boundary in the forward direction.
 * In the absence of soft line wrapping, this is equivalent
 * to [deleteToLineEnd].
 */
val deleteLineBoundaryForward: (EditorSession) -> Boolean = { view ->
    deleteBy(view, forward = true) { state, sel ->
        state.doc.lineAt(sel.head).to
    }
}

/** Delete from cursor to the start of the line. */
val deleteToLineStart: (EditorSession) -> Boolean = { view ->
    deleteBy(view, forward = false) { state, sel ->
        state.doc.lineAt(sel.head).from
    }
}

/** Delete from cursor to the end of the line. */
val deleteToLineEnd: (EditorSession) -> Boolean = { view ->
    deleteBy(view, forward = true) { state, sel ->
        state.doc.lineAt(sel.head).to
    }
}

/** Delete the selected line(s), including a line break. */
val deleteLine: (EditorSession) -> Boolean = { view ->
    val state = view.state
    if (state.readOnly) {
        false
    } else {
        val docEnd = state.doc.endPos
        val changes = state.changes(
            ChangeSpec.Multi(
                selectedLineBlocks(state).map { block ->
                    // Take the line break before the block, or after it
                    // when the block starts the document.
                    if (block.from > DocPos.ZERO) {
                        ChangeSpec.Single(block.from - 1, block.to)
                    } else if (block.to < docEnd) {
                        ChangeSpec.Single(block.from, block.to + 1)
                    } else {
                        ChangeSpec.Single(block.from, block.to)
                    }
                }
            )
        )
        val selection = EditorSelection.create(
            state.selection.ranges.map { moveVertically(view, it, forward = true) },
            state.selection.mainIndex
        ).map(changes)
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Set(changes),
                selection = SelectionSpec.EditorSelectionSpec(selection),
                scrollIntoView = true,
                userEvent = "delete.line",
                annotations = listOf(Transaction.addToHistory.of(true))
            )
        )
        true
    }
}

/**
 * Remove trailing whitespace from every line in the document.
 *
 * This command does not require a selection — it operates on the
 * entire document.
 */
val deleteTrailingWhitespace: (EditorSession) -> Boolean = { view ->
    val state = view.state
    if (state.readOnly) {
        false
    } else {
        val trailingWs = Regex("\\s+$")
        val changes = mutableListOf<ChangeSpec>()
        for (i in 1..state.doc.lines) {
            val line = state.doc.line(LineNumber(i))
            val match = trailingWs.find(line.text)
            if (match != null) {
                changes.add(
                    ChangeSpec.Single(line.from + match.range.first, line.to)
                )
            }
        }
        if (changes.isEmpty()) {
            false
        } else {
            view.dispatch(
                TransactionSpec(
                    changes = ChangeSpec.Multi(changes),
                    userEvent = "delete"
                )
            )
            true
        }
    }
}

/**
 * Helper for deletion commands. If selection is non-empty, deletes the
 * selection. Otherwise, calls [target] to find the deletion boundary.
 */
private fun deleteBy(
    view: EditorSession,
    forward: Boolean,
    target: (EditorState, SelectionRange) -> DocPos
): Boolean {
    val state = view.state
    if (state.readOnly) return false
    val event = if (forward) "delete.forward" else "delete.backward"
    val spec = state.changeByRange { sel ->
        if (!sel.empty) {
            ChangeByRangeResult(
                range = EditorSelection.cursor(sel.from),
                changes = ChangeSpec.Single(sel.from, sel.to)
            )
        } else {
            val boundary = target(state, sel)
            val from = minOf(sel.head, boundary)
            val to = maxOf(sel.head, boundary)
            if (from == to) {
                // Handle cross-line deletion for backspace at line start
                val crossLine = if (forward) {
                    (sel.head + 1).coerceAtMost(state.doc.endPos)
                } else {
                    (sel.head - 1).coerceAtLeast(DocPos.ZERO)
                }
                if (crossLine == sel.head) {
                    ChangeByRangeResult(range = sel)
                } else {
                    val newFrom = minOf(sel.head, crossLine)
                    val newTo = maxOf(sel.head, crossLine)
                    ChangeByRangeResult(
                        range = EditorSelection.cursor(newFrom),
                        changes = ChangeSpec.Single(newFrom, newTo)
                    )
                }
            } else {
                ChangeByRangeResult(
                    range = EditorSelection.cursor(from),
                    changes = ChangeSpec.Single(from, to)
                )
            }
        }
    }
    view.dispatch(
        spec.copy(
            scrollIntoView = true,
            userEvent = event,
            annotations = listOf(Transaction.addToHistory.of(true))
        )
    )
    return true
}

// --- Text insertion commands ---

/** Insert a newline. */
val insertNewline: (EditorSession) -> Boolean = { view ->
    if (view.state.readOnly) {
        false
    } else {
        view.dispatch(
            view.state.replaceSelection(view.state.lineBreak).copy(
                scrollIntoView = true,
                userEvent = "input.type"
            )
        )
        true
    }
}

/**
 * Check if the cursor is between matching bracket characters
 * (e.g., `{}`, `[]`, `()`). Returns true if bracket explosion
 * should occur when Enter is pressed.
 */
private fun isBetweenBrackets(state: EditorState, pos: DocPos): Boolean {
    val line = state.doc.lineAt(pos)
    val col = pos.value - line.from.value
    if (col <= 0 || col >= line.text.length) return false
    val pair = "${line.text[col - 1]}${line.text[col]}"
    return pair == "()" || pair == "[]" || pair == "{}"
}

/**
 * Insert a newline and indent using language-aware indentation when
 * available, falling back to copying leading whitespace.
 *
 * When the cursor is between matching brackets (e.g., `{|}`),
 * performs "bracket explosion": inserts two newlines so the closing
 * bracket ends up on its own line, with the cursor indented on the
 * middle line.
 */
val insertNewlineAndIndent: (EditorSession) -> Boolean = { view ->
    if (view.state.readOnly) {
        false
    } else {
        val state = view.state
        val spec = state.changeByRange { sel ->
            val line = state.doc.lineAt(sel.head)
            val fromEqTo = sel.from == sel.to
            val between = isBetweenBrackets(state, sel.from)
            val explode = fromEqTo && between
            val cols = getIndentation(
                state,
                sel.head,
                simulateBreak = sel.head,
                simulateDoubleBreak = explode
            )
            val indent = if (cols != null) {
                indentString(state, cols)
            } else {
                line.text.takeWhile { it == ' ' || it == '\t' }
            }
            if (explode) {
                val closingIndent =
                    line.text.takeWhile { it == ' ' || it == '\t' }
                val insert =
                    state.lineBreak + indent + state.lineBreak + closingIndent
                ChangeByRangeResult(
                    range = EditorSelection.cursor(
                        sel.from + state.lineBreak.length + indent.length
                    ),
                    changes = ChangeSpec.Single(
                        sel.from,
                        sel.to,
                        InsertContent.StringContent(insert)
                    )
                )
            } else {
                val insert = state.lineBreak + indent
                ChangeByRangeResult(
                    range = EditorSelection.cursor(sel.from + insert.length),
                    changes = ChangeSpec.Single(
                        sel.from,
                        sel.to,
                        InsertContent.StringContent(insert)
                    )
                )
            }
        }
        view.dispatch(
            spec.copy(
                scrollIntoView = true,
                userEvent = "input"
            )
        )
        true
    }
}

/**
 * Insert a newline and auto-indent. This is an alias for
 * [insertNewlineAndIndent], exported as a separate name to match
 * the upstream CodeMirror API.
 */
val newlineAndIndent: (EditorSession) -> Boolean = insertNewlineAndIndent

/**
 * Insert a tab character at the cursor, or indent the selected lines
 * with [indentMore] when any range is non-empty.
 */
val insertTab: (EditorSession) -> Boolean = { view ->
    if (view.state.readOnly) {
        false
    } else if (view.state.selection.ranges.any { !it.empty }) {
        indentMore(view)
    } else {
        view.dispatch(
            view.state.replaceSelection("\t").copy(
                scrollIntoView = true,
                userEvent = "input.type"
            )
        )
        true
    }
}

/**
 * Insert a newline, but keep the cursor before it
 * (splits the line without moving cursor down).
 */
val splitLine: (EditorSession) -> Boolean = { view ->
    if (view.state.readOnly) {
        false
    } else {
        val state = view.state
        val spec = state.changeByRange { sel ->
            ChangeByRangeResult(
                range = EditorSelection.cursor(sel.from),
                changes = ChangeSpec.Single(
                    sel.from,
                    sel.to,
                    InsertContent.StringContent(state.lineBreak)
                )
            )
        }
        view.dispatch(
            spec.copy(
                scrollIntoView = true,
                userEvent = "input.type"
            )
        )
        true
    }
}

/** Swap the characters (grapheme clusters) before and after the cursor. */
val transposeChars: (EditorSession) -> Boolean = { view ->
    val state = view.state
    val canTranspose: (SelectionRange) -> Boolean = {
        it.empty && it.from > DocPos.ZERO && it.from < state.doc.endPos
    }
    if (state.readOnly || state.selection.ranges.none(canTranspose)) {
        false
    } else {
        val spec = state.changeByRange { sel ->
            if (!canTranspose(sel)) {
                ChangeByRangeResult(range = sel)
            } else {
                val pos = sel.from
                val line = state.doc.lineAt(pos)
                val col = pos - line.from
                val from = if (pos == line.from) {
                    pos - 1
                } else {
                    line.from + findClusterBreak(line.text, col, forward = false)
                }
                val to = if (pos == line.to) {
                    pos + 1
                } else {
                    line.from + findClusterBreak(line.text, col, forward = true)
                }
                ChangeByRangeResult(
                    range = EditorSelection.cursor(to),
                    changes = ChangeSpec.Single(
                        from,
                        to,
                        InsertContent.StringContent(
                            state.sliceDoc(pos, to) + state.sliceDoc(from, pos)
                        )
                    )
                )
            }
        }
        view.dispatch(
            spec.copy(
                scrollIntoView = true,
                userEvent = "input.type"
            )
        )
        true
    }
}

// --- Simplify / collapse selection ---

/** Collapse each non-empty selection range to a cursor at its anchor. */
val simplifySelection: (EditorSession) -> Boolean = { view ->
    val state = view.state
    val hasNonEmpty = state.selection.ranges.any { !it.empty }
    if (!hasNonEmpty) {
        false
    } else {
        val newRanges = state.selection.ranges.map { sel ->
            EditorSelection.cursor(sel.anchor)
        }
        view.dispatch(
            TransactionSpec(
                selection = SelectionSpec.EditorSelectionSpec(
                    EditorSelection.create(newRanges, state.selection.mainIndex)
                ),
                scrollIntoView = true,
                userEvent = "select"
            )
        )
        true
    }
}

// --- Multi-cursor commands ---

/**
 * Add a cursor on the line above each existing cursor head.
 * Creates a new selection range at the vertically projected
 * position, keeping all existing selections.
 */
val addCursorAbove: (EditorSession) -> Boolean = { view ->
    addCursorVertically(view, forward = false)
}

/**
 * Add a cursor on the line below each existing cursor head.
 *
 * @see addCursorAbove
 */
val addCursorBelow: (EditorSession) -> Boolean = { view ->
    addCursorVertically(view, forward = true)
}

// --- Blank line insertion ---

/** Insert an empty line below the current line. */
val insertBlankLine: (EditorSession) -> Boolean = { view ->
    if (view.state.readOnly) {
        false
    } else {
        val state = view.state
        val spec = state.changeByRange { sel ->
            val line = state.doc.lineAt(sel.head)
            val insertPos = line.to
            val insert = state.lineBreak
            ChangeByRangeResult(
                range = EditorSelection.cursor(insertPos + insert.length),
                changes = ChangeSpec.Single(
                    insertPos,
                    insertPos,
                    InsertContent.StringContent(insert)
                )
            )
        }
        view.dispatch(
            spec.copy(
                scrollIntoView = true,
                userEvent = "input.type"
            )
        )
        true
    }
}

/**
 * Insert a newline and copy the leading whitespace from the current line
 * without any smart indentation logic.
 */
val insertNewlineKeepIndent: (EditorSession) -> Boolean = { view ->
    if (view.state.readOnly) {
        false
    } else {
        val state = view.state
        val spec = state.changeByRange { sel ->
            val line = state.doc.lineAt(sel.head)
            val indent = line.text.takeWhile { it == ' ' || it == '\t' }
            val insert = state.lineBreak + indent
            ChangeByRangeResult(
                range = EditorSelection.cursor(sel.from + insert.length),
                changes = ChangeSpec.Single(
                    sel.from,
                    sel.to,
                    InsertContent.StringContent(insert)
                )
            )
        }
        view.dispatch(
            spec.copy(
                scrollIntoView = true,
                userEvent = "input.type"
            )
        )
        true
    }
}

// --- Subword movement commands ---

/** Move cursor one subword forward (camelCase-aware). */
val cursorSubwordForward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveBySubword(view.state, sel, forward = true)
    }
}

/** Move cursor one subword backward (camelCase-aware). */
val cursorSubwordBackward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveBySubword(view.state, sel, forward = false)
    }
}

/** Extend selection one subword forward (camelCase-aware). */
val selectSubwordForward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveBySubword(view.state, sel, forward = true, extend = true)
    }
}

/** Extend selection one subword backward (camelCase-aware). */
val selectSubwordBackward: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveBySubword(view.state, sel, forward = false, extend = true)
    }
}

// --- Bracket matching commands ---

/** Move cursor to the matching bracket. */
val cursorMatchingBracket: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val pos = sel.head
        val state = view.state
        // Try character at cursor first (forward), then before cursor.
        // This matches CM6's priority where the bracket at the cursor
        // position is preferred over the one before it.
        val after = if (pos < state.doc.endPos) matchBrackets(state, pos, 1) else null
        val before = if (pos > DocPos.ZERO) matchBrackets(state, pos - 1, -1) else null
        val match = after ?: before
        val end = match?.end
        if (end != null) {
            EditorSelection.cursor(end.to)
        } else {
            sel
        }
    }
}

/** Extend selection to the matching bracket. */
val selectMatchingBracket: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val pos = sel.head
        val state = view.state
        val after = if (pos < state.doc.endPos) matchBrackets(state, pos, 1) else null
        val before = if (pos > DocPos.ZERO) matchBrackets(state, pos - 1, -1) else null
        val match = after ?: before
        val end = match?.end
        if (end != null) {
            EditorSelection.range(sel.anchor, end.to)
        } else {
            sel
        }
    }
}

/**
 * Select the enclosing syntax node (parent) of the current selection.
 * Repeatedly invoking expands the selection to larger and larger nodes.
 */
val selectParentSyntax: (EditorSession) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val state = view.state
        val tree = syntaxTree(state)
        var node = tree.resolveInner(sel.head.value, 1)
        // Find a node that's larger than the current selection
        while (node.parent != null) {
            if (node.from < sel.from.value || node.to > sel.to.value) {
                break
            }
            node = node.parent ?: break
        }
        EditorSelection.range(DocPos(node.from), DocPos(node.to))
    }
}

/**
 * Select the next occurrence of the current selection text (Ctrl-D style).
 *
 * If the cursor has no selection, selects the current word.
 * If the cursor has a selection, finds and adds the next occurrence
 * of the selected text as an additional selection range.
 */
val selectNextOccurrence: (EditorSession) -> Boolean = { view ->
    val state = view.state
    val sel = state.selection.main
    if (sel.empty) {
        // No selection: select the word at cursor
        val word = state.wordAt(sel.head)
        if (word != null) {
            view.dispatch(
                TransactionSpec(
                    selection = SelectionSpec.EditorSelectionSpec(
                        EditorSelection.single(word.from, word.to)
                    ),
                    userEvent = "select"
                )
            )
            true
        } else {
            false
        }
    } else {
        // Has selection: find next occurrence and add it
        val searchText = state.sliceDoc(sel.from, sel.to)
        val docLen = state.doc.length
        val existingRanges = state.selection.ranges

        // Search forward from the end of the current main selection
        var found: Pair<DocPos, DocPos>? = null
        val searchStart = sel.to
        // Search from current position to end of document
        for (i in searchStart.value..docLen - searchText.length) {
            val pos = DocPos(i)
            if (state.sliceDoc(pos, pos + searchText.length) == searchText) {
                // Make sure this range doesn't overlap an existing selection
                val overlaps = existingRanges.any { r ->
                    pos < r.to && pos + searchText.length > r.from
                }
                if (!overlaps) {
                    found = pos to (pos + searchText.length)
                    break
                }
            }
        }
        // Wrap around if not found
        if (found == null) {
            for (i in 0..searchStart.value - 1) {
                val pos = DocPos(i)
                if (i + searchText.length > docLen) break
                if (state.sliceDoc(pos, pos + searchText.length) == searchText) {
                    val overlaps = existingRanges.any { r ->
                        pos < r.to && pos + searchText.length > r.from
                    }
                    if (!overlaps) {
                        found = pos to (pos + searchText.length)
                        break
                    }
                }
            }
        }

        if (found != null) {
            val newRange = EditorSelection.range(found.first, found.second)
            val allRanges = existingRanges.toList() + newRange
            view.dispatch(
                TransactionSpec(
                    selection = SelectionSpec.EditorSelectionSpec(
                        EditorSelection.create(
                            allRanges,
                            allRanges.size - 1
                        )
                    ),
                    scrollIntoView = true,
                    userEvent = "select"
                )
            )
            true
        } else {
            false
        }
    }
}

// --- Helper functions for new command variants ---

/**
 * Move by character using logical string indices and grapheme
 * cluster breaks for boundary detection.
 */
private fun byCharLogical(
    state: EditorState,
    sel: SelectionRange,
    forward: Boolean,
    extend: Boolean
): SelectionRange {
    val line = state.doc.lineAt(sel.head)
    val lineOffset = sel.head - line.from
    val newOffset = if (forward) {
        if (lineOffset >= line.text.length) {
            // At end of line, move to start of next line
            if (sel.head >= state.doc.endPos) return sel
            val nextLine = state.doc.lineAt(sel.head + 1)
            val head = nextLine.from
            val anchor = if (extend) sel.anchor else head
            return if (extend) {
                EditorSelection.range(anchor, head)
            } else {
                EditorSelection.cursor(head)
            }
        } else {
            findClusterBreak(line.text, lineOffset, forward = true)
        }
    } else {
        if (lineOffset <= 0) {
            // At start of line, move to end of previous line
            if (sel.head <= DocPos.ZERO) return sel
            val prevLine = state.doc.lineAt(sel.head - 1)
            val head = prevLine.to
            val anchor = if (extend) sel.anchor else head
            return if (extend) {
                EditorSelection.range(anchor, head)
            } else {
                EditorSelection.cursor(head)
            }
        } else {
            findClusterBreak(line.text, lineOffset, forward = false)
        }
    }
    val head = line.from + newOffset
    val anchor = if (extend) sel.anchor else head
    return if (extend) {
        EditorSelection.range(anchor, head)
    } else {
        EditorSelection.cursor(head)
    }
}

/**
 * Windows-style word group movement: moves past the current group
 * and any trailing whitespace to stop at the start of the next
 * non-whitespace group.
 */
private fun moveToGroupStart(
    state: EditorState,
    sel: SelectionRange,
    forward: Boolean,
    extend: Boolean
): SelectionRange {
    val endPos = state.doc.endPos
    var pos = sel.head
    val dir = if (forward) 1 else -1

    // First, skip past the current group
    val startGroup = groupAt(state, pos, dir)
    while (true) {
        val next = pos + dir
        if (next < DocPos.ZERO || next > endPos) break
        val group = groupAt(state, next, -dir)
        if (group != startGroup) break
        pos = next
    }

    // Then skip past any whitespace
    while (true) {
        val next = pos + dir
        if (next < DocPos.ZERO || next > endPos) break
        val group = groupAt(state, next, -dir)
        if (group != CharCategory.Space) break
        pos = next
    }

    val anchor = if (extend) sel.anchor else pos
    return if (extend) {
        EditorSelection.range(anchor, pos)
    } else {
        EditorSelection.cursor(pos)
    }
}

/**
 * Navigate by syntax tree structure. Tries bracket matching
 * first; if no bracket match is found, walks up the tree to
 * find the nearest enclosing node whose boundary is in the
 * target direction.
 */
private fun moveBySyntax(
    state: EditorState,
    sel: SelectionRange,
    forward: Boolean,
    extend: Boolean = false
): SelectionRange {
    val pos = sel.head

    // Try bracket matching first
    val before = if (pos > DocPos.ZERO) matchBrackets(state, pos - 1, -1) else null
    val after = if (pos < state.doc.endPos) matchBrackets(state, pos, 1) else null
    val bracketMatch = before ?: after
    if (bracketMatch?.end != null) {
        val target = bracketMatch.end!!.to
        val anchor = if (extend) sel.anchor else target
        return if (extend) {
            EditorSelection.range(anchor, target)
        } else {
            EditorSelection.cursor(target)
        }
    }

    // Walk the syntax tree to find the nearest enclosing node boundary
    val tree = syntaxTree(state)
    var node = tree.resolveInner(pos.value, 1)
    while (node.parent != null) {
        if (forward && node.to > pos.value) break
        if (!forward && node.from < pos.value) break
        node = node.parent ?: break
    }
    val target = DocPos(if (forward) node.to else node.from)
    if (target == pos) return sel
    val anchor = if (extend) sel.anchor else target
    return if (extend) {
        EditorSelection.range(anchor, target)
    } else {
        EditorSelection.cursor(target)
    }
}

/**
 * Add a new cursor above or below each existing cursor head.
 */
private fun addCursorVertically(view: EditorSession, forward: Boolean): Boolean {
    val state = view.state
    val newRanges = mutableListOf<SelectionRange>()
    newRanges.addAll(state.selection.ranges)

    for (sel in state.selection.ranges) {
        val projected = moveVertically(view, sel, forward)
        // Only add if the new position is different from the original
        if (projected.head != sel.head) {
            newRanges.add(EditorSelection.cursor(projected.head))
        }
    }

    if (newRanges.size == state.selection.ranges.size) return false

    val newSel = EditorSelection.create(newRanges, newRanges.size - 1)
    view.dispatch(
        TransactionSpec(
            selection = SelectionSpec.EditorSelectionSpec(newSel),
            scrollIntoView = true,
            userEvent = "select"
        )
    )
    return true
}
