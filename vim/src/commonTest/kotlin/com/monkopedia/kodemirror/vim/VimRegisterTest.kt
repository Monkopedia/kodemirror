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

class VimRegisterTest {

    @Test
    fun named_register_yank_and_paste() = testVim(value = "foo\nbar") { h ->
        h.doKeys("\"", "a", "y", "y")
        h.doKeys("j")
        h.doKeys("\"", "a", "p")
        assertEquals("foo\nbar\nfoo", h.cm.getValue())
    }

    @Test
    fun unnamed_register_after_delete() = testVim(value = "foo\nbar\nbaz") { h ->
        h.doKeys("d", "d")
        h.doKeys("p")
        assertEquals("bar\nfoo\nbaz", h.cm.getValue())
    }

    @Test
    fun yank_to_register_a_then_b() = testVim(value = "aaa\nbbb\nccc") { h ->
        h.doKeys("\"", "a", "y", "y")
        h.doKeys("j")
        h.doKeys("\"", "b", "y", "y")
        h.doKeys("G")
        h.doKeys("\"", "a", "p")
        assertEquals("aaa\nbbb\nccc\naaa", h.cm.getValue())
    }

    @Test
    fun yank_register() = testVim(value = "foo\nbar") { h ->
        h.doKeys("\"", "a", "y", "y")
        h.doKeys("j", "\"", "b", "y", "y")
        val regA = h.getRegisterController().getRegister("a")
        assertEquals("foo\n", regA.toString())
        assertTrue(regA.linewise)
        val regB = h.getRegisterController().getRegister("b")
        assertEquals("bar\n", regB.toString())
        assertTrue(regB.linewise)
    }

    @Test
    fun yank_append_line_to_line_register() = testVim(value = "foo\nbar") { h ->
        h.doKeys("\"", "a", "y", "y")
        h.doKeys("j", "\"", "A", "y", "y")
        val regA = h.getRegisterController().getRegister("a")
        assertEquals("foo\nbar\n", regA.toString())
        assertTrue(regA.linewise)
        // Unnamed register should match
        val regUnnamed = h.getRegisterController().getRegister(null)
        assertEquals("foo\nbar\n", regUnnamed.toString())
    }

    @Test
    fun yank_append_word_to_word_register() = testVim(value = "foo\nbar") { h ->
        h.doKeys("\"", "a", "y", "w")
        h.doKeys("j", "\"", "A", "y", "w")
        val regA = h.getRegisterController().getRegister("a")
        assertEquals("foobar", regA.toString())
        assertFalse(regA.linewise)
        val regUnnamed = h.getRegisterController().getRegister(null)
        assertEquals("foobar", regUnnamed.toString())
    }

    @Test
    fun yank_append_line_to_word_register() = testVim(value = "foo\nbar") { h ->
        h.doKeys("\"", "a", "y", "w")
        h.doKeys("j", "\"", "A", "y", "y")
        val regA = h.getRegisterController().getRegister("a")
        assertEquals("foo\nbar\n", regA.toString())
        assertTrue(regA.linewise)
        val regUnnamed = h.getRegisterController().getRegister(null)
        assertEquals("foo\nbar\n", regUnnamed.toString())
    }

    @Test
    fun yank_append_word_to_line_register() = testVim(value = "foo\nbar") { h ->
        h.doKeys("\"", "a", "y", "y")
        h.doKeys("j", "\"", "A", "y", "w")
        val regA = h.getRegisterController().getRegister("a")
        assertEquals("foo\nbar", regA.toString())
        assertTrue(regA.linewise)
        val regUnnamed = h.getRegisterController().getRegister(null)
        assertEquals("foo\nbar", regUnnamed.toString())
    }

    @Test
    fun black_hole_register() = testVim(value = "foo\nbar") { h ->
        h.doKeys("g", "g", "y", "G")
        // Save register state before black hole delete
        val regBefore = h.getRegisterController().getRegister(null).toString()
        h.doKeys("\"", "_", "d", "G")
        // Registers should not be modified by black hole delete
        val regAfter = h.getRegisterController().getRegister(null).toString()
        assertEquals(regBefore, regAfter, "One or more registers were modified")
        // Pasting from black hole should produce nothing
        h.doKeys("\"", "_", "p")
        assertEquals("", h.cm.getValue())
    }

    @Test
    fun p_register() = testVim(value = "___", cursor = LinePos(0, 1)) { h ->
        h.getRegisterController().getRegister("a").setText("abc\ndef", false)
        h.doKeys("\"", "a", "p")
        assertEquals("__abc\ndef_", h.cm.getValue())
        h.assertCursorAt(0, 2)
    }

    @Test
    fun p_wrong_register() = testVim(value = "___", cursor = LinePos(0, 1)) { h ->
        // Ensure unnamed register is empty so p without register prefix does nothing
        h.getRegisterController().getRegister(null).setText("", false)
        h.getRegisterController().getRegister("a").setText("abc\ndef", false)
        h.doKeys("p")
        assertEquals("___", h.cm.getValue())
        h.assertCursorAt(0, 1)
    }
}
