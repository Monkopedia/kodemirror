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

class VimSearchTest {

    // Helper: type a search command like /query<Enter> or ?query<Enter>
    private fun VimHelpers.doSearch(direction: String, query: String) {
        doKeys(direction)
        for (ch in query) doKeys(ch.toString())
        doKeys("Enter")
    }

    @Test
    fun star_searches_word_under_cursor() = testVim(
        value = "foo bar foo baz foo",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("*")
        h.assertCursorAt(0, 8)
    }

    @Test
    fun star_wraps_around() = testVim(
        value = "foo bar foo",
        cursor = LinePos(0, 8)
    ) { h ->
        h.doKeys("*")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun hash_searches_backward() = testVim(
        value = "foo bar foo",
        cursor = LinePos(0, 8)
    ) { h ->
        h.doKeys("#")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun n_repeats_search_forward() = testVim(
        value = "foo bar foo baz foo",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("*")
        h.assertCursorAt(0, 8)
        h.doKeys("n")
        h.assertCursorAt(0, 16)
    }

    @Test
    fun capitalN_repeats_search_backward() = testVim(
        value = "foo bar foo baz foo",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("*")
        h.assertCursorAt(0, 8)
        h.doKeys("N")
        h.assertCursorAt(0, 0)
    }

    // --- Ported from upstream vim_test.js ---

    @Test
    fun d_slash() = testVim(
        value = "text match match \n next",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("2", "d")
        h.doSearch("/", "match")
        h.assertCursorAt(0, 0)
        assertEquals("match \n next", h.cm.getValue())
    }

    @Test
    fun forward_search_and_n_N() = testVim(
        value = "match nope match \n nope Match",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("/", "match")
        h.assertCursorAt(0, 11)
        h.doKeys("n")
        h.assertCursorAt(1, 6)
        h.doKeys("N")
        h.assertCursorAt(0, 11)

        h.cm.setCursor(0, 0)
        h.doKeys("2")
        h.doSearch("/", "match")
        h.assertCursorAt(1, 6)
    }

    @Test
    fun forward_search_and_gn_selects_appropriate_word() = testVim(
        value = "match nope match \n nope Match",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("/", "match")
        h.assertCursorAt(0, 11)

        // gn when cursor is in beginning of match
        h.doKeys("g", "n", "<Esc>")
        h.assertCursorAt(0, 15)

        // gn when cursor is at end of match
        h.doKeys("g", "n", "<Esc>")
        h.doKeys("<Esc>")
        h.assertCursorAt(0, 15)

        // consecutive gns should extend the selection
        h.doKeys("g", "n")
        h.assertCursorAt(0, 16)
        h.doKeys("g", "n")
        h.assertCursorAt(1, 11)

        // we should have selected the second and third "match"
        h.doKeys("d")
        assertEquals("match nope ", h.cm.getValue())
    }

    @Test
    fun forward_search_and_gN_selects_appropriate_word() = testVim(
        value = "match nope match \n nope Match",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("/", "match")
        h.assertCursorAt(0, 11)

        // gN when cursor is at beginning of match
        h.doKeys("g", "N", "<Esc>")
        h.assertCursorAt(0, 11)

        // gN when cursor is at end of match
        h.doKeys("e", "g", "N", "<Esc>")
        h.assertCursorAt(0, 11)

        // consecutive gNs should extend the selection
        h.doKeys("g", "N")
        h.assertCursorAt(0, 11)
        h.doKeys("g", "N")
        h.assertCursorAt(0, 0)

        // we should have selected the first and second "match"
        h.doKeys("d")
        assertEquals(" \n nope Match", h.cm.getValue())
    }

    @Test
    fun forward_search_and_gn_with_operator() = testVim(
        value = "match nope match \n nope Match",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("/", "match")
        h.assertCursorAt(0, 11)

        h.doKeys("c", "g", "n")
        for (ch in "changed") h.doKeys(ch.toString())
        h.doKeys("<Esc>")

        // change the current match
        assertEquals("match nope changed \n nope Match", h.cm.getValue())

        // change the next match
        h.doKeys(".")
        assertEquals("match nope changed \n nope changed", h.cm.getValue())

        // change the final match
        h.doKeys(".")
        assertEquals("changed nope changed \n nope changed", h.cm.getValue())
    }

    @Test
    fun forward_search_and_gN_with_operator() = testVim(
        value = "match nope match \n nope Match",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("/", "match")
        h.assertCursorAt(0, 11)

        h.doKeys("c", "g", "N")
        for (ch in "changed") h.doKeys(ch.toString())
        h.doKeys("<Esc>")

        // change the current match
        assertEquals("match nope changed \n nope Match", h.cm.getValue())

        // change the next match
        h.doKeys(".")
        assertEquals("changed nope changed \n nope Match", h.cm.getValue())

        // change the final match
        h.doKeys(".")
        assertEquals("changed nope changed \n nope changed", h.cm.getValue())
    }

    @Test
    fun forward_search_case_sensitive() = testVim(
        value = "match nope match \n nope Match",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("/", "Match")
        h.assertCursorAt(1, 6)
    }

    @Test
    fun forward_search_pcre() = testVim(
        value = "word\n another wordword\n wordwordword\n",
        cursor = LinePos(0, 0)
    ) { h ->
        Vim.setOption("pcre", true)
        h.doSearch("/", "(word){2}")
        h.assertCursorAt(1, 9)
        h.doKeys("n")
        h.assertCursorAt(2, 1)
    }

    @Test
    fun forward_search_nopcre() = testVim(
        value = "word\n another wordword\n wordwordword\n",
        cursor = LinePos(0, 0)
    ) { h ->
        Vim.setOption("pcre", false)
        h.doSearch("/", "\\(word\\)\\{2}")
        h.assertCursorAt(1, 9)
        h.doKeys("n")
        h.assertCursorAt(2, 1)
    }

    @Test
    fun forward_search_nongreedy() = testVim(
        value = "aaa aa \n a aa",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("/", "aa")
        h.assertCursorAt(0, 4)
        h.doKeys("n")
        h.assertCursorAt(1, 3)
        h.doKeys("n")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun backward_search_nongreedy() = testVim(
        value = "aaa aa \n a aa",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("?", "aa")
        h.assertCursorAt(1, 3)
        h.doKeys("n")
        h.assertCursorAt(0, 4)
        h.doKeys("n")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun forward_search_greedy() = testVim(
        value = "aaa aa \n a aa",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("/", "a+")
        h.assertCursorAt(0, 4)
        h.doKeys("n")
        h.assertCursorAt(1, 1)
        h.doKeys("n")
        h.assertCursorAt(1, 3)
        h.doKeys("n")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun backward_search_greedy() = testVim(
        value = "aaa aa \n a aa",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("?", "a+")
        h.assertCursorAt(1, 3)
        h.doKeys("n")
        h.assertCursorAt(1, 1)
        h.doKeys("n")
        h.assertCursorAt(0, 4)
        h.doKeys("n")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun forward_search_greedy_0_or_more() = testVim(
        value = "aaa  aa\n aa",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("/", "a*")
        h.assertCursorAt(0, 3)
        h.doKeys("n")
        h.assertCursorAt(0, 4)
        h.doKeys("n")
        h.assertCursorAt(0, 5)
        h.doKeys("n")
        h.assertCursorAt(1, 0)
        h.doKeys("n")
        h.assertCursorAt(1, 1)
        h.doKeys("n")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun backward_search_greedy_0_or_more() = testVim(
        value = "aaa  aa\n aa",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("?", "a*")
        h.assertCursorAt(1, 1)
        h.doKeys("n")
        h.assertCursorAt(1, 0)
        h.doKeys("n")
        h.assertCursorAt(0, 5)
        h.doKeys("n")
        h.assertCursorAt(0, 4)
        h.doKeys("n")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun backward_search_and_n_N() = testVim(
        value = "match nope match \n nope Match",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("?", "match")
        h.assertCursorAt(1, 6)
        h.doKeys("n")
        h.assertCursorAt(0, 11)
        h.doKeys("N")
        h.assertCursorAt(1, 6)

        h.cm.setCursor(0, 0)
        h.doKeys("2")
        h.doSearch("?", "match")
        h.assertCursorAt(0, 11)
    }

    @Test
    fun backward_search_and_gn_selects_appropriate_word() = testVim(
        value = "match nope match \n nope Match",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("?", "match")
        h.doKeys("n")
        h.assertCursorAt(0, 11)

        // gn when cursor is in beginning of match
        h.doKeys("g", "n", "<Esc>")
        h.assertCursorAt(0, 11)

        // gn when cursor is at end of match
        h.doKeys("e", "g", "n", "<Esc>")
        h.assertCursorAt(0, 11)

        // consecutive gns should extend the selection
        h.doKeys("g", "n")
        h.assertCursorAt(0, 11)
        h.doKeys("g", "n")
        h.assertCursorAt(0, 0)

        // we should have selected the first and second "match"
        h.doKeys("d")
        assertEquals(" \n nope Match", h.cm.getValue())
    }

    @Test
    fun backward_search_and_gN_selects_appropriate_word() = testVim(
        value = "match nope match \n nope Match",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("?", "match")
        h.doKeys("n")
        h.assertCursorAt(0, 11)

        // gN when cursor is at beginning of match
        h.doKeys("g", "N", "<Esc>")
        h.assertCursorAt(0, 15)

        // gN when cursor is at end of match
        h.doKeys("g", "N", "<Esc>")
        h.assertCursorAt(0, 15)

        // consecutive gNs should extend the selection
        h.doKeys("g", "N")
        h.assertCursorAt(0, 16)
        h.doKeys("g", "N")
        h.assertCursorAt(1, 11)

        // we should have selected the second and third "match"
        h.doKeys("d")
        assertEquals("match nope ", h.cm.getValue())
    }

    @Test
    fun backward_search_and_gn_with_operator() = testVim(
        value = "match nope match \n nope Match",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("?", "match")
        h.doKeys("n")
        h.assertCursorAt(0, 11)

        h.doKeys("c", "g", "n")
        for (ch in "changed") h.doKeys(ch.toString())
        h.doKeys("<Esc>")

        // change the current match
        assertEquals("match nope changed \n nope Match", h.cm.getValue())

        // change the next match
        h.doKeys(".")
        assertEquals("changed nope changed \n nope Match", h.cm.getValue())

        // change the final match
        h.doKeys(".")
        assertEquals("changed nope changed \n nope changed", h.cm.getValue())
    }

    @Test
    fun backward_search_and_gN_with_operator() = testVim(
        value = "match nope match \n nope Match",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doSearch("?", "match")
        h.doKeys("n")
        h.assertCursorAt(0, 11)

        h.doKeys("c", "g", "N")
        for (ch in "changed") h.doKeys(ch.toString())
        h.doKeys("<Esc>")

        // change the current match
        assertEquals("match nope changed \n nope Match", h.cm.getValue())

        // change the next match
        h.doKeys(".")
        assertEquals("match nope changed \n nope changed", h.cm.getValue())

        // change the final match
        h.doKeys(".")
        assertEquals("changed nope changed \n nope changed", h.cm.getValue())
    }

    @Test
    fun star() = testVim(
        value = "nomatch match nomatch match \nnomatch Match",
        cursor = LinePos(0, 0)
    ) { h ->
        h.cm.setCursor(0, 9)
        h.doKeys("*")
        h.assertCursorAt(0, 22)

        h.cm.setCursor(0, 9)
        h.doKeys("2", "*")
        h.assertCursorAt(1, 8)
    }

    @Test
    fun star_no_word() = testVim(
        value = " \n match \n",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("*")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun star_symbol() = testVim(
        value = " /}\n/} match \n",
        cursor = LinePos(0, 0)
    ) { h ->
        h.doKeys("*")
        h.assertCursorAt(1, 0)
    }

    @Test
    fun star_seek() = testVim(
        value = "    :=  match nomatch match \nnomatch Match",
        cursor = LinePos(0, 0)
    ) { h ->
        // Should skip over space and symbols.
        h.cm.setCursor(0, 3)
        h.doKeys("*")
        h.assertCursorAt(0, 22)
    }

    @Test
    fun hash() = testVim(
        value = "nomatch match nomatch match \nnomatch Match",
        cursor = LinePos(0, 0)
    ) { h ->
        h.cm.setCursor(0, 9)
        h.doKeys("#")
        h.assertCursorAt(1, 8)

        h.cm.setCursor(0, 9)
        h.doKeys("2", "#")
        h.assertCursorAt(0, 22)
    }

    @Test
    fun hash_seek() = testVim(
        value = "    :=  match nomatch match \nnomatch Match",
        cursor = LinePos(0, 0)
    ) { h ->
        // Should skip over space and symbols.
        h.cm.setCursor(0, 3)
        h.doKeys("#")
        h.assertCursorAt(1, 8)
    }

    @Test
    fun g_star() = testVim(
        value = "matches match alsoMatch\nmatchme matching",
        cursor = LinePos(0, 0)
    ) { h ->
        h.cm.setCursor(0, 8)
        h.doKeys("g", "*")
        h.assertCursorAt(0, 18)
        h.cm.setCursor(0, 8)
        h.doKeys("3", "g", "*")
        h.assertCursorAt(1, 8)
    }

    @Test
    fun g_hash() = testVim(
        value = "matches match alsoMatch\nmatchme matching",
        cursor = LinePos(0, 0)
    ) { h ->
        h.cm.setCursor(0, 8)
        h.doKeys("g", "#")
        h.assertCursorAt(0, 0)
        h.cm.setCursor(0, 8)
        h.doKeys("3", "g", "#")
        h.assertCursorAt(1, 0)
    }

    @Test
    fun moveTillCharacter() = testVim(
        value = "The quick brown fox \n",
        cursor = LinePos(0, 0)
    ) { h ->
        // Search for the 'q'.
        h.doSearch("/", "q")
        assertEquals(4, h.cm.getCursor().ch)
        // Jump to just before the first o in the list.
        h.doKeys("t")
        h.doKeys("o")
        assertEquals("The quick brown fox \n", h.cm.getValue())
        // Delete that one character.
        h.doKeys("d")
        h.doKeys("t")
        h.doKeys("o")
        assertEquals("The quick bown fox \n", h.cm.getValue())
        // Delete everything until the next 'o'.
        h.doKeys(".")
        assertEquals("The quick box \n", h.cm.getValue())
        // An unmatched character should have no effect.
        h.doKeys("d")
        h.doKeys("t")
        h.doKeys("q")
        assertEquals("The quick box \n", h.cm.getValue())
        // Matches should only be possible on single lines.
        h.doKeys("d")
        h.doKeys("t")
        h.doKeys("z")
        assertEquals("The quick box \n", h.cm.getValue())
        // After all that, the search for 'q' should still be active, so the 'N' command
        // can run it again in reverse. Use that to delete everything back to the 'q'.
        h.doKeys("d")
        h.doKeys("N")
        assertEquals("The ox \n", h.cm.getValue())
        assertEquals(4, h.cm.getCursor().ch)
    }

    @Test
    fun searchForPipe() = testVim(
        value = "this|that",
        cursor = LinePos(0, 0)
    ) { h ->
        Vim.setOption("pcre", false)
        // Search for the '|'.
        h.doSearch("/", "|")
        assertEquals(4, h.cm.getCursor().ch)
    }
}
