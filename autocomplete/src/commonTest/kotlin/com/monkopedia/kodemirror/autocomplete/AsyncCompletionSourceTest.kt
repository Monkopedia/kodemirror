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
import kotlinx.coroutines.CompletableDeferred

/**
 * [asyncCompletionSource] works on every platform (#225): the editor runs it as the
 * suspend source it wraps, without blocking, and calling it directly answers when the
 * source doesn't suspend. Before, the wasmJs version threw on every call.
 */
class AsyncCompletionSourceTest {

    private fun result() = CompletionResult(
        from = DocPos(4),
        options = listOf(Completion("bar"), Completion("baz")),
        validFor = Regex("\\w*")
    )

    @Test
    fun aSuspendingSourceRunsInTheEditorWithoutBlocking() {
        val gate = CompletableDeferred<Unit>()
        val source = asyncCompletionSource {
            gate.await()
            result()
        }
        val s = completionSession("foo ", config = CompletionConfig(override = listOf(source)))
        startCompletion(s)
        assertEquals("pending", completionStatus(s.state))
        gate.complete(Unit)
        assertEquals("active" to listOf("bar", "baz"), shown(s))
    }

    @Test
    fun callingASourceThatDoesNotSuspendAnswers() {
        val source = asyncCompletionSource { result() }
        val context = CompletionContext(completionState("foo "), DocPos(4), explicit = true)
        assertEquals(listOf("bar", "baz"), source(context)?.options?.map { it.label })
    }
}
