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
package com.monkopedia.kodemirror.lezer.markdown

import com.monkopedia.kodemirror.lezer.common.BalanceConfig
import com.monkopedia.kodemirror.lezer.common.NodeProp
import com.monkopedia.kodemirror.lezer.common.NodeSet
import com.monkopedia.kodemirror.lezer.common.NodeType
import com.monkopedia.kodemirror.lezer.common.Tree

class CompositeBlock(
    val type: Int,
    val value: Int,
    val from: Int,
    val hash: Int,
    var end: Int,
    val children: MutableList<Any> = mutableListOf(),
    val positions: MutableList<Int> = mutableListOf()
) {
    val hashProp: Map<Int, Any?> = mapOf(NodeProp.contextHash.id to hash)

    fun addChild(child: Tree, pos: Int) {
        var c = child
        if (c.prop(NodeProp.contextHash) != hash) {
            c = Tree(c.type, c.children, c.positions, c.length, hashProp)
        }
        children.add(c)
        positions.add(pos)
    }

    fun toTree(nodeSet: NodeSet, end: Int = this.end): Tree {
        val last = children.size - 1
        var e = end
        if (last >= 0) {
            val lastChild = children[last]
            val lastLen = if (lastChild is Tree) {
                lastChild.length
            } else {
                error("Unexpected child type")
            }
            e = maxOf(e, positions[last] + lastLen + from)
        }
        return Tree(
            nodeSet.types[type],
            children,
            positions,
            e - from,
            hashProp
        ).balance(
            BalanceConfig(
                makeTree = { ch, pos, length ->
                    Tree(NodeType.none, ch, pos, length, hashProp)
                }
            )
        )
    }

    companion object {
        fun create(type: Int, value: Int, from: Int, parentHash: Int, end: Int): CompositeBlock {
            val hash = (parentHash + (parentHash shl 8) + type + (value shl 4))
            return CompositeBlock(type, value, from, hash, end)
        }
    }
}
