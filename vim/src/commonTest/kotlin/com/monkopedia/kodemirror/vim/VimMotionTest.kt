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
 * Motion tests ported from vim_test.js.
 *
 * Uses the default code (DEFAULT_CODE) from the test harness.
 */
class VimMotionTest {

    // The default code lines for reference:
    // Line 0: " wOrd1 (#%"
    // Line 1: " word3] "
    // Line 2: "aopop pop 0 1 2 3 4"
    // Line 3: " (a) [b] {c} "
    // Line 4: "int getchar(void) {"
    // Line 5: "  static char buf[BUFSIZ];"
    // Line 6: "  static char *bufp = buf;"
    // Line 7: "  if (n == 0) {  /* buffer is empty */"
    // Line 8: "    n = read(0, buf, sizeof buf);"
    // Line 9: "    bufp = buf;"
    // Line 10: "  }"
    // Line 11: ""
    // Line 12: "  return (--n >= 0) ? (unsigned char) *bufp++ : EOF;"
    // Line 13: " "
    // Line 14: "}"
    // Line 15: "" (trailing newline creates empty last line)

    // Line info
    private val word1Start = LinePos(0, 1)
    private val word1End = LinePos(0, 5)
    private val word2Start = LinePos(0, 7)
    private val word2End = LinePos(0, 9)
    private val word3Start = LinePos(1, 1)
    private val word3End = LinePos(1, 5)
    private val charLine = 2
    private val endOfDocument = LinePos(15, 0)

    @Test
    fun pipe() = testMotion(listOf("|"), LinePos(0, 0), LinePos(0, 4))

    @Test
    fun pipe_repeat() = testMotion(listOf("3", "|"), LinePos(0, 2), LinePos(0, 4))

    @Test
    fun h() = testMotion(listOf("h"), LinePos(0, 0), word1Start)

    @Test
    fun h_repeat() = testMotion(listOf("3", "h"), LinePos(0, 2), word1End)

    @Test
    fun l() = testMotion(listOf("l"), LinePos(0, 1))

    @Test
    fun l_repeat() = testMotion(listOf("2", "l"), LinePos(0, 2))

    @Test
    fun j() = testMotion(listOf("j"), LinePos(1, 5), word1End)

    @Test
    fun j_repeat() = testMotion(listOf("2", "j"), LinePos(2, 5), word1End)

    @Test
    fun j_repeat_clip() = testMotion(listOf("1000", "j"), endOfDocument)

    @Test
    fun k() = testMotion(listOf("k"), LinePos(0, 5), word3End)

    @Test
    fun k_repeat() = testMotion(listOf("2", "k"), LinePos(0, 4), LinePos(2, 4))

    @Test
    fun w() = testMotion(listOf("w"), word1Start)

    @Test
    fun w_repeat() = testMotion(listOf("2", "w"), word2Start)

    @Test
    fun w_wrap() = testMotion(listOf("w"), word3Start, word2Start)

    @Test
    fun w_endOfDocument() = testMotion(listOf("w"), endOfDocument, endOfDocument)

    @Test
    fun w_start_to_end() = testMotion(listOf("1000", "w"), endOfDocument, LinePos(0, 0))

    @Test
    fun capitalW() = testMotion(listOf("W"), LinePos(0, 1))

    @Test
    fun e() = testMotion(listOf("e"), word1End)

    @Test
    fun e_repeat() = testMotion(listOf("2", "e"), word2End)

    @Test
    fun e_wrap() = testMotion(listOf("e"), word3End, word2End)

    @Test
    fun b() = testMotion(listOf("b"), word3Start, word3End)

    @Test
    fun b_repeat() = testMotion(listOf("2", "b"), word2Start, word3End)

    @Test
    fun b_wrap() = testMotion(listOf("b"), word2Start, word3Start)

    @Test
    fun ge() = testMotion(listOf("g", "e"), word2End, word3End)

    @Test
    fun ge_repeat() = testMotion(listOf("2", "g", "e"), word1End, word3Start)

    @Test
    fun gg() = testMotion(listOf("g", "g"), LinePos(0, 1), LinePos(3, 1))

    @Test
    fun gg_repeat() = testMotion(listOf("3", "g", "g"), LinePos(2, 0))

    @Test
    fun capitalG() = testMotion(listOf("G"), LinePos(15, 0), LinePos(3, 1))

