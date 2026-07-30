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

/**
 * Port of mapping-related tests from upstream vim_test.js.
 *
 * Skipped tests (with reasons):
 * - langmap tests (7): langmap not yet ported
 * - map_prompt: requires DOM dialog spy
 * - ex_map_key2key_to_colon: requires DOM dialog spy
 * - ex_map_key2key_visual_api: requires multi-cursor support in test harness
 * - ex_unmap_key2key_does_not_remove_default: requires notification text inspection
 */
class VimMappingTest {

    // -----------------------------------------------------------------------
    // mapclear (upstream: 'mapclear')
    // -----------------------------------------------------------------------

    @Test
    fun mapclear() = testVim(value = "abc abc") { h ->
        Vim.map("w", "l")
        h.cm.setCursor(0, 0)
        h.assertCursorAt(0, 0)
        // 'w' mapped to 'l' (move right one char)
        h.doKeys("w")
        h.assertCursorAt(0, 1)
        // mapclear('visual') only clears visual-context mappings
        Vim.mapclear("visual")
        // In visual mode 'w' should use default (word motion) since visual cleared
        h.doKeys("v", "w", "v")
        h.assertCursorAt(0, 4)
        // In normal mode 'w' -> 'l' should still be active
        h.doKeys("w")
        h.assertCursorAt(0, 5)
    }

    // -----------------------------------------------------------------------
    // mapclear_context (upstream: 'mapclear_context')
    // -----------------------------------------------------------------------

    @Test
    fun mapclear_context() = testVim(value = "abc abc") { h ->
        Vim.map("w", "l", "normal")
        h.cm.setCursor(0, 0)
        h.assertCursorAt(0, 0)
        h.doKeys("w")
        h.assertCursorAt(0, 1)
        Vim.mapclear("normal")
        // After clearing normal-mode mappings, 'w' should do default word motion
        h.doKeys("w")
        h.assertCursorAt(0, 4)
    }

    // -----------------------------------------------------------------------
    // ex_map_key2key (upstream: 'ex_map_key2key')
    // -----------------------------------------------------------------------

    @Test
    fun ex_map_key2key() = testVim(value = "abc") { h ->
        h.doEx("map a x")
        h.doKeys("a")
        h.assertCursorAt(0, 0)
        assertEquals("bc", h.cm.getValue())
    }

    // -----------------------------------------------------------------------
    // ex_unmap_key2key (upstream: 'ex_unmap_key2key')
    // -----------------------------------------------------------------------

    @Test
    fun ex_unmap_key2key() = testVim(value = "abc") { h ->
        h.doEx("map a x")
        h.doEx("unmap a")
        h.doKeys("a")
        // 'a' is the default append command, which enters insert mode
        assertTrue(h.vim.insertMode)
    }

    // -----------------------------------------------------------------------
    // ex_map_ex2key (upstream: 'ex_map_ex2key:')
    // -----------------------------------------------------------------------

    @Test
    fun ex_map_ex2key() = testVim(value = "abc") { h ->
        h.doEx("map :del x")
        h.doEx("del")
        h.assertCursorAt(0, 0)
        assertEquals("bc", h.cm.getValue())
    }

    // -----------------------------------------------------------------------
    // ex_map_ex2ex (upstream: 'ex_map_ex2ex')
    // Upstream maps :del to :w and verifies save; we use :s/a/z/ instead
    // since the test harness lacks CodeMirror.commands.save.
    // -----------------------------------------------------------------------

    @Test
    fun ex_map_ex2ex() = testVim(value = "abc") { h ->
        h.doEx("map :del :s/a/z/")
        h.doEx("del")
        assertEquals("zbc", h.cm.getValue())
    }

    // -----------------------------------------------------------------------
    // ex_map_key2ex (upstream: 'ex_map_key2ex')
    // Upstream maps 'a' to :w<CR>; we use :s/a/z/<CR> instead.
    // -----------------------------------------------------------------------

    @Test
    fun ex_map_key2ex() = testVim(value = "abc") { h ->
        h.doEx("map a :s/a/z/<CR>")
        h.doKeys("a")
        assertEquals("zbc", h.cm.getValue())
    }

    // -----------------------------------------------------------------------
    // ex_omap (upstream: 'ex_omap')
    // -----------------------------------------------------------------------

