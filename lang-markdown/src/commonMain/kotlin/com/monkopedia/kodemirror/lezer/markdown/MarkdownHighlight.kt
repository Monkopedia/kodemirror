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

import com.monkopedia.kodemirror.lezer.common.NodeProp
import com.monkopedia.kodemirror.lezer.common.NodeSet
import com.monkopedia.kodemirror.lezer.common.NodeType
import com.monkopedia.kodemirror.lezer.common.NodeTypeSpec
import com.monkopedia.kodemirror.lezer.highlight.Tags as t
import com.monkopedia.kodemirror.lezer.highlight.styleTags

val markdownHighlighting = styleTags(
    mapOf(
        "Blockquote/..." to t.quote,
        "HorizontalRule" to t.contentSeparator,
        "ATXHeading1/... SetextHeading1/..." to t.heading1,
        "ATXHeading2/... SetextHeading2/..." to t.heading2,
        "ATXHeading3/..." to t.heading3,
        "ATXHeading4/..." to t.heading4,
        "ATXHeading5/..." to t.heading5,
        "ATXHeading6/..." to t.heading6,
        "Comment CommentBlock" to t.comment,
        "Escape" to t.escape,
        "Entity" to t.character,
        "Emphasis/..." to t.emphasis,
        "StrongEmphasis/..." to t.strong,
        "Link/... Image/..." to t.link,
        "OrderedList/... BulletList/..." to t.list,
        "BlockQuote/..." to t.quote,
        "InlineCode CodeText" to t.monospace,
        "URL Autolink" to t.url,
        "HeaderMark HardBreak QuoteMark ListMark LinkMark EmphasisMark CodeMark" to
            t.processingInstruction,
        "CodeInfo LinkLabel" to t.labelName,
        "LinkTitle" to t.string,
        "Paragraph" to t.content
    )
)

private fun buildNodeTypes(): List<NodeType> {
    val types = mutableListOf(NodeType.none)
    // Build node types for IDs 1..44 (Type.Document through Type.URL)
    val names = listOf(
        // 0 = none
        "",
        "Document", "CodeBlock", "FencedCode", "Blockquote", "HorizontalRule",
        "BulletList", "OrderedList", "ListItem",
        "ATXHeading1", "ATXHeading2", "ATXHeading3",
        "ATXHeading4", "ATXHeading5", "ATXHeading6",
        "SetextHeading1", "SetextHeading2",
        "HTMLBlock", "LinkReference", "Paragraph",
        "CommentBlock", "ProcessingInstructionBlock",
        // Inline types start at 22
        "Escape", "Entity", "HardBreak", "Emphasis", "StrongEmphasis",
        "Link", "Image", "InlineCode", "HTMLTag", "Comment",
        "ProcessingInstruction", "Autolink",
        // Smaller tokens at 34
        "HeaderMark", "QuoteMark", "ListMark", "LinkMark", "EmphasisMark",
        "CodeMark", "CodeText", "CodeInfo", "LinkTitle", "LinkLabel", "URL"
    )
    for (i in 1 until names.size) {
        val name = names[i]
        val isInline = i >= Type.Escape
        val group = if (isInline) {
            null
        } else if (i in DefaultSkipMarkup) {
            listOf("Block", "BlockContext")
        } else {
            listOf("Block", "LeafBlock")
        }
        types.add(
            NodeType.define(
                NodeTypeSpec(
                    id = i,
                    name = name,
                    props = if (group != null) listOf(NodeProp.group to group) else emptyList(),
                    top = name == "Document"
                )
            )
        )
    }
    return types
}

val markdownParser: MarkdownParser = run {
    val nodeTypes = buildNodeTypes()
    val keys = DefaultBlockParsers.keys.toList()
    MarkdownParser(
        nodeSet = NodeSet(nodeTypes).extend(markdownHighlighting),
        blockParsers = keys.map { DefaultBlockParsers[it] },
        leafBlockParsers = keys.map { DefaultLeafBlocks[it] },
        blockNames = keys,
        endLeafBlock = DefaultEndLeaf,
        skipContextMarkup = DefaultSkipMarkup,
        inlineParsers = DefaultInline.keys.map { DefaultInline[it] },
        inlineNames = DefaultInline.keys.toList(),
        wrappers = emptyList()
    )
}
