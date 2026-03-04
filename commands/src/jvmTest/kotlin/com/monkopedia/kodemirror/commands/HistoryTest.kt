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

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorView
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryTest {

    private fun createView(doc: String, cursor: Int = 0): EditorView {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(cursor),
                extensions = history()
            )
        )
        return EditorView(state)
    }

    @Test
    fun testUndoSingleChange() {
        val view = createView("hello")
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    5,
                    5,
                    InsertContent.StringContent(" world")
                ),
                annotations = listOf(Transaction.addToHistory.of(true))
            )
        )
        assertEquals("hello world", view.state.doc.toString())
        assertEquals(1, undoDepth(view.state))

        undo(view)
        assertEquals("hello", view.state.doc.toString())
        assertEquals(0, undoDepth(view.state))
        assertEquals(1, redoDepth(view.state))
    }

    @Test
    fun testRedoAfterUndo() {
        val view = createView("hello")
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    5,
                    5,
                    InsertContent.StringContent(" world")
                ),
                annotations = listOf(Transaction.addToHistory.of(true))
            )
        )
        undo(view)
        assertEquals("hello", view.state.doc.toString())

        redo(view)
        assertEquals("hello world", view.state.doc.toString())
    }

    @Test
    fun testUndoOnEmptyHistoryReturnsFalse() {
        val view = createView("hello")
        val result = undo(view)
        assertEquals(false, result)
    }

    @Test
    fun testRedoOnEmptyHistoryReturnsFalse() {
        val view = createView("hello")
        val result = redo(view)
        assertEquals(false, result)
    }

    @Test
    fun testMultipleUndos() {
        val view = createView("a")
        // First change
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    1,
                    1,
                    InsertContent.StringContent("b")
                ),
                annotations = listOf(Transaction.addToHistory.of(true)),
                userEvent = "input.type.first"
            )
        )
        // Isolate to force a new undo group
        view.dispatch(
            TransactionSpec(
                annotations = listOf(isolateHistory.of("full"))
            )
        )
        // Second change
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    2,
                    2,
                    InsertContent.StringContent("c")
                ),
                annotations = listOf(Transaction.addToHistory.of(true)),
                userEvent = "input.type"
            )
        )
        assertEquals("abc", view.state.doc.toString())
        assertEquals(2, undoDepth(view.state))

        undo(view)
        assertEquals("ab", view.state.doc.toString())

        undo(view)
        assertEquals("a", view.state.doc.toString())
    }

    @Test
    fun testUndoDepthAndRedoDepth() {
        val view = createView("test")
        assertEquals(0, undoDepth(view.state))
        assertEquals(0, redoDepth(view.state))

        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    4,
                    4,
                    InsertContent.StringContent("!")
                ),
                annotations = listOf(Transaction.addToHistory.of(true))
            )
        )
        assertEquals(1, undoDepth(view.state))
        assertEquals(0, redoDepth(view.state))
    }

    @Test
    fun testNewChangeAfterUndoClearsRedoStack() {
        val view = createView("hello")
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    5,
                    5,
                    InsertContent.StringContent(" world")
                ),
                annotations = listOf(Transaction.addToHistory.of(true))
            )
        )
        undo(view)
        assertEquals(1, redoDepth(view.state))

        // Make a new change — should clear redo stack
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    5,
                    5,
                    InsertContent.StringContent("!")
                ),
                annotations = listOf(Transaction.addToHistory.of(true))
            )
        )
        assertEquals(0, redoDepth(view.state))
    }
}
