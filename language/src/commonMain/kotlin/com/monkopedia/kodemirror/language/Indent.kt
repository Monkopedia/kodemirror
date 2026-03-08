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
package com.monkopedia.kodemirror.language

import com.monkopedia.kodemirror.lezer.common.NodeProp
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.LanguageDataKey
import com.monkopedia.kodemirror.state.Line
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionFilterResult
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.transactionFilter

/**
 * Facet used to register indent services. An indent service is a
 * function that, given an [IndentContext] and a position, returns
 * the desired indentation (in columns), or null if it has no opinion.
 */
val indentService: Facet<(IndentContext, Int) -> Int?, List<(IndentContext, Int) -> Int?>> =
    Facet.define()

/**
 * Facet for configuring the indent unit (number of spaces per indent
 * level). Defaults to 2.
 */
val indentUnit: Facet<Int, Int> = Facet.define(
    combine = { values -> values.firstOrNull() ?: 4 }
)

/**
 * A node prop that attaches indentation strategies to node types
 * in the syntax tree.
 *
 * The function receives a [TreeIndentContext] and returns the desired
 * indentation in columns, or null to fall through to other strategies.
 */
val indentNodeProp: NodeProp<(TreeIndentContext) -> Int?> = NodeProp()

/**
 * Get the indent unit (number of spaces per level) for the given state.
 */
fun getIndentUnit(state: EditorState): Int = state.facet(indentUnit)

/**
 * Generate an indentation string for the given number of columns,
 * using tabs if the state's [indentUnit] divides evenly, otherwise spaces.
 */
fun indentString(state: EditorState, cols: Int): String {
    val unit = getIndentUnit(state)
    if (cols < 0) return ""
    // Always use spaces for consistency
    return " ".repeat(cols)
}

/**
 * Context object passed to indent services and node prop strategies.
 */
open class IndentContext(
    val state: EditorState,
    val simulateBreak: Int? = null,
    val simulateDoubleBreak: Boolean = false
) {
    /** Get the text of a document line by position. */
    fun lineAt(pos: Int, bias: Int = 1): Line {
        return state.doc.lineAt(pos)
    }

    /** Get the indentation column at the start of a line. */
    fun lineIndent(pos: Int, bias: Int = 1): Int {
        val line = lineAt(pos, bias)
        return countIndent(line.text, state.tabSize)
    }

    /** The indent unit size for this state. */
    val unit: Int get() = getIndentUnit(state)

    /** The text content of the document. */
    val textAfterPos: String get() = ""
}

/**
 * Extended indent context with access to the syntax tree for
 * tree-based indentation strategies.
 */
class TreeIndentContext(
    state: EditorState,
    val pos: Int,
    simulateBreak: Int? = null,
    simulateDoubleBreak: Boolean = false
) : IndentContext(state, simulateBreak, simulateDoubleBreak) {
    /**
     * The current context node. Initially the innermost node at the indent
     * position, but updated by [indentFrom] to the node bearing the indent
     * strategy before the strategy function is called.
     */
    var node: SyntaxNode = syntaxTree(state).resolveInner(pos)
        internal set

    /** Get the column of a given position. */
    fun column(pos: Int, bias: Int = 1): Int {
        val line = state.doc.lineAt(pos)
        val offset = pos - line.from
        return countIndent(line.text.substring(0, offset), state.tabSize)
    }

    /** The text on the line after the indent position. */
    val textAfter: String
        get() {
            val line = state.doc.lineAt(pos)
            return line.text.substring(pos - line.from)
        }

    /** The base indentation of the line the node starts on. */
    val baseIndent: Int
        get() = lineIndent(node.from)

    /** The base indentation from the node itself. */
    val baseIndentFor: (SyntaxNode) -> Int = { n ->
        lineIndent(n.from)
    }

    /**
     * Skip to parent node's indent strategy. Use this when a node's
     * strategy doesn't want to handle the current case and defers to
     * an ancestor.
     */
    fun continueAt(): Int? = indentFrom(node.parent, pos, this)
}

