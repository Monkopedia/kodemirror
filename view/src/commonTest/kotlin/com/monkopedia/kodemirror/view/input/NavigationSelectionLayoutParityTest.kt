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

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import com.monkopedia.kodemirror.view.PARITY_DOC
import com.monkopedia.kodemirror.view.assertParity
import com.monkopedia.kodemirror.view.assertParityCursorAt
import com.monkopedia.kodemirror.view.parityExtensions
import com.monkopedia.kodemirror.view.press
import kotlin.test.Test

/**
 * The layout-dependent twins for `gap-analysis/tests/navigation.spec.ts` and
 * `gap-analysis/tests/selection.spec.ts`.
 *
 * These four sequences cannot run headless. Vertical motion resolves through
 * the view's geometry — `moveVertically` steps by *visual* row and returns its
 * input unchanged when `coordsAtPos` has nothing to report — so `ArrowUp` /
 * `ArrowDown` in a session with no layout silently do nothing, and a twin
 * written that way would pass while proving the opposite of what it claims.
 * Everything here therefore runs inside [runEditorTest], whose pinned
 * `Density(1f, 1f)` frame gives every target the same 800x600 viewport.
 *
 * The deciding factor is the **setup sequence, not the assertion**:
 * `column memory across lines` asserts nothing but a column, and
 * `Shift+Down - extend selection down` asserts nothing but two offsets, yet
 * both reach their result through an `ArrowDown` and so belong here.
 *
 * The fixture's longest line is 46 characters, well inside 800px, so visual
 * rows and logical lines coincide and the captured CM6 offsets apply directly.
 *
 * Expectations are literals captured from real CodeMirror 6
 * (`gap-analysis/fixtures/cm6-navigation-selection-expectations.json`); the
 * state-transition twins live in `../NavigationSelectionParityTest.kt`. See
 * `docs/testing-strategy.md`.
 */
@OptIn(ExperimentalTestApi::class)
class NavigationSelectionLayoutParityTest {

    /**
     * Press a key and let the editor re-lay-out before the next one, since the
     * next key's result may depend on the geometry this one changed.
     */
    private fun ComposeUiTest.press(holder: SessionHolder, key: String, times: Int = 1) {
        repeat(times) {
            holder.session.press(key)
            waitForIdle()
        }
    }

    private fun parityTest(block: ComposeUiTest.(SessionHolder) -> Unit) =
        runEditorTest(doc = PARITY_DOC, extensions = parityExtensions, block = block)

    // ─── navigation.spec.ts ─────────────────────────────────────────────────

    // parity: gap-analysis/tests/navigation.spec.ts "arrow keys - down"
    @Test
    fun arrowKeysDown() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown")
        holder.session.assertParityCursorAt(pos = 22, line = 2, col = 0)
    }

    // parity: gap-analysis/tests/navigation.spec.ts "arrow keys - up"
    @Test
    fun arrowKeysUp() = parityTest { holder ->
        press(holder, "Ctrl-End")
        press(holder, "ArrowUp")
        // Document end is line 10 column 1; the goal column survives the step
        // up onto line 9.
        holder.session.assertParityCursorAt(pos = 181, line = 9, col = 1)
    }

    // parity: gap-analysis/tests/navigation.spec.ts "column memory across lines"
    @Test
    fun columnMemoryAcrossLines() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "End")
        press(holder, "ArrowDown")
        press(holder, "ArrowDown")
        // Column 21 is remembered across both steps: line 2 is 23 characters
        // and line 3 is 25, so neither truncates the goal column.
        holder.session.assertParityCursorAt(pos = 67, line = 3, col = 21)
    }

    // ─── selection.spec.ts ──────────────────────────────────────────────────

    // parity: gap-analysis/tests/selection.spec.ts "Shift+Down - extend selection down"
    @Test
    fun shiftDownExtendSelectionDown() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "Shift-ArrowDown")
        holder.session.assertParity(anchor = 0, head = 22)
    }
}
