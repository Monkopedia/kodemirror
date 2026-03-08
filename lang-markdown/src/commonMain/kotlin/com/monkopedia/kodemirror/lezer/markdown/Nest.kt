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

import com.monkopedia.kodemirror.lezer.common.NestedParse
import com.monkopedia.kodemirror.lezer.common.ParseOverlay
import com.monkopedia.kodemirror.lezer.common.ParseOverlayMatch
import com.monkopedia.kodemirror.lezer.common.Parser
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.lezer.common.SyntaxNodeRef
import com.monkopedia.kodemirror.lezer.common.TextRange
import com.monkopedia.kodemirror.lezer.common.TreeCursor
import com.monkopedia.kodemirror.lezer.common.parseMixed

private fun leftOverSpace(node: SyntaxNode, from: Int, to: Int): List<TextRange> {
    val ranges = mutableListOf<TextRange>()
    var pos = from
    var n = node.firstChild
    while (true) {
        val nextPos = n?.from ?: to
        if (nextPos > pos) ranges.add(TextRange(pos, nextPos))
        if (n == null) break
        pos = n.to
        n = n.nextSibling
    }
    return ranges
}

fun parseCode(config: ParseCodeConfig): MarkdownExtension {
    val codeParser = config.codeParser
    val htmlParser = config.htmlParser
    val wrap = parseMixed { nodeRef, input ->
        val id = nodeRef.type.id
        val syntaxNode = (nodeRef as? TreeCursor)?.node
            ?: (nodeRef as? SyntaxNode)
        if (codeParser != null &&
            (id == Type.CodeBlock || id == Type.FencedCode)
        ) {
            var info = ""
            if (id == Type.FencedCode && syntaxNode != null) {
                val infoNode = syntaxNode.getChild("CodeInfo")
                if (infoNode != null) {
                    info = input.read(infoNode.from, infoNode.to)
                }
            }
            val p = codeParser(info)
            if (p != null) {
                NestedParse(
                    parser = p,
                    overlay = ParseOverlay.Predicate { child: SyntaxNodeRef ->
                        if (child.type.id == Type.CodeText) {
                            ParseOverlayMatch.FullNode
                        } else {
                            null
                        }
                    },
                    bracketed = id == Type.FencedCode
                )
            } else {
                null
            }
        } else if (htmlParser != null &&
            (id == Type.HTMLBlock || id == Type.HTMLTag || id == Type.CommentBlock)
        ) {
            if (syntaxNode != null) {
                NestedParse(
                    parser = htmlParser,
                    overlay = ParseOverlay.Ranges(
                        leftOverSpace(syntaxNode, nodeRef.from, nodeRef.to)
                    )
                )
            } else {
                NestedParse(parser = htmlParser)
            }
        } else {
            null
        }
    }
    return markdownExtensionOf(MarkdownConfig(wrap = wrap))
}

data class ParseCodeConfig(
    val codeParser: ((info: String) -> Parser?)? = null,
    val htmlParser: Parser? = null
)
