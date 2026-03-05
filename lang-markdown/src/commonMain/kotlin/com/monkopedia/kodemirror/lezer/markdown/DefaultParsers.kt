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

// ---- Helper functions ----

private fun isFencedCode(line: Line): Int {
    if (line.next != 96 && line.next != 126) return -1 // '`' or '~'
    var pos = line.pos + 1
    while (pos < line.text.length && line.text[pos].code == line.next) pos++
    if (pos < line.pos + 3) return -1
    if (line.next == 96) {
        for (i in pos until line.text.length) {
            if (line.text[i].code == 96) return -1
        }
    }
    return pos
}

private fun isBlockquote(line: Line): Int {
    if (line.next != 62) return -1 // '>'
    return if (line.text.getOrNull(line.pos + 1)?.code == 32) 2 else 1
}

private fun isHorizontalRule(line: Line, cx: BlockContext, breaking: Boolean): Int {
    if (line.next != 42 && line.next != 45 && line.next != 95) return -1 // '*', '-', '_'
    var count = 1
    for (pos in (line.pos + 1) until line.text.length) {
        val ch = line.text[pos].code
        if (ch == line.next) {
            count++
        } else if (!space(ch)) return -1
    }
    if (breaking && line.next == 45 && isSetextUnderline(line) > -1 &&
        line.depth == cx.stack.size &&
        cx.parser.leafBlockParsers.indexOf(DefaultLeafBlocks["SetextHeading"]) > -1
    ) {
        return -1
    }
    return if (count < 3) -1 else 1
}

private fun inList(cx: BlockContext, type: Int): Boolean {
    for (i in cx.stack.indices.reversed()) {
        if (cx.stack[i].type == type) return true
    }
    return false
}

private fun isBulletList(line: Line, cx: BlockContext, breaking: Boolean): Int {
    return if ((line.next == 45 || line.next == 43 || line.next == 42) &&
        (line.pos == line.text.length - 1 || space(line.text[line.pos + 1].code)) &&
        (
            !breaking || inList(cx, Type.BulletList) ||
                line.skipSpace(line.pos + 2) < line.text.length
            )
    ) {
        1
    } else {
        -1
    }
}

private fun isOrderedList(line: Line, cx: BlockContext, breaking: Boolean): Int {
    var pos = line.pos
    var next = line.next
    while (true) {
        if (next in 48..57) {
            pos++ // '0'-'9'
        } else {
            break
        }
        if (pos == line.text.length) return -1
        next = line.text[pos].code
    }
    if (pos == line.pos || pos > line.pos + 9 ||
        (next != 46 && next != 41) || // '.' or ')'
        (pos < line.text.length - 1 && !space(line.text[pos + 1].code)) ||
        (
            breaking && !inList(cx, Type.OrderedList) &&
                (
                    line.skipSpace(pos + 1) == line.text.length ||
                        pos > line.pos + 1 || line.next != 49
                    )
            ) // '1'
    ) {
        return -1
    }
    return pos + 1 - line.pos
}

private fun isAtxHeading(line: Line): Int {
    if (line.next != 35) return -1 // '#'
    var pos = line.pos + 1
    while (pos < line.text.length && line.text[pos].code == 35) pos++
    if (pos < line.text.length && line.text[pos].code != 32) return -1
    val size = pos - line.pos
    return if (size > 6) -1 else size
}

internal fun isSetextUnderline(line: Line): Int {
    if (line.next != 45 && line.next != 61 || line.indent >= line.baseIndent + 4) return -1
    var pos = line.pos + 1
    while (pos < line.text.length && line.text[pos].code == line.next) pos++
    val end = pos
    while (pos < line.text.length && space(line.text[pos].code)) pos++
    return if (pos == line.text.length) end else -1
}

private val EmptyLine = Regex("^[ \\t]*$")
private val CommentEnd = Regex("-->")
private val ProcessingEnd = Regex("\\?>")

