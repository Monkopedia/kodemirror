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
import androidx.compose.ui.test.performMouseInput
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DragSelectionTest {

    private val threeLineDoc = "Line one\nLine two\nLine three"

    @Test
    fun dragWithinSingleLine() = runEditorTest(doc = threeLineDoc) { holder ->
        onNodeWithTag("KodeMirror").performMouseInput {
            moveTo(Offset(20f, 15f))
            press()
            // Move incrementally to exceed drag slop
            moveTo(Offset(40f, 15f))
            moveTo(Offset(60f, 15f))
            moveTo(Offset(100f, 15f))
            moveTo(Offset(200f, 15f))
            release()
        }
        waitForIdle()
        holder.assertSelectionNotEmpty()
        holder.assertCursorOnLine(1)
    }

    @Test
    fun dragAcrossMultipleLines() = runEditorTest(doc = threeLineDoc) { holder ->
        onNodeWithTag("KodeMirror").performMouseInput {
            moveTo(Offset(20f, 15f))
            press()
            moveTo(Offset(50f, 55f))
            release()
        }
        waitForIdle()
        holder.assertSelectionNotEmpty()
    }

    @Test
    fun dragReversed() = runEditorTest(doc = threeLineDoc) { holder ->
        onNodeWithTag("KodeMirror").performMouseInput {
            moveTo(Offset(100f, 15f))
            press()
            moveTo(Offset(20f, 15f))
            release()
        }
        waitForIdle()
        holder.assertSelectionNotEmpty()
    }

    @Test
    fun dragBottomToTop() = runEditorTest(doc = threeLineDoc) { holder ->
        // Drag from line 3 up to line 1 — should create a selection, not
        // jump to selecting the entire document.
        onNodeWithTag("KodeMirror").performMouseInput {
            moveTo(Offset(50f, 55f))
            press()
            moveTo(Offset(50f, 40f))
            moveTo(Offset(50f, 15f))
            release()
        }
        waitForIdle()
        holder.assertSelectionNotEmpty()

        // The anchor should be on line 3 and head on line 1 (or vice versa).
        // Verify the selection doesn't span the entire document.
        val sel = holder.session.state.selection.main
        val doc = holder.session.state.doc
        val selLength = sel.to.value - sel.from.value
        assert(selLength < doc.length) {
            "Selection covers the entire document ($selLength chars out of " +
                "${doc.length}). Expected partial selection from bottom-to-top drag."
        }
    }
}
