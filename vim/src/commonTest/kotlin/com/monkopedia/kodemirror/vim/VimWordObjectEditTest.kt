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

/**
 * Ports all testEdit calls from upstream vim_test.js (lines 1550-1642).
 * These delete tests effectively cover word-wise Change, Visual & Yank.
 * Tabs are used as differentiated whitespace to catch edge cases.
 */
class VimWordObjectEditTest {

    // ---- Normal word ----

    @Test
    fun diw_mid_spc() = testEdit("foo \tbAr\t baz", Regex("A"), "diw", "foo \t\t baz")

    @Test
    fun daw_mid_spc() = testEdit("foo \tbAr\t baz", Regex("A"), "daw", "foo \tbaz")

    @Test
    fun diw_mid_punct() = testEdit("foo \tbAr.\t baz", Regex("A"), "diw", "foo \t.\t baz")

    @Test
    fun daw_mid_punct() = testEdit("foo \tbAr.\t baz", Regex("A"), "daw", "foo.\t baz")

    @Test
    fun diw_mid_punct2() = testEdit("foo \t,bAr.\t baz", Regex("A"), "diw", "foo \t,.\t baz")

    @Test
    fun daw_mid_punct2() = testEdit("foo \t,bAr.\t baz", Regex("A"), "daw", "foo \t,.\t baz")

    @Test
    fun diw_start_spc() = testEdit("bAr \tbaz", Regex("A"), "diw", " \tbaz")

    @Test
    fun daw_start_spc() = testEdit("bAr \tbaz", Regex("A"), "daw", "baz")

    @Test
    fun diw_start_punct() = testEdit("bAr. \tbaz", Regex("A"), "diw", ". \tbaz")

    @Test
    fun daw_start_punct() = testEdit("bAr. \tbaz", Regex("A"), "daw", ". \tbaz")

    @Test
    fun diw_end_spc() = testEdit("foo \tbAr", Regex("A"), "diw", "foo \t")

    @Test
    fun daw_end_spc() = testEdit("foo \tbAr", Regex("A"), "daw", "foo")

    @Test
    fun diw_end_punct() = testEdit("foo \tbAr.", Regex("A"), "diw", "foo \t.")

    @Test
    fun daw_end_punct() = testEdit("foo \tbAr.", Regex("A"), "daw", "foo.")

    @Test
    fun diw_space_word1() = testEdit("foo \t\n\tbar.", Regex("\\t"), "diw", "foo\n\tbar.")

    @Test
    fun diw_space_word2() = testEdit("foo +bar.", Regex(" "), "diw", "foo+bar.")

    @Test
    fun diw_space_word3() = testEdit(" foo bar.", Regex(" "), "diw", "foo bar.")

    // ---- Big word ----

    @Test
    fun diW_mid_spc() = testEdit("foo \tbAr\t baz", Regex("A"), "diW", "foo \t\t baz")

    @Test
    fun daW_mid_spc() = testEdit("foo \tbAr\t baz", Regex("A"), "daW", "foo \tbaz")

    @Test
    fun diW_mid_punct() = testEdit("foo \tbAr.\t baz", Regex("A"), "diW", "foo \t\t baz")

    @Test
    fun daW_mid_punct() = testEdit("foo \tbAr.\t baz", Regex("A"), "daW", "foo \tbaz")

    @Test
    fun diW_mid_punct2() = testEdit("foo \t,bAr.\t baz", Regex("A"), "diW", "foo \t\t baz")

    @Test
    fun daW_mid_punct2() = testEdit("foo \t,bAr.\t baz", Regex("A"), "daW", "foo \tbaz")

    @Test
    fun diW_start_spc() = testEdit("bAr\t baz", Regex("A"), "diW", "\t baz")

    @Test
    fun daW_start_spc() = testEdit("bAr\t baz", Regex("A"), "daW", "baz")

    @Test
    fun diW_start_punct() = testEdit("bAr.\t baz", Regex("A"), "diW", "\t baz")

    @Test
    fun daW_start_punct() = testEdit("bAr.\t baz", Regex("A"), "daW", "baz")

    @Test
    fun diW_end_spc() = testEdit("foo \tbAr", Regex("A"), "diW", "foo \t")

    @Test
    fun daW_end_spc() = testEdit("foo \tbAr", Regex("A"), "daW", "foo")

    @Test
    fun diW_end_punct() = testEdit("foo \tbAr.", Regex("A"), "diW", "foo \t")

    @Test
    fun daW_end_punct() = testEdit("foo \tbAr.", Regex("A"), "daW", "foo")

    @Test
    fun diW_space_word2() = testEdit("foo +bar.", Regex(" "), "diW", "foo+bar.")

    // ---- Deleting text objects - Open and close on same line ----

    @Test
    fun di_paren_open_spc() = testEdit("foo (bAr) baz", Regex("\\("), "di(", "foo () baz")

    @Test
    fun di_close_paren_open_spc() = testEdit("foo (bAr) baz", Regex("\\("), "di)", "foo () baz")

    @Test
    fun dib_open_spc() = testEdit("foo (bAr) baz", Regex("\\("), "dib", "foo () baz")

    @Test
    fun da_paren_open_spc() = testEdit("foo (bAr) baz", Regex("\\("), "da(", "foo  baz")

    @Test
    fun da_close_paren_open_spc() = testEdit("foo (bAr) baz", Regex("\\("), "da)", "foo  baz")

    @Test
    fun di_paren_middle_spc() = testEdit("foo (bAr) baz", Regex("A"), "di(", "foo () baz")

    @Test
    fun di_close_paren_middle_spc() = testEdit("foo (bAr) baz", Regex("A"), "di)", "foo () baz")

