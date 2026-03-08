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

import com.monkopedia.kodemirror.lezer.common.NodeSet
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.lezer.common.TreeBuildBuffer
import com.monkopedia.kodemirror.lezer.common.TreeBuildSpec

class Buffer(val nodeSet: NodeSet) {
    val content: MutableList<Int> = mutableListOf()
    val nodes: MutableList<Tree> = mutableListOf()

    fun write(type: Int, from: Int, to: Int, children: Int = 0): Buffer {
        content.add(type)
        content.add(from)
        content.add(to)
        content.add(4 + children * 4)
        return this
    }

    fun writeElements(elts: List<Element>, offset: Int = 0): Buffer {
        for (e in elts) e.writeTo(this, offset)
        return this
    }

    fun finish(type: Int, length: Int): Tree {
        return Tree.build(
            TreeBuildSpec(
                buffer = TreeBuildBuffer.ListBuffer(content.toList()),
                nodeSet = nodeSet,
                reused = nodes,
                topID = type,
                length = length
            )
        )
    }
}
