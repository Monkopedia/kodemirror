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

class VimMiscTest {

    @Test
    fun mark_and_jump() = testVim(value = "foo\nbar\nbaz") { h ->
        h.doKeys("j") // go to line 1
        h.doKeys("m", "a") // set mark 'a'
        h.doKeys("G") // go to last line
        h.doKeys("'", "a") // jump to mark 'a'
        h.assertCursorAt(1, 0)
    }

    @Test
    fun backtick_mark_preserves_column() = testVim(
        value = "foo\nbar\nbaz",
        cursor = LinePos(1, 2)
    ) { h ->
        h.doKeys("m", "a")
        h.doKeys("G")
        h.doKeys("`", "a")
        h.assertCursorAt(1, 2)
    }

    @Test
    fun dot_repeats_last_edit() = testVim(value = "foo\nbar\nbaz") { h ->
        h.doKeys("d", "d") // delete line
        assertEquals("bar\nbaz", h.cm.getValue())
        h.doKeys(".") // repeat
        assertEquals("baz", h.cm.getValue())
    }

    @Test
    fun u_undoes_last_change() = testVim(value = "foo bar") { h ->
        h.doKeys("d", "w")
        assertEquals("bar", h.cm.getValue())
        h.doKeys("u")
        assertEquals("foo bar", h.cm.getValue())
    }

    @Test
    fun ctrl_r_redoes() = testVim(value = "foo bar") { h ->
        h.doKeys("d", "w")
        assertEquals("bar", h.cm.getValue())
        h.doKeys("u")
        assertEquals("foo bar", h.cm.getValue())
        h.doKeys("<C-r>")
        assertEquals("bar", h.cm.getValue())
    }

    @Test
    fun j_joins_lines() = testVim(value = "foo\nbar\nbaz") { h ->
        h.doKeys("J")
        assertEquals("foo bar\nbaz", h.cm.getValue())
    }

    @Test
    fun j_with_count() = testVim(value = "foo\nbar\nbaz\nqux") { h ->
        h.doKeys("3", "J")
        assertEquals("foo bar baz\nqux", h.cm.getValue())
    }

    @Test
    fun r_replaces_char() = testVim(value = "abc") { h ->
        h.doKeys("r", "x")
        assertEquals("xbc", h.cm.getValue())
    }

    @Test
    fun r_with_count() = testVim(value = "abcde") { h ->
        h.doKeys("3", "r", "x")
        assertEquals("xxxde", h.cm.getValue())
    }

    @Test
    fun p_pastes_after() = testVim(value = "abc", cursor = LinePos(0, 0)) { h ->
        h.doKeys("x") // delete 'a', yanked to unnamed register
        h.doKeys("p") // paste after
        assertEquals("bac", h.cm.getValue())
    }

    @Test
    fun capitalP_pastes_before() = testVim(value = "abc", cursor = LinePos(0, 1)) { h ->
        h.doKeys("x") // delete 'b'
        h.doKeys("P") // paste before cursor
        assertEquals("abc", h.cm.getValue())
    }

    @Test
    fun ctrl_a_increments_number() = testVim(value = "foo 42 bar", cursor = LinePos(0, 4)) { h ->
        h.doKeys("<C-a>")
        assertEquals("foo 43 bar", h.cm.getValue())
    }

    @Test
    fun ctrl_x_decrements_number() = testVim(value = "foo 42 bar", cursor = LinePos(0, 4)) { h ->
        h.doKeys("<C-x>")
        assertEquals("foo 41 bar", h.cm.getValue())
    }

    // --- Step 10: Dot Repeat Tests ---

