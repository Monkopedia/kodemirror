/*
 * Copyright 2025 Jason Monk
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
package com.monkopedia.kodemirror.lezer.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownParserTest {

    private fun parse(input: String): String = treeToString(markdownParser.parse(input))

    // === Paragraphs ===

    @Test
    fun parsesSimpleParagraph() {
        val result = parse("hello")
        assertTrue(result.contains("Document"))
        assertTrue(result.contains("Paragraph"))
    }

    @Test
    fun parsesMultipleParagraphs() {
        val result = parse("hello\n\nworld")
        assertTrue(result.contains("Document"))
        // Should have two Paragraph nodes
        val count = Regex("Paragraph").findAll(result).count()
        assertEquals(2, count, "Expected 2 paragraphs, got: $result")
    }

    // === ATX Headings ===

    @Test
    fun parsesAtxHeading1() {
        val result = parse("# Hello")
        assertTrue(
            result.contains("ATXHeading1"),
            "Expected ATXHeading1 in: $result"
        )
        assertTrue(result.contains("HeaderMark"))
    }

    @Test
    fun parsesAtxHeading2() {
        val result = parse("## Hello")
        assertTrue(
            result.contains("ATXHeading2"),
            "Expected ATXHeading2 in: $result"
        )
    }

    @Test
    fun parsesAtxHeading3() {
        val result = parse("### Hello")
        assertTrue(
            result.contains("ATXHeading3"),
            "Expected ATXHeading3 in: $result"
        )
    }

    @Test
    fun parsesAtxHeading6() {
        val result = parse("###### Hello")
        assertTrue(
            result.contains("ATXHeading6"),
            "Expected ATXHeading6 in: $result"
        )
    }

    // === Setext Headings ===

    @Test
    fun parsesSetextHeading1() {
        val result = parse("Hello\n=====")
        assertTrue(
            result.contains("SetextHeading1"),
            "Expected SetextHeading1 in: $result"
        )
    }

    @Test
    fun parsesSetextHeading2() {
        val result = parse("Hello\n-----")
        assertTrue(
            result.contains("SetextHeading2"),
            "Expected SetextHeading2 in: $result"
        )
    }

    // === Horizontal Rule ===

    @Test
    fun parsesHorizontalRule() {
        val result = parse("---")
        assertTrue(
            result.contains("HorizontalRule"),
            "Expected HorizontalRule in: $result"
        )
    }

    @Test
    fun parsesHorizontalRuleStars() {
        val result = parse("***")
        assertTrue(
            result.contains("HorizontalRule"),
            "Expected HorizontalRule in: $result"
        )
    }

    // === Blockquotes ===

    @Test
    fun parsesBlockquote() {
        val result = parse("> hello")
        assertTrue(
            result.contains("Blockquote"),
            "Expected Blockquote in: $result"
        )
        assertTrue(result.contains("QuoteMark"))
        assertTrue(result.contains("Paragraph"))
    }

    @Test
    fun parsesNestedBlockquote() {
        val result = parse("> > hello")
        val count = Regex("Blockquote").findAll(result).count()
        assertTrue(
            count >= 2,
            "Expected nested Blockquotes in: $result"
        )
    }

    // === Lists ===

    @Test
    fun parsesBulletList() {
        val result = parse("- item 1\n- item 2")
        assertTrue(
            result.contains("BulletList"),
            "Expected BulletList in: $result"
        )
        assertTrue(result.contains("ListItem"))
        assertTrue(result.contains("ListMark"))
    }

    @Test
    fun parsesOrderedList() {
        val result = parse("1. item 1\n2. item 2")
        assertTrue(
            result.contains("OrderedList"),
            "Expected OrderedList in: $result"
        )
        assertTrue(result.contains("ListItem"))
    }

    @Test
    fun parsesBulletListStar() {
        val result = parse("* item 1\n* item 2")
        assertTrue(
            result.contains("BulletList"),
            "Expected BulletList in: $result"
        )
    }

    // === Code Blocks ===

    @Test
    fun parsesIndentedCode() {
        val result = parse("    code line")
        assertTrue(
            result.contains("CodeBlock"),
            "Expected CodeBlock in: $result"
        )
    }

    @Test
    fun parsesFencedCode() {
        val result = parse("```\ncode\n```")
        assertTrue(
            result.contains("FencedCode"),
            "Expected FencedCode in: $result"
        )
        assertTrue(result.contains("CodeMark"))
        assertTrue(result.contains("CodeText"))
    }

    @Test
    fun parsesFencedCodeWithInfo() {
        val result = parse("```kotlin\nval x = 1\n```")
        assertTrue(
            result.contains("FencedCode"),
            "Expected FencedCode in: $result"
        )
        assertTrue(
            result.contains("CodeInfo"),
            "Expected CodeInfo in: $result"
        )
    }

    @Test
    fun parsesFencedCodeTildes() {
        val result = parse("~~~\ncode\n~~~")
        assertTrue(
            result.contains("FencedCode"),
            "Expected FencedCode in: $result"
        )
    }

    // === HTML Blocks ===

    @Test
    fun parsesHtmlBlock() {
        val result = parse("<div>\nhello\n</div>")
        assertTrue(
            result.contains("HTMLBlock"),
            "Expected HTMLBlock in: $result"
        )
    }

    // === Inline: Emphasis ===

    @Test
    fun parsesEmphasis() {
        val result = parse("*hello*")
        assertTrue(
            result.contains("Emphasis"),
            "Expected Emphasis in: $result"
        )
        assertTrue(result.contains("EmphasisMark"))
    }

    @Test
    fun parsesEmphasisUnderscore() {
        val result = parse("_hello_")
        assertTrue(
            result.contains("Emphasis"),
            "Expected Emphasis in: $result"
        )
    }

    @Test
    fun parsesStrongEmphasis() {
        val result = parse("**hello**")
        assertTrue(
            result.contains("StrongEmphasis"),
            "Expected StrongEmphasis in: $result"
        )
    }

    @Test
    fun parsesStrongEmphasisUnderscore() {
        val result = parse("__hello__")
        assertTrue(
            result.contains("StrongEmphasis"),
            "Expected StrongEmphasis in: $result"
        )
    }

    // === Inline: Code ===

    @Test
    fun parsesInlineCode() {
        val result = parse("`code`")
        assertTrue(
            result.contains("InlineCode"),
            "Expected InlineCode in: $result"
        )
        assertTrue(result.contains("CodeMark"))
    }

    @Test
    fun parsesInlineCodeDouble() {
        val result = parse("``code with ` backtick``")
        assertTrue(
            result.contains("InlineCode"),
            "Expected InlineCode in: $result"
        )
    }

    // === Inline: Links ===

    @Test
    fun parsesLink() {
        val result = parse("[text](url)")
        assertTrue(
            result.contains("Link"),
            "Expected Link in: $result"
        )
        assertTrue(result.contains("LinkMark"))
        assertTrue(result.contains("URL"))
    }

    @Test
    fun parsesLinkWithTitle() {
        val result = parse("[text](url \"title\")")
        assertTrue(
            result.contains("Link"),
            "Expected Link in: $result"
        )
        assertTrue(result.contains("LinkTitle"))
    }

    // === Inline: Images ===

    @Test
    fun parsesImage() {
        val result = parse("![alt](url)")
        assertTrue(
            result.contains("Image"),
            "Expected Image in: $result"
        )
        assertTrue(result.contains("URL"))
    }

    // === Inline: Escape ===

    @Test
    fun parsesEscape() {
        val result = parse("\\*not emphasis\\*")
        assertTrue(
            result.contains("Escape"),
            "Expected Escape in: $result"
        )
    }

    // === Inline: Entity ===

    @Test
    fun parsesEntity() {
        val result = parse("&amp;")
        assertTrue(
            result.contains("Entity"),
            "Expected Entity in: $result"
        )
    }

    @Test
    fun parsesNumericEntity() {
        val result = parse("&#65;")
        assertTrue(
            result.contains("Entity"),
            "Expected Entity in: $result"
        )
    }

    // === Inline: Hard Break ===

    @Test
    fun parsesHardBreak() {
        val result = parse("hello  \nworld")
        assertTrue(
            result.contains("HardBreak"),
            "Expected HardBreak in: $result"
        )
    }

    // === Inline: HTML Tag ===

    @Test
    fun parsesInlineHtmlTag() {
        val result = parse("hello <b>bold</b> world")
        assertTrue(
            result.contains("HTMLTag"),
            "Expected HTMLTag in: $result"
        )
    }

    // === Inline: Autolink ===

    @Test
    fun parsesAutolink() {
        val result = parse("<https://example.com>")
        assertTrue(
            result.contains("Autolink"),
            "Expected Autolink in: $result"
        )
    }

    // === Link References ===

    @Test
    fun parsesLinkReference() {
        val result = parse("[foo]: /url \"title\"\n\n[foo]")
        assertTrue(
            result.contains("LinkReference"),
            "Expected LinkReference in: $result"
        )
        assertTrue(result.contains("Link"))
    }

    // === Combined ===

    @Test
    fun parsesEmptyDocument() {
        val result = parse("")
        assertTrue(
            result.contains("Document"),
            "Expected Document in: $result"
        )
    }

    @Test
    fun parsesComplexDocument() {
        val input = """# Heading

A paragraph with *emphasis* and **strong**.

- item 1
- item 2

> A quote

```
code
```"""
        val result = parse(input)
        assertTrue(result.contains("ATXHeading1"))
        assertTrue(result.contains("Paragraph"))
        assertTrue(result.contains("Emphasis"))
        assertTrue(result.contains("StrongEmphasis"))
        assertTrue(result.contains("BulletList"))
        assertTrue(result.contains("Blockquote"))
        assertTrue(result.contains("FencedCode"))
    }
}
