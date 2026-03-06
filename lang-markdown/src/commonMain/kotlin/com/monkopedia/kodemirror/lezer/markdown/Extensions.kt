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

import com.monkopedia.kodemirror.lezer.highlight.Tags as t

// ---- Strikethrough ----

private val StrikethroughDelim = object : DelimiterType {
    override val resolve: String = "Strikethrough"
    override val mark: String = "StrikethroughMark"
}

val Strikethrough: MarkdownExtension = markdownExtensionOf(
    MarkdownConfig(
        defineNodes = listOf(
            SimpleNodeSpec("Strikethrough", block = false, style = t.strikethrough),
            SimpleNodeSpec("StrikethroughMark", block = false, style = t.processingInstruction)
        ),
        parseInline = listOf(
            object : InlineParser {
                override val name = "Strikethrough"
                override val after = "Emphasis"
                override fun parse(cx: InlineContext, next: Int, pos: Int): Int {
                    if (next != 126 || cx.char(pos + 1) != 126 || cx.char(pos + 2) == 126) {
                        return -1 // '~'
                    }
                    val before = cx.slice(maxOf(pos - 1, cx.offset), pos)
                    val after = cx.slice(pos + 2, minOf(pos + 3, cx.end))
                    val sBefore = before.isEmpty() || Regex("\\s").containsMatchIn(before)
                    val sAfter = after.isEmpty() || Regex("\\s").containsMatchIn(after)
                    val pBefore = PunctuationRegex.containsMatchIn(before)
                    val pAfter = PunctuationRegex.containsMatchIn(after)
                    val canOpen = !sAfter && (!pAfter || sBefore || pBefore)
                    val canClose = !sBefore && (!pBefore || sAfter || pAfter)
                    return cx.addDelimiter(StrikethroughDelim, pos, pos + 2, canOpen, canClose)
                }
            }
        )
    )
)

// ---- Table ----

private fun parseRow(
    cx: InlineContext,
    line: String,
    startI: Int = 0,
    elts: MutableList<Element>? = null,
    offset: Int = 0
): Int {
    var count = 0
    var i = startI
    var escaped = false
    var start = -1
    while (i < line.length) {
        val next = line[i].code
        if (next == 124 && !escaped) { // '|'
            if (elts != null) {
                val cell = elt(
                    cx.parser.getNodeType("TableCell"),
                    if (start < 0) i + offset else start + offset,
                    i + offset
                )
                elts.add(cell)
            }
            count++
            start = i + 1
        } else if (escaped) {
            escaped = false
        } else if (next == 92) { // '\\'
            escaped = true
        } else if (start < 0) {
            start = i
        }
        i++
    }
    if (start >= 0 && elts != null) {
        elts.add(
            elt(cx.parser.getNodeType("TableCell"), start + offset, line.length + offset)
        )
    }
    return count
}

private fun hasPipe(line: String, startI: Int): Boolean {
    var escaped = false
    for (i in startI until line.length) {
        val next = line[i].code
        if (next == 124 && !escaped) return true // '|'
        if (next == 92) {
            escaped = !escaped // '\\'
        } else {
            escaped = false
        }
    }
    return false
}

val Table: MarkdownExtension = markdownExtensionOf(
    MarkdownConfig(
        defineNodes = listOf(
            SimpleNodeSpec("Table", block = true),
            SimpleNodeSpec("TableHeader", block = true),
            SimpleNodeSpec("TableRow", block = true),
            SimpleNodeSpec("TableCell", block = false),
            SimpleNodeSpec("TableDelimiter", block = false, style = t.processingInstruction)
        ),
        parseBlock = listOf(
            object : BlockParser {
                override val name = "Table"
                override val leaf: ((BlockContext, LeafBlock) -> LeafBlockParser?) = { cx, leaf ->
                    if (!hasPipe(leaf.content, 0)) {
                        null
                    } else {
                        TableParserObj(cx, leaf)
                    }
                }
                override val endLeaf: ((BlockContext, Line, LeafBlock) -> Boolean) = { _, line, _ ->
                    !hasPipe(line.text, line.pos)
                }
            }
        )
    )
)

private class TableParserObj(cx: BlockContext, leaf: LeafBlock) : LeafBlockParser {
    private var columns: Int = -1

    init {
        // Count columns from first line
        val inlineCx = InlineContext(cx.parser, leaf.content, leaf.start)
        columns = parseRow(inlineCx, leaf.content)
    }

