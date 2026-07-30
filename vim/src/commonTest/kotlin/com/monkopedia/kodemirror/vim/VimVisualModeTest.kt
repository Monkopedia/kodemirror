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

class VimVisualModeTest {

    // -------------------------------------------------------------------------
    // Existing tests
    // -------------------------------------------------------------------------

    @Test
    fun v_enters_visual_mode() = testVim(value = "hello world") { h ->
        h.doKeys("v")
        assertTrue(h.vim.visualMode)
        assertEquals(false, h.vim.visualLine)
    }

    @Test
    fun capitalV_enters_visual_line() = testVim(value = "hello\nworld") { h ->
        h.doKeys("V")
        assertTrue(h.vim.visualMode)
        assertTrue(h.vim.visualLine)
    }

    @Test
    fun v_then_esc_exits_visual() = testVim(value = "hello") { h ->
        h.doKeys("v")
        assertTrue(h.vim.visualMode)
        h.doKeys("<Esc>")
        assertEquals(false, h.vim.visualMode)
    }

    @Test
    fun visual_d_deletes_selection() = testVim(value = "abcdef", cursor = LinePos(0, 1)) { h ->
        h.doKeys("v", "l", "l", "d")
        assertEquals("aef", h.cm.getValue())
    }

    @Test
    fun visual_line_d_deletes_lines() = testVim(
        value = "foo\nbar\nbaz",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("V", "j", "d")
        assertEquals("baz", h.cm.getValue())
    }

    @Test
    fun visual_line_V_j_selects_exactly_two_lines() = testVim(
        value = "// Fibonacci sequence\nfunction fibonacci(n) {\n  if (n <= 1) return n;\n}\n",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("V", "j")
        assertTrue(h.vim.visualMode)
        assertTrue(h.vim.visualLine)
        val sel = h.cm.session.state.selection.main
        // Selection should span from start of line 0 to end of line 1
        assertEquals(0, sel.from.value, "selection from")
        assertEquals(45, sel.to.value, "selection to")
    }

    @Test
    fun visual_y_yanks_selection() = testVim(value = "abcdef") { h ->
        h.doKeys("v", "l", "l", "y")
        assertEquals(false, h.vim.visualMode)
        h.doKeys("$", "p")
        assertEquals("abcdefabc", h.cm.getValue())
    }

    // -------------------------------------------------------------------------
    // Step 3: testSelection tests
    // -------------------------------------------------------------------------

    @Test
    fun viw_middle_spc() = testSelection(
        before = "foo \tbAr\t baz",
        pos = Regex("A"),
        keys = "viw",
        expectedSel = "bAr"
    )

    @Test
    fun vaw_middle_spc() = testSelection(
        before = "foo \tbAr\t baz",
        pos = Regex("A"),
        keys = "vaw",
        expectedSel = "bAr\t "
    )

    @Test
    fun viw_middle_punct() = testSelection(
        before = "foo \tbAr,\t baz",
        pos = Regex("A"),
        keys = "viw",
        expectedSel = "bAr"
    )

    @Test
    fun vaW_middle_punct() = testSelection(
        before = "foo \tbAr,\t baz",
        pos = Regex("A"),
        keys = "vaW",
        expectedSel = "bAr,\t "
    )

    @Test
    fun viw_start_spc() = testSelection(
        before = "foo \tbAr\t baz",
        pos = Regex("b"),
        keys = "viw",
        expectedSel = "bAr"
    )

    @Test
    fun viw_end_spc() = testSelection(
        before = "foo \tbAr\t baz",
        pos = Regex("r"),
        keys = "viw",
        expectedSel = "bAr"
    )

    @Test
    fun viw_eol() = testSelection(
        before = "foo \tbAr",
        pos = Regex("r"),
        keys = "viw",
        expectedSel = "bAr"
    )

    @Test
    fun vi_brace_middle_spc() = testSelection(
        before = "a{\n\tbar\n\t}b",
        pos = Regex("r"),
        keys = "vi{",
        expectedSel = "\n\tbar\n\t"
    )

