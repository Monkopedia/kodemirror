/*
 * Copyright 2026 Jason Monk
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
package com.monkopedia.kodemirror.language

import com.monkopedia.kodemirror.lezer.common.Input
import com.monkopedia.kodemirror.lezer.common.NodeProp
import com.monkopedia.kodemirror.lezer.common.NodeSet
import com.monkopedia.kodemirror.lezer.common.NodeType
import com.monkopedia.kodemirror.lezer.common.NodeTypeSpec
import com.monkopedia.kodemirror.lezer.common.Parser
import com.monkopedia.kodemirror.lezer.common.PartialParse
import com.monkopedia.kodemirror.lezer.common.TextRange
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.lezer.common.TreeBuildBuffer
import com.monkopedia.kodemirror.lezer.common.TreeBuildSpec
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import com.monkopedia.kodemirror.lezer.highlight.Tag
import com.monkopedia.kodemirror.lezer.highlight.Tags as highlightTags
import com.monkopedia.kodemirror.lezer.highlight.styleTagsList
import com.monkopedia.kodemirror.state.Facet

/**
 * A stream parser parses or tokenizes content from start to end,
 * emitting tokens as it goes. It keeps a mutable (but copyable)
 * object with state.
 */
interface StreamParser<State> {
    /** A name for this language. */
    val name: String get() = ""

    /** Produce a start state for the parser. */
    fun startState(indentUnit: Int): State

    /**
     * Read one token, advancing the stream past it, and returning a
     * string indicating the token's style tag.
     */
    fun token(stream: StringStream, state: State): String?

    /** Called for blank lines. Can update state. */
    fun blankLine(state: State, indentUnit: Int) {}

    /** Copy a given state. */
    fun copyState(state: State): State

    /**
     * Compute automatic indentation for the line that starts with the
     * given state and text.
     */
    fun indent(state: State, textAfter: String, context: IndentContext): Int? = null

    /** Default language data to attach to this language. */
    val languageData: Map<String, Any> get() = emptyMap()

    /**
     * Extra tokens to use in this parser. When the tokenizer returns a
     * token name that exists here, the corresponding tags will be assigned.
     */
    val tokenTable: Map<String, Any> get() = emptyMap()

    /**
     * By default, adjacent tokens of the same type are merged.
     * Set to false to disable.
     */
    val mergeTokens: Boolean get() = true
}

/**
 * A [Language] class based on a CodeMirror 5-style [StreamParser].
 */
class StreamLanguage<State> private constructor(
    parser: Parser,
    name: String,
    internal val streamParser: StreamParser<State>,
    internal val stateAfter: NodeProp<State>,
    internal val tokenTable: TokenTable,
    internal val topNode: NodeType
) : Language(parser, name) {

    override val allowsNesting: Boolean get() = false

    internal fun getIndent(cx: TreeIndentContext): Int? {
        val startState = findState(
            this,
            cx.node.tree,
            cx.node.from,
            cx.node.from,
            cx.pos
        )
        val state: State
        var statePos: Int
        if (startState != null) {
            state = startState.state
            statePos = startState.pos + 1
        } else {
            state = streamParser.startState(cx.unit)
            statePos = cx.node.from
        }
        if (cx.pos - statePos > MAX_INDENT_SCAN_DIST) return null
        while (statePos < cx.pos) {
            val line = cx.state.doc.lineAt(statePos)
            val end = minOf(cx.pos, line.to)
            if (line.length > 0) {
                val stream = StringStream(
                    line.text,
                    cx.state.tabSize,
                    cx.unit
                )
                while (stream.pos < end - line.from) {
                    readToken(streamParser, stream, state)
                }
            } else {
                streamParser.blankLine(state, cx.unit)
            }
            if (end == cx.pos) break
            statePos = line.to + 1
        }
        val line = cx.lineAt(cx.pos)
        val textAfterMatch = Regex("""^\s*(.*)""").find(line.text)
        val textAfter = textAfterMatch?.groupValues?.get(1) ?: ""
        return streamParser.indent(state, textAfter, cx)
    }

    companion object {
        /** Define a stream language. */
        fun <State> define(spec: StreamParser<State>): StreamLanguage<State> {
            val data = defineLanguageFacet()
            val stateAfter = NodeProp<State>(perNode = true)
            val tokenTable = TokenTable(spec.tokenTable)
            // Will be set after construction
            lateinit var lang: StreamLanguage<State>
            val topNode = docID(data) { cx -> lang.getIndent(cx) }
            val impl = object : Parser() {
                override fun createParse(
                    input: Input,
                    fragments: List<TreeFragment>,
                    ranges: List<TextRange>
                ): PartialParse {
                    return StreamParse(lang, input, ranges)
                }
            }
            lang = StreamLanguage(
                parser = impl,
                name = spec.name,
                streamParser = spec,
                stateAfter = stateAfter,
                tokenTable = tokenTable,
                topNode = topNode
            )
            return lang
        }
    }
}