private val HTMLBlockStyle: List<Pair<Regex, Regex>> = listOf(
    Regex("^<(?:script|pre|style)(?:\\s|>|$)", RegexOption.IGNORE_CASE) to
        Regex("</(script|pre|style)>", RegexOption.IGNORE_CASE),
    Regex("^\\s*<!--") to CommentEnd,
    Regex("^\\s*<\\?") to ProcessingEnd,
    Regex("^\\s*<![A-Z]") to Regex(">"),
    Regex("^\\s*<!\\[CDATA\\[") to Regex("]]>"),
    Regex(
        "^\\s*</?(?:address|article|aside|base|basefont|blockquote|body|caption|center|" +
            "col|colgroup|dd|details|dialog|dir|div|dl|dt|fieldset|figcaption|figure|footer|" +
            "form|frame|frameset|h1|h2|h3|h4|h5|h6|head|header|hr|html|iframe|legend|li|link|" +
            "main|menu|menuitem|nav|noframes|ol|optgroup|option|p|param|section|source|summary|" +
            "table|tbody|td|tfoot|th|thead|title|tr|track|ul)(?:\\s|/?>|$)",
        RegexOption.IGNORE_CASE
    ) to EmptyLine,
    Regex(
        "^\\s*(?:</[a-z][\\w-]*\\s*>|<[a-z][\\w-]*(\\s+[a-z:_][\\w-.]*" +
            "(?:\\s*=\\s*(?:[^\\s\"'=<>`]+|'[^']*'|\"[^\"]*\"))?)*\\s*>)\\s*$",
        RegexOption.IGNORE_CASE
    ) to EmptyLine
)

private fun isHTMLBlock(line: Line, cx: BlockContext, breaking: Boolean): Int {
    if (line.next != 60) return -1 // '<'
    val rest = line.text.substring(line.pos)
    val limit = HTMLBlockStyle.size - (if (breaking) 1 else 0)
    for (i in 0 until limit) {
        if (HTMLBlockStyle[i].first.containsMatchIn(rest)) return i
    }
    return -1
}

private fun getListIndent(line: Line, pos: Int): Int {
    val indentAfter = line.countIndent(pos, line.pos, line.indent)
    val indented = line.countIndent(line.skipSpace(pos), pos, indentAfter)
    return if (indented >= indentAfter + 5) indentAfter + 1 else indented
}

private fun addCodeText(marks: MutableList<Element>, from: Int, to: Int) {
    val last = marks.size - 1
    if (last >= 0 && marks[last].to == from && marks[last].type == Type.CodeText) {
        // Extend existing CodeText element
        marks[last] = Element(Type.CodeText, marks[last].from, to)
    } else {
        marks.add(elt(Type.CodeText, from, to))
    }
}

// ---- Skip markup for composite blocks ----

private fun skipForList(bl: CompositeBlock, cx: BlockContext, line: Line): Boolean {
    if (line.pos == line.text.length ||
        (bl != cx.block && line.indent >= cx.stack[line.depth + 1].value + line.baseIndent)
    ) {
        return true
    }
    if (line.indent >= line.baseIndent + 4) return false
    val size = if (bl.type == Type.OrderedList) {
        isOrderedList(line, cx, false)
    } else {
        isBulletList(line, cx, false)
    }
    return size > 0 &&
        (bl.type != Type.BulletList || isHorizontalRule(line, cx, false) < 0) &&
        line.text[line.pos + size - 1].code == bl.value
}

internal val DefaultSkipMarkup: Map<Int, (CompositeBlock, BlockContext, Line) -> Boolean> = mapOf(
    Type.Blockquote to { _, cx, line ->
        if (line.next != 62) { // '>'
            false
        } else {
            line.markers.add(
                elt(Type.QuoteMark, cx.lineStart + line.pos, cx.lineStart + line.pos + 1)
            )
            val hasSpace = line.text.getOrNull(line.pos + 1)?.code?.let { space(it) } == true
            line.moveBase(line.pos + (if (hasSpace) 2 else 1))
            true
        }
    },
    Type.ListItem to { bl, _, line ->
        if (line.indent < line.baseIndent + bl.value && line.next > -1) {
            false
        } else {
            line.moveBaseColumn(line.baseIndent + bl.value)
            true
        }
    },
    Type.OrderedList to ::skipForList,
    Type.BulletList to ::skipForList,
    Type.Document to { _, _, _ -> true }
)

