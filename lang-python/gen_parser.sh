#!/bin/bash
# Generate PythonParser.kt from upstream index.js

SRC=/tmp/pkg-lezer-python/dist/index.js
OUT=/home/jmonk/git/kodemirror/lezer-python/src/commonMain/kotlin/com/monkopedia/kodemirror/lezer/python/PythonParser.kt

# Extract data strings and escape $ as \$
STATES=$(grep -oP 'states: "\K[^"]+' "$SRC" | sed 's/\$/\\$/g')
STATEDATA=$(grep -oP 'stateData: "\K[^"]+' "$SRC" | sed 's/\$/\\$/g')
GOTO=$(grep -oP 'goto: "\K[^"]+' "$SRC" | sed 's/\$/\\$/g')
NODENAMES=$(grep -oP 'nodeNames: "\K[^"]+' "$SRC")
TOKENDATA=$(grep -oP 'tokenData: "\K[^"]+' "$SRC" | sed 's/\$/\\$/g')

# Function to split a string into chunks of 1000 chars for Kotlin
split_string() {
    local data="$1"
    local len=${#data}
    local chunk_size=1000
    local first=1
    local i=0
    while [ $i -lt $len ]; do
        local chunk="${data:$i:$chunk_size}"
        if [ $first -eq 1 ]; then
            printf '"%s"' "$chunk"
            first=0
        else
            printf ' +\n        "%s"' "$chunk"
        fi
        i=$((i + chunk_size))
    done
}

cat > "$OUT" << 'HEADER'
/*
 * Copyright 2025 Jason Monk
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
@file:Suppress("ktlint:standard:max-line-length")

package com.monkopedia.kodemirror.lezer.python

import com.monkopedia.kodemirror.lezer.lr.ContextTracker
import com.monkopedia.kodemirror.lezer.lr.ExternalTokenizer
import com.monkopedia.kodemirror.lezer.lr.LRParser
import com.monkopedia.kodemirror.lezer.lr.ParserSpec
import com.monkopedia.kodemirror.lezer.lr.SpecializerSpec

// Term constants from generated grammar
private const val PRINT_KEYWORD = 1
private const val INDENT = 194
private const val DEDENT = 195
private const val NEWLINE_TERM = 196
private const val BLANK_LINE_START = 197
private const val NEWLINE_BRACKETED = 198
private const val EOF_TERM = 199
private const val STRING_CONTENT = 200
private const val ESCAPE = 2
private const val REPLACEMENT_START = 3
private const val STRING_END = 201
private const val PAREN_L = 24
private const val PARENTHESIZED_EXPRESSION = 25
private const val TUPLE_EXPRESSION = 49
private const val COMPREHENSION_EXPRESSION = 50
private const val BRACKET_L = 55
private const val ARRAY_EXPRESSION = 56
private const val ARRAY_COMPREHENSION_EXPRESSION = 57
private const val BRACE_L = 59
private const val DICTIONARY_EXPRESSION = 60
private const val DICTIONARY_COMPREHENSION_EXPRESSION = 61
private const val SET_EXPRESSION = 62
private const val SET_COMPREHENSION_EXPRESSION = 63
private const val ARG_LIST = 65
private const val SUBSCRIPT = 238
private const val STRING_TYPE = 71
private const val STRING_START = 241
private const val STRING_START_D = 242
private const val STRING_START_L = 243
private const val STRING_START_LD = 244
private const val STRING_START_R = 245
private const val STRING_START_RD = 246
private const val STRING_START_RL = 247
private const val STRING_START_RLD = 248
private const val FORMAT_STRING = 72
private const val STRING_START_F = 249
private const val STRING_START_FD = 250
private const val STRING_START_FL = 251
private const val STRING_START_FLD = 252
private const val STRING_START_FR = 253
private const val STRING_START_FRD = 254
private const val STRING_START_FRL = 255
private const val STRING_START_FRLD = 256
private const val FORMAT_REPLACEMENT = 73
private const val NESTED_FORMAT_REPLACEMENT = 77
private const val IMPORT_LIST = 263
private const val TYPE_PARAM_LIST = 112
private const val PARAM_LIST = 130
private const val SEQUENCE_PATTERN = 151
private const val MAPPING_PATTERN = 152
private const val PATTERN_ARG_LIST = 155

// Character constants
private const val NEWLINE = 10
private const val CARRIAGE_RETURN = 13
private const val SPACE = 32
private const val TAB = 9
private const val HASH = 35
private const val PAREN_OPEN = 40
private const val DOT = 46
private const val BRACE_OPEN = 123
private const val BRACE_CLOSE = 125
private const val SINGLE_QUOTE = 39
private const val DOUBLE_QUOTE = 34
private const val BACKSLASH = 92
private const val LETTER_O = 111
private const val LETTER_X = 120
private const val LETTER_N_UPPER = 78
private const val LETTER_U = 117
private const val LETTER_U_UPPER = 85

private val bracketed = setOf(
    PARENTHESIZED_EXPRESSION, TUPLE_EXPRESSION, COMPREHENSION_EXPRESSION, IMPORT_LIST, ARG_LIST,
    PARAM_LIST, ARRAY_EXPRESSION, ARRAY_COMPREHENSION_EXPRESSION, SUBSCRIPT,
    SET_EXPRESSION, SET_COMPREHENSION_EXPRESSION, FORMAT_STRING, FORMAT_REPLACEMENT,
    NESTED_FORMAT_REPLACEMENT, DICTIONARY_EXPRESSION, DICTIONARY_COMPREHENSION_EXPRESSION,
    SEQUENCE_PATTERN, MAPPING_PATTERN, PATTERN_ARG_LIST, TYPE_PARAM_LIST
)

private fun isLineBreak(ch: Int): Boolean = ch == NEWLINE || ch == CARRIAGE_RETURN

private fun isHex(ch: Int): Boolean = ch in 48..57 || ch in 65..70 || ch in 97..102

// Flags used in Context objects
private const val CX_BRACKETED = 1
private const val CX_STRING = 2
private const val CX_DOUBLE_QUOTE = 4
private const val CX_LONG = 8
private const val CX_RAW = 16
private const val CX_FORMAT = 32

private class Context(val parent: Context?, val indent: Int, val flags: Int) {
    val hash: Int = ((parent?.let { it.hash + (it.hash shl 8) } ?: 0) + indent + (indent shl 4) + flags + (flags shl 6))
}

private val topIndent = Context(null, 0, 0)

private fun countIndent(space: String): Int {
    var depth = 0
    for (i in space.indices) {
        depth += if (space[i].code == TAB) 8 - (depth % 8) else 1
    }
    return depth
}

private val stringFlags = mapOf(
    STRING_START to (0 or CX_STRING),
    STRING_START_D to (CX_DOUBLE_QUOTE or CX_STRING),
    STRING_START_L to (CX_LONG or CX_STRING),
    STRING_START_LD to (CX_LONG or CX_DOUBLE_QUOTE or CX_STRING),
    STRING_START_R to (CX_RAW or CX_STRING),
    STRING_START_RD to (CX_RAW or CX_DOUBLE_QUOTE or CX_STRING),
    STRING_START_RL to (CX_RAW or CX_LONG or CX_STRING),
    STRING_START_RLD to (CX_RAW or CX_LONG or CX_DOUBLE_QUOTE or CX_STRING),
    STRING_START_F to (CX_FORMAT or CX_STRING),
    STRING_START_FD to (CX_FORMAT or CX_DOUBLE_QUOTE or CX_STRING),
    STRING_START_FL to (CX_FORMAT or CX_LONG or CX_STRING),
    STRING_START_FLD to (CX_FORMAT or CX_LONG or CX_DOUBLE_QUOTE or CX_STRING),
    STRING_START_FR to (CX_FORMAT or CX_RAW or CX_STRING),
    STRING_START_FRD to (CX_FORMAT or CX_RAW or CX_DOUBLE_QUOTE or CX_STRING),
    STRING_START_FRL to (CX_FORMAT or CX_RAW or CX_LONG or CX_STRING),
    STRING_START_FRLD to (CX_FORMAT or CX_RAW or CX_LONG or CX_DOUBLE_QUOTE or CX_STRING)
)

private val newlines = ExternalTokenizer(
    { input, stack ->
        @Suppress("UNCHECKED_CAST")
        val context = stack.context as Context
        val prev: Int
        if (input.next < 0) {
            input.acceptToken(EOF_TERM)
        } else if ((context.flags and CX_BRACKETED) != 0) {
            if (isLineBreak(input.next)) input.acceptToken(NEWLINE_BRACKETED, 1)
        } else if (((input.peek(-1).also { prev = it }) < 0 || isLineBreak(prev)) &&
            stack.canShift(BLANK_LINE_START)
        ) {
            var spaces = 0
            while (input.next == SPACE || input.next == TAB) { input.advance(); spaces++ }
            if (input.next == NEWLINE || input.next == CARRIAGE_RETURN || input.next == HASH) {
                input.acceptToken(BLANK_LINE_START, -spaces)
            }
        } else if (isLineBreak(input.next)) {
            input.acceptToken(NEWLINE_TERM, 1)
        }
    },
    contextual = true
)

private val indentation = ExternalTokenizer({ input, stack ->
    @Suppress("UNCHECKED_CAST")
    val context = stack.context as Context
    if (context.flags != 0) return@ExternalTokenizer
    val prev = input.peek(-1)
    if (prev == NEWLINE || prev == CARRIAGE_RETURN) {
        var depth = 0
        var chars = 0
        while (true) {
            if (input.next == SPACE) {
                depth++
            } else if (input.next == TAB) {
                depth += 8 - (depth % 8)
            } else {
                break
            }
            input.advance()
            chars++
        }
        if (depth != context.indent &&
            input.next != NEWLINE && input.next != CARRIAGE_RETURN && input.next != HASH
        ) {
            if (depth < context.indent) {
                input.acceptToken(DEDENT, -chars)
            } else {
                input.acceptToken(INDENT)
            }
        }
    }
})

@Suppress("UNCHECKED_CAST")
private val trackIndent = ContextTracker(
    start = topIndent as Any?,
    reduce = { context, term, _, _ ->
        val ctx = context as Context
        if ((ctx.flags and CX_BRACKETED) != 0 && bracketed.contains(term) ||
            (term == STRING_TYPE || term == FORMAT_STRING) && (ctx.flags and CX_STRING) != 0
        ) {
            ctx.parent
        } else {
            ctx
        }
    },
    shift = { context, term, stack, input ->
        val ctx = context as Context
        if (term == INDENT) {
            Context(ctx, countIndent(input.read(input.pos, stack.pos)), 0)
        } else if (term == DEDENT) {
            ctx.parent
        } else if (term == PAREN_L || term == BRACKET_L || term == BRACE_L || term == REPLACEMENT_START) {
            Context(ctx, 0, CX_BRACKETED)
        } else if (stringFlags.containsKey(term)) {
            Context(ctx, 0, stringFlags[term]!! or (ctx.flags and CX_BRACKETED))
        } else {
            ctx
        }
    },
    hash = { context -> (context as Context).hash }
) as ContextTracker<Any?>

private val legacyPrint = ExternalTokenizer({ input, _ ->
    for (i in 0 until 5) {
        if (input.next != "print".codePointAt(i)) return@ExternalTokenizer
        input.advance()
    }
    if (input.next >= 0 && Regex("\\w").containsMatchIn(input.next.toChar().toString())) {
        return@ExternalTokenizer
    }
    var off = 0
    while (true) {
        val next = input.peek(off)
        if (next == SPACE || next == TAB) {
            off++
            continue
        }
        if (next != PAREN_OPEN && next != DOT && next != NEWLINE && next != CARRIAGE_RETURN && next != HASH) {
            input.acceptToken(PRINT_KEYWORD)
        }
        return@ExternalTokenizer
    }
})

private fun skipEscape(input: com.monkopedia.kodemirror.lezer.lr.InputStream, ch: Int) {
    if (ch == LETTER_O) {
        for (i in 0 until 2) { if (input.next in 48..55) input.advance() else break }
    } else if (ch == LETTER_X) {
        for (i in 0 until 2) { if (isHex(input.next)) input.advance() else break }
    } else if (ch == LETTER_U) {
        for (i in 0 until 4) { if (isHex(input.next)) input.advance() else break }
    } else if (ch == LETTER_U_UPPER) {
        for (i in 0 until 8) { if (isHex(input.next)) input.advance() else break }
    } else if (ch == LETTER_N_UPPER) {
        if (input.next == BRACE_OPEN) {
            input.advance()
            while (input.next >= 0 && input.next != BRACE_CLOSE && input.next != SINGLE_QUOTE &&
                input.next != DOUBLE_QUOTE && input.next != NEWLINE
            ) {
                input.advance()
            }
            if (input.next == BRACE_CLOSE) input.advance()
        }
    }
}

private val strings = ExternalTokenizer({ input, stack ->
    @Suppress("UNCHECKED_CAST")
    val context = stack.context as Context
    val flags = context.flags
    val quote = if ((flags and CX_DOUBLE_QUOTE) != 0) DOUBLE_QUOTE else SINGLE_QUOTE
    val long = (flags and CX_LONG) > 0
    val escapes = (flags and CX_RAW) == 0
    val format = (flags and CX_FORMAT) > 0

    val start = input.pos
    while (true) {
        if (input.next < 0) {
            break
        } else if (format && input.next == BRACE_OPEN) {
            if (input.peek(1) == BRACE_OPEN) {
                input.advance(2)
            } else {
                if (input.pos == start) {
                    input.acceptToken(REPLACEMENT_START, 1)
                    return@ExternalTokenizer
                }
                break
            }
        } else if (escapes && input.next == BACKSLASH) {
            if (input.pos == start) {
                input.advance()
                val escaped = input.next
                if (escaped >= 0) {
                    input.advance()
                    skipEscape(input, escaped)
                }
                input.acceptToken(ESCAPE)
                return@ExternalTokenizer
            }
            break
        } else if (input.next == BACKSLASH && !escapes && input.peek(1) > -1) {
            // Raw strings still ignore escaped quotes
            input.advance(2)
        } else if (input.next == quote && (!long || (input.peek(1) == quote && input.peek(2) == quote))) {
            if (input.pos == start) {
                input.acceptToken(STRING_END, if (long) 3 else 1)
                return@ExternalTokenizer
            }
            break
        } else if (input.next == NEWLINE) {
            if (long) {
                input.advance()
            } else if (input.pos == start) {
                input.acceptToken(STRING_END)
                return@ExternalTokenizer
            } else {
                break
            }
        } else {
            input.advance()
        }
    }
    if (input.pos > start) input.acceptToken(STRING_CONTENT)
})

// Specializer map for Python keywords
private val specIdentifier = mapOf(
    "await" to 44, "or" to 54, "and" to 56, "in" to 60, "not" to 62, "is" to 64,
    "if" to 70, "else" to 72, "lambda" to 76, "yield" to 94, "from" to 96,
    "async" to 102, "for" to 104, "None" to 162, "True" to 164, "False" to 164,
    "del" to 178, "pass" to 182, "break" to 186, "continue" to 190, "return" to 194,
    "raise" to 202, "import" to 206, "as" to 208, "global" to 212, "nonlocal" to 214,
    "assert" to 218, "type" to 223, "elif" to 236, "while" to 240, "try" to 246,
    "except" to 248, "finally" to 250, "with" to 254, "def" to 258, "class" to 268,
    "match" to 279, "case" to 285
)

HEADER

# Now append the parser data
echo "" >> "$OUT"
echo "val parser: LRParser = LRParser.deserialize(" >> "$OUT"
echo "    ParserSpec(" >> "$OUT"
echo "        version = 14," >> "$OUT"

# States
printf '        states = ' >> "$OUT"
split_string "$STATES" >> "$OUT"
echo "," >> "$OUT"

# stateData
printf '        stateData = ' >> "$OUT"
split_string "$STATEDATA" >> "$OUT"
echo "," >> "$OUT"

# goto
printf '        goto = ' >> "$OUT"
split_string "$GOTO" >> "$OUT"
echo "," >> "$OUT"

# nodeNames
printf '        nodeNames = ' >> "$OUT"
split_string "$NODENAMES" >> "$OUT"
echo "," >> "$OUT"

# rest of the spec
cat >> "$OUT" << 'FOOTER'
        maxTerm = 277,
        context = trackIndent,
        nodeProps = listOf(
            listOf("isolate", -5, 4, 71, 72, 73, 77, ""),
            listOf(
                "group", -15, 6, 85, 87, 88, 90, 92, 94, 96, 98, 99, 100, 102, 105, 108, 110,
                "Statement Statement", -22, 8, 18, 21, 25, 40, 49, 50, 56, 57, 60, 61, 62, 63,
                64, 67, 70, 71, 72, 79, 80, 81, 82, "Expression", -10, 114, 116, 119, 121, 122,
                126, 128, 133, 135, 138, "Statement", -9, 143, 144, 147, 148, 150, 151, 152, 153,
                154, "Pattern"
            ),
            listOf("openedBy", 23, "(", 54, "[", 58, "{"),
            listOf("closedBy", 24, ")", 55, "]", 59, "}")
        ),
        propSources = listOf(pythonHighlighting),
        skippedNodes = listOf(0, 4),
        repeatNodeCount = 34,
FOOTER

# tokenData
printf '        tokenData = ' >> "$OUT"
split_string "$TOKENDATA" >> "$OUT"
echo "," >> "$OUT"

cat >> "$OUT" << 'FOOTER2'
        tokenizers = listOf(legacyPrint, indentation, newlines, strings, 0, 1, 2, 3, 4),
        topRules = mapOf("Script" to listOf(0, 5)),
        specialized = listOf(
            SpecializerSpec(
                term = 221,
                get = { value, _ -> specIdentifier[value] ?: -1 }
            )
        ),
        tokenPrec = 7668
    )
)
FOOTER2

echo "Generated $OUT successfully"