private data class StateResult<State>(val state: State, val pos: Int)

private fun <State> findState(
    lang: StreamLanguage<State>,
    tree: Tree?,
    off: Int,
    startPos: Int,
    before: Int
): StateResult<State>? {
    if (tree == null) return null
    val state = if (off >= startPos && off + tree.length <= before) {
        tree.prop(lang.stateAfter)
    } else {
        null
    }
    if (state != null) {
        return StateResult(lang.streamParser.copyState(state), off + tree.length)
    }
    for (i in tree.children.indices.reversed()) {
        val child = tree.children[i]
        val pos = off + tree.positions[i]
        if (child is Tree && pos < before) {
            val found = findState(lang, child, pos, startPos, before)
            if (found != null) return found
        }
    }
    return null
}

private const val CHUNK_SIZE = 512
private const val MAX_LINE_LENGTH = 10000
private const val MAX_INDENT_SCAN_DIST = 10000

/**
 * The parse implementation for stream languages.
 * Simplified vs upstream: always parses full document (no viewport optimization).
 */
private class StreamParse<State>(
    private val lang: StreamLanguage<State>,
    private val input: Input,
    private val ranges: List<TextRange>
) : PartialParse {

    private var state: State =
        lang.streamParser.startState(4)
    override var parsedPos: Int = ranges.first().from
    override var stoppedAt: Int? = null
    private val to: Int = ranges.last().to
    private val chunks = mutableListOf<Tree>()
    private val chunkPos = mutableListOf<Int>()
    private var chunkStart: Int = parsedPos
    private val chunk = mutableListOf<Int>()

    override fun advance(): Tree? {
        val parseEnd = if (stoppedAt == null) to else minOf(to, stoppedAt!!)
        val end = minOf(parseEnd, chunkStart + CHUNK_SIZE)
        while (parsedPos < end) parseLine()
        if (chunkStart < parsedPos) finishChunk()
        if (parsedPos >= parseEnd) return finish()
        return null
    }

    override fun stopAt(pos: Int) {
        stoppedAt = pos
    }

    private fun lineAfter(pos: Int): String {
        var chunk = input.chunk(pos)
        if (!input.lineChunks) {
            val eol = chunk.indexOf('\n')
            if (eol > -1) chunk = chunk.substring(0, eol)
        } else if (chunk == "\n") {
            chunk = ""
        }
        return if (pos + chunk.length <= to) chunk else chunk.substring(0, to - pos)
    }

    private fun parseLine() {
        val from = parsedPos
        val line = lineAfter(from)
        val end = from + line.length
        val stream = StringStream(line, 4, 2)
        if (stream.eol()) {
            lang.streamParser.blankLine(state, stream.indentUnit)
        } else {
            while (!stream.eol()) {
                val token = readToken(
                    lang.streamParser,
                    stream,
                    state
                )
                if (token != null) {
                    emitToken(
                        lang.tokenTable.resolve(token),
                        parsedPos + stream.start,
                        parsedPos + stream.pos
                    )
                }
                if (stream.start > MAX_LINE_LENGTH) break
            }
        }
        parsedPos = end
        if (parsedPos < to) parsedPos++
    }

    private fun emitToken(id: Int, from: Int, to: Int) {
        val last = chunk.size - 4
        if (lang.streamParser.mergeTokens && last >= 0 &&
            chunk[last] == id && chunk[last + 2] == from
        ) {
            chunk[last + 2] = to
        } else {
            chunk.addAll(listOf(id, from, to, 4))
        }
    }

    private fun finishChunk() {
        val tree = Tree.build(
            TreeBuildSpec(
                buffer = TreeBuildBuffer.ListBuffer(chunk.toList()),
                start = chunkStart,
                length = parsedPos - chunkStart,
                nodeSet = streamNodeSet,
                topID = 0,
                maxBufferLength = CHUNK_SIZE
            )
        )
        val treeWithState = Tree(
            tree.type,
            tree.children,
            tree.positions,
            tree.length,
            mapOf(
                lang.stateAfter.id to
                    lang.streamParser.copyState(state)
            )
        )
        chunks.add(treeWithState)
        chunkPos.add(chunkStart - ranges.first().from)
        chunk.clear()
        chunkStart = parsedPos
    }

    private fun finish(): Tree {
        return Tree(
            lang.topNode,
            chunks.toList(),
            chunkPos.toList(),
            parsedPos - ranges.first().from
        ).balance()
    }
}

