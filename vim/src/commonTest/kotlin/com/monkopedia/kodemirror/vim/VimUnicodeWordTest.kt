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

/**
 * Word and upper-case classification of non-Latin and astral text (#410).
 *
 * Expected values were measured by driving `@replit/codemirror-vim` 6.4.0 on the JS engine.
 * Upstream tests `[\w\p{Alphabetic}\p{Number}_]` and `^[\p{Lu}]$` (both with the `u` flag), one
 * UTF-16 unit at a time in its word motions, so a lone surrogate is never a word character and an
 * astral character reads as two punctuation units.
 */
class VimUnicodeWordTest {

    private fun motion(doc: String, start: Int, vararg keys: String, expected: Int) =
        testVim(value = doc, cursor = LinePos(0, start)) { h ->
            h.doKeys(*keys)
            h.assertCursorAt(0, expected)
        }

    private fun edit(doc: String, start: Int, vararg keys: String, expected: String) =
        testVim(value = doc, cursor = LinePos(0, start)) { h ->
            h.doKeys(*keys)
            assertEquals(expected, h.cm.getValue())
        }

    @Test
    fun wordCharacterClassMatchesUpstream() {
        for (word in listOf("𝐀", "𠀀", "ि", "ं", "क", "a", "_", "²", "Ⓐ")) {
            assertEquals(true, isWordChar(word), "isWordChar($word)")
        }
        for (other in listOf("😀", "🤔", "\uD835", "\uD83D", "-", " ")) {
            assertEquals(false, isWordChar(other), "isWordChar($other)")
        }
    }

    @Test
    fun upperCaseClassMatchesUpstream() {
        assertEquals(true, isUpperCase("𝐀"), "isUpperCase(U+1D400)")
        assertEquals(true, isUpperCase("É"))
        for (notUpper in listOf("\uD835", "𠀀", "😀", "𝐚", "a", "क")) {
            assertEquals(false, isUpperCase(notUpper), "isUpperCase($notUpper)")
        }
    }

    // "ab 😀😀 cd": the emoji run is one punctuation word.
    @Test fun wOverEmojiRun() = motion("ab 😀😀 cd", 0, "w", "w", expected = 8)

    @Test fun eOverEmojiRun() = motion("ab 😀😀 cd", 0, "e", "e", expected = 7)

    @Test fun bOverEmojiRun() = motion("ab 😀😀 cd", 8, "b", expected = 3)

    @Test fun wStopsAtEmojiBetweenLetters() = motion("a😀b c", 0, "w", expected = 1)

    @Test fun dwBeforeEmojiRun() = edit("ab 😀😀 cd", 0, "d", "w", expected = "😀😀 cd")

    @Test fun diwOnEmojiRun() = edit("ab 😀😀 cd", 0, "w", "d", "i", "w", expected = "ab  cd")

    // "x 𠀀𠀁 y": CJK Ext B is a single word too (two surrogate pairs of punctuation units).
    @Test fun wOverCjkExtB() = motion("x 𠀀𠀁 y", 0, "w", "w", expected = 7)

    @Test fun eOverCjkExtB() = motion("x 𠀀𠀁 y", 0, "e", expected = 6)

    @Test fun bOverCjkExtB() = motion("x 𠀀𠀁 y", 7, "b", expected = 2)

    @Test fun diwOnCjkExtB() = edit("x 𠀀𠀁 y", 2, "d", "i", "w", expected = "x  y")

    // "हिंदी ह": vowel signs and the anusvara are Alphabetic, so the Hindi word is not split.
    @Test fun wOverHindi() = motion("हिंदी ह", 0, "w", expected = 6)

    @Test fun eOverHindi() = motion("हिंदी ह", 0, "e", expected = 4)

    @Test fun bOverHindi() = motion("हिंदी ह", 6, "b", expected = 0)

    @Test fun diwOnHindi() = edit("हिंदी ह", 0, "d", "i", "w", expected = " ह")

    // `*` expands to the next keyword character, skipping the non-word astral units.
    @Test fun starSkipsCjkExtB() = motion("𠀀𠀁 x 𠀀𠀁", 0, "*", expected = 5)

    @Test fun starSkipsEmoji() = motion("😀 x 😀", 0, "*", expected = 3)

    // `~` and visual `~` work one UTF-16 unit at a time, so astral capitals are left alone.
    @Test fun tildeLeavesAstralCapital() = edit("𝐀b", 0, "~", "~", "~", expected = "𝐀B")

    @Test fun visualTildeLeavesAstralCapital() = edit(
        "𐐀b",
        0,
        "v",
        "l",
        "l",
        "~",
        expected = "𐐀B"
    )

    @Test fun gUUpperCasesAstralLetter() = edit("𐐨b", 0, "g", "U", "U", expected = "𐐀B")

    @Test fun gULeavesUncasedAstralLetter() = edit("𝐚b", 0, "g", "U", "U", expected = "𝐚B")
}
