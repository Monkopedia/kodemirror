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

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchBracketsTest {

    private fun createState(doc: String): EditorState =
        EditorState.create(EditorStateConfig(doc = doc.asDoc()))

    @Test
    fun matchParenthesesForward() {
        val state = createState("(hello)")
        val result = matchBrackets(state, DocPos.ZERO, 1)
        assertNotNull(result)
        assertTrue(result.matched)
        assertEquals(DocPos.ZERO, result.start.from)
        assertEquals(DocPos(1), result.start.to)
        val end = result.end
        assertNotNull(end)
        assertEquals(DocPos(6), end.from)
        assertEquals(DocPos(7), end.to)
    }

    @Test
    fun matchSquareBracketsForward() {
        val state = createState("[hello]")
        val result = matchBrackets(state, DocPos.ZERO, 1)
        assertNotNull(result)
        assertTrue(result.matched)
        val end = result.end
        assertNotNull(end)
        assertEquals(DocPos(6), end.from)
    }

    @Test
    fun matchCurlyBracesForward() {
        val state = createState("{hello}")
        val result = matchBrackets(state, DocPos.ZERO, 1)
        assertNotNull(result)
        assertTrue(result.matched)
        val end = result.end
        assertNotNull(end)
        assertEquals(DocPos(6), end.from)
    }

    @Test
    fun matchBackward() {
        val state = createState("(hello)")
        // dir < 0 looks at the bracket ending at pos
        val result = matchBrackets(state, DocPos(7), -1)
        assertNotNull(result)
        assertTrue(result.matched)
        assertEquals(DocPos(6), result.start.from)
        val end = result.end
        assertNotNull(end)
        assertEquals(DocPos.ZERO, end.from)
    }

    @Test
    fun returnsNullForNonBracketCharacter() {
        val state = createState("hello")
        val result = matchBrackets(state, DocPos(2), 1)
        assertNull(result)
    }

    @Test
    fun unmatchedBracketReturnsFalseMatched() {
        val state = createState("(hello")
        val result = matchBrackets(state, DocPos.ZERO, 1)
        assertNotNull(result)
        assertFalse(result.matched)
        assertNull(result.end)
    }

    @Test
    fun nestedBracketsResolveCorrectlyOuter() {
        val state = createState("((a))")
        // Match outer opening paren at position 0
        val result = matchBrackets(state, DocPos.ZERO, 1)
        assertNotNull(result)
        assertTrue(result.matched)
        val end = result.end
        assertNotNull(end)
        assertEquals(DocPos(4), end.from)
    }

    @Test
    fun nestedBracketsResolveCorrectlyInner() {
        val state = createState("((a))")
        // Match inner opening paren at position 1
        val result = matchBrackets(state, DocPos(1), 1)
        assertNotNull(result)
        assertTrue(result.matched)
        val end = result.end
        assertNotNull(end)
        assertEquals(DocPos(3), end.from)
    }

    @Test
    fun nestedBracketsBackwardOuter() {
        val state = createState("((a))")
        // Match outer closing paren at position 4 (ending at 5)
        val result = matchBrackets(state, DocPos(5), -1)
        assertNotNull(result)
        assertTrue(result.matched)
        val end = result.end
        assertNotNull(end)
        assertEquals(DocPos.ZERO, end.from)
    }

    @Test
    fun nestedBracketsBackwardInner() {
        val state = createState("((a))")
        // Match inner closing paren at position 3 (ending at 4)
        val result = matchBrackets(state, DocPos(4), -1)
        assertNotNull(result)
        assertTrue(result.matched)
        val end = result.end
        assertNotNull(end)
        assertEquals(DocPos(1), end.from)
    }

    @Test
    fun posOutOfBoundsReturnsNull() {
        val state = createState("()")
        assertNull(matchBrackets(state, DocPos(-1), 1))
        assertNull(matchBrackets(state, DocPos(2), 1))
    }

    // Expected values below come from @codemirror/language 6.12.2 run on the same input (#450).
    private fun describe(
        doc: String,
        pos: Int,
        dir: Int,
        config: BracketMatchingConfig = BracketMatchingConfig()
    ) = matchBrackets(createState(doc), DocPos(pos), dir, config)?.let {
        "${it.start.from.value}-${it.start.to.value}>" +
            (it.end?.let { e -> "${e.from.value}-${e.to.value}" } ?: "none") + ":${it.matched}"
    }

    @Test
    fun dirSelectsTheBracketBeforeOrAfterPos() {
        assertNull(describe("()", 0, -1))
        assertEquals("0-1>1-2:true", describe("()", 0, 1))
        assertNull(describe("()", 1, -1))
        assertNull(describe("()", 1, 1))
        assertEquals("1-2>0-1:true", describe("()", 2, -1))
        assertEquals("1-2>3-4:true", describe("a(b)c", 1, 1))
        assertEquals("3-4>1-2:true", describe("a(b)c", 4, -1))
    }

    @Test
    fun mismatchedBracketsReportTheFirstClosingBracketAsUnmatched() {
        assertEquals("0-1>3-4:false", describe("([)]", 0, 1))
        assertEquals("1-2>2-3:false", describe("([)]", 1, 1))
        assertEquals("2-3>1-2:false", describe("([)]", 3, -1))
        assertEquals("0-1>2-3:false", describe("(a]", 0, 1))
    }

    @Test
    fun configBracketsReplaceTheDefaultSet() {
        val angle = BracketMatchingConfig(brackets = "<>")
        assertEquals("0-1>2-3:true", describe("<a>", 0, 1, angle))
        assertNull(describe("<a>", 0, 1))
        assertNull(describe("(a)", 0, 1, angle))
    }

    @Test
    fun noPhantomBracketAtDocumentEnd() {
        // Upstream reports an unmatched bracket at [len, len + 1] here; there is no character.
        assertNull(describe("()", 2, 1))
    }
}
