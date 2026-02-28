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

import com.monkopedia.kodemirror.lezer.common.StringInput
import com.monkopedia.kodemirror.lezer.common.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Tests for [CachedToken] and [InputStream] classes.
 */
class TokenTest {

    // --- CachedToken tests ---

    @Test
    fun cachedTokenDefaultValues() {
        val token = CachedToken()
        assertEquals(-1, token.start)
        assertEquals(-1, token.value)
        assertEquals(-1, token.end)
        assertEquals(-1, token.extended)
        assertEquals(0, token.lookAhead)
        assertEquals(0, token.mask)
        assertEquals(0, token.context)
    }

    @Test
    fun cachedTokenCanBeModified() {
        val token = CachedToken()
        token.start = 10
        token.value = 5
        token.end = 20
        token.extended = 3
        token.lookAhead = 25
        token.mask = 0xff
        token.context = 42

        assertEquals(10, token.start)
        assertEquals(5, token.value)
        assertEquals(20, token.end)
        assertEquals(3, token.extended)
        assertEquals(25, token.lookAhead)
        assertEquals(0xff, token.mask)
        assertEquals(42, token.context)
    }

    @Test
    fun nullTokenExists() {
        assertNotNull(nullToken)
        assertEquals(-1, nullToken.start)
        assertEquals(-1, nullToken.value)
    }

    // --- Helper to create InputStream ---

    private fun createStream(text: String, ranges: List<TextRange>? = null): InputStream {
        val input = StringInput(text)
        val effectiveRanges = ranges ?: listOf(TextRange(0, text.length))
        return InputStream(input, effectiveRanges)
    }

    // --- InputStream constructor tests ---

    @Test
    fun inputStreamSetsInitialPosition() {
        val stream = createStream("hello")
        assertEquals(0, stream.pos)
    }

    @Test
    fun inputStreamSetsInitialPositionFromRange() {
        val stream = createStream("hello world", listOf(TextRange(6, 11)))
        assertEquals(6, stream.pos)
    }

    @Test
    fun inputStreamReadsFirstChar() {
        val stream = createStream("hello")
        assertEquals('h'.code, stream.next)
    }

    @Test
    fun inputStreamReadsFirstCharFromRange() {
        val stream = createStream("hello world", listOf(TextRange(6, 11)))
        assertEquals('w'.code, stream.next)
    }

    @Test
    fun inputStreamSetsEnd() {
        val stream = createStream("hello")
        assertEquals(5, stream.end)
    }

    @Test
    fun inputStreamSetsEndFromRanges() {
        val stream = createStream(
            "hello world",
            listOf(TextRange(0, 5), TextRange(6, 11))
        )
        assertEquals(11, stream.end)
    }

    // --- InputStream.advance tests ---

    @Test
    fun advanceMovesForwardOneChar() {
        val stream = createStream("hello")
        assertEquals('h'.code, stream.next)
        stream.advance()
        assertEquals('e'.code, stream.next)
        assertEquals(1, stream.pos)
    }

    @Test
    fun advanceMovesForwardMultipleChars() {
        val stream = createStream("hello")
        stream.advance(3)
        assertEquals('l'.code, stream.next)
        assertEquals(3, stream.pos)
    }

    @Test
    fun advanceToEndReturnsNegOne() {
        val stream = createStream("hi")
        stream.advance() // 'i'
        stream.advance() // past end
        assertEquals(-1, stream.next)
    }

    @Test
    fun advanceThroughEntireString() {
        val text = "abc"
        val stream = createStream(text)
        val chars = mutableListOf<Int>()
        while (stream.next >= 0) {
            chars.add(stream.next)
            stream.advance()
        }
        assertEquals(listOf('a'.code, 'b'.code, 'c'.code), chars)
    }

    @Test
    fun advanceReturnsNextCharCode() {
        val stream = createStream("abc")
        val next = stream.advance()
        assertEquals('b'.code, next)
    }

    // --- InputStream.peek tests ---

    @Test
    fun peekZeroEqualsNext() {
        val stream = createStream("hello")
        assertEquals(stream.next, stream.peek(0))
    }

    @Test
    fun peekAhead() {
        val stream = createStream("hello")
        assertEquals('e'.code, stream.peek(1))
        assertEquals('l'.code, stream.peek(2))
        assertEquals('l'.code, stream.peek(3))
        assertEquals('o'.code, stream.peek(4))
    }

    @Test
    fun peekPastEndReturnsNegOne() {
        val stream = createStream("hi")
        assertEquals(-1, stream.peek(5))
    }

    @Test
    fun peekDoesNotChangePosition() {
        val stream = createStream("hello")
        val posBefore = stream.pos
        stream.peek(3)
        assertEquals(posBefore, stream.pos)
        assertEquals('h'.code, stream.next)
    }

    // --- InputStream.acceptToken tests ---

