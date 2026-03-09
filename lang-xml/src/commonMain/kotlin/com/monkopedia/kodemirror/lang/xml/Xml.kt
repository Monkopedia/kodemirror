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
package com.monkopedia.kodemirror.lang.xml

import com.monkopedia.kodemirror.language.CommentTokens
import com.monkopedia.kodemirror.language.LRLanguage
import com.monkopedia.kodemirror.language.LanguageSupport
import com.monkopedia.kodemirror.language.commentTokens
import com.monkopedia.kodemirror.language.foldNodeProp
import com.monkopedia.kodemirror.language.indentNodeProp
import com.monkopedia.kodemirror.lezer.lr.ParserConfig
import com.monkopedia.kodemirror.state.DocPos

/**
 * A language provider based on the Lezer XML parser, extended with
 * highlighting and indentation information.
 */
val xmlLanguage: LRLanguage = LRLanguage.define(
    parser = xmlParser.configure(
        ParserConfig(
            props = listOf(
                indentNodeProp.add { type ->
                    when (type.name) {
                        "Element" -> { cx ->
                            val closed = Regex("""^\s*</""").containsMatchIn(cx.textAfter)
                            cx.lineIndent(DocPos(cx.node.from)) + if (closed) 0 else cx.unit
                        }
                        "OpenTag", "CloseTag", "SelfClosingTag" -> { cx ->
                            cx.column(cx.node.from) + cx.unit
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
                            com.monkopedia.kodemirror.language.FoldRange(
                                DocPos(first.to),
                                DocPos(to)
                            )
                        }
                        else -> null
                    }
                }
            )
        )
    ),
    name = "xml"
)

/**
 * XML language support.
 */
fun xml(): LanguageSupport = LanguageSupport(
    xmlLanguage,
    support = commentTokens.of(
        CommentTokens(
            block = CommentTokens.BlockComment("<!--", "-->")
        )
    )
)
