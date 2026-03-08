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
import kotlin.test.assertTrue

class ExtensionTest {

    private val gfmParser = markdownParser.configure(GFM)

    private fun parse(input: String): String = treeToString(gfmParser.parse(input))

    // === GFM Strikethrough ===

    @Test
    fun parsesStrikethrough() {
        val result = parse("~~deleted~~")
        assertTrue(
            result.contains("Strikethrough"),
            "Expected Strikethrough in: $result"
        )
    }

    @Test
    fun parsesStrikethroughInParagraph() {
        val result = parse("hello ~~world~~ foo")
        assertTrue(
            result.contains("Strikethrough"),
            "Expected Strikethrough in: $result"
        )
        assertTrue(result.contains("Paragraph"))
    }

    // === GFM Tables ===

    @Test
    fun parsesSimpleTable() {
        val input = "| a | b |\n| - | - |\n| c | d |"
        val result = parse(input)
        assertTrue(
            result.contains("Table"),
            "Expected Table in: $result"
        )
        assertTrue(
            result.contains("TableHeader"),
            "Expected TableHeader in: $result"
        )
        assertTrue(
            result.contains("TableRow"),
            "Expected TableRow in: $result"
        )
        assertTrue(
            result.contains("TableCell"),
            "Expected TableCell in: $result"
        )
    }

    @Test
    fun parsesTableWithAlignment() {
        val input = "| left | center | right |\n| :--- | :---: | ---: |\n| a | b | c |"
        val result = parse(input)
        assertTrue(
            result.contains("Table"),
            "Expected Table in: $result"
        )
        assertTrue(
            result.contains("TableDelimiter"),
            "Expected TableDelimiter in: $result"
        )
    }

    // === GFM Task Lists ===

    @Test
    fun parsesTaskList() {
        val input = "- [ ] unchecked\n- [x] checked"
        val result = parse(input)
        assertTrue(
            result.contains("BulletList"),
            "Expected BulletList in: $result"
        )
        assertTrue(
            result.contains("Task"),
            "Expected Task or TaskMarker in: $result"
        )
    }

    // === Subscript ===

    @Test
    fun parsesSubscript() {
        val subParser = markdownParser.configure(
            markdownExtensionOf(Subscript)
        )
        val result = treeToString(subParser.parse("H~2~O"))
        assertTrue(
            result.contains("Subscript"),
            "Expected Subscript in: $result"
        )
    }

    // === Superscript ===

    @Test
    fun parsesSuperscript() {
        val supParser = markdownParser.configure(
            markdownExtensionOf(Superscript)
        )
        val result = treeToString(supParser.parse("x^2^"))
        assertTrue(
            result.contains("Superscript"),
            "Expected Superscript in: $result"
        )
    }

    // === Emoji ===

    @Test
    fun parsesEmoji() {
        val emojiParser = markdownParser.configure(
            markdownExtensionOf(Emoji)
        )
        val result = treeToString(emojiParser.parse(":smile:"))
        assertTrue(
            result.contains("Emoji"),
            "Expected Emoji in: $result"
        )
    }

    // === Combined GFM ===

    @Test
    fun parsesGfmCombined() {
        val input = """| header |
| ------ |
| cell |

- [ ] task
- [x] done

~~deleted~~"""
        val result = parse(input)
        assertTrue(result.contains("Table"), "Expected Table in: $result")
        assertTrue(
            result.contains("Strikethrough"),
            "Expected Strikethrough in: $result"
        )
        assertTrue(
            result.contains("BulletList"),
            "Expected BulletList in: $result"
        )
    }
}
