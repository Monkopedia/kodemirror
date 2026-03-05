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

import com.monkopedia.kodemirror.lezer.common.Input
import com.monkopedia.kodemirror.lezer.common.NodeProp
import com.monkopedia.kodemirror.lezer.common.NodePropSource
import com.monkopedia.kodemirror.lezer.common.NodeSet
import com.monkopedia.kodemirror.lezer.common.NodeType
import com.monkopedia.kodemirror.lezer.common.NodeTypeSpec
import com.monkopedia.kodemirror.lezer.common.ParseWrapper
import com.monkopedia.kodemirror.lezer.common.Parser
import com.monkopedia.kodemirror.lezer.common.PartialParse
import com.monkopedia.kodemirror.lezer.common.TextRange
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import com.monkopedia.kodemirror.lezer.highlight.Tag
import com.monkopedia.kodemirror.lezer.highlight.styleTags

class MarkdownParser(
    val nodeSet: NodeSet,
    val blockParsers: List<((BlockContext, Line) -> BlockResult)?>,
    val leafBlockParsers: List<((BlockContext, LeafBlock) -> LeafBlockParser?)?>,
    val blockNames: List<String>,
    val endLeafBlock: List<(BlockContext, Line, LeafBlock) -> Boolean>,
    val skipContextMarkup: Map<Int, (CompositeBlock, BlockContext, Line) -> Boolean>,
    val inlineParsers: List<((InlineContext, Int, Int) -> Int)?>,
    val inlineNames: List<String>,
    val wrappers: List<ParseWrapper>
) : Parser() {
    internal val nodeTypes: MutableMap<String, Int> = mutableMapOf()

    init {
        for (t in nodeSet.types) nodeTypes[t.name] = t.id
    }

    override fun createParse(
        input: Input,
        fragments: List<TreeFragment>,
        ranges: List<TextRange>
    ): PartialParse {
        var parse: PartialParse = BlockContext(this, input, fragments, ranges)
        for (w in wrappers) parse = w(parse, input, fragments, ranges)
        return parse
    }

    fun configure(spec: MarkdownExtension): MarkdownParser {
        val config = resolveConfig(spec) ?: return this
        var nodeSet = this.nodeSet
        var skipContextMarkup = this.skipContextMarkup
        val blockParsers = this.blockParsers.toMutableList()
        val leafBlockParsers = this.leafBlockParsers.toMutableList()
        val blockNames = this.blockNames.toMutableList()
        val inlineParsers = this.inlineParsers.toMutableList()
        val inlineNames = this.inlineNames.toMutableList()
        val endLeafBlock = this.endLeafBlock.toMutableList()
        var wrappers = this.wrappers

        if (!config.defineNodes.isNullOrEmpty()) {
            val mutableSkip = skipContextMarkup.toMutableMap()
            val nodeTypes = nodeSet.types.toMutableList()
            var styles: MutableMap<String, Any>? = null
            for (s in config.defineNodes) {
                val spec = when (s) {
                    is String -> SimpleNodeSpec(name = s)
                    is NodeSpec -> s
                    else -> continue
                }
                if (nodeTypes.any { it.name == spec.name }) continue
                val composite = spec.composite
                if (composite != null) {
                    mutableSkip[nodeTypes.size] = { bl, cx, line ->
                        composite(cx, line, bl.value)
                    }
                }
                val id = nodeTypes.size
                val group = when {
                    composite != null -> listOf("Block", "BlockContext")
                    !spec.block -> null
                    id in Type.ATXHeading1..Type.SetextHeading2 ->
                        listOf("Block", "LeafBlock", "Heading")
                    else -> listOf("Block", "LeafBlock")
                }
                nodeTypes.add(
                    NodeType.define(
                        NodeTypeSpec(
                            id = id,
                            name = spec.name,
                            props = if (group != null) {
                                listOf(NodeProp.group to group)
                            } else {
                                emptyList()
                            },
                            top = spec.name == "Document"
                        )
                    )
                )
                val style = spec.style
                if (style != null) {
                    if (styles == null) styles = mutableMapOf()
                    when (style) {
                        is Tag -> styles[spec.name] = style
                        is List<*> -> styles[spec.name] = style
                        is Map<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            styles.putAll(style as Map<String, Any>)
                        }
                    }
                }
            }
            nodeSet = NodeSet(nodeTypes)
            if (styles != null) nodeSet = nodeSet.extend(styleTags(styles))
            skipContextMarkup = mutableSkip
        }

        if (!config.props.isNullOrEmpty()) {
            nodeSet = nodeSet.extend(*config.props.toTypedArray())
        }

        if (!config.remove.isNullOrEmpty()) {
            for (rm in config.remove) {
                val block = this.blockNames.indexOf(rm)
                val inline = this.inlineNames.indexOf(rm)
                if (block > -1) {
                    blockParsers[block] = null
                    leafBlockParsers[block] = null
                }
                if (inline > -1) inlineParsers[inline] = null
            }
        }

        if (!config.parseBlock.isNullOrEmpty()) {
            for (bSpec in config.parseBlock) {
                val found = blockNames.indexOf(bSpec.name)
                if (found > -1) {
                    blockParsers[found] = bSpec.parse
                    leafBlockParsers[found] = bSpec.leaf
                } else {
                    val pos = when {
                        bSpec.before != null -> findName(blockNames, bSpec.before!!)
                        bSpec.after != null -> findName(blockNames, bSpec.after!!) + 1
                        else -> blockNames.size - 1
                    }
                    blockParsers.add(pos, bSpec.parse)
                    leafBlockParsers.add(pos, bSpec.leaf)
                    blockNames.add(pos, bSpec.name)
                }
                if (bSpec.endLeaf != null) endLeafBlock.add(bSpec.endLeaf!!)
            }
        }

        if (!config.parseInline.isNullOrEmpty()) {
            for (iSpec in config.parseInline) {
                val found = inlineNames.indexOf(iSpec.name)
                if (found > -1) {
                    inlineParsers[found] = iSpec::parse
                } else {
                    val pos = when {
                        iSpec.before != null -> findName(inlineNames, iSpec.before!!)
                        iSpec.after != null -> findName(inlineNames, iSpec.after!!) + 1
                        else -> inlineNames.size - 1
                    }
                    inlineParsers.add(pos, iSpec::parse)
                    inlineNames.add(pos, iSpec.name)
                }
            }
        }

        if (config.wrap != null) wrappers = wrappers + config.wrap!!

        return MarkdownParser(
            nodeSet,
            blockParsers, leafBlockParsers, blockNames,
            endLeafBlock, skipContextMarkup,
            inlineParsers, inlineNames, wrappers
        )
    }

    internal fun getNodeType(name: String): Int {
        return nodeTypes[name]
            ?: throw IllegalArgumentException("Unknown node type '$name'")
    }

    fun parseInline(text: String, offset: Int): List<Element> {
        val cx = InlineContext(this, text, offset)
        var pos = offset
        outer@ while (pos < cx.end) {
            val next = cx.char(pos)
            for (token in inlineParsers) {
                if (token != null) {
                    val result = token(cx, next, pos)
                    if (result >= 0) {
                        pos = result
                        continue@outer
                    }
                }
            }
            pos++
        }
        return cx.resolveMarkers(0)
    }
}

