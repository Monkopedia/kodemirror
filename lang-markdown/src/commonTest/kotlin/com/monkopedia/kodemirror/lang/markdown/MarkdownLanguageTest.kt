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
package com.monkopedia.kodemirror.lang.markdown

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarkdownLanguageTest {

    @Test
    fun commonmarkLanguageExists() {
        assertNotNull(commonmarkLanguage)
        assertNotNull(commonmarkLanguage.parser)
    }

    @Test
    fun markdownLanguageExists() {
        assertNotNull(markdownLanguage)
        assertNotNull(markdownLanguage.parser)
    }

    @Test
    fun commonmarkLanguageParsesParagraph() {
        val tree = commonmarkLanguage.parser.parse("hello")
        val result = treeToString(tree)
        assertTrue(
            result.contains("Document"),
            "Expected Document in: $result"
        )
        assertTrue(
            result.contains("Paragraph"),
            "Expected Paragraph in: $result"
        )
    }

    @Test
    fun markdownLanguageParsesHeading() {
        val tree = markdownLanguage.parser.parse("# Hello")
        val result = treeToString(tree)
        assertTrue(
            result.contains("ATXHeading1"),
            "Expected ATXHeading1 in: $result"
        )
    }

    @Test
    fun markdownLanguageParsesGfmTable() {
        val input = "| a | b |\n| - | - |\n| c | d |"
        val tree = markdownLanguage.parser.parse(input)
        val result = treeToString(tree)
        assertTrue(
            result.contains("Table"),
            "Expected Table in: $result"
        )
    }

    @Test
    fun markdownSupportCreatesLanguageSupport() {
        val support = markdown()
        assertNotNull(support)
        assertNotNull(support.language)
    }

    @Test
    fun markdownSupportWithCustomConfig() {
        val support = markdown(
            MarkdownSupportConfig(addKeymap = false)
        )
        assertNotNull(support)
    }

    @Test
    fun markdownKeymapIsNotEmpty() {
        assertTrue(
            markdownKeymap.isNotEmpty(),
            "markdownKeymap should not be empty"
        )
    }

    @Test
    fun isHeadingDetectsHeadings() {
        val tree = commonmarkLanguage.parser.parse("# Hello")
        var foundHeading = false
        tree.iterate(
            com.monkopedia.kodemirror.lezer.common.IterateSpec(
                enter = { nodeRef ->
                    val level = isHeading(nodeRef.type)
                    if (level != null) {
                        foundHeading = true
                    }
                    null
                }
            )
        )
        assertTrue(foundHeading, "Should detect heading in tree")
    }

    @Test
    fun isListDetectsLists() {
        val tree = commonmarkLanguage.parser.parse("- item")
        var foundList = false
        tree.iterate(
            com.monkopedia.kodemirror.lezer.common.IterateSpec(
                enter = { nodeRef ->
                    if (isList(nodeRef.type)) {
                        foundList = true
                    }
                    null
                }
            )
        )
        assertTrue(foundList, "Should detect list in tree")
    }
}
