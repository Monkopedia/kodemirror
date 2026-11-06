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

import kotlin.test.Test
import kotlin.test.assertEquals

class CharCategoryTest {

    private fun mk(vararg extensions: Extension): EditorState = EditorState.create(
        EditorStateConfig(
            extensions = if (extensions.isEmpty()) {
                null
            } else {
                ExtensionList(extensions.toList())
            }
        )
    )

    @Test
    fun categorisesIntoAlphanumeric() {
        val st = mk()
        assertEquals(CharCategory.Word, st.charCategorizer(DocPos.ZERO)("1"))
        assertEquals(CharCategory.Word, st.charCategorizer(DocPos.ZERO)("a"))
    }

    @Test
    fun categorisesIntoWhitespace() {
        val st = mk()
        assertEquals(CharCategory.Space, st.charCategorizer(DocPos.ZERO)(" "))
    }

    @Test
    fun categorisesIntoOther() {
        val st = mk()
        assertEquals(CharCategory.Other, st.charCategorizer(DocPos.ZERO)("/"))
        assertEquals(CharCategory.Other, st.charCategorizer(DocPos.ZERO)("<"))
    }

    // Expected values from @codemirror/state 6.5.4 (#384), on every platform.
    @Test
    fun categorisesNonLatinLettersAsWord() {
        val cat = mk().charCategorizer(DocPos.ZERO)
        for (ch in listOf(
            "\u0915", // Devanagari KA
            "\u0902", // Devanagari anusvara
            "\u093f", // Devanagari vowel sign I
            "\u0e01", // Thai KO KAI
            "\u0ba4", // Tamil TA
            "\u00e9",
            "\u4e00",
            "\ud835\udc00", // astral: mathematical bold A
            "\ud840\udc00", // astral: CJK extension B
            "\ud80c\udc00", // astral: Egyptian hieroglyph A001
            "\ud835\udfce", // astral: mathematical bold digit zero
            "\ud800\udc20", // astral: Linear B syllable, low 16 bits are a space
            "\u00b2",
            "\u2160",
            "\u24b6", // circled A: Alphabetic but category So
            "_",
            "7"
        )) {
            assertEquals(CharCategory.Word, cat(ch), "category of $ch")
        }
        for (ch in listOf("\ud83d\ude00", "\ud83e\udd14", "/")) {
            assertEquals(CharCategory.Other, cat(ch), "category of $ch")
        }
    }

    @Test
    fun categorisesUnicodeSpacesAsSpace() {
        val cat = mk().charCategorizer(DocPos.ZERO)
        for (ch in listOf(
            "\t", "\u000b", "\u00a0", "\u1680", "\u2003", "\u2028", "\u2029", "\u202f",
            "\u205f", "\u3000", "\ufeff"
        )) {
            assertEquals(CharCategory.Space, cat(ch), "category of U+${ch[0].code.toString(16)}")
        }
        assertEquals(CharCategory.Other, cat("\u200b"))
        assertEquals(CharCategory.Other, cat("\u0085"))
    }

    @Test
    fun wordAtSpansNonLatinWords() {
        fun wordAt(doc: String, pos: Int): String? =
            EditorState.create(EditorStateConfig(doc = doc.asDoc()))
                .wordAt(DocPos(pos))?.let { "${it.from.value}-${it.to.value}" }
        assertEquals("2-7", wordAt("x \u0915\u093f\u0924\u093e\u092c y", 4))
        assertEquals("0-5", wordAt("\u0939\u093f\u0902\u0926\u0940 \u0939", 2))
        assertEquals("0-7", wordAt("\u0e20\u0e32\u0e29\u0e32\u0e44\u0e17\u0e22 \u0e20\u0e32", 3))
        assertEquals("0-5", wordAt("\u0ba4\u0bae\u0bbf\u0bb4\u0bcd \u0ba4", 2))
        assertEquals("0-2", wordAt("ab\u00a0cd", 1))
        assertEquals("3-5", wordAt("ab\u3000cd", 4))
    }
}
