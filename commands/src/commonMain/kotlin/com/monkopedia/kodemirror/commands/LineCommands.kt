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

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionRange
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.endPos
import com.monkopedia.kodemirror.view.EditorSession

/**
 * Move the selected line(s) up by one line.
 */
val moveLineUp: (EditorSession) -> Boolean = { view ->
    moveLines(view, forward = false)
}

/**
 * Move the selected line(s) down by one line.
 */
val moveLineDown: (EditorSession) -> Boolean = { view ->
    moveLines(view, forward = true)
}

/**
 * Copy the selected line(s) upward (duplicate above).
 */
val copyLineUp: (EditorSession) -> Boolean = { view ->
    copyLines(view, forward = false)
}

/**
 * Copy the selected line(s) downward (duplicate below).
 */
val copyLineDown: (EditorSession) -> Boolean = { view ->
    copyLines(view, forward = true)
}

/**
 * A contiguous block of lines covered by one or more selection
 * ranges. Adjacent/overlapping ranges (whose line spans touch) merge
 * into a single block.
 */
private class LineBlock(
    var from: DocPos,
    var to: DocPos,
    val ranges: MutableList<SelectionRange>
)

/**
 * Group all selection ranges into contiguous line-blocks, mirroring
 * upstream `@codemirror/commands` `selectedLineBlocks`.
 */
private fun selectedLineBlocks(state: EditorState): List<LineBlock> {
    val blocks = mutableListOf<LineBlock>()
    var upto = 0
    var seen = false
    for (range in state.selection.ranges) {
        val startLine = state.doc.lineAt(range.from)
        var endLine = state.doc.lineAt(range.to)
        // A non-empty range ending exactly at the start of a line does
        // not include that trailing line.
        if (!range.empty && range.to == endLine.from) {
            endLine = state.doc.lineAt(range.to - 1)
        }
        if (seen && upto >= startLine.number.value) {
            val prev = blocks[blocks.size - 1]
            prev.to = endLine.to
            prev.ranges.add(range)
        } else {
            blocks.add(LineBlock(startLine.from, endLine.to, mutableListOf(range)))
        }
        upto = endLine.number.value
        seen = true
    }
    return blocks
}

private fun moveLines(view: EditorSession, forward: Boolean): Boolean {
    val state = view.state
    if (state.readOnly) return false

    val docEnd = state.doc.endPos
    val changes = mutableListOf<ChangeSpec>()
    val ranges = mutableListOf<SelectionRange>()

    for (block in selectedLineBlocks(state)) {
        // Skip blocks already pinned at the doc edge in the move
        // direction.
        if (if (forward) block.to == docEnd else block.from == DocPos.ZERO) continue

        val nextLine = if (forward) {
            state.doc.lineAt(block.to + 1)
        } else {
            state.doc.lineAt(block.from - 1)
        }
        val size = nextLine.length + state.lineBreak.length

        if (forward) {
            // Delete the following line and re-insert it before the
            // block, sliding the block (and its ranges) up by `size`.
            changes.add(ChangeSpec.Single(block.to, nextLine.to))
            changes.add(
                ChangeSpec.Single(
                    block.from,
                    block.from,
                    InsertContent.StringContent(nextLine.text + state.lineBreak)
                )
            )
            for (r in block.ranges) {
                // Clip at end of doc so the block can't run past it.
                val anchor = (r.anchor + size).coerceAtMost(docEnd)
                val head = (r.head + size).coerceAtMost(docEnd)
                ranges.add(EditorSelection.range(anchor, head))
            }
        } else {
            // Delete the preceding line and re-insert it after the
            // block, sliding the block (and its ranges) down by `size`.
            changes.add(ChangeSpec.Single(nextLine.from, block.from))
            changes.add(
                ChangeSpec.Single(
                    block.to,
                    block.to,
                    InsertContent.StringContent(state.lineBreak + nextLine.text)
                )
            )
            for (r in block.ranges) {
                ranges.add(EditorSelection.range(r.anchor - size, r.head - size))
            }
        }
    }

    if (changes.isEmpty()) return false

    view.dispatch(
        TransactionSpec(
            changes = ChangeSpec.Multi(changes),
            selection = SelectionSpec.EditorSelectionSpec(
                EditorSelection.create(
                    ranges,
                    // An edge block may have been skipped, dropping its
                    // ranges; keep mainIndex in bounds.
                    state.selection.mainIndex.coerceIn(0, ranges.size - 1)
                )
            ),
            scrollIntoView = true,
            userEvent = "move.line",
            annotations = listOf(Transaction.addToHistory.of(true))
        )
    )
    return true
}

private fun copyLines(view: EditorSession, forward: Boolean): Boolean {
    val state = view.state
    if (state.readOnly) return false

    val sel = state.selection.main
    val startLine = state.doc.lineAt(sel.from)
    val endLine = state.doc.lineAt(sel.to)
    val blockText = state.sliceDoc(startLine.from, endLine.to)

    val changes: ChangeSpec

    if (forward) {
        // CM6 copyDown: insert copy BEFORE the block (at startLine.from).
        // The cursor stays at its old position (default mapping) which now
        // points into the copy. The original block shifts down.
        changes = ChangeSpec.Single(
            startLine.from,
            startLine.from,
            InsertContent.StringContent(blockText + state.lineBreak)
        )
    } else {
        // CM6 copyUp: insert copy AFTER the block (at endLine.to).
        // The cursor stays at its old position (default mapping) which
        // still points to the original block.
        changes = ChangeSpec.Single(
            endLine.to,
            endLine.to,
            InsertContent.StringContent(state.lineBreak + blockText)
        )
    }

    // No explicit selection — let the default mapping handle cursor
    // position. The cursor stays at its old position relative to the
    // insertion point.
    view.dispatch(
        TransactionSpec(
            changes = changes,
            scrollIntoView = true,
            userEvent = "input.copyline",
            annotations = listOf(Transaction.addToHistory.of(true))
        )
    )
    return true
}
