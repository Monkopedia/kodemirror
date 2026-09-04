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
     * A horizontal touch drag over the editor does not extend a selection: the
     * line content sits in a horizontal scroll container, which claims the drag
     * before the editor's gesture sees it, and the gesture falls back to placing
     * the cursor at the down position. That is pre-existing behaviour, measured
     * identical before and after the #303 pointer-down consume, and it is
     * characterised here so a change to it is caught rather than shipped —
     * touch drag-selection itself is tracked separately.
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
}
