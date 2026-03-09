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
package com.monkopedia.kodemirror.lang.markdown

import com.monkopedia.kodemirror.language.FoldRange
import com.monkopedia.kodemirror.language.Language
import com.monkopedia.kodemirror.language.defineLanguageFacet
import com.monkopedia.kodemirror.language.foldNodeProp
import com.monkopedia.kodemirror.language.foldService
import com.monkopedia.kodemirror.language.indentNodeProp
import com.monkopedia.kodemirror.language.languageDataProp
import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.lezer.common.NodeProp
import com.monkopedia.kodemirror.lezer.common.NodeType
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.lezer.markdown.Emoji
import com.monkopedia.kodemirror.lezer.markdown.GFM
import com.monkopedia.kodemirror.lezer.markdown.MarkdownParser
import com.monkopedia.kodemirror.lezer.markdown.Subscript
import com.monkopedia.kodemirror.lezer.markdown.Superscript
import com.monkopedia.kodemirror.lezer.markdown.markdownExtensionOf
import com.monkopedia.kodemirror.lezer.markdown.markdownParser as baseParser
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension

private val data = defineLanguageFacet()

internal val headingProp = NodeProp<Int>()

private val commonmark: MarkdownParser = baseParser.configure(
    markdownExtensionOf(
        com.monkopedia.kodemirror.lezer.markdown.MarkdownConfig(
            props = listOf(
                foldNodeProp.add { type ->
                    if (!type.`is`("Block") || type.`is`("Document") ||
                        isHeading(type) != null || isList(type)
                    ) {
                        null
                    } else {
                        { tree, state ->
                            val lineEnd = state.doc.lineAt(DocPos(tree.from)).to
                            FoldRange(lineEnd, DocPos(tree.to))
                        }
                    }
                },
                headingProp.add(::isHeading),
                indentNodeProp.add { type ->
                    if (type.name == "Document") {
                        { _ -> null }
                    } else {
                        null
                    }
                },
                languageDataProp.add { type ->
                    if (type.name == "Document") data else null
                }
            )
        )
    )
)

internal fun isHeading(type: NodeType): Int? {
    val match = Regex("^(?:ATX|Setext)Heading(\\d)\$").find(type.name)
    return match?.groupValues?.get(1)?.toIntOrNull()
}

internal fun isList(type: NodeType): Boolean =
    type.name == "OrderedList" || type.name == "BulletList"

private fun findSectionEnd(headerNode: SyntaxNode, level: Int): Int {
    var last = headerNode
    while (true) {
        val next = last.nextSibling ?: break
        val heading = isHeading(next.type)
        if (heading != null && heading <= level) break
        last = next
    }
    return last.to
}

val headerIndent: Extension = foldService.of { state, start ->
    var node: SyntaxNode? = syntaxTree(state).resolveInner(start.value, -1)
    while (node != null) {
        if (DocPos(node.from) < start) break
        val heading = node.type.prop(headingProp)
        if (heading != null) {
            val upto = findSectionEnd(node, heading)
            if (DocPos(upto) > start) return@of FoldRange(start, DocPos(upto))
        }
        node = node.parent
    }
    null
}

fun mkLang(parser: MarkdownParser): Language = Language(parser, "markdown")

val commonmarkLanguage: Language = mkLang(commonmark)

private val extended: MarkdownParser = commonmark.configure(
    markdownExtensionOf(
        GFM,
        Subscript,
        Superscript,
        Emoji,
        markdownExtensionOf(
            com.monkopedia.kodemirror.lezer.markdown.MarkdownConfig(
                props = listOf(
                    foldNodeProp.add { type ->
                        if (type.name == "Table") {
                            { tree, state ->
                                FoldRange(state.doc.lineAt(DocPos(tree.from)).to, DocPos(tree.to))
                            }
                        } else {
                            null
                        }
                    }
                )
            )
        )
    )
)

val markdownLanguage: Language = mkLang(extended)
