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
package com.monkopedia.kodemirror.autocomplete

import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState

/** How far back [CompletionContext.matchBefore] looks, as upstream. */
private const val MATCH_BEFORE_RANGE = 250

/** Result of [CompletionContext.matchBefore]. */
data class MatchBeforeResult(val from: DocPos, val to: DocPos, val text: String)

/**
 * Context passed to completion sources.
 *
 * @param state The current editor state.
 * @param pos The cursor position where completion was triggered.
 * @param explicit Whether completion was explicitly requested (e.g. Ctrl-Space).
 */
class CompletionContext(val state: EditorState, val pos: DocPos, val explicit: Boolean) {
    /**
     * The innermost syntax node ending at or around [pos] whose name is in
     * [types], or `null` when there is none. Like upstream's `tokenBefore`,
     * a node that contains the cursor counts: inside `"ab|c"` the `String`
     * node is found, which is what lets [ifIn]/[ifNotIn] test "the cursor is
     * in a string".
     */
    fun tokenBefore(types: List<String>): SyntaxNode? {
        var node: SyntaxNode? = syntaxTree(state).resolveInner(pos.value, -1)
        while (node != null && node.name !in types) node = node.parent
        return node
    }

    /**
     * Match [pattern] against the text directly before the cursor on the
     * current line (at most 250 characters back), returning the leftmost
     * match that ends at the cursor, or `null`.
     */
    fun matchBefore(pattern: Regex): MatchBeforeResult? {
        val line = state.doc.lineAt(pos)
        val start = maxOf(line.from, pos - MATCH_BEFORE_RANGE)
        val textBefore = state.doc.sliceString(start, pos)
        val anchored = if (pattern.pattern.endsWith("$")) {
            pattern
        } else {
            Regex("(?:${pattern.pattern})$", pattern.options)
        }
        val match = anchored.find(textBefore) ?: return null
        return MatchBeforeResult(
            from = start + match.range.first,
            to = pos,
            text = textBefore.substring(match.range.first)
        )
    }
}
