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

class VimMacroTest {

    @Test
    fun qq_at_q_records_and_replays() = testVim(
        value = "            ",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("q", "q", "l", "l", "q")
        h.assertCursorAt(0, 2)
        h.doKeys("@", "q")
        h.assertCursorAt(0, 4)
    }

    @Test
    fun at_at_replays_last() = testVim(value = "            ", cursor = LinePos(0, 0)) { h ->
        h.doKeys("q", "q", "l", "l", "q")
        h.assertCursorAt(0, 2)
        h.doKeys("@", "q")
        h.assertCursorAt(0, 4)
        h.doKeys("@", "@")
        h.assertCursorAt(0, 6)
    }

    @Test
    fun macro_with_insert() = testVim(value = "aaa\nbbb\nccc", cursor = LinePos(0, 0)) { h ->
        h.doKeys("q", "a", "I", "x", "<Esc>", "j", "q")
        h.assertCursorAt(1, 0)
        h.doKeys("@", "a")
        assertEquals("xaaa\nxbbb\nccc", h.cm.getValue())
    }

    @Test
    fun macro_insert() = testVim(value = "", cursor = LinePos(0, 0)) { h ->
        h.doKeys("q", "a", "0", "i")
        h.doKeys("f", "o", "o")
        h.doKeys("<Esc>")
        h.doKeys("q", "@", "a")
        assertEquals("foofoo", h.cm.getValue())
    }

    @Test
    fun macro_insert_repeat() = testVim(value = "", cursor = LinePos(0, 0)) { h ->
        h.doKeys("q", "a", "$", "a")
        h.doKeys("l", "a", "r", "r", "y", ".")
        h.doKeys("<Esc>")
        h.doKeys("a")
        h.doKeys("c", "u", "r", "l", "y", ".")
        h.doKeys("<Esc>")
        h.doKeys("q")
        h.doKeys("a")
        h.doKeys("m", "o", "e", ".")
        h.doKeys("<Esc>")
        h.doKeys("@", "a")
        // At this point, the most recent edit should be the 2nd insert change
        // inside the macro, i.e. "curly.".
        h.doKeys(".")
        assertEquals("larry.curly.moe.larry.curly.curly.", h.cm.getValue())
    }

    @Test
    fun macro_space() = testVim(
        value = "one line of text.",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("<Space>", "<Space>")
        h.assertCursorAt(0, 2)
        h.doKeys("q", "a", "<Space>", "<Space>", "q")
        h.assertCursorAt(0, 4)
        h.doKeys("@", "a")
        h.assertCursorAt(0, 6)
        h.doKeys("@", "a")
        h.assertCursorAt(0, 8)
    }

    @Test
    fun macro_t_search() = testVim(
        value = "one line of text.",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("q", "a", "t", "e", "q")
        h.assertCursorAt(0, 1)
        h.doKeys("l", "@", "a")
        h.assertCursorAt(0, 6)
        h.doKeys("l", ";")
        h.assertCursorAt(0, 12)
    }

    @Test
    fun macro_f_search() = testVim(
        value = "one line of text.",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("q", "b", "f", "e", "q")
        h.assertCursorAt(0, 2)
        h.doKeys("@", "b")
        h.assertCursorAt(0, 7)
        h.doKeys(";")
        h.assertCursorAt(0, 13)
    }

    @Test
    fun macro_slash_search() = testVim(
        value = "one line of text.",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("q", "c")
        h.doKeys("/", "e", "Enter", "q")
        h.assertCursorAt(0, 2)
        h.doKeys("@", "c")
        h.assertCursorAt(0, 7)
        h.doKeys("n")
        h.assertCursorAt(0, 13)
    }

    @Test
    fun macro_multislash_search() = testVim(
        value = "one line of text to rule them all.",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("q", "d")
        h.doKeys("/", "e", "Enter")
        h.doKeys("/", "t", "Enter", "q")
        h.assertCursorAt(0, 12)
        h.doKeys("@", "d")
        h.assertCursorAt(0, 15)
    }

    @Test
    fun macro_last_ex_command_register() = testVim(
        value = "aaaaa",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doEx("s/a/b")
        h.doKeys("2", "@", ":")
        assertEquals("bbbaa", h.cm.getValue())
        h.assertCursorAt(0, 2)
    }

    @Test
    fun macro_last_run_macro() = testVim(value = "", cursor = LinePos(0, 0)) { h ->
        h.doKeys("q", "a", "C", "a", "<Esc>", "q")
        h.doKeys("q", "b", "C", "b", "<Esc>", "q")
        h.doKeys("@", "a")
        h.doKeys("d", "d")
        h.doKeys("@", "@")
        assertEquals("a", h.cm.getValue())
    }

    @Test
    fun macro_parens() = testVim(
        value = "see spot run",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("q", "z", "i")
        h.doKeys("(")
        h.doKeys("<Esc>")
        h.doKeys("e", "a")
        h.doKeys(")")
        h.doKeys("<Esc>")
        h.doKeys("q")
        h.doKeys("w", "@", "z")
        h.doKeys("w", "@", "z")
        assertEquals("(see) (spot) (run)", h.cm.getValue())
    }

    @Test
    fun macro_overwrite() = testVim(
        value = "see spot run",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("q", "z", "0", "i")
        h.doKeys("I", " ")
        h.doKeys("<Esc>")
        h.doKeys("q")
        h.doKeys("e")
        // Now replace the macro with something else.
        h.doKeys("q", "z", "a")
        h.doKeys(".")
        h.doKeys("<Esc>")
        h.doKeys("q")
        h.doKeys("e", "@", "z")
        h.doKeys("e", "@", "z")
        assertEquals("I see. spot. run.", h.cm.getValue())
    }

    @Test
    fun macro_search_f() = testVim(
        value = "The quick brown fox jumped over the lazy dog.",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("q", "a", "f", " ")
        h.assertCursorAt(0, 3)
        h.doKeys("q", "0")
        h.assertCursorAt(0, 0)
        h.doKeys("@", "a")
        h.assertCursorAt(0, 3)
    }

    @Test
    fun macro_search_2f() = testVim(
        value = "The quick brown fox jumped over the lazy dog.",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("q", "a", "2", "f", " ")
        h.assertCursorAt(0, 9)
        h.doKeys("q", "0")
        h.assertCursorAt(0, 0)
        h.doKeys("@", "a")
        h.assertCursorAt(0, 9)
    }

    @Test
    fun macro_yank_tick() = testVim(
        value = "the ex parrot",
        cursor = LinePos(0, 0)
    ) { h ->
        // Start recording a macro into the ' register.
        h.doKeys("q", "'")
        h.doKeys("y", "<Right>", "<Right>", "<Right>", "<Right>", "p")
        h.assertCursorAt(0, 4)
        assertEquals("the tex parrot", h.cm.getValue())
    }
}
