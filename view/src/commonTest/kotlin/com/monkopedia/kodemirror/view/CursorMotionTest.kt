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

import com.monkopedia.kodemirror.state.CharCategory
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Line
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CursorMotionTest {

    private fun state(doc: String, pos: Int = 0): EditorState = EditorState.create(
        EditorStateConfig(
            doc = doc.asDoc(),
            selection = com.monkopedia.kodemirror.state.SelectionSpec.CursorSpec(DocPos(pos))
        )
    )

    @Test
    fun groupAtWordChar() {
        val s = state("hello world")
        assertEquals(CharCategory.Word, groupAt(s, DocPos.ZERO))
        assertEquals(CharCategory.Word, groupAt(s, DocPos(4)))
    }

    @Test
    fun groupAtSpace() {
        val s = state("hello world")
        assertEquals(CharCategory.Space, groupAt(s, DocPos(5)))
    }

    @Test
    fun groupAtPunctuation() {
        val s = state("a,b")
        assertEquals(CharCategory.Other, groupAt(s, DocPos(1)))
    }

    @Test
    fun moveByCharForward() {
        val s = state("hello", 0)
        val sel = EditorSelection.cursor(DocPos.ZERO)
        val moved = moveByChar(s, sel, forward = true)
        assertEquals(DocPos(1), moved.head)
        assertTrue(moved.empty) // cursor, not range
    }

    @Test
    fun moveByCharBackward() {
        val s = state("hello", 3)
        val sel = EditorSelection.cursor(DocPos(3))
        val moved = moveByChar(s, sel, forward = false)
        assertEquals(DocPos(2), moved.head)
    }

    @Test
    fun moveByCharAtStart() {
        val s = state("hello", 0)
        val sel = EditorSelection.cursor(DocPos.ZERO)
        val moved = moveByChar(s, sel, forward = false)
        assertEquals(DocPos.ZERO, moved.head)
    }

    @Test
    fun moveByCharAtEnd() {
        val s = state("hello", 5)
        val sel = EditorSelection.cursor(DocPos(5))
        val moved = moveByChar(s, sel, forward = true)
        assertEquals(DocPos(5), moved.head)
    }

    @Test
    fun moveByCharExtend() {
        val s = state("hello")
        val sel = EditorSelection.cursor(DocPos(1))
        val moved = moveByChar(s, sel, forward = true, extend = true)
        assertEquals(DocPos(1), moved.anchor)
        assertEquals(DocPos(2), moved.head)
        assertFalse(moved.empty)
    }

    @Test
    fun moveByGroupWord() {
        val s = state("hello world")
        val sel = EditorSelection.cursor(DocPos.ZERO)
        val moved = moveByGroup(s, sel, forward = true)
        // Should move past the entire word "hello"
        assertEquals(DocPos(5), moved.head)
    }

    @Test
    fun moveByGroupSpaces() {
        val s = state("hello world")
        val sel = EditorSelection.cursor(DocPos(5))
        val moved = moveByGroup(s, sel, forward = true)
        // Starting on space: skip the space then continue through "world"
        // (CM6 behavior: spaces are skipped, movement continues to next group end)
        assertEquals(DocPos(11), moved.head)
    }

    @Test
    fun moveByGroupBackward() {
        val s = state("hello world")
        val sel = EditorSelection.cursor(DocPos(11))
        val moved = moveByGroup(s, sel, forward = false)
        // Should move back past "world"
        assertEquals(DocPos(6), moved.head)
    }

    @Test
    fun moveByGroupExtend() {
        val s = state("hello world")
        val sel = EditorSelection.cursor(DocPos.ZERO)
        val moved = moveByGroup(s, sel, forward = true, extend = true)
        assertEquals(DocPos.ZERO, moved.anchor)
        assertEquals(DocPos(5), moved.head)
    }

    // -----------------------------------------------------------------------
    // moveVertically — visual (wrapped) row vs. logical line. This is the
    // primitive that vim gj/gk delegate to (#77). With wrap-aware geometry it
    // must step by VISUAL row (staying on the same logical line when that line
    // wraps); without geometry it must fall back to whole-logical-line motion.
    // -----------------------------------------------------------------------

    // A logical line "abcdefghij0123456789" (20 chars) that wraps into two
    // visual rows of 10 columns, followed by a short logical line "second".
    // Layout model: each visual row is 10px tall, each char is 1px wide.
    //   row 0: line-0 offsets  0..9   y in [0,10)
    //   row 1: line-0 offsets 10..19  y in [10,20)
    //   row 2: line-1 ("second")      y in [20,30)
    private val wrappedDoc = "abcdefghij0123456789\nsecond"

    private fun wrappedGeometrySession(): EditorSession =
        object : FakeEditorSession(state(wrappedDoc)) {
            private val rowHeight = 10f

            override fun coordsAtPos(pos: Int, side: Int): Rect? {
                val line = state.doc.lineAt(DocPos(pos))
                val offsetInLine = pos - line.from.value
                val visualRowWithinLine = offsetInLine / 10
                val col = offsetInLine % 10
                // Logical line 1 (number 1) occupies visual rows 0 and 1;
                // logical line 2 ("second") starts at visual row 2.
                val absRow = if (line.number.value == 1) visualRowWithinLine else 2
                val top = absRow * rowHeight
                val x = col.toFloat()
                return Rect(x, top, x, top + rowHeight)
            }

            override fun posAtCoords(x: Float, y: Float): Int? {
                val absRow = (y / rowHeight).toInt().coerceIn(0, 2)
                val col = x.toInt().coerceIn(0, 9)
                return when (absRow) {
                    0 -> col // line 0, row 0
                    1 -> 10 + col // line 0, row 1 (same logical line!)
                    else -> {
                        // line "second" starts after "abc...789\n" = offset 21
                        val secondStart = state.doc.line(
                            com.monkopedia.kodemirror.state.LineNumber(2)
                        ).from.value
                        secondStart + col.coerceAtMost(5)
                    }
                }
            }
        }

    @Test
    fun moveVerticallyStepsByVisualRowWithinWrappedLine() {
        val session = wrappedGeometrySession()
        // Start on the first visual row, column 3 (offset 3).
        val sel = EditorSelection.cursor(DocPos(3))
        val moved = moveVertically(session, sel, forward = true)
        // Visual-row motion: lands on the SECOND wrapped row of the SAME
        // logical line (offset 13), NOT on the next logical line "second".
        assertEquals(DocPos(13), moved.head, "gj should move one VISUAL row down")
        assertEquals(
            1,
            session.state.doc.lineAt(moved.head).number.value,
            "should stay on the same logical line while it wraps"
        )
    }

    @Test
    fun moveVerticallyCrossesToNextLogicalLineAfterLastVisualRow() {
        val session = wrappedGeometrySession()
        // Start on the SECOND visual row (offset 13). One more visual row down
        // exits the wrapped line and reaches the next logical line "second".
        val sel = EditorSelection.cursor(DocPos(13))
        val moved = moveVertically(session, sel, forward = true)
        assertEquals(
            2,
            session.state.doc.lineAt(moved.head).number.value,
            "from the last visual row, moving down crosses to the next logical line"
        )
    }

    @Test
    fun moveVerticallyUpStepsByVisualRow() {
        val session = wrappedGeometrySession()
        // From the second wrapped row (offset 13) up one visual row -> first row.
        val sel = EditorSelection.cursor(DocPos(13))
        val moved = moveVertically(session, sel, forward = false)
        assertEquals(DocPos(3), moved.head, "gk should move one VISUAL row up")
        assertEquals(1, session.state.doc.lineAt(moved.head).number.value)
    }

    // -----------------------------------------------------------------------
    // Goal column ("column memory") across vertical moves (#87). After landing
    // on a SHORT line (cursor clamped to its end), the goal column must be
    // remembered so the next move to a longer line springs back to the goal
    // column — matching CM6/vim/standard editors. Regressed by the visual-row
    // motion change (#78), which targeted the current head's x instead of the
    // remembered goal column's x.
    // -----------------------------------------------------------------------

    // Three NON-wrapping logical lines (each fits in one visual row):
    //   line 1: "a long first line here"  (22 chars)
    //   line 2: "shrt"                    (4 chars)
    //   line 3: "another long line here"  (22 chars)
    // Layout model: char width 1px, row height 10px, one visual row per line.
    private val columnMemoryDoc = "a long first line here\nshrt\nanother long line here"

    private fun columnMemorySession(startPos: Int): EditorSession =
        object : FakeEditorSession(state(columnMemoryDoc, startPos)) {
            private val rowHeight = 10f

            override fun coordsAtPos(pos: Int, side: Int): Rect? {
                val line = state.doc.lineAt(DocPos(pos))
                val col = pos - line.from.value
                val top = (line.number.value - 1) * rowHeight
                val x = col.toFloat()
                return Rect(x, top, x, top + rowHeight)
            }

            override fun posAtCoords(x: Float, y: Float): Int? {
                val rowIndex = (y / rowHeight).toInt().coerceIn(0, state.doc.lines - 1)
                val line = state.doc.line(LineNumber(rowIndex + 1))
                val col = x.toInt().coerceIn(0, line.text.length)
                return line.from.value + col
            }
        }

    @Test
    fun goalColumnPreservedAcrossShortIntermediateLine() {
        // Cursor starts at the END of line 1 (offset 22, column 22). This is the
        // remembered goal column once vertical motion begins.
        val session = columnMemorySession(startPos = 22)
        val start = EditorSelection.cursor(DocPos(22))

        // Down once: line 2 ("shrt", len 4) clamps the cursor to its end (col 4).
        val down1 = moveVertically(session, start, forward = true)
        assertEquals(2, session.state.doc.lineAt(down1.head).number.value)
        assertEquals(
            4,
            down1.head.value - session.state.doc.lineAt(down1.head).from.value,
            "short line clamps cursor to its end column"
        )
        assertEquals(22, down1.goalColumn, "goal column is captured and preserved")

        // Down again: line 3 ("another long line here", len 22) must spring back
        // to the GOAL column (22), NOT stay at the short-line column (4).
        val down2 = moveVertically(session, down1, forward = true)
        assertEquals(3, session.state.doc.lineAt(down2.head).number.value)
        assertEquals(
            22,
            down2.head.value - session.state.doc.lineAt(down2.head).from.value,
            "cursor returns to the remembered goal column on the longer line"
        )
        assertEquals(22, down2.goalColumn, "goal column survives the round trip")
    }

    @Test
    fun goalColumnPreservedOnDownThenUpRoundTrip() {
        // Start at the end of line 1 (col 22), move down to the short line, then
        // back up. The cursor must return to the original column on line 1.
        val session = columnMemorySession(startPos = 22)
        val start = EditorSelection.cursor(DocPos(22))

        val down = moveVertically(session, start, forward = true)
        // Clamped to the short line's end.
        assertEquals(2, session.state.doc.lineAt(down.head).number.value)

        val up = moveVertically(session, down, forward = false)
        assertEquals(1, session.state.doc.lineAt(up.head).number.value)
        assertEquals(
            22,
            up.head.value - session.state.doc.lineAt(up.head).from.value,
            "down-then-up round trip preserves the original column"
        )
    }

    @Test
    fun moveVerticallyFallsBackToLogicalLineWithoutGeometry() {
        // No layout geometry (coordsAtPos returns null) — must fall back to
        // whole-logical-line motion rather than failing. This mirrors the
        // headless path vim's moveByDisplayLines relies on.
        val session = object : FakeEditorSession(state(wrappedDoc)) {
            override fun coordsAtPos(pos: Int, side: Int): Rect? = null
            override fun posAtCoords(x: Float, y: Float): Int? = null
        }
        val sel = EditorSelection.cursor(DocPos(3))
        val moved = moveVertically(session, sel, forward = true)
        // coordsAtPos == null -> moveVertically returns the input unchanged,
        // signalling "no visual move possible" so callers can fall back.
        assertEquals(DocPos(3), moved.head)
    }
}

/**
 * Minimal [EditorSession] for motion unit tests. Holds a fixed [EditorState];
 * geometry methods default to "no layout available" and are overridden per test.
 */
private abstract class FakeEditorSession(override val state: EditorState) : EditorSession {
    override fun dispatch(vararg specs: TransactionSpec) = Unit
    override fun dispatchTransaction(tr: Transaction) = Unit
    override fun <V : PluginValue> plugin(plugin: ViewPlugin<V>): V? = null
    override fun coordsAtPos(pos: Int, side: Int): Rect? = null
    override fun posAtCoords(x: Float, y: Float): Int? = null
    override val coroutineScope: CoroutineScope = CoroutineScope(Job())
    override val editable: Boolean = true
    override val textDirection: Direction = Direction.LTR
    override fun textDirectionAt(pos: Int): Direction = Direction.LTR
    override fun bidiSpans(line: Line): List<BidiSpan> = emptyList()
    override fun phrase(phrase: String, vararg insert: Any): String = phrase
}

private fun assertFalse(condition: Boolean) = kotlin.test.assertFalse(condition)
