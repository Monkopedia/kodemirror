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
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchDebugTest {

    private fun makeEditor(text: String, cursorOffset: Int = 0): VimEditor {
        val state = EditorState.create(
            EditorStateConfig(
                doc = text.asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(cursorOffset)),
                extensions = extensionListOf(vimContextField, history())
            )
        )
        val session = EditorSession(state)
        val cm = VimEditor(session)
        Vim.maybeInitVimState_(cm)
        Vim.resetVimGlobalState_()
        resetVimContext(cm)
        return cm
    }

    @Test
    fun backward_search_cursor_from_end() {
        val text = "match nope match \n nope Match"
        val cm = makeEditor(text, cursorOffset = 0)
        val query = Regex("match", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

        // Create search cursor starting from end of document
        val endCursor = cm.getSearchCursor(query, LinePos(1, 11))
        val found = endCursor.find(true)
        val from = endCursor.from()
        assertEquals(LinePos(1, 6), from, "Backward from end should find Match at (1,6)")
    }

    @Test
    fun backward_search_cursor_from_zero() {
        val text = "match nope match \n nope Match"
        val cm = makeEditor(text, cursorOffset = 0)
        val query = Regex("match", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

        // Create search cursor starting from position 0
        val cursor = cm.getSearchCursor(query, LinePos(0, 0))
        val found = cursor.find(true)
        // Should be null since there's nothing before position 0
        assertEquals(null, found, "Backward from 0 should return null")
    }

    @Test
    fun findNext_backward_from_zero() {
        val text = "match nope match \n nope Match"
        val cm = makeEditor(text, cursorOffset = 0)
        val query = Regex("match", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

        // This should wrap around to the end and find "Match" at (1,6)
        val result = findNext(cm, prev = true, query = query, repeat = 1)
        assertEquals(LinePos(1, 6), result, "findNext backward from 0 should wrap to (1,6)")
    }

    @Test
    fun findNext_forward_from_zero() {
        val text = "match nope match \n nope Match"
        val cm = makeEditor(text, cursorOffset = 0)
        val query = Regex("match", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

        // Forward search from (0,0) should skip the match at (0,0) and find (0,11)
        val result = findNext(cm, prev = false, query = query, repeat = 1)
        assertEquals(LinePos(0, 11), result, "findNext forward from 0 should find (0,11)")
    }

    @Test
    fun doSearch_backward_integration() = testVim(
        value = "match nope match \n nope Match",
        cursor = LinePos(0, 0)
    ) { h ->
        // Check search state after ?match
        h.doKeys("?")
        for (ch in "match") h.doKeys(ch.toString())
        h.doKeys("Enter")

        val pos = h.cm.getCursor()
        assertEquals(
            LinePos(1, 6),
            LinePos(pos.line, pos.ch),
            "?match from (0,0) should wrap to (1,6) but got (${pos.line},${pos.ch})"
        )
    }
}
