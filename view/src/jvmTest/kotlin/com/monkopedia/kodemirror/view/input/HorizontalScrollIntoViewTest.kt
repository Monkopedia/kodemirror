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

import androidx.compose.ui.test.ExperimentalTestApi
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.lineWrapping
import org.junit.Test

/**
 * Regression tests for horizontal scroll-into-view in no-wrap mode (#69). A
 * transaction carrying `scrollIntoView = true` must scroll the content area
 * horizontally so the primary selection head stays visible when it moves past
 * the right or left content edge — the horizontal analog of [ScrollIntoViewTest].
 *
 * Wrapped lines never overflow horizontally, so wrap mode must not scroll.
 */
@OptIn(ExperimentalTestApi::class)
class HorizontalScrollIntoViewTest {

    // A single very long line that comfortably overflows a narrow viewport.
    private val longLine = "word ".repeat(200).trim()

    private fun moveCursor(holder: SessionHolder, offset: Int) {
        holder.session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(DocPos(offset)),
                scrollIntoView = true
            )
        )
    }

    @Test
    fun cursorJumpRightScrollsViewport() = runEditorTest(
        doc = longLine,
        width = 300
    ) { holder ->
        // Cursor starts at the document start; nothing is scrolled.
        moveCursor(holder, 0)
        waitForIdle()
        val before = holder.horizontalScrollPx()
        assert(before == 0) {
            "Expected no horizontal scroll at line start, but was $before"
        }

        // Jump the caret near the end of the long line.
        moveCursor(holder, longLine.length)
        waitForIdle()
        val after = holder.horizontalScrollPx()
        // The far-right caret must pull the viewport substantially right.
        assert(after > 200) {
            "Expected viewport to scroll right (>200px) to reveal the " +
                "far-right caret, but horizontalScrollPx=$after (before=$before)"
        }
    }

    @Test
    fun cursorJumpBackLeftScrollsViewportBack() = runEditorTest(
        doc = longLine,
        width = 300
    ) { holder ->
        // First scroll right by placing the caret at the end of the line.
        moveCursor(holder, longLine.length)
        waitForIdle()
        val scrolledRight = holder.horizontalScrollPx()
        assert(scrolledRight > 200) {
            "Precondition: expected viewport scrolled right, but was $scrolledRight"
        }

        // Now jump back to the start of the line.
        moveCursor(holder, 0)
        waitForIdle()
        val backLeft = holder.horizontalScrollPx()
        // The viewport must return to (approximately) the start.
        assert(backLeft <= 24) {
            "Expected viewport to scroll back to line start (<=24px), " +
                "but horizontalScrollPx=$backLeft (was $scrolledRight)"
        }
    }

    @Test
    fun cursorJumpRightScrollsViewportWithTabs() = runEditorTest(
        doc = "\t\t\t" + longLine,
        width = 300
    ) { holder ->
        // Leading tabs expand the rendered text; the tab-offset mapping must
        // still resolve the caret x correctly so the viewport follows it.
        moveCursor(holder, 0)
        waitForIdle()
        assert(holder.horizontalScrollPx() == 0)

        moveCursor(holder, longLine.length + 3)
        waitForIdle()
        val after = holder.horizontalScrollPx()
        assert(after > 200) {
            "Expected viewport to scroll right past tab-expanded content, " +
                "but horizontalScrollPx=$after"
        }
    }

    @Test
    fun wrapModeDoesNotHorizontalScroll() = runEditorTest(
        doc = longLine,
        extensions = lineWrapping,
        width = 300
    ) { holder ->
        moveCursor(holder, longLine.length)
        waitForIdle()
        val after = holder.horizontalScrollPx()
        assert(after == 0) {
            "Wrap mode must never scroll horizontally, but was $after"
        }
    }
}
