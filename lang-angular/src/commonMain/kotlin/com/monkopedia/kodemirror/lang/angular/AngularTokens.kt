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
package com.monkopedia.kodemirror.lang.angular

import com.monkopedia.kodemirror.lezer.lr.ExternalTokenizer

private const val CH_NEWLINE = 10
private const val CH_BRACE_L = 123
private const val CH_AMPERSAND = 38
private const val CH_SINGLE_QUOTE = 39
private const val CH_DOUBLE_QUOTE = 34

// Term IDs from the parser spec
private const val TEXT = 1
private const val ATTRIBUTE_CONTENT_SINGLE = 33
private const val ATTRIBUTE_CONTENT_DOUBLE = 34
private const val SCRIPT_ATTRIBUTE_CONTENT_SINGLE = 35
private const val SCRIPT_ATTRIBUTE_CONTENT_DOUBLE = 36

internal val text = ExternalTokenizer({ input, _ ->
    val start = input.pos
    while (true) {
        if (input.next == CH_NEWLINE) {
            input.advance()
            break
        } else if (
            (input.next == CH_BRACE_L && input.peek(1) == CH_BRACE_L) ||
            input.next < 0
        ) {
            break
        }
        input.advance()
    }
    if (input.pos > start) {
        input.acceptToken(TEXT)
    }
})

private fun attrContent(quote: Int, token: Int, script: Boolean): ExternalTokenizer =
    ExternalTokenizer({ input, _ ->
        val start = input.pos
        while (
            input.next != quote && input.next >= 0 &&
            (
                script || (
                    input.next != CH_AMPERSAND &&
                        (input.next != CH_BRACE_L || input.peek(1) != CH_BRACE_L)
                    )
                )
        ) {
            input.advance()
        }
        if (input.pos > start) {
            input.acceptToken(token)
        }
    })

internal val attrSingle = attrContent(CH_SINGLE_QUOTE, ATTRIBUTE_CONTENT_SINGLE, false)
internal val attrDouble = attrContent(CH_DOUBLE_QUOTE, ATTRIBUTE_CONTENT_DOUBLE, false)
internal val scriptAttrSingle =
    attrContent(CH_SINGLE_QUOTE, SCRIPT_ATTRIBUTE_CONTENT_SINGLE, true)
internal val scriptAttrDouble =
    attrContent(CH_DOUBLE_QUOTE, SCRIPT_ATTRIBUTE_CONTENT_DOUBLE, true)