    @Test
    fun dot_normal() = testVim(value = "1 2 3 4 5 6") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("2", "d", "w")
        h.doKeys(".")
        assertEquals("5 6", h.cm.getValue())
    }

    @Test
    fun dot_repeat() = testVim(value = "1 2 3 4 5 6") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("2", "d", "w")
        h.doKeys("3", ".")
        assertEquals("6", h.cm.getValue())
    }

    @Test
    fun dot_insert() = testVim(value = "") { h ->
        h.doKeys("i")
        h.doKeys("t", "e", "s", "t")
        h.doKeys("<Esc>")
        h.doKeys(".")
        assertEquals("testestt", h.cm.getValue())
        h.assertCursorAt(0, 6)
    }

    @Test
    fun dot_insert_repeat() = testVim(value = "") { h ->
        h.doKeys("i")
        h.doKeys("t", "e", "s", "t")
        h.cm.setCursor(0, 4)
        h.doKeys("<Esc>")
        h.doKeys("2", ".")
        assertEquals("testesttestt", h.cm.getValue())
        h.assertCursorAt(0, 10)
    }

    @Test
    fun dot_repeat_insert() = testVim(value = "") { h ->
        h.doKeys("3", "i")
        h.doKeys("t", "e")
        h.cm.setCursor(0, 2)
        h.doKeys("<Esc>")
        h.doKeys(".")
        assertEquals("tetettetetee", h.cm.getValue())
        h.assertCursorAt(0, 10)
    }

    @Test
    fun dot_insert_o() = testVim(value = "") { h ->
        h.doKeys("o")
        h.doKeys("z")
        h.cm.setCursor(1, 1)
        h.doKeys("<Esc>")
        h.doKeys(".")
        assertEquals("\nz\nz", h.cm.getValue())
        h.assertCursorAt(2, 0)
    }

    @Test
    fun dot_insert_o_repeat() = testVim(value = "") { h ->
        h.doKeys("o")
        h.doKeys("z")
        h.doKeys("<Esc>")
        h.cm.setCursor(1, 0)
        h.doKeys("2", ".")
        assertEquals("\nz\nz\nz", h.cm.getValue())
        h.assertCursorAt(3, 0)
    }

    @Test
    fun dot_insert_o_indent() = testVim(value = "{") { h ->
        h.doKeys("o")
        h.doKeys("z")
        h.doKeys("<Esc>")
        h.cm.setCursor(1, 2)
        h.doKeys(".")
        assertEquals("{\n  z\n  z", h.cm.getValue())
        h.assertCursorAt(2, 2)
    }

    @Test
    fun dot_insert_cw() = testVim(value = "word1 word2 word3") { h ->
        h.doKeys("c", "w")
        h.doKeys("t", "e", "s", "t")
        h.doKeys("<Esc>")
        h.cm.setCursor(0, 3)
        h.doKeys("2", "l")
        h.doKeys(".")
        assertEquals("test test word3", h.cm.getValue())
        h.assertCursorAt(0, 8)
    }

    @Test
    fun dot_insert_cw_repeat() = testVim(value = "word1 word2 word3") { h ->
        h.doKeys("c", "w")
        h.doKeys("t", "e", "s", "t")
        h.doKeys("<Esc>")
        h.cm.setCursor(0, 4)
        h.doKeys("l")
        h.doKeys("2", ".")
        assertEquals("test test", h.cm.getValue())
        h.assertCursorAt(0, 8)
    }

    @Test
    fun cw_insert_is_single_undo_unit_issue23() = testVim(value = "word1 word2") { h ->
        // Regression for #23: a change command (cw) plus the text typed in its
        // insert session must be ONE undo unit, matching vim. Previously the
        // operator-delete and the insert were separate undo steps because the
        // insert-entry cursor selection stamped a history boundary on the
        // delete event.
        h.doKeys("c", "w")
        h.doKeys("t", "e", "s", "t")
        h.doKeys("<Esc>")
        assertEquals("test word2", h.cm.getValue())
        h.doKeys("u")
        assertEquals("word1 word2", h.cm.getValue())
    }

    @Test
    fun dot_insert_cw_issue21() = testVim(value = "one two three") { h ->
        // Regression for #21: `.` after `cw<text><Esc>` must re-insert the
        // typed text, not merely delete the word. Previously the inserted text
        // was dropped because insert-mode input was never recorded into
        // lastInsertModeChanges (the change-event handler was never wired up).
        h.doKeys("c", "w")
        h.doKeys("X", "Y")
        h.doKeys("<Esc>")
        h.doKeys("w")
        h.doKeys(".")
        assertEquals("XY XY three", h.cm.getValue())
    }

    @Test
    fun dot_delete() = testVim(value = "zabcde") { h ->
        h.cm.setCursor(0, 5)
        h.doKeys("i")
        h.doKeys("Backspace")
        h.doKeys("<Esc>")
        h.doKeys(".")
        assertEquals("zace", h.cm.getValue())
        h.assertCursorAt(0, 1)
    }

    @Test
    fun dot_delete_repeat() = testVim(value = "zzabcde") { h ->
        h.cm.setCursor(0, 6)
        h.doKeys("i")
        h.doKeys("Backspace")
        h.doKeys("<Esc>")
        h.doKeys("2", ".")
        assertEquals("zzce", h.cm.getValue())
        h.assertCursorAt(0, 1)
    }

    @Test
    fun dot_visual_indent() = testVim(value = "1\n2\n3\n4") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("V", "j", ">")
        h.cm.setCursor(2, 0)
        h.doKeys(".")
        assertEquals("  1\n  2\n  3\n  4", h.cm.getValue())
        h.assertCursorAt(2, 2)
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
    }

    // --- Step 14: Misc Remaining Tests ---

    @Test
    fun increment_binary() = testVim(value = "0b000") { h ->
        h.cm.setCursor(0, 4)
        h.doKeys("<C-a>")
        assertEquals("0b001", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0b010", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0b001", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0b000", h.cm.getValue())
        h.cm.setCursor(0, 0)
        h.doKeys("<C-a>")
        assertEquals("0b001", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0b010", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0b001", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0b000", h.cm.getValue())
    }

    @Test
    fun increment_octal() = testVim(value = "000") { h ->
        h.cm.setCursor(0, 2)
        h.doKeys("<C-a>")
        assertEquals("001", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("002", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("003", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("004", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("005", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("006", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("007", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("010", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("007", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("006", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("005", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("004", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("003", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("002", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("001", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("000", h.cm.getValue())
        h.cm.setCursor(0, 0)
        h.doKeys("<C-a>")
        assertEquals("001", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("002", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("001", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("000", h.cm.getValue())
    }

    @Test
    fun increment_decimal() = testVim(value = "100") { h ->
        h.cm.setCursor(0, 2)
        h.doKeys("<C-a>")
        assertEquals("101", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("102", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("103", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("104", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("105", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("106", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("107", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("108", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("109", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("110", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("109", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("108", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("107", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("106", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("105", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("104", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("103", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("102", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("101", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("100", h.cm.getValue())
        h.cm.setCursor(0, 0)
        h.doKeys("<C-a>")
        assertEquals("101", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("102", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("101", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("100", h.cm.getValue())
    }

    @Test
    fun increment_decimal_single_zero() = testVim(value = "0") { h ->
        h.doKeys("<C-a>")
        assertEquals("1", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("2", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("3", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("4", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("5", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("6", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("7", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("8", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("9", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("10", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("9", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("8", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("7", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("6", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("5", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("4", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("3", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("2", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("1", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0", h.cm.getValue())
        h.cm.setCursor(0, 0)
        h.doKeys("<C-a>")
        assertEquals("1", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("2", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("1", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0", h.cm.getValue())
    }

    @Test
    fun increment_hexadecimal() = testVim(value = "0x0") { h ->
        h.cm.setCursor(0, 2)
        h.doKeys("<C-a>")
        assertEquals("0x1", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0x2", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0x3", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0x4", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0x5", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0x6", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0x7", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0x8", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0x9", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0xa", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0xb", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0xc", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0xd", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0xe", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0xf", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0x10", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x0f", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x0e", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x0d", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x0c", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x0b", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x0a", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x09", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x08", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x07", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x06", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x05", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x04", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x03", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x02", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x01", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x00", h.cm.getValue())
        h.cm.setCursor(0, 0)
        h.doKeys("<C-a>")
        assertEquals("0x01", h.cm.getValue())
        h.doKeys("<C-a>")
        assertEquals("0x02", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x01", h.cm.getValue())
        h.doKeys("<C-x>")
        assertEquals("0x00", h.cm.getValue())
    }

    @Test
    fun ctrl_x_ctrl_a_search_forward() = testVim(value = "__jmp1 jmp2 jmp") { h ->
        for (key in listOf("<C-x>", "<C-a>")) {
            h.cm.setCursor(0, 0)
            h.doKeys(key)
            h.assertCursorAt(0, 5)
            h.doKeys("l")
            h.doKeys(key)
            h.assertCursorAt(0, 10)
            h.cm.setCursor(0, 11)
            h.doKeys(key)
            h.assertCursorAt(0, 11)
        }
    }

    @Test
    fun r_visual_block() = testVim(value = "1234\n5678\nabcdefg") { h ->
        h.cm.setCursor(2, 3)
        h.doKeys("<C-v>", "k", "k", "h", "h", "r", "l")
        assertEquals("1lll\n5lll\nalllefg", h.cm.getValue())
        h.doKeys("<C-v>", "l", "j", "r", " ")
        assertEquals("1  l\n5  l\nalllefg", h.cm.getValue())
    }

    @Test
    fun p_register() = testVim(value = "___") { h ->
        h.cm.setCursor(0, 1)
        h.getRegisterController().getRegister("a").setText("abc\ndef", false)
        h.doKeys("\"", "a", "p")
        assertEquals("__abc\ndef_", h.cm.getValue())
        h.assertCursorAt(0, 2)
    }

    @Test
    fun p_wrong_register() = testVim(value = "___") { h ->
        h.cm.setCursor(0, 1)
        h.getRegisterController().getRegister("a").setText("abc\ndef", false)
        h.doKeys("p")
        assertEquals("___", h.cm.getValue())
        h.assertCursorAt(0, 1)
    }

    @Test
    fun p_line() = testVim(value = "___") { h ->
        h.cm.setCursor(0, 1)
        h.getRegisterController().pushText("\"", "yank", "  a\nd\n", true)
        h.doKeys("2", "p")
        assertEquals("___\n  a\nd\n  a\nd", h.cm.getValue())
        h.assertCursorAt(1, 2)
    }

    @Test
    fun p_lastline() = testVim(value = "___") { h ->
        h.cm.setCursor(0, 1)
        h.getRegisterController().pushText("\"", "yank", "  a\nd", true)
        h.doKeys("2", "p")
        assertEquals("___\n  a\nd\n  a\nd", h.cm.getValue())
        h.assertCursorAt(1, 2)
    }

    @Test
    fun bracket_p_first_indent_is_smaller() = testVim(value = "  ___") { h ->
        h.getRegisterController().pushText("\"", "yank", "  abc\n    def\n", true)
        h.doKeys("]", "p")
        assertEquals("  ___\n  abc\n    def", h.cm.getValue())
    }

    @Test
    fun bracket_p_first_indent_is_larger() = testVim(value = "  ___") { h ->
        h.getRegisterController().pushText("\"", "yank", "    abc\n  def\n", true)
        h.doKeys("]", "p")
        assertEquals("  ___\n  abc\ndef", h.cm.getValue())
    }

    @Test
    fun open_bracket_p() = testVim(value = "  ___") { h ->
        h.getRegisterController().pushText("\"", "yank", "  abc\n    def\n", true)
        h.doKeys("[", "p")
        assertEquals("  abc\n    def\n  ___", h.cm.getValue())
    }

    @Test
    fun capitalP_line() = testVim(value = "___") { h ->
        h.cm.setCursor(0, 1)
        h.getRegisterController().pushText("\"", "yank", "  a\nd\n", true)
        h.doKeys("2", "P")
        assertEquals("  a\nd\n  a\nd\n___", h.cm.getValue())
        h.assertCursorAt(0, 2)
    }

    @Test
    fun ci_quote_for_two_strings() = testVim(
        value = "   \"string1\":  \"string2\";"
    ) { h ->
        h.cm.setCursor(0, 11)
        h.doKeys("c", "i", "\"")
        assertEquals("   \"\":  \"string2\";", h.cm.getValue())
    }

    @Test
    fun ctrl_r_insert_mode() = testVim(value = "123 456 ") { h ->
        h.assertCursorAt(0, 0)
        h.doKeys("d", "w", "A")
        h.doKeys("<C-r>", "-")
        assertEquals("456 123 ", h.cm.getValue())
    }
}
