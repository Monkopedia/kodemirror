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

import com.monkopedia.kodemirror.lezer.common.NodePropSource
import com.monkopedia.kodemirror.lezer.common.ParseWrapper

interface NodeSpec {
    val name: String
    val block: Boolean get() = false
    val composite: ((cx: BlockContext, line: Line, value: Int) -> Boolean)? get() = null
    val style: Any? get() = null // Tag | List<Tag> | Map<String, Any>
}

data class SimpleNodeSpec(
    override val name: String,
    override val block: Boolean = false,
    override val composite: ((cx: BlockContext, line: Line, value: Int) -> Boolean)? = null,
    override val style: Any? = null
) : NodeSpec

interface InlineParser {
    val name: String
    fun parse(cx: InlineContext, next: Int, pos: Int): Int
    val before: String? get() = null
    val after: String? get() = null
}

interface BlockParser {
    val name: String
    val parse: ((cx: BlockContext, line: Line) -> BlockResult)? get() = null
    val leaf: ((cx: BlockContext, leaf: LeafBlock) -> LeafBlockParser?)? get() = null
    val endLeaf: ((cx: BlockContext, line: Line, leaf: LeafBlock) -> Boolean)? get() = null
    val before: String? get() = null
    val after: String? get() = null
}

interface LeafBlockParser {
    fun nextLine(cx: BlockContext, line: Line, leaf: LeafBlock): Boolean
    fun finish(cx: BlockContext, leaf: LeafBlock): Boolean
}

data class MarkdownConfig(
    val props: List<NodePropSource<*>>? = null,
    // String | NodeSpec
    val defineNodes: List<Any>? = null,
    val parseBlock: List<BlockParser>? = null,
    val parseInline: List<InlineParser>? = null,
    val remove: List<String>? = null,
    val wrap: ParseWrapper? = null
)

sealed class MarkdownExtension {
    class Single(val config: MarkdownConfig) : MarkdownExtension()
    class Multiple(val extensions: List<MarkdownExtension>) : MarkdownExtension()
}

fun markdownExtensionOf(config: MarkdownConfig): MarkdownExtension =
    MarkdownExtension.Single(config)

fun markdownExtensionOf(vararg extensions: MarkdownExtension): MarkdownExtension =
    MarkdownExtension.Multiple(extensions.toList())

interface DelimiterType {
    val resolve: String? get() = null
    val mark: String? get() = null
}
