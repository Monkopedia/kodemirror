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
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompletionStateTest {

    private fun createState(doc: String, cursor: Int = doc.length): EditorState =
        EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(cursor)),
                extensions = completionStateField
            )
        )

    private fun openCompletions(state: EditorState, result: CompletionResult): EditorState {
        val tr = state.update(
            TransactionSpec(
                effects = listOf(startCompletionEffect.of(result))
            )
        )
        return tr.state
    }

    @Test
    fun initialStateIsClosed() {
        val state = createState("hello")
        assertNull(completionStatus(state))
    }

    @Test
    fun startCompletionEffectOpensCompletion() {
        val state = createState("he", cursor = 2)
        val result = CompletionResult(
            from = DocPos.ZERO,
            options = listOf(Completion(label = "hello"), Completion(label = "help")),
            validFor = Regex("\\w*")
        )
        val updated = openCompletions(state, result)
        assertEquals("active", completionStatus(updated))
    }

    @Test
    fun closeCompletionEffectCloses() {
        val state = createState("he", cursor = 2)
        val result = CompletionResult(
            from = DocPos.ZERO,
            options = listOf(Completion(label = "hello")),
            validFor = Regex("\\w*")
        )
        val opened = openCompletions(state, result)
        assertEquals("active", completionStatus(opened))

        val tr = opened.update(
            TransactionSpec(
                effects = listOf(closeCompletionEffect.of(Unit))
            )
        )
        assertNull(completionStatus(tr.state))
    }

    @Test
    fun setSelectedCompletionChangesIndex() {
        val state = createState("", cursor = 0)
        val result = CompletionResult(
            from = DocPos.ZERO,
            options = listOf(
                Completion(label = "alpha"),
                Completion(label = "beta"),
                Completion(label = "gamma")
            )
        )
        val opened = openCompletions(state, result)
        assertEquals(0, selectedCompletionIndex(opened))

        val tr = opened.update(
            TransactionSpec(
                effects = listOf(setSelectedCompletion.of(2))
            )
        )
        assertEquals(2, selectedCompletionIndex(tr.state))
    }

    @Test
    fun docChangeReFiltersWithValidFor() {
        val state = createState("h", cursor = 1)
        val result = CompletionResult(
            from = DocPos.ZERO,
            options = listOf(
                Completion(label = "hello"),
                Completion(label = "help"),
                Completion(label = "world")
            ),
            validFor = Regex("\\w*")
        )
        val opened = openCompletions(state, result)
        val initial = currentCompletions(opened)
        assertEquals(2, initial.size)

        val tr = opened.update(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    DocPos(1),
                    DocPos(1),
                    InsertContent.StringContent("el")
                ),
                selection = SelectionSpec.CursorSpec(DocPos(3))
            )
        )
        val filtered = currentCompletions(tr.state)
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.label.startsWith("hel") })
    }

    @Test
    fun docChangeWithoutValidForCloses() {
        val state = createState("h", cursor = 1)
        val result = CompletionResult(
            from = DocPos.ZERO,
            options = listOf(Completion(label = "hello"))
        )
        val opened = openCompletions(state, result)
        assertEquals("active", completionStatus(opened))

        val tr = opened.update(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    DocPos(1),
                    DocPos(1),
                    InsertContent.StringContent("e")
                ),
                selection = SelectionSpec.CursorSpec(DocPos(2))
            )
        )
        assertNull(completionStatus(tr.state))
    }
}
