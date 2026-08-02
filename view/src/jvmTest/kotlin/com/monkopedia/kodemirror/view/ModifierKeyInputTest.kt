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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performMouseInput
import com.monkopedia.kodemirror.commands.standardKeymap
import com.monkopedia.kodemirror.view.input.assertDoc
import com.monkopedia.kodemirror.view.input.runEditorTest
import java.awt.event.KeyEvent as AwtKeyEvent
import javax.swing.JPanel
import kotlin.test.Test

/**
 * End-to-end reproduction of #220 on the platform it was reported against.
 *
 * The Compose test harness derives a key event's code point from the [Key]
 * alone and maps every non-printable key to `0`, so
 * `performKeyInput { keyDown(Key.ShiftLeft) }` cannot express this bug. What a
 * real AWT toolkit sends for a modifier-only press is a `KEY_PRESSED` whose
 * `keyChar` is [AwtKeyEvent.CHAR_UNDEFINED] (U+FFFF) — a Unicode noncharacter
 * that is not an ISO control, which is exactly what slipped past the old guard
 * and got inserted at the caret. These tests source the code point from a real
 * AWT event so the value under test is AWT's, not the test's; Compose Desktop's
 * own `toComposeEvent()` reads `keyChar` the same way.
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class ModifierKeyInputTest {

    private val keymapExt = keymapOf(standardKeymap)

    /** The character AWT reports for a modifier-only press. */
    private fun awtModifierKeyChar(keyCode: Int, modifiers: Int): Char = AwtKeyEvent(
        JPanel(),
        AwtKeyEvent.KEY_PRESSED,
        System.currentTimeMillis(),
        modifiers,
        keyCode,
        AwtKeyEvent.CHAR_UNDEFINED,
        AwtKeyEvent.KEY_LOCATION_LEFT
    ).keyChar

    private fun runModifierTest(
        key: Key,
        keyCode: Int,
        modifiers: Int = 0,
        isShiftPressed: Boolean = false
    ) = runEditorTest(
        doc = "Hello world",
        extensions = keymapExt
    ) { holder ->
        onNodeWithTag("KodeMirror").performMouseInput { click(Offset(10f, 15f)) }
        waitForIdle()
        onNodeWithTag("KodeMirror_input").performKeyPress(
            KeyEvent(
                key = key,
                type = KeyEventType.KeyDown,
                codePoint = awtModifierKeyChar(keyCode, modifiers).code,
                isShiftPressed = isShiftPressed
            )
        )
        waitForIdle()
        holder.assertDoc("Hello world")
    }

    @Test
    fun shiftPress_insertsNothing() = runModifierTest(
        key = Key.ShiftLeft,
        keyCode = AwtKeyEvent.VK_SHIFT,
        modifiers = AwtKeyEvent.SHIFT_DOWN_MASK,
        isShiftPressed = true
    )

    @Test
    fun capsLockPress_insertsNothing() = runModifierTest(
        key = Key.CapsLock,
        keyCode = AwtKeyEvent.VK_CAPS_LOCK
    )
}
