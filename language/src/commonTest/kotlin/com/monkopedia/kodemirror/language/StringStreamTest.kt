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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StringStreamTest {

    @Test
    fun solAndEol() {
        val stream = StringStream("hello", 4, 2)
        assertTrue(stream.sol())
        assertFalse(stream.eol())
        stream.skipToEnd()
        assertFalse(stream.sol())
        assertTrue(stream.eol())
    }

    @Test
    fun emptyStreamIsEol() {
        val stream = StringStream("", 4, 2)
        assertTrue(stream.sol())
        assertTrue(stream.eol())
    }

    @Test
    fun peekDoesNotAdvance() {
        val stream = StringStream("abc", 4, 2)
        assertEquals("a", stream.peek())
        assertEquals("a", stream.peek())
        assertEquals(0, stream.pos)
    }

    @Test
    fun peekReturnsNullAtEnd() {
        val stream = StringStream("", 4, 2)
        assertNull(stream.peek())
    }

    @Test
    fun nextAdvances() {
        val stream = StringStream("ab", 4, 2)
        assertEquals("a", stream.next())
        assertEquals("b", stream.next())
        assertNull(stream.next())
        assertTrue(stream.eol())
    }

    @Test
    fun eatString() {
        val stream = StringStream("abc", 4, 2)
        assertEquals("a", stream.eat("a"))
        assertNull(stream.eat("x"))
        assertEquals(1, stream.pos)
    }

    @Test
    fun eatRegex() {
        val stream = StringStream("123abc", 4, 2)
        assertNotNull(stream.eat(Regex("[0-9]")))
        assertEquals(1, stream.pos)
        assertNull(stream.eat(Regex("[a-z]")))
        assertEquals(1, stream.pos)
    }

    @Test
    fun eatPredicate() {
        val stream = StringStream("abc", 4, 2)
        assertEquals("a", stream.eat { it == "a" })
        assertNull(stream.eat { it == "z" })
    }

    @Test
    fun eatWhileString() {
        val stream = StringStream("aaab", 4, 2)
        assertTrue(stream.eatWhile("a"))
        assertEquals(3, stream.pos)
        assertFalse(stream.eatWhile("x"))
    }

    @Test
    fun eatWhileRegex() {
        val stream = StringStream("123abc", 4, 2)
        assertTrue(stream.eatWhile(Regex("[0-9]")))
        assertEquals(3, stream.pos)
    }

    @Test
    fun eatWhilePredicate() {
        val stream = StringStream("   x", 4, 2)
        assertTrue(stream.eatWhile { it == " " })
        assertEquals(3, stream.pos)
    }

    @Test
    fun eatSpace() {
        val stream = StringStream("  \thello", 4, 2)
        assertTrue(stream.eatSpace())
        assertEquals(3, stream.pos)
        assertFalse(stream.eatSpace())
    }

    @Test
    fun eatSpaceNbsp() {
        val stream = StringStream("\u00a0 x", 4, 2)
        assertTrue(stream.eatSpace())
        assertEquals(2, stream.pos)
    }

    @Test
    fun skipToEnd() {
        val stream = StringStream("hello", 4, 2)
        stream.skipToEnd()
        assertEquals(5, stream.pos)
        assertTrue(stream.eol())
    }

    @Test
    fun skipToFound() {
        val stream = StringStream("hello world", 4, 2)
        assertTrue(stream.skipTo("w"))
        assertEquals(6, stream.pos)
    }

    @Test
    fun skipToNotFound() {
        val stream = StringStream("hello", 4, 2)
        assertFalse(stream.skipTo("z"))
        assertEquals(0, stream.pos)
    }

    @Test
    fun backUp() {
        val stream = StringStream("abc", 4, 2)
        stream.next()
        stream.next()
        assertEquals(2, stream.pos)
        stream.backUp(1)
        assertEquals(1, stream.pos)
    }

    @Test
    fun column() {
        val stream = StringStream("  hello", 4, 2)
        stream.pos = 2
        stream.start = 2
        assertEquals(2, stream.column())
    }

    @Test
    fun columnWithTabs() {
        val stream = StringStream("\thello", 4, 2)
        stream.pos = 1
        stream.start = 1
        assertEquals(4, stream.column())
    }

    @Test
    fun indentation() {
        val stream = StringStream("  hello", 4, 2)
        assertEquals(2, stream.indentation())
    }

    @Test
    fun indentationWithTabs() {
        val stream = StringStream("\t\thello", 4, 2)
        assertEquals(8, stream.indentation())
    }

    @Test
    fun indentationOverride() {
        val stream = StringStream("  hello", 4, 2, overrideIndent = 10)
        assertEquals(10, stream.indentation())
    }

    @Test
    fun matchString() {
        val stream = StringStream("hello world", 4, 2)
        assertTrue(stream.match("hello"))
        assertEquals(5, stream.pos)
        assertFalse(stream.match("xyz"))
        assertEquals(5, stream.pos)
    }

    @Test
    fun matchStringNoConsume() {
        val stream = StringStream("hello", 4, 2)
        assertTrue(stream.match("hello", consume = false))
        assertEquals(0, stream.pos)
    }

    @Test
    fun matchStringCaseInsensitive() {
        val stream = StringStream("Hello", 4, 2)
        assertFalse(stream.match("hello"))
        assertEquals(0, stream.pos)
        assertTrue(stream.match("hello", caseInsensitive = true))
        assertEquals(5, stream.pos)
    }

    @Test
    fun matchRegex() {
        val stream = StringStream("123abc", 4, 2)
        val result = stream.match(Regex("^[0-9]+"))
        assertNotNull(result)
        assertEquals("123", result.value)
        assertEquals(3, stream.pos)
    }

    @Test
    fun matchRegexNoConsume() {
        val stream = StringStream("123", 4, 2)
        val result = stream.match(Regex("^[0-9]+"), consume = false)
        assertNotNull(result)
        assertEquals(0, stream.pos)
    }

    @Test
    fun matchRegexNotAtStart() {
        val stream = StringStream("abc123", 4, 2)
        // Pattern matches but not at pos 0 of the substring
        assertNull(stream.match(Regex("[0-9]+")))
    }

    @Test
    fun matchRegexNoMatch() {
        val stream = StringStream("abc", 4, 2)
        assertNull(stream.match(Regex("^[0-9]+")))
        assertEquals(0, stream.pos)
    }

    @Test
    fun current() {
        val stream = StringStream("hello world", 4, 2)
        stream.start = 0
        stream.pos = 5
        assertEquals("hello", stream.current())
    }

    @Test
    fun matchStringBeyondEnd() {
        val stream = StringStream("hi", 4, 2)
        assertFalse(stream.match("hello"))
    }
}
