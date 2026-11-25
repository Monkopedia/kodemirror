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
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asInsert
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.select
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred

/**
 * An async source still in flight while the user edits (#153, #421, #422). As upstream
 * `@codemirror/autocomplete` 6.20.1's plugin, edits made while the query runs are
 * replayed onto its result when it arrives: typing keeps it (mapping its range),
 * anything else that moves the cursor or changes the doc ends the query, and Escape
 * closes the session so the late result is dropped. Expected values are upstream's on
 * the same inputs (probe rows R1–R6 in the #422 PR).
 */
class AsyncCompletionRaceTest {

    private val testOptions = listOf(
        Completion(label = "apple"),
        Completion(label = "banana")
    )

    /** A session at "ab|" whose async source waits on [gate], then returns a lowercase-word result. */
    private fun raceView(gate: CompletableDeferred<Unit>): EditorSession = sourceView("ab", 2) {
        gate.await()
        CompletionResult(from = DocPos(1), options = testOptions, validFor = Regex("[a-z]*"))
    }

    @Test
    fun stillApplicableResultOpensPopup() {
        val gate = CompletableDeferred<Unit>()
        val view = raceView(gate)
        startCompletion(view)
        // Control: nothing changed while the request was in flight.
        gate.complete(Unit)
        assertEquals("active", completionStatus(view.state))
    }

    @Test
    fun cursorMovedBeforeFromDropsResult() {
        val gate = CompletableDeferred<Unit>()
        val view = raceView(gate)
        startCompletion(view)
        view.select(DocPos.ZERO)
        gate.complete(Unit)
        assertNull(completionStatus(view.state))
    }

    @Test
    fun typedTokenNoLongerMatchesValidForDropsResult() {
        val gate = CompletableDeferred<Unit>()
        val view = raceView(gate)
        startCompletion(view)
        // Typing a digit breaks the lowercase-word validFor once replayed, so the
        // source is asked again; its answer for "ab1" matches nothing.
        view.typeText("1")
        gate.complete(Unit)
        assertNull(completionStatus(view.state))
    }

    @Test
    fun nonEmptySelectionDropsResult() {
        val gate = CompletableDeferred<Unit>()
        val view = raceView(gate)
        startCompletion(view)
        view.select(anchor = DocPos.ZERO, head = DocPos(2))
        gate.complete(Unit)
        assertNull(completionStatus(view.state))
    }

    /** A session at [doc] with the cursor at [cursor] and the one async [source]. */
    private fun sourceView(doc: String, cursor: Int, source: SuspendCompletionSource) =
        completionSession(doc, cursor, CompletionConfig(asyncOverride = listOf(source)))

    /**
     * A source that waits on [gate], then completes the word before the query's cursor
     * with a range ending at that cursor, as an LSP edit range does.
     */
    private fun wordUpToCursor(
        gate: CompletableDeferred<Unit>,
        vararg labels: String
    ): SuspendCompletionSource = { ctx ->
        gate.await()
        CompletionResult(
            from = ctx.matchBefore(Regex("\\w+"))?.from ?: ctx.pos,
            to = ctx.pos,
            options = labels.map { Completion(label = it) },
            validFor = Regex("\\w*")
        )
    }

    @Test
    fun aLateResultsRangeFollowsTheEditsMadeWhileItRan() {
        // Queried at "x.ba|" with `to` 4, landing after "r" is typed. Upstream replays
        // the edit onto from..to (2..5): "bar" only.
        val gate = CompletableDeferred<Unit>()
        val view = sourceView("x.ba", 4, wordUpToCursor(gate, "bar", "baz"))
        startCompletion(view)
        view.typeText("r")
        gate.complete(Unit)
        assertEquals("active" to listOf("bar"), shown(view))
        acceptCompletion(view)
        assertEquals("x.bar", view.state.doc.toString())
    }

    @Test
    fun aLateResultFollowsTypingARepeatedCharacter() {
        // "x.b|b" with `to` 3, then "b" typed: the edit is at the cursor even though
        // the doc reads the same with it one character later.
        val gate = CompletableDeferred<Unit>()
        val view = sourceView("x.bb", 3, wordUpToCursor(gate, "bar", "baz", "bbb", "bb"))
        startCompletion(view)
        view.typeText("b")
        gate.complete(Unit)
        assertEquals("active" to listOf("bb", "bbb"), shown(view))
        acceptCompletion(view)
        assertEquals("x.bbb", view.state.doc.toString())
    }

    @Test
    fun anEditElsewhereRestartsTheQuery() {
        // "r" typed at the cursor, then "Q" inserted at the start by another edit,
        // which ends the query in flight. The source is still pending, so upstream
        // asks it again, at "Qx.bar|" (R5).
        val gate = CompletableDeferred<Unit>()
        val calls = mutableListOf<Int>()
        val source = wordUpToCursor(gate, "bar", "baz")
        val view = sourceView("x.ba", 4) { ctx ->
            calls += ctx.pos.value
            source(ctx)
        }
        startCompletion(view)
        view.typeText("r")
        view.dispatch(
            TransactionSpec(changes = ChangeSpec.Single(DocPos.ZERO, insert = "Q".asInsert()))
        )
        assertEquals(listOf(4, 6), calls)
        gate.complete(Unit)
        assertEquals("active" to listOf("bar"), shown(view))
        acceptCompletion(view)
        assertEquals("Qx.bar", view.state.doc.toString())
    }

    @Test
    fun anEditAtTheCursorEndsThePendingQuery() {
        // A non-typing insert at the cursor (R6): the pending source goes inactive.
        val gate = CompletableDeferred<Unit>()
        val view = sourceView("x.ba", 4, wordUpToCursor(gate, "bar", "baz"))
        startCompletion(view)
        view.dispatch(
            TransactionSpec(changes = ChangeSpec.Single(DocPos(4), insert = "Q".asInsert()))
        )
        assertNull(completionStatus(view.state))
        gate.complete(Unit)
        assertNull(completionStatus(view.state))
    }

    @Test
    fun aThrowingSourceIsNotLeftPending() {
        // The failure is logged rather than failing the scope (#430), and each trigger
        // asks the source again.
        var calls = 0
        val view = sourceView("x.", 2) {
            calls++
            error("server failed")
        }
        repeat(20) {
            startCompletion(view)
            assertNull(completionStatus(view.state))
        }
        assertEquals(20, calls)
    }

    @Test
    fun aSourceHasOneQueryInFlight() {
        // Upstream keeps a source pending while its query runs: starting again doesn't
        // query again (#422).
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val view = sourceView("x.", 2) {
            calls++
            gate.await()
            CompletionResult(from = DocPos(2), options = listOf(Completion("new")))
        }
        repeat(50) { startCompletion(view) }
        assertEquals(1, calls)
        assertEquals("pending", completionStatus(view.state))
        gate.complete(Unit)
        assertEquals("active" to listOf("new"), shown(view))
    }

    @Test
    fun closingDropsTheQueryInFlight() {
        // The first query opens the list; a re-query is still running when Escape
        // closes it, and its late result must not re-open it (#153, R4).
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val view = sourceView("x.", 2) {
            if (calls++ > 0) gate.await()
            CompletionResult(
                from = DocPos(2),
                options = listOf(Completion("bar"), Completion("baz"))
            )
        }
        startCompletion(view)
        assertEquals("active", completionStatus(view.state))
        startCompletion(view)
        assertEquals(2, calls)
        assertEquals("pending", completionStatus(view.state))
        closeCompletion(view)
        assertNull(completionStatus(view.state))
        gate.complete(Unit)
        assertNull(completionStatus(view.state))
    }
}
