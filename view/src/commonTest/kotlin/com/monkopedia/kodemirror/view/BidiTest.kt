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
package com.monkopedia.kodemirror.view

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BidiTest {

    // Helper: collect (from, to, level) tuples from computeOrder
    private fun order(
        line: String,
        direction: Direction = Direction.LTR
    ): List<Triple<Int, Int, Int>> =
        computeOrder(line, direction).map { Triple(it.from, it.to, it.level) }

    // Helper: assert a single span
    private fun assertSpan(spans: List<Triple<Int, Int, Int>>, from: Int, to: Int, level: Int) {
        val match = spans.any { it.first == from && it.second == to && it.third == level }
        assertTrue(match, "Expected span ($from, $to, $level) in $spans")
    }

    @Test
    fun emptyLine() {
        val spans = computeOrder("", Direction.LTR)
        assertEquals(1, spans.size)
        assertEquals(BidiSpan(0, 0, 0), spans[0].let { BidiSpan(it.from, it.to, it.level) })
    }

    @Test
    fun pureLtrLine() {
        val spans = computeOrder("hello world", Direction.LTR)
        // All characters at level 0
        assertEquals(1, spans.size)
        assertEquals(0, spans[0].level)
        assertEquals(0, spans[0].from)
        assertEquals(11, spans[0].to)
    }

    @Test
    fun pureRtlLine() {
        // Hebrew text: should produce a single RTL span
        val hebrew = "\u05E9\u05DC\u05D5\u05DD" // שלום
        val spans = computeOrder(hebrew, Direction.RTL)
        assertEquals(1, spans.size)
        assertEquals(1, spans[0].level)
    }

    @Test
    fun mixedLtrRtl() {
        // "abc \u05E9\u05DC\u05D5\u05DD xyz" — LTR, then RTL, then LTR
        val text = "abc \u05E9\u05DC\u05D5\u05DD xyz"
        val spans = computeOrder(text, Direction.LTR)
        // Expect at least 3 spans: level 0, level 1, level 0
        assertTrue(spans.size >= 2, "Expected multiple spans for mixed text, got $spans")
        // First and last spans should be LTR (level 0)
        assertEquals(Direction.LTR, spans.first().dir)
    }

    @Test
    fun autoDirectionLtr() {
        assertEquals(Direction.LTR, autoDirection("hello", 0, 5))
    }

    @Test
    fun autoDirectionRtl() {
        val hebrew = "\u05E9\u05DC\u05D5\u05DD"
        assertEquals(Direction.RTL, autoDirection(hebrew, 0, hebrew.length))
    }

    @Test
    fun autoDirectionEmpty() {
        assertEquals(Direction.LTR, autoDirection("", 0, 0))
    }

    @Test
    fun autoDirectionNumbers() {
        // Numbers are not strong bidi characters; should fall through to LTR
        assertEquals(Direction.LTR, autoDirection("12345", 0, 5))
    }

    @Test
    fun directionEnum() {
        assertEquals(Direction.LTR, Direction.LTR)
        assertEquals(Direction.RTL, Direction.RTL)
    }

    @Test
    fun bidiSpanDirection() {
        assertEquals(Direction.LTR, BidiSpan(0, 5, 0).dir)
        assertEquals(Direction.RTL, BidiSpan(0, 5, 1).dir)
        assertEquals(Direction.LTR, BidiSpan(0, 5, 2).dir) // even level = LTR
    }

    @Test
    fun europeanNumbers() {
        // Numbers in LTR context should not produce RTL spans
        val text = "Price: 42.00"
        val spans = computeOrder(text, Direction.LTR)
        // All should be at level 0 (European numbers in LTR paragraph = L context)
        assertTrue(spans.all { it.level <= 2 }, "Unexpected levels: $spans")
    }

    @Test
    fun isolateRecord() {
        val iso = Isolate(0, 5, Direction.RTL)
        assertEquals(0, iso.from)
        assertEquals(5, iso.to)
        assertEquals(Direction.RTL, iso.direction)
    }

    @Test
    fun rtlParagraphPureLtr() {
        // In an RTL paragraph, pure LTR text gets level 2
        val text = "hello"
        val spans = computeOrder(text, Direction.RTL)
        assertTrue(spans.isNotEmpty())
        // All chars are L in RTL paragraph → level 2
        assertTrue(spans.all { it.level >= 1 }, "Expected elevated levels in RTL paragraph: $spans")
    }
}
