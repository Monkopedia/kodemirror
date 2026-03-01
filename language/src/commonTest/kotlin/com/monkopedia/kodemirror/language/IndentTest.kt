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

import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IndentTest {

    private fun createState(
        doc: String,
        vararg extensions: com.monkopedia.kodemirror.state.Extension
    ): EditorState {
        return EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                extensions = if (extensions.isEmpty()) {
                    null
                } else if (extensions.size == 1) {
                    extensions[0]
                } else {
                    ExtensionList(extensions.toList())
                }
            )
        )
    }

    @Test
    fun getIndentUnitDefaultsTo4() {
        val state = createState("hello")
        assertEquals(4, getIndentUnit(state))
    }

    @Test
    fun getIndentUnitReturnsCustomValue() {
        val state = createState("hello", indentUnit.of(2))
        assertEquals(2, getIndentUnit(state))
    }

    @Test
    fun indentStringReturnsCorrectSpaces() {
        val state = createState("hello")
        assertEquals("    ", indentString(state, 4))
        assertEquals("  ", indentString(state, 2))
        assertEquals("", indentString(state, 0))
    }

    @Test
    fun indentStringNegativeReturnsEmpty() {
        val state = createState("hello")
        assertEquals("", indentString(state, -1))
    }

    @Test
    fun getIndentationWithIndentServiceReturnsServiceResult() {
        val service: (IndentContext, Int) -> Int? = { _, _ -> 8 }
        val state = createState("hello", indentService.of(service))
        val result = getIndentation(state, 0)
        assertEquals(8, result)
    }

    @Test
    fun getIndentationReturnsNullWithoutServiceOrTree() {
        val state = createState("hello")
        val result = getIndentation(state, 0)
        assertNull(result)
    }

    @Test
    fun indentContextLineIndentComputesCorrectColumnCount() {
        val state = createState("    hello\n  world")
        val ctx = IndentContext(state)
        assertEquals(4, ctx.lineIndent(0))
        assertEquals(2, ctx.lineIndent(10))
    }

    @Test
    fun countIndentSpaces() {
        assertEquals(0, countIndent("hello", 4))
        assertEquals(4, countIndent("    hello", 4))
        assertEquals(8, countIndent("        hello", 4))
    }

    @Test
    fun countIndentTabs() {
        assertEquals(4, countIndent("\thello", 4))
        assertEquals(8, countIndent("\t\thello", 4))
    }

    @Test
    fun countIndentMixed() {
        // 2 spaces + tab (tabSize=4) => 2 + (4 - 2%4) = 2 + 2 = 4
        assertEquals(4, countIndent("  \thello", 4))
        // 1 space + tab => 1 + (4 - 1%4) = 1 + 3 = 4
        assertEquals(4, countIndent(" \thello", 4))
    }

    @Test
    fun getIndentationFirstServiceWins() {
        val service1: (IndentContext, Int) -> Int? = { _, _ -> 2 }
        val service2: (IndentContext, Int) -> Int? = { _, _ -> 8 }
        val state = createState(
            "hello",
            ExtensionList(
                listOf(indentService.of(service1), indentService.of(service2))
            )
        )
        assertEquals(2, getIndentation(state, 0))
    }

    @Test
    fun getIndentationServiceReturningNullFallsThrough() {
        val service1: (IndentContext, Int) -> Int? = { _, _ -> null }
        val service2: (IndentContext, Int) -> Int? = { _, _ -> 6 }
        val state = createState(
            "hello",
            ExtensionList(
                listOf(indentService.of(service1), indentService.of(service2))
            )
        )
        assertEquals(6, getIndentation(state, 0))
    }
}
