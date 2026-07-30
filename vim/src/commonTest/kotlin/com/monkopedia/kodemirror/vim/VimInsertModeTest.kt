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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VimInsertModeTest {

    @Test
    fun i_enters_insert_mode() = testVim(value = "abc") { h ->
        h.doKeys("i")
        assertEquals(true, h.vim.insertMode)
    }

    @Test
    fun a_enters_insert_after() = testVim(value = "abc") { h ->
        h.doKeys("a")
        assertEquals(true, h.vim.insertMode)
        h.assertCursorAt(0, 1)
    }

    @Test
    fun capitalI_inserts_at_first_nonblank() = testVim(
        value = "  abc",
        cursor = LinePos(0, 4)
    ) { h ->
        h.doKeys("I")
        assertEquals(true, h.vim.insertMode)
        h.assertCursorAt(0, 2)
    }

    @Test
    fun capitalA_inserts_at_eol() = testVim(value = "abc") { h ->
        h.doKeys("A")
        assertEquals(true, h.vim.insertMode)
        h.assertCursorAt(0, 3)
    }

    @Test
    fun o_opens_line_below() = testVim(value = "foo\nbar") { h ->
        h.doKeys("o")
        assertEquals("foo\n\nbar", h.cm.getValue())
        assertEquals(true, h.vim.insertMode)
        h.assertCursorAt(1, 0)
    }

    @Test
    fun capitalO_opens_line_above() = testVim(value = "foo\nbar") { h ->
        h.doKeys("O")
        assertEquals("\nfoo\nbar", h.cm.getValue())
        assertEquals(true, h.vim.insertMode)
        h.assertCursorAt(0, 0)
    }

    @Test
    fun esc_exits_insert_mode() = testVim(value = "abc") { h ->
        h.doKeys("i")
        assertEquals(true, h.vim.insertMode)
        h.doKeys("<Esc>")
        assertEquals(false, h.vim.insertMode)
    }

    // --- Ported from upstream vim_test.js ---

    @Test
    fun a() = testVim { h ->
        h.cm.setCursor(0, 1)
        h.doKeys("a")
        h.assertCursorAt(0, 2)
        assertTrue(h.vim.insertMode)
    }

    @Test
    fun a_eol() = testVim { h ->
        // lines[0] = " wOrd1 (#%" which has length 10
        h.cm.setCursor(0, 9)
        h.doKeys("a")
        h.assertCursorAt(0, 10)
        assertTrue(h.vim.insertMode)
    }

    @Test
    fun A_endOfSelectedArea() = testVim(value = "foo\nbar") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("v", "j", "l")
        h.doKeys("A")
        h.assertCursorAt(1, 2)
        assertTrue(h.vim.insertMode)
    }

    @Test
    fun i() = testVim { h ->
        h.cm.setCursor(0, 1)
        h.doKeys("i")
        h.assertCursorAt(0, 1)
        assertTrue(h.vim.insertMode)
    }

    @Test
    fun i_repeat() = testVim(value = "") { h ->
        h.doKeys("3", "i")
        h.doKeys("t", "e", "s", "t")
        h.doKeys("<Esc>")
        assertEquals("testtesttest", h.cm.getValue())
        h.assertCursorAt(0, 11)
    }

    @Test
    fun i_repeat_delete() = testVim(value = "abcde", cursor = LinePos(0, 4)) { h ->
        h.doKeys("2", "i")
        h.doKeys("z")
        h.doKeys("Backspace", "Backspace")
        h.doKeys("<Esc>")
        assertEquals("abe", h.cm.getValue())
        h.assertCursorAt(0, 1)
    }

    @Test
    fun i_backspace() = testVim(value = "0123456789", cursor = LinePos(0, 10)) { h ->
        h.doKeys("i")
        h.doKeys("Backspace")
        h.assertCursorAt(0, 9)
        assertEquals("012345678", h.cm.getValue())
    }

    @Test
    fun i_forward_delete() = testVim(value = "A1234\nBCD", cursor = LinePos(0, 3)) { h ->
        h.doKeys("i")
        h.doKeys("Delete")
        h.assertCursorAt(0, 3)
        assertEquals("A124\nBCD", h.cm.getValue())
        h.doKeys("Delete")
        h.assertCursorAt(0, 3)
        assertEquals("A12\nBCD", h.cm.getValue())
        h.doKeys("Delete")
        h.assertCursorAt(0, 3)
        assertEquals("A12BCD", h.cm.getValue())
    }

    @Test
    fun forward_delete() = testVim(value = "A1234\nBCD", cursor = LinePos(0, 3)) { h ->
        h.doKeys("<Del>")
        h.assertCursorAt(0, 3)
        assertEquals("A124\nBCD", h.cm.getValue())
        h.doKeys("<Del>")
        h.assertCursorAt(0, 2)
        assertEquals("A12\nBCD", h.cm.getValue())
        h.doKeys("<Del>")
        h.assertCursorAt(0, 1)
        assertEquals("A1\nBCD", h.cm.getValue())
    }

    @Test
    fun A() = testVim { h ->
        h.doKeys("A")
        // lines[0] = " wOrd1 (#%" which has length 10
        h.assertCursorAt(0, 10)
        assertTrue(h.vim.insertMode)
    }

    @Test
    fun I() = testVim { h ->
        h.cm.setCursor(0, 4)
        h.doKeys("I")
        // lines[0].textStart = 1
        h.assertCursorAt(0, 1)
        assertTrue(h.vim.insertMode)
    }

    @Test
    fun I_repeat() = testVim(value = "blah", cursor = LinePos(0, 1)) { h ->
        h.doKeys("3", "I")
        h.doKeys("t", "e", "s", "t")
        h.doKeys("<Esc>")
        assertEquals("testtesttestblah", h.cm.getValue())
        h.assertCursorAt(0, 11)
    }

    @Test
    fun o() = testVim(value = "word1\nword2", cursor = LinePos(0, 4)) { h ->
        h.doKeys("o")
        assertEquals("word1\n\nword2", h.cm.getValue())
        h.assertCursorAt(1, 0)
        assertTrue(h.vim.insertMode)
    }

    @Test
    fun o_repeat() = testVim(value = "") { h ->
        h.doKeys("3", "o")
        h.doKeys("t", "e", "s", "t")
        h.doKeys("<Esc>")
        assertEquals("\ntest\ntest\ntest", h.cm.getValue())
        h.assertCursorAt(3, 3)
    }

    @Test
    fun O() = testVim(value = "word1\nword2", cursor = LinePos(0, 4)) { h ->
        h.doKeys("O")
        assertEquals("\nword1\nword2", h.cm.getValue())
        h.assertCursorAt(0, 0)
        assertTrue(h.vim.insertMode)
    }

    @Test
    fun J() = testVim(value = "word1 \n    word2\nword3\n word4", cursor = LinePos(0, 4)) { h ->
        h.doKeys("J")
        val expectedValue = "word1  word2\nword3\n word4"
        assertEquals(expectedValue, h.cm.getValue())
        h.assertCursorAt(0, expectedValue.indexOf("word2") - 1)
    }

    @Test
    fun J_repeat() = testVim(
        value = "word1 \n    word2\nword3\n word4",
        cursor = LinePos(0, 4)
    ) { h ->
        h.doKeys("3", "J")
        val expectedValue = "word1  word2 word3\n word4"
        assertEquals(expectedValue, h.cm.getValue())
        h.assertCursorAt(0, expectedValue.indexOf("word3") - 1)
    }

    @Test
    fun gJ() = testVim(value = "word1\nword2 \n word3", cursor = LinePos(0, 4)) { h ->
        h.doKeys("g", "J")
        assertEquals("word1word2 \n word3", h.cm.getValue())
        h.assertCursorAt(0, 5)
        h.doKeys("g", "J")
        assertEquals("word1word2  word3", h.cm.getValue())
        h.assertCursorAt(0, 11)
    }

    @Test
    fun gi() = testVim(value = "12\n  xxxx", cursor = LinePos(1, 5)) { h ->
        h.doKeys("g", "I")
        h.doKeys("a", "a", "<Esc>", "k")
        assertEquals("12\naa  xxxx", h.cm.getValue())
        h.assertCursorAt(0, 1)
        h.doKeys("g", "i")
        h.assertCursorAt(1, 2)
        assertTrue(h.vim.insertMode)
    }

    @Test
    fun R() = testVim { h ->
        h.cm.setCursor(0, 1)
        h.doKeys("R")
        h.assertCursorAt(0, 1)
        assertTrue(h.vim.overwrite)
    }

    @Test
    fun insert_ctrl_o() = testVim(value = "one two three here") { h ->
        h.doKeys("i")
        assertTrue(h.vim.insertMode)
        h.doKeys("<C-o>")
        assertFalse(h.vim.insertMode)
        h.doKeys("3", "w")
        assertTrue(h.vim.insertMode)
        h.assertCursorAt(0, 14)
    }

    @Test
    fun insert_ctrl_u() = testVim(value = "word1/word2", cursor = LinePos(0, 10)) { h ->
        h.doKeys("a")
        h.doKeys("<C-u>")
        assertEquals("", h.cm.getValue())
        val register = h.getRegisterController().getRegister(null)
        assertEquals("word1/word2", register.toString())
        assertFalse(register.linewise)
        h.assertCursorAt(0, 0)
        assertTrue(h.vim.insertMode)
    }

    @Test
    fun insert_ctrl_w() = testVim(value = "word1/word2", cursor = LinePos(0, 10)) { h ->
        h.doKeys("a")
        h.doKeys("<C-w>")
        assertEquals("word1/", h.cm.getValue())
        val register = h.getRegisterController().getRegister(null)
        assertEquals("word2", register.toString())
        assertFalse(register.linewise)
        h.assertCursorAt(0, 6)
        assertTrue(h.vim.insertMode)
    }

    @Test
    fun normal_ctrl_w() = testVim(value = "word", cursor = LinePos(0, 3)) { h ->
        h.doKeys("<C-w>")
        assertEquals("word", h.cm.getValue())
        h.assertCursorAt(0, 3)
    }

    @Test
    fun s_normal() = testVim(value = "abc", cursor = LinePos(0, 1)) { h ->
        h.doKeys("s")
        h.doKeys("<Esc>")
        assertEquals("ac", h.cm.getValue())
    }

    @Test
    fun s_visual() = testVim(value = "abc", cursor = LinePos(0, 1)) { h ->
        h.doKeys("v", "s")
        h.doKeys("<Esc>")
        h.assertCursorAt(0, 0)
        assertEquals("ac", h.cm.getValue())
    }

    @Test
    fun S_normal() = testVim(value = "aa{\n  bb\ncc", cursor = LinePos(0, 1)) { h ->
        h.doKeys("j", "S")
        h.doKeys("<Esc>")
        h.assertCursorAt(1, 1)
        assertEquals("aa{\n  \ncc", h.cm.getValue())
        h.doKeys("j", "S")
        assertEquals("aa{\n  \n", h.cm.getValue())
        h.assertCursorAt(2, 0)
        h.doKeys("<Esc>")
        h.doKeys("d", "d", "d", "d")
        h.assertCursorAt(0, 0)
        h.doKeys("S")
        assertTrue(h.vim.insertMode)
        assertEquals("", h.cm.getValue())
    }

    @Test
    fun i_indent_right() = testVim(
        value = " word1\nword2\nword3 ",
        cursor = LinePos(0, 3)
    ) { h ->
        h.doKeys("i", "<C-t>")
        assertEquals("   word1\nword2\nword3 ", h.cm.getValue())
        h.assertCursorAt(0, 5)
    }

    @Test
    fun i_indent_left() = testVim(
        value = "   word1\nword2\nword3 ",
        cursor = LinePos(0, 3)
    ) { h ->
        h.doKeys("i", "<C-d>")
        assertEquals(" word1\nword2\nword3 ", h.cm.getValue())
        h.assertCursorAt(0, 1)
    }

    @Test
    fun indent_motion_right() = testVim(
        value = " word1\nword2\nword3 ",
        cursor = LinePos(1, 3)
    ) { h ->
        h.doKeys(">", "k")
        assertEquals("   word1\n  word2\nword3 ", h.cm.getValue())
        val register = h.getRegisterController().getRegister(null)
        assertEquals("", register.toString())
        assertFalse(register.linewise)
        h.assertCursorAt(0, 3)
    }

    @Test
    fun indent_line_right() = testVim(
        value = " word1\nword2\nword3 ",
        cursor = LinePos(0, 3)
    ) { h ->
        h.doKeys("2", ">", ">")
        assertEquals("   word1\n  word2\nword3 ", h.cm.getValue())
        val register = h.getRegisterController().getRegister(null)
        assertEquals("", register.toString())
        assertFalse(register.linewise)
        h.assertCursorAt(0, 3)
    }

    @Test
    fun indent_motion_left() = testVim(
        value = "   word1\n  word2\nword3 ",
        cursor = LinePos(1, 3)
    ) { h ->
        h.doKeys("<", "k")
        assertEquals(" word1\nword2\nword3 ", h.cm.getValue())
        val register = h.getRegisterController().getRegister(null)
        assertEquals("", register.toString())
        assertFalse(register.linewise)
        h.assertCursorAt(0, 1)
    }

    @Test
    fun indent_line_left() = testVim(
        value = "   word1\n  word2\nword3 ",
        cursor = LinePos(0, 3)
    ) { h ->
        h.doKeys("2", "<", "<")
        assertEquals(" word1\nword2\nword3 ", h.cm.getValue())
        val register = h.getRegisterController().getRegister(null)
        assertEquals("", register.toString())
        assertFalse(register.linewise)
        h.assertCursorAt(0, 1)
    }
}
