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
package com.monkopedia.kodemirror.lang.html

import com.monkopedia.kodemirror.autocomplete.CompletionConfig
import com.monkopedia.kodemirror.autocomplete.autocompletion
import com.monkopedia.kodemirror.language.CommentTokens
import com.monkopedia.kodemirror.language.FoldRange
import com.monkopedia.kodemirror.language.LRLanguage
import com.monkopedia.kodemirror.language.LanguageSupport
import com.monkopedia.kodemirror.language.commentTokens
import com.monkopedia.kodemirror.language.foldNodeProp
import com.monkopedia.kodemirror.language.indentNodeProp
import com.monkopedia.kodemirror.lezer.lr.ParserConfig
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList

/**
 * A language provider based on the Lezer HTML parser, extended with
 * highlighting and indentation information.
 */
val htmlLanguage: LRLanguage = LRLanguage.define(
    parser = htmlParser.configure(
        ParserConfig(
            props = listOf(
                indentNodeProp.add { type ->
                    when (type.name) {
                        "Element" -> { cx ->
                            val match = Regex("""^(\s*)(<\/)?""").find(cx.textAfter)
                            val matchLen = match?.value?.length ?: 0
                            if (DocPos(cx.node.to) <= cx.pos + matchLen) {
                                cx.continueAt()
                            } else {
                                val hasClosing = match?.groupValues?.get(2)?.isNotEmpty() == true
                                cx.lineIndent(DocPos(cx.node.from)) + if (hasClosing) 0 else cx.unit
                            }
                        }
                        "OpenTag", "CloseTag", "SelfClosingTag" -> { cx ->
                            cx.column(cx.node.from) + cx.unit
                        }
                        "Document" -> { cx ->
                            val wsMatch = Regex("""\s*""").find(cx.textAfter)
                            val wsLen = wsMatch?.value?.length ?: 0
                            if (cx.pos + wsLen < DocPos(cx.node.to)) {
                                cx.continueAt()
                            } else {
                                var endElt = cx.node
                                while (true) {
                                    val last = endElt.lastChild ?: break
                                    if (last.name != "Element" || last.to != endElt.to) break
                                    endElt = last
                                }
                                val close = endElt.lastChild
                                if (endElt !== cx.node &&
                                    (
                                        close == null ||
                                            (
                                                close.name != "CloseTag" &&
                                                    close.name != "SelfClosingTag"
                                                )
                                        )
                                ) {
                                    cx.lineIndent(DocPos(endElt.from)) + cx.unit
                                } else {
                                    null
                                }
                            }
                        }
                        else -> null
                    }
                },
                foldNodeProp.add { type ->
                    when (type.name) {
                        "Element" -> { node, _ ->
                            val first = node.firstChild ?: return@add null
                            if (first.name != "OpenTag") return@add null
                            val last = node.lastChild ?: return@add null
                            val to = if (last.name == "CloseTag") last.from else node.to
                            FoldRange(DocPos(first.to), DocPos(to))
                        }
                        else -> null
                    }
                }
            )
        )
    ),
    name = "html"
)

/**
 * HTML language support with autocompletion and auto-closing tags.
 *
 * @param config Optional completion configuration for extending the
 *   default HTML schema with additional tags or attributes.
 * @param selfClosingTags Whether to include [autoCloseTags] support
 *   (enabled by default).
 */
fun html(
    config: HtmlCompletionConfig = HtmlCompletionConfig(),
    selfClosingTags: Boolean = true
): LanguageSupport {
    val support = mutableListOf<Extension>(
        commentTokens.of(
            CommentTokens(
                block = CommentTokens.BlockComment("<!--", "-->")
            )
        ),
        autocompletion(
            CompletionConfig(override = listOf(htmlCompletionSourceWith(config)))
        )
    )
    if (selfClosingTags) support.add(autoCloseTags)

    return LanguageSupport(htmlLanguage, support = ExtensionList(support))
}
