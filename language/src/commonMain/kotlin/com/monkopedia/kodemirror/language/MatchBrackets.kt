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

import androidx.compose.ui.text.SpanStyle
import com.monkopedia.kodemirror.lezer.common.NodeProp
import com.monkopedia.kodemirror.lezer.common.NodeType
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Range
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.SelectionRange
import com.monkopedia.kodemirror.state.endPos
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.MarkDecorationSpec
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.editorTheme

/**
 * When larger syntax nodes, such as HTML tags, are marked as opening/closing, it can be a bit
 * messy to treat the whole node as a matchable bracket. This node prop allows you to define,
 * for such a node, a "handle": the part of the node that is highlighted, and that the cursor
 * must be on to activate highlighting in the first place.
 */
val bracketMatchingHandle: NodeProp<(SyntaxNode) -> SyntaxNode?> = NodeProp()

/** Configuration for bracket matching. */
data class BracketMatchingConfig(
    val afterCursor: Boolean = true,
    val brackets: String = "()[]{}",
    val maxScanDistance: Int = 10000,
    val renderMatch: ((match: MatchResult) -> DecorationSet)? = null
)

/** Result of a bracket-matching query. */
data class MatchResult(val start: SelectionRange, val end: SelectionRange?, val matched: Boolean)

private fun matchingNodes(node: NodeType, dir: Int, brackets: String): List<String>? {
    val byProp = node.prop(if (dir < 0) NodeProp.openedBy else NodeProp.closedBy)
    if (byProp != null) return byProp
    if (node.name.length == 1) {
        val index = brackets.indexOf(node.name)
        if (index > -1 && index % 2 == (if (dir < 0) 1 else 0)) {
            return listOf(brackets[index + dir].toString())
        }
    }
    return null
}

private fun findHandle(node: SyntaxNode): SyntaxNode? {
    val hasHandle = node.type.prop(bracketMatchingHandle)
    return if (hasHandle != null) hasHandle(node) else node
}

private fun range(from: Int, to: Int): SelectionRange =
    EditorSelection.range(DocPos(from), DocPos(to))

/**
 * Find the matching bracket for the token at [pos], scanning direction [dir]: for `dir < 0`
 * the bracket is the one ending at [pos], otherwise the one starting there. Only the
 * [BracketMatchingConfig.brackets] and [BracketMatchingConfig.maxScanDistance] properties are
 * used from [config]. Returns null if no bracket was found at [pos], or a match result
 * otherwise.
 *
 * Bracket tokens are matched through the syntax tree's [NodeProp.closedBy]/[NodeProp.openedBy]
 * props (or single-character node names in [BracketMatchingConfig.brackets]); other brackets
 * are matched by scanning the document for characters in the same kind of token.
 */
fun matchBrackets(
    state: EditorState,
    pos: DocPos,
    dir: Int = -1,
    config: BracketMatchingConfig = BracketMatchingConfig()
): MatchResult? {
    val brackets = config.brackets
    val tree = syntaxTree(state)
    val node = tree.resolveInner(pos.value, dir)
    var cur: SyntaxNode? = node
    while (cur != null) {
        val matches = matchingNodes(cur.type, dir, brackets)
        if (matches != null && cur.from < cur.to) {
            val handle = findHandle(cur)
            if (handle != null &&
                if (dir > 0) {
                    pos.value >= handle.from && pos.value < handle.to
                } else {
                    pos.value > handle.from && pos.value <= handle.to
                }
            ) {
                return matchMarkedBrackets(dir, cur, handle, matches, brackets)
            }
        }
        cur = cur.parent
    }
    return matchPlainBrackets(
        state,
        pos.value,
        dir,
        tree,
        node.type,
        config.maxScanDistance,
        brackets
    )
}

private fun matchMarkedBrackets(
    dir: Int,
    token: SyntaxNode,
    handle: SyntaxNode,
    matching: List<String>,
    brackets: String
): MatchResult {
    val firstToken = range(handle.from, handle.to)
    var depth = 0
    val cursor = token.parent?.cursor()
    if (cursor != null &&
        (if (dir < 0) cursor.childBefore(token.from) else cursor.childAfter(token.to))
    ) {
        do {
            if (if (dir < 0) cursor.to <= token.from else cursor.from >= token.to) {
                if (depth == 0 && cursor.type.name in matching && cursor.from < cursor.to) {
                    val endHandle = findHandle(cursor.node)
                    return MatchResult(
                        firstToken,
                        endHandle?.let {
                            range(it.from, it.to)
                        },
                        matched = true
                    )
                } else if (matchingNodes(cursor.type, dir, brackets) != null) {
                    depth++
                } else if (matchingNodes(cursor.type, -dir, brackets) != null) {
                    if (depth == 0) {
                        val endHandle = findHandle(cursor.node)
                        return MatchResult(
                            firstToken,
                            endHandle?.takeIf { it.from < it.to }?.let { range(it.from, it.to) },
                            matched = false
                        )
                    }
                    depth--
                }
            }
        } while (if (dir < 0) cursor.prevSibling() else cursor.nextSibling())
    }
    return MatchResult(firstToken, null, matched = false)
}

