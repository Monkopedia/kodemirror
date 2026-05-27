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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ClickTargetingTest {

    private val threeLineDoc = "Line one\nLine two\nLine three"

    @Test
    fun clickOnFirstLine_noGutters() = runEditorTest(doc = threeLineDoc) { holder ->
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(50f, 15f))
        }
        waitForIdle()
        holder.assertCursorOnLine(1)
    }

    @Test
    fun clickOnSecondLine_noGutters() = runEditorTest(doc = threeLineDoc) { holder ->
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(50f, 35f))
        }
        waitForIdle()
        holder.assertCursorOnLine(2)
    }

    @Test
    fun clickOnThirdLine_noGutters() = runEditorTest(doc = threeLineDoc) { holder ->
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(50f, 55f))
        }
        waitForIdle()
        holder.assertCursorOnLine(3)
    }

    @Test
    fun clickOnFirstLine_withGutters() = runEditorTest(
        doc = threeLineDoc,
        withGutters = true
    ) { holder ->
        // Click past the gutter area (x=150 should be well into content)
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(150f, 15f))
        }
        waitForIdle()
        holder.assertCursorOnLine(1)
    }

    @Test
    fun clickOnSecondLine_withGutters() = runEditorTest(
        doc = threeLineDoc,
        withGutters = true
    ) { holder ->
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(150f, 35f))
        }
        waitForIdle()
        holder.assertCursorOnLine(2)
    }

    @Test
    fun clickPastEndOfLine() = runEditorTest(doc = "Hi\nWorld") { holder ->
        // Click far right on the first line — should land at end of line 1
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(700f, 15f))
        }
        waitForIdle()
        holder.assertCursorOnLine(1)
    }

    @Test
    fun clickFarColumnOnLongLine_noWrapDefault() = runEditorTest(
        // A single long line wider than the 400px viewport. In the no-wrap
        // default the line extends horizontally, so a click well into the
        // line must resolve to a column past the first handful of characters
        // (the bug in #20 clipped clicks to the first visible region).
        doc = (1..40).joinToString(" ") { "word$it" },
        width = 400
    ) { holder ->
        onNodeWithTag("KodeMirror").performMouseInput {
            // Click near the right edge of the viewport on the first line.
            click(Offset(360f, 15f))
        }
        waitForIdle()
        holder.assertCursorOnLine(1)
        val col = holder.session.state.selection.main.head.value
        assert(col > 10) {
            "Expected click near right edge to resolve to a column past the " +
                "start of the line, but landed at offset $col"
        }
    }

    @Test
    fun clickBelowLastLine() = runEditorTest(doc = "A\nB") { holder ->
        // Click well below all content — should land on last line
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(50f, 500f))
        }
        waitForIdle()
        val line = holder.session.state.doc.lineAt(
            holder.session.state.selection.main.head
        )
        assert(line.number.value >= 2) {
            "Expected cursor on last line but was on line ${line.number.value}"
        }
    }
}
