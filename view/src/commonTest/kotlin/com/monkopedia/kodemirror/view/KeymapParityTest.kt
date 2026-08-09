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
 * `gap-analysis/tests/keymap-commands.spec.ts`.
 *
 * Every expectation below is a literal captured from **real CodeMirror 6**
 * (`gap-analysis/fixtures/cm6-keymap-expectations.json`), not from KodeMirror.
 * The Playwright suite proves *we still match CM6*, in a browser, on wasm; this
 * one proves *the behaviour holds on jvm, android, wasmJs, macosArm64 and
 * iosSimulatorArm64*. See `docs/testing-strategy.md`.
 *
 * The 28 sequences whose result depends on layout — anything stepping by
 * visual line, and PageUp/PageDown — live in
 * `input/KeymapLayoutParityTest.kt`, because vertical motion resolves through
 * the view's geometry and is a no-op without a laid-out editor.
 *
 * Several of these pin a key as doing **nothing**, which is what the capture
 * measured: CM6's `basicSetup` does not include the emacs keymap, so `Ctrl-e`,
 * `Ctrl-b`, `Ctrl-h`, `Ctrl-k`, `Ctrl-t` and friends leave the document and
 * selection exactly where the preceding navigation left them. Those twins are
 * the ones that would catch KodeMirror binding an emacs key by accident.
 */
class KeymapParityTest {

    /**
     * The capture's offsets are meaningless against a different document, so
     * fail loudly here rather than as 32 confusing off-by-N failures.
     */
    @Test
    fun fixtureDocumentMatchesTheCapture() {
        assertEquals(PARITY_DOC_LENGTH, PARITY_DOC.length, "fixture document length")
        assertEquals(10, PARITY_DOC.split("\n").size, "fixture document line count")
    }

    /**
     * The wiring probe: assert *both* modifier branches, the way
     * `ClipboardCommandsTest` probes the `expect`/`actual` it depends on.
     *
     * `standardKeymap` binds document-start as `Ctrl-Home` on non-Mac and
     * `Meta-Home` on Mac, and `platformOsName()` reports `"Mac"` on every
     * Kotlin/Native target (#217). A twin that hardcoded `Ctrl` would pass on
     * the JVM and fail on macOS and iOS for a reason that is not a bug — this
     * exact failure already happened in #200 — so this test states which
     * modifier is live here and that the other one is inert.
     */
    @Test
    fun documentStartUsesThePlatformModifier() {
        val editor = parityEditor()
        editor.press("Ctrl-End")
        assertEquals(PARITY_DOC_LENGTH, editor.cursorPos, "setup: cursor at document end")

        editor.pressResolved(unboundDocStartKey)
        assertEquals(
            PARITY_DOC_LENGTH,
            editor.cursorPos,
            "$unboundDocStartKey is not bound on $currentOs and must do nothing"
        )

        editor.pressResolved(boundDocStartKey)
        assertEquals(0, editor.cursorPos, "$boundDocStartKey is the binding on $currentOs")
    }

