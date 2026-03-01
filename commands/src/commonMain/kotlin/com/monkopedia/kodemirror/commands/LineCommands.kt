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
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorView

/**
 * Move the selected line(s) up by one line.
 */
val moveLineUp: (EditorView) -> Boolean = { view ->
    moveLines(view, forward = false)
}

/**
 * Move the selected line(s) down by one line.
 */
val moveLineDown: (EditorView) -> Boolean = { view ->
    moveLines(view, forward = true)
}

/**
 * Copy the selected line(s) upward (duplicate above).
 */
val copyLineUp: (EditorView) -> Boolean = { view ->
    copyLines(view, forward = false)
}

/**
 * Copy the selected line(s) downward (duplicate below).
 */
val copyLineDown: (EditorView) -> Boolean = { view ->
    copyLines(view, forward = true)
}

private fun moveLines(view: EditorView, forward: Boolean): Boolean {
    val state = view.state
    if (state.readOnly) return false

    val sel = state.selection.main
    val startLine = state.doc.lineAt(sel.from)
    val endLine = state.doc.lineAt(sel.to)

    if (forward && endLine.number >= state.doc.lines) return false
    if (!forward && startLine.number <= 1) return false

    val changes: ChangeSpec
    val newAnchor: Int
    val newHead: Int

    if (forward) {
        val nextLine = state.doc.line(endLine.number + 1)
        val lineText = state.sliceDoc(nextLine.from, nextLine.to)
        // Delete the next line (including its preceding line break)
        // and insert it before the block
        changes = ChangeSpec.Multi(
            listOf(
                ChangeSpec.Single(endLine.to, nextLine.to),
                ChangeSpec.Single(
                    startLine.from,
                    startLine.from,
                    InsertContent.StringContent(lineText + state.lineBreak)
                )
            )
        )
        val lineLen = lineText.length + state.lineBreak.length
        newAnchor = sel.anchor + lineLen
        newHead = sel.head + lineLen
    } else {
        val prevLine = state.doc.line(startLine.number - 1)
        val lineText = state.sliceDoc(prevLine.from, prevLine.to)
        // Delete the previous line (including its trailing line break)
        // and insert it after the block
        changes = ChangeSpec.Multi(
            listOf(
                ChangeSpec.Single(prevLine.from, startLine.from),
                ChangeSpec.Single(
                    endLine.to,
                    endLine.to,
                    InsertContent.StringContent(state.lineBreak + lineText)
                )
            )
        )
        val lineLen = lineText.length + state.lineBreak.length
        newAnchor = sel.anchor - lineLen
        newHead = sel.head - lineLen
    }

    view.dispatch(
        TransactionSpec(
            changes = changes,
            selection = SelectionSpec.CursorSpec(
                newAnchor.coerceAtLeast(0),
                newHead.coerceAtLeast(0)
            ),
            scrollIntoView = true,
            userEvent = "move.line",
            annotations = listOf(Transaction.addToHistory.of(true))
        )
    )
    return true
}

private fun copyLines(view: EditorView, forward: Boolean): Boolean {
    val state = view.state
    if (state.readOnly) return false

    val sel = state.selection.main
    val startLine = state.doc.lineAt(sel.from)
    val endLine = state.doc.lineAt(sel.to)
    val blockText = state.sliceDoc(startLine.from, endLine.to)

    val changes: ChangeSpec
    val newAnchor: Int
    val newHead: Int

    if (forward) {
        // Insert copy after the block
        changes = ChangeSpec.Single(
            endLine.to,
            endLine.to,
            InsertContent.StringContent(state.lineBreak + blockText)
        )
        val offset = endLine.to - startLine.from + state.lineBreak.length
        newAnchor = sel.anchor + offset
        newHead = sel.head + offset
    } else {
        // Insert copy before the block
        changes = ChangeSpec.Single(
            startLine.from,
            startLine.from,
            InsertContent.StringContent(blockText + state.lineBreak)
        )
        // Keep selection on the original lines (which shift down)
        newAnchor = sel.anchor
        newHead = sel.head
    }

    view.dispatch(
        TransactionSpec(
            changes = changes,
            selection = SelectionSpec.CursorSpec(
                newAnchor.coerceAtLeast(0),
                newHead.coerceAtLeast(0)
            ),
            scrollIntoView = true,
            userEvent = "input.copyline",
            annotations = listOf(Transaction.addToHistory.of(true))
        )
    )
    return true
}
