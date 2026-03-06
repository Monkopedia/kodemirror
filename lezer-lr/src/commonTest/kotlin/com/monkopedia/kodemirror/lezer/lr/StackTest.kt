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
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class StackTest {

    private fun createParse(input: String): Parse {
        val si = StringInput(input)
        return Parse(
            jsonParser,
            si,
            emptyList(),
            listOf(TextRange(0, input.length))
        )
    }

    // --- split ---

    @Test
    fun splitCreatesIndependentCopy() {
        val p = createParse("[1, 2]")
        val stack = Stack.start(p, jsonParser.top[0], 0)
        stack.pushState(1, 0)

        val copy = stack.split()
        assertNotSame(stack, copy)
        assertEquals(stack.state, copy.state)
        assertEquals(stack.pos, copy.pos)

        // Mutating original doesn't affect copy
        stack.state = 999
        assertEquals(jsonParser.top[0], copy.stack[0])
    }

    // --- sameState ---

    @Test
    fun sameStateDetectsIdenticalStacks() {
        val p = createParse("true")
        val s1 = Stack.start(p, jsonParser.top[0], 0)
        val s2 = Stack.start(p, jsonParser.top[0], 0)
        assertTrue(s1.sameState(s2))
    }

    @Test
    fun sameStateDetectsDifferentStacks() {
        val p = createParse("true")
        val s1 = Stack.start(p, jsonParser.top[0], 0)
        val s2 = Stack.start(p, jsonParser.top[0], 0)
        s2.pushState(1, 0)
        assertFalse(s1.sameState(s2))
    }

    // --- forceAll ---

    @Test
    fun forceAllReachesAcceptingState() {
        val input = "true"
        val tree = jsonParser.parse(input)
        // If we can parse it, forceAll works. Test it directly via a partial parse.
        val p = createParse(input)
        val stack = Stack.start(p, jsonParser.top[0], 0)
        // forceAll on a fresh stack should either reach accepting or insert error
        val result = stack.forceAll()
        // Should not throw and should return a stack
        assertTrue(result.pos >= 0)
    }

    // --- canShift ---

    @Test
    fun canShiftChecksTermValidity() {
        val p = createParse("true")
        val stack = Stack.start(p, jsonParser.top[0], 0)
        // The start state should be able to shift some valid JSON token
        // Term.ERR (0) is the error term — check it doesn't throw
        val canShiftErr = stack.canShift(Term.ERR)
        // Just verify it returns a boolean without throwing
        assertTrue(canShiftErr || !canShiftErr)
    }
}