// ---- Default block parsers ----

internal val DefaultBlockParsers: Map<String, ((BlockContext, Line) -> BlockResult)?> = linkedMapOf(
    "LinkReference" to null,

    "IndentedCode" to { cx, line ->
        val base = line.baseIndent + 4
        if (line.indent < base) {
            false
        } else {
            val start = line.findColumn(base)
            val from = cx.lineStart + start
            var to = cx.lineStart + line.text.length
            val marks = mutableListOf<Element>()
            val pendingMarks = mutableListOf<Element>()
            addCodeText(marks, from, to)
            while (cx.nextLine() && line.depth >= cx.stack.size) {
                if (line.pos == line.text.length) {
                    addCodeText(pendingMarks, cx.lineStart - 1, cx.lineStart)
                    for (m in line.markers) pendingMarks.add(m)
                } else if (line.indent < base) {
                    break
                } else {
                    if (pendingMarks.isNotEmpty()) {
                        for (m in pendingMarks) {
                            if (m.type == Type.CodeText) {
                                addCodeText(marks, m.from, m.to)
                            } else {
                                marks.add(m)
                            }
                        }
                        pendingMarks.clear()
                    }
                    addCodeText(marks, cx.lineStart - 1, cx.lineStart)
                    for (m in line.markers) marks.add(m)
                    to = cx.lineStart + line.text.length
                    val codeStart = cx.lineStart + line.findColumn(line.baseIndent + 4)
                    if (codeStart < to) addCodeText(marks, codeStart, to)
                }
            }
            if (pendingMarks.isNotEmpty()) {
                val filtered = pendingMarks.filter { it.type != Type.CodeText }
                if (filtered.isNotEmpty()) {
                    line.markers = (filtered + line.markers).toMutableList()
                }
            }
            cx.addNode(
                cx.buffer.writeElements(marks, -from).finish(Type.CodeBlock, to - from),
                from
            )
            true
        }
    },

    "FencedCode" to { cx, line ->
        val fenceEnd = isFencedCode(line)
        if (fenceEnd < 0) {
            false
        } else {
            val from = cx.lineStart + line.pos
            val ch = line.next
            val len = fenceEnd - line.pos
            val infoFrom = line.skipSpace(fenceEnd)
            val infoTo = skipSpaceBack(line.text, line.text.length, infoFrom)
            val marks = mutableListOf<Element>()
            marks.add(elt(Type.CodeMark, from, from + len))
            if (infoFrom < infoTo) {
                marks.add(elt(Type.CodeInfo, cx.lineStart + infoFrom, cx.lineStart + infoTo))
            }

            var first = true
            var empty = true
            var hasLine = false
            while (cx.nextLine() && line.depth >= cx.stack.size) {
                var i = line.pos
                if (line.indent - line.baseIndent < 4) {
                    while (i < line.text.length && line.text[i].code == ch) i++
                }
                if (i - line.pos >= len && line.skipSpace(i) == line.text.length) {
                    for (m in line.markers) marks.add(m)
                    if (empty && hasLine) addCodeText(marks, cx.lineStart - 1, cx.lineStart)
                    marks.add(elt(Type.CodeMark, cx.lineStart + line.pos, cx.lineStart + i))
                    cx.nextLine()
                    break
                } else {
                    hasLine = true
                    if (!first) {
                        addCodeText(marks, cx.lineStart - 1, cx.lineStart)
                        empty = false
                    }
                    for (m in line.markers) marks.add(m)
                    val textStart = cx.lineStart + line.basePos
                    val textEnd = cx.lineStart + line.text.length
                    if (textStart < textEnd) {
                        addCodeText(marks, textStart, textEnd)
                        empty = false
                    }
                }
                first = false
            }
            cx.addNode(
                cx.buffer.writeElements(marks, -from)
                    .finish(Type.FencedCode, cx.prevLineEnd() - from),
                from
            )
            true
        }
    },

    "Blockquote" to { cx, line ->
        val size = isBlockquote(line)
        if (size < 0) {
            false
        } else {
            cx.startContext(Type.Blockquote, line.pos)
            cx.addNode(Type.QuoteMark, cx.lineStart + line.pos, cx.lineStart + line.pos + 1)
            line.moveBase(line.pos + size)
            null
        }
    },

    "HorizontalRule" to { cx, line ->
        if (isHorizontalRule(line, cx, false) < 0) {
            false
        } else {
            val from = cx.lineStart + line.pos
            cx.nextLine()
            cx.addNode(Type.HorizontalRule, from)
            true
        }
    },

    "BulletList" to { cx, line ->
        val size = isBulletList(line, cx, false)
        if (size < 0) {
            false
        } else {
            if (cx.block.type != Type.BulletList) {
                cx.startContext(Type.BulletList, line.basePos, line.next)
            }
            val newBase = getListIndent(line, line.pos + 1)
            cx.startContext(Type.ListItem, line.basePos, newBase - line.baseIndent)
            cx.addNode(Type.ListMark, cx.lineStart + line.pos, cx.lineStart + line.pos + size)
            line.moveBaseColumn(newBase)
            null
        }
    },

    "OrderedList" to { cx, line ->
        val size = isOrderedList(line, cx, false)
        if (size < 0) {
            false
        } else {
            if (cx.block.type != Type.OrderedList) {
                cx.startContext(Type.OrderedList, line.basePos, line.text[line.pos + size - 1].code)
            }
            val newBase = getListIndent(line, line.pos + size)
            cx.startContext(Type.ListItem, line.basePos, newBase - line.baseIndent)
            cx.addNode(Type.ListMark, cx.lineStart + line.pos, cx.lineStart + line.pos + size)
            line.moveBaseColumn(newBase)
            null
        }
    },

    "ATXHeading" to { cx, line ->
        val size = isAtxHeading(line)
        if (size < 0) {
            false
        } else {
            val off = line.pos
            val from = cx.lineStart + off
            val endOfSpace = skipSpaceBack(line.text, line.text.length, off)
            var after = endOfSpace
            while (after > off && line.text[after - 1].code == line.next) after--
            if (after == endOfSpace || after == off || !space(line.text[after - 1].code)) {
                after = line.text.length
            }
            val buf = cx.buffer
                .write(Type.HeaderMark, 0, size)
                .writeElements(
                    cx.parser.parseInline(
                        line.text.substring(off + size + 1, after),
                        from + size + 1
                    ),
                    -from
                )
            if (after < line.text.length) {
                buf.write(Type.HeaderMark, after - off, endOfSpace - off)
            }
            val node = buf.finish(Type.ATXHeading1 - 1 + size, line.text.length - off)
            cx.nextLine()
            cx.addNode(node, from)
            true
        }
    },

    "HTMLBlock" to { cx, line ->
        val type = isHTMLBlock(line, cx, false)
        if (type < 0) {
            false
        } else {
            val from = cx.lineStart + line.pos
            val end = HTMLBlockStyle[type].second
            val marks = mutableListOf<Element>()
            var trailing = end != EmptyLine
            while (!end.containsMatchIn(line.text) && cx.nextLine()) {
                if (line.depth < cx.stack.size) {
                    trailing = false
                    break
                }
                for (m in line.markers) marks.add(m)
            }
            if (trailing) cx.nextLine()
            val nodeType = when (end) {
                CommentEnd -> Type.CommentBlock
                ProcessingEnd -> Type.ProcessingInstructionBlock
                else -> Type.HTMLBlock
            }
            val to = cx.prevLineEnd()
            cx.addNode(
                cx.buffer.writeElements(marks, -from).finish(nodeType, to - from),
                from
            )
            true
        }
    },

    "SetextHeading" to null
)