    @Test
    fun zero() = testMotion(listOf("0"), LinePos(0, 0), LinePos(0, 8))

    @Test
    fun caret() = testMotion(listOf("^"), LinePos(0, 1), LinePos(0, 8))

    @Test
    fun plus() = testMotion(listOf("+"), LinePos(1, 1), LinePos(0, 8))

    @Test
    fun minus() = testMotion(listOf("-"), LinePos(0, 1), LinePos(1, 4))

    @Test
    fun dollar() = testMotion(listOf("$"), LinePos(0, 9), LinePos(0, 1))

    @Test
    fun dollar_repeat() = testMotion(listOf("2", "$"), LinePos(1, 7), LinePos(0, 3))

    @Test
    fun f() = testMotion(listOf("f", "p"), LinePos(charLine, 2), LinePos(charLine, 0))

    @Test
    fun f_repeat() = testMotion(
        listOf("2", "f", "p"),
        LinePos(charLine, 6),
        LinePos(charLine, 2)
    )

    @Test
    fun t() = testMotion(
        listOf("t", "p"),
        LinePos(charLine, 1),
        LinePos(charLine, 0)
    )

    @Test
    fun capitalF() = testMotion(listOf("F", "p"), LinePos(charLine, 2), LinePos(charLine, 4))

    @Test
    fun capitalT() = testMotion(
        listOf("T", "p"),
        LinePos(charLine, 3),
        LinePos(charLine, 4)
    )

    @Test
    fun percent_parens() = testMotion(listOf("%"), LinePos(3, 3), LinePos(3, 1))

    @Test
    fun percent_squares() = testMotion(listOf("%"), LinePos(3, 7), LinePos(3, 5))

    @Test
    fun percent_braces() = testMotion(listOf("%"), LinePos(3, 11), LinePos(3, 9))

    // -- Operator motions (d, c, y, etc.) are tested in VimOperatorTest

    @Test
    fun keepHPos() = testMotion(
        listOf("5", "j", "j", "7", "k"),
        LinePos(8, 12),
        LinePos(12, 12)
    )

    @Test
    fun keepHPosEol() = testMotion(
        listOf("$", "2", "j"),
        LinePos(2, 18)
    )

    @Test
    fun changingLinesAfterEolOperation() = testVim { helpers ->
        helpers.cm.setCursor(0, 0)
        helpers.doKeys("$")
        helpers.doKeys("j")
        // After moving to Eol and then down, we should be at Eol of line 1
        helpers.assertCursorAt(1, 7)
        helpers.doKeys("j")
        // After moving down, we should be at Eol of line 2
        helpers.assertCursorAt(2, 18)
        helpers.doKeys("h")
        helpers.doKeys("j")
        // Line 3 is shorter, so clipped to its end
        helpers.assertCursorAt(3, 12)
        helpers.doKeys("j")
        helpers.doKeys("j")
        // Back to line 5, should restore the saved column
        helpers.assertCursorAt(5, 17)
    }

    // ---- Additional motion tests ported from upstream vim_test.js ----

    @Test
    fun space() = testMotion(listOf("Space"), LinePos(0, 1))

    @Test
    fun k_repeat_clip() = testMotion(listOf("1000", "k"), LinePos(0, 4), LinePos(2, 4))

    @Test
    fun w_multiple_newlines_no_space() = testMotion(listOf("w"), LinePos(12, 2), LinePos(11, 2))

    @Test
    fun w_multiple_newlines_with_space() = testMotion(listOf("w"), LinePos(14, 0), LinePos(12, 51))

    @Test
    fun capitalW_repeat() = testMotion(listOf("2", "W"), LinePos(1, 1), LinePos(0, 1))

    @Test
    fun e_start_to_end() = testMotion(listOf("1000", "e"), endOfDocument, LinePos(0, 0))

    @Test
    fun b_startOfDocument() = testMotion(listOf("b"), LinePos(0, 0), LinePos(0, 0))

    @Test
    fun b_end_to_start() = testMotion(listOf("1000", "b"), LinePos(0, 0), endOfDocument)

    @Test
    fun ge_wrap() = testMotion(listOf("g", "e"), word2End, word3Start)

    @Test
    fun ge_startOfDocument() = testMotion(listOf("g", "e"), LinePos(0, 0), LinePos(0, 0))

