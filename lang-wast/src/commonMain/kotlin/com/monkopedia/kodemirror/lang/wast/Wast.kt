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
package com.monkopedia.kodemirror.lang.wast

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
import com.monkopedia.kodemirror.state.DocPos

/**
 * A language provider for WebAssembly Text format.
 */
val wastLanguage: LRLanguage = LRLanguage.define(
    parser = wastParser.configure(
        ParserConfig(
            props = listOf(
                indentNodeProp.add(
                    mapOf(
                        "App" to delimitedIndent(closing = ")", align = false)
                    )
                ),
                foldNodeProp.add(
                    mapOf(
                        "App" to { node, _ -> foldInside(node) },
                        "BlockComment" to { node, _ ->
                            FoldRange(DocPos(node.from + 2), DocPos(node.to - 2))
                        }
                    )
                )
            )
        )
    ),
    name = "wast"
)

/**
 * WebAssembly Text format language support.
 */
fun wast(): LanguageSupport = LanguageSupport(
    wastLanguage,
    support = commentTokens.of(
        CommentTokens(
            line = ";;",
            block = CommentTokens.BlockComment("(;", ";)")
        )
    )
)