    override fun nextLine(cx: BlockContext, line: Line, leaf: LeafBlock): Boolean {
        if (columns == -1) return false
        leaf.content += "\n" + line.scrub()
        for (m in line.markers) leaf.marks.add(m)
        val lines = leaf.content.split("\n")
        if (lines.size < 2) return false
        // Check delimiter line
        val delimLine = lines[1].trim()
        if (!isDelimiterLine(delimLine)) {
            columns = -1
            return false
        }
        // Consume all remaining table lines
        while (cx.nextLine()) {
            if (!hasPipe(line.text, line.pos)) break
            leaf.content += "\n" + line.scrub()
            for (m in line.markers) leaf.marks.add(m)
        }
        finishTable(cx, leaf)
        return true
    }

    override fun finish(cx: BlockContext, leaf: LeafBlock): Boolean {
        if (columns == -1) return false
        val lines = leaf.content.split("\n")
        if (lines.size < 2) return false
        if (!isDelimiterLine(lines[1].trim())) return false
        finishTable(cx, leaf)
        return true
    }

    private fun isDelimiterLine(line: String): Boolean {
        return Regex("^[|\\s:-]+$").matches(line) && line.contains('-')
    }

    private fun finishTable(cx: BlockContext, leaf: LeafBlock) {
        val lines = leaf.content.split("\n")
        val elts = mutableListOf<Element>()
        val inlineCx = InlineContext(cx.parser, leaf.content, leaf.start)

        var offset = leaf.start
        // Header
        if (lines.isNotEmpty()) {
            val headerElts = mutableListOf<Element>()
            parseRow(inlineCx, lines[0], elts = headerElts, offset = offset)
            elts.add(
                elt(
                    cx.parser.getNodeType("TableHeader"),
                    offset,
                    offset + lines[0].length,
                    headerElts
                )
            )
            offset += lines[0].length + 1
        }
        // Delimiter
        if (lines.size > 1) {
            elts.add(
                elt(
                    cx.parser.getNodeType("TableDelimiter"),
                    offset,
                    offset + lines[1].length
                )
            )
            offset += lines[1].length + 1
        }
        // Rows
        for (i in 2 until lines.size) {
            val rowElts = mutableListOf<Element>()
            parseRow(inlineCx, lines[i], elts = rowElts, offset = offset)
            elts.add(
                elt(
                    cx.parser.getNodeType("TableRow"),
                    offset,
                    offset + lines[i].length,
                    rowElts
                )
            )
            offset += lines[i].length + 1
        }

        cx.addLeafElement(
            leaf,
            elt(
                cx.parser.getNodeType("Table"),
                leaf.start,
                leaf.start + leaf.content.length,
                elts
            )
        )
    }
}

// ---- TaskList ----

val TaskList: MarkdownExtension = markdownExtensionOf(
    MarkdownConfig(
        defineNodes = listOf(
            SimpleNodeSpec("Task", block = false),
            SimpleNodeSpec("TaskMarker", block = false, style = t.atom)
        ),
        parseBlock = listOf(
            object : BlockParser {
                override val name = "TaskList"
                override val leaf: ((BlockContext, LeafBlock) -> LeafBlockParser?) = { cx, leaf ->
                    if (Regex("^\\[[ xX]][ \\t]").find(leaf.content) != null &&
                        cx.parentType().name == "ListItem"
                    ) {
                        TaskListParser()
                    } else {
                        null
                    }
                }
            }
        )
    )
)

private class TaskListParser : LeafBlockParser {
    override fun nextLine(cx: BlockContext, line: Line, leaf: LeafBlock): Boolean = false

    override fun finish(cx: BlockContext, leaf: LeafBlock): Boolean {
        val inline = cx.parser.parseInline(leaf.content.substring(3), leaf.start + 3)
        cx.addLeafElement(
            leaf,
            elt(
                cx.parser.getNodeType("Task"),
                leaf.start,
                leaf.start + leaf.content.length,
                listOf(
                    elt(cx.parser.getNodeType("TaskMarker"), leaf.start, leaf.start + 3)
                ) + inline
            )
        )
        return true
    }
}

// ---- Autolink ----

@Suppress("ktlint:standard:max-line-length")
private val autolinkEmailRe =
    Regex(
        "[\\w.!#\$%&'*+/=?^`{|}~-]+@[a-zA-Z\\d](?:[a-zA-Z\\d-]{0,61}[a-zA-Z\\d])?(?:\\.[a-zA-Z\\d](?:[a-zA-Z\\d-]{0,61}[a-zA-Z\\d])?)*"
    )
private val autolinkUrlRe =
    Regex("(?:www\\.|https?://)(?:[^\\s<>]*(?:\\([^\\s<>]*\\)[^\\s<>]*)*[^\\s<>.,;:!?\"')\\]]|)")

