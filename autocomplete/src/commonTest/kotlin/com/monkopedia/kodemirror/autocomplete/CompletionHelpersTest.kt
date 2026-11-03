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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
}
