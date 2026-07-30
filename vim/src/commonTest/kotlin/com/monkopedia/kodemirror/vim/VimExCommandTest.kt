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

class VimExCommandTest {

    @Test
    fun substitute_basic() = testVim(value = "foo bar foo") { h ->
        h.doEx("s/foo/baz/")
        assertEquals("baz bar foo", h.cm.getValue())
    }

    @Test
    fun substitute_global() = testVim(value = "foo bar foo") { h ->
        h.doEx("s/foo/baz/g")
        assertEquals("baz bar baz", h.cm.getValue())
    }

    @Test
    fun substitute_with_range() = testVim(value = "foo\nfoo\nfoo") { h ->
        h.doEx("1,2s/foo/bar/")
        assertEquals("bar\nbar\nfoo", h.cm.getValue())
    }

    @Test
    fun sort_lines() = testVim(value = "cherry\napple\nbanana") { h ->
        h.doEx("%sort")
        assertEquals("apple\nbanana\ncherry", h.cm.getValue())
    }

    @Test
    fun delete_lines() = testVim(value = "foo\nbar\nbaz") { h ->
        h.doEx("2d")
        assertEquals("foo\nbaz", h.cm.getValue())
    }

    @Test
    fun join_lines() = testVim(value = "foo\nbar\nbaz") { h ->
        h.doEx("1,2join")
        assertEquals("foo bar\nbaz", h.cm.getValue())
    }

    // --- Step 12: Ex Command Tests (non-substitute) ---

    @Test
    fun ex_go_to_line() = testVim(value = "a\nb\nc\nd\ne\n") { h ->
        h.cm.setCursor(0, 0)
        h.doEx("4")
        h.assertCursorAt(3, 0)
        h.doEx("4-1")
        h.assertCursorAt(2, 0)
    }

    @Test
    fun ex_go_to_mark() = testVim(value = "a\nb\nc\nd\ne\n") { h ->
        h.cm.setCursor(3, 0)
        h.doKeys("m", "a")
        h.cm.setCursor(0, 0)
        h.doEx("'a")
        h.assertCursorAt(3, 0)
    }

    @Test
    fun ex_go_to_line_offset() = testVim(value = "a\nb\nc\nd\ne\n") { h ->
        h.cm.setCursor(0, 0)
        h.doEx("+3")
        h.assertCursorAt(3, 0)
        h.doEx("-1")
        h.assertCursorAt(2, 0)
        h.doEx(".2")
        h.assertCursorAt(4, 0)
        h.doEx(".-3")
        h.assertCursorAt(1, 0)
    }

    @Test
    fun ex_go_to_mark_offset() = testVim(value = "a\nb\nc\nd\ne\n") { h ->
        h.cm.setCursor(2, 0)
        h.doKeys("m", "a")
        h.cm.setCursor(0, 0)
        h.doEx("'a1")
        h.assertCursorAt(3, 0)
        h.doEx("'a-1")
        h.assertCursorAt(1, 0)
        h.doEx("'a+2")
        h.assertCursorAt(4, 0)
    }

    @Test
    fun ex_delete() = testVim(value = "l 1\nl 2\nl 3\nl 4\n") { h ->
        h.doKeys("j")
        h.doEx("delete")
        assertEquals("l 1\nl 3\nl 4\n", h.cm.getValue())
        h.doEx("d")
        assertEquals("l 1\nl 4\n", h.cm.getValue())
    }

    @Test
    fun ex_sort() = testVim(value = "b\nZ\nd\nc\na") { h ->
        h.doEx("sort")
        assertEquals("Z\na\nb\nc\nd", h.cm.getValue())
    }

    @Test
    fun ex_sort_reverse() = testVim(value = "b\nd\nc\na") { h ->
        h.doEx("sort!")
        assertEquals("d\nc\nb\na", h.cm.getValue())
    }

    @Test
    fun ex_sort_range() = testVim(value = "b\nd\nc\na") { h ->
        h.doEx("2,3sort")
        assertEquals("b\nc\nd\na", h.cm.getValue())
    }

