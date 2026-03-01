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

import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloseBracketsTest {

    private fun createState(doc: String, cursor: Int = 0): EditorState = EditorState.create(
        EditorStateConfig(
            doc = doc.asDoc(),
            selection = SelectionSpec.CursorSpec(cursor)
        )
    )

    private fun createView(doc: String, cursor: Int = 0): EditorView =
        EditorView(createState(doc, cursor))

    @Test
    fun insertBracketInsertsPair() {
        val state = createState("", cursor = 0)
        val spec = insertBracket(state, "(")
        val tr = state.update(spec)
        assertEquals("()", tr.state.doc.toString())
        assertEquals(1, tr.state.selection.main.head)
    }

    @Test
    fun insertBracketForQuotes() {
        val state = createState("", cursor = 0)
        val spec = insertBracket(state, "\"")
        val tr = state.update(spec)
        assertEquals("\"\"", tr.state.doc.toString())
        assertEquals(1, tr.state.selection.main.head)
    }

    @Test
    fun insertBracketForSquareBrackets() {
        val state = createState("", cursor = 0)
        val spec = insertBracket(state, "[")
        val tr = state.update(spec)
        assertEquals("[]", tr.state.doc.toString())
        assertEquals(1, tr.state.selection.main.head)
    }

    @Test
    fun insertBracketForCurlyBraces() {
        val state = createState("", cursor = 0)
        val spec = insertBracket(state, "{")
        val tr = state.update(spec)
        assertEquals("{}", tr.state.doc.toString())
        assertEquals(1, tr.state.selection.main.head)
    }

    @Test
    fun deleteBracketPairDeletesBoth() {
        val view = createView("()", cursor = 1)
        assertTrue(deleteBracketPair(view))
        assertEquals("", view.state.doc.toString())
        assertEquals(0, view.state.selection.main.head)
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
        assertEquals(1, tr.state.selection.main.head)
    }
}
