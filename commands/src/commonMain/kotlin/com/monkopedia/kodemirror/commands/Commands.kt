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
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionRange
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.findClusterBreak
import com.monkopedia.kodemirror.view.EditorView
import com.monkopedia.kodemirror.view.groupAt
import com.monkopedia.kodemirror.view.moveByChar
import com.monkopedia.kodemirror.view.moveByGroup
import com.monkopedia.kodemirror.view.moveBySubword
import com.monkopedia.kodemirror.view.moveVertically

/**
 * Map over all selection ranges, dispatch the new selection.
 */
private fun updateSel(
    view: EditorView,
    how: (SelectionRange, EditorView) -> SelectionRange
): Boolean {
    val state = view.state
    val newRanges = state.selection.ranges.map { how(it, view) }
    val newSel = EditorSelection.create(newRanges, state.selection.mainIndex)
    if (newSel.eq(state.selection)) return false
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
val cursorCharLeft: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ -> moveByChar(view.state, sel, forward = false) }
}

/** Move cursor one character to the right. */
val cursorCharRight: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ -> moveByChar(view.state, sel, forward = true) }
}

/** Move cursor one word group to the left. */
val cursorGroupLeft: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ -> moveByGroup(view.state, sel, forward = false) }
}

/** Move cursor one word group to the right. */
val cursorGroupRight: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ -> moveByGroup(view.state, sel, forward = true) }
}

/** Move cursor one line up. */
val cursorLineUp: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, v -> moveVertically(v, sel, forward = false) }
}

/** Move cursor one line down. */
val cursorLineDown: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, v -> moveVertically(v, sel, forward = true) }
}

/** Move cursor to the start of the current line. */
val cursorLineStart: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        EditorSelection.cursor(line.from)
    }
}

/** Move cursor to the end of the current line. */
val cursorLineEnd: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        EditorSelection.cursor(line.to)
    }
}

/** Move cursor to the start of the document. */
val cursorDocStart: (EditorView) -> Boolean = { view ->
    updateSel(view) { _, _ -> EditorSelection.cursor(0) }
}

/** Move cursor to the end of the document. */
val cursorDocEnd: (EditorView) -> Boolean = { view ->
    updateSel(view) { _, _ -> EditorSelection.cursor(view.state.doc.length) }
}

/** Move cursor one page up (approximately 20 lines). */
val cursorPageUp: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, v ->
        var result = sel
        repeat(PAGE_SIZE) {
            result = moveVertically(v, result, forward = false)
        }
        result
    }
}

/** Move cursor one page down (approximately 20 lines). */
val cursorPageDown: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, v ->
        var result = sel
        repeat(PAGE_SIZE) {
            result = moveVertically(v, result, forward = true)
        }
        result
    }
}

private const val PAGE_SIZE = 20

// --- Selection commands ---

/** Extend selection one character to the left. */
val selectCharLeft: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveByChar(view.state, sel, forward = false, extend = true)
    }
}

/** Extend selection one character to the right. */
val selectCharRight: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveByChar(view.state, sel, forward = true, extend = true)
    }
}

/** Extend selection one word group to the left. */
val selectGroupLeft: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveByGroup(view.state, sel, forward = false, extend = true)
    }
}

/** Extend selection one word group to the right. */
val selectGroupRight: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveByGroup(view.state, sel, forward = true, extend = true)
    }
}

/** Extend selection one line up. */
val selectLineUp: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, v ->
        moveVertically(v, sel, forward = false, extend = true)
    }
}

/** Extend selection one line down. */
val selectLineDown: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, v ->
        moveVertically(v, sel, forward = true, extend = true)
    }
}

/** Extend selection to the start of the current line. */
val selectLineStart: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        EditorSelection.range(sel.anchor, line.from)
    }
}

/** Extend selection to the end of the current line. */
val selectLineEnd: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val line = view.state.doc.lineAt(sel.head)
        EditorSelection.range(sel.anchor, line.to)
    }
}

/** Extend selection to the start of the document. */
val selectDocStart: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        EditorSelection.range(sel.anchor, 0)
    }
}

/** Extend selection to the end of the document. */
val selectDocEnd: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        EditorSelection.range(sel.anchor, view.state.doc.length)
    }
}

