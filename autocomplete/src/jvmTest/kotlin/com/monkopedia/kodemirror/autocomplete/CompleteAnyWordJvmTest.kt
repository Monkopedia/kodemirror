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
package com.monkopedia.kodemirror.autocomplete

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Word boundaries that follow upstream's `\p{Alphabetic}` rather than "any mark". JVM only: Apple
 * native counts every mark as a word character. Expected values from @codemirror/autocomplete
 * 6.20.1.
 */
class CompleteAnyWordJvmTest {

    private fun complete(doc: String): CompletionResult {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.EditorSelectionSpec(
                    EditorSelection.single(DocPos(doc.length))
                )
            )
        )
        return assertNotNull(completeAnyWord(CompletionContext(state, DocPos(doc.length), false)))
    }

    @Test
    fun nonAlphabeticCombiningMarksEndAWord() {
        // U+0301 and U+0308 are not Alphabetic, U+0345 is.
        val result = complete("café naïve xͅy ca")
        assertEquals(listOf("cafe", "nai", "ve", "xͅy"), result.options.map { it.label })
    }

    @Test
    fun viramasEndAWord() {
        assertEquals(
            listOf("हिंदी", "नमस", "ते"),
            complete("हिंदी नमस्ते ह").options.map { it.label }
        )
        assertEquals(
            listOf("தமிழ", "வணக", "கம"),
            complete("தமிழ் வணக்கம் த").options.map { it.label }
        )
    }

    @Test
    fun circledLettersAreWordCharacters() {
        val result = complete("ⒶⒷ 🄰🄱 Ⓐ")
        assertEquals(DocPos(8), result.from)
        assertEquals(listOf("ⒶⒷ", "🄰🄱"), result.options.map { it.label })
    }
}