    @Test
    fun ge_end_to_start() = testMotion(listOf("1000", "g", "e"), LinePos(0, 0), endOfDocument)

    @Test
    fun capitalG_repeat() = testMotion(listOf("3", "G"), LinePos(2, 0))

    @Test
    fun underscore() = testMotion(listOf("6", "_"), LinePos(5, 2), LinePos(0, 8))

    @Test
    fun dollar_visual() = testMotion(listOf("v", "$"), LinePos(0, 10), LinePos(0, 1))

    @Test
    fun f_num() = testMotion(listOf("f", "2"), LinePos(charLine, 14), LinePos(charLine, 0))

    @Test
    fun f_shift_space() = testMotion(listOf("f", "<S-Space>"), LinePos(0, 6), word1Start)

    @Test
    fun t_repeat() = testMotion(listOf("2", "t", "p"), LinePos(charLine, 5), LinePos(charLine, 2))

    @Test
    fun capitalF_repeat() =
        testMotion(listOf("2", "F", "p"), LinePos(charLine, 2), LinePos(charLine, 6))

    @Test
    fun capitalT_repeat() =
        testMotion(listOf("2", "T", "p"), LinePos(charLine, 3), LinePos(charLine, 6))

    @Test
    fun percent_seek_outside() = testMotion(listOf("%"), LinePos(4, 16), LinePos(4, 1))

    @Test
    fun percent_seek_inside() = testMotion(listOf("%"), LinePos(4, 11), LinePos(4, 14))

