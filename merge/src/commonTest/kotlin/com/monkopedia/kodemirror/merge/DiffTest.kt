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
package com.monkopedia.kodemirror.merge

import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiffTest {

    private fun apply(d: List<Change>, orig: String, changed: String): String {
        var pos = 0
        val result = StringBuilder()
        for (ch in d) {
            result.append(orig, pos, ch.fromA)
            result.append(changed, ch.fromB, ch.toB)
            pos = ch.toA
        }
        result.append(orig, pos, orig.length)
        return result.toString()
    }

    private fun checkShape(d: List<Change>, shape: String) {
        var posA = 0
        var posB = 0
        val expected = mutableListOf<Change>()
        for (part in shape.split(" ")) {
            val m = Regex("(\\d+)/(\\d+)").matchEntire(part)
            if (m != null) {
                val toA = posA + m.groupValues[1].toInt()
                val toB = posB + m.groupValues[2].toInt()
                expected.add(Change(posA, toA, posB, toB))
                posA = toA
                posB = toB
            } else {
                val len = part.toInt()
                posA += len
                posB += len
            }
        }
        assertEquals(expected, d)
    }

    @Test
    fun producesCloseToMinimalDiffs() {
        val rng = Random(42)
        for (i in 0 until 1000) {
            val len = ceil(sqrt(i.toDouble())).toInt() * 5 + 5
            val chars = "  abcdefghij"
            val sb = StringBuilder()
            for (j in 0 until len) sb.append(chars[rng.nextInt(chars.length)])
            val str = sb.toString()
            val changed = StringBuilder()
            var skipped = 0
            var inserted = 0
            var pos = 0
            while (pos < len) {
                val skip = rng.nextInt(10) + 1
                val actual = minOf(skip, len - pos)
                skipped += actual
                changed.append(str, pos, pos + actual)
                pos += skip
                if (pos >= len) break
                val insert = rng.nextInt(5)
                inserted += insert
                repeat(insert) { changed.append('X') }
                pos += rng.nextInt(5)
            }
            val changedStr = changed.toString()
            val d = diff(str, changedStr)
            val dSkipped = len - d.sumOf { it.toA - it.fromA }
            val dInserted = d.sumOf { it.toB - it.fromB }
            val margin = (len / 10.0).roundToInt()
            if (dSkipped < skipped - margin || dInserted > inserted + margin) {
                assertEquals(skipped, dSkipped, "For $str -> $changedStr")
                assertEquals(inserted, dInserted, "For $str -> $changedStr")
            }
            assertEquals(changedStr, apply(d, str, changedStr))
        }
    }

    @Test
    fun doesNotCutInTheMiddleOfSurrogatePairs() {
        val cases = listOf(
            Triple("\uD83D\uDC36", "\uD83D\uDC2F", "2/2"),
            Triple("\uD83D\uDC68\uD83C\uDFFD", "\uD83D\uDC69\uD83C\uDFFD", "2/2 2"),
            Triple("\uD83D\uDC69\uD83C\uDFFC", "\uD83D\uDC69\uD83C\uDFFD", "2 2/2"),
            Triple("\uD83C\uDF4F\uD83C\uDF4E", "\uD83C\uDF4E", "2/0 2"),
            Triple("\uD83C\uDF4E", "\uD83C\uDF4F\uD83C\uDF4E", "0/2 2"),
            Triple("x\uD83C\uDF4E", "x\uD83C\uDF4F\uD83C\uDF4E", "1 0/2 2"),
            Triple("\uD83C\uDF4Ex", "\uD83C\uDF4F\uD83C\uDF4Ex", "0/2 3")
        )
        for ((a, b, shape) in cases) {
            val d = diff(a, b)
            checkShape(d, shape)
            assertEquals(b, apply(d, a, b))
        }
    }

    @Test
    fun handlesRandomInput() {
        val alphabet = "AAACGTT"
        val rng = Random(123)
        fun word(): String {
            val sb = StringBuilder()
            for (l in 0 until 100) sb.append(alphabet[rng.nextInt(alphabet.length)])
            return sb.toString()
        }
        for (i in 0..1000) {
            val a = word()
            val b = word()
            val d = diff(a, b)
            assertEquals(b, apply(d, a, b))
        }
    }

    @Test
    fun canLimitScanDepth() {
        val t0 = currentTimeMillis()
        diff("a".repeat(10000), "b".repeat(10000), DiffConfig(scanLimit = 500))
        assertTrue(currentTimeMillis() < t0 + 2000)
    }

    @Test
    fun canTimeOutDiffs() {
        val t0 = currentTimeMillis()
        diff("a".repeat(10000), "b".repeat(10000), DiffConfig(timeout = 50))
        assertTrue(currentTimeMillis() < t0 + 2000)
    }
}

