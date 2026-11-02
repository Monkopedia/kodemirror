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

import com.monkopedia.kodemirror.language.CommentTokens
import com.monkopedia.kodemirror.language.commentTokens
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.allowMultipleSelections
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Default-keymap commands checked against `@codemirror/commands` 6.10.2 (#380).
 *
 * Every expected value is the output of the upstream package run on the same
 * document and selection (node, headless view: no layout, so vertical motion
 * is a no-op, which matches a headless [EditorSession]).
 */
class UpstreamParity380Test {

    private val comments = commentTokens.of(
        CommentTokens(line = "//", block = CommentTokens.BlockComment("/*", "*/"))
    )

    private fun session(
        doc: String,
        ranges: List<Pair<Int, Int>>,
        vararg ext: Extension
    ): EditorSession = EditorSession(
        EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.EditorSelectionSpec(
                    EditorSelection.create(
                        ranges.map { (a, h) -> EditorSelection.range(DocPos(a), DocPos(h)) },
                        0
                    )
                ),
                extensions = ExtensionList(listOf(allowMultipleSelections.of(true)) + ext)
            )
        )
    )

    /** Runs [cmd] and returns (returned, doc, selection) for one assertion. */
    private fun run(
        cmd: (EditorSession) -> Boolean,
        doc: String,
        ranges: List<Pair<Int, Int>>,
        vararg ext: Extension
    ): Triple<Boolean, String, List<Pair<Int, Int>>> {
        val view = session(doc, ranges, *ext)
        val ret = cmd(view)
        return Triple(
            ret,
            view.state.doc.toString(),
            view.state.selection.ranges.map { it.anchor.value to it.head.value }
        )
    }

    private fun at(pos: Int) = listOf(pos to pos)

    // --- deleteLine: threw on the last line ---

    @Test
    fun deleteLineOnLastLine() {
        assertEquals(Triple(true, "a", at(1)), run(deleteLine, "a\nb", at(2)))
        assertEquals(Triple(true, "a", at(1)), run(deleteLine, "a\nbc", at(3)))
    }

    @Test
    fun deleteLineSelectionSpanningLastTwoLines() {
        assertEquals(Triple(true, "a", at(1)), run(deleteLine, "a\nb\nc", listOf(2 to 5)))
    }

    @Test
    fun deleteLineMiddleAndOnlyLine() {
        assertEquals(Triple(true, "a\nc", at(1)), run(deleteLine, "a\nb\nc", at(2)))
        assertEquals(Triple(true, "", at(0)), run(deleteLine, "abc", at(1)))
    }

    @Test
    fun deleteLineCursorsOnAdjacentLines() {
        assertEquals(
            Triple(true, "c", at(0)),
            run(deleteLine, "a\nb\nc", listOf(0 to 0, 2 to 2))
        )
    }

    // --- moveLine: corrupted the document with cursors on adjacent lines ---

    @Test
    fun moveLineDownCursorsOnAdjacentLines() {
        assertEquals(
            Triple(true, "c\na\nb", listOf(2 to 2, 4 to 4)),
            run(moveLineDown, "a\nb\nc", listOf(0 to 0, 2 to 2))
        )
    }

    @Test
    fun moveLineUpCursorsOnAdjacentLines() {
        assertEquals(
            Triple(true, "b\nc\na", listOf(0 to 0, 2 to 2)),
            run(moveLineUp, "a\nb\nc", listOf(2 to 2, 4 to 4))
        )
    }

    // --- copyLine: only copied the main selection ---

    @Test
    fun copyLineDownMultiCursor() {
        assertEquals(
            Triple(true, "a\nb\na\nb", listOf(4 to 4, 6 to 6)),
            run(copyLineDown, "a\nb", listOf(0 to 0, 2 to 2))
        )
    }

    @Test
    fun copyLineUpMultiCursor() {
        assertEquals(
            Triple(true, "a\nb\na\nb", listOf(0 to 0, 2 to 2)),
            run(copyLineUp, "a\nb", listOf(0 to 0, 2 to 2))
        )
    }

    @Test
    fun copyLineDownCursorAtLineStartAndSelectionEndingAtLineStart() {
        assertEquals(Triple(true, "ab\nab", at(3)), run(copyLineDown, "ab", at(0)))
        assertEquals(
            Triple(true, "ab\nab\ncd", listOf(3 to 6)),
            run(copyLineDown, "ab\ncd", listOf(0 to 3))
        )
    }

    // --- transposeChars: split a surrogate pair ---

    @Test
    fun transposeCharsKeepsSurrogatePairWhole() {
        assertEquals(Triple(true, "😀a", at(3)), run(transposeChars, "a😀", at(1)))
    }

    @Test
    fun transposeCharsAtDocStartIsNoOp() {
        assertEquals(Triple(false, "ab", at(0)), run(transposeChars, "ab", at(0)))
    }

    @Test
    fun transposeCharsAcrossLineBreak() {
        assertEquals(Triple(true, "a\nbcd", at(3)), run(transposeChars, "ab\ncd", at(2)))
        assertEquals(Triple(true, "abc\nd", at(4)), run(transposeChars, "ab\ncd", at(3)))
    }

    // --- insertTab: deleted the selected text ---

    @Test
    fun insertTabWithSelectionIndents() {
        assertEquals(Triple(true, "  abc", listOf(2 to 5)), run(insertTab, "abc", listOf(0 to 3)))
        assertEquals(
            Triple(true, "  ab\n  cd", listOf(2 to 2, 7 to 9)),
            run(insertTab, "ab\ncd", listOf(0 to 0, 3 to 5))
        )
    }

    // --- indentMore / indentLess: only the main selection ---

    @Test
    fun indentMoreMultiCursor() {
        assertEquals(
            Triple(true, "  a\nb\n  c", listOf(2 to 2, 8 to 8)),
            run(indentMore, "a\nb\nc", listOf(0 to 0, 4 to 4))
        )
    }

    @Test
    fun indentLessMultiCursor() {
        assertEquals(
            Triple(true, "a\nb\nc", listOf(0 to 0, 4 to 4)),
            run(indentLess, "  a\nb\n  c", listOf(0 to 0, 6 to 6))
        )
    }

    // --- toggleComment (line): only the main selection ---

    @Test
    fun toggleCommentMultiCursor() {
        assertEquals(
            Triple(true, "// a\n// b", listOf(3 to 3, 8 to 8)),
            run(toggleComment, "a\nb", listOf(0 to 0, 2 to 2), comments)
        )
        assertEquals(
            Triple(true, "a\nb", listOf(0 to 0, 2 to 2)),
            run(toggleComment, "// a\n// b", listOf(0 to 0, 5 to 5), comments)
        )
    }

    @Test
    fun toggleCommentLineSelectionShape() {
        // Marker at the block's minimum indentation.
        assertEquals(
            Triple(true, "  // a\n  //   b", listOf(0 to 15)),
            run(toggleComment, "  a\n    b", listOf(0 to 9), comments)
        )
        // Blank line inside a multi-line block is left alone.
        assertEquals(
            Triple(true, "// a\n\n// b", listOf(3 to 10)),
            run(toggleComment, "a\n\nb", listOf(0 to 4), comments)
        )
        // A selection ending at a line start does not include that line.
        assertEquals(
            Triple(true, "// a\nb", listOf(3 to 5)),
            run(toggleComment, "a\nb", listOf(0 to 2), comments)
        )
    }
}