    @Test
    fun va_brace_middle_spc() = testSelection(
        before = "a{\n\tbar\n\t}b",
        pos = Regex("r"),
        keys = "va{",
        expectedSel = "{\n\tbar\n\t}"
    )

    @Test
    fun va_brace_outside() = testSelection(
        before = "xa{\n\tbar\n\t}b",
        pos = Regex("x"),
        keys = "va{",
        expectedSel = "{\n\tbar\n\t}"
    )

    // -------------------------------------------------------------------------
    // Step 6: Visual Block tests (lines 1214-1295)
    // -------------------------------------------------------------------------

    @Test
    fun c_visual_block() = testVim(value = "1234\n5678\nabcdefg") { h ->
        h.cm.setCursor(0, 1)
        h.doKeys("<C-v>", "2", "j", "l", "l", "l", "c")
        h.doKeys("h", "e", "l", "l", "o")
        assertEquals("1hello\n5hello\nahellofg", h.cm.getValue())
        h.doKeys("<Esc>")
        h.cm.setCursor(2, 3)
        h.doKeys("<C-v>", "2", "k", "h", "C")
        h.doKeys("w", "o", "r", "l", "d")
        assertEquals("1hworld\n5hworld\nahworld", h.cm.getValue())
    }

    @Test
    fun c_visual_block_replay() = testVim(value = "1234\n5678\nabcdefg") { h ->
        h.cm.setCursor(0, 1)
        h.doKeys("<C-v>", "2", "j", "l", "c")
        h.doKeys("f", "o")
        assertEquals("1fo4\n5fo8\nafodefg", h.cm.getValue())
        h.doKeys("<Esc>")
        h.cm.setCursor(0, 0)
        h.doKeys(".")
        assertEquals("foo4\nfoo8\nfoodefg", h.cm.getValue())
    }

    @Test
    fun I_visual_block_replay() = testVim(value = "1234\n5678\nabcdefg\nxyz") { h ->
        h.cm.setCursor(0, 2)
        h.doKeys("<C-v>", "2", "j", "l", "I")
        h.doKeys("+", "-")
        assertEquals("12+-34\n56+-78\nab+-cdefg\nxyz", h.cm.getValue())
        h.doKeys("<Esc>")
        // ensure that repeat location doesn't depend on last selection
        h.cm.setCursor(3, 2)
        h.doKeys("g", "v")
        assertEquals("+-34\n+-78\n+-cd", h.cm.getSelection())
        h.cm.setCursor(0, 3)
        h.doKeys("<C-v>", "1", "j", "2", "l")
        assertEquals("-34\n-78", h.cm.getSelection())
        h.cm.setCursor(0, 0)
        assertEquals("", h.cm.getSelection())
        h.doKeys("g", "v")
        assertEquals("-34\n-78", h.cm.getSelection())
        h.cm.setCursor(1, 1)
        h.doKeys(".")
        assertEquals("12+-34\n5+-6+-78\na+-b+-cdefg\nx+-yz", h.cm.getValue())
    }

    @Test
    fun visual_block_backwards() = testVim(
        value = "01234 line 1\n56789 line 2\nabcdefg line 3\nline 4"
    ) { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("3", "l")
        h.doKeys("<C-v>", "2", "j", "2", "<Left>")
        assertEquals("123\n678\nbcd", h.cm.getSelection())
        h.doKeys("A")
        h.assertCursorAt(0, 4)
        h.doKeys("A", "<Esc>")
        h.assertCursorAt(0, 4)
        h.doKeys("g", "v")
        assertEquals("123\n678\nbcd", h.cm.getSelection())
        h.doKeys("x")
        h.assertCursorAt(0, 1)
        h.doKeys("g", "v")
        assertEquals("A4 \nA9 \nAef", h.cm.getSelection())
    }

    @Test
    fun d_visual_block() = testVim(value = "1234\n5678\nabcdefg") { h ->
        h.cm.setCursor(0, 1)
        h.doKeys("<C-v>", "2", "j", "l", "l", "l", "d")
        assertEquals("1\n5\nafg", h.cm.getValue())
    }

