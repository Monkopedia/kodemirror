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
package com.monkopedia.kodemirror.view

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.asInsert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The hidden field's edits as the input method produces them, applied with [applyHiddenInput]
 * (#294). Each step feeds the field value an input method would leave next, the way Compose's
 * edit processor hands it to `onValueChange`.
 */
class HiddenInputTest {

    private class Driver(doc: String, anchor: Int, head: Int = anchor) {
        var state: EditorState = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(anchor), DocPos(head))
            )
        )
        var buffer = HiddenInput.Empty
        val userEvents = mutableListOf<String?>()

        fun input(text: String, cursor: Int = text.length, composition: TextRange? = null) {
            val value = TextFieldValue(text, TextRange(cursor), composition)
            val edit = applyHiddenInput(state, buffer, value)
            edit.transaction?.let {
                val tr = state.update(it)
                userEvents += tr.annotation(Transaction.userEvent)
                state = tr.state
            }
            buffer = edit.next
        }

        /** Input method composing [text] as the whole field. */
        fun compose(text: String) = input(text, composition = TextRange(0, text.length))

        val doc get() = state.doc.toString()
        val cursor get() = state.selection.main.head.value
    }

    @Test
    fun pinyinComposition_replacesItsOwnRange() {
        val d = Driver("ab", 1)
        d.compose("n")
        d.compose("ni")
        d.compose("nih")
        assertEquals("anihb", d.doc)
        assertEquals(4, d.cursor)
        d.input("你好")
        assertEquals("a你好b", d.doc)
        assertEquals(3, d.cursor)
        assertFalse(d.buffer.composing)
        assertEquals("", d.buffer.value.text)
    }

    @Test
    fun compositionUserEvents_matchCodeMirror() {
        val d = Driver("", 0)
        d.compose("n")
        d.compose("ni")
        d.input("你")
        assertEquals<List<String?>>(
            listOf("input.type.compose.start", "input.type.compose", "input.type.compose"),
            d.userEvents
        )
        d.input("x")
        assertEquals("input.type", d.userEvents.last())
    }

    @Test
    fun compositionReplacesSelection() {
        val d = Driver("Hello World", 0, 5)
        d.compose("h")
        assertEquals("h World", d.doc)
        d.input("Hi")
        assertEquals("Hi World", d.doc)
    }

    @Test
    fun cancelledComposition_removesMirroredText() {
        val d = Driver("ab", 1)
        d.compose("ni")
        d.input("")
        assertEquals("ab", d.doc)
        assertEquals(1, d.cursor)
    }

    @Test
    fun finishingComposition_leavesTextAndEmptiesBuffer() {
        val d = Driver("", 0)
        d.compose("hello")
        d.input("hello")
        assertEquals("hello", d.doc)
        assertEquals(5, d.cursor)
        assertEquals("", d.buffer.value.text)
    }

    @Test
    fun emojiCommit_insertsWholeSurrogatePair() {
        val d = Driver("ab", 1)
        d.input("😀")
        assertEquals("a😀b", d.doc)
        assertEquals(3, d.cursor)
    }

    @Test
    fun emojiReplacingEmojiWithSameHighSurrogate_doesNotSplitPair() {
        // 😀 = D83D DE00, 😁 = D83D DE01: equal high surrogates.
        val d = Driver("", 0)
        d.compose("😀")
        d.compose("😁")
        assertEquals("😁", d.doc)
        d.compose("x😁")
        d.compose("y😁")
        assertEquals("y😁", d.doc)
    }

    @Test
    fun surrogateSafeDiff() {
        assertEquals(0, commonPrefix("😀", "😁"))
        assertEquals(2, commonPrefix("😀a", "😀b"))
        // Equal low surrogate, different high: 😀 (D83D DE00) vs 🨀 (D83E DE00).
        assertEquals(0, commonSuffix("😀", "🨀", 0))
        assertEquals(2, commonSuffix("a😀", "b😀", 0))
    }

    @Test
    fun mixedChineseEmojiAndLatin() {
        val d = Driver("", 0)
        d.compose("zh")
        d.input("中")
        d.input("😀")
        d.input("a")
        d.compose("w")
        d.compose("we")
        d.input("文")
        assertEquals("中😀a文", d.doc)
        assertEquals(5, d.cursor)
    }

    @Test
    fun compositionCursorInsideComposingText_isMirrored() {
        val d = Driver("xy", 1)
        d.input("abc", cursor = 1, composition = TextRange(0, 3))
        assertEquals("xabcy", d.doc)
        assertEquals(2, d.cursor)
    }

    @Test
    fun documentChangedUnderComposition_startsAfresh() {
        val d = Driver("ab", 1)
        d.compose("ni")
        // Something else edits the document under the composition.
        d.state = d.state.update(
            TransactionSpec(changes = ChangeSpec.Single(DocPos(0), DocPos(0), "Z".asInsert()))
        ).state
        assertFalse(d.buffer.mirrors(d.state))
    }

    @Test
    fun selectionMovedUnderComposition_isDetected() {
        val d = Driver("ab", 1)
        d.compose("ni")
        assertTrue(d.buffer.mirrors(d.state))
        d.state = d.state.update(TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos(0))))
            .state
        assertFalse(d.buffer.mirrors(d.state))
    }

    @Test
    fun committedControlCharacters_areDropped() {
        val d = Driver("Hello", 0, 5)
        d.input("\t")
        assertEquals("Hello", d.doc)
        val tab = applyHiddenInput(d.state, HiddenInput.Empty, TextFieldValue("\t"))
        assertNull(tab.transaction)
    }
}
