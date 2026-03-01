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
package com.monkopedia.kodemirror.commands

import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorView
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandsTest {

    private fun createView(doc: String, cursor: Int = 0): EditorView {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(cursor)
            )
        )
        return EditorView(state)
    }

    private fun createViewWithSelection(doc: String, anchor: Int, head: Int): EditorView {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.EditorSelectionSpec(
                    EditorSelection.single(anchor, head)
                )
            )
        )
        return EditorView(state)
    }

    @Test
    fun testDeleteCharBackward() {
        val view = createView("hello", cursor = 3)
        deleteCharBackward(view)
        assertEquals("helo", view.state.doc.toString())
        assertEquals(2, view.state.selection.main.head)
    }

    @Test
    fun testDeleteCharBackwardAtStart() {
        val view = createView("hello", cursor = 0)
        deleteCharBackward(view)
        // Should not change anything at position 0
        assertEquals("hello", view.state.doc.toString())
    }

    @Test
    fun testDeleteCharForward() {
        val view = createView("hello", cursor = 2)
        deleteCharForward(view)
        assertEquals("helo", view.state.doc.toString())
        assertEquals(2, view.state.selection.main.head)
    }

    @Test
    fun testDeleteSelection() {
        val view = createViewWithSelection("hello world", 2, 5)
        deleteCharBackward(view)
        assertEquals("he world", view.state.doc.toString())
        assertEquals(2, view.state.selection.main.head)
    }

    @Test
    fun testInsertNewline() {
        val view = createView("hello", cursor = 3)
        insertNewline(view)
        assertEquals("hel\nlo", view.state.doc.toString())
        assertEquals(4, view.state.selection.main.head)
    }

    @Test
    fun testInsertNewlineAndIndent() {
        val view = createView("  hello", cursor = 7)
        insertNewlineAndIndent(view)
        assertEquals("  hello\n  ", view.state.doc.toString())
    }

    @Test
    fun testSelectAll() {
        val view = createView("hello world")
        selectAll(view)
        assertEquals(0, view.state.selection.main.from)
        assertEquals(11, view.state.selection.main.to)
    }

    @Test
    fun testTransposeChars() {
        val view = createView("abc", cursor = 2)
        transposeChars(view)
        assertEquals("acb", view.state.doc.toString())
        assertEquals(3, view.state.selection.main.head)
    }

    @Test
    fun testSplitLine() {
        val view = createView("hello", cursor = 3)
        splitLine(view)
        assertEquals("hel\nlo", view.state.doc.toString())
        assertEquals(3, view.state.selection.main.head)
    }

    @Test
    fun testCursorLineStart() {
        val view = createView("hello\nworld", cursor = 8)
        cursorLineStart(view)
        assertEquals(6, view.state.selection.main.head)
    }

    @Test
    fun testCursorLineEnd() {
        val view = createView("hello\nworld", cursor = 7)
        cursorLineEnd(view)
        assertEquals(11, view.state.selection.main.head)
    }

    @Test
    fun testCursorDocStart() {
        val view = createView("hello\nworld", cursor = 8)
        cursorDocStart(view)
        assertEquals(0, view.state.selection.main.head)
    }

    @Test
    fun testCursorDocEnd() {
        val view = createView("hello\nworld", cursor = 2)
        cursorDocEnd(view)
        assertEquals(11, view.state.selection.main.head)
    }

    @Test
    fun testSelectLineStart() {
        val view = createView("hello\nworld", cursor = 8)
        selectLineStart(view)
        assertEquals(8, view.state.selection.main.anchor)
        assertEquals(6, view.state.selection.main.head)
    }

    @Test
    fun testSelectLineEnd() {
        val view = createView("hello\nworld", cursor = 7)
        selectLineEnd(view)
        assertEquals(7, view.state.selection.main.anchor)
        assertEquals(11, view.state.selection.main.head)
    }

    @Test
    fun testDeleteLine() {
        val view = createView("aaa\nbbb\nccc", cursor = 5)
        deleteLine(view)
        assertEquals("aaa\nccc", view.state.doc.toString())
    }

    @Test
    fun testDeleteToLineEnd() {
        val view = createView("hello world", cursor = 5)
        deleteToLineEnd(view)
        assertEquals("hello", view.state.doc.toString())
    }

    @Test
    fun testDeleteToLineStart() {
        val view = createView("hello world", cursor = 5)
        deleteToLineStart(view)
        assertEquals(" world", view.state.doc.toString())
    }

    @Test
    fun testInsertTab() {
        val view = createView("hello", cursor = 5)
        insertTab(view)
        assertEquals("hello\t", view.state.doc.toString())
    }

    @Test
    fun testSelectLine() {
        val view = createView("aaa\nbbb\nccc", cursor = 5)
        selectLine(view)
        assertEquals(4, view.state.selection.main.from)
        assertEquals(8, view.state.selection.main.to)
    }
}
