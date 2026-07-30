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

import kotlin.test.Test
import kotlin.test.assertEquals

class VimMarkTest {

    // -----------------------------------------------------------------------
    // Jumplist scene and tests
    // -----------------------------------------------------------------------

    private val jumplistScene =
        "word\n(word)\n{word\nword.\n\nword search\n}word\nword\nword\n"

    @Test
    fun jumplist_G() = testJumplist(
        keys = listOf("G", "<C-o>"),
        endPos = LinePos(5, 2),
        startPos = LinePos(5, 2),
        value = jumplistScene
    )

    @Test
    fun jumplist_gg() = testJumplist(
        keys = listOf("g", "g", "<C-o>"),
        endPos = LinePos(5, 2),
        startPos = LinePos(5, 2),
        value = jumplistScene
    )

    @Test
    fun jumplist_percent() = testJumplist(
        keys = listOf("%", "<C-o>"),
        endPos = LinePos(1, 5),
        startPos = LinePos(1, 5),
        value = jumplistScene
    )

    @Test
    fun jumplist_open_brace() = testJumplist(
        keys = listOf("{", "<C-o>"),
        endPos = LinePos(1, 5),
        startPos = LinePos(1, 5),
        value = jumplistScene
    )

    @Test
    fun jumplist_close_brace() = testJumplist(
        keys = listOf("}", "<C-o>"),
        endPos = LinePos(1, 5),
        startPos = LinePos(1, 5),
        value = jumplistScene
    )

    @Test
    fun jumplist_single_quote() = testJumplist(
        keys = listOf("m", "a", "h", "'", "a", "h", "<C-i>"),
        endPos = LinePos(1, 0),
        startPos = LinePos(1, 5),
        value = jumplistScene
    )

    @Test
    fun jumplist_backtick() = testJumplist(
        keys = listOf("m", "a", "h", "`", "a", "h", "<C-i>"),
        endPos = LinePos(1, 5),
        startPos = LinePos(1, 5),
        value = jumplistScene
    )

    @Test
    fun jumplist_star_cachedCursor() = testJumplist(
        keys = listOf("*", "<C-o>"),
        endPos = LinePos(1, 3),
        startPos = LinePos(1, 3),
        value = jumplistScene
    )

    @Test
    fun jumplist_hash_cachedCursor() = testJumplist(
        keys = listOf("#", "<C-o>"),
        endPos = LinePos(1, 3),
        startPos = LinePos(1, 3),
        value = jumplistScene
    )

    @Test
    fun jumplist_n() = testJumplist(
        keys = listOf("#", "n", "<C-o>"),
        endPos = LinePos(1, 1),
        startPos = LinePos(2, 3),
        value = jumplistScene
    )

    @Test
    fun jumplist_N() = testJumplist(
        keys = listOf("#", "N", "<C-o>"),
        endPos = LinePos(1, 1),
        startPos = LinePos(2, 3),
        value = jumplistScene
    )

    @Test
    fun jumplist_repeat_ctrl_o() = testJumplist(
        keys = listOf("*", "*", "*", "3", "<C-o>"),
        endPos = LinePos(2, 3),
        startPos = LinePos(2, 3),
        value = jumplistScene
    )

    @Test
    fun jumplist_repeat_ctrl_i() = testJumplist(
        keys = listOf("*", "*", "*", "3", "<C-o>", "2", "<C-i>"),
        endPos = LinePos(5, 0),
        startPos = LinePos(2, 3),
        value = jumplistScene
    )

    @Test
    fun jumplist_repeated_motion() = testJumplist(
        keys = listOf("3", "*", "<C-o>"),
        endPos = LinePos(2, 3),
        startPos = LinePos(2, 3),
        value = jumplistScene
    )

    @Test
    fun jumplist_forward_search() = testJumplist(
        keys = listOf("/", "d", "i", "a", "l", "o", "g", "Enter", "<C-o>"),
        endPos = LinePos(2, 3),
        startPos = LinePos(2, 3),
        value = jumplistScene
    )

    @Test
    fun jumplist_backward_search() = testJumplist(
        keys = listOf("?", "d", "i", "a", "l", "o", "g", "Enter", "<C-o>"),
        endPos = LinePos(2, 3),
        startPos = LinePos(2, 3),
        value = jumplistScene
    )

