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
package com.monkopedia.kodemirror.lezer.markdown

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Compiles every `Regex("...")` literal in this module's `commonMain` sources (#447).
 *
 * These patterns are transcribed from JavaScript, which reads an unescaped `[` inside a
 * character class as a literal. The JVM reads it as a nested class: `[([)]` throws, and a pattern
 * that does compile means something different from upstream. So each literal must compile here
 * and must escape any `[` inside a class.
 */
class RegexLiteralTest {

    private val regexCall = Regex("""Regex\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)""")
    private val stringLiteral = Regex(""""((?:[^"\\]|\\.)*)"""")
    private val kotlinEscape = Regex("""\\(u[0-9a-fA-F]{4}|.)""")

    private fun decode(body: String): String {
        if (Regex("""(?<!\\)\$[{\w]""").containsMatchIn(body)) {
            fail("String template in a Regex literal; hoist it so this test can check it: $body")
        }
        return kotlinEscape.replace(body) {
            val e = it.groupValues[1]
            when (e[0]) {
                'u' -> e.substring(1).toInt(16).toChar().toString()
                'n' -> "\n"
                't' -> "\t"
                'r' -> "\r"
                'b' -> "\b"
                else -> e
            }
        }
    }

    /** Index of the first unescaped `[` inside a character class, or -1. */
    private fun nestedBracket(pattern: String): Int {
        var inClass = false
        var i = 0
        while (i < pattern.length) {
            when (pattern[i]) {
                '\\' -> i++
                '[' -> if (inClass) return i else inClass = true
                ']' -> inClass = false
            }
            i++
        }
        return -1
    }

    private fun patterns(): List<Pair<String, String>> =
        File("src/commonMain/kotlin").walk().filter { it.extension == "kt" }.flatMap { file ->
            regexCall.findAll(file.readText()).map { call ->
                val pattern = stringLiteral.findAll(call.groupValues[1])
                    .joinToString("") { decode(it.groupValues[1]) }
                file.name to pattern
            }
        }.toList()

    @Test
    fun everyRegexLiteralCompilesWithoutNestedBrackets() {
        val patterns = patterns()
        assertTrue(
            patterns.any { it.second.startsWith("^(?:#\\d+|#x[a-fA-F\\d]+|\\w+);") },
            "Scan found no known pattern; it is not reading the sources (${patterns.size} found)"
        )
        val failures = patterns.mapNotNull { (file, pattern) ->
            val error = runCatching { Regex(pattern) }.exceptionOrNull()
            val nested = nestedBracket(pattern)
            when {
                error != null -> "$file: $pattern -> ${error.message?.lineSequence()?.first()}"
                nested >= 0 -> "$file: $pattern -> unescaped '[' in a class at $nested"
                else -> null
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