    @Test
    fun ex_sort_oneline() = testVim(value = "b\nd\nc\na") { h ->
        h.doEx("2sort")
        // Expect no change.
        assertEquals("b\nd\nc\na", h.cm.getValue())
    }

    @Test
    fun ex_sort_ignoreCase() = testVim(value = "b\nZ\nd\nc\na") { h ->
        h.doEx("sort i")
        assertEquals("a\nb\nc\nd\nZ", h.cm.getValue())
    }

    @Test
    fun ex_sort_unique() = testVim(value = "b\nZ\na\na\nd\na\nc\na") { h ->
        h.doEx("sort u")
        assertEquals("Z\na\nb\nc\nd", h.cm.getValue())
    }

    @Test
    fun ex_sort_decimal() = testVim(value = "6\nd3\n s5\n.9") { h ->
        h.doEx("sort d")
        assertEquals("d3\n s5\n6\n.9", h.cm.getValue())
    }

    @Test
    fun ex_sort_decimal_negative() = testVim(value = "6\nd3\n s5\n.9\nz-9") { h ->
        h.doEx("sort d")
        assertEquals("z-9\nd3\n s5\n6\n.9", h.cm.getValue())
    }

    @Test
    fun ex_sort_decimal_reverse() = testVim(value = "6\nd3\n s5\n.9") { h ->
        h.doEx("sort! d")
        assertEquals(".9\n6\n s5\nd3", h.cm.getValue())
    }

    @Test
    fun ex_sort_hex() = testVim(value = "6\nd3\n s5\n&0xB\n.9") { h ->
        h.doEx("sort x")
        assertEquals(" s5\n6\n.9\n&0xB\nd3", h.cm.getValue())
    }

    @Test
    fun ex_sort_octal() = testVim(value = "6\nd3\n s5\n.9\n.8") { h ->
        h.doEx("sort o")
        assertEquals(".9\n.8\nd3\n s5\n6", h.cm.getValue())
    }

    @Test
    fun ex_sort_decimal_mixed() = testVim(value = "a3\nz\nc1\ny\nb2") { h ->
        h.doEx("sort d")
        assertEquals("z\ny\nc1\nb2\na3", h.cm.getValue())
    }

    @Test
    fun ex_sort_decimal_mixed_reverse() = testVim(value = "a3\nz\nc1\ny\nb2") { h ->
        h.doEx("sort! d")
        assertEquals("a3\nb2\nc1\nz\ny", h.cm.getValue())
    }

    @Test
    fun ex_sort_pattern_alpha() = testVim(value = "z\ny\nc1\nb2\na3") { h ->
        h.doEx("sort r/[a-z]/")
        assertEquals("a3\nb2\nc1\ny\nz", h.cm.getValue())
    }

    @Test
    fun ex_sort_pattern_alpha_reverse() = testVim(value = "z\ny\nc1\nb2\na3") { h ->
        h.doEx("sort! r /[a-z]/")
        assertEquals("z\ny\nc1\nb2\na3", h.cm.getValue())
    }

    @Test
    fun ex_sort_pattern_alpha_ignoreCase() = testVim(value = "z\nY\nC1\nb2\na3") { h ->
        h.doEx("sort ri/[a-z]/")
        assertEquals("a3\nb2\nC1\nY\nz", h.cm.getValue())
    }

    @Test
    fun ex_sort_pattern_alpha_longer() = testVim(
        value = "z\nab\naa\nade\nadelle\nalexandra\nalex\nadriana\nadele\ny\nc\nb\na"
    ) { h ->
        h.doEx("sort r/[a-z]+/")
        assertEquals(
            "a\naa\nab\nade\nadele\nadelle\nadriana\nalex\nalexandra\nb\nc\ny\nz",
            h.cm.getValue()
        )
    }

