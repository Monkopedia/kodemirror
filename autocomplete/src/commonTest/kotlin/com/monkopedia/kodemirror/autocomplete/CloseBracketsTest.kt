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

import com.monkopedia.kodemirror.state.ChangeByRangeResult
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloseBracketsTest {

    private fun createState(doc: String, cursor: Int = 0): EditorState = EditorState.create(
        EditorStateConfig(
            doc = doc.asDoc(),
            selection = SelectionSpec.CursorSpec(DocPos(cursor))
        )
    )

    private fun createView(doc: String, cursor: Int = 0): EditorSession =
        EditorSession(createState(doc, cursor))

    @Test
    fun insertBracketInsertsPair() {
        val state = createState("", cursor = 0)
        val spec = insertBracket(state, "(")
        val tr = state.update(spec)
        assertEquals("()", tr.state.doc.toString())
        assertEquals(DocPos(1), tr.state.selection.main.head)
    }

    @Test
    fun insertBracketForQuotes() {
        val state = createState("", cursor = 0)
        val spec = insertBracket(state, "\"")
        val tr = state.update(spec)
        assertEquals("\"\"", tr.state.doc.toString())
        assertEquals(DocPos(1), tr.state.selection.main.head)
    }

    @Test
    fun insertBracketForSquareBrackets() {
        val state = createState("", cursor = 0)
        val spec = insertBracket(state, "[")
        val tr = state.update(spec)
        assertEquals("[]", tr.state.doc.toString())
        assertEquals(DocPos(1), tr.state.selection.main.head)
    }

    @Test
    fun insertBracketForCurlyBraces() {
        val state = createState("", cursor = 0)
        val spec = insertBracket(state, "{")
        val tr = state.update(spec)
        assertEquals("{}", tr.state.doc.toString())
        assertEquals(DocPos(1), tr.state.selection.main.head)
    }

    @Test
    fun deleteBracketPairDeletesBoth() {
        val view = createView("()", cursor = 1)
        assertTrue(deleteBracketPair(view))
        assertEquals("", view.state.doc.toString())
        assertEquals(DocPos.ZERO, view.state.selection.main.head)
    }

    @Test
    fun deleteBracketPairReturnsFalseWhenNotBetweenPair() {
        val view = createView("ab", cursor = 1)
        assertFalse(deleteBracketPair(view))
        assertEquals("ab", view.state.doc.toString())
    }

    @Test
    fun deleteBracketPairAtBoundaries() {
        val view1 = createView("hello", cursor = 0)
        assertFalse(deleteBracketPair(view1))

        val view2 = createView("hello", cursor = 5)
        assertFalse(deleteBracketPair(view2))
    }

    @Test
    fun insertBracketUnknownChar() {
        val state = createState("", cursor = 0)
        val spec = insertBracket(state, "x")
        val tr = state.update(spec)
        assertEquals("x", tr.state.doc.toString())
        assertEquals(DocPos(1), tr.state.selection.main.head)
    }

    // ── Against @codemirror/autocomplete 6.20.1 on the same inputs (#383) ──

    private fun multi(vararg ranges: Pair<Int, Int>) = SelectionSpec.EditorSelectionSpec(
        EditorSelection.create(ranges.map { (a, h) -> EditorSelection.range(DocPos(a), DocPos(h)) })
    )

    private fun bracketState(doc: String, sel: SelectionSpec): EditorState = EditorState.create(
        EditorStateConfig(
            doc = doc.asDoc(),
            selection = sel,
            extensions = ExtensionList(
                listOf(EditorState.allowMultipleSelections.of(true), closeBrackets())
            )
        )
    )

    /** Type [ch] the way the editor does: replace each selection range, as `input.type`. */
    private fun type(state: EditorState, ch: String): EditorState = state.update(
        state.changeByRange { r ->
            ChangeByRangeResult(
                changes = ChangeSpec.Single(r.from, r.to, InsertContent.StringContent(ch)),
                range = EditorSelection.cursor(r.from + ch.length)
            )
        }.copy(userEvent = "input.type")
    ).state

    private fun EditorState.selString() = selection.ranges.joinToString(",") {
        if (it.empty) "${it.head.value}" else "${it.anchor.value}-${it.head.value}"
    }

    @Test
    fun quoteAfterAWordIsNotDoubled() {
        val s = type(bracketState("don", multi(3 to 3)), "'")
        assertEquals("don'", s.doc.toString())
        assertEquals("4", s.selString())
    }

    @Test
    fun quoteAtStartIsClosed() {
        val s = type(bracketState("", multi(0 to 0)), "'")
        assertEquals("''", s.doc.toString())
        assertEquals("1", s.selString())
    }

    @Test
    fun openBracketWrapsTheSelection() {
        val s = type(bracketState("foo", multi(0 to 3)), "(")
        assertEquals("(foo)", s.doc.toString())
        assertEquals("1-4", s.selString())
    }

    @Test
    fun closingBracketNotInsertedByTheExtensionIsTyped() {
        val s = type(bracketState("f()", multi(2 to 2)), ")")
        assertEquals("f())", s.doc.toString())
        assertEquals("3", s.selString())
    }

    @Test
    fun closingBracketInsertedByTheExtensionIsOvertyped() {
        val opened = type(bracketState("f", multi(1 to 1)), "(")
        assertEquals("f()", opened.doc.toString())
        val s = type(opened, ")")
        assertEquals("f()", s.doc.toString())
        assertEquals("3", s.selString())
    }

    @Test
    fun everyCursorGetsAClosingBracket() {
        val s = type(bracketState("a b", multi(1 to 1, 3 to 3)), "(")
        assertEquals("a() b()", s.doc.toString())
        assertEquals("2,6", s.selString())
    }

    @Test
    fun insertBracketLeavesQuoteAfterWordUnpaired() {
        val state = createState("don", cursor = 3)
        assertEquals("don'", state.update(insertBracket(state, "'")).state.doc.toString())
    }

    @Test
    fun insertBracketBeforeAWordDoesNotClose() {
        val state = createState("foo", cursor = 0)
        assertEquals("(foo", state.update(insertBracket(state, "(")).state.doc.toString())
    }

    @Test
    fun insertBracketDoesNotSkipAClosingBracketItDidNotInsert() {
        val state = createState("f()", cursor = 2)
        assertEquals("f())", state.update(insertBracket(state, ")")).state.doc.toString())
    }

    @Test
    fun deleteBracketPairDeletesEveryCursorsPair() {
        val view = EditorSession(bracketState("() ()", multi(1 to 1, 4 to 4)))
        assertTrue(deleteBracketPair(view))
        assertEquals(" ", view.state.doc.toString())
        assertEquals("0,1", view.state.selString())
    }

    @Test
    fun deleteBracketPairIgnoresANonEmptySelection() {
        val view = EditorSession(bracketState("()", multi(0 to 1)))
        assertFalse(deleteBracketPair(view))
        assertEquals("()", view.state.doc.toString())
    }
}
