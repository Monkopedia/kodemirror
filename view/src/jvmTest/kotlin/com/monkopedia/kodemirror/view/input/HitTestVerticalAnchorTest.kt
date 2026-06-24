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
 */
package com.monkopedia.kodemirror.view.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import com.monkopedia.kodemirror.state.LineNumber
import org.junit.Test

/**
 * Regression guard for #169: the click hit-test must use the same vertical
 * anchor as the rendered glyphs.
 *
 * The LazyColumn renders its content shifted down by `contentTopPadding`, but
 * `LazyListItemInfo.offset` starts at y=0. `posFromVisibleItems` therefore has
 * to add `contentTopPadding` when mapping a pointer-y to a line top; omitting it
 * shifts the whole hit-test grid ~4px above the glyphs, so a click near the
 * bottom of a line resolves to the line *below* it (the symptom in #169).
 *
 * A click at a line's vertical centre lands correctly even with the bug (the
 * centre is safely inside the shifted grid), which is why the existing
 * [ClickTargetingTest] cases did not catch it. These tests click in the thin
 * band just below a glyph's bottom edge — derived from `coordsAtPos` so it does
 * not hardcode the line metrics — where the missing padding flips the result.
 */
@OptIn(ExperimentalTestApi::class)
class HitTestVerticalAnchorTest {

    private val fiveLineDoc = "Line one\nLine two\nLine three\nLine four\nLine five"

    private fun clickJustBelowGlyphResolvesToSameLine(line: Int) =
        runEditorTest(doc = fiveLineDoc) { holder ->
            val from = holder.session.state.doc.line(LineNumber(line)).from.value
            val rect = holder.session.coordsAtPos(from, 1)
                ?: error("coordsAtPos returned null for line $line start")
            // Just inside the inter-line gap below this line's glyph: clearly
            // below the glyph bottom, but above the next line's glyph. With the
            // contentTopPadding omitted this falls into the next line's grid row.
            onNodeWithTag("KodeMirror").performMouseInput {
                click(Offset(rect.left + 4f, rect.bottom + 1.5f))
            }
            waitForIdle()
            holder.assertCursorOnLine(line)
        }

    @Test
    fun clickBelowGlyphBottom_line2_staysOnLine2() =
        clickJustBelowGlyphResolvesToSameLine(2)

    @Test
    fun clickBelowGlyphBottom_line3_staysOnLine3() =
        clickJustBelowGlyphResolvesToSameLine(3)

    @Test
    fun clickBelowGlyphBottom_line4_staysOnLine4() =
        clickJustBelowGlyphResolvesToSameLine(4)
}
