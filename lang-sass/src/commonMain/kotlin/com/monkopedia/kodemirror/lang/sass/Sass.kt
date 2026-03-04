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
package com.monkopedia.kodemirror.lang.sass

import com.monkopedia.kodemirror.language.CommentTokens
import com.monkopedia.kodemirror.language.FoldRange
import com.monkopedia.kodemirror.language.LRLanguage
import com.monkopedia.kodemirror.language.LanguageSupport
import com.monkopedia.kodemirror.language.commentTokens
import com.monkopedia.kodemirror.language.continuedIndent
import com.monkopedia.kodemirror.language.foldInside
import com.monkopedia.kodemirror.language.foldNodeProp
import com.monkopedia.kodemirror.language.indentNodeProp
import com.monkopedia.kodemirror.lezer.lr.ParserConfig

/**
 * A language provider based on the Lezer Sass parser, extended with
 * highlighting and indentation information.
 */
val sassLanguage: LRLanguage = LRLanguage.define(
    parser = parser.configure(
        ParserConfig(
            props = listOf(
                foldNodeProp.add { type ->
                    when (type.name) {
                        "Block" -> { node, _ -> foldInside(node) }
                        "Comment" -> { node, state ->
                            val text = state.doc.sliceString(node.to - 2, node.to)
                            FoldRange(
                                node.from + 2,
                                if (text == "*/") node.to - 2 else node.to
                            )
                        }
                        else -> null
                    }
                },
                indentNodeProp.add(
                    mapOf("Declaration" to continuedIndent())
                )
            )
        )
    ),
    name = "sass"
)

/**
 * Sass/SCSS language support.
 */
fun sass(): LanguageSupport = LanguageSupport(
    sassLanguage,
    support = commentTokens.of(
        CommentTokens(
            line = "//",
            block = CommentTokens.BlockComment("/*", "*/")
        )
    )
)
