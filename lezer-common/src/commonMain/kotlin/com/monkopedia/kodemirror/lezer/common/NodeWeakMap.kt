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
package com.monkopedia.kodemirror.lezer.common

/**
 * Associates values with (syntax) nodes, using their identity as a
 * key. In Kotlin/multiplatform we use a regular HashMap keyed by
 * the backing Tree instance + position, since true WeakReferences
 * aren't available cross-platform.
 */
class NodeWeakMap<T> {
    private data class NodeKey(
        val tree: Any?,
        val from: Int,
        val to: Int,
        val typeId: Int
    )

    private val map = HashMap<NodeKey, T>()

    private fun keyFor(node: SyntaxNode): NodeKey {
        return NodeKey(node.tree, node.from, node.to, node.type.id)
    }

    private fun keyFor(cursor: TreeCursor): NodeKey {
        return NodeKey(cursor.tree, cursor.from, cursor.to, cursor.type.id)
    }

    fun get(node: SyntaxNode): T? = map[keyFor(node)]

    fun set(node: SyntaxNode, value: T) {
        map[keyFor(node)] = value
    }

    fun cursorGet(cursor: TreeCursor): T? = map[keyFor(cursor)]

    fun cursorSet(cursor: TreeCursor, value: T) {
        map[keyFor(cursor)] = value
    }
}
