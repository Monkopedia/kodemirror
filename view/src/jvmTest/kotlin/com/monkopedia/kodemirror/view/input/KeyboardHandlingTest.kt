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
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asInsert
import com.monkopedia.kodemirror.view.insertAt
import com.monkopedia.kodemirror.view.keymapOf
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class KeyboardHandlingTest {

    private val twoLineDoc = "Hello world\nSecond line"
    private val keymapExt = keymapOf(standardKeymap)

    @Test
    fun arrowRight_movesCursorForward() = runEditorTest(
        doc = twoLineDoc,
        extensions = keymapExt
    ) { holder ->
        // Click to place cursor and focus
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(10f, 15f))
        }
        waitForIdle()
        val posBefore = holder.session.state.selection.main.head.value

        // Send key events to the hidden input field
        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        waitForIdle()
        val posAfter = holder.session.state.selection.main.head.value
        assert(posAfter == posBefore + 1) {
            "Expected cursor to move right by 1 (from $posBefore to ${posBefore + 1}), " +
                "but got $posAfter"
        }
    }

    @Test
    fun arrowDown_movesToNextLine() = runEditorTest(
        doc = twoLineDoc,
        extensions = keymapExt
    ) { holder ->
        // Click on first line
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(10f, 15f))
        }
        waitForIdle()
        holder.assertCursorOnLine(1)

        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        waitForIdle()
        holder.assertCursorOnLine(2)
    }

    @Test
    fun home_movesToLineStart() = runEditorTest(
        doc = twoLineDoc,
        extensions = keymapExt
    ) { holder ->
        // Click somewhere in the middle of line 1
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(80f, 15f))
        }
        waitForIdle()

        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.Home)
            keyUp(Key.Home)
        }
        waitForIdle()
        holder.assertCursorAt(0)
    }

    @Test
    fun end_movesToLineEnd() = runEditorTest(
        doc = twoLineDoc,
        extensions = keymapExt
    ) { holder ->
        // Click at start of line 1
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(10f, 15f))
        }
        waitForIdle()

        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.MoveEnd)
            keyUp(Key.MoveEnd)
        }
        waitForIdle()
        // "Hello world" is 11 chars, so end of line 1 = offset 11
        holder.assertCursorAt(11)
    }

    @Test
    fun arrowUp_movesToPreviousLine() = runEditorTest(
        doc = twoLineDoc,
        extensions = keymapExt
    ) { holder ->
        // Place cursor on line 2 via dispatch
        holder.session.dispatch(
            com.monkopedia.kodemirror.state.TransactionSpec(
                selection = com.monkopedia.kodemirror.state.SelectionSpec
                    .CursorSpec(com.monkopedia.kodemirror.state.DocPos(14))
            )
        )
        waitForIdle()
        holder.assertCursorOnLine(2)

        // Focus the input
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(10f, 15f))
        }
        waitForIdle()

        // Re-set cursor on line 2 (click moved it)
        holder.session.dispatch(
            com.monkopedia.kodemirror.state.TransactionSpec(
                selection = com.monkopedia.kodemirror.state.SelectionSpec
                    .CursorSpec(com.monkopedia.kodemirror.state.DocPos(14))
            )
        )
        waitForIdle()
        holder.assertCursorOnLine(2)

        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.DirectionUp)
            keyUp(Key.DirectionUp)
        }
        waitForIdle()
        holder.assertCursorOnLine(1)
    }

    @Test
    fun ctrlX_cutsSelectedText() = runEditorTest(
        doc = "Hello World",
        extensions = keymapExt
    ) { holder ->
        // Focus
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(10f, 15f))
        }
        waitForIdle()

        // Select "Hello" (positions 0-5)
        holder.session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.EditorSelectionSpec(
                    EditorSelection.single(DocPos(0), DocPos(5))
                )
            )
        )
        waitForIdle()
        holder.assertSelectionNotEmpty()

        // Press Ctrl+X
        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.X)
            keyUp(Key.X)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        // "Hello" should have been cut
        holder.assertDoc(" World")
    }

    @Test
    fun insertAt_advancesCursor() = runEditorTest(
        doc = "Hello",
        extensions = keymapExt
    ) { holder ->
        // Focus
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(10f, 15f))
        }
        waitForIdle()

        // Place cursor at position 5 (end of "Hello")
        holder.session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(DocPos(5))
            )
        )
        waitForIdle()
        holder.assertCursorAt(5)

        // Insert a tab via insertAt — cursor should advance past it
        holder.session.insertAt(DocPos(5), "\t")
        waitForIdle()
        holder.assertDoc("Hello\t")
        holder.assertCursorAt(6)
    }

    @Test
    fun paste_advancesCursorPastInsertedText() = runEditorTest(
        doc = "ABCDEF",
        extensions = keymapExt
    ) { holder ->
        // Focus
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(10f, 15f))
        }
        waitForIdle()

        // Place cursor at position 3
        holder.session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(DocPos(3))
            )
        )
        waitForIdle()
        holder.assertCursorAt(3)

        // Simulate what clipboardPaste does: insert text with explicit cursor
        val pasteText = "XYZ"
        val sel = holder.session.state.selection.main
        holder.session.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = sel.from,
                    to = sel.to,
                    insert = pasteText.asInsert()
                ),
                selection = SelectionSpec.CursorSpec(
                    DocPos(sel.from.value + pasteText.length)
                )
            )
        )
        waitForIdle()
        holder.assertDoc("ABCXYZDEF")
        holder.assertCursorAt(6)
    }

    @Test
    fun arrowLeft_movesCursorBackward() = runEditorTest(
        doc = twoLineDoc,
        extensions = keymapExt
    ) { holder ->
        // Place cursor at position 3
        holder.session.dispatch(
            com.monkopedia.kodemirror.state.TransactionSpec(
                selection = com.monkopedia.kodemirror.state.SelectionSpec
                    .CursorSpec(com.monkopedia.kodemirror.state.DocPos(3))
            )
        )
        waitForIdle()

        // Focus the input
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(10f, 15f))
        }
        waitForIdle()

        // Re-set cursor at position 3
        holder.session.dispatch(
            com.monkopedia.kodemirror.state.TransactionSpec(
                selection = com.monkopedia.kodemirror.state.SelectionSpec
                    .CursorSpec(com.monkopedia.kodemirror.state.DocPos(3))
            )
        )
        waitForIdle()
        holder.assertCursorAt(3)

        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.DirectionLeft)
            keyUp(Key.DirectionLeft)
        }
        waitForIdle()
        holder.assertCursorAt(2)
    }
}
