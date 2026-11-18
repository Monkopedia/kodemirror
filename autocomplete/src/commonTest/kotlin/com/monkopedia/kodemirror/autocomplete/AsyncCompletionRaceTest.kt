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
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.asInsert
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.insertAt
import com.monkopedia.kodemirror.view.select
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Regression tests for the async LSP completion re-open race (#153): a suspend
 * source still in flight when the user moves the cursor, changes the typed token,
 * or closes the popup used to dispatch its result unconditionally and re-open the
 * popup, demanding an extra Escape. The result must now be dropped when it no
 * longer applies to the live state.
 */
class AsyncCompletionRaceTest {

    private val testOptions = listOf(
        Completion(label = "apple"),
        Completion(label = "banana")
    )

    /**
     * Build a session whose single async source suspends on [gate] before
     * returning a result anchored at [DocPos(1)] with a lowercase-word validFor.
     * [Dispatchers.Unconfined] runs the launched coroutine synchronously up to the
     * `gate.await()` suspension, then resumes it inline when the test completes the
     * gate — so the staleness check is observed deterministically.
     */
    private fun raceView(gate: CompletableDeferred<Unit>): EditorSession {
        val source: SuspendCompletionSource = {
            gate.await()
            CompletionResult(
                from = DocPos(1),
                options = testOptions,
                validFor = Regex("[a-z]*")
            )
        }
        val config = CompletionConfig(asyncOverride = listOf(source))
        val state = EditorState.create(
            EditorStateConfig(
                doc = "ab".asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(2)),
                extensions = ExtensionList(
                    listOf(
                        completionConfig.of(config),
                        completionStateField
                    )
                )
            )
        )
        return UnconfinedSession(state)
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
        // Cursor moved before the result's `from` (1) while the request was pending.
        view.select(DocPos.ZERO)
        gate.complete(Unit)
        assertNull(completionStatus(view.state))
    }

    @Test
    fun typedTokenNoLongerMatchesValidForDropsResult() {
        val gate = CompletableDeferred<Unit>()
        val view = raceView(gate)
        startCompletion(view)
        // Typing a digit breaks the lowercase-word validFor over from..head.
        view.insertAt(DocPos(2), "1")
        gate.complete(Unit)
        assertNull(completionStatus(view.state))
    }

    @Test
    fun nonEmptySelectionDropsResult() {
        val gate = CompletableDeferred<Unit>()
        val view = raceView(gate)
        startCompletion(view)
        // A range selection means the cursor context no longer applies.
        view.select(anchor = DocPos.ZERO, head = DocPos(2))
        gate.complete(Unit)
        assertNull(completionStatus(view.state))
    }

    /**
     * A session at [doc] with the cursor at [cursor], whose async source suspends on
     * [gate] before returning [result] (#421).
     */
    private fun lateView(
        doc: String,
        cursor: Int,
        gate: CompletableDeferred<Unit>,
        result: CompletionResult
    ): EditorSession = sourceView(doc, cursor) {
        gate.await()
        result
    }

    /** A session at [doc] with the cursor at [cursor] and the one async [source]. */
    private fun sourceView(
        doc: String,
        cursor: Int,
        source: SuspendCompletionSource
    ): EditorSession = UnconfinedSession(
        EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(cursor)),
                extensions = ExtensionList(
                    listOf(
                        completionConfig.of(CompletionConfig(asyncOverride = listOf(source))),
                        completionStateField
                    )
                )
            )
        )
    )

    private fun lateResult(from: Int, to: Int, vararg labels: String) = CompletionResult(
        from = DocPos(from),
        to = DocPos(to),
        options = labels.map { Completion(label = it) },
        validFor = Regex("\\w*")
    )

    @Test
    fun aLateResultsRangeFollowsTheEditsMadeWhileItRan() {
        // Queried at "x.ba|" with `to` 4 (an LSP edit range), landing after "r" is
        // typed. Upstream replays the edit onto from..to (2..5): "bar" only.
        val gate = CompletableDeferred<Unit>()
        val view = lateView("x.ba", 4, gate, lateResult(2, 4, "bar", "baz"))
        startCompletion(view)
        view.insertAt(DocPos(4), "r")
        gate.complete(Unit)
        assertEquals(listOf("bar"), currentCompletions(view.state).map { it.label })
        acceptCompletion(view)
        assertEquals("x.bar", view.state.doc.toString())
    }

    @Test
    fun aLateResultFollowsTypingARepeatedCharacter() {
        // "x.b|b" with `to` 3, then "b" typed: the edit is at the cursor even though
        // the doc reads the same with it one character later.
        val gate = CompletableDeferred<Unit>()
        val view = lateView("x.bb", 3, gate, lateResult(2, 3, "bar", "baz", "bbb", "bb"))
        startCompletion(view)
        view.insertAt(DocPos(3), "b")
        gate.complete(Unit)
        assertEquals(listOf("bb", "bbb"), currentCompletions(view.state).map { it.label })
        acceptCompletion(view)
        assertEquals("x.bbb", view.state.doc.toString())
    }

    @Test
    fun aLateResultFollowsEditsAtTwoPlaces() {
        // "r" typed at the cursor, then "Q" inserted at the start by another edit.
        val gate = CompletableDeferred<Unit>()
        val view = lateView("x.ba", 4, gate, lateResult(2, 4, "bar", "baz"))
        startCompletion(view)
        view.insertAt(DocPos(4), "r")
        view.dispatch(
            TransactionSpec(changes = ChangeSpec.Single(DocPos.ZERO, insert = "Q".asInsert()))
        )
        gate.complete(Unit)
        assertEquals(listOf("bar"), currentCompletions(view.state).map { it.label })
        acceptCompletion(view)
        assertEquals("Qx.bar", view.state.doc.toString())
    }

    private fun tracked(view: EditorSession) = view.state.field(asyncQueryField).changes.keys

    @Test
    fun aThrowingSourceStopsTracking() {
        // The failure is logged rather than failing the scope (#430).
        val view = sourceView("x.", 2) { error("server failed") }
        repeat(20) {
            startCompletion(view)
            assertEquals(emptySet(), tracked(view))
        }
    }

    @Test
    fun hangingSourcesTrackAtMostTheLatestQueries() {
        val never = CompletableDeferred<CompletionResult?>()
        val view = sourceView("x.", 2) { never.await() }
        repeat(50) {
            startCompletion(view)
            assertTrue(tracked(view).size <= MAX_ASYNC_QUERIES)
        }
        assertEquals(MAX_ASYNC_QUERIES, tracked(view).size)
    }

    @Test
    fun closingEndsTheQueriesInFlight() {
        // The first query opens the list; a re-query is still running when Escape
        // closes it, and its late result must not re-open it (#153).
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val view = sourceView("x.", 2) {
            if (calls++ > 0) gate.await()
            lateResult(2, 2, "bar", "baz")
        }
        startCompletion(view)
        assertEquals("active", completionStatus(view.state))
        startCompletion(view)
        assertEquals(1, tracked(view).size)
        closeCompletion(view)
        assertEquals(emptySet(), tracked(view))
        gate.complete(Unit)
        assertNull(completionStatus(view.state))
    }

    @Test
    fun openingEndsTheOlderQueriesInFlight() {
        // Two queries in flight; the newer one opens the list, so the older one's
        // late result is dropped rather than replacing it.
        val gates = listOf(CompletableDeferred<Unit>(), CompletableDeferred<Unit>())
        var calls = 0
        val view = sourceView("x.", 2) {
            val n = calls++
            gates[n].await()
            lateResult(2, 2, if (n == 0) "old" else "new")
        }
        startCompletion(view)
        startCompletion(view)
        gates[1].complete(Unit)
        assertEquals(listOf("new"), currentCompletions(view.state).map { it.label })
        assertEquals(emptySet(), tracked(view))
        gates[0].complete(Unit)
        assertEquals(listOf("new"), currentCompletions(view.state).map { it.label })
    }

    /**
     * An [EditorSession] whose [coroutineScope] uses [Dispatchers.Unconfined] — the
     * real composition-tied scope errors outside a composable, and Unconfined lets
     * the launched async source run/resume inline for deterministic assertions.
     */
    private class UnconfinedSession(
        initial: EditorState,
        override val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
    ) : EditorSession by EditorSession(initial)
}
