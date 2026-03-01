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
package com.monkopedia.kodemirror.view.screenshots

import com.monkopedia.kodemirror.language.FoldRange
import com.monkopedia.kodemirror.language.Language
import com.monkopedia.kodemirror.language.defaultHighlightStyle
import com.monkopedia.kodemirror.language.foldInside
import com.monkopedia.kodemirror.language.foldNodeProp
import com.monkopedia.kodemirror.language.oneDarkHighlightStyle
import com.monkopedia.kodemirror.language.syntaxHighlighting
import com.monkopedia.kodemirror.lezer.javascript.parser
import com.monkopedia.kodemirror.lezer.lr.ParserConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList

/**
 * Shared sample content strings for screenshot tests.
 * These match the content used in the Playwright HTML scenario files
 * so that visual comparisons are meaningful.
 */
object TestScenarios {

    val SAMPLE_CODE = """
        |function fibonacci(n) {
        |  if (n <= 1) return n;
        |  return fibonacci(n - 1) + fibonacci(n - 2);
        |}
        |
        |// Calculate first 10 Fibonacci numbers
        |const results = [];
        |for (let i = 0; i < 10; i++) {
        |  results.push(fibonacci(i));
        |}
        |console.log(results);
    """.trimMargin()

    val PLACEHOLDER_TEXT = "Enter your code here..."

    private val foldPropSource = foldNodeProp.add { type ->
        val foldableBlocks = setOf(
            "Block",
            "ClassBody",
            "SwitchBody",
            "EnumBody",
            "ObjectExpression",
            "ArrayExpression",
            "ObjectType"
        )
        when (type.name) {
            in foldableBlocks -> { node, _ -> foldInside(node) }
            "BlockComment" -> { node, _ ->
                val from = node.from + 2
                val to = node.to - 2
                if (from < to) FoldRange(from, to) else null
            }
            else -> null
        }
    }

    private val configuredParser = parser.configure(
        ParserConfig(props = listOf(foldPropSource))
    )

    fun jsLanguageExtensions(light: Boolean = true): Extension {
        val lang = Language(configuredParser, "javascript")
        val style = if (light) defaultHighlightStyle else oneDarkHighlightStyle
        return ExtensionList(listOf(lang.extension, syntaxHighlighting(style)))
    }
}