// ---- Default leaf block parsers ----

private const val REF_STAGE_FAILED = -1
private const val REF_STAGE_START = 0
private const val REF_STAGE_LABEL = 1
private const val REF_STAGE_LINK = 2
private const val REF_STAGE_TITLE = 3

private class LinkReferenceParser(leaf: LeafBlock) : LeafBlockParser {
    var stage = REF_STAGE_START
    val elts = mutableListOf<Element>()
    var pos = 0
    val start: Int = leaf.start

    init {
        advance(leaf.content)
    }

    override fun nextLine(cx: BlockContext, line: Line, leaf: LeafBlock): Boolean {
        if (stage == REF_STAGE_FAILED) return false
        val content = leaf.content + "\n" + line.scrub()
        val finish = advance(content)
        if (finish > -1 && finish < content.length) return complete(cx, leaf, finish)
        return false
    }

    override fun finish(cx: BlockContext, leaf: LeafBlock): Boolean {
        if ((stage == REF_STAGE_LINK || stage == REF_STAGE_TITLE) &&
            skipSpaceFn(leaf.content, pos) == leaf.content.length
        ) {
            return complete(cx, leaf, leaf.content.length)
        }
        return false
    }

    private fun complete(cx: BlockContext, leaf: LeafBlock, len: Int): Boolean {
        cx.addLeafElement(
            leaf,
            elt(Type.LinkReference, start, start + len, elts)
        )
        return true
    }