val Autolink: MarkdownExtension = markdownExtensionOf(
    MarkdownConfig(
        defineNodes = listOf(
            SimpleNodeSpec("Autolink", block = false, style = t.link)
        ),
        parseInline = listOf(
            object : InlineParser {
                override val name = "Autolink"
                override val after = "Emphasis"
                override fun parse(cx: InlineContext, next: Int, pos: Int): Int {
                    // Check for URL autolinks
                    if (next == 119 || next == 87) { // 'w' or 'W'
                        val rest = cx.slice(pos, cx.end)
                        val m = autolinkUrlRe.find(rest)
                        if (m != null && m.range.first == 0) {
                            return cx.addElement(
                                elt(
                                    cx.parser.getNodeType("Autolink"),
                                    pos,
                                    pos + m.value.length,
                                    listOf(elt(Type.URL, pos, pos + m.value.length))
                                )
                            )
                        }
                    }
                    if (next == 104 || next == 72) { // 'h' or 'H'
                        val rest = cx.slice(pos, cx.end)
                        val m = autolinkUrlRe.find(rest)
                        if (m != null && m.range.first == 0) {
                            return cx.addElement(
                                elt(
                                    cx.parser.getNodeType("Autolink"),
                                    pos,
                                    pos + m.value.length,
                                    listOf(elt(Type.URL, pos, pos + m.value.length))
                                )
                            )
                        }
                    }
                    return -1
                }
            }
        )
    )
)

// ---- Superscript/Subscript ----

private val SuperscriptDelim = object : DelimiterType {
    override val resolve: String = "Superscript"
    override val mark: String = "SuperscriptMark"
}

val Superscript: MarkdownExtension = markdownExtensionOf(
    MarkdownConfig(
        defineNodes = listOf(
            SimpleNodeSpec("Superscript", block = false),
            SimpleNodeSpec("SuperscriptMark", block = false, style = t.processingInstruction)
        ),
        parseInline = listOf(
            object : InlineParser {
                override val name = "Superscript"
                override val after = "Emphasis"
                override fun parse(cx: InlineContext, next: Int, pos: Int): Int {
                    if (next != 94 || cx.char(pos + 1) == 94) return -1 // '^'
                    val canOpen = !space(cx.char(pos + 1))
                    val before = if (pos > cx.offset) cx.char(pos - 1) else -1
                    val canClose = !space(before) && before != -1
                    return cx.addDelimiter(SuperscriptDelim, pos, pos + 1, canOpen, canClose)
                }
            }
        )
    )
)

private val SubscriptDelim = object : DelimiterType {
    override val resolve: String = "Subscript"
    override val mark: String = "SubscriptMark"
}

val Subscript: MarkdownExtension = markdownExtensionOf(
    MarkdownConfig(
        defineNodes = listOf(
            SimpleNodeSpec("Subscript", block = false),
            SimpleNodeSpec("SubscriptMark", block = false, style = t.processingInstruction)
        ),
        parseInline = listOf(
            object : InlineParser {
                override val name = "Subscript"
                override val after = "Emphasis"
                override fun parse(cx: InlineContext, next: Int, pos: Int): Int {
                    if (next != 126 || cx.char(pos + 1) == 126) return -1 // '~'
                    val canOpen = !space(cx.char(pos + 1))
                    val before = if (pos > cx.offset) cx.char(pos - 1) else -1
                    val canClose = !space(before) && before != -1
                    return cx.addDelimiter(SubscriptDelim, pos, pos + 1, canOpen, canClose)
                }
            }
        )
    )
)

// ---- Emoji ----

val Emoji: MarkdownExtension = markdownExtensionOf(
    MarkdownConfig(
        defineNodes = listOf(
            SimpleNodeSpec("Emoji", block = false, style = t.character)
        ),
        parseInline = listOf(
            object : InlineParser {
                override val name = "Emoji"
                override val after = "Emphasis"
                override fun parse(cx: InlineContext, next: Int, pos: Int): Int {
                    if (next != 58) return -1 // ':'
                    val rest = cx.slice(pos + 1, cx.end)
                    val m = Regex("^[a-zA-Z_][a-zA-Z_0-9]*:").find(rest)
                    if (m != null) {
                        return cx.addElement(
                            elt(
                                cx.parser.getNodeType("Emoji"),
                                pos,
                                pos + 1 + m.value.length
                            )
                        )
                    }
                    return -1
                }
            }
        )
    )
)

// ---- GFM Bundle ----

val GFM: MarkdownExtension = markdownExtensionOf(Table, TaskList, Strikethrough, Autolink)
