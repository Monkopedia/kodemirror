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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
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
import kotlin.test.Test
import kotlin.test.assertTrue

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
        assertTrue(doc.contains("a"), "Expected 'a' to be inserted into document, but doc is: $doc")
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
    fun pluginSeesNewStateDuringUpdate() = run {
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

            assertTrue(
                sessionStateDuringUpdate == updateStateDuringUpdate,
                "session.state during update() was " +
                    "'$sessionStateDuringUpdate' but update.state was " +
                    "'$updateStateDuringUpdate'"
            )
            assertTrue(
                updateStateDuringUpdate == "XHello",
                "Expected doc 'XHello' but got '$updateStateDuringUpdate'"
            )
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

    /**
     * Input must keep flowing after the keymap consumes a key (#294).
     *
     * Echo suppression is armed whenever a key-event path handles a key. Its
     * reset used to live only in the handler installed by
     * `platformRegisterKeyHandler`, which is a real implementation on wasmJs
     * and a no-op on JVM, Android and native — so on those targets the first
     * keymap-consumed key latched the flag and every later `onValueChange`
     * was discarded, permanently. Backspace is the reported trigger.
     */
    @Test
    fun typingAfterBackspace_reachesTheDocument() = runEditorTest(
        doc = "Hello",
        extensions = keymapExt
    ) { holder ->
        // Deliberately no click: the editor auto-focuses the hidden field at
        // composition, and on Android a pointer press then takes that focus away
        // so no key event is delivered at all (#259). Clicking here would make
        // these tests fail on the very platform the report came from, for an
        // unrelated reason.
        holder.session.dispatch(
            TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos(5)))
        )
        waitForIdle()

        // Backspace is consumed by the keymap, which arms echo suppression.
        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.Backspace)
            keyUp(Key.Backspace)
        }
        waitForIdle()
        holder.assertDoc("Hell")

        // Text input arriving with no key event of its own — an IME commit on
        // Android, or any onValueChange echo — must still reach the document.
        onNodeWithTag("KodeMirror_input").performTextInput("X")
        waitForIdle()
        holder.assertDoc("HellX")
    }

    /**
     * The other direction of #294: suppression must still suppress. A printable
     * key entered by the key-event path is echoed back through the hidden field's
     * `onValueChange` on wasmJs (returning true from `onPreviewKeyEvent` does not
     * `preventDefault()` the DOM event), and that echo must be dropped rather
     * than doubling the character — so the character appears exactly once
     * however it arrived.
     *
     * Which half does the entering is target-dependent, which is why the
     * assertion is on the result rather than on the path. On JVM, Android and
     * native the key event carries the character, so the key path enters it and
     * the following text input is its echo. On wasmJs `keyEventLayoutKey` reads
     * the browser's real keydown, which a synthetic `performKeyInput` never
     * fires, so the key press enters nothing and the text input is the only
     * source. Either way the document must end up with one `x`.
     */
    @Test
    fun keyEnteredCharacter_isNotDoubledByItsEcho() = runEditorTest(
        doc = "",
        extensions = keymapExt
    ) { holder ->
        // No click here either — see typingAfterBackspace_reachesTheDocument.
        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.X)
            keyUp(Key.X)
        }
        waitForIdle()

        onNodeWithTag("KodeMirror_input").performTextInput("x")
        waitForIdle()
        holder.assertDoc("x")
    }

    /**
     * Echo suppression must not swallow input that is not the echo (#294). This
     * is the same latch as [typingAfterBackspace_reachesTheDocument] reached
     * through the other arming path — a printable key rather than a keymap
     * command — and it is what bounds the suppression to the keystroke that
     * armed it: text differing from what the key path entered is always genuine.
     */
    @Test
    fun differentTextAfterAKeyPress_isNotSuppressed() = runEditorTest(
        doc = "",
        extensions = keymapExt
    ) { holder ->
        // No click here either — see typingAfterBackspace_reachesTheDocument.
        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.X)
            keyUp(Key.X)
        }
        waitForIdle()

        onNodeWithTag("KodeMirror_input").performTextInput("y")
        waitForIdle()
        val doc = holder.session.state.doc.toString()
        assertTrue(doc.endsWith("y"), "Expected typed 'y' to reach the document, but doc is: $doc")
    }
}
