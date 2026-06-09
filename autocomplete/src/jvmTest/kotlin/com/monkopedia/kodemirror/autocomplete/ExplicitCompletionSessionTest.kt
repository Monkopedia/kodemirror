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
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Ports the upstream `@codemirror/autocomplete` explicit-session lifecycle cases
 * (#139). An explicit session (Ctrl-Space / [startCompletion]) tracks the
 * [CompletionState.explicit] flag and follows distinct backspacing rules from an
 * implicit (typed) session — these assert the open/close transitions and the
 * selection reset across the lifecycle.
 *
 * Assertions focus on the lifecycle (session open vs closed, the full option set
 * restored on backspace, selection reset) rather than the exact filtered option
 * strings: kodemirror's fuzzy filter (#111) differs from upstream's (e.g. it does
 * not restrict single-letter queries to prefix matches), which is out of scope here.
 */
class ExplicitCompletionSessionTest {

    /**
     * Mirrors upstream's `from(list)` test source: completes the word before the
     * cursor (or, when explicit, at the bare cursor) with a fixed option list and a
     * word-prefix `validFor`.
     */
    private fun fromSource(list: String): CompletionSource = { ctx ->
        val word = ctx.matchBefore(Regex("[\\w]+$"))
        if (word == null && !ctx.explicit) {
            null
        } else {
            CompletionResult(
                from = word?.from ?: ctx.pos,
                options = list.split(" ").map { Completion(label = it) },
                validFor = Regex("[\\w]*")
            )
        }
    }

    private fun createView(source: CompletionSource, doc: String = "", cursor: Int = doc.length):
        EditorSession {
        val config = CompletionConfig(override = listOf(source))
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

    /** Simulate a real backspace (the `delete.backward` user event). */
    private fun deleteBack(view: EditorSession) {
        val pos = view.state.selection.main.head
        if (pos > DocPos.ZERO) {
            view.dispatch(
                TransactionSpec(
                    changes = ChangeSpec.Single(pos - 1, pos, InsertContent.StringContent("")),
                    selection = SelectionSpec.CursorSpec(pos - 1),
                    userEvent = "delete.backward"
                )
            )
        }
    }

    /** Open an implicit (typed) session directly, bypassing the headless ViewPlugin. */
    private fun openImplicit(view: EditorSession, result: CompletionResult) {
        view.dispatch(
            TransactionSpec(
                effects = listOf(startCompletionEffect.of(StartCompletion(listOf(result), false)))
            )
        )
    }

    @Test
    fun canBackspaceOutEntireWordWhenExplicit() {
        val view = createView(fromSource("one two"))
        startCompletion(view)
        assertEquals("active", completionStatus(view.state))
        assertEquals(2, currentCompletions(view.state).size)
        // Type a char to narrow, then backspace the whole word out. An explicit
        // session stays open and re-shows the full option set on the empty query.
        typeChar(view, "o")
        assertEquals("active", completionStatus(view.state))
        deleteBack(view)
        assertEquals("active", completionStatus(view.state))
        assertEquals(2, currentCompletions(view.state).size)
    }

    @Test
    fun stopsExplicitCompletionWhenBackspacingPastStart() {
        // Word "o" at offset 4 (after "foo."), so the session's `from` is 4.
        val view = createView(fromSource("one two"), doc = "foo.o", cursor = 5)
        startCompletion(view)
        assertEquals("active", completionStatus(view.state))
        // Backspace to the empty query (cursor at `from`) — explicit stays open.
        deleteBack(view)
        assertEquals("active", completionStatus(view.state))
        assertEquals(2, currentCompletions(view.state).size)
        // Backspace once more, past `from` — the session closes.
        deleteBack(view)
        assertNull(completionStatus(view.state))
    }

    @Test
    fun stopsExplicitCompletionOnNonSpanningInput() {
        val view = createView(fromSource("one two"))
        startCompletion(view)
        typeChar(view, "o")
        assertEquals("active", completionStatus(view.state))
        // A space does not span the word `validFor` — the session closes.
        typeChar(view, " ")
        assertNull(completionStatus(view.state))
    }

    @Test
    fun stopsExplicitCompletionsForNonMatchingInput() {
        val view = createView(fromSource("one"))
        startCompletion(view)
        assertEquals("active", completionStatus(view.state))
        // "x" still spans the word `validFor`, but no option matches it — close.
        typeChar(view, "x")
        assertNull(completionStatus(view.state))
    }

    @Test
    fun resetsSelectionAfterRefinement() {
        val view = createView(fromSource("print primitive proxy"), doc = "p", cursor = 1)
        startCompletion(view)
        assertEquals(3, currentCompletions(view.state).size)
        // Move the selection off the top.
        moveCompletionSelection(forward = true)(view)
        moveCompletionSelection(forward = true)(view)
        assertEquals(2, selectedCompletionIndex(view.state))
        // Refine: the option set changes and the selection resets to the top.
        typeChar(view, "r")
        typeChar(view, "i")
        assertEquals(2, currentCompletions(view.state).size) // proxy dropped
        assertEquals(0, selectedCompletionIndex(view.state))
    }

    @Test
    fun implicitSessionClosesWhenBackspacingToStart() {
        // Contrast with the explicit case: an implicit session closes as soon as the
        // typed span is fully removed (cursor back AT `from`), rather than re-showing
        // everything. Mirrors upstream's `limit = from + (explicit ? 0 : 1)`.
        val view = createView(fromSource("one two"), doc = "o", cursor = 1)
        val result = CompletionResult(
            from = DocPos.ZERO,
            options = listOf(Completion(label = "one"), Completion(label = "two")),
            validFor = Regex("[\\w]*")
        )
        openImplicit(view, result)
        assertEquals("active", completionStatus(view.state))
        deleteBack(view) // cursor back to offset 0 == from
        assertNull(completionStatus(view.state))
    }
}
