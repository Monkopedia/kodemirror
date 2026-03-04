/*
 * Copyright 2025 Jason Monk
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
package com.monkopedia.kodemirror.lezer.java

import com.monkopedia.kodemirror.lezer.common.IterateSpec
import com.monkopedia.kodemirror.lezer.common.Tree

private val nonWordRegex = Regex("\\W")

/**
 * Returns true if this node should be ignored in tree output.
 * Matches upstream @lezer/generator defaultIgnore: skip nodes whose name contains
 * non-word characters (e.g. "[", "]", "{", "}", ":", ",").
 * Also skip nodes with empty names (anonymous).
 */
private fun shouldIgnore(name: String, isError: Boolean): Boolean {
    if (isError) return false
    return name.isEmpty() || nonWordRegex.containsMatchIn(name)
}

/**
 * Convert a tree to the upstream s-expression format used in .txt test fixtures.
 * - Error node (id=0) renders as "\u26A0"
 * - Anonymous nodes (empty name) are skipped
 * - Punctuation nodes (name contains non-word characters, e.g. "[", "]") are skipped
 * - Format: NodeName(Child1,Child2,...)
 */
fun treeToString(tree: Tree): String {
    // Stack of (nodeName, children-list) for open nodes
    val stack = mutableListOf<Pair<String, MutableList<String>>>()

    tree.iterate(
        IterateSpec(
            enter = { nodeRef ->
                val name = nodeRef.type.name
                val isError = nodeRef.type.isError
                val displayName = if (isError) "\u26A0" else name
                if (shouldIgnore(name, isError)) {
                    // Skip this node -- don't recurse into it
                    false
                } else {
                    stack.add(displayName to mutableListOf())
                    null // continue into children
                }
            },
            leave = { nodeRef ->
                val name = nodeRef.type.name
                val isError = nodeRef.type.isError
                if (!shouldIgnore(name, isError)) {
                    val (nodeName, children) = stack.removeLast()
                    val str = if (children.isEmpty()) {
                        nodeName
                    } else {
                        "$nodeName(${children.joinToString(",")})"
                    }
                    if (stack.isEmpty()) {
                        // Root -- push a sentinel result
                        stack.add(str to mutableListOf())
                    } else {
                        stack.last().second.add(str)
                    }
                }
            }
        )
    )

    return stack.firstOrNull()?.first ?: ""
}
