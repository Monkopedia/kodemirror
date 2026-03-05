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
package com.monkopedia.kodemirror.language

/**
 * Counts the column offset in a string, taking tabs into account.
 */
internal fun countCol(
    string: String,
    end: Int?,
    tabSize: Int,
    startIndex: Int = 0,
    startValue: Int = 0
): Int {
    val effectiveEnd = end ?: run {
        val idx = string.indexOfFirst { it != ' ' && it != '\t' && it != '\u00a0' }
        if (idx == -1) string.length else idx
    }
    var n = startValue
    for (i in startIndex until effectiveEnd) {
        if (string[i] == '\t') {
            n += tabSize - (n % tabSize)
        } else {
            n++
        }
    }
    return n
}

/**
 * Encapsulates a single line of input. Given to stream syntax code,
 * which uses it to tokenize the content.
 */
class StringStream(
    /** The line content. */
    val string: String,
    private val tabSize: Int,
    /** The current indent unit size. */
    val indentUnit: Int,
    private val overrideIndent: Int? = null
) {
    /** The current position on the line. */
    var pos: Int = 0

    /** The start position of the current token. */
    var start: Int = 0

    private var lastColumnPos: Int = 0
    private var lastColumnValue: Int = 0

    /** True if we are at the end of the line. */
    fun eol(): Boolean = pos >= string.length

    /** True if we are at the start of the line. */
    fun sol(): Boolean = pos == 0

    /** Get the next character without advancing, or null at end of line. */
    fun peek(): String? {
        if (pos >= string.length) return null
        return string[pos].toString()
    }

    /** Read the next character and advance [pos], or return null at end. */
    fun next(): String? {
        if (pos < string.length) {
            return string[pos++].toString()
        }
        return null
    }

    /** Consume and return the next character if it equals [ch]. */
    fun eat(ch: String): String? {
        if (pos >= string.length) return null
        val c = string[pos].toString()
        return if (c == ch) {
            pos++
            c
        } else {
            null
        }
    }

    /** Consume and return the next character if it matches [pattern]. */
    fun eat(pattern: Regex): String? {
        if (pos >= string.length) return null
        val c = string[pos].toString()
        return if (pattern.containsMatchIn(c)) {
            pos++
            c
        } else {
            null
        }
    }

    /** Consume and return the next character if [predicate] returns true. */
    fun eat(predicate: (String) -> Boolean): String? {
        if (pos >= string.length) return null
        val c = string[pos].toString()
        return if (predicate(c)) {
            pos++
            c
        } else {
            null
        }
    }

    /**
     * Continue matching characters that match the given string.
     * Return true if any characters were consumed.
     */
    fun eatWhile(ch: String): Boolean {
        val s = pos
        while (eat(ch) != null) { /* consume */ }
        return pos > s
    }

    /**
     * Continue matching characters that match the given regex.
     * Return true if any characters were consumed.
     */
    fun eatWhile(pattern: Regex): Boolean {
        val s = pos
        while (eat(pattern) != null) { /* consume */ }
        return pos > s
    }

    /**
     * Continue matching characters for which the predicate returns true.
     * Return true if any characters were consumed.
     */
    fun eatWhile(predicate: (String) -> Boolean): Boolean {
        val s = pos
        while (eat(predicate) != null) { /* consume */ }
        return pos > s
    }

    /** Consume whitespace ahead of [pos]. Return true if any was found. */
    fun eatSpace(): Boolean {
        val s = pos
        while (pos < string.length) {
            val ch = string[pos]
            if (ch == ' ' || ch == '\t' || ch == '\u00a0') {
                pos++
            } else {
                break
            }
        }
        return pos > s
    }

    /** Move to the end of the line. */
    fun skipToEnd() {
        pos = string.length
    }

    /**
     * Move to directly before the given character, if found on the
     * current line. Returns true if found.
     */
    fun skipTo(ch: String): Boolean {
        val found = string.indexOf(ch, pos)
        if (found > -1) {
            pos = found
            return true
        }
        return false
    }

    /** Move back [n] characters. */
    fun backUp(n: Int) {
        pos -= n
    }

    /** Get the column position at [start]. */
    fun column(): Int {
        if (lastColumnPos < start) {
            lastColumnValue = countCol(
                string, start, tabSize, lastColumnPos, lastColumnValue
            )
            lastColumnPos = start
        }
        return lastColumnValue
    }

    /** Get the indentation column of the current line. */
    fun indentation(): Int {
        return overrideIndent ?: countCol(string, null, tabSize)
    }

    /**
     * Match the input against the given string.
     * If [consume] is true (default), advance past the matched text.
     * When [caseInsensitive] is true, the match is case-insensitive.
     */
    fun match(pattern: String, consume: Boolean = true, caseInsensitive: Boolean = false): Boolean {
        if (pos + pattern.length > string.length) return false
        val substr = string.substring(pos, pos + pattern.length)
        val matches = if (caseInsensitive) {
            substr.equals(pattern, ignoreCase = true)
        } else {
            substr == pattern
        }
        if (matches) {
            if (consume) pos += pattern.length
            return true
        }
        return false
    }

    /**
     * Match the input against the given regex (which should start with ^).
     * If [consume] is true (default), advance past the matched text.
     * Returns the match result if it matches at the current position, or null.
     */
    fun match(pattern: Regex, consume: Boolean = true): kotlin.text.MatchResult? {
        val remainder = string.substring(pos)
        val m = pattern.find(remainder) ?: return null
        if (m.range.first > 0) return null
        if (consume) pos += m.value.length
        return m
    }

    /** Get the current token string. */
    fun current(): String = string.substring(start, pos)
}