    @Test
    fun da_paren_middle_spc() = testEdit("foo (bAr) baz", Regex("A"), "da(", "foo  baz")

    @Test
    fun da_close_paren_middle_spc() = testEdit("foo (bAr) baz", Regex("A"), "da)", "foo  baz")

    @Test
    fun di_paren_close_spc() = testEdit("foo (bAr) baz", Regex("\\)"), "di(", "foo () baz")

    @Test
    fun di_close_paren_close_spc() = testEdit("foo (bAr) baz", Regex("\\)"), "di)", "foo () baz")

    @Test
    fun da_paren_close_spc() = testEdit("foo (bAr) baz", Regex("\\)"), "da(", "foo  baz")

    @Test
    fun da_close_paren_close_spc() = testEdit("foo (bAr) baz", Regex("\\)"), "da)", "foo  baz")

    @Test
    fun di_backtick() = testEdit("foo `bAr` baz", Regex("`"), "di`", "foo `` baz")

    @Test
    fun di_angle_right() = testEdit("foo <bAr> baz", Regex("<"), "di>", "foo <> baz")

    @Test
    fun da_angle_left() = testEdit("foo <bAr> baz", Regex("<"), "da<", "foo  baz")

    // ---- delete around and inner b ----

    @Test
    fun dab_on_paren_should_delete_around_paren_block() =
        testEdit("o( in(abc) )", Regex("\\(a"), "dab", "o( in )")

    // ---- delete around and inner B ----

    @Test
    fun daB_on_brace_should_delete_around_brace_block() =
        testEdit("o{ in{abc} }", Regex("\\{a"), "daB", "o{ in }")

    @Test
    fun diB_on_brace_should_delete_inner_brace_block() =
        testEdit("o{ in{abc} }", Regex("\\{a"), "diB", "o{ in{} }")

    @Test
    fun da_brace_on_brace_should_delete_inner_block() =
        testEdit("o{ in{abc} }", Regex("\\{a"), "da{", "o{ in }")

    @Test
    fun di_bracket_on_paren_should_not_delete() =
        testEdit("foo (bAr) baz", Regex("\\("), "di[", "foo (bAr) baz")

    @Test
    fun di_bracket_on_close_paren_should_not_delete() =
        testEdit("foo (bAr) baz", Regex("\\)"), "di[", "foo (bAr) baz")

    @Test
    fun da_bracket_on_paren_should_not_delete() =
        testEdit("foo (bAr) baz", Regex("\\("), "da[", "foo (bAr) baz")

    @Test
    fun da_bracket_on_close_paren_should_not_delete() =
        testEdit("foo (bAr) baz", Regex("\\)"), "da[", "foo (bAr) baz")

    // ---- Open and close on different lines, equally indented ----

    @Test
    fun di_brace_middle_spc_equal_indent() = testEdit("a{\n\tbar\n}b", Regex("r"), "di{", "a{}b")

    @Test
    fun di_close_brace_middle_spc_equal_indent() =
        testEdit("a{\n\tbar\n}b", Regex("r"), "di}", "a{}b")

    @Test
    fun da_brace_middle_spc_equal_indent() = testEdit("a{\n\tbar\n}b", Regex("r"), "da{", "ab")

    @Test
    fun da_close_brace_middle_spc_equal_indent() =
        testEdit("a{\n\tbar\n}b", Regex("r"), "da}", "ab")

    @Test
    fun daB_middle_spc_equal_indent() = testEdit("a{\n\tbar\n}b", Regex("r"), "daB", "ab")

    // ---- Open/close diff lines, open indented less than close ----

    @Test
    fun di_brace_middle_spc_close_indented() =
        testEdit("a{\n\tbar\n\t}b", Regex("r"), "di{", "a{}b")

    @Test
    fun di_close_brace_middle_spc_close_indented() =
        testEdit("a{\n\tbar\n\t}b", Regex("r"), "di}", "a{}b")

    @Test
    fun da_brace_middle_spc_close_indented() = testEdit("a{\n\tbar\n\t}b", Regex("r"), "da{", "ab")

    @Test
    fun da_close_brace_middle_spc_close_indented() =
        testEdit("a{\n\tbar\n\t}b", Regex("r"), "da}", "ab")

    // ---- Open/close diff lines, open indented more than close ----

    @Test
    fun di_bracket_middle_spc_open_indented() =
        testEdit("a\t[\n\tbar\n]b", Regex("r"), "di[", "a\t[]b")

    @Test
    fun di_close_bracket_middle_spc_open_indented() =
        testEdit("a\t[\n\tbar\n]b", Regex("r"), "di]", "a\t[]b")

    @Test
    fun da_bracket_middle_spc_open_indented() =
        testEdit("a\t[\n\tbar\n]b", Regex("r"), "da[", "a\tb")

    @Test
    fun da_close_bracket_middle_spc_open_indented() =
        testEdit("a\t[\n\tbar\n]b", Regex("r"), "da]", "a\tb")

    // ---- Angle brackets on different lines ----

    @Test
    fun di_angle_left_middle_spc_diff_lines() =
        testEdit("a\t<\n\tbar\n>b", Regex("r"), "di<", "a\t<>b")

    @Test
    fun di_angle_right_middle_spc_diff_lines() =
        testEdit("a\t<\n\tbar\n>b", Regex("r"), "di>", "a\t<>b")

    @Test
    fun da_angle_left_middle_spc_diff_lines() =
        testEdit("a\t<\n\tbar\n>b", Regex("r"), "da<", "a\tb")

    @Test
    fun da_angle_right_middle_spc_diff_lines() =
        testEdit("a\t<\n\tbar\n>b", Regex("r"), "da>", "a\tb")
}
