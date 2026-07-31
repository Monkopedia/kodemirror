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
 * Tests for focus management after canvas/editor clicks.
 *
 * # Issue #2: Canvas click can break keyboard input
 *
 * ## Root cause analysis
 *
 * The editor Box has THREE separate `pointerInput` modifiers:
 *   1. `detectTapGestures`  — calls `focusRequester.requestFocus()` on tap
 *   2. `detectDragGestures` — calls `focusRequester.requestFocus()` on drag start
 *   3. `awaitPointerEventScope` — hover tracking (no focus involvement)
 *
 * Each `pointerInput` block runs in its own coroutine and competes for pointer
 * events. When the user clicks (press + quick release), Compose's gesture
 * detection applies "slop": a pointer move below ~18px is still considered a
 * tap, but both coroutines receive the DOWN event simultaneously.
 *
 * On Desktop/JVM, `detectDragGestures` requires the pointer to move past the
 * drag slop threshold before it starts consuming events, so a pure click
 * normally reaches `detectTapGestures` intact.
 *
 * On **wasmJs**, the situation is more complex:
 *   - `platformFocusInput()` calls `platformFocusCanvas()`, which focuses the
 *     `<canvas>` element in Skiko's shadow DOM. A canvas click can momentarily
 *     move *browser* DOM focus to the canvas, away from any hidden textarea.
 *   - If `detectTapGestures` does not fire (e.g., drag detector consumed the
 *     event before the tap completed), `focusRequester.requestFocus()` is never
 *     called, leaving the BasicTextField without Compose focus.
 *   - The `recentlyDragged` flag (set in `onDragStart`, cleared in tap) adds
 *     another wrinkle: if drag fires first and sets `recentlyDragged = true`,
 *     the subsequent tap callback early-returns and skips `requestFocus()`.
 *
 * ## Why this test can/cannot reproduce the bug on JVM
 *
 * On JVM (Desktop Compose), `platformFocusInput()` is a no-op. Focus is
 * managed entirely by Compose's `FocusRequester`. The drag/tap race exists in
 * the pointer-input pipeline, but in the Desktop test environment Compose
 * correctly hands a click (no slop exceeded) to `detectTapGestures`, so
 * `requestFocus()` fires and the BasicTextField is focused.
 *
 * **The bug is wasmJs-specific**: it only manifests when:
 *   a) the browser moves DOM focus to the canvas on click, AND
 *   b) the Compose FocusRequester doesn't re-focus the hidden textarea, AND
 *   c) the document-level key handler (`platformRegisterKeyHandler`) does not
 *      catch keys because they target the canvas rather than the body.
 *
 * The tests below verify the *correct* JVM behavior (click → focus → keyboard
 * works) and document the wasmJs-specific failure mode as comments.
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
     * A single click on the editor canvas should give Compose focus to the
     * hidden BasicTextField so that subsequent key events are routed to it.
     *
     * On JVM: passes — detectTapGestures fires and calls requestFocus().
     * On wasmJs: may fail — canvas click may move DOM focus away from the
     * textarea; see class KDoc for details.
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
     * Multiple sequential clicks should each preserve focus.
     *
     * Each click calls detectTapGestures → requestFocus(). Clicking the second
     * time should NOT steal focus from the BasicTextField.
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
     * After a drag gesture, the editor should remain focused.
     *
     * detectDragGestures.onDragStart calls requestFocus(), so a drag should
     * also focus the editor. Subsequent key input must work.
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
     * Tap immediately after a drag must NOT skip focus due to `recentlyDragged`.
     *
     * In KodeMirror.kt, the tap handler checks `recentlyDragged` and early-
     * returns (skipping requestFocus) if it's true. The drag's `onDragEnd`
     * callback sets `recentlyDragged = false`, so a fresh tap after drag
     * completion should still call requestFocus().
     *
     * If the ordering is wrong (tap fires before onDragEnd clears the flag),
     * the tap skips requestFocus and focus is lost.
     *
     * On JVM, `recentlyDragged` is cleared by `onDragEnd` before the next tap
     * can fire, so this passes. The risk is on wasmJs where timing differs.
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
                "This suggests `recentlyDragged` was still true when the tap fired, " +
                "causing requestFocus() to be skipped."
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
