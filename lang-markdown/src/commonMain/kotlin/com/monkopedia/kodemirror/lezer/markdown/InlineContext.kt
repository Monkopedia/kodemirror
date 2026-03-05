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

import com.monkopedia.kodemirror.lezer.common.Tree

internal object Mark {
    const val NONE = 0
    const val OPEN = 1
    const val CLOSE = 2
}

class InlineDelimiter(
    val type: DelimiterType,
    val from: Int,
    val to: Int,
    var side: Int
)

class InlineContext(
    val parser: MarkdownParser,
    val text: String,
    val offset: Int
) {
    internal val parts: MutableList<Any?> = mutableListOf() // Element | InlineDelimiter | null

    fun char(pos: Int): Int = if (pos >= end) -1 else text[pos - offset].code

    val end: Int get() = offset + text.length

    fun slice(from: Int, to: Int): String = text.substring(from - offset, to - offset)

    internal fun append(elt: Element): Int {
        parts.add(elt)
        return elt.to
    }

    internal fun append(delim: InlineDelimiter): Int {
        parts.add(delim)
        return delim.to
    }

    fun addDelimiter(type: DelimiterType, from: Int, to: Int, open: Boolean, close: Boolean): Int {
        return append(
            InlineDelimiter(
                type,
                from,
                to,
                (if (open) Mark.OPEN else Mark.NONE) or
                    (if (close) Mark.CLOSE else Mark.NONE)
            )
        )
    }

    val hasOpenLink: Boolean
        get() {
            for (i in parts.indices.reversed()) {
                val part = parts[i]
                if (part is InlineDelimiter &&
                    (part.type == LinkStart || part.type == ImageStart)
                ) {
                    return true
                }
            }
            return false
        }

    fun addElement(elt: Element): Int = append(elt)

    internal fun resolveMarkers(from: Int): List<Element> {
        for (i in from until parts.size) {
            val close = parts[i]
            if (close !is InlineDelimiter || close.type.resolve == null ||
                (close.side and Mark.CLOSE) == 0
            ) {
                continue
            }

            val emp = close.type == EmphasisUnderscore || close.type == EmphasisAsterisk
            val closeSize = close.to - close.from
            var open: InlineDelimiter? = null
            var j = i - 1
            while (j >= from) {
                val part = parts[j]
                if (part is InlineDelimiter && (part.side and Mark.OPEN) != 0 &&
                    part.type == close.type &&
                    !(
                        emp &&
                            ((close.side and Mark.OPEN) != 0 || (part.side and Mark.CLOSE) != 0) &&
                            (part.to - part.from + closeSize) % 3 == 0 &&
                            ((part.to - part.from) % 3 != 0 || closeSize % 3 != 0)
                        )
                ) {
                    open = part
                    break
                }
                j--
            }
            if (open == null) continue

            var typeName = close.type.resolve!!
            val content = mutableListOf<Element>()
            var start = open.from
            var end = close.to
            if (emp) {
                val size = minOf(2, open.to - open.from, closeSize)
                start = open.to - size
                end = close.from + size
                typeName = if (size == 1) "Emphasis" else "StrongEmphasis"
            }
            if (open.type.mark != null) {
                content.add(this.elt(open.type.mark!!, start, open.to))
            }
            for (k in (j + 1) until i) {
                val p = parts[k]
                if (p is Element) content.add(p)
                parts[k] = null
            }
            if (close.type.mark != null) {
                content.add(this.elt(close.type.mark!!, close.from, end))
            }
            val element = this.elt(typeName, start, end, content)
            parts[j] = if (emp && open.from != start) {
                InlineDelimiter(open.type, open.from, start, open.side)
            } else {
                null
            }
            val keep = if (emp && close.to != end) {
                InlineDelimiter(close.type, end, close.to, close.side)
            } else {
                null
            }
            if (keep != null) {
                parts[i] = keep
                parts.add(i, element)
            } else {
                parts[i] = element
            }
        }

        val result = mutableListOf<Element>()
        for (i in from until parts.size) {
            val part = parts[i]
            if (part is Element) result.add(part)
        }
        return result
    }

    fun findOpeningDelimiter(type: DelimiterType): Int? {
        for (i in parts.indices.reversed()) {
            val part = parts[i]
            if (part is InlineDelimiter && part.type == type &&
                (part.side and Mark.OPEN) != 0
            ) {
                return i
            }
        }
        return null
    }

    fun takeContent(startIndex: Int): List<Element> {
        val content = resolveMarkers(startIndex)
        while (parts.size > startIndex) parts.removeLast()
        return content
    }

    fun getDelimiterAt(index: Int): InlineDelimiter? {
        val part = parts[index]
        return part as? InlineDelimiter
    }

    fun skipSpace(from: Int): Int = skipSpaceFn(text, from - offset) + offset

    fun elt(type: String, from: Int, to: Int, children: List<Element>? = null): Element =
        elt(parser.getNodeType(type), from, to, children ?: emptyList())

    fun elt(tree: Tree, at: Int): Element = TreeElement(tree, at)

    companion object {
        val linkStart: DelimiterType = LinkStart
        val imageStart: DelimiterType = ImageStart
    }
}

internal val EmphasisUnderscore: DelimiterType = object : DelimiterType {
    override val resolve: String = "Emphasis"
    override val mark: String = "EmphasisMark"
}
internal val EmphasisAsterisk: DelimiterType = object : DelimiterType {
    override val resolve: String = "Emphasis"
    override val mark: String = "EmphasisMark"
}
internal val LinkStart: DelimiterType = object : DelimiterType {}
internal val ImageStart: DelimiterType = object : DelimiterType {}
