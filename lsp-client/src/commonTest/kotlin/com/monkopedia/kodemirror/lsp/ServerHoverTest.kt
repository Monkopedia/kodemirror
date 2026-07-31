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
package com.monkopedia.kodemirror.lsp

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.lsp.Hover
import com.monkopedia.lsp.HoverContents
import com.monkopedia.lsp.MarkedString
import com.monkopedia.lsp.MarkedStringObject
import com.monkopedia.lsp.MarkupContent
import com.monkopedia.lsp.MarkupKind
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.StringOr
import com.monkopedia.lsp.markdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerHoverTest {
    private fun markedString(value: String): MarkedString = StringOr.StringValue(value)

    private fun markedString(language: String, value: String): MarkedString =
        StringOr.Value(MarkedStringObject(language = language, value = value))

    // --- parseHoverContents: union variants ---

    @Test
    fun parsesPlainString() {
        // A bare-string MarkedString is markdown per the LSP spec.
        val blocks = parseHoverContents(
            HoverContents.MarkedStringValue(markedString("hello world"))
        )
        assertEquals(1, blocks.size)
        assertEquals("hello world", blocks[0].text)
        assertTrue(blocks[0].markdown)
        assertNull(blocks[0].language)
    }

    @Test
    fun parsesMarkupContentMarkdown() {
        val blocks = parseHoverContents(
            HoverContents.MarkupContentValue(
                MarkupContent(kind = MarkupKind.MARKDOWN, value = "**bold** text")
            )
        )
        assertEquals(1, blocks.size)
        assertEquals("**bold** text", blocks[0].text)
        assertTrue(blocks[0].markdown)
        assertNull(blocks[0].language)
    }

    @Test
    fun parsesMarkupContentPlainText() {
        val blocks = parseHoverContents(
            HoverContents.MarkupContentValue(
                MarkupContent(kind = MarkupKind.PLAIN_TEXT, value = "*not* bold")
            )
        )
        assertEquals(1, blocks.size)
        assertFalse(blocks[0].markdown)
    }

    @Test
    fun parsesMarkedStringObject() {
        val blocks = parseHoverContents(
            HoverContents.MarkedStringValue(markedString("kotlin", "fun foo()"))
        )
        assertEquals(1, blocks.size)
        assertEquals("fun foo()", blocks[0].text)
        assertEquals("kotlin", blocks[0].language)
        assertFalse(blocks[0].markdown)
    }

    @Test
    fun parsesMarkedStringArray() {
        val blocks = parseHoverContents(
            HoverContents.MarkedStringArray(
                listOf(markedString("kotlin", "fun foo()"), markedString("some *markdown*"))
            )
        )
        assertEquals(2, blocks.size)
        assertEquals("kotlin", blocks[0].language)
        assertEquals("some *markdown*", blocks[1].text)
        // Bare string inside a MarkedString[] is markdown per the spec.
        assertTrue(blocks[1].markdown)
    }

    @Test
    fun dropsBlankBlocks() {
        assertTrue(
            parseHoverContents(HoverContents.MarkedStringValue(markedString("   "))).isEmpty()
        )
        assertTrue(parseHoverContents(HoverContents.MarkedStringArray(emptyList())).isEmpty())
        val blocks = parseHoverContents(
            HoverContents.MarkedStringArray(
                listOf(markedString("kotlin", "x"), markedString("  "))
            )
        )
        assertEquals(1, blocks.size)
    }

    // --- hoverBlockToAnnotatedString: markdown -> AnnotatedString ---

    private val styles = HoverMarkdownStyles()

    @Test
    fun plainTextIsVerbatim() {
        val out = hoverBlockToAnnotatedString(HoverBlock("**not** bold", markdown = false))
        assertEquals("**not** bold", out.text)
        assertTrue(out.spanStyles.isEmpty())
    }

    @Test
    fun languageBlockIsMonospace() {
        val out = hoverBlockToAnnotatedString(
            HoverBlock("fun foo()", language = "kotlin")
        )
        assertEquals("fun foo()", out.text)
        assertEquals(1, out.spanStyles.size)
        assertEquals(FontFamily.Monospace, out.spanStyles[0].item.fontFamily)
    }

    @Test
    fun boldIsStripped() {
        val out = hoverBlockToAnnotatedString(HoverBlock("a **b** c", markdown = true))
        assertEquals("a b c", out.text)
        val bold = out.spanStyles.first { it.item.fontWeight == FontWeight.Bold }
        assertEquals(2, bold.start)
        assertEquals(3, bold.end)
    }

    @Test
    fun italicIsStripped() {
        val out = hoverBlockToAnnotatedString(HoverBlock("a *b* c", markdown = true))
        assertEquals("a b c", out.text)
        assertTrue(out.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun inlineCodeIsStripped() {
        val out = hoverBlockToAnnotatedString(HoverBlock("call `foo()` now", markdown = true))
        assertEquals("call foo() now", out.text)
        assertTrue(out.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
    }

    @Test
    fun headingMarkerIsStripped() {
        val out = hoverBlockToAnnotatedString(HoverBlock("## Title", markdown = true))
        assertEquals("Title", out.text)
        assertTrue(out.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun fencedCodeBlockIsMonospaceWithoutFences() {
        val src = "before\n```kotlin\nval x = 1\n```\nafter"
        val out = hoverBlockToAnnotatedString(HoverBlock(src, markdown = true))
        assertEquals("before\nval x = 1\nafter", out.text)
        assertTrue(out.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
    }

    @Test
    fun unmatchedMarkerIsLiteral() {
        val out = hoverBlockToAnnotatedString(HoverBlock("a * b", markdown = true))
        assertEquals("a * b", out.text)
        assertTrue(out.spanStyles.isEmpty())
    }

    @Test
    fun customStylesAreApplied() {
        val custom = HoverMarkdownStyles(bold = SpanStyle(fontWeight = FontWeight.Black))
        val out = hoverBlockToAnnotatedString(HoverBlock("**x**", markdown = true), custom)
        assertEquals("x", out.text)
        assertEquals(FontWeight.Black, out.spanStyles[0].item.fontWeight)
    }

    // --- hoverTooltipPos: range honoring + remapping ---

    private fun doc(text: String): Text = Text.of(text.split("\n"))

    @Test
    fun usesPointerPosWhenNoRange() {
        val d = doc("line one\nline two")
        val hover = Hover(contents = HoverContents.markdown("x"), range = null)
        assertEquals(5, hoverTooltipPos(hover, 5, d, null))
    }

    @Test
    fun usesRangeStartWhenPresent() {
        val d = doc("line one\nline two")
        // Range starts at line 1 (0-based), char 0 -> offset 9.
        val hover = Hover(
            contents = HoverContents.markdown("x"),
            range = Range(
                start = Position(line = 1u, character = 0u),
                end = Position(line = 1u, character = 4u)
            )
        )
        assertEquals(9, hoverTooltipPos(hover, 12, d, null))
    }
}