    @Test
    fun ex_omap() = testVim(value = "hello unfair world") { h ->
        // Default: 'dw' deletes a word
        h.doKeys("0", "w", "d", "w")
        assertEquals("hello world", h.cm.getValue())
        h.doKeys("u")
        // Map 'w' in operator-pending mode to '$' (end of line)
        h.doEx("omap w \$")
        // Normal 'w' motion should still work
        h.doKeys("0", "w")
        h.assertCursorAt(0, 6)
        // 'dw' should now delete to end of line (because 'w' in omap -> '$')
        h.doKeys("d", "w")
        assertEquals("hello ", h.cm.getValue())
    }

    // -----------------------------------------------------------------------
    // ex_nmap (upstream: 'ex_nmap')
    // -----------------------------------------------------------------------

    @Test
    fun ex_nmap() = testVim(value = "hello\nunfair\nworld") { h ->
        h.cm.setCursor(0, 3)
        h.doEx("nmap k gj")
        // 'k' in normal mode should now act like 'gj' (down)
        h.doKeys("k")
        h.assertCursorAt(1, 3)
        // 'k' in operator-pending mode (dk) should still use default 'k' (up)
        h.doKeys("d", "k")
        assertEquals("world", h.cm.getValue())
        h.doKeys("u")
        // Now test with global 'map' instead of 'nmap'
        h.cm.setCursor(1, 3)
        h.doEx("map k gj")
        // 'dk' should now use the mapped 'gj' in operator-pending context too
        h.doKeys("d", "k")
        assertEquals("hello\nunfld", h.cm.getValue())
        h.assertCursorAt(1, 3)
    }

    // -----------------------------------------------------------------------
    // ex_imap (upstream: 'ex_imap')
    // Split into focused sub-tests; multi-cursor parts skipped.
    // -----------------------------------------------------------------------

    @Test
    fun ex_imap_jk_escape() = testVim(value = "1234\n5678\nabcdefg") { h ->
        // Map 'jk' to Escape in insert mode
        Vim.map("jk", "<Esc>", "insert")
        h.doKeys("i")
        assertTrue(h.vim.insertMode)
        h.doKeys("j", "k")
        assertFalse(h.vim.insertMode)
    }

    @Test
    fun ex_imap_jj_escape() = testVim(value = "1234\n5678\nabcdefg") { h ->
        // Map 'jj' to Escape in insert mode
        Vim.map("jj", "<Esc>", "insert")
        h.doKeys("i")
        assertTrue(h.vim.insertMode)
        h.doKeys("j", "j")
        assertFalse(h.vim.insertMode)
    }

    @Test
    fun ex_imap_ctrl_c() = testVim(value = "1\n2") { h ->
        // Delete first line, then use imap to map 'a' to <C-c> (exit insert)
        h.doKeys("g", "g", "d", "d")
        h.doEx("imap a <C-c>")
        // 'i' enters insert, 'x' types x, 'a' exits insert via <C-c>, 'p' pastes
        h.doKeys("i", "x", "a", "p")
        assertEquals("x2\n1", h.cm.getValue())
    }

    // -----------------------------------------------------------------------
    // ex_unmap_api (upstream: 'ex_unmap_api')
    // -----------------------------------------------------------------------

    @Test
    fun ex_unmap_api() = testVim { h ->
        Vim.map("<Alt-X>", "gg", "normal")
        // After mapping, handleKey should recognize the mapped key
        val mapped = Vim.handleKey(h.cm, "<Alt-X>", "normal")
        assertTrue(mapped, "Alt-X key should be mapped")
        Vim.unmap("<Alt-X>", "normal")
        val unmapped = Vim.handleKey(h.cm, "<Alt-X>", "normal")
        assertFalse(unmapped, "Alt-X key should be unmapped")
    }

    // -----------------------------------------------------------------------
    // ex_api_test (upstream: 'ex_api_test')
    // Tests defineEx registration and mapping to <Key>-keys.
    // -----------------------------------------------------------------------

