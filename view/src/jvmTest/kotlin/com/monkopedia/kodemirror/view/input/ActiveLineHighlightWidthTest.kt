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
import com.monkopedia.kodemirror.view.highlightActiveLine
import org.junit.Test

/**
 * Regression guard for #85: in no-wrap mode the active-line (and any
 * line-decoration) background highlight must fill the full viewport width, not
 * stop at the text width.
 *
 * The horizontal-scroll layout work (#20/#64/#68) sized each line's content box
 * to the natural content width (clamped to the widest line), so when content
 * was narrower than the viewport the highlight stopped just past the text. The
 * fix makes the no-wrap content min-width `max(widestLine, viewport)` so the
 * background fills the viewport.
 *
 * With a 2-char document ("hi") in an 800px-wide editor, the text occupies only
 * a few dozen pixels. Pre-fix the highlighted content box measured roughly the
 * text width (well under 100px); post-fix it measures ~the viewport width
 * (close to 800px). The 600px threshold is comfortably between the two.
 */
@OptIn(ExperimentalTestApi::class)
class ActiveLineHighlightWidthTest {

    @Test
    fun activeLineHighlightFillsViewportWidthInNoWrapMode() = runEditorTest(
        doc = "hi",
        extensions = highlightActiveLine,
        width = 800,
        height = 600
    ) { holder ->
        waitForIdle()
        val contentWidth = holder.activeLineContentWidthPx()
        assert(contentWidth > 600) {
            "Expected the active-line highlight content box to fill ~the 800px " +
                "viewport in no-wrap mode, but it was only ${contentWidth}px wide " +
                "(stopping at the text width — regression #85)."
        }
    }
}
