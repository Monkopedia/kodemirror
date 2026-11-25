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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CompletableDeferred

/**
 * When the completion plugin queries a source (#422), against upstream
 * `@codemirror/autocomplete` 6.20.1's real `completionPlugin` on the same inputs
 * (each test names its probe rows, listed in the #422 PR):
 *   * only typing (`input.type`) activates completion;
 *   * a source has one query in flight, and the edits made meanwhile are replayed onto
 *     its result, which then stays, is asked again, or closes;
 *   * in a multi-source session, only the sources whose result an edit invalidated are
 *     asked again.
 */
class PendingQueryTest {

    private val calls = mutableListOf<String>()

    private fun options(vararg labels: String) = labels.map { Completion(it) }

    /** Completes the word before the cursor, logging each call as `name(pos,explicit)`. */
    private fun wordSource(
        name: String,
        vararg labels: String,
        validFor: Regex? = Regex("\\w*")
    ): CompletionSource = { ctx ->
        calls += "$name(${ctx.pos.value},${ctx.explicit})"
        val word = ctx.matchBefore(Regex("\\w+"))
        if (word == null && !ctx.explicit) {
            null
        } else {
            CompletionResult(word?.from ?: ctx.pos, options = options(*labels), validFor = validFor)
        }
    }

    /** [wordSource], but each call waits for the test to open its gate. */
    private class Gated {
        val gates = mutableListOf<CompletableDeferred<Unit>>()

        fun open(count: Int = gates.size) = gates.take(count).forEach { it.complete(Unit) }
    }

    private fun gatedSource(
        gated: Gated,
        name: String,
        vararg labels: String,
        validFor: Regex? = Regex("\\w*")
    ): SuspendCompletionSource {
        val source = wordSource(name, *labels, validFor = validFor)
        return { ctx ->
            val gate = CompletableDeferred<Unit>()
            gated.gates += gate
            val result = source(ctx)
            gate.await()
            result
        }
    }

    private fun session(
        doc: String,
        vararg sources: CompletionSource,
        async: List<SuspendCompletionSource> = emptyList()
    ) = completionSession(
        doc,
        config = CompletionConfig(
            override = sources.toList().ifEmpty {
                null
            },
            asyncOverride = async.ifEmpty { null }
        )
    )

    @Test
    fun onlyTypingActivatesCompletion() {
        // I1: a paste, a drop, plain input or an accepted completion doesn't query.
        for (event in listOf("input.paste", "input.drop", "input", "input.complete")) {
            calls.clear()
            val s = session("foo ", wordSource("s", "bar", "baz"))
            s.typeText("b", event)
            assertEquals(null to emptyList<String>(), shown(s), event)
            assertEquals(emptyList(), calls, event)
        }
        for (event in listOf("input.type", "input.type.compose")) {
            calls.clear()
            val s = session("foo ", wordSource("s", "bar", "baz"))
            s.typeText("b", event)
            assertEquals("active" to listOf("bar", "baz"), shown(s), event)
            assertEquals(listOf("s(5,false)"), calls, event)
        }
    }

    @Test
    fun withoutActivateOnTypingTypingDoesNotQuery() {
        // I2
        val s = completionSession(
            "foo ",
            config = CompletionConfig(
                activateOnTyping = false,
                override = listOf(wordSource("s", "bar"))
            )
        )
        s.typeText("b")
        assertEquals(null to emptyList<String>(), shown(s))
        assertEquals(emptyList(), calls)
    }

    @Test
    fun pastingIntoAnOpenListClosesIt() {
        // P1
        val s = session("foo b", wordSource("s", "bar", "baz"))
        startCompletion(s)
        s.typeText("a", "input.paste")
        assertEquals(null to emptyList<String>(), shown(s))
        assertEquals(listOf("s(5,true)"), calls)
    }

    @Test
    fun typingWhileAQueryRunsReplaysOntoItsResult() {
        // Q1: one query; its result, mapped through "a" and "r", filters by "bar".
        val gated = Gated()
        val s = session("foo ", async = listOf(gatedSource(gated, "a", "bar", "baz")))
        s.typeText("b")
        s.typeText("a")
        s.typeText("r")
        assertEquals("pending" to emptyList<String>(), shown(s))
        assertEquals(listOf("a(5,false)"), calls)
        gated.open()
        assertEquals("active" to listOf("bar"), shown(s))
        acceptCompletion(s)
        assertEquals("foo bar", s.state.doc.toString())
    }