private fun matchPlainBrackets(
    state: EditorState,
    pos: Int,
    dir: Int,
    tree: Tree,
    tokenType: NodeType,
    maxScanDistance: Int,
    brackets: String
): MatchResult? {
    val startCh = if (dir <
        0
    ) {
        state.sliceDoc(DocPos(pos - 1), DocPos(pos))
    } else {
        state.sliceDoc(
            DocPos(pos),
            DocPos(
                pos + 1
            )
        )
    }
    // Upstream's `brackets.indexOf("")` is 0 at the document end, so it reports a phantom
    // opening bracket past the last character; there is no character there to match.
    if (startCh.isEmpty()) return null
    val bracket = brackets.indexOf(startCh)
    if (bracket < 0 || (bracket % 2 == 0) != (dir > 0)) return null
    val startToken = range(if (dir < 0) pos - 1 else pos, if (dir > 0) pos + 1 else pos)
    val iter = state.doc.iterRange(DocPos(pos), if (dir > 0) state.doc.endPos else DocPos.ZERO)
    var depth = 0
    var distance = 0
    while (!iter.next().done && distance <= maxScanDistance) {
        val text = iter.value
        if (dir < 0) distance += text.length
        val basePos = pos + distance * dir
        var i = if (dir > 0) 0 else text.length - 1
        val end = if (dir > 0) text.length else -1
        while (i != end) {
            val found = brackets.indexOf(text[i])
            if (found >= 0 && tree.resolveInner(basePos + i, 1).type == tokenType) {
                if ((found % 2 == 0) == (dir > 0)) {
                    depth++
                } else if (depth == 1) { // Closing
                    return MatchResult(
                        startToken,
                        range(basePos + i, basePos + i + 1),
                        matched = (found shr 1) == (bracket shr 1)
                    )
                } else {
                    depth--
                }
            }
            i += dir
        }
        if (dir > 0) distance += text.length
    }
    return if (iter.done) MatchResult(startToken, null, matched = false) else null
}

/**
 * Extension that highlights matching brackets near the cursor.
 *
 * On each selection change, looks at the character before and after the
 * cursor. If either is a bracket, highlights both it and its match
 * (or just the unmatched bracket if no match is found).
 */
fun bracketMatching(config: BracketMatchingConfig = BracketMatchingConfig()): Extension =
    ViewPlugin.define(
        create = { view ->
            BracketMatchingPlugin(view.state, config)
        },
        configure = {
            copy(
                decorations = { plugin ->
                    (plugin as? BracketMatchingPlugin)?.decos ?: RangeSet.empty()
                }
            )
        }
    ).asExtension()

private class BracketMatchingPlugin(state: EditorState, private val config: BracketMatchingConfig) :
    PluginValue {
    var decos: DecorationSet = buildDecos(state)

    override fun update(update: ViewUpdate) {
        if (update.docChanged || update.selectionSet) {
            decos = buildDecos(update.state)
        }
    }

    private fun buildDecos(state: EditorState): DecorationSet {
        val theme = state.facet(editorTheme)
        val matchedDeco = Decoration.mark(
            MarkDecorationSpec(
                style = SpanStyle(background = theme.matchingBracketBackground),
                cssClass = "cm-matchingBracket"
            )
        )
        val unmatchedDeco = Decoration.mark(
            MarkDecorationSpec(
                style = SpanStyle(background = theme.nonMatchingBracketBackground),
                cssClass = "cm-nonmatchingBracket"
            )
        )

        val decorations = mutableListOf<Range<Decoration>>()
        for (range in state.selection.ranges) {
            if (!range.empty) continue
            val head = range.head
            val match = matchBrackets(state, head, -1, config)
                ?: (if (head > DocPos.ZERO) matchBrackets(state, head - 1, 1, config) else null)
                ?: (if (config.afterCursor) matchBrackets(state, head, 1, config) else null)
                ?: (
                    if (config.afterCursor && head < state.doc.endPos) {
                        matchBrackets(state, head + 1, -1, config)
                    } else {
                        null
                    }
                    )
                ?: continue
            val mark = if (match.matched) matchedDeco else unmatchedDeco
            decorations.add(mark.range(match.start.from, match.start.to))
            match.end?.let { decorations.add(mark.range(it.from, it.to)) }
        }
        return RangeSet.of(decorations, sort = true)
    }
}
