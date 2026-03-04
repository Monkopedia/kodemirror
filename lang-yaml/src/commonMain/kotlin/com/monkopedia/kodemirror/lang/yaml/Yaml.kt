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
package com.monkopedia.kodemirror.lang.yaml

import com.monkopedia.kodemirror.language.CommentTokens
import com.monkopedia.kodemirror.language.FoldRange
import com.monkopedia.kodemirror.language.LRLanguage
import com.monkopedia.kodemirror.language.LanguageSupport
import com.monkopedia.kodemirror.language.commentTokens
import com.monkopedia.kodemirror.language.delimitedIndent
import com.monkopedia.kodemirror.language.foldInside
import com.monkopedia.kodemirror.language.foldNodeProp
import com.monkopedia.kodemirror.language.indentNodeProp
import com.monkopedia.kodemirror.lezer.lr.ParserConfig

/**
 * A language provider based on the Lezer YAML parser, extended with
 * highlighting and indentation information.
 */
val yamlLanguage: LRLanguage = LRLanguage.define(
    parser = parser.configure(
        ParserConfig(
            props = listOf(
                indentNodeProp.add { type ->
                    when (type.name) {
                        "Stream" -> { cx ->
                            var before = cx.node.resolve(cx.pos, -1)
                            while (before.to >= cx.pos) {
                                when (before.name) {
                                    "BlockLiteralContent" ->
                                        if (before.from < before.to) {
                                            return@add cx.baseIndentFor(before)
                                        }
                                    "BlockLiteral" ->
                                        return@add cx.baseIndentFor(before) + cx.unit
                                    "BlockSequence", "BlockMapping" ->
                                        return@add cx.column(before.from, 1)
                                    "QuotedLiteral" -> return@add null
                                    "Literal" -> {
                                        val col = cx.column(before.from, 1)
                                        if (col == cx.lineIndent(before.from, 1)) {
                                            return@add col
                                        }
                                        if (before.to > cx.pos) return@add null
                                    }
                                }
                                before = before.parent ?: break
                            }
                            null
                        }
                        "FlowMapping" -> delimitedIndent(closing = "}")
                        "FlowSequence" -> delimitedIndent(closing = "]")
                        else -> null
                    }
                },
                foldNodeProp.add { type ->
                    when (type.name) {
                        "FlowMapping", "FlowSequence" ->
                            { node, _ -> foldInside(node) }
                        "Item", "Pair", "BlockLiteral" -> { node, state ->
                            FoldRange(
                                state.doc.lineAt(node.from).to,
                                node.to
                            )
                        }
                        else -> null
                    }
                }
            )
        )
    ),
    name = "yaml"
)

/**
 * YAML language support.
 */
fun yaml(): LanguageSupport = LanguageSupport(
    yamlLanguage,
    support = commentTokens.of(
        CommentTokens(line = "#")
    )
)
