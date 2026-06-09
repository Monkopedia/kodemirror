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
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import com.monkopedia.kodemirror.lang.javascript.javascriptLanguage
import com.monkopedia.kodemirror.language.FoldRange
import com.monkopedia.kodemirror.language.HighlightStyle
import com.monkopedia.kodemirror.language.Language
import com.monkopedia.kodemirror.language.TagStyleSpec
import com.monkopedia.kodemirror.language.defaultHighlightStyle
import com.monkopedia.kodemirror.language.foldInside
import com.monkopedia.kodemirror.language.foldNodeProp
import com.monkopedia.kodemirror.language.oneDarkHighlightStyle
import com.monkopedia.kodemirror.language.syntaxHighlighting
import com.monkopedia.kodemirror.lezer.highlight.Tags
import com.monkopedia.kodemirror.lezer.lr.LRParser
import com.monkopedia.kodemirror.lezer.lr.ParserConfig
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.view.EditorTheme
import com.monkopedia.kodemirror.view.editorContentStyle
import com.monkopedia.kodemirror.view.editorTheme

/**
 * Shared sample content strings for screenshot tests.
 * These match the content used in the Playwright HTML scenario files
 * so that visual comparisons are meaningful.
 */
object TestScenarios {

    /**
     * Deterministic monospace font for screenshot baselines.
     *
     * The editor's default [com.monkopedia.kodemirror.view.defaultEditorFontFamily]
     * is [FontFamily.Monospace], which resolves to whatever monospace font the host
     * OS happens to provide. That makes glyph advance widths (and therefore the
     * rendered pixels) vary across machines, so committed baselines fail
     * `verifyRoborazzi` on a different runner purely from font drift.
     *
     * We pin a bundled JetBrains Mono Regular (OFL-1.1, see
     * `view/src/jvmTest/resources/fonts/OFL.txt`) loaded from the test classpath
     * via the Compose Desktop `Font(resource: String)` factory. Skiko's rasteriser
     * is bundled with Compose (it does not use OS-native rasterisation), so the
     * same font + same Skiko version produces the same pixels everywhere, making
     * the baselines reproducible across the local dev machine and CI runners.
     */
    val pinnedFontFamily: FontFamily = FontFamily(
        Font(resource = "fonts/JetBrainsMono-Regular.ttf")
    )

    /**
     * Extension that forces every screenshot to render with [pinnedFontFamily].
     * Applied by [jsLanguageExtensions] / [jsExtensionsContrast] and the
     * placeholder scenario so all baselines share one deterministic font.
     */
    val pinnedFont: Extension = editorContentStyle.of(
        TextStyle(fontFamily = pinnedFontFamily)
    )

    /**
     * Roborazzi compare options shared by every screenshot test.
     *
     * With [pinnedFont] the render is intended to be byte-stable across machines,
     * but we allow a tiny non-zero [changeThreshold][RoborazziOptions.CompareOptions]
     * (0.1% of pixels) as a safety margin against incidental antialiasing drift
     * between the local dev machine and the CI runner. This is small enough that a
     * real visual regression (e.g. a missing highlight fill or a shifted line) still
     * trips the gate, while sub-pixel noise does not turn the gate flaky.
     */
    val screenshotOptions: RoborazziOptions = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f)
    )

    /** Capture [path] using the shared [screenshotOptions] tolerance. */
    fun SemanticsNodeInteraction.captureScreenshot(path: String) {
        captureRoboImage(filePath = path, roborazziOptions = screenshotOptions)
    }

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

    val BIDI_CODE = """
        |// مرحبا بالعالم - Hello World in Arabic
        |// שלום עולם - Hello World in Hebrew
        |
        |function greet(name) {
        |  // التحية بالاسم
        |  const greeting = "مرحبا، " + name + "!";
        |  console.log(greeting);
        |}
        |
        |const names = [
        |  "Alice",
        |  "أحمد",     // Ahmed
        |  "דוד",       // David
        |  "خالد",     // Khaled
        |];
        |
        |// حلقة لطباعة كل الأسماء
        |for (const n of names) {
        |  greet(n); // استدعاء الدالة
        |}
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
                if (from < to) FoldRange(DocPos(from), DocPos(to)) else null
            }
            else -> null
        }
    }

    private val configuredParser = (javascriptLanguage.parser as LRParser).configure(
        ParserConfig(props = listOf(foldPropSource))
    )

    fun jsLanguageExtensions(light: Boolean = true): Extension {
        val lang = Language(configuredParser, "javascript")
        val style = if (light) defaultHighlightStyle else oneDarkHighlightStyle
        return extensionListOf(lang.extension, syntaxHighlighting(style), pinnedFont)
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
            TagStyleSpec(Tags.keyword, SpanStyle(color = Color(0xFFFF0080.toInt()))),
            TagStyleSpec(
                listOf(Tags.string, Tags.inserted),
                SpanStyle(color = Color(0xFF00FF80))
            ),
            TagStyleSpec(
                listOf(Tags.number, Tags.changed, Tags.annotation),
                SpanStyle(color = Color(0xFFFF8000.toInt()))
            ),
            TagStyleSpec(
                listOf(Tags.meta, Tags.comment),
                SpanStyle(color = Color(0xFF808080.toInt()))
            ),
            TagStyleSpec(
                listOf(Tags.function(Tags.variableName), Tags.labelName),
                SpanStyle(color = Color(0xFF00FFFF))
            ),
            TagStyleSpec(Tags.variableName, SpanStyle(color = Color(0xFFFFFF00.toInt()))),
            TagStyleSpec(
                listOf(Tags.operator, Tags.operatorKeyword),
                SpanStyle(color = Color(0xFFFF4040.toInt()))
            ),
            TagStyleSpec(
                listOf(Tags.typeName, Tags.className),
                SpanStyle(color = Color(0xFF8080FF.toInt()))
            ),
            TagStyleSpec(
                Tags.definition(Tags.variableName),
                SpanStyle(color = Color(0xFF00A0FF))
            ),
            TagStyleSpec(
                listOf(Tags.name, Tags.propertyName),
                SpanStyle(color = Color(0xFFFFA0A0.toInt()))
            )
        )
    )

    fun jsExtensionsContrast(): Extension {
        val lang = Language(configuredParser, "javascript")
        return extensionListOf(
            lang.extension,
            syntaxHighlighting(extremeContrastHighlightStyle),
            editorTheme.of(extremeContrastTheme),
            pinnedFont,
            editorContentStyle.of(
                TextStyle(fontSize = 13.sp, lineHeight = (13 * 1.4).sp)
            )
        )
    }
}
