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
package com.monkopedia.kodemirror.view

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `commonTest` twins for the state-transition half of
 * `gap-analysis/tests/navigation.spec.ts` and
 * `gap-analysis/tests/selection.spec.ts`.
 *
 * Every expectation below is a literal captured from **real CodeMirror 6**
 * (`gap-analysis/fixtures/cm6-navigation-selection-expectations.json`), not
 * from KodeMirror. The Playwright suite proves *we still match CM6*, in a
 * browser, on wasm; this one proves *the behaviour holds on jvm, android,
 * wasmJs, macosArm64 and iosSimulatorArm64*. See `docs/testing-strategy.md`.
 *
 * The four sequences that touch a vertical motion — `arrow keys - down`,
 * `arrow keys - up`, `column memory across lines` and
 * `Shift+Down - extend selection down` — live in
 * `input/NavigationSelectionLayoutParityTest.kt` instead. `moveVertically`
 * steps by *visual* row and returns its input unchanged when `coordsAtPos` has
 * nothing to report, so an `ArrowDown` in a session with no layout does not
 * fail; it silently does nothing.
 *
 * `selection.spec.ts` presses `Control+Home` in its `beforeEach`, so each
 * selection twin below opens with it, exactly as the spec does.
 *
 * Every `Ctrl-…` written below is a key name **as the Playwright spec presses
 * it**, not a hardcoded modifier: [press] translates it through the keymap the
 * running platform actually has, so `Ctrl-Home` becomes `Meta-Home` and
 * `Ctrl-ArrowRight` becomes `Alt-ArrowRight` on the Mac-flavoured targets — the
 * two disagree about which modifier Mac substitutes, which is why a blanket
 * `Mod` rule would also be wrong. `KeymapParityTest.documentStartUsesThePlatformModifier`
 * is the probe that pins that translation; it is not repeated here.
 */
class NavigationSelectionParityTest {

    /**
     * The capture's offsets are meaningless against a different document, so
     * fail loudly here rather than as twenty confusing off-by-N failures.
     */
    @Test
    fun fixtureDocumentMatchesTheCapture() {
        assertEquals(PARITY_DOC_LENGTH, PARITY_DOC.length, "fixture document length")
        assertEquals(10, PARITY_DOC.split("\n").size, "fixture document line count")
    }

    // ─── navigation.spec.ts ─────────────────────────────────────────────────

    // parity: gap-analysis/tests/navigation.spec.ts "arrow keys - right"
    @Test
    fun arrowKeysRight() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("ArrowRight")
        editor.assertParityCursorAt(pos = 1, line = 1, col = 1)
    }

    // parity: gap-analysis/tests/navigation.spec.ts "arrow keys - left"
    @Test
    fun arrowKeysLeft() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("ArrowRight", times = 2)
        editor.press("ArrowLeft")
        editor.assertParityCursorAt(pos = 1, line = 1, col = 1)
    }

    // parity: gap-analysis/tests/navigation.spec.ts "Home key"
    @Test
    fun homeKey() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("ArrowRight", times = 5)
        editor.press("Home")
        // Smart home lands on the first non-whitespace character; line 1 has no
        // indent, so that is column 0.
        editor.assertParityCursorAt(pos = 0, line = 1, col = 0)
    }

    // parity: gap-analysis/tests/navigation.spec.ts "End key"
    @Test
    fun endKey() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("End")
        editor.assertParityCursorAt(pos = 21, line = 1, col = 21)
    }

    // parity: gap-analysis/tests/navigation.spec.ts "Ctrl+Home - go to document start"
    @Test
    fun ctrlHomeGoToDocumentStart() {
        val editor = parityEditor()
        editor.press("Ctrl-End")
        editor.press("Ctrl-Home")
        editor.assertParityCursorAt(pos = 0, line = 1, col = 0)
    }

    // parity: gap-analysis/tests/navigation.spec.ts "Ctrl+End - go to document end"
    @Test
    fun ctrlEndGoToDocumentEnd() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Ctrl-End")
        editor.assertParityCursorAt(pos = 229, line = 10, col = 1)
    }

    // parity: gap-analysis/tests/navigation.spec.ts "Ctrl+Right - word movement forward"
    @Test
    fun ctrlRightWordMovementForward() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Ctrl-ArrowRight")
        // Stops after the "//" group, not after the first word.
        editor.assertParityCursorAt(pos = 2, line = 1, col = 2)
    }

    // parity: gap-analysis/tests/navigation.spec.ts "Ctrl+Left - word movement backward"
    @Test
    fun ctrlLeftWordMovementBackward() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("End")
        editor.press("Ctrl-ArrowLeft")
        editor.assertParityCursorAt(pos = 13, line = 1, col = 13)
    }

    // ─── selection.spec.ts ──────────────────────────────────────────────────

    // parity: gap-analysis/tests/selection.spec.ts "Shift+Right - extend selection right"
    @Test
    fun shiftRightExtendSelectionRight() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Shift-ArrowRight")
        editor.assertParity(anchor = 0, head = 1)
    }

    // parity: gap-analysis/tests/selection.spec.ts "Shift+Left - extend selection left"
    @Test
    fun shiftLeftExtendSelectionLeft() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("ArrowRight", times = 5)
        editor.press("Shift-ArrowLeft")
        editor.assertParity(anchor = 5, head = 4)
    }

    // parity: gap-analysis/tests/selection.spec.ts "Ctrl+Shift+Right - select word right"
    @Test
    fun ctrlShiftRightSelectWordRight() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Ctrl-Shift-ArrowRight")
        editor.assertParity(anchor = 0, head = 2)
    }

    // parity: gap-analysis/tests/selection.spec.ts "Ctrl+Shift+Left - select word left"
    @Test
    fun ctrlShiftLeftSelectWordLeft() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("End")
        editor.press("Ctrl-Shift-ArrowLeft")
        editor.assertParity(anchor = 21, head = 13)
    }

    // parity: gap-analysis/tests/selection.spec.ts "Shift+Home - select to line start"
    @Test
    fun shiftHomeSelectToLineStart() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("ArrowRight", times = 10)
        editor.press("Shift-Home")
        editor.assertParity(anchor = 10, head = 0)
    }

    // parity: gap-analysis/tests/selection.spec.ts "Shift+End - select to line end"
    @Test
    fun shiftEndSelectToLineEnd() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Shift-End")
        editor.assertParity(anchor = 0, head = 21)
    }

    // parity: gap-analysis/tests/selection.spec.ts "Ctrl+A - select all"
    @Test
    fun ctrlASelectAll() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Ctrl-a")
        editor.assertParity(anchor = 0, head = 229)
    }

    // parity: gap-analysis/tests/selection.spec.ts "typing replaces selection"
    @Test
    fun typingReplacesSelection() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Ctrl-Shift-ArrowRight")
        editor.type("replaced")
        // The selected "//" is gone, the typed text took its place, and the
        // selection collapsed to a cursor after it.
        editor.assertParityCursor(
            8,
            doc = parityDocWithHead("replaced Fibonacci sequence", PARITY_LINE_2, PARITY_LINE_3)
        )
    }
}
