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
package com.monkopedia.kodemirror.language

import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Minimal state: just tracks whether we're in a string. */
private data class SimpleState(var inString: Boolean = false)

/**
 * A minimal stream parser that recognizes:
 * - keywords: "if", "else", "return"
 * - strings: double-quoted
 * - numbers: sequences of digits
 * - everything else as null (no style)
 */
private val simpleParser = object : StreamParser<SimpleState> {
    override val name: String get() = "simple"
    private val keywords = setOf("if", "else", "return")

    override fun startState(indentUnit: Int) = SimpleState()

    override fun copyState(state: SimpleState) = state.copy()

    override fun token(stream: StringStream, state: SimpleState): String? {
        if (state.inString) {
            while (!stream.eol()) {
                if (stream.next() == "\"") {
                    state.inString = false
                    return "string"
                }
            }
            return "string"
        }
        if (stream.eatSpace()) return null
        if (stream.eat("\"") != null) {
            state.inString = true
            while (!stream.eol()) {
                if (stream.next() == "\"") {
                    state.inString = false
                    return "string"
                }
            }
            return "string"
        }
        if (stream.eatWhile(Regex("[0-9]"))) return "number"
        if (stream.eatWhile(Regex("[a-zA-Z_]"))) {
            return if (stream.current() in keywords) "keyword" else "variableName"
        }
        stream.next()
        return null
    }
}

class StreamParserTest {

    @Test
    fun defineCreatesStreamLanguage() {
        val lang = StreamLanguage.define(simpleParser)
        assertNotNull(lang)
        assertEquals("simple", lang.name)
        assertFalse(lang.allowsNesting)
    }

    @Test
    fun parsesSimpleDocument() {
        val lang = StreamLanguage.define(simpleParser)
        val state = EditorState.create(
            EditorStateConfig(
                doc = "if x 42".asDoc(),
                extensions = lang.extension
            )
        )
        val tree = syntaxTree(state)
        assertNotNull(tree)
        assertEquals(7, tree.length)
        assertEquals("Document", tree.type.name)
    }

    @Test
    fun parsesMultiLineDocument() {
        val lang = StreamLanguage.define(simpleParser)
        val state = EditorState.create(
            EditorStateConfig(
                doc = "if x\nreturn 42".asDoc(),
                extensions = lang.extension
            )
        )
        val tree = syntaxTree(state)
        assertNotNull(tree)
        assertEquals(14, tree.length)
    }

    @Test
    fun parsesEmptyDocument() {
        val lang = StreamLanguage.define(simpleParser)
        val state = EditorState.create(
            EditorStateConfig(
                doc = "".asDoc(),
                extensions = lang.extension
            )
        )
        val tree = syntaxTree(state)
        assertNotNull(tree)
        assertEquals(0, tree.length)
    }

    @Test
    fun treeHasTokenNodes() {
        val lang = StreamLanguage.define(simpleParser)
        val state = EditorState.create(
            EditorStateConfig(
                doc = "if 42".asDoc(),
                extensions = lang.extension
            )
        )
        val tree = syntaxTree(state)
        // The tree should have child nodes for "if" (keyword) and "42" (number)
        var foundKeyword = false
        var foundNumber = false
        val cursor = tree.cursor()
        while (cursor.next()) {
            if (cursor.type.name == "keyword") foundKeyword = true
            if (cursor.type.name == "number") foundNumber = true
        }
        assertTrue(foundKeyword, "Expected keyword token in tree")
        assertTrue(foundNumber, "Expected number token in tree")
    }

    @Test
    fun parsesStrings() {
        val lang = StreamLanguage.define(simpleParser)
        val state = EditorState.create(
            EditorStateConfig(
                doc = "\"hello\"".asDoc(),
                extensions = lang.extension
            )
        )
        val tree = syntaxTree(state)
        var foundString = false
        val cursor = tree.cursor()
        while (cursor.next()) {
            if (cursor.type.name == "string") foundString = true
        }
        assertTrue(foundString, "Expected string token in tree")
    }

    @Test
    fun handlesBlankLines() {
        val lang = StreamLanguage.define(simpleParser)
        val state = EditorState.create(
            EditorStateConfig(
                doc = "if\n\nreturn".asDoc(),
                extensions = lang.extension
            )
        )
        val tree = syntaxTree(state)
        assertNotNull(tree)
        assertEquals(10, tree.length)
    }

    @Test
    fun languageExtensionWorks() {
        val lang = StreamLanguage.define(simpleParser)
        val state = EditorState.create(
            EditorStateConfig(
                doc = "test".asDoc(),
                extensions = lang.extension
            )
        )
        val resolved = state.facet(language)
        assertNotNull(resolved)
        assertEquals("simple", resolved.name)
    }
}
