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
package com.monkopedia.kodemirror.lang.xml

import com.monkopedia.kodemirror.lezer.common.IterateSpec
import com.monkopedia.kodemirror.lezer.common.Tree

private val nonWordRegex = Regex("\\W")

private fun shouldIgnore(name: String, isError: Boolean): Boolean {
    if (isError) return false
    return name.isEmpty() || nonWordRegex.containsMatchIn(name)
}

fun treeToString(tree: Tree): String {
    val stack = mutableListOf<Pair<String, MutableList<String>>>()

    tree.iterate(
        IterateSpec(
            enter = { nodeRef ->
                val name = nodeRef.type.name
                val isError = nodeRef.type.isError
                val displayName = if (isError) "\u26A0" else name
                if (shouldIgnore(name, isError)) {
                    false
                } else {
                    stack.add(displayName to mutableListOf())
                    null
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
