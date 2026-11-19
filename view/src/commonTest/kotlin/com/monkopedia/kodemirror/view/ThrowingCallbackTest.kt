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
package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive

/**
 * A user callback run on the session's scope that throws is logged through
 * [exceptionSink] and does not reach the uncaught-exception handler, which crashes
 * the app on Android (#431). Covers hover sources, `onChangeAsync`/`onSelectionAsync`
 * and `onSave` handlers.
 *
 * Upstream 6.39.16 logs a rejected hover source as "hover tooltip", shows nothing,
 * and the next hover works. It lets a sync throw escape unlogged; here that is
 * logged too, as for completion sources (#430).
 */
class ThrowingCallbackTest {

    private val logged = mutableListOf<String>()

    /** Lost failures would land here; a throwing callback must never reach it. */
    private val unhandled = mutableListOf<Throwable>()

    /** A plain [Job], like the composition scope behind [EditorSession.coroutineScope]. */
    private val scope = CoroutineScope(
        Job() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> unhandled += e }
    )

    private fun session(extension: Extension): EditorSessionImpl {
        val state = EditorState.create(
            EditorStateConfig(
                doc = "hello world".asDoc(),
                extensions = ExtensionList(
                    listOf(extension, exceptionSink.of { logged += it.message.orEmpty() })
                )
            )
        )
        return (EditorSession(state) as EditorSessionImpl).also { session ->
            session.backingCoroutineScope = scope
            session.pluginHost = ViewPluginHost(session).also { it.syncToState(state, null) }
        }
    }

    private fun EditorSessionImpl.hover() = pluginHost!!.collectHoverPlugins().single()

    private fun assertNothingEscaped() {
        assertEquals(emptyList(), unhandled)
        assertTrue(scope.isActive, "the editor's scope survives the throw")
    }

    private val good = Tooltip(pos = 2) {}

    private fun insert(text: String) = TransactionSpec(
        changes = ChangeSpec.Single(DocPos(0), DocPos(0), InsertContent.StringContent(text))
    )

    @Test
    fun anAsyncHoverSourceFailingAfterSuspendingIsLoggedAndTheNextHoverWorks() {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val session = session(
            hoverTooltip(hoverTime = 0) { _, _ ->
                if (calls++ == 0) {
                    gate.await()
                    error("boom-hover")
                }
                good
            }
        )
        session.hover().updateHoverAtPos(1)
        gate.complete(Unit)
        assertNothingEscaped()
        assertEquals(listOf("boom-hover"), logged)
        assertNull(session.hover().currentTooltip)
        session.hover().updateHoverAtPos(2)
        assertSame(good, session.hover().currentTooltip)
    }

    @Test
    fun anAsyncHoverSourceThrowingImmediatelyIsLogged() {
        val session = session(hoverTooltip(hoverTime = 0) { _, _ -> error("boom-hover") })
        session.hover().updateHoverAtPos(1)
        assertNothingEscaped()
        assertEquals(listOf("boom-hover"), logged)
        assertNull(session.hover().currentTooltip)
    }

    @Test
    fun aSyncHoverSourceThatThrowsIsLoggedAndTheNextHoverWorks() {
        var calls = 0
        val session = session(
            hoverTooltip { _, _ -> if (calls++ == 0) error("boom-sync") else good }
        )
        session.hover().updateHoverAtPos(1)
        assertEquals(listOf("boom-sync"), logged)
        assertNull(session.hover().currentTooltip)
        session.hover().updateHoverAtPos(2)
        assertSame(good, session.hover().currentTooltip)
    }

    @Test
    fun aHoverCancelledByMovingAwayIsNotLogged() {
        val session = session(hoverTooltip(hoverTime = 0) { _, _ -> awaitCancellation() })
        session.hover().updateHoverAtPos(1)
        session.hover().updateHoverAtPos(null)
        assertEquals(emptyList(), logged)
        assertNothingEscaped()
    }

    @Test
    fun aHoverCancelledByDisposingTheEditorIsNotLogged() {
        val session = session(hoverTooltip(hoverTime = 0) { _, _ -> awaitCancellation() })
        session.hover().updateHoverAtPos(1)
        scope.cancel()
        assertEquals(emptyList(), logged)
        assertEquals(emptyList(), unhandled)
    }

    @Test
    fun aThrowingOnChangeAsyncCallbackIsLoggedAndRunsOnTheNextChange() {
        val seen = mutableListOf<String>()
        val session = session(
            onChangeAsync { text ->
                if (seen.isEmpty()) {
                    seen += "failed"
                    error("boom-change")
                }
                seen += text
            }
        )
        session.dispatch(insert("a"))
        assertNothingEscaped()
        assertEquals(listOf("boom-change"), logged)
        session.dispatch(insert("b"))
        assertEquals(listOf("failed", "bahello world"), seen)
    }

    @Test
    fun aThrowingOnSelectionAsyncCallbackIsLoggedAndRunsOnTheNextSelection() {
        val seen = mutableListOf<Int>()
        val session = session(
            onSelectionAsync { selection ->
                if (seen.isEmpty()) {
                    seen += -1
                    error("boom-selection")
                }
                seen += selection.main.head.value
            }
        )
        session.dispatch(TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos(1))))
        assertNothingEscaped()
        assertEquals(listOf("boom-selection"), logged)
        session.dispatch(TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos(3))))
        assertEquals(listOf(-1, 3), seen)
    }

    @Test
    fun aCancelledAsyncCallbackIsNotLogged() {
        val session = session(onChangeAsync { throw CancellationException("gone") })
        session.dispatch(insert("a"))
        assertEquals(emptyList(), logged)
        assertNothingEscaped()
    }

    @Test
    fun aThrowingSaveHandlerIsLoggedAndTheOthersStillRun() {
        var saved = false
        val session = session(
            ExtensionList(
                listOf(onSave.of { error("boom-save") }, onSave.of { saved = true })
            )
        )
        assertTrue(save(session))
        assertNothingEscaped()
        assertEquals(listOf("boom-save"), logged)
        assertTrue(saved)
    }
}
