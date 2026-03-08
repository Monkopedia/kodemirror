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
package com.monkopedia.kodemirror.lang.cpp

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
 * A language provider based on the Lezer C++ parser, extended with
 * highlighting and indentation information.
 */
val cppLanguage: LRLanguage = LRLanguage.define(
    parser = cppParser.configure(
        ParserConfig(
            props = listOf(
                indentNodeProp.add { type ->
                    when {
                        type.name == "IfStatement" ->
                            continuedIndent(except = Regex("""^\s*(\{|else\b)"""))
                        type.name == "TryStatement" ->
                            continuedIndent(except = Regex("""^\s*(\{|catch)\b"""))
                        type.name == "LabeledStatement" -> flatIndent
                        type.name == "CaseStatement" -> { cx -> cx.baseIndent + cx.unit }
                        type.name == "BlockComment" -> { _ -> null }
                        type.name == "CompoundStatement" ->
                            delimitedIndent(closing = "}")
                        type.`is`("Statement") ->
                            continuedIndent(except = Regex("""^\{"""))
                        else -> null
                    }
                },
                foldNodeProp.add { type ->
                    when (type.name) {
                        "DeclarationList", "CompoundStatement",
                        "EnumeratorList", "FieldDeclarationList",
                        "InitializerList" -> { node, _ -> foldInside(node) }
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
    name = "cpp"
)

/**
 * Language support for C++.
 */
fun cpp(): LanguageSupport = LanguageSupport(
    cppLanguage,
    support = commentTokens.of(
        CommentTokens(
            line = "//",
            block = CommentTokens.BlockComment("/*", "*/")
        )
    )
)
