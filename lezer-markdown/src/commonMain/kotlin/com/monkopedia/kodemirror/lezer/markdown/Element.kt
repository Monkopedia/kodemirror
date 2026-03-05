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

internal val none: List<Any> = emptyList()

open class Element(
    val type: Int,
    val from: Int,
    open var to: Int,
    val children: List<Element> = emptyList()
) {
    open fun writeTo(buf: Buffer, offset: Int) {
        val startOff = buf.content.size
        buf.writeElements(children, offset)
        val endSize = buf.content.size + 4 - startOff
        buf.content.add(type)
        buf.content.add(from + offset)
        buf.content.add(to + offset)
        buf.content.add(endSize)
    }

    open fun toTree(nodeSet: NodeSet): Tree {
        return Buffer(nodeSet).writeElements(children, -from).finish(type, to - from)
    }
}

class TreeElement(
    val tree: Tree,
    from: Int
) : Element(tree.type.id, from, from + tree.length) {
    override var to: Int
        get() = from + tree.length
        set(_) {}

    override fun writeTo(buf: Buffer, offset: Int) {
        buf.nodes.add(tree)
        buf.content.add(buf.nodes.size - 1)
        buf.content.add(from + offset)
        buf.content.add(to + offset)
        buf.content.add(-1)
    }

    override fun toTree(nodeSet: NodeSet): Tree = tree
}

internal fun elt(type: Int, from: Int, to: Int, children: List<Element> = emptyList()): Element =
    Element(type, from, to, children)

internal fun injectMarks(elements: List<Element>, marks: List<Element>): List<Element> {
    if (marks.isEmpty()) return elements
    if (elements.isEmpty()) return marks
    val elts = elements.toMutableList()
    var eI = 0
    for (mark in marks) {
        while (eI < elts.size && elts[eI].to < mark.to) eI++
        if (eI < elts.size && elts[eI].from < mark.from) {
            val e = elts[eI]
            if (e !is TreeElement) {
                elts[eI] = Element(
                    e.type, e.from, e.to,
                    injectMarks(e.children, listOf(mark))
                )
            }
        } else {
            elts.add(eI++, mark)
        }
    }
    return elts
}
