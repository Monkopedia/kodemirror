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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Touch-pointer coverage for the editor's unified tap/drag gesture.
 *
 * The mouse equivalents live in [DragSelectionTest], [ClickTargetingTest] and
 * [ScrollClickTest]. This file exists because the gesture consumes the pointer
 * down (#303) — on wasmJs that is what makes Compose call `preventDefault()`,
 * which stops the browser moving DOM focus to the `tabindex=0` canvas and
 * blurring the backing textarea the soft keyboard is raised for. Consuming a
 * down changes what sibling and child gesture handlers observe, and touch is
 * the pointer type where that matters most: the same vertical drag is both a
 * text selection and a list scroll depending on who wins.
 */
@OptIn(ExperimentalTestApi::class)
class TouchGestureTest {

    private val threeLineDoc = "Line one\nLine two\nLine three"
    private val fiftyLineDoc = (1..50).joinToString("\n") { "Line $it content here" }

    @Test
    fun touchTapPlacesCursor() = runEditorTest(doc = threeLineDoc) { holder ->
        onNodeWithTag("KodeMirror").performTouchInput {
            down(Offset(40f, 35f))
            up()
        }
        waitForIdle()
        holder.assertCursorOnLine(2)
    }

    @Test
    fun touchTapOnFirstLinePlacesCursor() = runEditorTest(doc = threeLineDoc) { holder ->
        onNodeWithTag("KodeMirror").performTouchInput {
            down(Offset(40f, 8f))
            up()
        }
        waitForIdle()
        holder.assertCursorOnLine(1)
    }

    /**
     * A horizontal touch drag that does *not* begin with a long press still
     * belongs to the horizontal scroll container the line content sits in: the
     * gesture falls back to placing the cursor at the down position and selects
     * nothing. That was the whole of the behaviour before #311 and is now the
     * deliberate half of it — dragging a finger over a line too wide for the
     * viewport has to scroll it, since touch has no wheel. Selecting with a
     * finger goes through [longPressThenTouchDragSelectsAcrossLines] instead.
     */
    @Test
    fun horizontalTouchDragPlacesCursorAndLeavesDocument() =
        runEditorTest(doc = threeLineDoc) { holder ->
            onNodeWithTag("KodeMirror").performTouchInput {
                down(Offset(20f, 8f))
                moveTo(Offset(60f, 8f))
                moveTo(Offset(120f, 8f))
                moveTo(Offset(200f, 8f))
                up()
            }
            waitForIdle()
            holder.assertDoc(threeLineDoc)
            holder.assertCursorOnLine(1)
            assertTrue(
                holder.session.state.selection.main.empty,
                "Expected a horizontal touch drag to leave the selection empty, but got " +
                    "${holder.session.state.selection.main}"
            )
        }

    /**
     * A vertical touch drag over a document taller than the viewport must
     * scroll the line list. The LazyColumn is a child of the node carrying the
     * editor gesture, so it sees the pointer first; the editor consuming the
     * down must not take that away.
     */
    @Test
    fun verticalTouchDragScrollsDocument() = runEditorTest(
        doc = fiftyLineDoc,
        height = 300
    ) { holder ->
        val before = holder.firstVisibleIndex()
        onNodeWithTag("KodeMirror").performTouchInput {
            down(Offset(400f, 250f))
            var y = 250f
            while (y > 30f) {
                y -= 20f
                moveTo(Offset(400f, y))
            }
            up()
        }
        waitForIdle()
        val after = holder.firstVisibleIndex()
        assertTrue(
            after > before,
            "Expected a vertical touch drag to scroll the line list " +
                "(firstVisibleIndex $before -> $after)"
        )
    }

    /**
     * A long press followed by a touch drag selects text, like CodeMirror 6 on
     * mobile. The drag is claimed away from the enclosing scroll containers only
     * once the long press has fired, so a plain drag still scrolls (#311).
     *
     * The coordinates are chosen to be font-metric independent: x=8 is just
     * inside the content's 6dp start padding, so every press resolves to the
     * first character of its line, and the assertion is an exact anchor/head
     * pair rather than "something is selected".
     */
    @Test
    fun longPressThenTouchDragSelectsAcrossLines() = runEditorTest(doc = threeLineDoc) { holder ->
        onNodeWithTag("KodeMirror").performTouchInput {
            down(Offset(8f, 8f))
            // Hold still past the long-press timeout, emitting moves the
            // way `longClick` does so the gesture stays alive.
            repeat(10) {
                advanceEventTime(viewConfiguration.longPressTimeoutMillis / 5)
                moveTo(Offset(8f, 8f))
            }
            moveTo(Offset(8f, 35f))
            moveTo(Offset(8f, 55f))
            up()
        }
        waitForIdle()
        val sel = holder.session.state.selection.main
        assertEquals(
            0,
            sel.anchor.value,
            "Expected the selection anchored at the long-pressed position, " +
                "but got $sel"
        )
        assertEquals(
            18,
            sel.head.value,
            "Expected the selection head at the start of line 3, but got $sel"
        )
    }

    /**
     * The same long-press drag over a document taller than the viewport selects
     * text and leaves the line list where it was. This is the half of #311 the
     * three-line case cannot show: there is nothing there to scroll, so the
     * gesture would look the same whether or not the editor actually claimed it.
     * Here the LazyColumn takes this very drag when it is not preceded by a long
     * press — the same three moves without the hold scroll it twelve lines and
     * leave the caret at 60 — so both the exact anchor/head pair and the unmoved
     * first-visible index say the editor took the gesture away from it.
     */
    @Test
    fun longPressThenTouchDragSelectsWithoutScrollingTheList() = runEditorTest(
        doc = fiftyLineDoc,
        height = 300
    ) { holder ->
        onNodeWithTag("KodeMirror").performTouchInput {
            down(Offset(8f, 55f))
            repeat(10) {
                advanceEventTime(viewConfiguration.longPressTimeoutMillis / 5)
                moveTo(Offset(8f, 55f))
            }
            moveTo(Offset(8f, 40f))
            moveTo(Offset(8f, 25f))
            moveTo(Offset(8f, 8f))
            up()
        }
        waitForIdle()
        val sel = holder.session.state.selection.main
        assertEquals(
            40 to 0,
            sel.anchor.value to sel.head.value,
            "Expected a selection anchored at the start of line 3 and headed at " +
                "the start of line 1, but got $sel"
        )
        assertEquals(
            0,
            holder.firstVisibleIndex(),
            "Expected the line list not to scroll while the long-press drag " +
                "selected, but the first visible item moved"
        )
    }
}
