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
package com.monkopedia.kodemirror.autocomplete

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.languageData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [completeFromList], [completeAnyWord] and [insertCompletionText] against the
 * output of @codemirror/autocomplete 6.20.1 on the same inputs (#383).
 */
class CompletionHelpersTest {

    private fun state(doc: String, cursor: Int = doc.length): EditorState = EditorState.create(
        EditorStateConfig(
            doc = doc.asDoc(),
            selection = SelectionSpec.EditorSelectionSpec(EditorSelection.single(DocPos(cursor)))
        )
    )

    private fun context(doc: String, explicit: Boolean = false) =
        CompletionContext(state(doc), DocPos(doc.length), explicit)

    @Test
    fun completeFromListFiresAfterTheFirstWordOfALine() {
        val result =
            completeFromList(listOf(Completion("bar"), Completion("baz")))(context("foo ba"))
        assertNotNull(result)
        assertEquals(DocPos(4), result.from)
    }

    @Test
    fun completeFromListMatchesNonWordLabelCharacters() {
        val list = listOf(Completion("@media"), Completion("@import"))
        val result = completeFromList(list)(context("@me"))
        assertNotNull(result)
        assertEquals(DocPos(0), result.from)
        val validFor = assertNotNull(result.validFor)
        assertEquals(true, validFor.matches("@med"))
        assertEquals(false, validFor.matches("x@med"))

        val s = state("@me")
        val accepted = s.update(insertCompletionText(s, "@media", result.from, DocPos(3))).state
        assertEquals("@media", accepted.doc.toString())
    }

    @Test
    fun completeFromListStaysQuietWithoutAToken() {
        assertNull(completeFromList(listOf(Completion("bar")))(context("foo ")))
    }

    @Test
    fun completeAnyWordOffersOtherWordsForASingleCharacter() {
        val result = completeAnyWord(context("foo bar f"))
        assertNotNull(result)
        assertEquals(DocPos(8), result.from)
        assertEquals(listOf("foo", "bar"), result.options.map { it.label })
    }

    @Test
    fun completeAnyWordSkipsOnlyTheWordAtTheCursor() {
        val result = completeAnyWord(context("foo bar foo"))
        assertNotNull(result)
        assertEquals(DocPos(8), result.from)
        assertEquals(listOf("foo", "bar"), result.options.map { it.label })
    }

    @Test
    fun completeAnyWordTreatsNonAsciiLettersAsWordCharacters() {
        val result = completeAnyWord(context("café caf"))
        assertNotNull(result)
        assertEquals(DocPos(5), result.from)
        assertEquals(listOf("café"), result.options.map { it.label })
    }

    @Test
    fun insertCompletionTextWithCrlfPlacesCursorAfterInsertedText() {
        val s = state("ab")
        val next = s.update(insertCompletionText(s, "x\r\ny", DocPos(2), DocPos(2))).state
        assertEquals("abx\ny", next.doc.toString())
        assertEquals(DocPos(5), next.selection.main.head)
    }

    @Test
    fun insertCompletionTextWithCrlfMidDocument() {
        val s = state("ab cd", cursor = 2)
        val next = s.update(insertCompletionText(s, "x\r\ny", DocPos(2), DocPos(2))).state
        assertEquals("abx\ny cd", next.doc.toString())
        assertEquals(DocPos(5), next.selection.main.head)
    }

    @Test
    fun completeAnyWordKeepsCombiningMarksInsideAWord() {
        // Devanagari vowel signs are combining marks (\p{M}); upstream's \p{Alphabetic}
        // keeps them, so the word is one option, not fragments.
        val result = completeAnyWord(context("हिंदी ह", explicit = false))
        assertNotNull(result)
        assertEquals(DocPos(6), result.from)
        assertEquals(listOf("हिंदी"), result.options.map { it.label })
    }

    // Expected values from @codemirror/autocomplete 6.20.1 (#407). Kotlin/Wasm's Regex engine
    // classifies astral code points by their low 16 bits, so U+1F914 read as a letter there.
    @Test
    fun completeAnyWordDoesNotTreatEmojiAsAWord() {
        val doc = "\ud83e\udd14\ud83e\udd14 bar \ud83e\udd14"
        assertNull(completeAnyWord(context(doc)))
        val result = completeAnyWord(context(doc, explicit = true))
        assertNotNull(result)
        assertEquals(DocPos(11), result.from)
        assertEquals(listOf("bar"), result.options.map { it.label })
        val validFor = assertNotNull(result.validFor)
        assertFalse(validFor.matches("\ud83e\udd14"))
        assertFalse(validFor.matches("ab\ud83e\udd14"))
    }

    @Test
    fun completeAnyWordSplitsWordsAtAnEmoji() {
        val result = completeAnyWord(context("a\ud83e\udd14b c"))
        assertNotNull(result)
        assertEquals(DocPos(5), result.from)
        assertEquals(listOf("a", "b"), result.options.map { it.label })
    }

    @Test
    fun completeAnyWordTreatsAstralLettersAsWordCharacters() {
        // U+20000 U+20001, CJK Extension B.
        val result = completeAnyWord(context("\ud840\udc00\ud840\udc01 x \ud840\udc00"))
        assertNotNull(result)
        assertEquals(DocPos(7), result.from)
        assertEquals(listOf("\ud840\udc00\ud840\udc01", "x"), result.options.map { it.label })
    }

    @Test
    fun completeAnyWordTreatsEnclosedLettersAsWordCharacters() {
        // U+1F170 U+1F171, negative squared A and B: Alphabetic but category So (#408).
        val result = completeAnyWord(context("\ud83c\udd70\ud83c\udd71 x \ud83c\udd70"))
        assertNotNull(result)
        assertEquals(DocPos(7), result.from)
        assertEquals(listOf("\ud83c\udd70\ud83c\udd71", "x"), result.options.map { it.label })
    }

    @Test
    fun completeAnyWordSplitsWordsAtANonAlphabeticSquaredSymbol() {
        // U+1F14A squared HV sits inside the enclosed-letter block but is not Alphabetic.
        val result = completeAnyWord(context("a\ud83c\udd4ab c"))
        assertNotNull(result)
        assertEquals(DocPos(5), result.from)
        assertEquals(listOf("a", "b"), result.options.map { it.label })
    }

    @Test
    fun completeAnyWordKeepsThaiVowelsInsideAWord() {
        val result =
            completeAnyWord(context("\u0e20\u0e32\u0e29\u0e32\u0e44\u0e17\u0e22 \u0e20\u0e32"))
        assertNotNull(result)
        assertEquals(DocPos(8), result.from)
        assertEquals(
            listOf("\u0e20\u0e32\u0e29\u0e32\u0e44\u0e17\u0e22"),
            result.options.map { it.label }
        )
    }

    @Test
    fun completeAnyWordScansEveryLineWithTheLanguageWordChars() {
        val doc = "foo-bar a&&b\n\$x baz\n\ud83e\udd14\ud840\udc00y fo-"
        val s = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.EditorSelectionSpec(
                    EditorSelection.single(DocPos(doc.length))
                ),
                extensions = languageData.of { _, _, _ -> listOf(mapOf("wordChars" to "-&\$")) }
            )
        )
        val result = completeAnyWord(CompletionContext(s, DocPos(doc.length), false))
        assertNotNull(result)
        assertEquals(DocPos(26), result.from)
        assertEquals(
            listOf("foo-bar", "a&&b", "\$x", "baz", "\ud840\udc00y"),
            result.options.map { it.label }
        )
        val validFor = assertNotNull(result.validFor)
        assertTrue(validFor.matches("fo-x"))
        assertTrue(validFor.matches("a&b"))
        assertFalse(validFor.matches("a b"))
    }
}