/**
 * Query the indentation for a position using registered indent services
 * and tree-based indentation strategies.
 *
 * Returns the desired indentation in columns, or null if no service
 * or strategy provides an answer.
 */
fun getIndentation(state: EditorState, pos: Int): Int? {
    val context = IndentContext(state)
    // Try indent services first
    for (service in state.facet(indentService)) {
        val result = service(context, pos)
        if (result != null) return result
    }

    // Try tree-based indentation
    val tree = syntaxTree(state)
    if (tree.length > 0) {
        return getTreeIndent(TreeIndentContext(state, pos))
    }

    return null
}

private fun getTreeIndent(cx: TreeIndentContext): Int? {
    return indentFrom(cx.node, cx.pos, cx)
}

internal fun indentFrom(start: SyntaxNode?, pos: Int, cx: TreeIndentContext): Int? {
    var node = start
    while (node != null) {
        val strategy = indentStrategy(node)
        if (strategy != null) {
            cx.node = node
            return strategy(cx)
        }
        node = node.parent
    }
    return 0
}

private fun indentStrategy(node: SyntaxNode): ((TreeIndentContext) -> Int?)? {
    val strategy = node.type.prop(indentNodeProp)
    if (strategy != null) return strategy
    val first = node.firstChild
    val close = first?.type?.prop(NodeProp.closedBy)
    if (first != null && close != null) {
        val last = node.lastChild
        val closedAt = if (last != null && close.contains(last.name)) last.from else null
        return { cx ->
            delimitedStrategy(
                cx,
                align = true,
                units = 1,
                closing = null,
                closedAt = if (closedAt != null && !ignoreClosed(cx)) closedAt else null
            )
        }
    }
    return if (node.parent == null) { _ -> 0 } else null
}

private fun ignoreClosed(cx: TreeIndentContext): Boolean {
    return cx.pos == cx.simulateBreak && cx.simulateDoubleBreak
}

private fun bracketedAligned(context: TreeIndentContext): Pair<Int, Int>? {
    val tree = context.node
    val openToken = tree.childAfter(tree.from) ?: return null
    val last = tree.lastChild
    val sim = context.simulateBreak
    val openLine = context.state.doc.lineAt(openToken.from)
    val lineEnd = if (sim == null || sim <= openLine.from) {
        openLine.to
    } else {
        minOf(openLine.to, sim)
    }
    var pos = openToken.to
    while (true) {
        val next = tree.childAfter(pos) ?: return null
        if (last != null && next.from >= last.from) return null
        if (!next.type.isSkipped) {
            if (next.from >= lineEnd) return null
            val afterOpen = openLine.text.substring(openToken.to - openLine.from)
            val space = afterOpen.takeWhile { it == ' ' }.length
            return Pair(openToken.from, openToken.to + space)
        }
        pos = next.to
    }
}

internal fun delimitedStrategy(
    context: TreeIndentContext,
    align: Boolean,
    units: Int,
    closing: String? = null,
    closedAt: Int? = null
): Int {
    val after = context.textAfter
    val space = after.takeWhile { it == ' ' || it == '\t' }.length
    val closed = (
        closing != null &&
            after.length >= space + closing.length &&
            after.substring(space, space + closing.length) == closing
        ) ||
        closedAt == context.pos + space
    val aligned = if (align) bracketedAligned(context) else null
    if (aligned != null) {
        return if (closed) {
            context.column(aligned.first)
        } else {
            context.column(aligned.second)
        }
    }
    return context.baseIndent + if (closed) 0 else context.unit * units
}

/**
 * Generate a [ChangeSpec.Multi] that re-indents every line in the
 * given range.
 */
fun indentRange(state: EditorState, from: Int, to: Int): ChangeSpec? {
    val changes = mutableListOf<ChangeSpec>()
    val startLine = state.doc.lineAt(from)
    val endLine = state.doc.lineAt(to)

    for (lineNum in startLine.number..endLine.number) {
        val line = state.doc.line(lineNum)
        val desired = getIndentation(state, line.from) ?: continue
        val current = countIndent(line.text, state.tabSize)
        if (current == desired) continue

        val currentLen = currentIndentLength(line.text)
        val newIndent = indentString(state, desired)
        changes.add(
            ChangeSpec.Single(
                line.from,
                line.from + currentLen,
                InsertContent.StringContent(newIndent)
            )
        )
    }

    return if (changes.isEmpty()) null else ChangeSpec.Multi(changes)
}