    @Test
    fun percent_seek_skip() = testVim(value = "01234\"(\"()") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("%")
        h.assertCursorAt(0, 9)
    }

    @Test
    fun percent_skip_string() = testVim(value = "(\")\")") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("%")
        h.assertCursorAt(0, 4)
        h.cm.setCursor(0, 2)
        h.doKeys("%")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun percent_skip_comment() = testVim(value = "(/*)*/)") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("%")
        h.assertCursorAt(0, 6)
        h.cm.setCursor(0, 3)
        h.doKeys("%")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun paragraphForward() = testVim(value = "a\n\nb\nc\n\nd") { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("}")
        h.assertCursorAt(1, 0)
        h.cm.setCursor(0, 0)
        h.doKeys("2", "}")
        h.assertCursorAt(4, 0)
        h.cm.setCursor(0, 0)
        h.doKeys("6", "}")
        h.assertCursorAt(5, 0)
    }

    @Test
    fun paragraphBackward() = testVim(value = "a\n\nb\nc\n\nd") { h ->
        h.cm.setCursor(5, 0)
        h.doKeys("{")
        h.assertCursorAt(4, 0)
        h.cm.setCursor(5, 0)
        h.doKeys("2", "{")
        h.assertCursorAt(1, 0)
        h.cm.setCursor(5, 0)
        h.doKeys("6", "{")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun sentenceBackward() = testVim(
        value = "sentence1.\n\n\nsentence2\n\nsentence3. sentence4\n" +
            "   sentence5? sentence6!"
    ) { h ->
        h.cm.setCursor(6, 23)
        h.doKeys("(")
        h.assertCursorAt(6, 14)
        h.doKeys("2", "(")
        h.assertCursorAt(5, 0)
        h.doKeys("(")
        h.assertCursorAt(4, 0)
        h.doKeys("(")
        h.assertCursorAt(3, 0)
        h.doKeys("(")
        h.assertCursorAt(2, 0)
        h.doKeys("(")
        h.assertCursorAt(0, 0)
        h.doKeys("(")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun sentenceForward() = testVim(
        value = "sentence1.\n\n\nsentence2\n\nsentence3. sentence4\n" +
            "   sentence5? sentence6!"
    ) { h ->
        h.cm.setCursor(0, 0)
        h.doKeys("2", ")")
        h.assertCursorAt(3, 0)
        h.doKeys(")")
        h.assertCursorAt(4, 0)
        h.doKeys(")")
        h.assertCursorAt(5, 0)
        h.doKeys(")")
        h.assertCursorAt(5, 11)
        h.doKeys(")")
        h.assertCursorAt(6, 14)
        h.doKeys(")")
        h.assertCursorAt(6, 23)
        h.doKeys(")")
        h.assertCursorAt(6, 23)
    }

    // ---- Bracket motions ported from upstream vim_test.js ----

    // The sandbox value used for bracket motion tests in upstream
    private val squareBracketMotionSandbox = buildString {
        appendLine("({") // 0
        appendLine("  ({") // 1
        appendLine("  /*comment {") // 2
        appendLine("            */(") // 3
        appendLine("#else                ") // 4
        appendLine("  /*       )") // 5
        appendLine("#if        }") // 6
        appendLine("  )}*/") // 7
        appendLine(")}") // 8
        appendLine("{}") // 9
        appendLine("#else {{") // 10
        appendLine("{}") // 11
        appendLine("}") // 12
        appendLine("{") // 13
        appendLine("#endif") // 14
        appendLine("}") // 15
        appendLine("}") // 16
        append("#else") // 17 (no trailing newline)
    }

    @Test
    fun bracketForwardBackward_sections() = testVim(value = squareBracketMotionSandbox) { h ->
        // ]] forward
        h.cm.setCursor(0, 0)
        h.doKeys("]", "]")
        h.assertCursorAt(9, 0)
        h.doKeys("2", "]", "]")
        h.assertCursorAt(13, 0)
        h.doKeys("]", "]")
        h.assertCursorAt(17, 0)
        // [[ backward
        h.doKeys("[", "[")
        h.assertCursorAt(13, 0)
        h.doKeys("2", "[", "[")
        h.assertCursorAt(9, 0)
        h.doKeys("[", "[")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun bracketEndSection() = testVim(value = squareBracketMotionSandbox) { h ->
        // ][ forward (end of section)
        h.cm.setCursor(0, 0)
        h.doKeys("]", "[")
        h.assertCursorAt(12, 0)
        h.doKeys("2", "]", "[")
        h.assertCursorAt(16, 0)
        h.doKeys("]", "[")
        h.assertCursorAt(17, 0)
        // [] backward (end of section)
        h.doKeys("[", "]")
        h.assertCursorAt(16, 0)
        h.doKeys("2", "[", "]")
        h.assertCursorAt(12, 0)
        h.doKeys("[", "]")
        h.assertCursorAt(0, 0)
    }

    @Test
    fun bracketCurlyBrace() = testVim(value = squareBracketMotionSandbox) { h ->
        // [{ backward to unmatched {
        h.cm.setCursor(4, 10)
        h.doKeys("[", "{")
        h.assertCursorAt(2, 12)
        h.doKeys("2", "[", "{")
        h.assertCursorAt(0, 1)
        // ]} forward to unmatched }
        h.cm.setCursor(4, 10)
        h.doKeys("]", "}")
        h.assertCursorAt(6, 11)
        h.doKeys("2", "]", "}")
        h.assertCursorAt(8, 1)
        // From start of document
        h.cm.setCursor(0, 1)
        h.doKeys("]", "}")
        h.assertCursorAt(8, 1)
        h.doKeys("[", "{")
        h.assertCursorAt(0, 1)
    }

    @Test
    fun bracketParen() = testVim(value = squareBracketMotionSandbox) { h ->
        // [( backward to unmatched (
        h.cm.setCursor(4, 10)
        h.doKeys("[", "(")
        h.assertCursorAt(3, 14)
        h.doKeys("2", "[", "(")
        h.assertCursorAt(0, 0)
        // ]) forward to unmatched )
        h.cm.setCursor(4, 10)
        h.doKeys("]", ")")
        h.assertCursorAt(5, 11)
        h.doKeys("2", "]", ")")
        h.assertCursorAt(8, 0)
        // Round-trip
        h.doKeys("[", "(")
        h.assertCursorAt(0, 0)
        h.doKeys("]", ")")
        h.assertCursorAt(8, 0)
    }

    @Test
    fun bracketMethod() = testVim(value = squareBracketMotionSandbox) { h ->
        // [m backward to start of method
        h.cm.setCursor(11, 0)
        h.doKeys("[", "m")
        h.assertCursorAt(10, 7)
        h.doKeys("4", "[", "m")
        h.assertCursorAt(1, 3)
        // ]m forward to start of method
        h.doKeys("5", "]", "m")
        h.assertCursorAt(11, 0)
        // [M backward to end of method
        h.doKeys("[", "M")
        h.assertCursorAt(9, 1)
        // ]M forward to end of method
        h.doKeys("3", "]", "M")
        h.assertCursorAt(15, 0)
        h.doKeys("5", "[", "M")
        h.assertCursorAt(7, 3)
    }
}