    @Test
    fun ex_api_test() = testVim { h ->
        var res = false
        var value = "from"
        Vim.defineEx("extest", "ext") { _, params ->
            if (params.args != null) {
                value = params.args!![0]
            } else {
                res = true
            }
        }
        h.doEx(":ext to")
        assertEquals("to", value, "Defining ex-command failed")
        Vim.map("<C-CR><Space>", ":ext<CR>")
        h.doKeys("<C-CR>", "<Space>")
        assertTrue(res, "Mapping to key failed")
    }

    // -----------------------------------------------------------------------
    // ex_special_names (upstream: 'ex_special_names')
    // Tests ex-commands with non-alpha names.
    // -----------------------------------------------------------------------

    @Test
    fun ex_special_names() = testVim { h ->
        val cmds = listOf(
            "!", "!!", "#", "&", "<", "=", ">", "@", "@@", "~", "regtest1", "RT2"
        )
        for (name in cmds) {
            var ran = ""
            var argVal = ""
            Vim.defineEx(name, "") { _, params ->
                ran = params.commandName
                argVal = params.argString
            }
            h.doEx(":$name")
            assertEquals(name, ran, "Running ex-command '$name' failed")
            h.doEx(":$name x")
            assertEquals(" x", argVal, "Running ex-command '$name' with param failed")
            if (Regex("^\\W+$").matches(name)) {
                h.doEx(":${name}y")
                assertEquals(
                    "y",
                    argVal,
                    "Running ex-command '$name' with adjacent param failed"
                )
            } else {
                h.doEx(":$name-y")
                assertEquals(
                    "-y",
                    argVal,
                    "Running ex-command '$name' with param failed"
                )
            }
            if (name != "!") {
                h.doEx(":$name!")
                assertEquals(
                    name,
                    ran,
                    "Running ex-command '$name' with bang failed"
                )
                assertEquals(
                    "!",
                    argVal,
                    "Running ex-command '$name' with bang value failed"
                )
                h.doEx(":$name!z")
                assertEquals(
                    name,
                    ran,
                    "Running ex-command '$name' with bang & param failed"
                )
                assertEquals(
                    "!z",
                    argVal,
                    "Running ex-command '$name' with bang & param value failed"
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // ex_map_key2key_from_colon (upstream: 'ex_map_key2key_from_colon')
    // Note: upstream says "this test needs to be last because it messes up :"
    // -----------------------------------------------------------------------

    @Test
    fun ex_map_key2key_from_colon() = testVim(value = "abc") { h ->
        h.doEx("map : x")
        h.doKeys(":")
        h.assertCursorAt(0, 0)
        assertEquals("bc", h.cm.getValue())
    }

    // -----------------------------------------------------------------------
    // map <Esc> in normal mode (upstream: 'map <Esc> in normal mode')
    // -----------------------------------------------------------------------

    @Test
    fun map_esc_in_normal_mode() = testVim { h ->
        Vim.noremap("<Esc>", "i", "normal")
        h.doKeys("<Esc>")
        assertTrue(h.vim.insertMode, "Didn't switch to insert mode.")
        h.doKeys("<Esc>")
        assertFalse(h.vim.insertMode, "Didn't switch to normal mode.")
    }

    // -----------------------------------------------------------------------
    // noremap (upstream: 'noremap')
    // -----------------------------------------------------------------------

    @Test
    fun noremap() = testVim(value = "wOrd1") { h ->
        h.doEx("noremap ; l")
        h.doEx("map l \$")
        h.doEx("map q l")
        // 'l' is mapped to '$', so pressing 'l' goes to end of line
        h.doKeys("l")
        h.assertCursorAt(0, 4)
        h.cm.setCursor(0, 0)
        // 'q' is mapped to 'l', which is mapped to '$'
        h.doKeys("q")
        h.assertCursorAt(0, 4)
        h.cm.setCursor(0, 0)
        assertEquals("wOrd1", h.cm.getValue())
        // ';' is noremapped to 'l' (the actual 'l' motion, not the mapping)
        h.doKeys(";", "r", "1")
        assertEquals("w1rd1", h.cm.getValue())
        // In insert mode, ';' mapping does not apply
        h.doKeys("i", ";", "<Esc>")
        assertEquals("w;1rd1", h.cm.getValue())
        // mapclear removes all user mappings
        h.doEx("mapclear")
        h.cm.setCursor(0, 0)
        h.doKeys("l")
        h.assertCursorAt(0, 1)
        // After clearing, 'x' then 'p' should work normally
        h.doKeys("x", "p", "l")
        assertEquals("w1;rd1", h.cm.getValue())
        // noremap 'x' to '"_x' (black-hole delete)
        h.doEx("noremap x \"_x")
        h.doKeys("x", "p")
        // After black-hole delete, paste puts back previously yanked char
        assertEquals("w1;d;1", h.cm.getValue())
        h.doEx("mapclear")
    }

    // -----------------------------------------------------------------------
    // noremap_all_mappings (upstream: 'noremap_all_mappings')
    // noremap should capture all mappings of the rhs.
    // -----------------------------------------------------------------------

    @Test
    fun noremap_all_mappings() = testVim(value = "HeY") { h ->
        // Mapping to 'u' should undo in normal mode and lowercase in visual mode
        Vim.noremap("a", "u")
        h.doKeys("y", "y", "p")
        assertEquals("HeY\nHeY", h.cm.getValue())
        // Undo via mapped 'a'
        h.doKeys("a")
        assertEquals("HeY", h.cm.getValue())
        // Lowercase via 'u' in visual mode
        h.doKeys("V", "a")
        assertEquals("hey", h.cm.getValue())
    }

    // -----------------------------------------------------------------------
    // noremap_swap (upstream: 'noremap_swap')
    // -----------------------------------------------------------------------

    @Test
    fun noremap_swap() = testVim(value = "foo") { h ->
        Vim.noremap("i", "a", "normal")
        Vim.noremap("a", "i", "normal")
        h.cm.setCursor(0, 0)
        // 'a' should act like 'i' (insert before cursor)
        h.doKeys("a")
        assertEquals(
            LinePos(0, 0),
            LinePos(h.cm.getCursor().line, h.cm.getCursor().ch)
        )
        // 'i' should act like 'a' (append after cursor)
        h.doKeys("<Esc>", "i")
        assertEquals(
            LinePos(0, 1),
            LinePos(h.cm.getCursor().line, h.cm.getCursor().ch)
        )
    }

    // -----------------------------------------------------------------------
    // noremap_map_interaction (upstream: 'noremap_map_interaction')
    // noremap should clobber map.
    // -----------------------------------------------------------------------

    @Test
    fun noremap_map_interaction() = testVim(value = "wOrd1\nwOrd2") { h ->
        // noremap should clobber map
        Vim.map(";", "l")
        Vim.noremap(";", "l")
        Vim.map("l", "j")
        h.cm.setCursor(0, 0)
        // ';' is noremapped to 'l' motion (move right), not the mapped 'l'
        h.doKeys(";")
        assertEquals(
            LinePos(0, 1),
            LinePos(h.cm.getCursor().line, h.cm.getCursor().ch)
        )
        // 'l' is mapped to 'j' (move down)
        h.doKeys("l")
        assertEquals(
            LinePos(1, 1),
            LinePos(h.cm.getCursor().line, h.cm.getCursor().ch)
        )
        // map should be able to point to a noremap
        Vim.map("m", ";")
        h.doKeys("m")
        assertEquals(
            LinePos(1, 2),
            LinePos(h.cm.getCursor().line, h.cm.getCursor().ch)
        )
    }

    // -----------------------------------------------------------------------
    // noremap_map_interaction2 (upstream: 'noremap_map_interaction2')
    // map should point to the most recent noremap.
    // -----------------------------------------------------------------------

    @Test
    fun noremap_map_interaction2() = testVim(value = "wOrd1\nwOrd2") { h ->
        // map should point to the most recent noremap
        Vim.noremap(";", "l")
        Vim.map("m", ";")
        Vim.noremap(";", "h")
        h.cm.setCursor(0, 0)
        // 'l' goes right (default motion, not mapped)
        h.doKeys("l")
        assertEquals(
            LinePos(0, 1),
            LinePos(h.cm.getCursor().line, h.cm.getCursor().ch)
        )
        // 'm' -> ';' -> 'h' (most recent noremap), so goes left
        h.doKeys("m")
        assertEquals(
            LinePos(0, 0),
            LinePos(h.cm.getCursor().line, h.cm.getCursor().ch)
        )
    }
}
