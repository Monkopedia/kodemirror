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

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.Compartment
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.exceptionSink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * The lint plugin's scheduling and source batching (#382), on virtual time.
 *
 * Each expected value was produced by driving `@codemirror/lint` 6.9.5's `lintPlugin` in node with
 * the same sources, delays and edits at the same times, and reading `forEachDiagnostic` (and a call
 * counter) at the same instants.
 */
class LintPluginTest {

    /**
     * A session without a composable: it runs [LintPlugin] on [coroutineScope] and forwards every
     * dispatched transaction to it, the way the view's plugin host does.
     */
    private class PluginSession(
        initial: EditorState,
        override val coroutineScope: CoroutineScope,
        private val delegate: EditorSession = EditorSession(initial)
    ) : EditorSession by delegate {
        val plugin = LintPlugin(this)

        override fun dispatch(vararg specs: TransactionSpec) {
            val tr = delegate.state.update(*specs)
            delegate.dispatchTransaction(tr)
            plugin.update(ViewUpdate(this, tr.state, listOf(tr)))
        }
    }

    private fun TestScope.session(vararg extensions: Extension): PluginSession = PluginSession(
        EditorState.create(
            EditorStateConfig(
                doc = "hello world".asDoc(),
                extensions = ExtensionList(extensions.toList())
            )
        ),
        backgroundScope
    )

    /** Types a lambda as the plain [LintSource] overload of [linter]. */
    private fun sync(source: LintSource): LintSource = source

    private fun d(from: Int, to: Int, message: String) =
        Diagnostic(DocPos(from), DocPos(to), Severity.ERROR, message)

    private fun dump(view: EditorSession): String {
        val parts = mutableListOf<String>()
        forEachDiagnostic(view.state) { parts.add("${it.message}@${it.from.value}-${it.to.value}") }
        return "count=${diagnosticCount(view.state)} [${parts.joinToString(",")}]"
    }

    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun PluginSession.type() = dispatch(
        TransactionSpec(
            changes = ChangeSpec.Single(DocPos(0), insert = InsertContent.StringContent("x"))
        )
    )

    @Test
    fun firstRunWaitsForTheDelay() = runTest {
        var calls = 0
        val view =
            session(
                linter(
                    sync { _ ->
                        calls++
                        listOf(d(0, 1, "run$calls"))
                    },
                    LintConfig(delay = 100)
                )
            )
        wait(50)
        assertEquals("calls=0 count=0 []", "calls=$calls ${dump(view)}")
        wait(100)
        assertEquals("calls=1 count=1 [run1@0-1]", "calls=$calls ${dump(view)}")
    }

    @Test
    fun syncLinterRerunsOnceTypingStops() = runTest {
        var calls = 0
        val view =
            session(
                linter(
                    sync { _ ->
                        calls++
                        listOf(d(0, 1, "run$calls"))
                    },
                    LintConfig(delay = 50)
                )
            )
        wait(150)
        view.type()
        wait(300)
        assertEquals("calls=2 count=1 [run2@0-1]", "calls=$calls ${dump(view)}")
    }

    @Test
    fun runIsDebouncedWhileTyping() = runTest {
        var calls = 0
        val view =
            session(
                linter(
                    sync { _ ->
                        calls++
                        listOf(d(0, 1, "run$calls"))
                    },
                    LintConfig(delay = 100)
                )
            )
        wait(200)
        val before = calls
        repeat(4) {
            view.type()
            wait(30)
        }
        assertEquals(0, calls - before)
        wait(200)
        assertEquals(
            "calls-since=1 count=1 [run2@0-1]",
            "calls-since=${calls - before} ${dump(view)}"
        )
    }

    @Test
    fun twoLintersAreCombined() = runTest {
        val view = session(
            linter(sync { _ -> listOf(d(0, 2, "A")) }, LintConfig(delay = 10)),
            linter(sync { _ -> listOf(d(6, 8, "B")) }, LintConfig(delay = 10))
        )
        wait(100)
        assertEquals("count=2 [A@0-2,B@6-8]", dump(view))
    }