    @Test
    fun jumplist_skip_deleted_mark_ctrl_o() = testJumplist(
        keys = listOf("*", "n", "n", "k", "d", "k", "<C-o>", "<C-o>", "<C-o>"),
        endPos = LinePos(0, 2),
        startPos = LinePos(0, 2),
        value = jumplistScene
    )

    @Test
    fun jumplist_skip_deleted_mark_ctrl_i() = testJumplist(
        keys = listOf("*", "n", "n", "k", "d", "k", "<C-o>", "<C-i>", "<C-i>"),
        endPos = LinePos(1, 0),
        startPos = LinePos(0, 2),
        value = jumplistScene
    )

    // -----------------------------------------------------------------------
    // Mark tests
    // -----------------------------------------------------------------------

    @Test
    fun mark() = testVim { h ->
        h.cm.setCursor(2, 2)
        h.doKeys("m", "t")
        h.cm.setCursor(0, 0)
        h.doKeys("`", "t")
        h.assertCursorAt(2, 2)
        h.cm.setCursor(2, 0)
        h.cm.replaceRange("   h", h.cm.getCursor())
        h.cm.setCursor(0, 0)
        h.doKeys("'", "t")
        h.assertCursorAt(2, 3)
    }

    @Test
    fun mark_single_quote() = testVim { h ->
        // motions that do not update jumplist
        h.cm.setCursor(2, 2)
        h.doKeys("`", "'")
        h.assertCursorAt(0, 0)
        h.doKeys("j", "3", "l")
        h.doKeys("`", "`")
        h.assertCursorAt(2, 2)
        h.doKeys("`", "`")
        h.assertCursorAt(1, 3)
        // motions that update jumplist
        h.doKeys("/", "=", "Enter")
        h.assertCursorAt(6, 20)
        h.doKeys("`", "`")
        h.assertCursorAt(1, 3)
        h.doKeys("'", "'")
        h.assertCursorAt(6, 2)
        h.doKeys("'", "`")
        h.assertCursorAt(1, 1)
        // edits
        h.doKeys("g", "I", "Enter", "<Esc>", "l")
        // the column may be different depending on editor behavior in insert mode
        val ch = h.cm.getCursor().ch
        h.doKeys("`", "`")
        h.assertCursorAt(7, 2)
        h.doKeys("`", "`")
        h.assertCursorAt(2, ch)
    }

    @Test
    fun mark_dot() = testVim { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("O", "t", "e", "s", "t", "i", "n", "g", "<Esc>")
        h.cm.setCursor(3, 3)
        h.doKeys("'", ".")
        h.assertCursorAt(0, 0)
        h.cm.setCursor(4, 4)
        h.doKeys("`", ".")
        h.assertCursorAt(0, 6)
    }

    // -----------------------------------------------------------------------
    // jumpToMark tests
    // -----------------------------------------------------------------------

    @Test
    fun jumpToMark_next() = testVim { h ->
        h.cm.setCursor(2, 2)
        h.doKeys("m", "t")
        h.cm.setCursor(0, 0)
        h.doKeys("]", "`")
        h.assertCursorAt(2, 2)
        h.cm.setCursor(0, 0)
        h.doKeys("]", "'")
        h.assertCursorAt(2, 0)
    }

    @Test
    fun jumpToMark_next_repeat() = testVim { h ->
        h.cm.setCursor(2, 2)
        h.doKeys("m", "a")
        h.cm.setCursor(3, 2)
        h.doKeys("m", "b")
        h.cm.setCursor(4, 2)
        h.doKeys("m", "c")
        h.cm.setCursor(0, 0)
        h.doKeys("2", "]", "`")
        h.assertCursorAt(3, 2)
        h.cm.setCursor(0, 0)
        h.doKeys("2", "]", "'")
        h.assertCursorAt(3, 1)
    }

    @Test
    fun jumpToMark_next_sameline() = testVim { h ->
        h.cm.setCursor(2, 0)
        h.doKeys("m", "a")
        h.cm.setCursor(2, 4)
        h.doKeys("m", "b")
        h.cm.setCursor(2, 2)
        h.doKeys("]", "`")
        h.assertCursorAt(2, 4)
    }

