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
package com.monkopedia.kodemirror.lint

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Malformed diagnostics must not hang the lint state. Splitting them into pieces runs on every
 * render, so a loop that fails to end freezes the editor; these run it on a thread with a time
 * limit so that a regression fails instead of hanging the suite.
 */
class LintMalformedInputTest {

    private fun withinTimeLimit(block: () -> Unit) {
        var failure: Throwable? = null
        val worker = thread(isDaemon = true) {
            try {
                block()
            } catch (t: Throwable) {
                failure = t
            }
        }
        worker.join(10_000)
        assertFalse(worker.isAlive, "did not finish within 10s")
        failure?.let { throw it }
    }

    private fun error(from: Int, to: Int) =
        Diagnostic(DocPos(from), DocPos(to), Severity.ERROR, "$from-$to")

    @Test
    fun invertedDiagnosticOverlappingAnotherFinishes() = withinTimeLimit {
        val session = EditorSession(
            EditorState.create(EditorStateConfig(doc = "a\nbcdef".asDoc(), extensions = lintState))
        )
        setDiagnostics(session, listOf(error(5, 3), error(2, 6)))
        var count = 0
        session.state.field(lintState).decorations.between(DocPos.ZERO, DocPos(7)) { _, _, _ ->
            count++
            null
        }
        assertEquals(3, count)
        // F8 navigates over the same pieces.
        nextDiagnostic(session)
        val sel = session.state.selection.main
        assertEquals(2 to 5, sel.from.value to sel.to.value)
    }

    @Test
    fun negativeAndPastEndDiagnosticsFinish() = withinTimeLimit {
        val session = EditorSession(
            EditorState.create(EditorStateConfig(doc = "a\n\nb".asDoc(), extensions = lintState))
        )
        setDiagnostics(
            session,
            listOf(error(-2, 1), error(-3, -1), error(2, 2), error(1, 9), error(7, 9), error(3, 1))
        )
        session.state.field(lintState).decorations
        repeat(4) { nextDiagnostic(session) }
        repeat(4) { previousDiagnostic(session) }
        setDiagnostics(session, emptyList())
        assertEquals(0, session.state.field(lintState).decorations.size)
    }
}
