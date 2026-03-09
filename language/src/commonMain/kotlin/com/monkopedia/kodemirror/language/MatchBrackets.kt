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
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.SelectionRange
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.MarkDecorationSpec
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.editorTheme

/**
 * A [NodeProp] that language parsers can attach to node types to
 * provide custom bracket-matching behavior. The function receives
 * the syntax node and the document state, and returns a
 * [MatchResult] if this node represents a bracket, or `null`
 * otherwise.
 *
 * This allows languages with non-standard bracket syntax to
 * participate in bracket matching and highlighting.
 */
val bracketMatchingHandle: NodeProp<(SyntaxNode, EditorState) -> MatchResult?> =
    NodeProp()

/** Configuration for bracket matching. */
data class BracketMatchingConfig(
    val afterCursor: Boolean = true,
    val brackets: String = "()[]{}",
    val maxScanDistance: Int = 10000,
    val renderMatch: ((match: MatchResult) -> DecorationSet)? = null
)

/** Result of a bracket-matching query. */
data class MatchResult(
    val start: SelectionRange,
    val end: SelectionRange?,
    val matched: Boolean
)

private val defaultBracketPairs = mapOf(
    '(' to ')',
    '[' to ']',
    '{' to '}',
    ')' to '(',
    ']' to '[',
    '}' to '{'
)

private val openingBrackets = setOf('(', '[', '{')

/**
 * Match brackets at a given position in the document.
 *
 * Uses the syntax tree's [NodeProp.closedBy]/[NodeProp.openedBy] props
 * when available, and falls back to character scanning for basic brackets.
 */
fun matchBrackets(
    state: EditorState,
    pos: DocPos,
    dir: Int = -1,
    config: BracketMatchingConfig = BracketMatchingConfig()
): MatchResult? {
    val tree = syntaxTree(state)

    // Try custom bracket matching handle first
    val node = tree.resolveInner(pos.value, dir)
    val handle = node.type.prop(bracketMatchingHandle)
    if (handle != null) {
        val result = handle(node, state)
        if (result != null) return result
    }

    // Try tree-based matching
    val treeResult = tryTreeMatch(state, tree, pos, dir, config)
    if (treeResult != null) return treeResult

    // Fall back to character scanning
    return tryScanMatch(state, pos, dir, config)
}

private fun tryTreeMatch(
    state: EditorState,
    tree: com.monkopedia.kodemirror.lezer.common.Tree,
    pos: DocPos,
    dir: Int,
    config: BracketMatchingConfig
): MatchResult? {
    val node = tree.resolveInner(pos.value, dir)
    val closedBy = node.type.prop(NodeProp.closedBy)
    val openedBy = node.type.prop(NodeProp.openedBy)

    if (closedBy != null) {
        // This is an opening bracket node -- find its close
        val matchNode = findMatchingNode(node, closedBy, 1, config.maxScanDistance)
        return MatchResult(
            start = com.monkopedia.kodemirror.state.EditorSelection.range(
                DocPos(node.from),
                DocPos(node.to)
            ),
            end = matchNode?.let {
                com.monkopedia.kodemirror.state.EditorSelection.range(
                    DocPos(it.from),
                    DocPos(it.to)
                )
            },
            matched = matchNode != null
        )
    }

    if (openedBy != null) {
        // This is a closing bracket node -- find its open
        val matchNode = findMatchingNode(node, openedBy, -1, config.maxScanDistance)
        return MatchResult(
            start = com.monkopedia.kodemirror.state.EditorSelection.range(
                DocPos(node.from),
                DocPos(node.to)
            ),
            end = matchNode?.let {
                com.monkopedia.kodemirror.state.EditorSelection.range(
                    DocPos(it.from),
                    DocPos(it.to)
                )
            },
            matched = matchNode != null
        )
    }

    return null
}

