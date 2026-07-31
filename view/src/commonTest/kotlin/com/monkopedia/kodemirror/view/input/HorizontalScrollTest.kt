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
import com.monkopedia.kodemirror.view.lineWrapping
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HorizontalScrollTest {

    private val longLine = (1..60).joinToString(" ") { "word$it" }

    /**
     * Not run on macOS native: the test harness cannot deliver a wheel event there, because
     * Compose's `MacOsScrollConfig` reads the scroll delta off the underlying AppKit `NSEvent`
     * and a synthesized one carries none. It is a harness limitation, not an editor or a
     * Compose-on-macOS one — real wheel and trackpad gestures scroll normally, and iOS, whose
     * `UiKitScrollConfig` needs no native event, runs this test. See `view/build.gradle.kts`.
     */
    @Test
    fun wheelScrollAdvancesClickOffset() = runEditorTest(
        doc = longLine,
        width = 300
    ) { holder ->
        // Click near the right edge before scrolling.
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(250f, 15f))
        }
        waitForIdle()
        val before = holder.session.state.selection.main.head.value

        // Scroll horizontally with the wheel, then click the same screen point. The wheel
        // delta needed to travel a given number of pixels differs per platform, so this
        // scrolls until the content stops moving rather than assuming one event suffices.
        wheelScrollRightToEnd(holder, Offset(150f, 15f))

        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(250f, 15f))
        }
        waitForIdle()
        val after = holder.session.state.selection.main.head.value

        assertTrue(
            after > before + 50,
            "Expected click offset to advance after horizontal scroll, " +
                "but before=$before after=$after"
        )
    }

    @Test
    fun scrollbarVisibleOnOverflow() = runEditorTest(
        doc = longLine,
        width = 300
    ) {
        onNodeWithTag("KodeMirror_hscroll").assertExists()
    }

    @Test
    fun scrollbarHiddenWhenContentFits() = runEditorTest(
        doc = "short",
        width = 800
    ) {
        onNodeWithTag("KodeMirror_hscroll").assertDoesNotExist()
    }

    @Test
    fun scrollbarHiddenWhenWrapping() = runEditorTest(
        doc = longLine,
        extensions = lineWrapping,
        width = 300
    ) {
        onNodeWithTag("KodeMirror_hscroll").assertDoesNotExist()
    }
}
