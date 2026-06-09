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
package com.monkopedia.kodemirror.commands

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.allowMultipleSelections
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionRange
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals

class LineCommandsTest {

    private fun createView(doc: String, cursor: Int = 0): EditorSession {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(cursor))
            )
        )
        return EditorSession(state)
    }

    /** Build a view with a multi-range selection. */
    private fun createView(
        doc: String,
        ranges: List<SelectionRange>,
        mainIndex: Int = 0
    ): EditorSession {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.EditorSelectionSpec(
                    EditorSelection.create(ranges, mainIndex)
                ),
                extensions = allowMultipleSelections.of(true)
            )
        )
        return EditorSession(state)
    }

    private fun range(anchor: Int, head: Int = anchor): SelectionRange =
        EditorSelection.range(DocPos(anchor), DocPos(head))

    private fun EditorSession.selectionPairs(): List<Pair<Int, Int>> =
        state.selection.ranges.map { it.anchor.value to it.head.value }

    @Test
    fun testMoveLineDown() {
        val view = createView("aaa\nbbb\nccc", cursor = 1)
        moveLineDown(view)
        assertEquals("bbb\naaa\nccc", view.state.doc.toString())
    }

    @Test
    fun testMoveLineUp() {
        val view = createView("aaa\nbbb\nccc", cursor = 5)
        moveLineUp(view)
        assertEquals("bbb\naaa\nccc", view.state.doc.toString())
    }

    @Test
    fun testMoveLineDownAtLastLine() {
        val view = createView("aaa\nbbb", cursor = 5)
        val result = moveLineDown(view)
        assertEquals(false, result)
        assertEquals("aaa\nbbb", view.state.doc.toString())
    }

    @Test
    fun testMoveLineUpAtFirstLine() {
        val view = createView("aaa\nbbb", cursor = 1)
        val result = moveLineUp(view)
        assertEquals(false, result)
        assertEquals("aaa\nbbb", view.state.doc.toString())
    }

    // --- multi-range moveLine (issue #130) ---

    @Test
    fun testMoveLineDownPreservesMultipleCursorsOnOneLine() {
        // Two cursors on line 2; both ride the moved line down.
        val view = createView("aaa\nbbb\nccc", listOf(range(4), range(6)))
        assertEquals(true, moveLineDown(view))
        assertEquals("aaa\nccc\nbbb", view.state.doc.toString())
        assertEquals(listOf(8 to 8, 10 to 10), view.selectionPairs())
    }

    @Test
    fun testMoveLineUpPreservesMultipleCursorsOnOneLine() {
        val view = createView("aaa\nbbb\nccc", listOf(range(4), range(6)))
        assertEquals(true, moveLineUp(view))
        assertEquals("bbb\naaa\nccc", view.state.doc.toString())
        assertEquals(listOf(0 to 0, 2 to 2), view.selectionPairs())
    }

    @Test
    fun testMoveLineDownMovesMultiLineBlockAsOneUnit() {
        // A single range spanning lines 1-2 moves as one block.
        val view = createView("aaa\nbbb\nccc", listOf(range(1, 6)))
        assertEquals(true, moveLineDown(view))
        assertEquals("ccc\naaa\nbbb", view.state.doc.toString())
        assertEquals(listOf(5 to 10), view.selectionPairs())
    }

    @Test
    fun testMoveLineDownBlockMadeOfMultipleRanges() {
        // A range spanning lines 1-2 plus another range on line 2 merge
        // into a single block and move together; line 3 (ccc) hops above.
        val view = createView(
            "aaa\nbbb\nccc\nddd",
            listOf(range(0, 5), range(6))
        )
        assertEquals(true, moveLineDown(view))
        assertEquals("ccc\naaa\nbbb\nddd", view.state.doc.toString())
        assertEquals(listOf(4 to 9, 10 to 10), view.selectionPairs())
    }

    @Test
    fun testMoveLineDownDoesNotIncludeTrailingLineAfterRange() {
        // Range ends exactly at the start of line 2, so the block is
        // line 1 only (line 2 is not dragged along).
        val view = createView("aaa\nbbb\nccc", listOf(range(0, 4)))
        assertEquals(true, moveLineDown(view))
        assertEquals("bbb\naaa\nccc", view.state.doc.toString())
        assertEquals(listOf(4 to 8), view.selectionPairs())
    }

    @Test
    fun testMoveLineDownClipsBlockAtEndOfDoc() {
        // Doc with no trailing newline: the swapped-in last line is
        // shorter, so the shifted range is clipped to the doc end.
        val view = createView("aaa\nbbb\nc", listOf(range(4, 7)))
        assertEquals(true, moveLineDown(view))
        assertEquals("aaa\nc\nbbb", view.state.doc.toString())
        assertEquals(listOf(6 to 9), view.selectionPairs())
    }

    @Test
    fun testMoveLineDownNoOpWhenAllBlocksAtDocEnd() {
        // Only block is the last line; nothing can move.
        val view = createView("aaa\nbbb", listOf(range(5)))
        assertEquals(false, moveLineDown(view))
        assertEquals("aaa\nbbb", view.state.doc.toString())
    }

    @Test
    fun testMoveLineUpSkipsFirstBlockButMovesOthers() {
        // Block on line 1 can't move up and is skipped; block on line 3
        // still moves. Returns true because something moved.
        val view = createView(
            "aaa\nbbb\nccc\nddd",
            listOf(range(1), range(9))
        )
        assertEquals(true, moveLineUp(view))
        assertEquals("aaa\nccc\nbbb\nddd", view.state.doc.toString())
    }

    @Test
    fun testCopyLineDown() {
        val view = createView("aaa\nbbb\nccc", cursor = 1)
        copyLineDown(view)
        assertEquals("aaa\naaa\nbbb\nccc", view.state.doc.toString())
    }

    @Test
    fun testCopyLineUp() {
        val view = createView("aaa\nbbb\nccc", cursor = 5)
        copyLineUp(view)
        assertEquals("aaa\nbbb\nbbb\nccc", view.state.doc.toString())
    }
}