    @Test
    fun D_visual_block() = testVim(value = "1234\n5678\nabcdefg") { h ->
        h.cm.setCursor(0, 1)
        h.doKeys("<C-v>", "2", "j", "l", "D")
        assertEquals("1\n5\na", h.cm.getValue())
    }

    @Test
    fun s_visual_block() = testVim(value = "1234\n5678\nabcdefg\n") { h ->
        h.cm.setCursor(0, 1)
        h.doKeys("<C-v>", "2", "j", "l", "l", "l", "s")
        h.doKeys("h", "e", "l", "l", "o", "{")
        assertEquals("1hello{\n5hello{\nahello{fg\n", h.cm.getValue())
        h.doKeys("<Esc>")
        h.cm.setCursor(2, 3)
        h.doKeys("<C-v>", "1", "k", "h", "S")
        h.doKeys("w", "o", "r", "l", "d")
        assertEquals("1hello{\n  world\n", h.cm.getValue())
    }

    // -------------------------------------------------------------------------
    // Visual block tilde & dot tests (lines 1437-1469)
    // -------------------------------------------------------------------------

    @Test
    fun visual_block_tilde() = testVim(value = "hello\nwOrld\nabcde") { h ->
        h.cm.setCursor(1, 1)
        h.doKeys("<C-v>", "l", "l", "j", "~")
        h.assertCursorAt(1, 1)
        assertEquals("hello\nwoRLd\naBCDe", h.cm.getValue())
        h.cm.setCursor(2, 0)
        h.doKeys("v", "l", "l", "~")
        h.assertCursorAt(2, 0)
        assertEquals("hello\nwoRLd\nAbcDe", h.cm.getValue())
    }

    @Test
    fun dot_swapCase_visualBlock() = testVim(value = "hEllo\nwOrlD\naBcDe") { h ->
        h.doKeys("<C-v>", "j", "j", "l", "~")
        h.cm.setCursor(0, 3)
        h.doKeys(".")
        assertEquals("HelLO\nWorLd\nAbcdE", h.cm.getValue())
    }

    @Test
    fun dot_delete_visualBlock() = testVim(value = "give\nme\nsome\nsugar") { h ->
        h.doKeys("<C-v>", "j", "x")
        assertEquals("ive\ne\nsome\nsugar", h.cm.getValue())
        h.doKeys(".")
        assertEquals("ve\n\nsome\nsugar", h.cm.getValue())
        h.doKeys("j", "j", ".")
        assertEquals("ve\n\nome\nugar", h.cm.getValue())
        h.doKeys("u")
        assertEquals("ve\n\nsome\nsugar", h.cm.getValue())
        h.doKeys("<C-r>")
        h.assertCursorAt(2, 0)
        assertEquals("ve\n\nome\nugar", h.cm.getValue())
        h.doKeys(".")
        h.assertCursorAt(2, 0)
        assertEquals("ve\n\nme\ngar", h.cm.getValue())
    }

    // -------------------------------------------------------------------------
    // A/I visual block (lines 1951-1981)
    // -------------------------------------------------------------------------

    @Test
    fun A_visual_block() = testVim(value = "test\nme\nplease") { h ->
        h.cm.setCursor(0, 1)
        h.doKeys("<C-v>", "2", "j", "l", "l", "A")
        h.doKeys("h", "e", "l", "l", "o")
        assertEquals("testhello\nmehello\npleahellose", h.cm.getValue())
    }

