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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.monkopedia.kodemirror.language.FoldRange
import com.monkopedia.kodemirror.language.HighlightStyle
import com.monkopedia.kodemirror.language.Language
import com.monkopedia.kodemirror.language.TagStyleSpec
import com.monkopedia.kodemirror.language.defaultHighlightStyle
import com.monkopedia.kodemirror.language.foldInside
import com.monkopedia.kodemirror.language.foldNodeProp
import com.monkopedia.kodemirror.language.oneDarkHighlightStyle
import com.monkopedia.kodemirror.language.syntaxHighlighting
import com.monkopedia.kodemirror.lezer.highlight.tags
import com.monkopedia.kodemirror.lezer.javascript.parser
import com.monkopedia.kodemirror.lezer.lr.ParserConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.view.EditorTheme
import com.monkopedia.kodemirror.view.editorTheme

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

    val extremeContrastTheme = EditorTheme(
        background = Color(0xFF000000),
        foreground = Color(0xFFFFFFFF),
        cursor = Color(0xFF00FF00),
        selection = Color(0x999900FF.toInt()),
        activeLineBackground = Color(0x4DFFFF00),
        gutterBackground = Color(0xFF550000),
        gutterForeground = Color(0xFF00FFFF),
        gutterActiveForeground = Color(0xFFFF8800),
        gutterBorderColor = Color(0xFFFF00FF),
        contentTextStyle = TextStyle(
            fontSize = 13.sp,
            lineHeight = (13 * 1.4).sp,
            color = Color(0xFFFFFFFF)
        ),
        searchMatchBackground = Color(0x80AAFF00.toInt()),
        searchMatchSelectedBackground = Color(0x80FF4400.toInt()),
        selectionMatchBackground = Color(0x6600AAFF),
        matchingBracketBackground = Color(0x8000FF00.toInt()),
        nonMatchingBracketBackground = Color(0x80FF0000.toInt()),
        panelBackground = Color(0xFF000033),
        panelBorderColor = Color(0xFFFF00FF),
        buttonBackground = Color(0xFF003300),
        buttonBorderColor = Color(0xFF00FF00),
        inputBackground = Color(0xFF000066),
        inputBorderColor = Color(0xFF00FFFF),
        tooltipBackground = Color(0xFF000033),
        foldPlaceholderColor = Color(0xFFFFD700),
        foldPlaceholderBackground = Color(0x66FFD700),
        activeLineGutterBackground = Color(0x6600FF66),
        dark = true
    )

    private val extremeContrastHighlightStyle = HighlightStyle.define(
        listOf(
            TagStyleSpec(tags.keyword, SpanStyle(color = Color(0xFFFF0080.toInt()))),
            TagStyleSpec(
                listOf(tags.string, tags.inserted),
                SpanStyle(color = Color(0xFF00FF80))
            ),
            TagStyleSpec(
                listOf(tags.number, tags.changed, tags.annotation),
                SpanStyle(color = Color(0xFFFF8000.toInt()))
            ),
            TagStyleSpec(
                listOf(tags.meta, tags.comment),
                SpanStyle(color = Color(0xFF808080.toInt()))
            ),
            TagStyleSpec(
                listOf(tags.function(tags.variableName), tags.labelName),
                SpanStyle(color = Color(0xFF00FFFF))
            ),
            TagStyleSpec(tags.variableName, SpanStyle(color = Color(0xFFFFFF00.toInt()))),
            TagStyleSpec(
                listOf(tags.operator, tags.operatorKeyword),
                SpanStyle(color = Color(0xFFFF4040.toInt()))
            ),
            TagStyleSpec(
                listOf(tags.typeName, tags.className),
                SpanStyle(color = Color(0xFF8080FF.toInt()))
            ),
            TagStyleSpec(
                tags.definition(tags.variableName),
                SpanStyle(color = Color(0xFF00A0FF))
            ),
            TagStyleSpec(
                listOf(tags.name, tags.propertyName),
                SpanStyle(color = Color(0xFFFFA0A0.toInt()))
            )
        )
    )

    fun jsExtensionsContrast(): Extension {
        val lang = Language(configuredParser, "javascript")
        return ExtensionList(
            listOf(
                lang.extension,
                syntaxHighlighting(extremeContrastHighlightStyle),
                editorTheme.of(extremeContrastTheme)
            )
        )
    }
}
