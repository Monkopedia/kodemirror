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
import com.monkopedia.kodemirror.commands.standardKeymap
import com.monkopedia.kodemirror.view.EditorSessionImpl
import com.monkopedia.kodemirror.view.keymapOf
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for focus management after editor clicks.
 *
 * # Issue #2: a tap/drag race could skip `requestFocus()`
 *
 * The editor used to run separate tap and drag gesture coroutines that each
 * competed for the DOWN event, so focus could be skipped depending on which
 * won. They are now one `awaitEachGesture` block that requests focus on the
 * down before deciding tap-vs-drag. These tests hold that: click, repeated
 * click, drag, and click-after-drag must all leave the hidden field focused
 * and keyboard navigation working.
 *
 * # Issue #303: DOM focus and the soft keyboard on wasmJs
 *
 * Compose focus is not the whole story in a browser. Compose's canvas carries
 * `tabindex="0"`, so an un-`preventDefault()`ed pointer down moves *DOM* focus
 * to the canvas and blurs the backing `<textarea>` — the only element a browser
 * raises a soft keyboard for — and Compose-web only calls `preventDefault()`
 * when the gesture consumed a change. The editor therefore consumes the down,
 * and calls `SoftwareKeyboardController.show()` in the same gesture because a
 * `requestFocus()` on an already-focused field is a no-op and never asks the
 * platform for the IME.
 *
 * Neither half is observable from a Compose UI test: `hasFocus` below reports
 * Compose focus, not `document.activeElement`, and there is no soft keyboard in
 * a test environment. The DOM-level evidence for #303 lives in the PR, measured
 * against a real browser. What these tests can and do guard is that the added
 * consume did not cost the gesture behaviour above; the pointer-type coverage
 * that consume most affects is in [TouchGestureTest] and [DragSelectionTest].
 */
@OptIn(ExperimentalTestApi::class)
class FocusManagementTest {

    private val doc = "Hello world\nSecond line"
    private val keymapExt = keymapOf(standardKeymap)

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Assert that [impl.hasFocus] is true.
     *
     * [EditorSessionImpl.hasFocus] is set by the `onFocusChanged` modifier on
     * the hidden BasicTextField. It is true iff Compose considers the text
     * field focused. On JVM this is reliable; on wasmJs it may differ from
     * browser-level DOM focus.
     */
    private fun SessionHolder.assertHasFocus() {
        val impl = session as EditorSessionImpl
        assertTrue(
            impl.hasFocus,
            "Expected editor to have focus (BasicTextField.onFocusChanged isFocused=true), " +
                "but hasFocus=${impl.hasFocus}. " +
                "focusRequester.requestFocus() was not called after the click, " +
                "leaving the keyboard handler inactive."
        )
    }

    // -------------------------------------------------------------------------
    // Click → focus tests (verifiable on JVM)
    // -------------------------------------------------------------------------