class PresentableDiffTest {

    private fun apply(d: List<Change>, orig: String, changed: String): String {
        var pos = 0
        val result = StringBuilder()
        for (ch in d) {
            result.append(orig, pos, ch.fromA)
            result.append(changed, ch.fromB, ch.toB)
            pos = ch.toA
        }
        result.append(orig, pos, orig.length)
        return result.toString()
    }

    private fun parseDiff(d: String): Pair<String, String> {
        val pattern = Regex("\\[(.*?)/(.*?)]")
        val a = pattern.replace(d) { it.groupValues[1] }
        val b = pattern.replace(d) { it.groupValues[2] }
        return Pair(a, b)
    }

    private fun serializeDiff(d: List<Change>, a: String, b: String): String {
        var posA = 0
        val result = StringBuilder()
        for (ch in d) {
            result.append(a, posA, ch.fromA)
            result.append("[")
            result.append(a, ch.fromA, ch.toA)
            result.append("/")
            result.append(b, ch.fromB, ch.toB)
            result.append("]")
            posA = ch.toA
        }
        result.append(a, posA, a.length)
        return result.toString()
    }

    private fun test(name: String, diffStr: String) {
        val (a, b) = parseDiff(diffStr)
        val result = presentableDiff(a, b)
        assertEquals(diffStr, serializeDiff(result, a, b), name)
        assertEquals(b, apply(result, a, b), "$name (apply)")
    }

    @Test
    fun growsChangesToWordStart() = test("grows changes to word start", "one [two/twi] three")

    @Test
    fun growsChangesToWordEnd() = test("grows changes to word end", "one [iwo/two] three")

    @Test
    fun growsChangesFromBothSides() = test("grows changes from both sides", "[drop/drip]")

    @Test
    fun doesNotGrowShortInsertions() = test("doesn't grow short insertions", "blo[/o]p")

    @Test
    fun doesNotGrowShortDeletions() = test("doesn't grow short deletions", "blo[o/]p")

    @Test
    fun doesGrowLongInsertions() = test("does grow long insertions", "[oaks/oaktrees]")

    @Test
    fun doesGrowLongDeletions() = test("does grow long deletions", "[oaktrees/oaks]")

    @Test
    fun coversWordsThatContainOtherChanges() =
        test("covers words that contain other changes", "[Threepwood/three]")

    @Test
    fun alignsToTheEndOfWords() = test("aligns to the end of words", "fromA[/ + offA]")

    @Test
    fun alignsToTheStartOfWords() = test("aligns to the start of words", "[offA + /]fromA")

    @Test
    fun removesSmallUnchangedRanges() = test("removes small unchanged ranges", "[one->two/a->b]")

    @Test
    fun movesIndentationAfterAChange() =
        test("moves indentation after a change", "x\n[   foo/]\n   bar\n   baz")

    @Test
    fun alignsInsertionsToLineBoundaries() =
        test("aligns insertions to line boundaries", " x,\n[/ y,]\n z,\n")

    @Test
    fun alignsDeletionsToLineBoundaries() =
        test("aligns deletions to line boundaries", " x,\n[ y,/]\n z,\n")
}
