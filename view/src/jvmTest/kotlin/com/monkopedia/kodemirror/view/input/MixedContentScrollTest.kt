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
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import org.junit.Test

/**
 * Regression guards for #67: a long line mixed among short lines must still
 * scroll horizontally and show the scrollbar. Short lines, laid out last,
 * previously clobbered the shared [androidx.compose.foundation.ScrollState]
 * maxValue to 0, breaking both behaviors.
 */
@OptIn(ExperimentalTestApi::class)
class MixedContentScrollTest {

    private val mixed = (1..60).joinToString(" ") { "word$it" } +
        "\nshort\nshort\nshort\nshort"

    @Test
    fun scrollbarVisibleWithMixedContent() = runEditorTest(
        doc = mixed,
        width = 300,
        height = 400
    ) {
        onNodeWithTag("KodeMirror_hscroll").assertExists()
    }

    @Test
    fun longLineScrollsAmongShortLines() = runEditorTest(
        doc = mixed,
        width = 300,
        height = 400
    ) { holder ->
        // Click near the right edge of the long line (line 1) before scrolling.
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(250f, 10f))
        }
        waitForIdle()
        val before = holder.session.state.selection.main.head.value

        // Scroll the long line horizontally, then click the same screen point.
        onNodeWithTag("KodeMirror").performMouseInput {
            moveTo(Offset(150f, 10f))
            scroll(600f, ScrollWheel.Horizontal)
        }
        waitForIdle()

        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(250f, 10f))
        }
        waitForIdle()
        val after = holder.session.state.selection.main.head.value

        assert(after > before + 100) {
            "Expected click offset to advance well past the viewport after " +
                "horizontal scroll on a long line among short lines, " +
                "but before=$before after=$after"
        }
    }
}