    private fun nextStage(element: Element?): Boolean {
        if (element != null) {
            pos = element.to - start
            elts.add(element)
            stage++
            return true
        }
        return false
    }

    private fun nextStageFailed(failed: Boolean): Boolean {
        if (failed) stage = REF_STAGE_FAILED
        return false
    }

    private fun advance(content: String): Int {
        while (true) {
            when (stage) {
                REF_STAGE_FAILED -> return -1
                REF_STAGE_START -> {
                    val label = parseLinkLabel(content, pos, start, true)
                    if (label == null) {
                        stage = REF_STAGE_FAILED
                        return -1
                    }
                    if (!nextStage(label)) return -1
                    if (pos >= content.length || content[pos].code != 58) { // ':'
                        stage = REF_STAGE_FAILED
                        return -1
                    }
                    elts.add(elt(Type.LinkMark, pos + start, pos + start + 1))
                    pos++
                }
                REF_STAGE_LABEL -> {
                    val url = parseURL(content, skipSpaceFn(content, pos), start)
                    if (url == null) return -1
                    if (!nextStage(url)) return -1
                }
                REF_STAGE_LINK -> {
                    val skip = skipSpaceFn(content, pos)
                    var end = 0
                    if (skip > pos) {
                        val title = parseLinkTitle(content, skip, start)
                        if (title != null) {
                            val titleEnd = lineEnd(content, title.to - start)
                            if (titleEnd > 0) {
                                nextStage(title)
                                end = titleEnd
                            }
                        }
                    }
                    if (end == 0) end = lineEnd(content, pos)
                    return if (end > 0 && end < content.length) end else -1
                }
                else -> { // REF_STAGE_TITLE
                    return lineEnd(content, pos)
                }
            }
        }
    }
}

private fun lineEnd(text: String, pos: Int): Int {
    var p = pos
    while (p < text.length) {
        val next = text[p].code
        if (next == 10) break // '\n'
        if (!space(next)) return -1
        p++
    }
    return p
}

private class SetextHeadingParser : LeafBlockParser {
    override fun nextLine(cx: BlockContext, line: Line, leaf: LeafBlock): Boolean {
        val underline = if (line.depth < cx.stack.size) -1 else isSetextUnderline(line)
        val next = line.next
        if (underline < 0) return false
        val underlineMark = elt(Type.HeaderMark, cx.lineStart + line.pos, cx.lineStart + underline)
        cx.nextLine()
        cx.addLeafElement(
            leaf,
            elt(
                if (next == 61) Type.SetextHeading1 else Type.SetextHeading2,
                leaf.start,
                cx.prevLineEnd(),
                cx.parser.parseInline(leaf.content, leaf.start) + underlineMark
            )
        )
        return true
    }

    override fun finish(cx: BlockContext, leaf: LeafBlock): Boolean = false
}

internal val DefaultLeafBlocks: Map<String, (BlockContext, LeafBlock) -> LeafBlockParser?> =
    mapOf(
        "LinkReference" to { _, leaf ->
            if (leaf.content.isNotEmpty() && leaf.content[0].code == 91) { // '['
                LinkReferenceParser(leaf)
            } else {
                null
            }
        },
        "SetextHeading" to { _, _ -> SetextHeadingParser() }
    )

