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
package com.monkopedia.kodemirror.state

/**
 * The categories produced by a [character categorizer][EditorState.charCategorizer].
 * These are used to do things like selecting by word.
 */
enum class CharCategory {
    /** Word characters. */
    Word,

    /** Whitespace. */
    Space,

    /** Anything else. */
    Other
}

private val nonASCIISingleCaseWordChar = Regex(
    "[\\u00df\\u0587\\u0590-\\u05f4\\u0600-\\u06ff\\u3040-\\u309f\\u30a0-\\u30ff" +
        "\\u3400-\\u4db5\\u4e00-\\u9fcc\\uac00-\\ud7af]"
)

private val wordChar: Regex? = try {
    Regex("[\\p{Alphabetic}\\p{Number}_]")
} catch (_: Exception) {
    null
}

internal fun hasWordChar(str: String): Boolean {
    wordChar?.let { return it.containsMatchIn(str) }
    for (element in str) {
        val ch = element
        if (
            Regex("\\w").matches(ch.toString()) ||
            ch > '\u0080' &&
            (
                ch.uppercaseChar() != ch.lowercaseChar() ||
                    nonASCIISingleCaseWordChar.matches(ch.toString())
                )
        ) {
            return true
        }
    }
    return false
}

fun makeCategorizer(wordChars: String): (String) -> CharCategory {
    return { char: String ->
        when {
            !Regex("\\S").containsMatchIn(char) -> CharCategory.Space
            hasWordChar(char) -> CharCategory.Word
            wordChars.any { char.contains(it) } -> CharCategory.Word
            else -> CharCategory.Other
        }
    }
}