    @Test
    fun I_visual_block() = testVim(value = "test\nme\nplease") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("<C-v>", "2", "j", "l", "l", "I")
        h.doKeys("h", "e", "l", "l", "o")
        assertEquals("hellotest\nhellome\nhelloplease", h.cm.getValue())
    }

    // -------------------------------------------------------------------------
    // R_visual (line 2173)
    // -------------------------------------------------------------------------

    @Test
    fun R_visual() = testVim(value = "a11\na22\nb33\nc44\nc55") { h ->
        h.doKeys("<C-v>", "j", "R", "0", "<Esc>")
        assertEquals("0\nb33\nc44\nc55", h.cm.getValue())
        h.doKeys("2", "j", ".")
        assertEquals("0\nb33\n0", h.cm.getValue())
        h.doKeys("k", "v", "R", "1", "<Esc>")
        assertEquals("0\n1\n0", h.cm.getValue())
        h.doKeys("k", ".")
        assertEquals("1\n1\n0", h.cm.getValue())
        h.doKeys("p")
        assertEquals("1\n0\n1\n0", h.cm.getValue())
    }

    // -------------------------------------------------------------------------
    // Visual mode tests (lines 2471-2530)
    // -------------------------------------------------------------------------

    @Test
    fun visual() = testVim(value = "12345") { h ->
        h.doKeys("l", "v", "l", "l")
        h.assertCursorAt(0, 4)
        assertEquals(LinePos(0, 1), h.cm.getCursor("anchor"))
        h.doKeys("d")
        assertEquals("15", h.cm.getValue())
    }

    @Test
    fun visual_yank() = testVim(value = "a test for yank") { h ->
        h.doKeys("v", "3", "l", "y")
        h.assertCursorAt(0, 0)
        h.doKeys("p")
        assertEquals("aa te test for yank", h.cm.getValue())
    }

    @Test
    fun visual_w() = testVim(value = "motion test") { h ->
        h.doKeys("v", "w")
        assertEquals("motion t", h.cm.getSelection())
    }

    @Test
    fun visual_initial_selection() = testVim(value = "init") { h ->
        h.cm.setCursor(0, 1)
        h.doKeys("v")
        assertEquals("n", h.cm.getSelection())
    }

    @Test
    fun visual_crossover_left_1() = testVim(value = "cross") { h ->
        h.cm.setCursor(0, 2)
        h.doKeys("v", "l", "h", "h")
        assertEquals("ro", h.cm.getSelection())
    }

    @Test
    fun visual_crossover_left_2() = testVim(value = "cross") { h ->
        h.cm.setCursor(0, 2)
        h.doKeys("v", "h", "l", "l")
        assertEquals("os", h.cm.getSelection())
    }

    @Test
    fun visual_crossover_up() = testVim(value = "cross\ncross\ncross\ncross\ncross\n") { h ->
        h.cm.setCursor(3, 2)
        h.doKeys("v", "j", "k", "k")
        assertEquals(LinePos(2, 2), h.cm.getCursor("head"))
        assertEquals(LinePos(3, 3), h.cm.getCursor("anchor"))
        h.doKeys("k")
        assertEquals(LinePos(1, 2), h.cm.getCursor("head"))
        assertEquals(LinePos(3, 3), h.cm.getCursor("anchor"))
    }

    @Test
    fun visual_crossover_down() = testVim(value = "cross\ncross\ncross\ncross\ncross\n") { h ->
        h.cm.setCursor(1, 2)
        h.doKeys("v", "k", "j", "j")
        assertEquals(LinePos(2, 3), h.cm.getCursor("head"))
        assertEquals(LinePos(1, 2), h.cm.getCursor("anchor"))
        h.doKeys("j")
        assertEquals(LinePos(3, 3), h.cm.getCursor("head"))
        assertEquals(LinePos(1, 2), h.cm.getCursor("anchor"))
    }

    @Test
    fun visual_exit() = testVim(value = "hello\nworld\nfoo") { h ->
        h.doKeys("<C-v>", "l", "j", "j", "<Esc>")
        assertEquals(h.cm.getCursor("anchor"), h.cm.getCursor("head"))
        assertFalse(h.vim.visualMode)
    }

    @Test
    fun visual_line() = testVim(value = " 1\n 2\n 3\n 4\n 5") { h ->
        h.doKeys("l", "V", "l", "j", "j", "d")
        assertEquals(" 4\n 5", h.cm.getValue())
    }

    // -------------------------------------------------------------------------
    // Visual block geometry tests (lines 2530-2613)
    // -------------------------------------------------------------------------

    @Test
    fun visual_block_move_to_eol() = testVim(value = "123\n45\n6") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("<C-v>", "G", "$")
        val selections = h.cm.getSelections().joinToString(",")
        assertEquals("123,45,6", selections)
        // Checks that with cursor at Infinity, finding words backwards still works.
        h.doKeys("2", "k", "b")
        val selections2 = h.cm.getSelections().joinToString(",")
        assertEquals("1", selections2)
    }

    @Test
    fun visual_block_different_line_lengths() = testVim(value = "1234\n5678\nabcdefg") { h ->
        h.doKeys("<C-v>", "l", "j", "j", "6", "l", "d")
        h.doKeys("d", "d", "d", "d")
        assertEquals("", h.cm.getValue())
    }

    @Test
    fun visual_block_truncate_on_short_line() =
        testVim(value = "hello world\n{\nthis is\nsparta!") { h ->
            h.cm.setCursor(3, 4)
            h.doKeys("<C-v>", "l", "k", "k", "d")
            assertEquals("hello world\n{\ntis\nsa!", h.cm.getValue())
        }

    @Test
    fun visual_block_corners() = testVim(value = "12345\n67891\nabcde") { h ->
        h.cm.setCursor(1, 2)
        h.doKeys("<C-v>", "2", "l", "k")
        // circle around the anchor and check the selections
        var selections = h.cm.getSelections()
        assertEquals("345891", selections.joinToString(""))
        h.doKeys("4", "h")
        selections = h.cm.getSelections()
        assertEquals("123678", selections.joinToString(""))
        h.doKeys("j", "j")
        selections = h.cm.getSelections()
        assertEquals("678abc", selections.joinToString(""))
        h.doKeys("4", "l")
        selections = h.cm.getSelections()
        assertEquals("891cde", selections.joinToString(""))
    }

    @Test
    fun visual_block_mode_switch() = testVim(value = "12345\n67891\nabcde") { h ->
        // switch between visual modes
        h.cm.setCursor(1, 1)
        // blockwise to characterwise visual
        h.doKeys("<C-v>", "j", "l", "v")
        var selections = h.cm.getSelections()
        assertEquals("7891\nabc", selections.joinToString(""))
        // characterwise to blockwise
        h.doKeys("<C-v>")
        selections = h.cm.getSelections()
        assertEquals("78bc", selections.joinToString(""))
        // blockwise to linewise visual
        h.doKeys("V")
        selections = h.cm.getSelections()
        assertEquals("67891\nabcde", selections.joinToString(""))
    }

    @Test
    fun visual_block_crossing_short_line() =
        testVim(value = "123456\n78\nabcdefg\nfoobar\n}\n") { h ->
            // visual block with long and short lines
            h.cm.setCursor(0, 3)
            h.doKeys("<C-v>", "j", "j", "j")
            var selections = h.cm.getSelections().joinToString(",")
            assertEquals("4,,d,b", selections)
            h.doKeys("3", "k")
            selections = h.cm.getSelections().joinToString(",")
            assertEquals("4", selections)
            h.doKeys("5", "j", "k")
            selections = h.cm.getSelections().joinToString("")
            assertEquals(10, selections.length)
        }

    @Test
    fun visual_block_curPos_on_exit() = testVim(value = "123456\n78\nabcdefg\nfoobar") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("<C-v>", "3", "l", "<Esc>")
        assertEquals(LinePos(0, 3), h.cm.getCursor())
        h.doKeys("h", "<C-v>", "2", "j", "3", "l")
        assertEquals("3456,,cdef", h.cm.getSelections().joinToString(","))
        h.doKeys("4", "h")
        assertEquals("23,8,bc", h.cm.getSelections().joinToString(","))
        h.doKeys("2", "l")
        assertEquals("34,,cd", h.cm.getSelections().joinToString(","))
    }

    // -------------------------------------------------------------------------
    // Visual marks (line 2615)
    // -------------------------------------------------------------------------

    @Test
    fun visual_marks() = testVim { h ->
        h.doKeys("l", "v", "l", "l", "j", "j", "v")
        // Test visual mode marks
        h.cm.setCursor(2, 1)
        h.doKeys("'", "<")
        h.assertCursorAt(0, 1)
        h.doKeys("'", ">")
        h.assertCursorAt(2, 0)
    }

    // -------------------------------------------------------------------------
    // Visual join tests (lines 2624-2653)
    // -------------------------------------------------------------------------

    @Test
    fun visual_join() = testVim(value = " 1\n 2\n 3\n 4\n 5") { h ->
        h.doKeys("l", "V", "l", "j", "j", "J")
        assertEquals(" 1 2 3\n 4\n 5", h.cm.getValue())
        assertFalse(h.vim.visualMode)
    }

    @Test
    fun visual_join_2() = testVim(value = "1\n2\n3\n4\n5\n6\n") { h ->
        h.doKeys("G", "V", "g", "g", "J")
        assertEquals("1 2 3 4 5 6", h.cm.getValue())
        assertFalse(h.vim.visualMode)
    }

    @Test
    fun visual_join_blank() = testVim(value = "1 \n\t2\n\t  \n\n5\n 6\n") { h ->
        val initialValue = h.cm.getValue()
        h.doKeys("G", "V", "g", "g", "J")
        assertEquals("1  2 5 6", h.cm.getValue())
        assertFalse(h.vim.visualMode)
        h.doKeys("u")
        assertEquals(initialValue, h.cm.getValue())
        h.doKeys("G", "V", "g", "g", "g", "J")
        assertEquals("1 \t2\t  5 6", h.cm.getValue())
        h.doKeys("u")
        assertEquals(h.cm.getCursor().line, 0)
        assertEquals(initialValue, h.cm.getValue())
        h.doKeys("J", "J", "J")
        h.assertCursorAt(0, 3)
        h.doKeys("J")
        h.assertCursorAt(0, 4)
        assertEquals("1  2 5\n 6\n", h.cm.getValue())
        h.doKeys("u")
        assertEquals("1  2\n5\n 6\n", h.cm.getValue())
    }

    // -------------------------------------------------------------------------
    // visual_blank (line 2654)
    // -------------------------------------------------------------------------

    @Test
    fun visual_blank() = testVim(value = "\n") { h ->
        h.doKeys("v", "k")
        assertTrue(h.vim.visualMode)
    }

    // -------------------------------------------------------------------------
    // Reselect visual tests (lines 2658-2707)
    // -------------------------------------------------------------------------

    @Test
    fun reselect_visual() = testVim(value = "123456\nfoo\nbar") { h ->
        h.doKeys("l", "v", "l", "l", "l", "y", "g", "v")
        h.assertCursorAt(0, 5)
        assertEquals(LinePos(0, 1), h.cm.getCursor("anchor"))
        h.doKeys("v")
        h.cm.setCursor(1, 0)
        h.doKeys("v", "l", "l", "p")
        assertEquals("123456\n2345\nbar", h.cm.getValue())
        h.cm.setCursor(0, 0)
        h.doKeys("g", "v")
        // here the fake cursor is at (1, 3)
        h.assertCursorAt(1, 4)
        assertEquals(LinePos(1, 0), h.cm.getCursor("anchor"))
        h.doKeys("v")
        h.cm.setCursor(2, 0)
        h.doKeys("v", "l", "l", "g", "v")
        h.assertCursorAt(1, 4)
        assertEquals(LinePos(1, 0), h.cm.getCursor("anchor"))
        h.doKeys("g", "v")
        h.assertCursorAt(2, 3)
        assertEquals(LinePos(2, 0), h.cm.getCursor("anchor"))
        assertEquals("123456\n2345\nbar", h.cm.getValue())
    }

    @Test
    fun reselect_visual_line() = testVim(value = "hello\nthis\nis\nfoo\nand\nbar") { h ->
        h.doKeys("l", "V", "j", "j", "V", "g", "v", "d")
        assertEquals("foo\nand\nbar", h.cm.getValue())
        h.cm.setCursor(1, 0)
        h.doKeys("V", "y", "j")
        h.doKeys("V", "p", "g", "v", "d")
        assertEquals("foo\nand", h.cm.getValue())
    }

    @Test
    fun reselect_visual_block() = testVim(value = "123456\nfoo\nbar") { h ->
        h.cm.setCursor(1, 2)
        h.doKeys("<C-v>", "k", "h", "<C-v>")
        h.cm.setCursor(2, 1)
        h.doKeys("v", "l", "g", "v")
        assertEquals(LinePos(1, 2), h.vim.sel.anchor)
        assertEquals(LinePos(0, 1), h.vim.sel.head)
        // Ensure selection is done with visual block mode rather than one
        // continuous range.
        assertEquals("23oo", h.cm.getSelections().joinToString(""))
        h.doKeys("g", "v")
        assertEquals(LinePos(2, 1), h.vim.sel.anchor)
        assertEquals(LinePos(2, 2), h.vim.sel.head)
        h.doKeys("<Esc>")
        // Ensure selection of deleted range
        h.cm.setCursor(1, 1)
        h.doKeys("v", "<C-v>", "j", "d", "g", "v")
        assertEquals("or", h.cm.getSelections().joinToString(""))
    }

    // -------------------------------------------------------------------------
    // o_visual and o_visual_block (lines 2735-2758)
    // -------------------------------------------------------------------------

    @Test
    fun o_visual() = testVim(value = "abcd\nefgh\nijkl\nmnop") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("v", "l", "l", "l", "o")
        h.assertCursorAt(0, 0)
        h.doKeys("v", "v", "j", "j", "j", "o")
        h.assertCursorAt(0, 0)
        h.doKeys("O")
        h.doKeys("l", "l")
        h.assertCursorAt(3, 3)
        h.doKeys("d")
        assertEquals("p", h.cm.getValue())
    }

    @Test
    fun o_visual_block() = testVim(value = "abcd\nefgh\nijkl\nmnop") { h ->
        h.cm.setCursor(0, 1)
        h.doKeys("<C-v>", "3", "j", "l", "l", "o")
        assertEquals(LinePos(3, 3), h.vim.sel.anchor)
        assertEquals(LinePos(0, 1), h.vim.sel.head)
        h.doKeys("O")
        assertEquals(LinePos(3, 1), h.vim.sel.anchor)
        assertEquals(LinePos(0, 3), h.vim.sel.head)
        h.doKeys("o")
        assertEquals(LinePos(0, 3), h.vim.sel.anchor)
        assertEquals(LinePos(3, 1), h.vim.sel.head)
    }

    // -------------------------------------------------------------------------
    // changeCase_visual and changeCase_visual_block (lines 2759-2789)
    // -------------------------------------------------------------------------

    @Test
    fun changeCase_visual() =
        testVim(value = "abcdef\nghijkl\nmnopq\nshort line\nlong line of text") { h ->
            h.cm.setCursor(0, 0)
            h.doKeys("v", "l", "l")
            h.doKeys("U")
            h.assertCursorAt(0, 0)
            h.doKeys("v", "l", "l")
            h.doKeys("u")
            h.assertCursorAt(0, 0)
            h.doKeys("l", "l", "l", ".")
            h.assertCursorAt(0, 3)
            h.cm.setCursor(0, 0)
            h.doKeys("q", "a", "v", "j", "U", "q")
            h.assertCursorAt(0, 0)
            h.doKeys("j", "@", "a")
            h.assertCursorAt(1, 0)
            h.cm.setCursor(3, 0)
            h.doKeys("V", "U", "j", ".")
            assertEquals(
                "ABCDEF\nGHIJKL\nMnopq\nSHORT LINE\nLONG LINE OF TEXT",
                h.cm.getValue()
            )
        }

    @Test
    fun changeCase_visual_block() = testVim(value = "abcdef\nghijkl\nmnopq\nfoo") { h ->
        h.cm.setCursor(2, 1)
        h.doKeys("<C-v>", "k", "k", "h", "U")
        assertEquals("ABcdef\nGHijkl\nMNopq\nfoo", h.cm.getValue())
        h.cm.setCursor(0, 2)
        h.doKeys(".")
        assertEquals("ABCDef\nGHIJkl\nMNOPq\nfoo", h.cm.getValue())
        // check when last line is shorter.
        h.cm.setCursor(2, 2)
        h.doKeys(".")
        assertEquals("ABCDef\nGHIJkl\nMNOPq\nfoO", h.cm.getValue())
    }

    // -------------------------------------------------------------------------
    // visual_paste (line 2790)
    // -------------------------------------------------------------------------

    @Test
    fun visual_paste() = testVim(value = "this is a\nunit test for visual paste") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("v", "l", "l", "y")
        h.assertCursorAt(0, 0)
        h.doKeys("3", "l", "j", "v", "l", "p")
        h.assertCursorAt(1, 5)
        assertEquals("this is a\nunithitest for visual paste", h.cm.getValue())
        h.cm.setCursor(0, 0)
        // in case of pasting whole line
        h.doKeys("y", "y")
        h.cm.setCursor(1, 6)
        h.doKeys("v", "l", "l", "l", "p")
        h.assertCursorAt(2, 0)
        assertEquals("this is a\nunithi\nthis is a\n for visual paste", h.cm.getValue())
    }

    // -------------------------------------------------------------------------
    // S_visual (line 2874)
    // -------------------------------------------------------------------------

    @Test
    fun S_visual() = testVim(value = "aa\nbb\ncc") { h ->
        h.cm.setCursor(0, 1)
        h.doKeys("v", "j", "S")
        h.doKeys("<Esc>")
        h.assertCursorAt(0, 0)
        assertEquals("\ncc", h.cm.getValue())
    }

    // -------------------------------------------------------------------------
    // blockwise_paste tests (lines 2831-2872)
    // -------------------------------------------------------------------------

    @Test
    fun blockwise_paste() = testVim(value = "hello\nworld\nfoo\nbar") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("<C-v>", "3", "j", "l", "y")
        h.cm.setCursor(0, 2)
        // paste one char after the current cursor position
        h.doKeys("p")
        assertEquals("helhelo\nworwold\nfoofo\nbarba", h.cm.getValue())
        h.cm.setCursor(0, 0)
        h.doKeys("v", "4", "l", "y")
        h.cm.setCursor(0, 0)
        h.doKeys("<C-v>", "3", "j", "p")
        assertEquals("helheelhelo\norwold\noofo\narba", h.cm.getValue())
    }

    @Test
    fun blockwise_paste_long_short_line() = testVim(value = "hello\nfoo\nbar") { h ->
        // extend short lines in case of different line lengths.
        h.cm.setCursor(0, 0)
        h.doKeys("<C-v>", "j", "j", "y")
        h.cm.setCursor(0, 3)
        h.doKeys("p")
        assertEquals("hellho\nfoo f\nbar b", h.cm.getValue())
    }

    @Test
    fun blockwise_paste_cut_paste() = testVim(value = "cut\nand\npaste\nme") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("<C-v>", "2", "j", "x")
        h.cm.setCursor(0, 0)
        h.doKeys("P")
        assertEquals("cut\nand\npaste\nme", h.cm.getValue())
    }

    @Test
    fun blockwise_paste_from_register() = testVim(value = "foobar\nhello\nworld") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("<C-v>", "2", "j", "\"", "a", "y")
        h.cm.setCursor(0, 3)
        h.doKeys("\"", "a", "p")
        assertEquals("foobfar\nhellho\nworlwd", h.cm.getValue())
    }

    @Test
    fun blockwise_paste_last_line() = testVim(value = "cut\nand\npaste\nme") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("<C-v>", "2", "j", "l", "y")
        h.cm.setCursor(3, 0)
        h.doKeys("p")
        assertEquals("cut\nand\npaste\nmcue\n an\n pa", h.cm.getValue())
    }
}
