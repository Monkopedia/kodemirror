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

class VimSubstituteTest {

    // ========================================================================
    // testSubstitute tests (upstream lines 4623-4795)
    // ========================================================================

    @Test
    fun ex_substitute_capture() = testSubstitute(
        value = "a11 a12 a13",
        expr = "s/(\\d+)/\$1\$1/g",
        expectedValue = "a1111 a1212 a1313",
        noPcreExpr = "s/\\(\\d\\+\\)/\\1\\1/g"
    )

    @Test
    fun ex_substitute_capture2() = testSubstitute(
        value = "a 0 b",
        expr = "s/(\\d+)/\$\$\$1\$1/g",
        expectedValue = "a \$00 b",
        noPcreExpr = "s/\\(\\d\\+\\)/\$\\1\\1/g"
    )

    @Test
    fun ex_substitute_nocapture() = testSubstitute(
        value = "a11 a12 a13",
        expr = "s/(\\d+)/\$\$1\$\$1/g",
        expectedValue = "a\$1\$1 a\$1\$1 a\$1\$1",
        noPcreExpr = "s/\\(\\d\\+\\)/\$1\$1/g"
    )

    @Test
    fun ex_substitute_nocapture2() = testSubstitute(
        value = "a 0 b",
        expr = "s/(\\d+)/\$\$1\$1/g",
        expectedValue = "a \$10 b",
        noPcreExpr = "s/\\(\\d\\+\\)/\\\$1\\1/g"
    )

    @Test
    fun ex_substitute_nocapture3() = testSubstitute(
        value = "a b c",
        expr = "s/b/\$\$/",
        expectedValue = "a \$ c",
        noPcreExpr = "s/b/\$/"
    )

    @Test
    fun ex_substitute_slash_regex() = testSubstitute(
        value = "one/two \n three/four",
        expr = "%s/\\//|",
        expectedValue = "one|two \n three|four"
    )

    @Test
    fun ex_substitute_pipe_regex() = testSubstitute(
        value = "one|two \n three|four",
        expr = "%s/\\|/,/",
        expectedValue = "one,two \n three,four",
        noPcreExpr = "%s/|/,/"
    )

    @Test
    fun ex_substitute_or_regex() = testSubstitute(
        value = "one|two \n three|four",
        expr = "%s/o|e|u/a/g",
        expectedValue = "ana|twa \n thraa|faar",
        noPcreExpr = "%s/o\\|e\\|u/a/g"
    )

    @Test
    fun ex_substitute_or_word_regex() = testSubstitute(
        value = "one|two \n three|four",
        expr = "%s/(one|two)/five/g",
        expectedValue = "five|five \n three|four",
        noPcreExpr = "%s/\\(one\\|two\\)/five/g"
    )

    @Test
    fun ex_substitute_forward_slash_regex() = testSubstitute(
        value = "forward slash / was here",
        expr = "%s#\\/##g",
        expectedValue = "forward slash  was here",
        noPcreExpr = "%s#/##g"
    )

    @Test
    fun ex_substitute_backslashslash_regex() = testSubstitute(
        value = "one\\two \n three\\four",
        expr = "%s/\\\\/,",
        expectedValue = "one,two \n three,four"
    )

    @Test
    fun ex_substitute_slash_replacement() = testSubstitute(
        value = "one,two \n three,four",
        expr = "%s/,/\\/",
        expectedValue = "one/two \n three/four"
    )

    @Test
    fun ex_substitute_backslash_replacement() = testSubstitute(
        value = "one,two \n three,four",
        expr = "%s/,/\\\\/g",
        expectedValue = "one\\two \n three\\four"
    )

    @Test
    fun ex_substitute_multibackslash_replacement() = testSubstitute(
        value = "one,two \n three,four",
        expr = "%s/,/\\\\\\\\\\\\\\\\/g",
        expectedValue = "one\\\\\\\\two \n three\\\\\\\\four"
    )

    @Test
    fun ex_substitute_dollar_assertion() = testSubstitute(
        value = "one,two \n three,four",
        expr = "%s/\$/,/g",
        expectedValue = "one,two ,\n three,four,"
    )

    @Test
    fun ex_substitute_dollar_assertion_empty_lines() = testSubstitute(
        value = "\n\n\n\n\n\n",
        expr = "%s/\$/;/g",
        expectedValue = ";\n;\n;\n;\n;\n;\n;"
    )

    @Test
    fun ex_substitute_dollar_literal() = testSubstitute(
        value = "one\$two\n\$three\nfour\$\n\$",
        expr = "%s/\\\$/,/g",
        expectedValue = "one,two\n,three\nfour,\n,"
    )

    @Test
    fun ex_substitute_newline_match() = testSubstitute(
        value = "one,two \n three,four",
        expr = "%s/\\n/,/g",
        expectedValue = "one,two , three,four"
    )

