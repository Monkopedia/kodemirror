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
 * Each node in a syntax tree has a type, which describes its role
 * and the information associated with it.
 */
class NodeType internal constructor(
    val name: String,
    val id: Int,
    internal val props: Map<Int, Any?>,
    val isTop: Boolean,
    val isError: Boolean,
    val isSkipped: Boolean
) {
    /** Check whether this type is part of a given group (via [NodeProp.group]). */
    fun `is`(nameOrGroup: String): Boolean {
        if (this.name == nameOrGroup) return true
        val groups = prop(NodeProp.group)
        return groups != null && groups.contains(nameOrGroup)
    }

    /** Look up a prop on this type. */
    @Suppress("UNCHECKED_CAST")
    fun <T> prop(prop: NodeProp<T>): T? {
        return props[prop.id] as T?
    }

    override fun toString(): String = name.ifEmpty { "anonymous($id)" }

    companion object {
        /** The empty/anonymous node type. */
        val none = NodeType("", 0, emptyMap(), isTop = false, isError = false, isSkipped = false)

        /**
         * Create a function from node types to the result type that
         * returns values assigned to node types (or named groups of types)
         * via the given map.
         */
        fun <T> match(map: Map<String, T>): (NodeType) -> T? {
            val mapEntries = map.entries.toList()
            return { type ->
                var result: T? = null
                for ((selector, value) in mapEntries) {
                    if (type.`is`(selector)) {
                        result = value
                        break
                    }
                }
                result
            }
        }

        /** Define a [NodeType]. */
        fun define(spec: NodeTypeSpec): NodeType {
            val propsMap = mutableMapOf<Int, Any?>()
            for ((prop, value) in spec.props) {
                propsMap[prop.id] = value
            }
            if (spec.top) {
                propsMap[NodeProp.top.id] = true
            }
            return NodeType(
                name = spec.name,
                id = spec.id,
                props = propsMap,
                isTop = spec.top,
                isError = spec.error,
                isSkipped = spec.skipped
            )
        }
    }
}

/**
 * Spec for constructing a [NodeType].
 */
data class NodeTypeSpec(
    val name: String = "",
    val id: Int,
    val props: List<Pair<NodeProp<*>, Any?>> = emptyList(),
    val top: Boolean = false,
    val error: Boolean = false,
    val skipped: Boolean = false
)

/**
 * An indexed set of [NodeType]s with helper methods for extending
 * them with props.
 */
class NodeSet(val types: List<NodeType>) {

    /** Create a copy of this set with the given [NodePropSource]s applied. */
    fun extend(vararg sources: NodePropSource<*>): NodeSet {
        val newTypes = types.map { type ->
            var changed = false
            val newProps = type.props.toMutableMap()
            for (source in sources) {
                val value = source.f(type)
                if (value != null) {
                    val existing = newProps[source.prop.id]
                    if (existing != null && source.prop.combine != null) {
                        @Suppress("UNCHECKED_CAST")
                        val combine = source.prop.combine as (Any?, Any?) -> Any?
                        newProps[source.prop.id] = combine(existing, value)
                    } else {
                        newProps[source.prop.id] = value
                    }
                    changed = true
                }
            }
            if (changed) {
                NodeType(
                    name = type.name,
                    id = type.id,
                    props = newProps,
                    isTop = type.isTop,
                    isError = type.isError,
                    isSkipped = type.isSkipped
                )
            } else {
                type
            }
        }
        return NodeSet(newTypes)
    }
}
