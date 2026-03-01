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

import com.monkopedia.kodemirror.language.CommentTokens
import com.monkopedia.kodemirror.language.commentTokens
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorView
import com.monkopedia.kodemirror.view.KeyBinding

/**
 * Toggle line comments on selected lines.
 *
 * Adds or removes line comment markers (e.g. `//`) at the start of each
 * selected line. If all lines are commented, uncomments; otherwise comments.
 */
val toggleComment: (EditorView) -> Boolean = { view ->
    val tokens = getCommentTokens(view.state)
    val line = tokens?.line
    val block = tokens?.block
    if (line != null) {
        toggleLineComment(view, line)
    } else if (block != null) {
        toggleBlockCommentCommand(view, block)
    } else {
        false
    }
}

/**
 * Add line comments to selected lines.
 */
val lineComment: (EditorView) -> Boolean = { view ->
    val line = getCommentTokens(view.state)?.line
    if (line != null) {
        applyLineComment(view, line, add = true)
    } else {
        false
    }
}

/**
 * Remove line comments from selected lines.
 */
val lineUncomment: (EditorView) -> Boolean = { view ->
    val line = getCommentTokens(view.state)?.line
    if (line != null) {
        applyLineComment(view, line, add = false)
    } else {
        false
    }
}

/**
 * Toggle block comments around the selection.
 */
val toggleBlockComment: (EditorView) -> Boolean = { view ->
    val block = getCommentTokens(view.state)?.block
    if (block != null) {
        toggleBlockCommentCommand(view, block)
    } else {
        false
    }
}

/**
 * Add a block comment around the selection.
 */
val blockComment: (EditorView) -> Boolean = { view ->
    val block = getCommentTokens(view.state)?.block
    if (block != null) {
        applyBlockComment(view, block, add = true)
    } else {
        false
    }
}

/**
 * Remove a block comment around the selection.
 */
val blockUncomment: (EditorView) -> Boolean = { view ->
    val block = getCommentTokens(view.state)?.block
    if (block != null) {
        applyBlockComment(view, block, add = false)
    } else {
        false
    }
}

/** Key bindings for comment commands. */
val commentKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Ctrl-/", mac = "Meta-/", run = toggleComment)
)

private fun getCommentTokens(state: EditorState): CommentTokens? {
    return state.facet(commentTokens)
}

private fun toggleLineComment(view: EditorView, lineToken: String): Boolean {
    val state = view.state
    if (state.readOnly) return false

    val sel = state.selection.main
    val startLine = state.doc.lineAt(sel.from)
    val endLine = state.doc.lineAt(sel.to)

    // Check if all lines are already commented
    var allCommented = true
    for (lineNum in startLine.number..endLine.number) {
        val line = state.doc.line(lineNum)
        val trimmed = line.text.trimStart()
        if (trimmed.isNotEmpty() && !trimmed.startsWith(lineToken)) {
            allCommented = false
            break
        }
    }

    return applyLineComment(view, lineToken, add = !allCommented)
}

private fun applyLineComment(view: EditorView, lineToken: String, add: Boolean): Boolean {
    val state = view.state
    if (state.readOnly) return false

    val sel = state.selection.main
    val startLine = state.doc.lineAt(sel.from)
    val endLine = state.doc.lineAt(sel.to)

    val changes = mutableListOf<ChangeSpec>()

    for (lineNum in startLine.number..endLine.number) {
        val line = state.doc.line(lineNum)
        if (add) {
            changes.add(
                ChangeSpec.Single(
                    line.from,
                    line.from,
                    InsertContent.StringContent("$lineToken ")
                )
            )
        } else {
            val text = line.text
            val leadingSpace = text.length - text.trimStart().length
            if (text.substring(leadingSpace).startsWith(lineToken)) {
                val removeEnd = leadingSpace + lineToken.length
                // Also remove a trailing space after the comment marker
                val finalEnd = if (
                    removeEnd < text.length && text[removeEnd] == ' '
                ) {
                    removeEnd + 1
                } else {
                    removeEnd
                }
                changes.add(
                    ChangeSpec.Single(line.from + leadingSpace, line.from + finalEnd)
                )
            }
        }
    }

    if (changes.isEmpty()) return false

    view.dispatch(
        TransactionSpec(
            changes = ChangeSpec.Multi(changes),
            scrollIntoView = true,
            userEvent = if (add) "comment.line" else "uncomment.line",
            annotations = listOf(Transaction.addToHistory.of(true))
        )
    )
    return true
}

private fun toggleBlockCommentCommand(
    view: EditorView,
    block: CommentTokens.BlockComment
): Boolean {
    val state = view.state
    if (state.readOnly) return false

    val sel = state.selection.main
    val text = state.sliceDoc(sel.from, sel.to)

    // Check if selection is already wrapped in block comment
    val isCommented = text.startsWith(block.open) && text.endsWith(block.close)

    return applyBlockComment(view, block, add = !isCommented)
}

private fun applyBlockComment(
    view: EditorView,
    block: CommentTokens.BlockComment,
    add: Boolean
): Boolean {
    val state = view.state
    if (state.readOnly) return false

    val sel = state.selection.main
    val changes = mutableListOf<ChangeSpec>()

    if (add) {
        changes.add(
            ChangeSpec.Single(
                sel.from,
                sel.from,
                InsertContent.StringContent(block.open)
            )
        )
        changes.add(
            ChangeSpec.Single(
                sel.to,
                sel.to,
                InsertContent.StringContent(block.close)
            )
        )
    } else {
        val text = state.sliceDoc(sel.from, sel.to)
        if (text.startsWith(block.open) && text.endsWith(block.close)) {
            changes.add(
                ChangeSpec.Single(sel.from, sel.from + block.open.length)
            )
            changes.add(
                ChangeSpec.Single(
                    sel.to - block.close.length,
                    sel.to
                )
            )
        } else {
            return false
        }
    }

    val newSelFrom = sel.from
    val newSelTo = if (add) {
        sel.to + block.open.length + block.close.length
    } else {
        sel.to - block.open.length - block.close.length
    }

    view.dispatch(
        TransactionSpec(
            changes = ChangeSpec.Multi(changes),
            selection = com.monkopedia.kodemirror.state.SelectionSpec.EditorSelectionSpec(
                EditorSelection.single(newSelFrom, newSelTo)
            ),
            scrollIntoView = true,
            userEvent = if (add) "comment.block" else "uncomment.block",
            annotations = listOf(Transaction.addToHistory.of(true))
        )
    )
    return true
}
