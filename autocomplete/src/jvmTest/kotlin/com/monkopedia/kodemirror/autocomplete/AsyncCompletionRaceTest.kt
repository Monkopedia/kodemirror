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
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.insertAt
import com.monkopedia.kodemirror.view.select
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
     * An [EditorSession] whose [coroutineScope] uses [Dispatchers.Unconfined] — the
     * real composition-tied scope errors outside a composable, and Unconfined lets
     * the launched async source run/resume inline for deterministic assertions.
     */
    private class UnconfinedSession(
        initial: EditorState
    ) : EditorSession by EditorSession(initial) {
        override val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
    }
}