    @Test
    fun aLateResultTheReplayInvalidatesIsAskedForAgain() {
        // Q2: no validFor, so the replayed typing asks again, once the result is in.
        val gated = Gated()
        val s =
            session("foo ", async = listOf(gatedSource(gated, "a", "bar", "baz", validFor = null)))
        s.typeText("b")
        s.typeText("a")
        assertEquals(listOf("a(5,false)"), calls)
        gated.open()
        assertEquals("pending" to emptyList<String>(), shown(s))
        assertEquals(listOf("a(5,false)", "a(6,false)"), calls)
        gated.open()
        assertEquals("active" to listOf("bar", "baz"), shown(s))
    }

    @Test
    fun aLateExplicitResultIsAskedForAgainExplicitly() {
        // A5: the restarted query keeps the explicit flag.
        val gated = Gated()
        val s =
            session("foo b", async = listOf(gatedSource(gated, "a", "bar", "baz", validFor = null)))
        startCompletion(s)
        s.typeText("a")
        assertEquals("pending" to emptyList<String>(), shown(s))
        gated.open()
        assertEquals(listOf("a(5,true)", "a(6,true)"), calls)
        gated.open()
        assertEquals("active" to listOf("bar", "baz"), shown(s))
    }

    @Test
    fun aLateResultFollowsBackspacesMadeWhileItRan() {
        // A3: queried at "foo bar|", two backspaces in flight; the result's limit (5)
        // keeps it open over "b".
        val gated = Gated()
        var s = session("foo ba", async = listOf(gatedSource(gated, "a", "bar", "baz")))
        s.typeText("r")
        s.backspace()
        s.backspace()
        gated.open()
        assertEquals("active" to listOf("bar", "baz"), shown(s))
        assertEquals(listOf("a(7,false)"), calls)
        // A3b: a third backspace passes the limit, so the source, still pending, is
        // asked again at the new cursor.
        calls.clear()
        val gated2 = Gated()
        s = session("foo ba", async = listOf(gatedSource(gated2, "a", "bar", "baz")))
        s.typeText("r")
        repeat(3) { s.backspace() }
        gated2.open()
        assertEquals("pending" to emptyList<String>(), shown(s))
        assertEquals(listOf("a(7,false)", "a(4,false)"), calls)
    }

    @Test
    fun onlyTheInvalidatedSourceIsAskedAgain() {
        // M1: "d" fails its validFor on "1", "w" keeps its result.
        val s = session(
            "foo ",
            wordSource("w", "bar", "baz"),
            wordSource("d", "b1", "b2", validFor = Regex("[a-z]*"))
        )
        s.typeText("b")
        assertEquals("active" to listOf("b1", "b2", "bar", "baz"), shown(s))
        s.typeText("1")
        assertEquals("active" to listOf("b1"), shown(s))
        assertEquals(listOf("w(5,false)", "d(5,false)", "d(6,false)"), calls)
    }

    @Test
    fun aSlowInvalidatedSourceKeepsTheListPendingUntilItAnswers() {
        // M2: the list waits for the slow source, then merges its answer.
        val gated = Gated()
        val s = session(
            "foo ",
            wordSource("w", "bar", "baz"),
            async = listOf(gatedSource(gated, "d", "b1", "b2", validFor = Regex("[a-z]*")))
        )
        s.typeText("b")
        gated.open()
        assertEquals("active" to listOf("b1", "b2", "bar", "baz"), shown(s))
        s.typeText("1")
        assertEquals("pending" to emptyList<String>(), shown(s))
        assertEquals(listOf("w(5,false)", "d(5,false)", "d(6,false)"), calls)
        gated.open()
        assertEquals("active" to listOf("b1"), shown(s))
    }