    @Test
    fun ex_substitute_newline_join_global() = testSubstitute(
        value = "one,two \n three,four \n five \n six",
        expr = "2s/\\n/,/g",
        expectedValue = "one,two \n three,four , five \n six"
    )

    @Test
    fun ex_substitute_newline_join_noglobal() = testSubstitute(
        value = "one,two \n three,four \n five \n six\n",
        expr = "2,3s/\\n/,/",
        expectedValue = "one,two \n three,four , five , six\n"
    )

    @Test
    fun ex_substitute_newline_replacement() = testSubstitute(
        value = "one,two, \n three,four,",
        expr = "%s/,/\\n/g",
        expectedValue = "one\ntwo\n \n three\nfour\n"
    )

    @Test
    fun ex_substitute_newline_multiple_splits() = testSubstitute(
        value = "one,two, \n three,four,five,six, \n seven,",
        expr = "2s/,/\\n/g",
        expectedValue = "one,two, \n three\nfour\nfive\nsix\n \n seven,"
    )

    @Test
    fun ex_substitute_newline_first_occurrences() = testSubstitute(
        value = "one,two, \n three,four,five,six, \n seven,",
        expr = "%s/,/\\n/",
        expectedValue = "one\ntwo, \n three\nfour,five,six, \n seven\n"
    )

    @Test
    fun ex_substitute_braces_word() = testSubstitute(
        value = "ababab abb ab{2}",
        expr = "%s/(ab){2}//g",
        expectedValue = "ab abb ab{2}",
        noPcreExpr = "%s/\\(ab\\)\\{2\\}//g"
    )

    @Test
    fun ex_substitute_braces_range() = testSubstitute(
        value = "a aa aaa aaaa",
        expr = "%s/a{2,3}//g",
        expectedValue = "a   a",
        noPcreExpr = "%s/a\\{2,3\\}//g"
    )

    @Test
    fun ex_substitute_braces_literal() = testSubstitute(
        value = "ababab abb ab{2}",
        expr = "%s/ab\\{2\\}//g",
        expectedValue = "ababab abb ",
        noPcreExpr = "%s/ab{2}//g"
    )

    @Test
    fun ex_substitute_braces_char() = testSubstitute(
        value = "ababab abb ab{2}",
        expr = "%s/ab{2}//g",
        expectedValue = "ababab  ab{2}",
        noPcreExpr = "%s/ab\\{2\\}//g"
    )

    @Test
    fun ex_substitute_braces_no_escape() = testSubstitute(
        value = "ababab abb ab{2}",
        expr = "%s/ab{2}//g",
        expectedValue = "ababab  ab{2}",
        noPcreExpr = "%s/ab\\{2}//g"
    )

    @Test
    fun ex_substitute_count() = testSubstitute(
        value = "1\n2\n3\n4",
        expr = "s/\\d/0/i 2",
        expectedValue = "1\n0\n0\n4"
    )

    @Test
    fun ex_substitute_count_with_range() = testSubstitute(
        value = "1\n2\n3\n4",
        expr = "1,3s/\\d/0/ 3",
        expectedValue = "1\n2\n0\n0"
    )

    @Test
    fun ex_substitute_not_global() = testSubstitute(
        value = "aaa\nbaa\ncaa",
        expr = "%s/a/x/",
        expectedValue = "xaa\nbxa\ncxa"
    )

    @Test
    fun ex_substitute_optional() = testSubstitute(
        value = "aaa  aa\n aa",
        expr = "%s/(a*)/<\$1>/g",
        expectedValue = "<aaa> <> <aa>\n<> <aa>",
        noPcreExpr = "%s/\\(a*\\)/<\\1>/g"
    )

    @Test
    fun ex_substitute_empty_match() = testSubstitute(
        value = "aaa  aa\n aa\nbb\n",
        expr = "%s/(a+|\$)/<\$1>/g",
        expectedValue = "<aaa>  <aa>\n <aa>\nbb<>\n<>",
        noPcreExpr = "%s/\\(a\\+\\|\$\\)/<\\1>/g"
    )

    @Test
    fun ex_substitute_empty_or_match() = testSubstitute(
        value = "1234\n567\n89\n0\n",
        expr = "%s/(..|\$)/<\$1>/g",
        expectedValue = "<12><34>\n<56>7<>\n<89>\n0<>\n<>",
        noPcreExpr = "%s/\\(..\\|\$\\)/<\\1>/g"
    )

    // ========================================================================
    // Ampersand substitute tests (upstream lines 4674-4691)
    // ========================================================================

    @Test
    fun ex_substitute_ampersand_pcre() = testVim(value = "foo") { h ->
        h.cm.setCursor(0, 0)
        Vim.setOption("pcre", true)
        h.doEx("%s/foo/namespace.&/")
        assertEquals("namespace.foo", h.cm.getValue())
    }