/** Select the entire document. */
val selectAll: (EditorView) -> Boolean = { view ->
    view.dispatch(
        TransactionSpec(
            selection = SelectionSpec.EditorSelectionSpec(
                EditorSelection.single(0, view.state.doc.length)
            ),
            userEvent = "select"
        )
    )
    true
}

/** Expand selection to cover full lines. */
val selectLine: (EditorView) -> Boolean = { view ->
    val state = view.state
    val newRanges = state.selection.ranges.map { sel ->
        val startLine = state.doc.lineAt(sel.from)
        val endLine = state.doc.lineAt(sel.to)
        val to = if (endLine.number < state.doc.lines) {
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
val deleteCharBackward: (EditorView) -> Boolean = { view ->
    deleteBy(view, forward = false) { state, sel ->
        val line = state.doc.lineAt(sel.head)
        val lineOffset = sel.head - line.from
        val prevOffset = findClusterBreak(line.text, lineOffset, forward = false)
        line.from + prevOffset
    }
}

/** Delete one character forward (Delete key). */
val deleteCharForward: (EditorView) -> Boolean = { view ->
    deleteBy(view, forward = true) { state, sel ->
        val line = state.doc.lineAt(sel.head)
        val lineOffset = sel.head - line.from
        if (lineOffset >= line.text.length) {
            // At end of line, delete the line break
            (sel.head + 1).coerceAtMost(state.doc.length)
        } else {
            val nextOffset = findClusterBreak(line.text, lineOffset, forward = true)
            line.from + nextOffset
        }
    }
}

/** Delete one word group backward. */
val deleteGroupBackward: (EditorView) -> Boolean = { view ->
    deleteBy(view, forward = false) { state, sel ->
        var pos = sel.head
        val startGroup = groupAt(state, pos, -1)
        while (pos > 0) {
            val prev = pos - 1
            val group = groupAt(state, prev, -1)
            if (group != startGroup) break
            pos = prev
        }
        pos
    }
}

/** Delete one word group forward. */
val deleteGroupForward: (EditorView) -> Boolean = { view ->
    deleteBy(view, forward = true) { state, sel ->
        var pos = sel.head
        val len = state.doc.length
        val startGroup = groupAt(state, pos, 1)
        while (pos < len) {
            val next = pos + 1
            val group = groupAt(state, next, 1)
            if (group != startGroup) break
            pos = next
        }
        pos
    }
}

/** Delete from cursor to the start of the line. */
val deleteToLineStart: (EditorView) -> Boolean = { view ->
    deleteBy(view, forward = false) { state, sel ->
        state.doc.lineAt(sel.head).from
    }
}

/** Delete from cursor to the end of the line. */
val deleteToLineEnd: (EditorView) -> Boolean = { view ->
    deleteBy(view, forward = true) { state, sel ->
        state.doc.lineAt(sel.head).to
    }
}

/** Delete the entire current line (including line break). */
val deleteLine: (EditorView) -> Boolean = { view ->
    val state = view.state
    if (state.readOnly) {
        false
    } else {
        val spec = state.changeByRange { sel ->
            val startLine = state.doc.lineAt(sel.from)
            val endLine = if (sel.empty) startLine else state.doc.lineAt(sel.to)
            val from = startLine.from
            val to = if (endLine.number < state.doc.lines) {
                endLine.to + 1 // include line break
            } else if (startLine.number > 1) {
                startLine.from - 1 // delete preceding line break
            } else {
                endLine.to
            }
            ChangeByRangeResult(
                range = EditorSelection.cursor(from.coerceAtMost(state.doc.length)),
                changes = ChangeSpec.Single(from, to)
            )
        }
        view.dispatch(
            spec.copy(
                scrollIntoView = true,
                userEvent = "delete.line",
                annotations = listOf(Transaction.addToHistory.of(true))
            )
        )
        true
    }
}

/**
 * Helper for deletion commands. If selection is non-empty, deletes the
 * selection. Otherwise, calls [target] to find the deletion boundary.
 */
private fun deleteBy(
    view: EditorView,
    forward: Boolean,
    target: (EditorState, SelectionRange) -> Int
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
                    (sel.head + 1).coerceAtMost(state.doc.length)
                } else {
                    (sel.head - 1).coerceAtLeast(0)
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
val insertNewline: (EditorView) -> Boolean = { view ->
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
 * Insert a newline and indent using language-aware indentation when
 * available, falling back to copying leading whitespace.
 */
val insertNewlineAndIndent: (EditorView) -> Boolean = { view ->
    if (view.state.readOnly) {
        false
    } else {
        val state = view.state
        val spec = state.changeByRange { sel ->
            val line = state.doc.lineAt(sel.head)
            val cols = getIndentation(state, sel.head)
            val indent = if (cols != null) {
                indentString(state, cols)
            } else {
                line.text.takeWhile { it == ' ' || it == '\t' }
            }
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

/** Insert a tab character or indent unit. */
val insertTab: (EditorView) -> Boolean = { view ->
    if (view.state.readOnly) {
        false
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
val splitLine: (EditorView) -> Boolean = { view ->
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

/** Swap the characters before and after the cursor. */
val transposeChars: (EditorView) -> Boolean = { view ->
    val state = view.state
    if (state.readOnly) {
        false
    } else {
        val spec = state.changeByRange { sel ->
            if (!sel.empty || sel.from == 0 || sel.from >= state.doc.length) {
                ChangeByRangeResult(range = sel)
            } else {
                val pos = sel.from
                val before = state.sliceDoc(pos - 1, pos)
                val after = state.sliceDoc(pos, pos + 1)
                ChangeByRangeResult(
                    range = EditorSelection.cursor(pos + 1),
                    changes = ChangeSpec.Single(
                        pos - 1,
                        pos + 1,
                        InsertContent.StringContent(after + before)
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
val simplifySelection: (EditorView) -> Boolean = { view ->
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

// --- Blank line insertion ---

/** Insert an empty line below the current line. */
val insertBlankLine: (EditorView) -> Boolean = { view ->
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
val insertNewlineKeepIndent: (EditorView) -> Boolean = { view ->
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
val cursorSubwordForward: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveBySubword(view.state, sel, forward = true)
    }
}

/** Move cursor one subword backward (camelCase-aware). */
val cursorSubwordBackward: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveBySubword(view.state, sel, forward = false)
    }
}

/** Extend selection one subword forward (camelCase-aware). */
val selectSubwordForward: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveBySubword(view.state, sel, forward = true, extend = true)
    }
}

/** Extend selection one subword backward (camelCase-aware). */
val selectSubwordBackward: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        moveBySubword(view.state, sel, forward = false, extend = true)
    }
}

// --- Bracket matching commands ---

/** Move cursor to the matching bracket. */
val cursorMatchingBracket: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val pos = sel.head
        val state = view.state
        // Try character before cursor
        val before = if (pos > 0) matchBrackets(state, pos - 1, -1) else null
        val after = if (pos < state.doc.length) matchBrackets(state, pos, 1) else null
        val match = before ?: after
        val end = match?.end
        if (end != null) {
            EditorSelection.cursor(end.to)
        } else {
            sel
        }
    }
}

/** Extend selection to the matching bracket. */
val selectMatchingBracket: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val pos = sel.head
        val state = view.state
        val before = if (pos > 0) matchBrackets(state, pos - 1, -1) else null
        val after = if (pos < state.doc.length) matchBrackets(state, pos, 1) else null
        val match = before ?: after
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
val selectParentSyntax: (EditorView) -> Boolean = { view ->
    updateSel(view) { sel, _ ->
        val state = view.state
        val tree = syntaxTree(state)
        var node = tree.resolveInner(sel.head, 1)
        // Find a node that's larger than the current selection
        while (node.parent != null) {
            if (node.from < sel.from || node.to > sel.to) {
                break
            }
            node = node.parent ?: break
        }
        EditorSelection.range(node.from, node.to)
    }
}

/**
 * Select the next occurrence of the current selection text (Ctrl-D style).
 *
 * If the cursor has no selection, selects the current word.
 * If the cursor has a selection, finds and adds the next occurrence
 * of the selected text as an additional selection range.
 */
val selectNextOccurrence: (EditorView) -> Boolean = { view ->
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
        var found: Pair<Int, Int>? = null
        val searchStart = sel.to
        // Search from current position to end of document
        for (pos in searchStart..docLen - searchText.length) {
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
            for (pos in 0..searchStart - 1) {
                if (pos + searchText.length > docLen) break
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
