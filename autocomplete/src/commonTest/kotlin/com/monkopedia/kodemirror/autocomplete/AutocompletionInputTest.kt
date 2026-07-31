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

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.allowMultipleSelections
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutocompletionInputTest {

    private val testOptions = listOf(
        Completion(label = "apple"),
        Completion(label = "apricot"),
        Completion(label = "banana"),
        Completion(label = "cherry")
    )

    private val testSource: CompletionSource = { ctx ->
        CompletionResult(
            from = DocPos.ZERO,
            to = ctx.pos,
            options = testOptions,
            validFor = Regex("[\\w]*")
        )
    }

    private fun createView(doc: String = "", cursor: Int = doc.length): EditorSession {
        val config = CompletionConfig(override = listOf(testSource))
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(cursor)),
                extensions = ExtensionList(
                    listOf(
                        completionConfig.of(config),
                        completionStateField
                    )
                )
            )
        )
        return EditorSession(state)
    }

    /** Simulate typing a character at the current cursor position. */
    private fun typeChar(view: EditorSession, ch: String) {
        val pos = view.state.selection.main.head
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(pos, pos, InsertContent.StringContent(ch)),
                selection = SelectionSpec.CursorSpec(pos + ch.length),
                userEvent = "input.type"
            )
        )
    }

    /** Simulate deleting the character before the cursor. */
    private fun deleteBack(view: EditorSession) {
        val pos = view.state.selection.main.head
        if (pos > DocPos.ZERO) {
            view.dispatch(
                TransactionSpec(
                    changes = ChangeSpec.Single(pos - 1, pos, InsertContent.StringContent("")),
                    selection = SelectionSpec.CursorSpec(pos - 1),
                    userEvent = "input.delete"
                )
            )
        }
    }

    @Test
    fun typingFiltersResults() {
        val view = createView()
        startCompletion(view)
        assertEquals(4, currentCompletions(view.state).size)
        // Type "b" - only "banana" should match
        typeChar(view, "b")
        val completions = currentCompletions(view.state)
        assertEquals(1, completions.size)
        assertEquals("banana", completions[0].label)
    }

    @Test
    fun typingWithNoMatchCloses() {
        val view = createView()
        startCompletion(view)
        assertEquals("active", completionStatus(view.state))
        // Type "xyz" - no match
        typeChar(view, "x")
        typeChar(view, "y")
        typeChar(view, "z")
        assertNull(completionStatus(view.state))
    }

    @Test
    fun deletionReopensIfValid() {
        val view = createView()
        startCompletion(view)
        // Type "ap" - filters to apple, apricot
        typeChar(view, "a")
        typeChar(view, "p")
        assertEquals(2, currentCompletions(view.state).size)
        // Type "p" again - only "apple"
        typeChar(view, "p")
        assertEquals(1, currentCompletions(view.state).size)
        // Delete back to "ap" - both should reappear
        deleteBack(view)
        assertEquals(2, currentCompletions(view.state).size)
    }

    @Test
    fun completionApplyInsertsText() {
        val view = createView()
        startCompletion(view)
        // Accept "apple" (index 0)
        assertTrue(acceptCompletion(view))
        assertEquals("apple", view.state.doc.toString())
    }

    @Test
    fun moveSelectionAndAccept() {
        val view = createView()
        startCompletion(view)
        // Move down to select "apricot" (index 1)
        moveCompletionSelection(forward = true)(view)
        assertTrue(acceptCompletion(view))
        assertEquals("apricot", view.state.doc.toString())
    }

    @Test
    fun currentCompletionsReturnsFiltered() {
        val view = createView()
        startCompletion(view)
        // All 4 initially
        assertEquals(4, currentCompletions(view.state).size)
        // Type "ch" - only "cherry"
        typeChar(view, "c")
        typeChar(view, "h")
        val filtered = currentCompletions(view.state)
        assertEquals(1, filtered.size)
        assertEquals("cherry", filtered[0].label)
    }

    @Test
    fun acceptAfterDocShrankDoesNotCrash() {
        // #124: the result's to-position is captured when completion is requested.
        // If the doc shrinks before accept (backspace while the popup is open), that
        // stale position points past the doc end and must be clamped — previously it
        // crashed dispatch with "Invalid position DocPos(N) in document of length …".
        val view = createView("apple", cursor = 5)
        startCompletion(view) // result.to captured at pos 5
        deleteBack(view) // doc -> "appl" (len 4); stored to=5 is now past the end
        assertEquals("active", completionStatus(view.state)) // still open, filtered to "apple"
        // Must not throw; applies the completion over the clamped range.
        assertTrue(acceptCompletion(view))
        assertEquals("apple", view.state.doc.toString())
    }

    /**
     * A completion source that replaces the two characters before the (primary)
     * cursor — i.e. a typed two-char token — so multi-cursor apply has a concrete
     * prefix span to match against the other cursors.
     */
    private val twoCharPrefixSource: CompletionSource = { ctx ->
        CompletionResult(
            from = ctx.pos - 2,
            to = ctx.pos,
            options = listOf(Completion(label = "apple")),
            validFor = Regex("[\\w]*")
        )
    }

    private fun createMultiCursorView(doc: String, cursors: List<Int>, main: Int): EditorSession {
        val config = CompletionConfig(override = listOf(twoCharPrefixSource))
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.EditorSelectionSpec(
                    EditorSelection.create(
                        cursors.map { EditorSelection.cursor(DocPos(it)) },
                        main
                    )
                ),
                extensions = ExtensionList(
                    listOf(
                        allowMultipleSelections.of(true),
                        completionConfig.of(config),
                        completionStateField
                    )
                )
            )
        )
        return EditorSession(state)
    }

    @Test
    fun completesForMultipleCursors() {
        // Two cursors each after a typed "ap" prefix; both should be completed.
        val view = createMultiCursorView("ap ap", cursors = listOf(2, 5), main = 1)
        startCompletion(view)
        assertEquals("active", completionStatus(view.state))
        assertTrue(acceptCompletion(view))
        assertEquals("apple apple", view.state.doc.toString())
        assertEquals(2, view.state.selection.ranges.size)
    }

    @Test
    fun doesNotCompleteForMultipleCursorsIfPrefixDoesNotMatch() {
        // Primary cursor after "ap" (matches "apple"); secondary cursor after "xy"
        // (different token) must be left untouched — only the matching one completes.
        val view = createMultiCursorView("xy ap", cursors = listOf(2, 5), main = 1)
        startCompletion(view)
        assertEquals("active", completionStatus(view.state))
        assertTrue(acceptCompletion(view))
        assertEquals("xy apple", view.state.doc.toString())
        // The non-matching cursor is preserved as a still-separate range.
        assertEquals(2, view.state.selection.ranges.size)
    }
}