    // ─── Standard: arrow keys ───────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "ArrowLeft moves cursor left"
    @Test
    fun arrowLeftMovesCursorLeft() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("ArrowRight", times = 5)
        editor.press("ArrowLeft")
        editor.assertParityCursor(4)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "ArrowRight moves cursor right"
    @Test
    fun arrowRightMovesCursorRight() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("ArrowRight")
        editor.assertParityCursor(1)
    }

    // ─── Standard: Home/End ─────────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-Home moves to document start"
    @Test
    fun ctrlHomeMovesToDocumentStart() {
        val editor = parityEditor()
        editor.press("Ctrl-End")
        editor.press("Ctrl-Home")
        editor.assertParityCursor(0)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-End moves to document end"
    @Test
    fun ctrlEndMovesToDocumentEnd() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Ctrl-End")
        editor.assertParityCursor(229)
    }

    // ─── Standard: Enter ────────────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Enter inserts newline"
    @Test
    fun enterInsertsNewline() {
        val editor = parityEditor()
        editor.press("Ctrl-End")
        editor.press("Enter")
        editor.assertParityCursor(230, doc = PARITY_DOC + "\n")
    }

    // ─── Standard: Backspace/Delete ─────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Backspace deletes char backward"
    @Test
    fun backspaceDeletesCharBackward() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("ArrowRight", times = 5)
        editor.press("Backspace")
        editor.assertParityCursor(
            4,
            doc = parityDocWithHead("// Fbonacci sequence", PARITY_LINE_2, PARITY_LINE_3)
        )
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Delete deletes char forward"
    @Test
    fun deleteDeletesCharForward() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Delete")
        editor.assertParityCursor(
            0,
            doc = parityDocWithHead("/ Fibonacci sequence", PARITY_LINE_2, PARITY_LINE_3)
        )
    }

    // ─── Standard: word movement ────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-ArrowRight moves word right"
    @Test
    fun ctrlArrowRightMovesWordRight() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Ctrl-ArrowRight")
        // Stops after the "//" group, not after the first word.
        editor.assertParityCursor(2)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-ArrowRight twice"
    @Test
    fun ctrlArrowRightTwice() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Ctrl-ArrowRight", times = 2)
        editor.assertParityCursor(12)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-ArrowLeft moves word left"
    @Test
    fun ctrlArrowLeftMovesWordLeft() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("End")
        editor.press("Ctrl-ArrowLeft")
        editor.assertParityCursor(13)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-ArrowLeft twice"
    @Test
    fun ctrlArrowLeftTwice() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("End")
        editor.press("Ctrl-ArrowLeft", times = 2)
        editor.assertParityCursor(3)
    }

    // ─── Standard: word delete ──────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-Backspace deletes word backward"
    @Test
    fun ctrlBackspaceDeletesWordBackward() {
        val editor = parityEditor()
        editor.press("Ctrl-End")
        editor.press("Enter")
        editor.type("hello world")
        editor.press("Ctrl-Backspace")
        // "world" goes; the space before it stays.
        editor.assertParityCursor(236, doc = PARITY_DOC + "\nhello ")
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts
    //   "Ctrl-Backspace on typed text multiple times"
    @Test
    fun ctrlBackspaceOnTypedTextMultipleTimes() {
        val editor = parityEditor()
        editor.press("Ctrl-End")
        editor.press("Enter")
        editor.type("one two three")
        editor.press("Ctrl-Backspace", times = 3)
        // Three groups back clears the typed text but not the inserted newline.
        editor.assertParityCursor(230, doc = PARITY_DOC + "\n")
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-Delete deletes word forward"
    @Test
    fun ctrlDeleteDeletesWordForward() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Ctrl-Delete")
        editor.assertParityCursor(
            0,
            doc = parityDocWithHead(" Fibonacci sequence", PARITY_LINE_2, PARITY_LINE_3)
        )
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-Delete twice"
    @Test
    fun ctrlDeleteTwice() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Ctrl-Delete", times = 2)
        editor.assertParityCursor(
            0,
            doc = parityDocWithHead(" sequence", PARITY_LINE_2, PARITY_LINE_3)
        )
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts
    //   "Ctrl-Delete at line end crosses line boundary"
    @Test
    fun ctrlDeleteAtLineEndCrossesLineBoundary() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("End")
        editor.press("Ctrl-Delete")
        editor.assertParityCursor(
            21,
            doc = parityDocWithHead(PARITY_LINE_1 + PARITY_LINE_2, PARITY_LINE_3)
        )
    }

    // ─── Default: transpose ─────────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-t transposes characters"
    @Test
    fun ctrlTTransposesCharacters() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("ArrowRight", times = 3)
        editor.press("Ctrl-t")
        // No Ctrl-t in basicSetup: the document and the cursor stand still.
        editor.assertParityCursor(3)
    }

    // ─── Emacs: inert under basicSetup ──────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-f moves char right"
    @Test
    fun ctrlFMovesCharRight() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Ctrl-f")
        // Ctrl-f is the search panel, not a cursor motion: nothing here moves.
        editor.assertParityCursor(0)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-b moves char left"
    @Test
    fun ctrlBMovesCharLeft() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("ArrowRight", times = 5)
        editor.press("Ctrl-b")
        editor.assertParityCursor(5)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-d deletes char forward"
    @Test
    fun ctrlDDeletesCharForward() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Ctrl-d")
        // Ctrl-d is select-next-occurrence, and there is no word under the
        // cursor at offset 0, so it finds nothing to select.
        editor.assertParityCursor(0)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-h deletes char backward"
    @Test
    fun ctrlHDeletesCharBackward() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("ArrowRight", times = 5)
        editor.press("Ctrl-h")
        editor.assertParityCursor(5)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-k kills to end of line"
    @Test
    fun ctrlKKillsToEndOfLine() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("ArrowRight", times = 3)
        editor.press("Ctrl-k")
        editor.assertParityCursor(3)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-t transposes characters (emacs)"
    @Test
    fun ctrlTTransposesCharactersEmacs() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("ArrowRight", times = 2)
        editor.press("Ctrl-t")
        editor.assertParityCursor(2)
    }

    // ─── Selection with Shift ───────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Shift-ArrowRight extends selection right"
    @Test
    fun shiftArrowRightExtendsSelectionRight() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Shift-ArrowRight", times = 5)
        editor.assertParity(anchor = 0, head = 5)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Shift-ArrowLeft extends selection left"
    @Test
    fun shiftArrowLeftExtendsSelectionLeft() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("ArrowRight", times = 10)
        editor.press("Shift-ArrowLeft", times = 3)
        editor.assertParity(anchor = 10, head = 7)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-Shift-ArrowRight selects word right"
    @Test
    fun ctrlShiftArrowRightSelectsWordRight() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Ctrl-Shift-ArrowRight")
        editor.assertParity(anchor = 0, head = 2)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-Shift-ArrowLeft selects word left"
    @Test
    fun ctrlShiftArrowLeftSelectsWordLeft() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("End")
        editor.press("Ctrl-Shift-ArrowLeft")
        editor.assertParity(anchor = 21, head = 13)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-Shift-Home selects to doc start"
    @Test
    fun ctrlShiftHomeSelectsToDocStart() {
        val editor = parityEditor()
        editor.press("Ctrl-End")
        editor.press("Ctrl-Shift-Home")
        editor.assertParity(anchor = 229, head = 0)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-Shift-End selects to doc end"
    @Test
    fun ctrlShiftEndSelectsToDocEnd() {
        val editor = parityEditor()
        editor.press("Ctrl-Home")
        editor.press("Ctrl-Shift-End")
        editor.assertParity(anchor = 0, head = 229)
    }

    // ─── Select all ─────────────────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-a selects all text"
    @Test
    fun ctrlASelectsAllText() {
        val editor = parityEditor()
        editor.press("Ctrl-a")
        editor.assertParity(anchor = 0, head = 229)
    }

    // ─── Undo/Redo ──────────────────────────────────────────────────────────

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-z undoes last change"
    @Test
    fun ctrlZUndoesLastChange() {
        val editor = parityEditor()
        editor.press("Ctrl-End")
        editor.type("X")
        editor.press("Ctrl-z")
        editor.assertParityCursor(229)
    }

    // parity: gap-analysis/tests/keymap-commands.spec.ts "Ctrl-Shift-z redoes after undo"
    @Test
    fun ctrlShiftZRedoesAfterUndo() {
        val editor = parityEditor()
        editor.press("Ctrl-End")
        editor.type("Y")
        editor.press("Ctrl-z")
        editor.press("Ctrl-Shift-z")
        editor.assertParityCursor(230, doc = PARITY_DOC + "Y")
    }
}
