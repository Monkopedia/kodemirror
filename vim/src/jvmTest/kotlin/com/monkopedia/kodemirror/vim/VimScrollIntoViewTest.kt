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
import com.monkopedia.kodemirror.language.indentService
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.ViewUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for vim ↔ scroll-into-view integration (issue #158).
 *
 * In-operation vim motions dispatch their cursor transaction with
 * `scrollIntoView = false` (because `curOp != null`), so the operation has to
 * reveal the cursor itself when it ends. These tests assert that a moving vim
 * operation queues a scroll-into-view request whose target is the *destination*
 * selection head — fixing `gg`/`G` and offscreen `j`/`k` leaving the cursor off
 * screen, and the "jump to top" caused by revealing a stale offset-0.
 *
 * The seam mirrors [com.monkopedia.kodemirror.view.EditorSessionImpl], which
 * builds its scroll request from `tr.scrollIntoView` and
 * `tr.state.selection.main.head`; this test records exactly those values from
 * every dispatched transaction.
 */
class VimScrollIntoViewTest {

    /** A 100-line document, comfortably larger than any single viewport. */
    private val hundredLineDoc = (1..100).joinToString("\n") { "Line $it content here" }

    /** Document offset of the start of the given 0-based line. */
    private fun lineStart(state: EditorState, line: Int): Int =
        state.doc.line(LineNumber(line + 1)).from.value

    /** A recorded scroll-into-view request: the head the view would reveal. */
    private data class ScrollRecord(val target: Int)

    /**
     * Build a headless vim editor over [hundredLineDoc] with the cursor at the
     * start of [startLine] (0-based), recording every scroll-into-view request
     * the way the real view layer would resolve it.
     */
    private fun harness(startLine: Int, block: (VimEditor, List<ScrollRecord>) -> Unit) {
        val scrolls = mutableListOf<ScrollRecord>()
        val startOffset = run {
            val lines = hundredLineDoc.split("\n")
            var offset = 0
            for (i in 0 until startLine) offset += lines[i].length + 1
            offset
        }
        val state = EditorState.create(
            EditorStateConfig(
                doc = hundredLineDoc.asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(startOffset)),
                extensions = extensionListOf(
                    vimContextField,
                    history(),
                    EditorState.allowMultipleSelections.of(true),
                    indentService.of { _, _ -> 0 }
                )
            )
        )
        var cm: VimEditor? = null
        val session = EditorSession(state) { tr ->
            if (tr.scrollIntoView) {
                scrolls.add(ScrollRecord(tr.state.selection.main.head.value))
            }
            val editor = cm ?: return@EditorSession
            if (editor.vimPlugin != null) return@EditorSession
            if (tr.docChanged) {
                editor.onChangeLight(
                    ViewUpdate(
                        session = editor.session,
                        state = editor.session.state,
                        transactions = listOf(tr)
                    )
                )
            }
        }
        cm = VimEditor(session)
        Vim.maybeInitVimState_(cm)
        Vim.resetVimGlobalState_()
        resetVimContext(cm)
        block(cm, scrolls)
    }

    private fun VimEditor.head(): Int = session.state.selection.main.head.value

    @Test
    fun gg_revealsTopFromBelowTheFold() = harness(startLine = 60) { cm, scrolls ->
        val before = scrolls.size
        typeKey(cm, "g")
        typeKey(cm, "g")
        // gg lands on the first line.
        assertEquals(0, cm.head(), "gg should move the cursor to offset 0")
        val produced = scrolls.drop(before)
        assertTrue(
            produced.isNotEmpty(),
            "gg should queue a scroll-into-view request, got none"
        )
        assertEquals(
            0,
            produced.last().target,
            "gg should reveal the destination (offset 0), got ${produced.last().target}"
        )
    }

    @Test
    fun shiftG_revealsBottom() = harness(startLine = 0) { cm, scrolls ->
        val before = scrolls.size
        typeKey(cm, "G")
        val destination = cm.head()
        assertEquals(
            lineStart(cm.session.state, 99),
            destination,
            "G should move the cursor to the last line"
        )
        val produced = scrolls.drop(before)
        assertTrue(
            produced.isNotEmpty(),
            "G should queue a scroll-into-view request, got none"
        )
        assertEquals(
            destination,
            produced.last().target,
            "G should reveal the destination head, not a stale offset"
        )
    }

    @Test
    fun j_revealsDestinationNotStaleZero() = harness(startLine = 40) { cm, scrolls ->
        val before = scrolls.size
        typeKey(cm, "j")
        val destination = cm.head()
        assertTrue(destination > 0, "j should move the cursor below offset 0")
        val produced = scrolls.drop(before)
        assertTrue(
            produced.isNotEmpty(),
            "an offscreen j should queue a scroll-into-view request, got none"
        )
        assertEquals(
            destination,
            produced.last().target,
            "j should reveal the destination head (${destination}), not offset 0"
        )
    }

    @Test
    fun k_revealsDestinationNotStaleZero() = harness(startLine = 60) { cm, scrolls ->
        val before = scrolls.size
        typeKey(cm, "k")
        val destination = cm.head()
        assertTrue(destination > 0, "k should remain below offset 0 here")
        val produced = scrolls.drop(before)
        assertTrue(
            produced.isNotEmpty(),
            "an offscreen k should queue a scroll-into-view request, got none"
        )
        assertEquals(
            destination,
            produced.last().target,
            "k should reveal the destination head (${destination}), not offset 0"
        )
    }

    @Test
    fun nonMovingMotionDoesNotJumpViewport() = harness(startLine = 0) { cm, scrolls ->
        // Already at the top-left: gg cannot move the cursor, so it must NOT
        // queue a reveal that would yank the viewport to the cursor.
        val before = scrolls.size
        typeKey(cm, "g")
        typeKey(cm, "g")
        assertEquals(0, cm.head(), "cursor should still be at offset 0")
        assertEquals(
            before,
            scrolls.size,
            "a motion that does not move the cursor must not queue a scroll request"
        )
    }
}
