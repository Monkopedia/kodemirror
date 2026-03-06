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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class LRParserTest {

    private fun parse(input: String): String = treeToString(jsonParser.parse(input))

    // --- Deserialization ---

    @Test
    fun deserializeCreatesValidParser() {
        assertTrue(jsonParser.states.isNotEmpty())
        assertTrue(jsonParser.data.isNotEmpty())
        assertTrue(jsonParser.goto.isNotEmpty())
        assertTrue(jsonParser.maxTerm > 0)
        assertTrue(jsonParser.nodeSet.types.isNotEmpty())
        assertTrue(jsonParser.tokenizers.isNotEmpty())
        assertEquals("JsonText", jsonParser.topNode.name)
    }

    // --- End-to-end parsing ---

    @Test
    fun parsesSimpleLiteral() {
        assertEquals("JsonText(True)", parse("true"))
    }

    @Test
    fun parsesNestedStructure() {
        assertEquals(
            "JsonText(Object(Property(PropertyName,Array(Number,Object))))",
            parse("{\"a\":[1,{}]}")
        )
    }

    @Test
    fun parsesEmptyInput() {
        val result = parse("")
        // Empty input produces an error node inside JsonText
        assertTrue(result.startsWith("JsonText"))
    }

    // --- Goto table ---

    @Test
    fun getGotoReturnsValidTarget() {
        // State 0 is the start state; the top rule produces JsonText (term 1)
        val target = jsonParser.getGoto(0, 1, true)
        assertTrue(target >= 0, "getGoto should return a valid state for the top rule")
    }

    // --- Action lookup ---

    @Test
    fun hasActionForValidAndInvalidTerminals() {
        val startState = jsonParser.top[0]
        // The start state should have actions for some token
        // Check that an invalid terminal (maxTerm+5) returns 0
        val invalid = jsonParser.hasAction(startState, jsonParser.maxTerm + 5)
        assertEquals(0, invalid, "hasAction should return 0 for an invalid terminal")
    }

    // --- configure ---

    @Test
    fun configureStrictMode() {
        val strict = jsonParser.configure(ParserConfig(strict = true))
        assertTrue(strict.strict)
        assertFailsWith<IllegalStateException> {
            strict.parse("not valid json at all @@@@")
        }
    }

    @Test
    fun configureBufferLength() {
        val small = jsonParser.configure(ParserConfig(bufferLength = 32))
        assertEquals(32, small.bufferLength)
        // Should still produce correct tree
        assertEquals("JsonText(True)", treeToString(small.parse("true")))
        assertEquals(
            "JsonText(Array(Number,Number,Number))",
            treeToString(small.parse("[1,2,3]"))
        )
    }

    @Test
    fun configurePreservesOriginal() {
        val original = jsonParser
        val configured = original.configure(ParserConfig(strict = true, bufferLength = 16))
        assertNotSame(original, configured)
        assertEquals(false, original.strict)
        assertTrue(configured.strict)
    }

    @Test
    fun configureInvalidTopThrows() {
        assertFailsWith<IllegalArgumentException> {
            jsonParser.configure(ParserConfig(top = "NonExistentRule"))
        }
    }

    // --- getName ---

    @Test
    fun getNameReturnsNodeTypeName() {
        // Term 1 is "JsonText" based on the nodeNames string
        assertEquals("JsonText", jsonParser.getName(1))
    }

    // --- parseDialect ---

    @Test
    fun parseDialectWithNoDialects() {
        // JSON grammar has no dialects, so default dialect should allow all terms
        val dialect = jsonParser.parseDialect()
        assertTrue(dialect.flags.isEmpty())
    }
}
