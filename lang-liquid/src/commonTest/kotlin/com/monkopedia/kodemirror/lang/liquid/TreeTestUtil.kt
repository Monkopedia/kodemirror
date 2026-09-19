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
package com.monkopedia.kodemirror.lang.liquid

import com.monkopedia.kodemirror.lezer.common.IterateSpec
import com.monkopedia.kodemirror.lezer.common.Tree

private val nonWordRegex = Regex("\\W")

private fun shouldIgnore(name: String, isError: Boolean): Boolean {
    if (isError) return false
    return name.isEmpty() || nonWordRegex.containsMatchIn(name)
}

/**
 * Renders [tree] as a compact `Parent(Child,Child)` string, skipping punctuation-only node
 * names so the result is readable. Error nodes are rendered as `⚠` and never skipped.
 *
 * When [withPositions] is set every node is annotated with `[from,to]`, which is what makes
 * the assertions in these tests sensitive to token offsets and not only to tree shape.
 */
fun treeToString(tree: Tree, withPositions: Boolean = false): String {
    val stack = mutableListOf<Node>()

    tree.iterate(
        IterateSpec(
            enter = { nodeRef ->
                val name = nodeRef.type.name
                val isError = nodeRef.type.isError
                if (shouldIgnore(name, isError)) {
                    false
                } else {
                    val displayName = if (isError) "⚠" else name
                    stack.add(Node(displayName, nodeRef.from, nodeRef.to))
                    null
                }
            },
            leave = { nodeRef ->
                val name = nodeRef.type.name
                val isError = nodeRef.type.isError
                if (!shouldIgnore(name, isError)) {
                    val node = stack.removeAt(stack.lastIndex)
                    val head = if (withPositions) {
                        "${node.name}[${node.from},${node.to}]"
                    } else {
                        node.name
                    }
                    val str = if (node.children.isEmpty()) {
                        head
                    } else {
                        "$head(${node.children.joinToString(",")})"
                    }
                    if (stack.isEmpty()) {
                        stack.add(Node(str, node.from, node.to))
                    } else {
                        stack.last().children.add(str)
                    }
                }
            }
        )
    )

    return stack.firstOrNull()?.name ?: ""
}

/** Number of error nodes in [tree]; the parse is only correct when this is zero. */
fun errorCount(tree: Tree): Int {
    var count = 0
    tree.iterate(
        IterateSpec(
            enter = { nodeRef ->
                if (nodeRef.type.isError) count++
                null
            }
        )
    )
    return count
}

private class Node(val name: String, val from: Int, val to: Int) {
    val children: MutableList<String> = mutableListOf()
}