/**
 * Standard indent strategy: indent one level inside delimiters.
 *
 * @param closing  The closing delimiter character to detect for outdent.
 * @param align    Whether to align content with the opening delimiter.
 * @param units    Number of indent units to add (default 1).
 */
fun delimitedIndent(
    closing: String? = null,
    align: Boolean = true,
    units: Int = 1
): (TreeIndentContext) -> Int? {
    return { cx ->
        delimitedStrategy(cx, align, units, closing)
    }
}

/**
 * Standard indent strategy: continue the indentation of the previous line,
 * optionally with additional units.
 *
 * @param units Number of indent units to add.
 * @param except When the text after the cursor matches this regex, return
 *   [baseIndent] without extra indentation.
 */
fun continuedIndent(units: Int = 1, except: Regex? = null): (TreeIndentContext) -> Int? {
    return { cx ->
        val unit = getIndentUnit(cx.state)
        if (except != null && except.containsMatchIn(cx.textAfter)) {
            cx.baseIndent
        } else {
            cx.baseIndent + unit * units
        }
    }
}

/**
 * Standard indent strategy: don't indent (return 0 columns).
 */
val flatIndent: (TreeIndentContext) -> Int? = { 0 }

/** Count the number of columns in the leading whitespace of a string. */
internal fun countIndent(text: String, tabSize: Int = 4): Int {
    var cols = 0
    for (ch in text) {
        when (ch) {
            ' ' -> cols++
            '\t' -> cols += tabSize - (cols % tabSize)
            else -> break
        }
    }
    return cols
}

/** Get the length (in characters) of the leading whitespace. */
internal fun currentIndentLength(text: String): Int {
    var i = 0
    while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
    return i
}

/**
 * Extension that automatically re-indents the current line when the
 * user types a character that triggers indentation changes (e.g. a
 * closing brace).
 *
 * Language data providers register `"indentOnInput"` [Regex] entries
 * that are tested against the text from the start of the line to the
 * cursor. When a match is found and the computed indentation differs
 * from the current one, the line is re-indented.
 */
val indentOnInput: Extension = transactionFilter.of { tr ->
    if (!tr.docChanged || !tr.isUserEvent("input.type")) {
        return@of TransactionFilterResult.Filtered(tr)
    }

    val state = tr.state
    val changes = mutableListOf<ChangeSpec>()
    val selRanges = mutableListOf<SelectionSpec>()
    var changed = false

    for (range in state.selection.ranges) {
        val pos = range.head
        val line = state.doc.lineAt(pos)
        val col = pos - line.from
        val lineText = line.text
        val upToCursor = lineText.substring(0, minOf(col, lineText.length))

        val triggers = state.languageDataAt(LanguageDataKey.INDENT_ON_INPUT, pos)
        val matches = triggers.any { it.containsMatchIn(upToCursor) }
        if (!matches) continue

        val desired = getIndentation(state, line.from) ?: continue
        val current = countIndent(lineText, state.tabSize)
        if (current == desired) continue

        val currentLen = currentIndentLength(lineText)
        val newIndent = indentString(state, desired)
        changes.add(
            ChangeSpec.Single(
                line.from,
                line.from + currentLen,
                InsertContent.StringContent(newIndent)
            )
        )
        val cursorShift = newIndent.length - currentLen
        selRanges.add(SelectionSpec.CursorSpec(pos + cursorShift))
        changed = true
    }

    if (changed) {
        TransactionFilterResult.Specs(
            listOf(
                TransactionSpec(
                    changes = if (changes.size == 1) {
                        changes[0]
                    } else {
                        ChangeSpec.Multi(changes)
                    },
                    selection = if (selRanges.size == 1) {
                        selRanges[0]
                    } else {
                        null
                    },
                    userEvent = "input.type"
                )
            )
        )
    } else {
        TransactionFilterResult.Filtered(tr)
    }
}