    @Test
    fun acceptTokenSetsTokenValue() {
        val stream = createStream("hello")
        val token = CachedToken()
        stream.reset(0, token)
        stream.advance(3)
        stream.acceptToken(42)
        assertEquals(42, token.value)
        assertEquals(3, token.end)
    }

    @Test
    fun acceptTokenWithEndOffset() {
        val stream = createStream("hello")
        val token = CachedToken()
        stream.reset(0, token)
        stream.advance(3) // pos = 3
        stream.acceptToken(42, -1) // end at pos + (-1) = 2
        assertEquals(42, token.value)
        assertEquals(2, token.end)
    }

    @Test
    fun acceptTokenToSetsExactEnd() {
        val stream = createStream("hello")
        val token = CachedToken()
        stream.reset(0, token)
        stream.advance(2)
        stream.acceptTokenTo(99, 5)
        assertEquals(99, token.value)
        assertEquals(5, token.end)
    }

    // --- InputStream.reset tests ---

    @Test
    fun resetRepositionsStream() {
        val stream = createStream("hello")
        stream.advance(3)
        assertEquals('l'.code, stream.next)
        assertEquals(3, stream.pos)

        stream.reset(1)
        assertEquals(1, stream.pos)
        assertEquals('e'.code, stream.next)
    }

    @Test
    fun resetToSamePositionIsNoop() {
        val stream = createStream("hello")
        stream.advance(2)
        val posBefore = stream.pos
        val nextBefore = stream.next
        stream.reset(2)
        assertEquals(posBefore, stream.pos)
        assertEquals(nextBefore, stream.next)
    }

    @Test
    fun resetWithTokenSetsTokenFields() {
        val stream = createStream("hello")
        val token = CachedToken()
        stream.reset(2, token)

        assertSame(token, stream.token)
        assertEquals(2, token.start)
        assertEquals(3, token.lookAhead)
        assertEquals(-1, token.value)
        assertEquals(-1, token.extended)
    }

    @Test
    fun resetWithNullTokenUsesNullToken() {
        val stream = createStream("hello")
        val token = CachedToken()
        stream.reset(2, token)
        stream.reset(0, null)
        assertSame(nullToken, stream.token)
    }

    @Test
    fun resetToEnd() {
        val stream = createStream("hello")
        stream.reset(5)
        assertEquals(-1, stream.next)
        assertEquals(5, stream.pos)
    }

    @Test
    fun resetToBeginning() {
        val stream = createStream("hello")
        stream.advance(4)
        stream.reset(0)
        assertEquals(0, stream.pos)
        assertEquals('h'.code, stream.next)
    }

    // --- InputStream.read tests ---

    @Test
    fun readSubstring() {
        val stream = createStream("hello world")
        val result = stream.read(0, 5)
        assertEquals("hello", result)
    }

    @Test
    fun readMiddleSubstring() {
        val stream = createStream("hello world")
        val result = stream.read(6, 11)
        assertEquals("world", result)
    }

    @Test
    fun readSingleChar() {
        val stream = createStream("hello")
        val result = stream.read(0, 1)
        assertEquals("h", result)
    }

    @Test
    fun readEmptyString() {
        val stream = createStream("hello")
        val result = stream.read(2, 2)
        assertEquals("", result)
    }

    // --- InputStream with multiple ranges ---

    @Test
    fun multipleRangesBasicNavigation() {
        // Text: "hello world" with ranges [0,5) and [6,11)
        // This simulates a document with "hello" and "world" as separate ranges
        val stream = createStream(
            "hello world",
            listOf(TextRange(0, 5), TextRange(6, 11))
        )
        assertEquals(0, stream.pos)
        assertEquals('h'.code, stream.next)
        assertEquals(11, stream.end)
    }

    @Test
    fun advanceAcrossRangeBoundary() {
        val stream = createStream(
            "hello world",
            listOf(TextRange(0, 5), TextRange(6, 11))
        )
        // Advance through first range
        val chars = mutableListOf<Int>()
        while (stream.next >= 0) {
            chars.add(stream.next)
            stream.advance()
        }
        // Should read 'h','e','l','l','o','w','o','r','l','d'
        assertEquals(10, chars.size)
        assertEquals('h'.code, chars[0])
        assertEquals('o'.code, chars[4])
        assertEquals('w'.code, chars[5]) // jumps to range 2
        assertEquals('d'.code, chars[9])
    }

    @Test
    fun readAcrossRanges() {
        val stream = createStream(
            "hello world",
            listOf(TextRange(0, 5), TextRange(6, 11))
        )
        // Reading within a single range
        assertEquals("hello", stream.read(0, 5))
        assertEquals("world", stream.read(6, 11))
    }

    @Test
    fun resetWithinSecondRange() {
        val stream = createStream(
            "hello world",
            listOf(TextRange(0, 5), TextRange(6, 11))
        )
        stream.reset(8)
        assertEquals(8, stream.pos)
        assertEquals('r'.code, stream.next)
    }

    // --- InputStream with single character range ---

