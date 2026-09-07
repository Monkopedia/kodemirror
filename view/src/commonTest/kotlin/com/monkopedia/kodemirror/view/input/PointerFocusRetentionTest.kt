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
import androidx.compose.ui.test.performTouchInput
import com.monkopedia.kodemirror.commands.standardKeymap
import com.monkopedia.kodemirror.view.EditorSessionImpl
import com.monkopedia.kodemirror.view.keymapOf
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A press on the editor must leave the editor still focused, whatever kind of
 * pointer made it.
 *
 * # What this isolates (#259)
 *
 * Thirteen instrumented tests failed on Android with `Expected cursor at 11 but
 * was at 0`, which reads as a hit-test failure and is not one: the press
 * hit-tests correctly and dispatches the right selection, and it is the *key*
 * afterwards that does nothing, because the press has taken the editor's focus
 * away in between. Three diagnoses were reverse-engineered from that offset
 * before anyone measured focus.
 *
 * The mechanism is Compose's, not the editor's. `AndroidComposeView`
 * `dispatchTouchEvent` applies `AutoClearFocusBehavior.CursorBased` — the
 * platform default — *after* the gesture handlers have run: on a press from a
 * mouse or touchpad it clears focus whenever the press lands outside the bounds
 * of the focused node. The editor's focused node is the 1-dp hidden input, so
 * every mouse press anywhere in the editor lands "outside" it. Touch presses are
 * not cursor presses and never trip it, which is why the same gesture worked
 * with a finger and not with a mouse.
 *
 * # Why the two tests are a pair
 *
 * They differ in one thing: the pointer type. Keeping both means a fix for the
 * mouse path that broke the touch path — the more likely accident, since the
 * fix has to hold focus across a press without stopping anything else from ever
 * taking it — fails here immediately rather than somewhere unrelated.
 *
 * Each asserts an exact offset, not "the cursor moved": the press at x=8 is two
 * pixels into the content's 6-dp start padding, so it resolves to the first
 * character of line 1 on any font, and `End` from there must land on 11, the
 * end of `"Hello world"`. Position 0 is what a dropped key leaves behind, so an
 * exact 11 is the only outcome that says the key was delivered.
 */
@OptIn(ExperimentalTestApi::class)
class PointerFocusRetentionTest {

    private val doc = "Hello world\nSecond line"
    private val keymapExt = keymapOf(standardKeymap)

    private fun SessionHolder.assertFocusHeld(pointer: String) {
        val impl = session as EditorSessionImpl
        assertTrue(
            impl.hasFocus,
            "Expected the editor to still hold focus after a $pointer press, but " +
                "hasFocus=false. The press reached the editor and placed the cursor at " +
                "${session.state.selection.main.head.value}; focus was then taken away " +
                "from it, so every key that follows is dropped (#259)."
        )
    }

    private fun SessionHolder.assertEndOfLineReached(pointer: String) {
        val head = session.state.selection.main.head.value
        assertTrue(
            head == 11,
            "Expected End to move the cursor to 11, the end of \"Hello world\", after a " +
                "$pointer press, but it is at $head. At 0 the key was never delivered — " +
                "the press placed the cursor there and the editor lost focus before the " +
                "key arrived (#259)."
        )
    }

    /**
     * A mouse press must not cost the editor its focus: the key after it has to
     * reach the keymap and move the cursor to the end of the line.
     */
    @Test
    fun mousePress_keepsFocus_andTheKeyAfterItIsDelivered() = runEditorTest(
        doc = doc,
        extensions = keymapExt
    ) { holder ->
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(8f, 15f))
        }
        waitForIdle()
        holder.assertFocusHeld("mouse")

        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.MoveEnd)
            keyUp(Key.MoveEnd)
        }
        waitForIdle()
        holder.assertEndOfLineReached("mouse")
    }

    /**
     * The same gesture with a finger, which already worked on every platform.
     * It is here so that holding focus across a mouse press cannot be bought by
     * breaking the touch path.
     */
    @Test
    fun touchPress_keepsFocus_andTheKeyAfterItIsDelivered() = runEditorTest(
        doc = doc,
        extensions = keymapExt
    ) { holder ->
        onNodeWithTag("KodeMirror").performTouchInput {
            down(Offset(8f, 15f))
            up()
        }
        waitForIdle()
        holder.assertFocusHeld("touch")

        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.MoveEnd)
            keyUp(Key.MoveEnd)
        }
        waitForIdle()
        holder.assertEndOfLineReached("touch")
    }
}
