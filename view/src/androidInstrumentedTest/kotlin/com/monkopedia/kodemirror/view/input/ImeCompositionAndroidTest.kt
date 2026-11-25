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

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.commands.standardKeymap
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.keymapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Soft-keyboard input through the hidden field's real [InputConnection] (#294).
 *
 * Android keyboards compose far more than CJK text: Gboard keeps an underlined composing region
 * for every Latin word it may autocorrect, so `setComposingText` / `commitText` is the ordinary
 * typing path here, not a niche one. The connection is the one Compose creates for the field,
 * obtained from the text input request with [InterceptPlatformTextInput].
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class ImeCompositionAndroidTest {

    private fun runImeTest(
        doc: String,
        cursor: Int,
        block: ComposeUiTest.(EditorSession, () -> InputConnection) -> Unit
    ) = runComposeUiTest {
        var connection: InputConnection? = null
        val session = EditorSession(
            EditorState.create(
                EditorStateConfig(
                    doc = doc.asDoc(),
                    selection = SelectionSpec.CursorSpec(DocPos(cursor)),
                    extensions = keymapOf(standardKeymap)
                )
            )
        )
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                InterceptPlatformTextInput(
                    interceptor = { request, nextHandler ->
                        connection = request.createInputConnection(EditorInfo())
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
        block(session) {
            assertNotNull(connection, "the hidden field never started a text input session")
        }
    }

    /** Run [edit] on the main thread, as the input method's binder calls are, then settle. */
    private fun ComposeUiTest.ime(ic: () -> InputConnection, edit: InputConnection.() -> Unit) {
        runOnUiThread { ic().edit() }
        waitForIdle()
    }

    @Test
    fun latinWordComposedByTheKeyboard_isTypedOnce() = runImeTest("", 0) { session, ic ->
        for (prefix in listOf("h", "he", "hel", "hell", "hello")) {
            ime(ic) { setComposingText(prefix, 1) }
        }
        assertEquals("hello", session.state.doc.toString())
        ime(ic) { commitText("hello ", 1) }
        assertEquals("hello ", session.state.doc.toString())
        ime(ic) { setComposingText("w", 1) }
        ime(ic) { setComposingText("wo", 1) }
        ime(ic) { finishComposingText() }
        assertEquals("hello wo", session.state.doc.toString())
        assertEquals(8, session.state.selection.main.head.value)
    }

    @Test
    fun autocorrectReplacesTheComposingWord() = runImeTest("", 0) { session, ic ->
        ime(ic) { setComposingText("teh", 1) }
        ime(ic) { commitText("the", 1) }
        assertEquals("the", session.state.doc.toString())
    }

    @Test
    fun pinyinCommitsChinese() = runImeTest("ab", 1) { session, ic ->
        ime(ic) { setComposingText("ni", 1) }
        ime(ic) { setComposingText("nihao", 1) }
        ime(ic) { commitText("你好", 1) }
        assertEquals("a你好b", session.state.doc.toString())
        assertEquals(3, session.state.selection.main.head.value)
    }

    @Test
    fun emojiAndBackspace() = runImeTest("ab", 1) { session, ic ->
        ime(ic) { commitText("😀", 1) }
        assertEquals("a😀b", session.state.doc.toString())
        // With nothing in the field, a keyboard sends Backspace as a key event, which the
        // keymap handles — and input keeps working after it.
        ime(ic) { sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)) }
        ime(ic) { sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)) }
        assertEquals("ab", session.state.doc.toString())
        ime(ic) { commitText("中", 1) }
        assertEquals("a中b", session.state.doc.toString())
    }
}