    @Test
    fun singleCharRange() {
        val stream = createStream("x", listOf(TextRange(0, 1)))
        assertEquals('x'.code, stream.next)
        stream.advance()
        assertEquals(-1, stream.next)
    }

    // --- InputStream with empty string ---

    @Test
    fun emptyStringStream() {
        // Empty content with a zero-length range
        val stream = createStream("x", listOf(TextRange(0, 0)))
        assertEquals(-1, stream.next)
    }

    // --- TokenGroup tests ---

    @Test
    fun tokenGroupProperties() {
        val group = TokenGroup(intArrayOf(1, 2, 3), 0)
        assertEquals(false, group.contextual)
        assertEquals(false, group.fallback)
        assertEquals(false, group.extend)
    }

    // --- ExternalTokenizer tests ---

    @Test
    fun externalTokenizerDefault() {
        val tokenizer = ExternalTokenizer(
            tokenFn = { _, _ -> }
        )
        assertEquals(false, tokenizer.contextual)
        assertEquals(false, tokenizer.fallback)
        assertEquals(false, tokenizer.extend)
    }

    @Test
    fun externalTokenizerCustomFlags() {
        val tokenizer = ExternalTokenizer(
            tokenFn = { _, _ -> },
            contextual = true,
            fallback = true,
            extend = true
        )
        assertEquals(true, tokenizer.contextual)
        assertEquals(true, tokenizer.fallback)
        assertEquals(true, tokenizer.extend)
    }

    // --- clipPos tests ---

    @Test
    fun clipPosWithinRange() {
        val stream = createStream("hello")
        assertEquals(2, stream.clipPos(2))
    }

    @Test
    fun clipPosBeyondEnd() {
        val stream = createStream("hello")
        assertEquals(5, stream.clipPos(10))
    }

    @Test
    fun clipPosBeforeRange() {
        val stream = createStream(
            "hello world",
            listOf(TextRange(6, 11))
        )
        // pos 3 is before the range [6,11), so should clamp to range.from = 6
        assertEquals(6, stream.clipPos(3))
    }

    // --- resolveOffset tests ---

    @Test
    fun resolveOffsetForward() {
        val stream = createStream("hello")
        val resolved = stream.resolveOffset(2, 1)
        assertEquals(2, resolved)
    }

    @Test
    fun resolveOffsetAtEnd() {
        val stream = createStream("hello")
        stream.advance(4) // pos = 4
        val resolved = stream.resolveOffset(1, 1)
        // pos 5 is at range.to (end), so assoc=1 means >= to → null
        assertEquals(null, resolved)
    }

    @Test
    fun resolveOffsetBeyondEnd() {
        val stream = createStream("hello")
        val resolved = stream.resolveOffset(10, 1)
        assertEquals(null, resolved)
    }

    // --- findOffset utility function tests ---

    @Test
    fun findOffsetFindsExistingTerm() {
        val data = intArrayOf(10, 20, 30, Seq.End)
        assertEquals(0, findOffset(data, 0, 10))
        assertEquals(1, findOffset(data, 0, 20))
        assertEquals(2, findOffset(data, 0, 30))
    }

    @Test
    fun findOffsetReturnsNeg1ForMissing() {
        val data = intArrayOf(10, 20, 30, Seq.End)
        assertEquals(-1, findOffset(data, 0, 99))
    }

    @Test
    fun findOffsetRespectsStartIndex() {
        val data = intArrayOf(10, 20, 30, Seq.End)
        assertEquals(0, findOffset(data, 1, 20))
        assertEquals(1, findOffset(data, 1, 30))
        assertEquals(-1, findOffset(data, 1, 10))
    }

    // --- overrides utility function tests ---

    @Test
    fun overridesReturnsTrueWhenTokenAppearsFirst() {
        // token appears before prev in the precedence table
        val table = intArrayOf(5, 10, 20, Seq.End)
        assertEquals(true, overrides(5, 10, table, 0))
    }

    @Test
    fun overridesReturnsFalseWhenTokenAppearsAfter() {
        val table = intArrayOf(5, 10, 20, Seq.End)
        assertEquals(false, overrides(10, 5, table, 0))
    }

    @Test
    fun overridesReturnsTrueWhenPrevNotInTable() {
        val table = intArrayOf(5, 10, Seq.End)
        // prev=99 not found → iPrev = -1 → return true? No: iPrev < 0 → return (iPrev < 0) || ...
        // Actually: if iPrev < 0, return -1 < 0 which is true, but wait:
        // overrides returns: iPrev < 0 || findOffset(token) < iPrev
        // If iPrev < 0 (prev not found), function returns true
        assertEquals(true, overrides(5, 99, table, 0))
    }

    @Test
    fun overridesReturnsFalseWhenBothEqual() {
        val table = intArrayOf(5, 10, Seq.End)
        // Same token: findOffset returns same position, not less than
        assertEquals(false, overrides(5, 5, table, 0))
    }
}
