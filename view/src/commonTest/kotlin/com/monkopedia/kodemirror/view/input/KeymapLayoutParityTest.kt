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
import com.monkopedia.kodemirror.view.PARITY_LINE_1
import com.monkopedia.kodemirror.view.PARITY_LINE_2
import com.monkopedia.kodemirror.view.PARITY_LINE_3
import com.monkopedia.kodemirror.view.assertParity
import com.monkopedia.kodemirror.view.assertParityCursor
import com.monkopedia.kodemirror.view.cursorPos
import com.monkopedia.kodemirror.view.parityDocWithHead
import com.monkopedia.kodemirror.view.parityExtensions
import com.monkopedia.kodemirror.view.press
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The layout-dependent half of the `keymap-commands.spec.ts` twins.
 *
 * These 28 sequences cannot run headless. Vertical motion resolves through the
 * view's geometry — `moveVertically` steps by *visual* row and returns its
 * input unchanged when `coordsAtPos` has nothing to report — so `ArrowUp` /
 * `ArrowDown` in a session with no layout silently do nothing, and a twin
 * written that way would pass while proving the opposite of what it claims.
 * Everything here therefore runs inside [runEditorTest], whose pinned
 * `Density(1f, 1f)` frame gives every target the same 800x600 viewport.
 *
 * The fixture's longest line is 46 characters, well inside 800px, so visual
 * rows and logical lines coincide and the captured CM6 offsets apply directly.
 *
 * Expectations are literals captured from real CodeMirror 6
 * (`gap-analysis/fixtures/cm6-keymap-expectations.json`); the state-transition
 * twins live in `../KeymapParityTest.kt`. See `docs/testing-strategy.md`.
 */
@OptIn(ExperimentalTestApi::class)
class KeymapLayoutParityTest {

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

