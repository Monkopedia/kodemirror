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
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.view.rectangularSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Alt+drag selects a rectangle: one range per line, all over the same columns (#312).
 *
 * #312 found the gesture working in a browser and producing a single range from a Compose UI
 * test, and suspected the harness never delivered Alt. It does: the test's editor had no
 * `EditorState.allowMultipleSelections`, so the state reduced the rectangle to its main range,
 * as it would for any multi-range selection. The browser page uses `basicSetup`, which enables
 * it together with [rectangularSelection]; these tests do the same.
 *
 * The rectangle is asserted by its shape rather than exact offsets, since where a pixel lands
 * depends on the platform's font: three ranges, on lines 1 to 3, each spanning the same columns.
 */
@OptIn(ExperimentalTestApi::class)
class RectangularDragTest {

    private val doc = "abcdefgh\nabcdefgh\nabcdefgh\nabcdefgh"
    private val extensions = ExtensionList(
        listOf(EditorState.allowMultipleSelections.of(true), rectangularSelection)
    )

    /** Drag from inside line 1 to further right on line 3, mouse held throughout. */
    private fun ComposeUiTest.dragDownAndRight() {
        onNodeWithTag("KodeMirror").performMouseInput {
            moveTo(Offset(20f, 15f))
            press()
            moveTo(Offset(40f, 15f))
            moveTo(Offset(60f, 30f))
            moveTo(Offset(60f, 45f))
            moveTo(Offset(60f, 55f))
            release()
        }
        waitForIdle()
    }

    @Test
    fun altDrag_selectsTheSameColumnsOnEachLine() = runEditorTest(
        doc = doc,
        extensions = extensions
    ) { holder ->
        onNodeWithTag("KodeMirror_input").performKeyInput { keyDown(Key.AltLeft) }
        waitForIdle()
        dragDownAndRight()
        onNodeWithTag("KodeMirror_input").performKeyInput { keyUp(Key.AltLeft) }
        waitForIdle()

        val state = holder.session.state
        val ranges = state.selection.ranges
        val shape = ranges.map { r ->
            val line = state.doc.lineAt(r.from)
            Triple(line.number.value, r.from - line.from, r.to - line.from)
        }
        assertEquals(listOf(1, 2, 3), shape.map { it.first }, "one range per line: $shape")
        assertEquals(1, shape.map { it.second to it.third }.distinct().size, "same columns: $shape")
        assertTrue(shape.first().second < shape.first().third, "a non-empty rectangle: $shape")
    }

    @Test
    fun dragWithoutAlt_selectsOneContinuousRange() = runEditorTest(
        doc = doc,
        extensions = extensions
    ) { holder ->
        dragDownAndRight()
        val ranges = holder.session.state.selection.ranges
        assertEquals(1, ranges.size, "a plain drag selects one range: $ranges")
        val line = holder.session.state.doc.lineAt(ranges.single().to)
        assertEquals(3, line.number.value, "the plain drag reaches line 3: $ranges")
    }
}
