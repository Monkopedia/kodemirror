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

/** Result of [CompletionContext.matchBefore]. */
data class MatchBeforeResult(
    val from: DocPos,
    val to: DocPos,
    val text: String
)

/**
 * Context passed to completion sources.
 *
 * @param state The current editor state.
 * @param pos The cursor position where completion was triggered.
 * @param explicit Whether completion was explicitly requested (e.g. Ctrl-Space).
 */
class CompletionContext(
    val state: EditorState,
    val pos: DocPos,
    val explicit: Boolean
) {
    /**
     * Walk the syntax tree backward from [pos] looking for a node whose
     * name is in [types].
     */
    fun tokenBefore(types: List<String>): SyntaxNode? {
        val tree = syntaxTree(state)
        var node = tree.resolveInner(pos.value, -1)
        while (node.from < pos.value) {
            if (types.contains(node.name) && node.to <= pos.value) {
                return node
            }
            val parent = node.parent ?: break
            node = parent
        }
        return null
    }

    /**
     * Match a regex against the text before the cursor on the current line.
     * Returns the match result or null.
     */
    fun matchBefore(pattern: Regex): MatchBeforeResult? {
        val line = state.doc.lineAt(pos)
        val lineStart = line.from
        val textBefore = state.doc.sliceString(lineStart, pos)
        val match = pattern.find(textBefore) ?: return null
        // Only consider matches that end at the cursor position within the line
        if (match.range.last + 1 != textBefore.length) return null
        return MatchBeforeResult(
            from = lineStart + match.range.first,
            to = pos,
            text = match.value
        )
    }
}