    // ─── Standard: arrow keys ───────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "ArrowUp moves cursor up"
    @Test
    fun arrowUpMovesCursorUp() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 3)
        press(holder, "ArrowUp")
        holder.session.assertParityCursor(46)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "ArrowDown moves cursor down"
    @Test
    fun arrowDownMovesCursorDown() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown")
        holder.session.assertParityCursor(22)
    }

    // ─── Standard: Home/End ─────────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Home moves to line start"
    @Test
    fun homeMovesToLineStart() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 2)
        press(holder, "ArrowRight", times = 5)
        press(holder, "Home")
        // Smart home: the first non-whitespace character, not column 0.
        holder.session.assertParityCursor(50)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "End moves to line end"
    @Test
    fun endMovesToLineEnd() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 2)
        press(holder, "End")
        holder.session.assertParityCursor(71)
    }

    // ─── Standard: PageUp/PageDown ──────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "PageDown moves cursor down a page"
    @Test
    fun pageDownMovesCursorDownAPage() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        val before = holder.session.cursorPos
        press(holder, "PageDown")
        val after = holder.session.cursorPos
        // A page is however many lines fit in the viewport, which is not the
        // same number in CM6's DOM and in a Compose canvas. The Playwright
        // counterpart asserts only a direction for exactly that reason, so this
        // asserts the same property rather than freezing an offset.
        assertTrue(after > before, "PageDown should move the cursor forward, was $before -> $after")
        holder.assertDoc(PARITY_DOC)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "PageUp moves cursor up a page"
    @Test
    fun pageUpMovesCursorUpAPage() = parityTest { holder ->
        press(holder, "Ctrl-End")
        val before = holder.session.cursorPos
        press(holder, "PageUp")
        val after = holder.session.cursorPos
        assertTrue(after < before, "PageUp should move the cursor backward, was $before -> $after")
        holder.assertDoc(PARITY_DOC)
    }

    // ─── Standard: word delete ──────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts
    //   "Ctrl-Backspace at line start crosses line boundary"
    @Test
    fun ctrlBackspaceAtLineStartCrossesLineBoundary() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 2)
        press(holder, "Home")
        press(holder, "Ctrl-Backspace")
        // Smart home leaves the cursor after the indent, so the group deleted
        // is that indent — the line boundary is not reached.
        holder.session.assertParityCursor(
            46,
            doc = parityDocWithHead(PARITY_LINE_1, PARITY_LINE_2, "if (n <= 1) return n;")
        )
    }

    // ─── Default: move line ─────────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Alt-ArrowUp moves line up"
    @Test
    fun altArrowUpMovesLineUp() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 2)
        press(holder, "Alt-ArrowUp")
        holder.session.assertParityCursor(
            22,
            doc = parityDocWithHead(PARITY_LINE_1, PARITY_LINE_3, PARITY_LINE_2)
        )
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Alt-ArrowDown moves line down"
    @Test
    fun altArrowDownMovesLineDown() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown")
        press(holder, "Alt-ArrowDown")
        // Same document as moving line 3 up; only the cursor differs.
        holder.session.assertParityCursor(
            48,
            doc = parityDocWithHead(PARITY_LINE_1, PARITY_LINE_3, PARITY_LINE_2)
        )
    }

    // ─── Default: copy line ─────────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Shift-Alt-ArrowUp copies line up"
    @Test
    fun shiftAltArrowUpCopiesLineUp() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 2)
        press(holder, "Shift-Alt-ArrowUp")
        holder.session.assertParityCursor(
            46,
            doc = parityDocWithHead(
                PARITY_LINE_1,
                PARITY_LINE_2,
                PARITY_LINE_3,
                PARITY_LINE_3
            )
        )
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Shift-Alt-ArrowDown copies line down"
    @Test
    fun shiftAltArrowDownCopiesLineDown() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown")
        press(holder, "Shift-Alt-ArrowDown")
        holder.session.assertParityCursor(
            22,
            doc = parityDocWithHead(
                PARITY_LINE_1,
                PARITY_LINE_2,
                PARITY_LINE_2,
                PARITY_LINE_3
            )
        )
    }

    // ─── Default: delete line ───────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-Shift-k deletes current line"
    @Test
    fun ctrlShiftKDeletesCurrentLine() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown")
        press(holder, "Ctrl-Shift-k")
        holder.session.assertParityCursor(
            22,
            doc = parityDocWithHead(PARITY_LINE_1, PARITY_LINE_3)
        )
    }

    // ─── Default: indent ────────────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-] indents line"
    @Test
    fun ctrlBracketRightIndentsLine() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 2)
        press(holder, "Ctrl-]")
        // One indent unit is two spaces, and the cursor rides the insertion.
        holder.session.assertParityCursor(
            48,
            doc = parityDocWithHead(
                PARITY_LINE_1,
                PARITY_LINE_2,
                "      if (n <= 1) return n;"
            )
        )
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-[ dedents line"
    @Test
    fun ctrlBracketLeftDedentsLine() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 2)
        press(holder, "Ctrl-]")
        press(holder, "Ctrl-[")
        // The document round-trips, but the cursor does not go home with it:
        // it stays two columns in, where the indent left it.
        holder.session.assertParityCursor(48)
    }

    // ─── Default: bracket matching ──────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-Shift-\\ goes to matching bracket"
    @Test
    fun ctrlShiftBackslashGoesToMatchingBracket() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown")
        press(holder, "End")
        press(holder, "ArrowLeft")
        press(holder, "Ctrl-Shift-\\")
        // From the `{` closing line 2 to just past the `}` on line 5.
        holder.session.assertParityCursor(121)
    }

    // ─── Tab / Shift-Tab ────────────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts
    //   "Tab indents (or inserts tab depending on config)"
    @Test
    fun tabIndentsOrInsertsTabDependingOnConfig() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 2)
        press(holder, "Tab")
        // basicSetup does not add indentWithTab, so Tab is inert on both sides.
        holder.session.assertParityCursor(46)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Shift-Tab dedents"
    @Test
    fun shiftTabDedents() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 2)
        press(holder, "Shift-Tab")
        holder.session.assertParityCursor(46)
    }

    // ─── Emacs: inert under basicSetup ──────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-a goes to line start"
    @Test
    fun ctrlAGoesToLineStart() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 2)
        press(holder, "ArrowRight", times = 5)
        press(holder, "Ctrl-a")
        // What the name says is not what either editor does: with no emacs
        // keymap loaded, Ctrl-a is select-all.
        holder.session.assertParity(anchor = 0, head = 229)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-e goes to line end"
    @Test
    fun ctrlEGoesToLineEnd() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 2)
        press(holder, "Ctrl-e")
        holder.session.assertParityCursor(46)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-p moves line up"
    @Test
    fun ctrlPMovesLineUp() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 3)
        press(holder, "Ctrl-p")
        holder.session.assertParityCursor(72)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-n moves line down"
    @Test
    fun ctrlNMovesLineDown() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "Ctrl-n")
        holder.session.assertParityCursor(0)
    }

    // ─── Selection with Shift ───────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Shift-ArrowDown extends selection down"
    @Test
    fun shiftArrowDownExtendsSelectionDown() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "Shift-ArrowDown")
        holder.session.assertParity(anchor = 0, head = 22)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Shift-ArrowUp extends selection up"
    @Test
    fun shiftArrowUpExtendsSelectionUp() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 3)
        press(holder, "Shift-ArrowUp")
        holder.session.assertParity(anchor = 72, head = 46)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Shift-Home selects to line start"
    @Test
    fun shiftHomeSelectsToLineStart() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown")
        press(holder, "End")
        press(holder, "Shift-Home")
        holder.session.assertParity(anchor = 45, head = 22)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Shift-End selects to line end"
    @Test
    fun shiftEndSelectsToLineEnd() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown")
        press(holder, "Home")
        press(holder, "Shift-End")
        holder.session.assertParity(anchor = 22, head = 45)
    }

    // ─── Tab handling ───────────────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts
    //   "Tab key behavior matches between CM6 and KM"
    @Test
    fun tabKeyBehaviorMatchesBetweenCm6AndKm() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 2)
        press(holder, "Home")
        press(holder, "Tab")
        holder.session.assertParityCursor(50)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Tab then Shift-Tab round-trips"
    @Test
    fun tabThenShiftTabRoundTrips() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 2)
        press(holder, "Home")
        press(holder, "Tab")
        press(holder, "Shift-Tab")
        holder.session.assertParityCursor(50)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts
    //   "multiple Tab presses increase indent consistently"
    @Test
    fun multipleTabPressesIncreaseIndentConsistently() = parityTest { holder ->
        press(holder, "Ctrl-Home")
        press(holder, "ArrowDown", times = 2)
        press(holder, "Home")
        press(holder, "Tab", times = 3)
        holder.session.assertParityCursor(50)
    }
}