internal val DefaultEndLeaf: List<(BlockContext, Line, LeafBlock) -> Boolean> = listOf(
    { _, line, _ -> isAtxHeading(line) >= 0 },
    { _, line, _ -> isFencedCode(line) >= 0 },
    { _, line, _ -> isBlockquote(line) >= 0 },
    { p, line, _ -> isBulletList(line, p, true) >= 0 },
    { p, line, _ -> isOrderedList(line, p, true) >= 0 },
    { p, line, _ -> isHorizontalRule(line, p, true) >= 0 },
    { p, line, _ -> isHTMLBlock(line, p, true) >= 0 }
)

// ---- Default inline parsers ----

private val Escapable = "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

internal val PunctuationRegex =
    Regex("[!\"#\$%&'()*+,\\-./:;<=>?@\\[\\\\\\]^_`{|}~\\xA1\\u2010-\\u2027]")

internal val DefaultInline: Map<String, (InlineContext, Int, Int) -> Int> = linkedMapOf(
    "Escape" to { cx, next, start ->
        if (next != 92 || start == cx.end - 1) {
            -1 // '\\'
        } else {
            val escaped = cx.char(start + 1)
            var found = false
            for (i in Escapable.indices) {
                if (Escapable[i].code == escaped) {
                    found = true
                    break
                }
            }
            if (found) {
                cx.append(elt(Type.Escape, start, start + 2))
            } else {
                -1
            }
        }
    },

    "Entity" to { cx, next, start ->
        if (next != 38) {
            -1 // '&'
        } else {
            val sub = cx.slice(start + 1, minOf(start + 31, cx.end))
            val m = Regex("^(?:#\\d+|#x[a-fA-F\\d]+|\\w+);").find(sub)
            if (m != null) {
                cx.append(elt(Type.Entity, start, start + 1 + m.value.length))
            } else {
                -1
            }
        }
    },

    "InlineCode" to { cx, next, start ->
        if (next != 96 || (start > cx.offset && cx.char(start - 1) == 96)) {
            -1 // '`'
        } else {
            var pos = start + 1
            while (pos < cx.end && cx.char(pos) == 96) pos++
            val size = pos - start
            var curSize = 0
            var result = -1
            while (pos < cx.end) {
                if (cx.char(pos) == 96) {
                    curSize++
                    if (curSize == size && cx.char(pos + 1) != 96) {
                        result = cx.append(
                            elt(
                                Type.InlineCode, start, pos + 1,
                                listOf(
                                    elt(Type.CodeMark, start, start + size),
                                    elt(Type.CodeMark, pos + 1 - size, pos + 1)
                                )
                            )
                        )
                        break
                    }
                } else {
                    curSize = 0
                }
                pos++
            }
            result
        }
    },

    "HTMLTag" to { cx, next, start ->
        if (next != 60 || start == cx.end - 1) {
            -1 // '<'
        } else {
            val after = cx.slice(start + 1, cx.end)
            val url = Regex(
                "^(?:[a-z][-\\w+.]+:[^\\s>]+|[a-z\\d.!#\$%&'*+/=?^_`{|}~-]+@[a-z\\d]" +
                    "(?:[a-z\\d-]{0,61}[a-z\\d])?(?:\\.[a-z\\d](?:[a-z\\d-]{0,61}[a-z\\d])?)*)>",
                RegexOption.IGNORE_CASE
            ).find(after)
            if (url != null) {
                cx.append(
                    elt(
                        Type.Autolink, start, start + 1 + url.value.length,
                        listOf(
                            elt(Type.LinkMark, start, start + 1),
                            elt(Type.URL, start + 1, start + url.value.length),
                            elt(
                                Type.LinkMark,
                                start + url.value.length,
                                start + 1 + url.value.length
                            )
                        )
                    )
                )
            } else {
                val comment =
                    Regex("^!--[^>](?:-[^-]|[^-])*?-->", RegexOption.IGNORE_CASE).find(after)
                if (comment != null) {
                    cx.append(elt(Type.Comment, start, start + 1 + comment.value.length))
                } else {
                    val procInst = Regex("^\\?[\\s\\S]*?\\?>").find(after)
                    if (procInst != null) {
                        cx.append(
                            elt(
                                Type.ProcessingInstruction,
                                start,
                                start + 1 + procInst.value.length
                            )
                        )
                    } else {
                        val m = Regex(
                            "^(?:![A-Z][\\s\\S]*?>|!\\[CDATA\\[[\\s\\S]*?]]>|" +
                                "/\\s*[a-zA-Z][\\w-]*\\s*>|" +
                                "\\s*[a-zA-Z][\\w-]*(\\s+[a-zA-Z:_][\\w-.:]*" +
                                "(?:\\s*=\\s*(?:[^\\s\"'=<>`]+" +
                                "|'[^']*'|\"[^\"]*\"))?)*\\s*(/\\s*)?>)"
                        ).find(after)
                        if (m != null) {
                            cx.append(elt(Type.HTMLTag, start, start + 1 + m.value.length))
                        } else {
                            -1
                        }
                    }
                }
            }
        }
    },

    "Emphasis" to { cx, next, start ->
        if (next != 95 && next != 42) {
            -1 // '_' or '*'
        } else {
            var pos = start + 1
            while (cx.char(pos) == next) pos++
            val before = cx.slice(maxOf(start - 1, cx.offset), start)
            val after = cx.slice(pos, minOf(pos + 1, cx.end))
            val pBefore = PunctuationRegex.containsMatchIn(before)
            val pAfter = PunctuationRegex.containsMatchIn(after)
            val sBefore = before.isEmpty() || Regex("\\s").containsMatchIn(before)
            val sAfter = after.isEmpty() || Regex("\\s").containsMatchIn(after)
            val leftFlanking = !sAfter && (!pAfter || sBefore || pBefore)
            val rightFlanking = !sBefore && (!pBefore || sAfter || pAfter)
            val canOpen = leftFlanking && (next == 42 || !rightFlanking || pBefore)
            val canClose = rightFlanking && (next == 42 || !leftFlanking || pAfter)
            cx.append(
                InlineDelimiter(
                    if (next == 95) EmphasisUnderscore else EmphasisAsterisk,
                    start, pos,
                    (if (canOpen) Mark.OPEN else Mark.NONE) or
                        (if (canClose) Mark.CLOSE else Mark.NONE)
                )
            )
        }
    },

    "HardBreak" to { cx, next, start ->
        if (next == 92 && cx.char(start + 1) == 10) { // '\\' '\n'
            cx.append(elt(Type.HardBreak, start, start + 2))
        } else if (next == 32) { // ' '
            var pos = start + 1
            while (cx.char(pos) == 32) pos++
            if (cx.char(pos) == 10 && pos >= start + 2) {
                cx.append(elt(Type.HardBreak, start, pos + 1))
            } else {
                -1
            }
        } else {
            -1
        }
    },

    "Link" to { cx, next, start ->
        if (next == 91) {
            cx.append(InlineDelimiter(LinkStart, start, start + 1, Mark.OPEN)) // '['
        } else {
            -1
        }
    },

    "Image" to { cx, next, start ->
        if (next == 33 && cx.char(start + 1) == 91) { // '!' '['
            cx.append(InlineDelimiter(ImageStart, start, start + 2, Mark.OPEN))
        } else {
            -1
        }
    },

    "LinkEnd" to { cx, next, start ->
        if (next != 93) {
            -1 // ']'
        } else {
            var result = -1
            for (i in cx.parts.indices.reversed()) {
                val part = cx.parts[i]
                if (part is InlineDelimiter &&
                    (part.type == LinkStart || part.type == ImageStart)
                ) {
                    if (part.side == Mark.NONE ||
                        (
                            cx.skipSpace(part.to) == start &&
                                !Regex("[([)]").containsMatchIn(
                                    cx.slice(start + 1, minOf(start + 2, cx.end))
                                )
                            )
                    ) {
                        cx.parts[i] = null
                        result = -1
                    } else {
                        val content = cx.takeContent(i)
                        val link = finishLink(
                            cx, content,
                            if (part.type == LinkStart) Type.Link else Type.Image,
                            part.from, start + 1
                        )
                        cx.parts.add(link)
                        if (part.type == LinkStart) {
                            for (j in 0 until i) {
                                val p = cx.parts[j]
                                if (p is InlineDelimiter && p.type == LinkStart) {
                                    p.side = Mark.NONE
                                }
                            }
                        }
                        result = link.to
                    }
                    break
                }
            }
            result
        }
    }
)

