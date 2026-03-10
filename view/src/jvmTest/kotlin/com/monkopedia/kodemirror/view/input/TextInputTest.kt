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
package com.monkopedia.kodemirror.view.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import com.monkopedia.kodemirror.commands.standardKeymap
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.PluginSpec
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.keymapOf
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TextInputTest {

    private val keymapExt = keymapOf(standardKeymap)

    @Test
    fun typeCharacter_insertsIntoDocument() = runEditorTest(
        doc = "Hello",
        extensions = keymapExt
    ) { holder ->
        // Click to focus
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(10f, 15f))
        }
        waitForIdle()

        // Type into the hidden input field
        onNodeWithTag("KodeMirror_input").performTextInput("a")
        waitForIdle()

        val doc = holder.session.state.doc.toString()
        assert(doc.contains("a")) {
            "Expected 'a' to be inserted into document, but doc is: $doc"
        }
    }

    @Test
    fun typeAtCursorPosition() = runEditorTest(
        doc = "ABCDEF",
        extensions = keymapExt
    ) { holder ->
        // Click to focus
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(10f, 15f))
        }
        waitForIdle()

        // Set cursor at position 2 via dispatch
        holder.session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(DocPos(2))
            )
        )
        waitForIdle()
        holder.assertCursorAt(2)

        // Type into the hidden input field
        onNodeWithTag("KodeMirror_input").performTextInput("X")
        waitForIdle()
        holder.assertDoc("ABXCDEF")
    }

    @Test
    fun typeReplacesSelection() = runEditorTest(
        doc = "Hello World",
        extensions = keymapExt
    ) { holder ->
        // Select "Hello" programmatically (positions 0-5)
        holder.session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.EditorSelectionSpec(
                    com.monkopedia.kodemirror.state.EditorSelection.single(
                        DocPos(0),
                        DocPos(5)
                    )
                )
            )
        )
        waitForIdle()
        holder.assertSelectionNotEmpty()

        // Click to focus
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(10f, 15f))
        }
        waitForIdle()

        // Re-set the selection since click moved cursor
        holder.session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.EditorSelectionSpec(
                    com.monkopedia.kodemirror.state.EditorSelection.single(
                        DocPos(0),
                        DocPos(5)
                    )
                )
            )
        )
        waitForIdle()

        // Type into the hidden input field
        onNodeWithTag("KodeMirror_input").performTextInput("Hi")
        waitForIdle()

        holder.assertDoc("Hi World")
    }

    @Test
    fun cursorAdvancesAfterTyping() = runEditorTest(
        doc = "ABCDEF",
        extensions = keymapExt
    ) { holder ->
        // Click to focus
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(10f, 15f))
        }
        waitForIdle()

        // Set cursor at position 2 via dispatch
        holder.session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(DocPos(2))
            )
        )
        waitForIdle()
        holder.assertCursorAt(2)

        // Type "X" — cursor should move to 3
        onNodeWithTag("KodeMirror_input").performTextInput("X")
        waitForIdle()
        holder.assertCursorAt(3)
        holder.assertDoc("ABXCDEF")

        // Type "Y" — cursor should move to 4
        onNodeWithTag("KodeMirror_input").performTextInput("Y")
        waitForIdle()
        holder.assertCursorAt(4)
        holder.assertDoc("ABXYCDEF")
    }

    @Test
    fun pluginSeesNewStateDuringUpdate() {
        // Verify that a ViewPlugin's update() callback sees the new state
        // on session.state (not the stale pre-dispatch state).
        var sessionStateDuringUpdate: String? = null
        var updateStateDuringUpdate: String? = null
        val testPlugin = ViewPlugin.define(
            PluginSpec<PluginValue>(
                create = { _ ->
                    object : PluginValue {
                        override fun update(update: ViewUpdate) {
                            if (update.docChanged) {
                                sessionStateDuringUpdate =
                                    update.session.state.doc.toString()
                                updateStateDuringUpdate =
                                    update.state.doc.toString()
                            }
                        }
                    }
                }
            )
        )
        val ext = ExtensionList(
            listOf(keymapOf(standardKeymap), testPlugin.asExtension())
        )
        runEditorTest(doc = "Hello", extensions = ext) { holder ->
            onNodeWithTag("KodeMirror").performMouseInput {
                click(Offset(10f, 15f))
            }
            waitForIdle()

            holder.session.dispatch(
                TransactionSpec(
                    selection = SelectionSpec.CursorSpec(DocPos(0))
                )
            )
            waitForIdle()

            onNodeWithTag("KodeMirror_input").performTextInput("X")
            waitForIdle()

            assert(sessionStateDuringUpdate == updateStateDuringUpdate) {
                "session.state during update() was " +
                    "'$sessionStateDuringUpdate' but update.state was " +
                    "'$updateStateDuringUpdate'"
            }
            assert(updateStateDuringUpdate == "XHello") {
                "Expected doc 'XHello' but got '$updateStateDuringUpdate'"
            }
        }
    }

    @Test
    fun multipleCharactersInsertSequentially() = runEditorTest(
        doc = "Hello",
        extensions = keymapExt
    ) { holder ->
        // Click to focus
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(10f, 15f))
        }
        waitForIdle()

        // Set cursor at position 0
        holder.session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(DocPos(0))
            )
        )
        waitForIdle()

        // Type "ABC" one char at a time
        onNodeWithTag("KodeMirror_input").performTextInput("A")
        waitForIdle()
        onNodeWithTag("KodeMirror_input").performTextInput("B")
        waitForIdle()
        onNodeWithTag("KodeMirror_input").performTextInput("C")
        waitForIdle()

        holder.assertDoc("ABCHello")
        holder.assertCursorAt(3)
    }
}
