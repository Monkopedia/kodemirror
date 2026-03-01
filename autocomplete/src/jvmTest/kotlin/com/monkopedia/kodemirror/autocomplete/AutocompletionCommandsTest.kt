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

import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutocompletionCommandsTest {

    private val testOptions = listOf(
        Completion(label = "apple"),
        Completion(label = "banana"),
        Completion(label = "cherry")
    )

    private val testSource: CompletionSource = { ctx ->
        CompletionResult(
            from = 0,
            to = ctx.pos,
            options = testOptions
        )
    }

    private fun createView(doc: String, cursor: Int = doc.length): EditorView {
        val config = CompletionConfig(override = listOf(testSource))
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(cursor),
                extensions = ExtensionList(
                    listOf(
                        completionConfig.of(config),
                        completionStateField
                    )
                )
            )
        )
        return EditorView(state)
    }

    @Test
    fun startCompletionTriggersSources() {
        val view = createView("")
        startCompletion(view)
        assertEquals("active", completionStatus(view.state))
    }

    @Test
    fun closeCompletionClosesOpenList() {
        val view = createView("")
        startCompletion(view)
        assertEquals("active", completionStatus(view.state))
        assertTrue(closeCompletion(view))
        assertNull(completionStatus(view.state))
    }

    @Test
    fun acceptCompletionAppliesSelected() {
        val view = createView("")
        startCompletion(view)
        assertTrue(acceptCompletion(view))
        assertEquals("apple", view.state.doc.toString())
    }

    @Test
    fun moveCompletionSelectionChangesIndex() {
        val view = createView("")
        startCompletion(view)
        assertEquals(0, selectedCompletionIndex(view.state))
        val moveDown = moveCompletionSelection(forward = true)
        assertTrue(moveDown(view))
        assertEquals(1, selectedCompletionIndex(view.state))
    }

    @Test
    fun completeFromListProvidesResults() {
        val source = completeFromList(
            listOf(
                Completion(label = "foo"),
                Completion(label = "bar")
            )
        )
        val config = CompletionConfig(override = listOf(source))
        val state = EditorState.create(
            EditorStateConfig(
                doc = "".asDoc(),
                extensions = ExtensionList(
                    listOf(
                        completionConfig.of(config),
                        completionStateField
                    )
                )
            )
        )
        val view = EditorView(state)
        startCompletion(view)
        val completions = currentCompletions(view.state)
        assertEquals(2, completions.size)
    }

    @Test
    fun acceptCompletionReturnsFalseWhenClosed() {
        val view = createView("")
        assertFalse(acceptCompletion(view))
    }
}