private fun finishLink(
    cx: InlineContext,
    content: List<Element>,
    type: Int,
    start: Int,
    startPos: Int
): Element {
    val text = cx.text
    var next = cx.char(startPos)
    var endPos = startPos
    val elts = content.toMutableList()
    elts.add(0, elt(Type.LinkMark, start, start + (if (type == Type.Image) 2 else 1)))
    elts.add(elt(Type.LinkMark, startPos - 1, startPos))
    if (next == 40) { // '('
        var pos = cx.skipSpace(startPos + 1)
        val dest = parseURL(text, pos - cx.offset, cx.offset)
        var title: Element? = null
        if (dest != null) {
            pos = cx.skipSpace(dest.to)
            if (pos != dest.to) {
                title = parseLinkTitle(text, pos - cx.offset, cx.offset)
                if (title != null) pos = cx.skipSpace(title.to)
            }
        }
        if (cx.char(pos) == 41) { // ')'
            elts.add(elt(Type.LinkMark, startPos, startPos + 1))
            endPos = pos + 1
            if (dest != null) elts.add(dest)
            if (title != null) elts.add(title)
            elts.add(elt(Type.LinkMark, pos, endPos))
        }
    } else if (next == 91) { // '['
        val label = parseLinkLabel(text, startPos - cx.offset, cx.offset, false)
        if (label != null) {
            elts.add(label)
            endPos = label.to
        }
    }
    return elt(type, start, endPos, elts)
}

