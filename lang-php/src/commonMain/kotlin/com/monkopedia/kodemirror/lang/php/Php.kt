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
package com.monkopedia.kodemirror.lang.php

import com.monkopedia.kodemirror.language.CommentTokens
import com.monkopedia.kodemirror.language.FoldRange
import com.monkopedia.kodemirror.language.LRLanguage
import com.monkopedia.kodemirror.language.LanguageSupport
import com.monkopedia.kodemirror.language.commentTokens
import com.monkopedia.kodemirror.language.continuedIndent
import com.monkopedia.kodemirror.language.delimitedIndent
import com.monkopedia.kodemirror.language.foldInside
import com.monkopedia.kodemirror.language.foldNodeProp
import com.monkopedia.kodemirror.language.getIndentUnit
import com.monkopedia.kodemirror.language.indentNodeProp
import com.monkopedia.kodemirror.lezer.lr.ParserConfig

/**
 * A language provider based on the Lezer PHP parser, extended with
 * highlighting and indentation information.
 */
val phpLanguage: LRLanguage = LRLanguage.define(
    parser = parser.configure(
        ParserConfig(
            props = listOf(
                indentNodeProp.add { type ->
                    when (type.name) {
                        "IfStatement" -> continuedIndent(
                            except = Regex("""^\s*(\{|else\b|elseif\b|endif\b)""")
                        )
                        "TryStatement" -> continuedIndent(
                            except = Regex("""^\s*(\{|catch\b|finally\b)""")
                        )
                        "SwitchBody" -> { cx ->
                            val after = cx.textAfter
                            val closed = Regex("""^\s*\}""").containsMatchIn(after)
                            val isCase =
                                Regex("""^\s*(case|default)\b""").containsMatchIn(after)
                            val unit = getIndentUnit(cx.state)
                            cx.baseIndent + (if (closed) 0 else if (isCase) 1 else 2) * unit
                        }
                        "ColonBlock" -> { cx -> cx.baseIndent + cx.unit }
                        "Block", "EnumBody", "DeclarationList" ->
                            delimitedIndent(closing = "}")
                        "ArrowFunction" -> { cx -> cx.baseIndent + cx.unit }
                        "String", "BlockComment" -> { _ -> null }
                        "Statement" -> continuedIndent(
                            except = Regex("""^(\{|end(for|foreach|switch|while)\b)""")
                        )
                        else -> null
                    }
                },
                foldNodeProp.add { type ->
                    when (type.name) {
                        "Block", "EnumBody", "DeclarationList",
                        "SwitchBody", "ArrayExpression",
                        "ValueList" ->
                            { node, _ -> foldInside(node) }
                        "ColonBlock" -> { node, _ ->
                            FoldRange(node.from + 1, node.to)
                        }
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
    name = "php"
)

/**
 * PHP language support.
 */
fun php(): LanguageSupport = LanguageSupport(
    phpLanguage,
    support = commentTokens.of(
        CommentTokens(
            line = "//",
            block = CommentTokens.BlockComment("/*", "*/")
        )
    )
)
