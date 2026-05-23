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
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import org.junit.Test

/**
 * Regression tests for scroll-into-view on dispatched selection jumps
 * (issues #58 and #33). A transaction carrying `scrollIntoView = true` must
 * scroll the line list so the primary selection head becomes visible, even
 * when the target lands far outside the current viewport.
 */
@OptIn(ExperimentalTestApi::class)
class ScrollIntoViewTest {

    private val hundredLineDoc = (1..100).joinToString("\n") { "Line $it content here" }

    /** Document offset of the start of the given 1-based line. */
    private fun lineStart(state: EditorState, line: Int): Int =
        state.doc.line(LineNumber(line)).from.value

    @Test
    fun jumpDown_withScrollIntoView_revealsOffscreenTarget() = runEditorTest(
        doc = hundredLineDoc,
        height = 300
    ) { holder ->
        // Initially the viewport is at the top.
        assert(holder.firstVisibleIndex() == 0) {
            "Expected to start at top, but firstVisible=${holder.firstVisibleIndex()}"
        }

        // Jump the selection to a line far below the fold WITH scrollIntoView.
        val target = lineStart(holder.session.state, 80)
        holder.session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(DocPos(target)),
                scrollIntoView = true
            )
        )
        waitForIdle()

        // The viewport must have scrolled down to reveal the target line.
        val first = holder.firstVisibleIndex()
        assert(first > 0) {
            "Expected viewport to scroll down to reveal line 80, " +
                "but firstVisible=$first"
        }
        // The target line (index 79) should now be within the visible window.
        assert(holder.isIndexVisible(79)) {
            "Expected target line index 79 to be visible after scrollIntoView, " +
                "visible range first=$first"
        }
    }

    @Test
    fun jumpUp_withScrollIntoView_revealsTargetAbove() = runEditorTest(
        doc = hundredLineDoc,
        height = 300
    ) { holder ->
        // Scroll near the bottom first.
        onNode(hasScrollToIndexAction()).performScrollToIndex(90)
        waitForIdle()
        assert(holder.firstVisibleIndex() > 50) {
            "Expected to be scrolled down, firstVisible=${holder.firstVisibleIndex()}"
        }

        // Jump back up to line 5 with scrollIntoView.
        val target = lineStart(holder.session.state, 5)
        holder.session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(DocPos(target)),
                scrollIntoView = true
            )
        )
        waitForIdle()

        assert(holder.isIndexVisible(4)) {
            "Expected target line index 4 to be visible after scrolling up, " +
                "first=${holder.firstVisibleIndex()}"
        }
    }

    @Test
    fun jumpDown_withoutScrollIntoView_doesNotScroll() = runEditorTest(
        doc = hundredLineDoc,
        height = 300
    ) { holder ->
        val before = holder.firstVisibleIndex()
        // Move selection far down but WITHOUT requesting scrollIntoView.
        val target = lineStart(holder.session.state, 80)
        holder.session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(DocPos(target))
            )
        )
        waitForIdle()
        assert(holder.firstVisibleIndex() == before) {
            "Viewport should not move without scrollIntoView, " +
                "before=$before after=${holder.firstVisibleIndex()}"
        }
    }
}