    /**
     * A single click on the editor should give Compose focus to the hidden
     * BasicTextField so that subsequent key events are routed to it.
     */
    @Test
    fun clickOnEditor_givesFocusToTextField() = runEditorTest(
        doc = doc,
        extensions = keymapExt
    ) { holder ->
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(10f, 15f))
        }
        waitForIdle()
        holder.assertHasFocus()
    }

    /**
     * After clicking the editor, keyboard input must still work.
     *
     * This is the key regression check for issue #2: if focus was not properly
     * restored after the click, ArrowRight would not move the cursor.
     */
    @Test
    fun clickThenArrowRight_cursorMoves() = runEditorTest(
        doc = doc,
        extensions = keymapExt
    ) { holder ->
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(10f, 15f))
        }
        waitForIdle()
        val posBefore = holder.session.state.selection.main.head.value

        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        waitForIdle()
        val posAfter = holder.session.state.selection.main.head.value
        assertTrue(
            posAfter == posBefore + 1,
            "Expected cursor to move right by 1 after click (from $posBefore to " +
                "${posBefore + 1}), but got $posAfter. " +
                "This suggests focus was not properly restored after the click."
        )
    }

    /**
     * Multiple sequential clicks should each preserve focus. Every pointer
     * down calls requestFocus(); a later click must not steal focus back.
     */
    @Test
    fun multipleClicks_eachPreservesFocus() = runEditorTest(
        doc = doc,
        extensions = keymapExt
    ) { holder ->
        repeat(3) { i ->
            onNodeWithTag("KodeMirror").performMouseInput {
                click(Offset(10f + i * 20f, 15f))
            }
            waitForIdle()
            holder.assertHasFocus()
        }
    }

    /**
     * After a drag gesture, the editor should remain focused. Focus is
     * requested on the down, before tap-vs-drag is decided, so a drag focuses
     * the editor just as a tap does and key input must still work.
     */
    @Test
    fun dragThenKeyboard_cursorMoves() = runEditorTest(
        doc = doc,
        extensions = keymapExt
    ) { holder ->
        // Perform a drag to select text
        onNodeWithTag("KodeMirror").performMouseInput {
            moveTo(Offset(10f, 15f))
            press()
            moveTo(Offset(50f, 15f))
            moveTo(Offset(100f, 15f))
            release()
        }
        waitForIdle()
        holder.assertHasFocus()
        // Selection should not be empty after a horizontal drag
        holder.assertSelectionNotEmpty()

        // After the drag, keyboard navigation must work (tests focus is intact)
        // Collapse selection first via programmatic dispatch
        val head = holder.session.state.selection.main.head
        holder.session.dispatch(
            com.monkopedia.kodemirror.state.TransactionSpec(
                selection = com.monkopedia.kodemirror.state.SelectionSpec.CursorSpec(head)
            )
        )
        waitForIdle()

        val posBefore = holder.session.state.selection.main.head.value
        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        waitForIdle()
        val posAfter = holder.session.state.selection.main.head.value
        assertTrue(
            posAfter == posBefore + 1,
            "Expected cursor to move right by 1 after drag (from $posBefore to " +
                "${posBefore + 1}), but got $posAfter. " +
                "This suggests drag did not properly focus the editor."
        )
    }

    /**
     * A tap immediately after a drag must still focus the editor. The original
     * defect (#2) was a `recentlyDragged` flag that made the tap handler
     * early-return past `requestFocus()`; the unified gesture has no such flag,
     * and this holds it that way.
     */
    @Test
    fun clickAfterDrag_focusIsRestored() = runEditorTest(
        doc = doc,
        extensions = keymapExt
    ) { holder ->
        // 1) Drag to select some text
        onNodeWithTag("KodeMirror").performMouseInput {
            moveTo(Offset(10f, 15f))
            press()
            moveTo(Offset(80f, 15f))
            release()
        }
        waitForIdle()

        // 2) Click again on the editor — this should re-focus
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(50f, 15f))
        }
        waitForIdle()
        holder.assertHasFocus()

        // 3) Keyboard must work
        val posBefore = holder.session.state.selection.main.head.value
        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.DirectionLeft)
            keyUp(Key.DirectionLeft)
        }
        waitForIdle()
        val posAfter = holder.session.state.selection.main.head.value
        assertTrue(
            posAfter == posBefore - 1,
            "Expected cursor to move left by 1 after click-after-drag " +
                "(from $posBefore to ${posBefore - 1}), but got $posAfter. " +
                "This suggests the tap after a drag skipped requestFocus()."
        )
    }

    /**
     * Verify that `hasFocus` is set to true on initial auto-focus.
     *
     * KodeMirror has a LaunchedEffect(Unit) that calls requestFocus() when
     * the editor first appears. This ensures keyboard input works without
     * requiring an explicit click.
     */
    @Test
    fun initialAutoFocus_textFieldIsFocused() = runEditorTest(
        doc = doc
    ) { holder ->
        // No click — just wait for the editor to settle
        waitForIdle()
        holder.assertHasFocus()
    }
}
