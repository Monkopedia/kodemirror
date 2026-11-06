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

// JavaScript's `\s`, spelled out rather than as a Regex: Java's `\s` is ASCII-only, and the
// Kotlin/Wasm regex engine classifies astral code points by their low 16 bits.
private fun isSpace(ch: Char): Boolean = ch in '\t'..'\r' ||
    ch == ' ' ||
    ch == '\u00a0' ||
    ch == '\u1680' ||
    ch in '\u2000'..'\u200a' ||
    ch == '\u2028' ||
    ch == '\u2029' ||
    ch == '\u202f' ||
    ch == '\u205f' ||
    ch == '\u3000' ||
    ch == '\ufeff'

/**
 * Whether [str] contains a character upstream's `[\p{Alphabetic}\p{Number}_]` matches. Per
 * platform, since no target's Kotlin Regex supports that pattern.
 */
internal expect fun hasWordChar(str: String): Boolean

fun makeCategorizer(wordChars: String): (String) -> CharCategory = { char: String ->
    when {
        char.all(::isSpace) -> CharCategory.Space
        hasWordChar(char) -> CharCategory.Word
        wordChars.any { char.contains(it) } -> CharCategory.Word
        else -> CharCategory.Other
    }
}