private fun <State> readToken(
    parser: StreamParser<State>,
    stream: StringStream,
    state: State
): String? {
    stream.start = stream.pos
    for (i in 0 until 10) {
        val result = parser.token(stream, state)
        if (stream.pos > stream.start) return result
    }
    error("Stream parser failed to advance stream.")
}

// ---- Token type management ----

private val typeArray = mutableListOf(NodeType.none)
private val streamNodeSet: NodeSet get() = NodeSet(typeArray.toList())

private val byTag = mutableMapOf<String, NodeType>()

private val defaultTable: Map<String, Int> = run {
    val table = mutableMapOf<String, Int>()
    val legacyMappings = listOf(
        "variable" to "variableName",
        "variable-2" to "variableName.special",
        "string-2" to "string.special",
        "def" to "variableName.definition",
        "tag" to "tagName",
        "attribute" to "attributeName",
        "type" to "typeName",
        "builtin" to "variableName.standard",
        "qualifier" to "modifier",
        "error" to "invalid",
        "header" to "heading",
        "property" to "propertyName"
    )
    for ((legacyName, name) in legacyMappings) {
        table[legacyName] = createTokenType(emptyMap(), name)
    }
    table
}

internal class TokenTable(
    private val extra: Map<String, Any>
) {
    private val table = defaultTable.toMutableMap()

    fun resolve(tag: String): Int {
        if (tag.isEmpty()) return 0
        return table.getOrPut(tag) { createTokenType(extra, tag) }
    }
}

@Suppress("UNCHECKED_CAST")
private fun createTokenType(extra: Map<String, Any>, tagStr: String): Int {
    val resultTags = mutableListOf<Tag>()
    for (name in tagStr.split(" ")) {
        var found = listOf<Tag>()
        for (part in name.split(".")) {
            val value = extra[part] ?: resolveHighlightTag(part)
            if (value == null) {
                // Unknown tag, skip
            } else if (value is Function1<*, *>) {
                if (found.isNotEmpty()) {
                    val modifier = value as (Tag) -> Tag
                    found = found.map(modifier)
                }
            } else {
                if (found.isEmpty()) {
                    found = when (value) {
                        is Tag -> listOf(value)
                        is List<*> -> value as List<Tag>
                        else -> emptyList()
                    }
                }
            }
        }
        resultTags.addAll(found)
    }
    if (resultTags.isEmpty()) return 0

    val typeName = tagStr.replace(" ", "_")
    val key = "$typeName ${resultTags.map { it.id }}"
    val known = byTag[key]
    if (known != null) return known.id

    // Create a bare type, then extend with styleTags to get highlight rules
    val bareType = NodeType.define(
        NodeTypeSpec(id = typeArray.size, name = typeName)
    )
    val extended = NodeSet(listOf(bareType))
        .extend(styleTagsList(mapOf(typeName to resultTags)))
    val type = extended.types[0]
    byTag[key] = type
    typeArray.add(type)
    return type.id
}

/**
 * Resolve a tag name to its value in the standard [highlightTags] object.
 * Returns a [Tag], a modifier function `(Tag) -> Tag`, or null.
 */