private fun findName(names: List<String>, name: String): Int {
    val found = names.indexOf(name)
    if (found < 0) {
        throw IllegalArgumentException(
            "Position specified relative to unknown parser $name"
        )
    }
    return found
}

internal fun resolveConfig(spec: MarkdownExtension): MarkdownConfig? {
    return when (spec) {
        is MarkdownExtension.Single -> spec.config
        is MarkdownExtension.Multiple -> {
            if (spec.extensions.isEmpty()) return null
            val conf = resolveConfig(spec.extensions[0])
            if (spec.extensions.size == 1) return conf
            val rest = resolveConfig(
                MarkdownExtension.Multiple(spec.extensions.drop(1))
            )
            if (rest == null || conf == null) return conf ?: rest
            val conc = { a: List<Any>?, b: List<Any>? ->
                (a ?: emptyList()) + (b ?: emptyList())
            }
            val wrapA = conf.wrap
            val wrapB = rest.wrap
            MarkdownConfig(
                props = @Suppress("UNCHECKED_CAST") (
                    conc(
                        conf.props,
                        rest.props
                    ) as List<NodePropSource<*>>
                    ),
                defineNodes = conc(conf.defineNodes, rest.defineNodes),
                parseBlock = @Suppress("UNCHECKED_CAST") (
                    conc(
                        conf.parseBlock,
                        rest.parseBlock
                    ) as List<BlockParser>
                    ),
                parseInline = @Suppress("UNCHECKED_CAST") (
                    conc(
                        conf.parseInline,
                        rest.parseInline
                    ) as List<InlineParser>
                    ),
                remove = @Suppress("UNCHECKED_CAST") (
                    conc(
                        conf.remove,
                        rest.remove
                    ) as List<String>
                    ),
                wrap = when {
                    wrapA == null -> wrapB
                    wrapB == null -> wrapA
                    else -> { inner, input, fragments, ranges ->
                        wrapA(wrapB(inner, input, fragments, ranges), input, fragments, ranges)
                    }
                }
            )
        }
    }
}