    @Test
    fun ex_substitute_ampersand_multiple_pcre() = testVim(value = "foo\nfzo") { h ->
        h.cm.setCursor(0, 0)
        Vim.setOption("pcre", true)
        h.doEx("%s/f.o/namespace.&/")
        assertEquals("namespace.foo\nnamespace.fzo", h.cm.getValue())
    }

    @Test
    fun ex_escaped_ampersand_should_not_substitute_pcre() = testVim(value = "foo") { h ->
        h.cm.setCursor(0, 0)
        Vim.setOption("pcre", true)
        h.doEx("%s/foo/namespace.\\&/")
        assertEquals("namespace.&", h.cm.getValue())
    }

    // ========================================================================
    // testSubstituteConfirm tests (upstream lines 4796-4833)
    // ========================================================================

    @Test
    fun ex_substitute_confirm_emptydoc() = testSubstituteConfirm(
        command = "%s/x/b/c",
        initial = "",
        expected = "",
        keys = "",
        finalPos = LinePos(0, 0)
    )

    @Test
    fun ex_substitute_confirm_nomatch() = testSubstituteConfirm(
        command = "%s/x/b/c",
        initial = "ba a\nbab",
        expected = "ba a\nbab",
        keys = "",
        finalPos = LinePos(0, 0)
    )

    @Test
    fun ex_substitute_confirm_accept() = testSubstituteConfirm(
        command = "%s/a/b/cg",
        initial = "ba a\nbab",
        expected = "bb b\nbbb",
        keys = "yyy",
        finalPos = LinePos(1, 1)
    )

    @Test
    fun ex_substitute_confirm_random_keys() = testSubstituteConfirm(
        command = "%s/a/b/cg",
        initial = "ba a\nbab",
        expected = "bb b\nbbb",
        keys = "ysdkywerty",
        finalPos = LinePos(1, 1)
    )

    @Test
    fun ex_substitute_confirm_some() = testSubstituteConfirm(
        command = "%s/a/b/cg",
        initial = "ba a\nbab",
        expected = "bb a\nbbb",
        keys = "yny",
        finalPos = LinePos(1, 1)
    )

    @Test
    fun ex_substitute_confirm_all() = testSubstituteConfirm(
        command = "%s/a/b/cg",
        initial = "ba a\nbab",
        expected = "bb b\nbbb",
        keys = "a",
        finalPos = LinePos(1, 1)
    )

    @Test
    fun ex_substitute_confirm_accept_then_all() = testSubstituteConfirm(
        command = "%s/a/b/cg",
        initial = "ba a\nbab",
        expected = "bb b\nbbb",
        keys = "ya",
        finalPos = LinePos(1, 1)
    )

    @Test
    fun ex_substitute_confirm_quit() = testSubstituteConfirm(
        command = "%s/a/b/cg",
        initial = "ba a\nbab",
        expected = "bb a\nbab",
        keys = "yq",
        finalPos = LinePos(0, 3)
    )

    @Test
    fun ex_substitute_confirm_last() = testSubstituteConfirm(
        command = "%s/a/b/cg",
        initial = "ba a\nbab",
        expected = "bb b\nbab",
        keys = "yl",
        finalPos = LinePos(0, 3)
    )

    @Test
    fun ex_substitute_confirm_oneline() = testSubstituteConfirm(
        command = "1s/a/b/cg",
        initial = "ba a\nbab",
        expected = "bb b\nbab",
        keys = "yl",
        finalPos = LinePos(0, 3)
    )

    @Test
    fun ex_substitute_confirm_range_accept() = testSubstituteConfirm(
        command = "1,2s/a/b/cg",
        initial = "aa\na \na\na",
        expected = "bb\nb \na\na",
        keys = "yyy",
        finalPos = LinePos(1, 0)
    )

    @Test
    fun ex_substitute_confirm_range_some() = testSubstituteConfirm(
        command = "1,3s/a/b/cg",
        initial = "aa\na \na\na",
        expected = "ba\nb \nb\na",
        keys = "ynyy",
        finalPos = LinePos(2, 0)
    )

    @Test
    fun ex_substitute_confirm_range_all() = testSubstituteConfirm(
        command = "1,3s/a/b/cg",
        initial = "aa\na \na\na",
        expected = "bb\nb \nb\na",
        keys = "a",
        finalPos = LinePos(2, 0)
    )

    @Test
    fun ex_substitute_confirm_range_last() = testSubstituteConfirm(
        command = "1,3s/a/b/cg",
        initial = "aa\na \na\na",
        expected = "bb\nb \na\na",
        keys = "yyl",
        finalPos = LinePos(1, 0)
    )
}
