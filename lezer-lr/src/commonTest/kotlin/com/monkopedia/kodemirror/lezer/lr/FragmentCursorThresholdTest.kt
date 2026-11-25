/*
 * Copyright 2025 Jason Monk
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
package com.monkopedia.kodemirror.lezer.lr

import com.monkopedia.kodemirror.lezer.common.DEFAULT_BUFFER_LENGTH
import com.monkopedia.kodemirror.lezer.common.NodeType
import com.monkopedia.kodemirror.lezer.common.TextRange
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `@lezer/lr` 1.4.10 only builds a `FragmentCursor` when the parsed extent is
 * longer than four buffer lengths (`dist/index.js:1278`):
 *
 * ```js
 * this.fragments = fragments.length && this.stream.end - from > parser.bufferLength * 4
 *     ? new FragmentCursor(fragments, parser.nodeSet) : null;
 * ```
 *
 * Each case offers a `String` fragment over a run of digits that a fresh
 * parse reads as a `Number`, so `JsonText(String)` means the fragment was
 * reused and `JsonText(Number)` means no cursor was built. Every expectation
 * is what `@lezer/lr` 1.4.10 produces for the same parser, input, ranges and
 * fragment.
 */
class FragmentCursorThresholdTest {

    private fun shape(bufferLength: Int, doc: String, range: TextRange? = null): String {
        val parser = jsonParser.configure(ParserConfig(bufferLength = bufferLength))
        val from = range?.from ?: 0
        val to = range?.to ?: doc.length
        val string = parser.nodeSet.types.first { it.name == "String" }
        val cached = Tree(string, emptyList(), emptyList(), to - from)
        val root = Tree(NodeType.none, listOf(cached), listOf(from), doc.length)
        val fragments = listOf(TreeFragment(0, doc.length, root, 0))
        return treeToString(parser.parse(doc, fragments, range?.let { listOf(it) }))
    }

    @Test
    fun documentLongerThanFourBufferLengthsReusesTheFragment() {
        // Positive control: 5 > 1 * 4, so the cursor exists and reuse happens.
        assertEquals("JsonText(String)", shape(bufferLength = 1, doc = "12345"))
    }

    @Test
    fun documentOfExactlyFourBufferLengthsDoesNotReuse() {
        // The comparison is strict: 4 > 1 * 4 is false.
        assertEquals("JsonText(Number)", shape(bufferLength = 1, doc = "1234"))
    }

    @Test
    fun shortDocumentAtTheDefaultBufferLengthDoesNotReuse() {
        assertEquals("JsonText(Number)", shape(bufferLength = DEFAULT_BUFFER_LENGTH, doc = "12345"))
    }

    @Test
    fun thresholdScalesWithBufferLength() {
        assertEquals("JsonText(Number)", shape(bufferLength = 2, doc = "12345678"))
        assertEquals("JsonText(String)", shape(bufferLength = 2, doc = "123456789"))
    }

    @Test
    fun thresholdMeasuresFromTheStartOfTheFirstRange() {
        // Parsing "12345" at offset 4: the extent is 9 - 4 = 5. That clears
        // 1 * 4 but not 2 * 4, even though the range's end (9) clears both.
        val range = TextRange(4, 9)
        assertEquals("JsonText(String)", shape(bufferLength = 1, doc = "xxxx12345", range = range))
        assertEquals("JsonText(Number)", shape(bufferLength = 2, doc = "xxxx12345", range = range))
    }
}
