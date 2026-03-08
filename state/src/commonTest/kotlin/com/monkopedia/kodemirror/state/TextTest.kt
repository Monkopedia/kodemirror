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

import kotlin.math.min
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TextTest {

    private fun depth(node: Text): Int {
        return when (node) {
            is TextLeaf -> 1
            is TextNode -> 1 + node.children.maxOf { depth(it) }
            else -> error("Unknown Text subclass")
        }
    }

    private val line = "1234567890".repeat(10)
    private val lines = List(200) { line }
    private val text0 = lines.joinToString("\n")
    private val doc0 = Text.of(lines)

    @Test
    fun handlesBasicReplacement() {
        val doc = Text.of(listOf("one", "two", "three"))
        assertEquals(
            "onfoo\nbarwo\nthree",
            doc.replace(2, 5, Text.of(listOf("foo", "bar"))).toString()
        )
    }

    @Test
    fun canAppendDocuments() {
        assertEquals(
            "one\ntwo\nthree!\nok",
            Text.of(listOf("one", "two", "three"))
                .append(Text.of(listOf("!", "ok"))).toString()
        )
    }

    @Test
    fun preservesLength() {
        assertEquals(text0.length, doc0.length)
    }

    @Test
    fun createsABalancedTreeWhenLoadingADocument() {
        val doc = Text.of(List(2000) { line })
        val d = depth(doc)
        assertTrue(d <= 2)
    }

    @Test
    fun rebalancesOnInsert() {
        var doc = doc0
        val insert = "abc".repeat(200)
        val at = doc.length / 2
        for (i in 0 until 10) doc = doc.replace(at, at, Text.of(listOf(insert)))
        assertTrue(depth(doc) <= 2)
        assertEquals(
            text0.substring(0, at) + "abc".repeat(2000) + text0.substring(at),
            doc.toString()
        )
    }

    @Test
    fun collapsesOnDelete() {
        val doc = doc0.replace(10, text0.length - 10, Text.empty)
        assertEquals(1, depth(doc))
        assertEquals(20, doc.length)
        assertEquals(line.substring(0, 20), doc.toString())
    }

    @Test
    fun handlesDeletingAtStart() {
        assertEquals(
            text0.substring(9500) + "!",
            Text.of(lines.subList(0, lines.size - 1) + listOf(line + "!"))
                .replace(0, 9500, Text.empty).toString()
        )
    }

    @Test
    fun handlesDeletingAtEnd() {
        assertEquals(
            "?" + text0.substring(0, 9499),
            Text.of(listOf("?" + line) + lines.subList(1, lines.size))
                .replace(9500, text0.length + 1, Text.empty).toString()
        )
    }

    @Test
    fun canHandleDeletingTheEntireDocument() {
        assertEquals("", doc0.replace(0, doc0.length, Text.empty).toString())
    }

    @Test
    fun canInsertOnNodeBoundaries() {
        val doc = doc0
        val pos = (doc as TextNode).children[0].length
        assertEquals(
            "abc",
            doc.replace(pos, pos, Text.of(listOf("abc")))
                .slice(pos, pos + 3).toString()
        )
    }

    @Test
    fun canBuildUpADocByRepeatedAppending() {
        var doc = Text.of(listOf(""))
        var text = ""
        for (i in 1 until 1000) {
            val add = "newtext$i "
            doc = doc.replace(doc.length, doc.length, Text.of(listOf(add)))
            text += add
        }
        assertEquals(text, doc.toString())
    }

    @Test
    fun properlyMaintainsContentDuringEditing() {
        val rng = Random(42)
        var str = text0
        var doc: Text = doc0
        for (i in 0 until 200) {
            val insPos = rng.nextInt(doc.length)
            val insChar = ('A' + rng.nextInt(26)).toString()
            str = str.substring(0, insPos) + insChar + str.substring(insPos)
            doc = doc.replace(insPos, insPos, Text.of(listOf(insChar)))
            val delFrom = rng.nextInt(doc.length)
            val delTo = min(doc.length, delFrom + rng.nextInt(20))
            str = str.substring(0, delFrom) + str.substring(delTo)
            doc = doc.replace(delFrom, delTo, Text.empty)
        }
        assertEquals(str, doc.toString())
    }

    @Test
    fun returnsTheCorrectStringsForSlice() {
        val textList = mutableListOf<String>()
        for (i in 0 until 1000) textList.add(i.toString().padStart(4, '0'))
        val doc = Text.of(textList)
        val str = textList.joinToString("\n")
        val rng = Random(42)
        for (i in 0 until 400) {
            var start = if (i == 0) 0 else rng.nextInt(doc.length)
            var end = if (i == 399) doc.length else start + rng.nextInt(doc.length - start)
            start = 4150
            end = 4160
            assertEquals(str.substring(start, end), doc.slice(start, end).toString())
        }
    }

    @Test
    fun canBeCompared() {
        val doc = doc0
        val doc2 = Text.of(lines)
        assertEquals(doc, doc)
        assertEquals(doc, doc2)
        assertEquals(doc2, doc)
        assertNotEquals(doc, doc2.replace(5000, 5000, Text.of(listOf("y"))))
        assertNotEquals(doc, doc2.replace(5000, 5001, Text.of(listOf("y"))))
        assertEquals(doc, doc.replace(5000, 5001, doc.slice(5000, 5001)))
        assertNotEquals(doc, doc.replace(5000, 5001, Text.of(listOf("y"))))
    }

    @Test
    fun canBeComparedDespiteDifferentTreeShape() {
        assertEquals(
            doc0.replace(100, 201, Text.of(listOf("abc"))),
            Text.of(listOf(line + "abc") + lines.subList(2, lines.size))
        )
    }

    @Test
    fun canCompareSmallDocuments() {
        assertEquals(Text.of(listOf("foo", "bar")), Text.of(listOf("foo", "bar")))
        assertNotEquals(Text.of(listOf("foo", "bar")), Text.of(listOf("foo", "baz")))
    }

    @Test
    fun isIterable() {
        val iter = doc0.iter()
        var build = ""
        while (true) {
            iter.next()
            if (iter.done) {
                assertEquals(text0, build)
                break
            }
            if (iter.lineBreak) {
                build += "\n"
            } else {
                assertEquals(-1, iter.value.indexOf("\n"))
                build += iter.value
            }
        }
    }

    @Test
    fun isIterableInReverse() {
        var found = ""
        val iter = doc0.iter(-1)
        while (!iter.next().done) found = iter.value + found
        assertEquals(text0, found)
    }

    @Test
    fun allowsNegativeSkipValuesInIteration() {
        val iter = Text.of(listOf("one", "two", "three", "four")).iter()
        assertEquals("e", iter.next(12).value)
        assertEquals("ne", iter.next(-12).value)
        assertEquals("our", iter.next(12).value)
        assertEquals("one", iter.next(-1000).value)
    }

    @Test
    fun isPartiallyIterable() {
        var found = ""
        val iter = doc0.iterRange(500, doc0.length - 500)
        while (!iter.next().done) found += iter.value
        assertEquals(
            text0.substring(500, text0.length - 500),
            found
        )
    }

    @Test
    fun isPartiallyIterableInReverse() {
        var found = ""
        val iter = doc0.iterRange(doc0.length - 500, 500)
        while (!iter.next().done) found = iter.value + found
        assertEquals(
            text0.substring(500, text0.length - 500),
            found
        )
    }

    @Test
    fun canPartiallyIterOverSubsectionsAtTheStartAndEnd() {
        assertEquals("1", doc0.iterRange(0, 1).next().value)
        assertEquals("2", doc0.iterRange(1, 2).next().value)
        assertEquals("0", doc0.iterRange(doc0.length - 1, doc0.length).next().value)
        assertEquals("9", doc0.iterRange(doc0.length - 2, doc0.length - 1).next().value)
    }

    @Test
    fun canIterateOverLines() {
        val doc = Text.of(listOf("ab", "cde", "", "", "f", "", "g"))
        fun get(from: Int? = null, to: Int? = null): String {
            val result = mutableListOf<String>()
            val iter = doc.iterLines(from, to)
            while (!iter.next().done) result.add(iter.value)
            return result.joinToString("\n")
        }
        assertEquals("ab\ncde\n\n\nf\n\ng", get())
        assertEquals("ab\ncde\n\n\nf\n\ng", get(1, doc.lines + 1))
        assertEquals("cde", get(2, 3))
        assertEquals("cde", get(2, 3))
        assertEquals("ab\ncde\n\n", get(1, 5))
        assertEquals("", get(2, 1))
        assertEquals("\n\nf\n\ng", get(3))
    }

    @Test
    fun canConvertToJSON() {
        val extendedLines = lines.toMutableList()
        for (i in 0 until 200) extendedLines.add("line $i")
        val text = Text.of(extendedLines)
        assertEquals(Text.of(text.toJSON()), text)
    }

    @Test
    fun canGetLineInfoByLineNumber() {
        assertFailsWith<IllegalArgumentException> { doc0.line(0) }
        assertFailsWith<IllegalArgumentException> { doc0.line(doc0.lines + 1) }
        var i = 1
        while (i < doc0.lines) {
            val l = doc0.line(i)
            assertEquals((i - 1) * 101, l.from)
            assertEquals(i * 101 - 1, l.to)
            assertEquals(i, l.number)
            assertEquals(line, l.text)
            i += 5
        }
    }

    @Test
    fun canGetLineInfoByPosition() {
        assertFailsWith<IllegalArgumentException> { doc0.lineAt(-10) }
        assertFailsWith<IllegalArgumentException> { doc0.lineAt(doc0.length + 1) }
        var i = 0
        while (i < doc0.length) {
            val l = doc0.lineAt(i)
            assertEquals(i - (i % 101), l.from)
            assertEquals(i - (i % 101) + 100, l.to)
            assertEquals(i / 101 + 1, l.number)
            assertEquals(line, l.text)
            i += 5
        }
    }

    @Test
    fun canDeleteARangeAtTheStartOfAChildNode() {
        assertEquals(
            "x" + text0.substring(100),
            doc0.replace(0, 100, Text.of(listOf("x"))).toString()
        )
    }

    @Test
    fun canRetrievePiecesOfText() {
        val rng = Random(42)
        for (i in 0 until 500) {
            val from = rng.nextInt(doc0.length - 1)
            val to = if (rng.nextDouble() < 0.5) {
                from + 2
            } else {
                from + rng.nextInt(doc0.length - 1 - from) + 1
            }
            assertEquals(text0.substring(from, to), doc0.sliceString(from, to))
            assertEquals(text0.substring(from, to), doc0.slice(from, to).toString())
        }
    }

    @Test
    fun clipsOutOfRangeBoundaries() {
        assertEquals(0, doc0.slice(0, -10).length)
        assertEquals(0, Text.empty.slice(0, 10).length)
        assertEquals(0, Text.empty.slice(1000, 1100).length)
        assertEquals(0, doc0.slice(5, 0).length)
        assertEquals(0, doc0.slice(-5, 0).length)
    }
}