@Suppress("CyclomaticComplexMethod")
private fun resolveHighlightTag(name: String): Any? = when (name) {
    "comment" -> highlightTags.comment
    "lineComment" -> highlightTags.lineComment
    "blockComment" -> highlightTags.blockComment
    "docComment" -> highlightTags.docComment
    "name" -> highlightTags.name
    "variableName" -> highlightTags.variableName
    "typeName" -> highlightTags.typeName
    "tagName" -> highlightTags.tagName
    "propertyName" -> highlightTags.propertyName
    "attributeName" -> highlightTags.attributeName
    "className" -> highlightTags.className
    "labelName" -> highlightTags.labelName
    "namespace" -> highlightTags.namespace
    "macroName" -> highlightTags.macroName
    "literal" -> highlightTags.literal
    "string" -> highlightTags.string
    "docString" -> highlightTags.docString
    "character" -> highlightTags.character
    "attributeValue" -> highlightTags.attributeValue
    "number" -> highlightTags.number
    "integer" -> highlightTags.integer
    "float" -> highlightTags.float
    "bool" -> highlightTags.bool
    "regexp" -> highlightTags.regexp
    "escape" -> highlightTags.escape
    "color" -> highlightTags.color
    "url" -> highlightTags.url
    "keyword" -> highlightTags.keyword
    "self" -> highlightTags.self
    "null" -> highlightTags.`null`
    "atom" -> highlightTags.atom
    "unit" -> highlightTags.unit
    "modifier" -> highlightTags.modifier
    "operatorKeyword" -> highlightTags.operatorKeyword
    "controlKeyword" -> highlightTags.controlKeyword
    "definitionKeyword" -> highlightTags.definitionKeyword
    "moduleKeyword" -> highlightTags.moduleKeyword
    "operator" -> highlightTags.operator
    "derefOperator" -> highlightTags.derefOperator
    "arithmeticOperator" -> highlightTags.arithmeticOperator
    "logicOperator" -> highlightTags.logicOperator
    "bitwiseOperator" -> highlightTags.bitwiseOperator
    "compareOperator" -> highlightTags.compareOperator
    "updateOperator" -> highlightTags.updateOperator
    "definitionOperator" -> highlightTags.definitionOperator
    "typeOperator" -> highlightTags.typeOperator
    "controlOperator" -> highlightTags.controlOperator
    "punctuation" -> highlightTags.punctuation
    "separator" -> highlightTags.separator
    "bracket" -> highlightTags.bracket
    "angleBracket" -> highlightTags.angleBracket
    "squareBracket" -> highlightTags.squareBracket
    "paren" -> highlightTags.paren
    "brace" -> highlightTags.brace
    "content" -> highlightTags.content
    "heading" -> highlightTags.heading
    "heading1" -> highlightTags.heading1
    "heading2" -> highlightTags.heading2
    "heading3" -> highlightTags.heading3
    "heading4" -> highlightTags.heading4
    "heading5" -> highlightTags.heading5
    "heading6" -> highlightTags.heading6
    "contentSeparator" -> highlightTags.contentSeparator
    "list" -> highlightTags.list
    "quote" -> highlightTags.quote
    "emphasis" -> highlightTags.emphasis
    "strong" -> highlightTags.strong
    "link" -> highlightTags.link
    "monospace" -> highlightTags.monospace
    "strikethrough" -> highlightTags.strikethrough
    "inserted" -> highlightTags.inserted
    "deleted" -> highlightTags.deleted
    "changed" -> highlightTags.changed
    "invalid" -> highlightTags.invalid
    "meta" -> highlightTags.meta
    "documentMeta" -> highlightTags.documentMeta
    "annotation" -> highlightTags.annotation
    "processingInstruction" -> highlightTags.processingInstruction
    // Modifiers
    "definition" -> highlightTags.definition
    "constant" -> highlightTags.constant
    "function" -> highlightTags.function
    "standard" -> highlightTags.standard
    "local" -> highlightTags.local
    "special" -> highlightTags.special
    else -> null
}

private fun docID(data: Facet<*, *>, getIndent: (TreeIndentContext) -> Int?): NodeType {
    val type = NodeType.define(
        NodeTypeSpec(
            id = typeArray.size,
            name = "Document",
            props = listOf(
                @Suppress("UNCHECKED_CAST")
                (languageDataProp as NodeProp<Any?>)
                    to data,
                @Suppress("UNCHECKED_CAST")
                (indentNodeProp as NodeProp<Any?>)
                    to
                    { cx: TreeIndentContext -> getIndent(cx) }
            ),
            top = true
        )
    )
    typeArray.add(type)
    return type
}