private fun findMatchingNode(
    node: SyntaxNode,
    targetNames: List<String>,
    dir: Int,
    maxDist: Int
): SyntaxNode? {
    val parent = node.parent ?: return null
    var sibling: SyntaxNode? = if (dir > 0) node.nextSibling else node.prevSibling
    var distance = 0
    while (sibling != null) {
        distance += kotlin.math.abs(sibling.to - sibling.from)
        if (distance > maxDist) return null
        if (targetNames.contains(sibling.name)) return sibling
        sibling = if (dir > 0) sibling.nextSibling else sibling.prevSibling
    }
    return null
}

private fun tryScanMatch(
    state: EditorState,
    pos: DocPos,
    dir: Int,
    config: BracketMatchingConfig
): MatchResult? {
    if (pos.value < 0 || pos.value >= state.doc.length) return null
    val doc = state.doc
    val charStr = doc.sliceString(pos, pos + 1)
    if (charStr.isEmpty()) return null
    val ch = charStr[0]

    val matchCh = defaultBracketPairs[ch] ?: return null
    val isOpen = openingBrackets.contains(ch)
    val scanDir = if (isOpen) 1 else -1

    val start = com.monkopedia.kodemirror.state.EditorSelection.range(pos, pos + 1)

    // Scan for the matching bracket
    var depth = 1
    var scanPos = pos.value + scanDir
    val limit = config.maxScanDistance
    var scanned = 0

    while (scanned < limit && scanPos >= 0 && scanPos < doc.length) {
        val scanStr = doc.sliceString(DocPos(scanPos), DocPos(scanPos + 1))
        if (scanStr.isNotEmpty()) {
            val scanCh = scanStr[0]
            if (scanCh == ch) {
                depth++
            } else if (scanCh == matchCh) {
                depth--
                if (depth == 0) {
                    return MatchResult(
                        start = start,
                        end = com.monkopedia.kodemirror.state.EditorSelection.range(
                            DocPos(scanPos),
                            DocPos(scanPos + 1)
                        ),
                        matched = true
                    )
                }
            }
        }
        scanPos += scanDir
        scanned++
    }

    return MatchResult(start = start, end = null, matched = false)
}

/**
 * Extension that highlights matching brackets near the cursor.
 *
 * On each selection change, looks at the character before and after the
 * cursor. If either is a bracket, highlights both it and its match
 * (or just the unmatched bracket if no match is found).
 */
fun bracketMatching(config: BracketMatchingConfig = BracketMatchingConfig()): Extension {
    return ViewPlugin.define(
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
}

private class BracketMatchingPlugin(
    state: EditorState,
    private val config: BracketMatchingConfig
) : PluginValue {
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

        val builder = RangeSetBuilder<Decoration>()
        val ranges = mutableListOf<Triple<DocPos, DocPos, Decoration>>()

        for (sel in state.selection.ranges) {
            val pos = sel.head
            // Check character before cursor
            if (pos > DocPos.ZERO) {
                val match = matchBrackets(state, pos - 1, -1, config)
                if (match != null) {
                    addMatchRanges(match, matchedDeco, unmatchedDeco, ranges)
                }
            }
            // Check character after cursor (if configured)
            if (config.afterCursor && pos.value < state.doc.length) {
                val match = matchBrackets(state, pos, 1, config)
                if (match != null) {
                    addMatchRanges(match, matchedDeco, unmatchedDeco, ranges)
                }
            }
        }

        // Sort and deduplicate ranges, then add to builder
        ranges.sortBy { it.first }
        var lastTo = DocPos(-1)
        for ((from, to, deco) in ranges) {
            if (from >= lastTo) {
                builder.add(from, to, deco)
                lastTo = to
            }
        }

        return builder.finish()
    }

    private fun addMatchRanges(
        match: MatchResult,
        matchedDeco: Decoration,
        unmatchedDeco: Decoration,
        ranges: MutableList<Triple<DocPos, DocPos, Decoration>>
    ) {
        val deco = if (match.matched) matchedDeco else unmatchedDeco
        ranges.add(Triple(match.start.from, match.start.to, deco))
        if (match.end != null && match.matched) {
            ranges.add(Triple(match.end.from, match.end.to, matchedDeco))
        }
    }
}
