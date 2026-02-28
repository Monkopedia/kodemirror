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

private var nextPropID = 0

/**
 * Each [NodeType] or individual [Tree] can have metadata associated with it
 * in props. Instances of this class represent prop names.
 */
class NodeProp<T>(
    val perNode: Boolean = false,
    val deserialize: ((String) -> T)? = null,
    val combine: ((T, T) -> T)? = null
) {
    val id: Int = nextPropID++

    /**
     * Create a [NodePropSource] that assigns a value for this prop to
     * [NodeType]s by name or by a predicate function.
     */
    fun add(map: Map<String, T>): NodePropSource<T> {
        return NodePropSource(this) { type ->
            map[type.name]
        }
    }

    /**
     * Create a [NodePropSource] using a function that returns a value
     * for each [NodeType].
     */
    fun add(f: (NodeType) -> T?): NodePropSource<T> {
        return NodePropSource(this, f)
    }

    companion object {
        /** The group prop: a list of group names this type belongs to. */
        val group = NodeProp<List<String>>(
            deserialize = { it.split(" ") }
        )

        /** Tokens closed by other tokens. */
        val closedBy = NodeProp<List<String>>(
            deserialize = { it.split(" ") }
        )

        /** Tokens that open other tokens. */
        val openedBy = NodeProp<List<String>>(
            deserialize = { it.split(" ") }
        )

        /** Whether this node is the top node of a language. */
        val top = NodeProp<Boolean>(
            deserialize = { true }
        )

        /**
         * Per-node prop for mounted trees.
         */
        val mounted = NodeProp<MountedTree>(perNode = true)
    }
}

/**
 * Info about a mounted tree inside another tree.
 */
data class MountedTree(
    val tree: Tree,
    val overlay: List<TextRange>?,
    val parser: Parser
)

/**
 * A source that can provide values for a [NodeProp] on [NodeType]s.
 */
class NodePropSource<T>(
    val prop: NodeProp<T>,
    val f: (NodeType) -> T?
)
