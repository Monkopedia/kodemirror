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
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToIndex
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ScrollClickTest {

    private val fiftyLineDoc = (1..50).joinToString("\n") { "Line $it content here" }

    @Test
    fun clickAfterScrollDown() = runEditorTest(
        doc = fiftyLineDoc,
        height = 300
    ) { holder ->
        // Scroll the LazyColumn programmatically to index 20
        onNode(hasScrollToIndexAction()).performScrollToIndex(20)
        waitForIdle()

        // Click on a visible line after scrolling
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(50f, 15f))
        }
        waitForIdle()

        val cursorLine = holder.session.state.doc.lineAt(
            holder.session.state.selection.main.head
        ).number.value
        assert(cursorLine > 1) {
            "Expected cursor past line 1 after scrolling, but was on line $cursorLine"
        }
    }

    @Test
    fun clickOnLastVisibleLine() = runEditorTest(
        doc = fiftyLineDoc,
        height = 300
    ) { holder ->
        // Scroll the LazyColumn to near the end
        onNode(hasScrollToIndexAction()).performScrollToIndex(45)
        waitForIdle()

        // Click near the bottom of the visible area
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(50f, 280f))
        }
        waitForIdle()

        val cursorLine = holder.session.state.doc.lineAt(
            holder.session.state.selection.main.head
        ).number.value
        assert(cursorLine > 30) {
            "Expected cursor near end of document after scrolling to bottom, " +
                "but was on line $cursorLine"
        }
    }
}