internal fun parseURL(text: String, start: Int, offset: Int): Element? {
    if (start >= text.length) return null
    val next = text[start].code
    if (next == 60) { // '<'
        for (pos in (start + 1) until text.length) {
            val ch = text[pos].code
            if (ch == 62) return elt(Type.URL, start + offset, pos + 1 + offset) // '>'
            if (ch == 60 || ch == 10) return null // '<' or '\n'
        }
        return null
    } else {
        var depth = 0
        var pos = start
        var escaped = false
        while (pos < text.length) {
            val ch = text[pos].code
            if (space(ch)) {
                break
            } else if (escaped) {
                escaped = false
            } else if (ch == 40) { // '('
                depth++
            } else if (ch == 41) { // ')'
                if (depth == 0) break
                depth--
            } else if (ch == 92) { // '\\'
                escaped = true
            }
            pos++
        }
        return if (pos > start) {
            elt(Type.URL, start + offset, pos + offset)
        } else {
            null
        }
    }
}

internal fun parseLinkTitle(text: String, start: Int, offset: Int): Element? {
    if (start >= text.length) return null
    val next = text[start].code
    if (next != 39 && next != 34 && next != 40) return null // '"', '\'', '('
    val end = if (next == 40) 41 else next
    var escaped = false
    for (pos in (start + 1) until text.length) {
        val ch = text[pos].code
        if (escaped) {
            escaped = false
        } else if (ch == end) {
            return elt(Type.LinkTitle, start + offset, pos + 1 + offset)
        } else if (ch == 92) { // '\\'
            escaped = true
        }
    }
    return null
}

internal fun parseLinkLabel(
    text: String,
    start: Int,
    offset: Int,
    requireNonWS: Boolean
): Element? {
    var nonWS = requireNonWS
    var escaped = false
    val end = minOf(text.length, start + 1 + 999)
    for (pos in (start + 1) until end) {
        val ch = text[pos].code
        if (escaped) {
            escaped = false
        } else if (ch == 93) { // ']'
            return if (nonWS) {
                null
            } else {
                elt(Type.LinkLabel, start + offset, pos + 1 + offset)
            }
        } else {
            if (nonWS && !space(ch)) nonWS = false
            if (ch == 91) {
                return null // '['
            } else if (ch == 92) escaped = true // '\\'
        }
    }
    return null
}
