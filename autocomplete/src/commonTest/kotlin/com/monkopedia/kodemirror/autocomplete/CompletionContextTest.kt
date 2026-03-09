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
package com.monkopedia.kodemirror.autocomplete

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

class CompletionContextTest {

    private fun state(doc: String): EditorState =
        EditorState.create(EditorStateConfig(doc = doc.asDoc()))

    @Test
    fun matchBeforeFindsWordBeforeCursor() {
        val s = state("hello world")
        val ctx = CompletionContext(s, pos = DocPos(5), explicit = false)
        val result = ctx.matchBefore(Regex("\\w+"))
        assertNotNull(result)
        assertEquals(DocPos(0), result.from)
        assertEquals(DocPos(5), result.to)
        assertEquals("hello", result.text)
    }

    @Test
    fun matchBeforeReturnsNullWhenNoMatch() {
        val s = state("hello world")
        val ctx = CompletionContext(s, pos = DocPos.ZERO, explicit = false)
        val result = ctx.matchBefore(Regex("\\w+"))
        assertNull(result)
    }

    @Test
    fun matchBeforeRespectsLineBoundary() {
        val s = state("first\nsecond")
        val ctx = CompletionContext(s, pos = DocPos(12), explicit = false)
        val result = ctx.matchBefore(Regex("\\w+"))
        assertNotNull(result)
        assertEquals("second", result.text)
        assertEquals(DocPos(6), result.from)
    }

    @Test
    fun explicitFlagIsAccessible() {
        val s = state("hello")
        val ctx1 = CompletionContext(s, pos = DocPos(5), explicit = true)
        assertTrue(ctx1.explicit)
        val ctx2 = CompletionContext(s, pos = DocPos(5), explicit = false)
        assertFalse(ctx2.explicit)
    }
}