    @Test
    fun aSourceBelowItsLimitClosesWhileAnInvalidatedOneIsAskedAgain() {
        // E1c: on one backspace "b" (implicit, from 6) drops below its limit and "a"
        // fails its validFor; only "a" is asked again.
        val a: CompletionSource = { ctx ->
            calls += "a(${ctx.pos.value},${ctx.explicit})"
            if (ctx.pos >= DocPos(7)) {
                CompletionResult(
                    DocPos(4),
                    options = options("bar", "baz"),
                    validFor = Regex("\\w{3}")
                )
            } else {
                CompletionResult(DocPos(4), options = options("bat"), validFor = Regex("\\w*"))
            }
        }
        val b: CompletionSource = { ctx ->
            calls += "b(${ctx.pos.value},${ctx.explicit})"
            if (ctx.pos >= DocPos(7)) {
                CompletionResult(
                    DocPos(6),
                    options = options("rex", "rot"),
                    validFor = Regex("\\w*")
                )
            } else {
                null
            }
        }
        val s = session("foo ba", a, b)
        s.typeText("r")
        assertEquals("active" to listOf("bar", "rex", "rot"), shown(s))
        s.backspace()
        assertEquals("active" to listOf("bat"), shown(s))
        assertEquals(listOf("a(7,false)", "b(7,false)", "a(6,false)"), calls)
    }

    // ── Review of #486: one test per condition the suite didn't pin ──

    @Test
    fun aQueryOutlivingManyEditsIsAbandonedAndAskedAgain() {
        // Upstream MaxUpdateCount/MinAbortTime: a query that has seen more than 50
        // updates and is over a second old is aborted, and its source asked again.
        val gated = Gated()
        val time = TestTimeSource()
        val s = completionSession(
            "foo ",
            config = CompletionConfig(asyncOverride = listOf(gatedSource(gated, "a", "bar"))),
            timeSource = time
        )
        s.typeText("x")
        repeat(50) { s.typeText("y") }
        assertEquals(listOf("a(5,false)"), calls)
        time += 1100.milliseconds
        s.typeText("z")
        assertEquals(listOf("a(5,false)", "a(56,false)"), calls)
    }

    @Test
    fun aQueryAbandonedOnlyWhenBothOldAndBusy() {
        // Control for the above: fifty-one updates in under a second keep the query.
        val gated = Gated()
        val s = completionSession(
            "foo ",
            config = CompletionConfig(asyncOverride = listOf(gatedSource(gated, "a", "bar"))),
            timeSource = TestTimeSource()
        )
        s.typeText("x")
        repeat(51) { s.typeText("y") }
        assertEquals(listOf("a(5,false)"), calls)
    }

    @Test
    fun aNullResultLeavesTheSourcePendingWhenTypingActivatedItAgain() {
        // Upstream: no result at 5, but "a" typed meanwhile set the source pending
        // again, so it stays pending without a new query until the next keystroke.
        val gated = Gated()
        val source = gatedSource(gated, "a", "bar", "baz")
        val s =
            session("foo ", async = listOf({ ctx -> source(ctx).takeIf { ctx.pos != DocPos(5) } }))
        s.typeText("b")
        s.typeText("a")
        gated.open()
        assertEquals("pending" to emptyList<String>(), shown(s))
        assertEquals(listOf("a(5,false)"), calls)
        s.typeText("r")
        gated.open()
        assertEquals(listOf("a(5,false)", "a(7,false)"), calls)
        assertEquals("active" to listOf("bar"), shown(s))
    }

    @Test
    fun closingWhileASourceReQueriesRemovesTheDisabledList() {
        // Upstream: the disabled list goes away once nothing is pending.
        val gated = Gated()
        val s =
            session("foo ", async = listOf(gatedSource(gated, "a", "bar", "baz", validFor = null)))
        s.typeText("b")
        gated.open()
        s.typeText("a")
        assertEquals(true, s.state.field(completionStateField).open?.disabled)
        closeCompletion(s)
        assertEquals(null, s.state.field(completionStateField).open)
    }

    @Test
    fun theOtherSourcesShowWhileOneReQueries() {
        // Upstream K1: with a slow source re-querying, the list re-enables with the
        // other sources' results (upstream after updateSyncTime, the port at once).
        val gated = Gated()
        val s = session(
            "foo ",
            wordSource("s", "bar", "baz"),
            async = listOf(gatedSource(gated, "a", "bat", validFor = null))
        )
        s.typeText("b")
        gated.open()
        assertEquals("active" to listOf("bar", "bat", "baz"), shown(s))
        s.typeText("a")
        assertEquals("pending" to listOf("bar", "baz"), shown(s))
    }
}
