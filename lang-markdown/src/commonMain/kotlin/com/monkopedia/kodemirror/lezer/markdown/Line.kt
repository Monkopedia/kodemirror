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

class LeafBlock(
    val start: Int,
    content: String
) {
    var content: String = content
        internal set
    internal val marks: MutableList<Element> = mutableListOf()
    var parsers: MutableList<LeafBlockParser> = mutableListOf()
        internal set
}

class Line {
    var text: String = ""
    var baseIndent: Int = 0
    var basePos: Int = 0
    var depth: Int = 0
    var markers: MutableList<Element> = mutableListOf()
    var pos: Int = 0
    var indent: Int = 0
    var next: Int = -1

    internal fun forward() {
        if (basePos > pos) forwardInner()
    }

    internal fun forwardInner() {
        val newPos = skipSpace(basePos)
        indent = countIndent(newPos, pos, indent)
        pos = newPos
        next = if (newPos == text.length) -1 else text[newPos].code
    }

    fun skipSpace(from: Int): Int = skipSpaceFn(text, from)

    internal fun reset(text: String) {
        this.text = text
        baseIndent = 0
        basePos = 0
        pos = 0
        indent = 0
        forwardInner()
        depth = 1
        while (markers.isNotEmpty()) markers.removeLast()
    }

    fun moveBase(to: Int) {
        basePos = to
        baseIndent = countIndent(to, pos, indent)
    }

    fun moveBaseColumn(indent: Int) {
        baseIndent = indent
        basePos = findColumn(indent)
    }

    fun addMarker(elt: Element) {
        markers.add(elt)
    }

    fun countIndent(to: Int, from: Int = 0, indent: Int = 0): Int {
        var result = indent
        for (i in from until to) {
            result += if (text[i].code == 9) 4 - result % 4 else 1
        }
        return result
    }

    fun findColumn(goal: Int): Int {
        var i = 0
        var indent = 0
        while (i < text.length && indent < goal) {
            indent += if (text[i].code == 9) 4 - indent % 4 else 1
            i++
        }
        return i
    }

    internal fun scrub(): String {
        if (baseIndent == 0) return text
        val result = StringBuilder()
        for (i in 0 until basePos) result.append(' ')
        return result.toString() + text.substring(basePos)
    }
}

fun space(ch: Int): Boolean = ch == 32 || ch == 9 || ch == 10 || ch == 13

internal fun skipSpaceFn(line: String, i: Int = 0): Int {
    var pos = i
    while (pos < line.length && space(line[pos].code)) pos++
    return pos
}

internal fun skipSpaceBack(line: String, i: Int, to: Int): Int {
    var pos = i
    while (pos > to && space(line[pos - 1].code)) pos--
    return pos
}