    @Test
    fun ex_sort_pattern_alpha_only() = testVim(value = "z1\ny2\na3\nc\nb") { h ->
        h.doEx("sort r/^[a-z]\$/")
        assertEquals("z1\ny2\na3\nb\nc", h.cm.getValue())
    }

    @Test
    fun ex_sort_pattern_alpha_only_reverse() = testVim(value = "z1\ny2\na3\nc\nb") { h ->
        h.doEx("sort! r/^[a-z]\$/")
        assertEquals("c\nb\nz1\ny2\na3", h.cm.getValue())
    }

    @Test
    fun ex_sort_pattern_alpha_num() = testVim(value = "z1\ny2\na3\nc\nb") { h ->
        h.doEx("sort r/[a-z][0-9]/")
        assertEquals("c\nb\na3\ny2\nz1", h.cm.getValue())
    }

    @Test
    fun ex_sort_pattern_no_r() = testVim(
        value = "1 in c \n z \n2 in d \n in\n3 in a \n"
    ) { h ->
        h.doEx("sort /in/")
        assertEquals(" z \n in\n\n3 in a \n1 in c \n2 in d ", h.cm.getValue())
        h.doEx("sort r/in/")
        assertEquals(" z \n\n in\n3 in a \n1 in c \n2 in d ", h.cm.getValue())
        h.doEx("sort r/. in/")
        assertEquals(" z \n\n in\n1 in c \n2 in d \n3 in a ", h.cm.getValue())
        h.doEx("sort /./")
        assertEquals("\n3 in a \n1 in c \n2 in d \n in\n z ", h.cm.getValue())
        h.doEx("sort /in d/")
        assertEquals("\n3 in a \n1 in c \n in\n z \n2 in d ", h.cm.getValue())
    }

    @Test
    fun ex_global() = testVim(value = "one one\n one one\n one one") { h ->
        h.cm.setCursor(0, 0)
        h.doEx("g/one/s//two")
        assertEquals("two one\n two one\n two one", h.cm.getValue())
        h.doEx("1,2g/two/s//one")
        assertEquals("one one\n one one\n two one", h.cm.getValue())
    }

    @Test
    fun ex_global_substitute_join() = testVim(
        value = "one\ntwo\nthree\nfour\nfive\n"
    ) { h ->
        h.doEx("g/o/s/\\n/;")
        assertEquals("one;two\nthree\nfour;five\n", h.cm.getValue())
    }

    @Test
    fun ex_global_substitute_split() = testVim(
        value = "one\ntwo\nthree\nfour\nfive\n"
    ) { h ->
        h.doEx("g/e/s/[or]/\\n")
        assertEquals("\nne\ntwo\nth\nee\nfour\nfive\n", h.cm.getValue())
    }

    @Test
    fun ex_global_delete() = testVim(
        value = "one\ntwo\nthree\nfour\nfive\nsix\nseven\nnine\n---"
    ) { h ->
        h.doEx("g/e/d\\n")
        assertEquals("two\nfour\nsix\n---", h.cm.getValue())
    }

    @Test
    fun ex_vglobal() = testVim(value = "one\n two\n three\n four\n five\n") { h ->
        h.doEx("v/e/s/o/e")
        assertEquals("one\n twe\n three\n feur\n five\n", h.cm.getValue())
    }

    @Test
    fun ex_normal() = testVim(value = "one one\nxxx\none one\none one") { h ->
        h.cm.setCursor(0, 0)
        h.doEx("g/one/normal    cw 1<lt>Esc><Esc>\$i\$")
        h.doKeys("r", "t")
        assertEquals(
            " 1<Esc> on\$e\nxxx\n 1<Esc> on\$e\n 1<Esc> on\$t",
            h.cm.getValue()
        )
    }

    @Test
    fun ex_yank() = testVim { h ->
        h.cm.setCursor(3, 0)
        h.doEx("y")
        val register = h.getRegisterController().getRegister(null)
        val line = h.cm.getLine(3)
        assertEquals(line + "\n", register.toString())
    }
}
