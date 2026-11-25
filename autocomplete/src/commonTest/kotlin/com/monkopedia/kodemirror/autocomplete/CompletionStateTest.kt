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
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompletionStateTest {

    /** A session at [doc] whose one source returns [result], opened implicitly. */
    private fun opened(doc: String, cursor: Int, result: CompletionResult): EditorSession =
        completionSession(doc, cursor, CompletionConfig(override = listOf(fixed(result))))
            .also { it.startImplicit() }

    @Test
    fun initialStateIsClosed() {
        assertNull(completionStatus(completionState("hello")))
    }

    @Test
    fun startCompletionEffectOpensCompletion() {
        val result = CompletionResult(
            from = DocPos.ZERO,
            options = listOf(Completion(label = "hello"), Completion(label = "help")),
            validFor = Regex("\\w*")
        )
        assertEquals("active", completionStatus(opened("he", 2, result).state))
    }

    @Test
    fun closeCompletionEffectCloses() {
        val result = CompletionResult(
            from = DocPos.ZERO,
            options = listOf(Completion(label = "hello")),
            validFor = Regex("\\w*")
        )
        val s = opened("he", 2, result)
        assertEquals("active", completionStatus(s.state))
        s.dispatch(TransactionSpec(effects = listOf(closeCompletionEffect.of(Unit))))
        assertNull(completionStatus(s.state))
    }

    @Test
    fun setSelectedCompletionChangesIndex() {
        val result = CompletionResult(
            from = DocPos.ZERO,
            options = listOf(
                Completion(label = "alpha"),
                Completion(label = "beta"),
                Completion(label = "gamma")
            )
        )
        val s = opened("", 0, result)
        assertEquals(0, selectedCompletionIndex(s.state))
        s.dispatch(TransactionSpec(effects = listOf(setSelectedCompletion.of(2))))
        assertEquals(2, selectedCompletionIndex(s.state))
    }

    @Test
    fun docChangeReFiltersWithValidFor() {
        val result = CompletionResult(
            from = DocPos.ZERO,
            options = listOf(
                Completion(label = "hello"),
                Completion(label = "help"),
                Completion(label = "world")
            ),
            validFor = Regex("\\w*")
        )
        val s = opened("h", 1, result)
        assertEquals(2, currentCompletions(s.state).size)
        s.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    DocPos(1),
                    DocPos(1),
                    InsertContent.StringContent("el")
                ),
                selection = SelectionSpec.CursorSpec(DocPos(3)),
                userEvent = "input.type"
            )
        )
        val filtered = currentCompletions(s.state)
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.label.startsWith("hel") })
    }

    @Test
    fun docChangeWithoutValidForCloses() {
        val result = CompletionResult(
            from = DocPos.ZERO,
            options = listOf(Completion(label = "hello"))
        )
        val s = opened("h", 1, result)
        assertEquals("active", completionStatus(s.state))
        // Not typing: an edit that moves the cursor resets the session.
        s.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(DocPos(1), DocPos(1), InsertContent.StringContent("e")),
                selection = SelectionSpec.CursorSpec(DocPos(2))
            )
        )
        assertNull(completionStatus(s.state))
    }
}
