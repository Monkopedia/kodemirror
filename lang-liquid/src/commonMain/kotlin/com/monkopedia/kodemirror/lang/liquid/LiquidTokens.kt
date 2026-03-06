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
package com.monkopedia.kodemirror.lang.liquid

import com.monkopedia.kodemirror.lezer.lr.ExternalTokenizer

// Token type constants from generated grammar
internal const val INTERPOLATION_START = 1
internal const val TAG_START = 2
internal const val END_TAG_START = 3
internal const val TEXT = 180
internal const val ENDRAW_TAG_START = 4
internal const val RAW_TEXT = 181
internal const val ENDCOMMENT_TAG_START = 5
internal const val COMMENT_TEXT = 182
internal const val INLINE_COMMENT = 6

// Character codes
private const val CH_BRACE_L = 123
private const val CH_BRACE_R = 125
private const val CH_PERCENT = 37
private const val CH_HASH = 35
private const val CH_SPACE = 32
private const val CH_NEWLINE = 10
private const val CH_DASH = 45
private const val CH_E = 101
private const val CH_N = 110
private const val CH_D = 100

private fun wordChar(code: Int): Boolean = (code in 65..90) || (code in 97..122)

/**
 * Base tokenizer: handles text, {{ interpolation starts, and {% tag starts.
 */
internal val base = ExternalTokenizer({ input, _ ->
    val start = input.pos
    while (true) {
        val next = input.next
        if (next < 0) break
        if (next == CH_BRACE_L) {
            val after = input.peek(1)
            if (after == CH_BRACE_L) {
                if (input.pos > start) break
                input.acceptToken(INTERPOLATION_START, 2)
                return@ExternalTokenizer
            } else if (after == CH_PERCENT) {
                if (input.pos > start) break
                var scan = 2
                var size = 2
                while (true) {
                    val n = input.peek(scan)
                    if (n == CH_SPACE || n == CH_NEWLINE) {
                        ++scan
                    } else if (n == CH_HASH) {
                        ++scan
                        while (true) {
                            val comment = input.peek(scan)
                            if (comment < 0 || comment == CH_NEWLINE) break
                            scan++
                        }
                    } else if (n == CH_DASH && size == 2) {
                        size = ++scan
                    } else {
                        val end = n == CH_E &&
                            input.peek(scan + 1) == CH_N &&
                            input.peek(scan + 2) == CH_D
                        input.acceptToken(if (end) END_TAG_START else TAG_START, size)
                        return@ExternalTokenizer
                    }
                }
            }
        }
        input.advance()
        if (next == CH_NEWLINE) break
    }
    if (input.pos > start) input.acceptToken(TEXT)
})

private fun rawTokenizer(endTag: String, textToken: Int, tagStartToken: Int): ExternalTokenizer =
    ExternalTokenizer({ input, _ ->
        val start = input.pos
        while (true) {
            val next = input.next
            if (next == CH_BRACE_L && input.peek(1) == CH_PERCENT) {
                var scan = 2
                while (true) {
                    val ch = input.peek(scan)
                    if (ch != CH_SPACE && ch != CH_NEWLINE) break
                    scan++
                }
                var word = ""
                while (true) {
                    val wn = input.peek(scan)
                    if (!wordChar(wn)) break
                    word += wn.toChar()
                    scan++
                }
                if (word == endTag) {
                    if (input.pos > start) break
                    input.acceptToken(tagStartToken, 2)
                    break
                }
            } else if (next < 0) {
                break
            }
            input.advance()
            if (next == CH_NEWLINE) break
        }
        if (input.pos > start) input.acceptToken(textToken)
    })

/**
 * Tokenizer for comment blocks (endcomment tag).
 */
internal val comment = rawTokenizer("endcomment", COMMENT_TEXT, ENDCOMMENT_TAG_START)

/**
 * Tokenizer for raw blocks (endraw tag).
 */
internal val raw = rawTokenizer("endraw", RAW_TEXT, ENDRAW_TAG_START)

/**
 * Inline comment tokenizer: handles #... inline comments inside Tags.
 */
internal val inlineComment = ExternalTokenizer({ input, _ ->
    if (input.next != CH_HASH) return@ExternalTokenizer
    input.advance()
    while (true) {
        if (input.next == CH_NEWLINE || input.next < 0) break
        if ((input.next == CH_PERCENT || input.next == CH_BRACE_R) &&
            input.peek(1) == CH_BRACE_R
        ) {
            break
        }
        input.advance()
    }
    input.acceptToken(INLINE_COMMENT)
})
