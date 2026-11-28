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

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyPress
import com.monkopedia.kodemirror.commands.standardKeymap
import com.monkopedia.kodemirror.view.input.assertDoc
import com.monkopedia.kodemirror.view.input.runEditorTest
import java.awt.event.KeyEvent as AwtKeyEvent
import javax.swing.JPanel
import kotlin.test.Test

/**
 * Several keystrokes within one frame (#480).
 *
 * On desktop a printable key arrives twice: AWT's `KEY_PRESSED`, on which the editor enters the
 * character itself, and then `KEY_TYPED` (Compose `KeyEventType.Unknown`), which the hidden
 * text field turns into the same text. That second copy is the echo the editor drops. The
 * field only takes back the editor's emptied value when it next recomposes, so when the next
 * keystroke lands before that frame the field still holds the previous character, reports
 * `"fi"` for the `i`, and the editor used to insert all of it: typing `field` gave
 * `fifiefielfield`. Frames are held back here so that every keystroke lands in one.
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class FastTypingTest {

    /**
     * The Compose events for one AWT key event, carrying the AWT event itself: the text field
     * recognises typed text by the native event being a `KEY_TYPED`.
     */
    private fun awtKey(id: Int, ch: Char): KeyEvent {
        val keyCode = if (id == AwtKeyEvent.KEY_TYPED) {
            AwtKeyEvent.VK_UNDEFINED
        } else {
            AwtKeyEvent.getExtendedKeyCodeForChar(ch.code)
        }
        return KeyEvent(
            key = Key(keyCode),
            type = if (id ==
                AwtKeyEvent.KEY_PRESSED
            ) {
                KeyEventType.KeyDown
            } else {
                KeyEventType.Unknown
            },
            codePoint = ch.code,
            nativeEvent = AwtKeyEvent(JPanel(), id, 0L, 0, keyCode, ch)
        )
    }

    private fun pressed(ch: Char) = awtKey(AwtKeyEvent.KEY_PRESSED, ch)

    private fun typed(ch: Char) = awtKey(AwtKeyEvent.KEY_TYPED, ch)

    @Test
    fun keysTypedWithinOneFrame_areEnteredOnce() = runEditorTest(
        doc = "",
        extensions = keymapOf(standardKeymap)
    ) { holder ->
        mainClock.autoAdvance = false
        val input = onNodeWithTag("KodeMirror_input")
        for (ch in "field") {
            input.performKeyPress(pressed(ch))
            input.performKeyPress(typed(ch))
        }
        mainClock.autoAdvance = true
        waitForIdle()
        holder.assertDoc("field")
    }

    @Test
    fun typedTextWithoutAKeyDown_isStillEntered() = runEditorTest(
        doc = "",
        extensions = keymapOf(standardKeymap)
    ) { holder ->
        // Alt/AltGr characters, dead-key results and input methods arrive only as typed text,
        // with no KeyDown entering them first, and must reach the field. Frames held back, too.
        mainClock.autoAdvance = false
        val input = onNodeWithTag("KodeMirror_input")
        input.performKeyPress(typed('é'))
        input.performKeyPress(pressed('x'))
        input.performKeyPress(typed('x'))
        input.performKeyPress(typed('ü'))
        mainClock.autoAdvance = true
        waitForIdle()
        holder.assertDoc("éxü")
    }

    @Test
    fun typedTextOnSeparateFrames_isEnteredOnce() = runEditorTest(
        doc = "",
        extensions = keymapOf(standardKeymap)
    ) { holder ->
        // The ordinary case: the field adopts the emptied value between keystrokes.
        val input = onNodeWithTag("KodeMirror_input")
        for (ch in "éüåç") {
            input.performKeyPress(typed(ch))
            waitForIdle()
        }
        holder.assertDoc("éüåç")
    }

    @Test
    fun fieldCatchingUpBetweenFrames_isTracked() = runEditorTest(
        doc = "",
        extensions = keymapOf(standardKeymap)
    ) { holder ->
        // "ab" in one frame leaves the field holding "ab" until it recomposes. Once it has, the
        // next "a" is typed into an empty field and must not be read as deleting the "b".
        mainClock.autoAdvance = false
        val input = onNodeWithTag("KodeMirror_input")
        input.performKeyPress(typed('a'))
        input.performKeyPress(typed('b'))
        mainClock.autoAdvance = true
        waitForIdle()
        input.performKeyPress(typed('a'))
        waitForIdle()
        holder.assertDoc("aba")
    }
}