    @Test
    fun combinedDelayIsTheLongest() = runTest {
        var calls = 0
        val view = session(
            linter(
                sync { _ ->
                    calls++
                    listOf(d(0, 2, "A"))
                },
                LintConfig(delay = 50)
            ),
            linter(sync { _ -> listOf(d(6, 8, "B")) }, LintConfig(delay = 300))
        )
        wait(150)
        assertEquals("calls=0 count=0 []", "calls=$calls ${dump(view)}")
        wait(250)
        assertEquals("calls=1 count=2 [A@0-2,B@6-8]", "calls=$calls ${dump(view)}")
    }

    @Test
    fun slowSourceDoesNotHoldBackTheOthersPastTheBatchTimeout() = runTest {
        val fast: SuspendLintSource = { _ ->
            delay(10)
            listOf(d(0, 2, "A"))
        }
        val slow: SuspendLintSource = { _ ->
            delay(600)
            listOf(d(6, 8, "B"))
        }
        val view = session(linter(fast, LintConfig(delay = 10)), linter(slow))
        wait(100)
        assertEquals("count=0 []", dump(view))
        wait(250)
        assertEquals("count=1 [A@0-2]", dump(view))
        wait(400)
        assertEquals("count=2 [A@0-2,B@6-8]", dump(view))
    }

    @Test
    fun resultForAnOutdatedDocumentIsDiscarded() = runTest {
        var calls = 0
        val source: SuspendLintSource = { _ ->
            val n = ++calls
            delay(100)
            listOf(d(0, 1, "run$n"))
        }
        val view = session(linter(source, LintConfig(delay = 10)))
        wait(50)
        view.type()
        wait(100)
        assertEquals("calls=2 count=0 []", "calls=$calls ${dump(view)}")
        wait(200)
        assertEquals("calls=2 count=1 [run2@0-1]", "calls=$calls ${dump(view)}")
    }

    @Test
    fun configChangeLetsAnInFlightRunLand() = runTest {
        // Upstream (review-382/configChange.mjs): a reconfigure with no doc change schedules a
        // fresh run but does not discard the one already running.
        var calls = 0
        val source: SuspendLintSource = { _ ->
            val n = ++calls
            delay(100)
            listOf(d(0, 1, "run$n"))
        }
        val compartment = Compartment()
        val view =
            session(
                linter(source, LintConfig(delay = 10)),
                compartment.of(ExtensionList(emptyList()))
            )
        wait(50)
        view.dispatch(
            TransactionSpec(
                effects = listOf(compartment.reconfigure(lintDisplay(LintConfig(delay = 20))))
            )
        )
        wait(70)
        assertEquals("calls=2 count=1 [run1@0-1]", "calls=$calls ${dump(view)}")
        wait(180)
        assertEquals("calls=2 count=1 [run2@0-1]", "calls=$calls ${dump(view)}")
    }

    @Test
    fun forceLintingRunsAPendingRunAndOtherwiseDoesNothing() = runTest {
        var calls = 0
        val view =
            session(
                linter(
                    sync { _ ->
                        calls++
                        listOf(d(0, 1, "run$calls"))
                    },
                    LintConfig(delay = 500)
                )
            )
        forceLinting(view)
        runCurrent()
        assertEquals(1, calls)
        forceLinting(view)
        runCurrent()
        assertEquals(1, calls)
        wait(700)
        assertEquals(1, calls)
    }

    @Test
    fun selectionChangeDoesNotTriggerARun() = runTest {
        var calls = 0
        val view = session(
            linter(
                sync { _ ->
                    calls++
                    emptyList()
                },
                LintConfig(delay = 50)
            )
        )
        wait(100)
        val before = calls
        view.dispatch(TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos(3))))
        wait(150)
        assertEquals(0, calls - before)
    }

    @Test
    fun failingSourceDoesNotSuppressTheOthers() = runTest {
        val logged = mutableListOf<Throwable>()
        val failing: SuspendLintSource = { _ -> error("boom") }
        val view = session(
            exceptionSink.of { logged.add(it) },
            linter(failing, LintConfig(delay = 10)),
            linter(sync { _ -> listOf(d(6, 8, "B")) }, LintConfig(delay = 10))
        )
        wait(400)
        assertEquals("count=1 [B@6-8]", dump(view))
        assertEquals(listOf("boom"), logged.map { it.message })
    }
}