    @Test
    fun jumpToMark_next_onlyprev() = testVim { h ->
        h.cm.setCursor(2, 0)
        h.doKeys("m", "a")
        h.cm.setCursor(4, 0)
        h.doKeys("]", "`")
        h.assertCursorAt(4, 0)
    }

    @Test
    fun jumpToMark_next_nomark() = testVim { h ->
        h.cm.setCursor(2, 2)
        h.doKeys("]", "`")
        h.assertCursorAt(2, 2)
        h.doKeys("]", "'")
        h.assertCursorAt(2, 0)
    }

    @Test
    fun jumpToMark_next_linewise_over() = testVim { h ->
        h.cm.setCursor(2, 2)
        h.doKeys("m", "a")
        h.cm.setCursor(3, 4)
        h.doKeys("m", "b")
        h.cm.setCursor(2, 1)
        h.doKeys("]", "'")
        h.assertCursorAt(3, 1)
    }

    @Test
    fun jumpToMark_next_action() = testVim { h ->
        h.cm.setCursor(2, 2)
        h.doKeys("m", "t")
        h.cm.setCursor(0, 0)
        h.doKeys("d", "]", "`")
        h.assertCursorAt(0, 0)
        val actual = h.cm.getLine(0)
        val expected = "pop pop 0 1 2 3 4"
        assertEquals(expected, actual, "Deleting while jumping to the next mark failed.")
    }

    @Test
    fun jumpToMark_next_line_action() = testVim { h ->
        h.cm.setCursor(2, 2)
        h.doKeys("m", "t")
        h.cm.setCursor(0, 0)
        h.doKeys("d", "]", "'")
        h.assertCursorAt(0, 1)
        val actual = h.cm.getLine(0)
        val expected = " (a) [b] {c} "
        assertEquals(expected, actual, "Deleting while jumping to the next mark line failed.")
    }

    @Test
    fun jumpToMark_prev() = testVim { h ->
        h.cm.setCursor(2, 2)
        h.doKeys("m", "t")
        h.cm.setCursor(4, 0)
        h.doKeys("[", "`")
        h.assertCursorAt(2, 2)
        h.cm.setCursor(4, 0)
        h.doKeys("[", "'")
        h.assertCursorAt(2, 0)
    }

    @Test
    fun jumpToMark_prev_repeat() = testVim { h ->
        h.cm.setCursor(2, 2)
        h.doKeys("m", "a")
        h.cm.setCursor(3, 2)
        h.doKeys("m", "b")
        h.cm.setCursor(4, 2)
        h.doKeys("m", "c")
        h.cm.setCursor(5, 0)
        h.doKeys("2", "[", "`")
        h.assertCursorAt(3, 2)
        h.cm.setCursor(5, 0)
        h.doKeys("2", "[", "'")
        h.assertCursorAt(3, 1)
    }

    @Test
    fun jumpToMark_prev_sameline() = testVim { h ->
        h.cm.setCursor(2, 0)
        h.doKeys("m", "a")
        h.cm.setCursor(2, 4)
        h.doKeys("m", "b")
        h.cm.setCursor(2, 2)
        h.doKeys("[", "`")
        h.assertCursorAt(2, 0)
    }

    @Test
    fun jumpToMark_prev_onlynext() = testVim { h ->
        h.cm.setCursor(4, 4)
        h.doKeys("m", "a")
        h.cm.setCursor(2, 0)
        h.doKeys("[", "`")
        h.assertCursorAt(2, 0)
    }

    @Test
    fun jumpToMark_prev_nomark() = testVim { h ->
        h.cm.setCursor(2, 2)
        h.doKeys("[", "`")
        h.assertCursorAt(2, 2)
        h.doKeys("[", "'")
        h.assertCursorAt(2, 0)
    }

    @Test
    fun jumpToMark_prev_linewise_over() = testVim { h ->
        h.cm.setCursor(2, 2)
        h.doKeys("m", "a")
        h.cm.setCursor(3, 4)
        h.doKeys("m", "b")
        h.cm.setCursor(3, 6)
        h.doKeys("[", "'")
        h.assertCursorAt(2, 0)
    }

