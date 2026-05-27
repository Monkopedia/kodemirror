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
package com.monkopedia.kodemirror.vim

import com.monkopedia.kodemirror.commands.history
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.Rect
import com.monkopedia.kodemirror.view.ViewUpdate
import kotlin.test.Test

/**
 * Distinguishes vim's display-line motion (`gj`/`gk` -> `moveByDisplayLines`)
 * from the linewise motion (`j`/`k` -> `moveByLines`) on a WRAPPED line (#77).
 *
 * The fixture wraps a real [EditorSession] so vim's motion machinery runs
 * unchanged, and overrides only the layout geometry to model a single logical
 * line that wraps across two visual rows:
 *
 *   logical line 1: "aaaaaaaaaa0123456789" (20 chars)
 *     visual row 0: offsets  0..9   y in [0,10)
 *     visual row 1: offsets 10..19  y in [10,20)
 *   logical line 2: "next"          y in [20,30)
 *
 * `gj` must step DOWN one VISUAL row (staying on logical line 1); `j` must jump
 * the whole logical line (to line 2).
 */
class VimDisplayLineMotionTest {

    private val doc = "aaaaaaaaaa0123456789\nnext"

    /**
     * An [EditorSession] that delegates everything to a real session but
     * supplies wrap-aware geometry for [coordsAtPos]/[posAtCoords].
     */
    private class WrappedGeometrySession(
        private val delegate: EditorSession
    ) : EditorSession by delegate {
        private val rowHeight = 10f

        override fun coordsAtPos(pos: Int, side: Int): Rect {
            val line = state.doc.lineAt(DocPos(pos))
            val offsetInLine = pos - line.from.value
            val col = offsetInLine % 10
            val absRow = if (line.number.value == 1) offsetInLine / 10 else 2
            val top = absRow * rowHeight
            val x = col.toFloat()
            return Rect(x, top, x, top + rowHeight)
        }

        override fun posAtCoords(x: Float, y: Float): Int {
            val absRow = (y / rowHeight).toInt().coerceIn(0, 2)
            val col = x.toInt().coerceIn(0, 9)
            return when (absRow) {
                0 -> col
                1 -> 10 + col
                else -> {
                    val secondStart = state.doc.line(LineNumber(2)).from.value
                    secondStart + col.coerceAtMost(4)
                }
            }
        }
    }

    private fun buildHelpers(): VimHelpers {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(3)),
                extensions = extensionListOf(
                    vimContextField,
                    history(),
                    EditorState.allowMultipleSelections.of(true)
                )
            )
        )
        var cm: VimEditor? = null
        val real = EditorSession(state) { tr ->
            val editor = cm ?: return@EditorSession
            if (editor.vimPlugin != null) return@EditorSession
            if (tr.docChanged) {
                val update = ViewUpdate(
                    session = editor.session,
                    state = editor.session.state,
                    transactions = listOf(tr)
                )
                editor.onChangeLight(update)
            }
        }
        val session = WrappedGeometrySession(real)
        val editor = VimEditor(session)
        cm = editor
        val vim = Vim.maybeInitVimState_(editor)
        Vim.resetVimGlobalState_()
        resetVimContext(editor)
        return VimHelpers(editor, vim)
    }

    @Test
    fun gjMovesByVisualRowStayingOnSameLogicalLine() {
        val h = buildHelpers()
        // Cursor starts at logical line 0, column 3 (first visual row).
        h.assertCursorAt(0, 3)
        h.doKeys("g", "j")
        // gj steps DOWN one VISUAL row -> still logical line 0, column 13
        // (column 3 of the second wrapped row). NOT the next logical line.
        h.assertCursorAt(0, 13)
    }

    @Test
    fun gkMovesByVisualRowUp() {
        val h = buildHelpers()
        h.doKeys("g", "j") // down to (0,13)
        h.doKeys("g", "k") // up one visual row, back to (0,3)
        h.assertCursorAt(0, 3)
    }

    @Test
    fun jMovesByWholeLogicalLine() {
        val h = buildHelpers()
        h.assertCursorAt(0, 3)
        h.doKeys("j")
        // j is LINEWISE: it jumps the entire wrapped logical line and lands on
        // the NEXT logical line ("next", line 1), NOT the second visual row.
        h.assertCursorAt(1, 3)
    }
}
