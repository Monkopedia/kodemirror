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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.commands.standardKeymap
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Input-method composition through the real text field (#294).
 *
 * The edits are delivered the way a desktop input method's events reach Compose: as
 * [EditCommand]s on the text input session the hidden field opens, which Compose's edit
 * processor applies to the field's value before calling `onValueChange`. The session's
 * request is captured with [InterceptPlatformTextInput], which leaves the platform session
 * running as normal.
 *
 * This lives in `jvmTest` because the request's `onEditCommand` is skiko API; Android's
 * equivalent (an `InputConnection`) is exercised by the instrumented suite.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class ImeCompositionTest {

    private class Ime {
        var request: PlatformTextInputMethodRequest? = null

        fun send(vararg commands: EditCommand) {
            assertNotNull(request, "the hidden field never started a text input session")
                .onEditCommand(commands.toList())
        }
    }

    private fun runImeTest(
        doc: String,
        cursor: Int,
        keymap: Extension = keymapOf(standardKeymap),
        block: ComposeUiTest.(EditorSession, Ime) -> Unit
    ) = runComposeUiTest {
        val ime = Ime()
        val session = EditorSession(
            EditorState.create(
                EditorStateConfig(
                    doc = doc.asDoc(),
                    selection = SelectionSpec.CursorSpec(DocPos(cursor)),
                    extensions = keymap
                )
            )
        )
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                InterceptPlatformTextInput(
                    interceptor = { request, nextHandler ->
                        ime.request = request
                        nextHandler.startInputMethod(request)
                    }
                ) {
                    Box(Modifier.requiredSize(400.dp, 300.dp)) {
                        KodeMirror(session = session)
                    }
                }
            }
        }
        waitForIdle()
        block(session, ime)
    }

    @Test
    fun pinyinComposition_commitsChineseOnce() = runImeTest("ab", 1) { session, ime ->
        ime.send(SetComposingTextCommand("n", 1))
        waitForIdle()
        ime.send(SetComposingTextCommand("ni", 1))
        waitForIdle()
        ime.send(SetComposingTextCommand("nih", 1))
        waitForIdle()
        assertEquals("anihb", session.state.doc.toString())
        ime.send(CommitTextCommand("你好", 1))
        waitForIdle()
        assertEquals("a你好b", session.state.doc.toString())
        assertEquals(3, session.state.selection.main.head.value)
    }

    @Test
    fun emojiCommit_insertsWholeCharacter() = runImeTest("ab", 1) { session, ime ->
        ime.send(CommitTextCommand("😀", 1))
        waitForIdle()
        assertEquals("a😀b", session.state.doc.toString())
        assertEquals(3, session.state.selection.main.head.value)
    }

    @Test
    fun mixedChineseEmojiAndLatin() = runImeTest("", 0) { session, ime ->
        ime.send(SetComposingTextCommand("zh", 1))
        waitForIdle()
        ime.send(CommitTextCommand("中", 1))
        waitForIdle()
        ime.send(CommitTextCommand("😀", 1))
        waitForIdle()
        onNodeWithTag("KodeMirror_input").performKeyInput { pressKey(Key.A) }
        waitForIdle()
        ime.send(SetComposingTextCommand("wen", 1))
        waitForIdle()
        ime.send(SetComposingTextCommand("文", 1))
        waitForIdle()
        ime.send(FinishComposingTextCommand())
        waitForIdle()
        assertEquals("中😀a文", session.state.doc.toString())
        assertEquals(5, session.state.selection.main.head.value)
    }

    @Test
    fun selectionMovedElsewhere_endsTheComposition() = runImeTest("ab", 1) { session, ime ->
        ime.send(SetComposingTextCommand("ni", 1))
        waitForIdle()
        onNodeWithTag("KodeMirror_input").assertTextEquals("ni")
        session.dispatch(TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos(0))))
        waitForIdle()
        // The mirrored text stays as typed, and the field the input method sees is emptied.
        assertEquals("anib", session.state.doc.toString())
        onNodeWithTag("KodeMirror_input").assertTextEquals("")
    }

    @Test
    fun compositionMatchingAKeystrokesEcho_isNotDropped() = runImeTest("", 0) { session, ime ->
        // The key inserts "x" itself and arms suppression of its text-field echo.
        onNodeWithTag("KodeMirror_input").performKeyInput { pressKey(Key.X) }
        waitForIdle()
        // A composition is never that echo, even when its text matches.
        ime.send(SetComposingTextCommand("x", 1))
        waitForIdle()
        assertEquals("xx", session.state.doc.toString())
    }

    @Test
    fun keysDuringComposition_skipTheKeymapAndTheTypingBridge() {
        val keymapKeys = mutableListOf<String>()
        val userEvents = mutableListOf<String?>()
        val recorder = ExtensionList(
            listOf(
                keymapOf(
                    listOf("Backspace", "Enter").map { key ->
                        KeyBinding(key = key, run = {
                            keymapKeys += key
                            true
                        })
                    }
                ),
                EditorSession.updateListener.of { update ->
                    update.transactions.forEach {
                        userEvents += it.annotation(Transaction.userEvent)
                    }
                }
            )
        )
        runImeTest("ab", 1, keymap = recorder) { _, ime ->
            // The keys an input method is composing with are its own, so neither the keymap nor
            // the bridge that inserts printable keys directly may see them. (Each key is sent
            // into a fresh composition: the field's own handling of a key ends the one it is in.)
            for (key in listOf(Key.Backspace, Key.Enter, Key.X)) {
                ime.send(SetComposingTextCommand("ni", 1))
                waitForIdle()
                onNodeWithTag("KodeMirror_input").performKeyInput { pressKey(key) }
                waitForIdle()
            }
            assertEquals(emptyList(), keymapKeys)
            // The typing bridge's insert is plain `input.type`; the field's own edits of a
            // composition are `input.type.compose`.
            assertEquals(emptyList(), userEvents.filter { it == "input.type" })
            // ...and the composition's own transactions carry CodeMirror's compose events, which
            // history and closeBrackets key on.
            assertEquals(
                listOf("input.type.compose.start"),
                userEvents.filter { it == "input.type.compose.start" }.distinct()
            )
            // Outside a composition the same keys do reach the keymap.
            ime.send(CommitTextCommand("你", 1))
            waitForIdle()
            onNodeWithTag("KodeMirror_input").performKeyInput { pressKey(Key.Backspace) }
            waitForIdle()
            assertEquals(listOf("Backspace"), keymapKeys)
        }
    }
}
