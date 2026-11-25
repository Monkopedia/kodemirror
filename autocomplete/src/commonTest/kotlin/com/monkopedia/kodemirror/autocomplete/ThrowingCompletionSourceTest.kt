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
import com.monkopedia.kodemirror.view.exceptionSink
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

/**
 * A completion source that throws is logged through [exceptionSink] and treated as
 * having returned nothing (#430). Before, a sync throw escaped the trigger, and an
 * async one failed the editor's coroutine scope, after which no async query ran again.
 *
 * Upstream 6.20.1 logs a rejected source; measured on the npm package, it also closes
 * the whole list and leaves that source pending until the cursor moves, and lets a sync
 * throw escape uncaught. Here the other sources' options stay and the next trigger
 * queries again.
 */
class ThrowingCompletionSourceTest {

    private val logged = mutableListOf<String>()

    /** Lost failures would land here; a throwing source must never reach it. */
    private val unhandled = mutableListOf<Throwable>()

    /**
     * A plain [Job], like the composition scope behind [EditorSession.coroutineScope]:
     * one failed child cancels it, and every later query with it.
     */
    private val scope = CoroutineScope(
        Job() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> unhandled += e }
    )

    private fun session(
        sync: List<CompletionSource> = emptyList(),
        async: List<SuspendCompletionSource> = emptyList()
    ): EditorSession {
        val config = CompletionConfig(
            override = sync.ifEmpty { null },
            asyncOverride = async.ifEmpty { null }
        )
        val state = EditorState.create(
            EditorStateConfig(
                doc = "foo ".asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(4)),
                extensions = ExtensionList(
                    listOf(
                        completionConfig.of(config),
                        completionStateField,
                        exceptionSink.of { logged += it.message.orEmpty() }
                    )
                )
            )
        )
        return PluginSession(state, scope)
    }

    private fun options(vararg labels: String) =
        CompletionResult(from = DocPos(4), options = labels.map { Completion(label = it) })

    private fun labels(view: EditorSession) = currentCompletions(view.state).map { it.label }

    @Test
    fun aSyncSourceThatThrowsOnceCompletesOnTheNextTrigger() {
        var calls = 0
        val view = session(
            sync = listOf({ if (calls++ == 0) error("boom-sync") else options("alpha", "beta") })
        )
        startCompletion(view)
        assertEquals(listOf("boom-sync"), logged)
        assertEquals(emptyList(), labels(view))
        startCompletion(view)
        assertEquals(listOf("alpha", "beta"), labels(view))
    }

    @Test
    fun aThrowingSyncSourceKeepsTheOthersOptions() {
        val view = session(sync = listOf({ error("boom-sync") }, { options("good") }))
        startCompletion(view)
        assertEquals(listOf("good"), labels(view))
        assertEquals(listOf("boom-sync"), logged)
    }

    @Test
    fun anAsyncSourceFailingAfterSuspendingCompletesOnTheNextTrigger() {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val view = session(
            async = listOf({
                if (calls++ == 0) {
                    gate.await()
                    error("boom-async")
                }
                options("alpha", "beta")
            })
        )
        startCompletion(view)
        gate.complete(Unit)
        assertTrue(scope.isActive, "the editor's scope survives the throw")
        assertEquals(emptyList(), unhandled)
        assertEquals(listOf("boom-async"), logged)
        startCompletion(view)
        assertEquals(listOf("alpha", "beta"), labels(view))
        assertEquals("active", completionStatus(view.state))
    }

    @Test
    fun aThrowingAsyncSourceKeepsTheOthersOptions() {
        val view = session(async = listOf({ error("boom-async") }, { options("good") }))
        startCompletion(view)
        assertEquals(listOf("good"), labels(view))
        assertEquals(listOf("boom-async"), logged)
        assertTrue(scope.isActive)
    }

    @Test
    fun aCancelledSourceIsNotLogged() {
        val view = session(async = listOf({ throw CancellationException("gone") }))
        startCompletion(view)
        assertEquals(emptyList(), logged)
        assertTrue(scope.isActive)
        assertEquals(emptyList(), unhandled)
        // It counts as no result, so the source isn't left pending.
        assertEquals(null, completionStatus(view.state))
    }
}
