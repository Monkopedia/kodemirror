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
import com.monkopedia.kodemirror.state.Line
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding

/**
 * Toggle line comments on selected lines.
 *
 * Adds or removes line comment markers (e.g. `//`) at the start of each
 * selected line. If all lines are commented, uncomments; otherwise comments.
 */
val toggleComment: (EditorSession) -> Boolean = { view ->
    val tokens = getCommentTokens(view.state)
    val line = tokens?.line
    val block = tokens?.block
    if (line != null) {
        changeLineComment(view, line, CommentOption.Toggle)
    } else if (block != null) {
        toggleBlockCommentCommand(view, block)
    } else {
        false
    }
}

/**
 * Add line comments to selected lines.
 */
val lineComment: (EditorSession) -> Boolean = { view ->
    val line = getCommentTokens(view.state)?.line
    if (line != null) {
        changeLineComment(view, line, CommentOption.Comment)
    } else {
        false
    }
}

/**
 * Remove line comments from selected lines.
 */
val lineUncomment: (EditorSession) -> Boolean = { view ->
    val line = getCommentTokens(view.state)?.line
    if (line != null) {
        changeLineComment(view, line, CommentOption.Uncomment)
    } else {
        false
    }
}

/**
 * Toggle block comments around the selection.
 */
val toggleBlockComment: (EditorSession) -> Boolean = { view ->
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
val blockComment: (EditorSession) -> Boolean = { view ->
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
val blockUncomment: (EditorSession) -> Boolean = { view ->
    val block = getCommentTokens(view.state)?.block
    if (block != null) {
        applyBlockComment(view, block, add = false)
    } else {
        false
    }
}

/** Key bindings for comment commands. */
val commentKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Ctrl-/", mac = "Meta-/", run = toggleComment),
    KeyBinding(key = "Alt-Shift-a", run = toggleBlockComment)
)

private fun getCommentTokens(state: EditorState): CommentTokens? = state.facet(commentTokens)

private enum class CommentOption { Toggle, Comment, Uncomment }

private class LineCommentInfo(
    val line: Line,
    val comment: Int,
    var indent: Int,
    val empty: Boolean,
    var single: Boolean = false
)

/**
 * Comment or uncomment every line touched by the selection, mirroring
 * upstream `changeLineComment`: the marker goes at the block's minimum
 * indentation, blank lines inside a multi-line block are skipped, and a
 * range ending at the start of a line does not include that line.
 */
private fun changeLineComment(view: EditorSession, token: String, option: CommentOption): Boolean {
    val state = view.state
    if (state.readOnly) return false

    val lines = mutableListOf<LineCommentInfo>()
    var prevLine = -1
    for (range in state.selection.ranges) {
        val startI = lines.size
        var minIndent = Int.MAX_VALUE
        var pos = range.from
        while (pos <= range.to) {
            val line = state.doc.lineAt(pos)
            if (line.from.value > prevLine && (range.empty || range.to > line.from)) {
                prevLine = line.from.value
                val indent = line.text.takeWhile { it.isWhitespace() }.length
                val comment = if (line.text.startsWith(token, indent)) indent else -1
                if (indent < line.text.length && indent < minIndent) minIndent = indent
                lines.add(LineCommentInfo(line, comment, indent, empty = indent == line.length))
            }
            pos = line.to + 1
        }
        if (minIndent < Int.MAX_VALUE) {
            for (i in startI until lines.size) {
                if (lines[i].indent < lines[i].line.text.length) lines[i].indent = minIndent
            }
        }
        if (lines.size == startI + 1) lines[startI].single = true
    }

    if (
        option != CommentOption.Uncomment &&
        lines.any { it.comment < 0 && (!it.empty || it.single) }
    ) {
        val changes = lines.filter { it.single || !it.empty }.map {
            val at = it.line.from + it.indent
            ChangeSpec.Single(at, at, InsertContent.StringContent("$token "))
        }
        val changeSet = state.changes(ChangeSpec.Multi(changes))
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Set(changeSet),
                selection = SelectionSpec.EditorSelectionSpec(state.selection.map(changeSet, 1)),
                scrollIntoView = true,
                userEvent = "comment.line",
                annotations = listOf(Transaction.addToHistory.of(true))
            )
        )
        return true
    }
    if (option != CommentOption.Comment && lines.any { it.comment >= 0 }) {
        val changes = lines.filter { it.comment >= 0 }.map {
            val from = it.line.from + it.comment
            var to = from + token.length
            if (it.line.text.getOrNull(to - it.line.from) == ' ') to += 1
            ChangeSpec.Single(from, to)
        }
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Multi(changes),
                scrollIntoView = true,
                userEvent = "uncomment.line",
                annotations = listOf(Transaction.addToHistory.of(true))
            )
        )
        return true
    }
    return false
}

private fun toggleBlockCommentCommand(
    view: EditorSession,
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
    view: EditorSession,
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
            selection = SelectionSpec.EditorSelectionSpec(
                EditorSelection.single(newSelFrom, newSelTo)
            ),
            scrollIntoView = true,
            userEvent = if (add) "comment.block" else "uncomment.block",
            annotations = listOf(Transaction.addToHistory.of(true))
        )
    )
    return true
}