    // -----------------------------------------------------------------------
    // delmark tests
    // -----------------------------------------------------------------------

    @Test
    fun delmark_single() = testVim { h ->
        h.cm.setCursor(1, 2)
        h.doKeys("m", "t")
        h.doEx("delmarks t")
        h.cm.setCursor(0, 0)
        h.doKeys("`", "t")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun delmark_range() = testVim { h ->
        h.cm.setCursor(1, 2)
        h.doKeys("m", "a")
        h.cm.setCursor(2, 2)
        h.doKeys("m", "b")
        h.cm.setCursor(3, 2)
        h.doKeys("m", "c")
        h.cm.setCursor(4, 2)
        h.doKeys("m", "d")
        h.cm.setCursor(5, 2)
        h.doKeys("m", "e")
        h.doEx("delmarks b-d")
        h.cm.setCursor(0, 0)
        h.doKeys("`", "a")
        h.assertCursorAt(1, 2)
        h.doKeys("`", "b")
        h.assertCursorAt(1, 2)
        h.doKeys("`", "c")
        h.assertCursorAt(1, 2)
        h.doKeys("`", "d")
        h.assertCursorAt(1, 2)
        h.doKeys("`", "e")
        h.assertCursorAt(5, 2)
    }

    @Test
    fun delmark_multi() = testVim { h ->
        h.cm.setCursor(1, 2)
        h.doKeys("m", "a")
        h.cm.setCursor(2, 2)
        h.doKeys("m", "b")
        h.cm.setCursor(3, 2)
        h.doKeys("m", "c")
        h.cm.setCursor(4, 2)
        h.doKeys("m", "d")
        h.cm.setCursor(5, 2)
        h.doKeys("m", "e")
        h.doEx("delmarks bcd")
        h.cm.setCursor(0, 0)
        h.doKeys("`", "a")
        h.assertCursorAt(1, 2)
        h.doKeys("`", "b")
        h.assertCursorAt(1, 2)
        h.doKeys("`", "c")
        h.assertCursorAt(1, 2)
        h.doKeys("`", "d")
        h.assertCursorAt(1, 2)
        h.doKeys("`", "e")
        h.assertCursorAt(5, 2)
    }

    @Test
    fun delmark_multi_space() = testVim { h ->
        h.cm.setCursor(1, 2)
        h.doKeys("m", "a")
        h.cm.setCursor(2, 2)
        h.doKeys("m", "b")
        h.cm.setCursor(3, 2)
        h.doKeys("m", "c")
        h.cm.setCursor(4, 2)
        h.doKeys("m", "d")
        h.cm.setCursor(5, 2)
        h.doKeys("m", "e")
        h.doEx("delmarks b c d")
        h.cm.setCursor(0, 0)
        h.doKeys("`", "a")
        h.assertCursorAt(1, 2)
        h.doKeys("`", "b")
        h.assertCursorAt(1, 2)
        h.doKeys("`", "c")
        h.assertCursorAt(1, 2)
        h.doKeys("`", "d")
        h.assertCursorAt(1, 2)
        h.doKeys("`", "e")
        h.assertCursorAt(5, 2)
    }

    @Test
    fun delmark_all() = testVim { h ->
        h.cm.setCursor(1, 2)
        h.doKeys("m", "a")
        h.cm.setCursor(2, 2)
        h.doKeys("m", "b")
        h.cm.setCursor(3, 2)
        h.doKeys("m", "c")
        h.cm.setCursor(4, 2)
        h.doKeys("m", "d")
        h.cm.setCursor(5, 2)
        h.doKeys("m", "e")
        h.doEx("delmarks a b-de")
        h.cm.setCursor(0, 0)
        h.doKeys("`", "a")
        h.assertCursorAt(0, 0)
        h.doKeys("`", "b")
        h.assertCursorAt(0, 0)
        h.doKeys("`", "c")
        h.assertCursorAt(0, 0)
        h.doKeys("`", "d")
        h.assertCursorAt(0, 0)
        h.doKeys("`", "e")
        h.assertCursorAt(0, 0)
    }
}
