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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.monkopedia.kodemirror.lezer.highlight.tags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HighlightStyleTest {

    @Test
    fun defineCreatesHighlightStyleWithCorrectSpecsSize() {
        val hs = HighlightStyle.define(
            listOf(
                TagStyleSpec(tags.keyword, SpanStyle(color = Color.Red)),
                TagStyleSpec(tags.comment, SpanStyle(color = Color.Gray))
            )
        )
        assertEquals(2, hs.specs.size)
    }

    @Test
    fun styleReturnsClassForMatchingTag() {
        val hs = HighlightStyle.define(
            listOf(
                TagStyleSpec(tags.keyword, SpanStyle(color = Color.Red))
            )
        )
        val cls = hs.style(listOf(tags.keyword))
        assertEquals("hl-0", cls)
    }

    @Test
    fun spanStyleForRoundTrips() {
        val expected = SpanStyle(color = Color.Red)
        val hs = HighlightStyle.define(
            listOf(
                TagStyleSpec(tags.keyword, expected)
            )
        )
        val cls = hs.style(listOf(tags.keyword))
        assertNotNull(cls)
        val result = hs.spanStyleFor(cls)
        assertEquals(expected, result)
    }

    @Test
    fun styleReturnsNullForNonMatchingTag() {
        val hs = HighlightStyle.define(
            listOf(
                TagStyleSpec(tags.keyword, SpanStyle(color = Color.Red))
            )
        )
        // tags.comment was not registered, should return null
        val cls = hs.style(listOf(tags.comment))
        assertNull(cls)
    }

    @Test
    fun spanStyleForReturnsNullForUnknownClass() {
        val hs = HighlightStyle.define(
            listOf(
                TagStyleSpec(tags.keyword, SpanStyle(color = Color.Red))
            )
        )
        assertNull(hs.spanStyleFor("hl-99"))
        assertNull(hs.spanStyleFor("unknown"))
    }

    @Test
    fun multipleSpecsGetCorrectIndices() {
        val hs = HighlightStyle.define(
            listOf(
                TagStyleSpec(tags.keyword, SpanStyle(color = Color.Red)),
                TagStyleSpec(tags.comment, SpanStyle(color = Color.Gray)),
                TagStyleSpec(tags.string, SpanStyle(color = Color.Green))
            )
        )
        assertEquals("hl-0", hs.style(listOf(tags.keyword)))
        assertEquals("hl-1", hs.style(listOf(tags.comment)))
        assertEquals("hl-2", hs.style(listOf(tags.string)))
    }

    @Test
    fun defaultHighlightStyleHasNonEmptySpecs() {
        assertTrue(defaultHighlightStyle.specs.isNotEmpty())
    }

    @Test
    fun oneDarkHighlightStyleHasNonEmptySpecs() {
        assertTrue(oneDarkHighlightStyle.specs.isNotEmpty())
    }
}
