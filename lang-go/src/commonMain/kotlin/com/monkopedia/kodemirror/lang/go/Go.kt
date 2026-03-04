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
package com.monkopedia.kodemirror.lang.go

import com.monkopedia.kodemirror.language.CommentTokens
import com.monkopedia.kodemirror.language.FoldRange
import com.monkopedia.kodemirror.language.LRLanguage
import com.monkopedia.kodemirror.language.LanguageSupport
import com.monkopedia.kodemirror.language.commentTokens
import com.monkopedia.kodemirror.language.continuedIndent
import com.monkopedia.kodemirror.language.delimitedIndent
import com.monkopedia.kodemirror.language.flatIndent
import com.monkopedia.kodemirror.language.foldInside
import com.monkopedia.kodemirror.language.foldNodeProp
import com.monkopedia.kodemirror.language.indentNodeProp
import com.monkopedia.kodemirror.lezer.lr.ParserConfig

/**
 * A language provider based on the Lezer Go parser, extended with
 * folding and indentation information.
 */
val goLanguage: LRLanguage = LRLanguage.define(
    parser = parser.configure(
        ParserConfig(
            props = listOf(
                indentNodeProp.add { type ->
                    when {
                        type.name == "IfStatement" ->
                            continuedIndent(except = Regex("""^\s*(\{|else\b)"""))
                        type.name == "LabeledStatement" -> flatIndent
                        type.name == "SwitchBlock" ||
                            type.name == "SelectBlock" -> { cx ->
                            val after = cx.textAfter
                            val closed = Regex("""^\s*\}""").containsMatchIn(after)
                            val isCase =
                                Regex("""^\s*(case|default)\b""").containsMatchIn(after)
                            cx.baseIndent + if (closed || isCase) 0 else cx.unit
                        }
                        type.name == "Block" -> delimitedIndent(closing = "}")
                        type.name == "BlockComment" -> { _ -> null }
                        type.`is`("Statement") ->
                            continuedIndent(except = Regex("""^\{"""))
                        else -> null
                    }
                },
                foldNodeProp.add { type ->
                    when (type.name) {
                        "Block", "SwitchBlock", "SelectBlock",
                        "LiteralValue", "InterfaceType", "StructType",
                        "SpecList" -> { node, _ -> foldInside(node) }
                        "BlockComment" -> { node, _ ->
                            if (node.to - node.from > 4) {
                                FoldRange(node.from + 2, node.to - 2)
                            } else {
                                null
                            }
                        }
                        else -> null
                    }
                }
            )
        )
    ),
    name = "go"
)

/**
 * Go language support.
 */
fun go(): LanguageSupport = LanguageSupport(
    goLanguage,
    support = commentTokens.of(
        CommentTokens(
            line = "//",
            block = CommentTokens.BlockComment("/*", "*/")
        )
    )
)
