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
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IndentTest {

    private fun createState(
        doc: String,
        vararg extensions: com.monkopedia.kodemirror.state.Extension
    ): EditorState = EditorState.create(
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

    @Test
    fun getIndentUnitDefaultsTo2() {
        val state = createState("hello")
        assertEquals(2, getIndentUnit(state))
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
        val service: (IndentContext, DocPos) -> Int? = { _, _ -> 8 }
        val state = createState("hello", indentService.of(service))
        val result = getIndentation(state, DocPos.ZERO)
        assertEquals(8, result)
    }

    @Test
    fun getIndentationReturnsNullWithoutServiceOrTree() {
        val state = createState("hello")
        val result = getIndentation(state, DocPos.ZERO)
        assertNull(result)
    }

    @Test
    fun indentContextLineIndentComputesCorrectColumnCount() {
        val state = createState("    hello\n  world")
        val ctx = IndentContext(state)
        assertEquals(4, ctx.lineIndent(DocPos.ZERO))
        assertEquals(2, ctx.lineIndent(DocPos(10)))
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
        val service1: (IndentContext, DocPos) -> Int? = { _, _ -> 2 }
        val service2: (IndentContext, DocPos) -> Int? = { _, _ -> 8 }
        val state = createState(
            "hello",
            ExtensionList(
                listOf(indentService.of(service1), indentService.of(service2))
            )
        )
        assertEquals(2, getIndentation(state, DocPos.ZERO))
    }

    @Test
    fun textAfterPosBasicSlice() {
        val state = createState("    hello world")
        val ctx = IndentContext(state)
        assertEquals("hello world", ctx.textAfterPos(DocPos(4)))
        assertEquals("world", ctx.textAfterPos(DocPos(10)))
        assertEquals("    hello world", ctx.textAfterPos(DocPos.ZERO))
    }

    @Test
    fun textAfterPosCapsAt100Chars() {
        val longLine = "x".repeat(250)
        val state = createState(longLine)
        val ctx = IndentContext(state)
        // From pos 0, capped at 100 characters.
        assertEquals(100, ctx.textAfterPos(DocPos.ZERO).length)
        // From pos 10, still 100 characters (min(len, pos+100) - pos).
        assertEquals(100, ctx.textAfterPos(DocPos(10)).length)
        // Near the end, capped by the line length instead.
        assertEquals(30, ctx.textAfterPos(DocPos(220)).length)
    }

    @Test
    fun textAfterPosDoubleBreakReturnsEmpty() {
        val state = createState("    hello")
        val ctx = IndentContext(
            state,
            simulateBreak = DocPos(4),
            simulateDoubleBreak = true
        )
        // At the break position with a simulated double break => "".
        assertEquals("", ctx.textAfterPos(DocPos(4)))
    }

    @Test
    fun textAfterPosSimulateBreakSplitsLine() {
        // Single line "foobar"; simulate a break at pos 3.
        val state = createState("foobar")
        val ctx = IndentContext(state, simulateBreak = DocPos(3))
        // bias >= 0 and break <= pos => content after the break.
        assertEquals("bar", ctx.textAfterPos(DocPos(3), 1))
        // A pos before the break sees the content before the break
        // ("foo"), sliced from pos 1 => "oo".
        assertEquals("oo", ctx.textAfterPos(DocPos(1), 1))
    }

    @Test
    fun lineIndentHonorsSimulateBreak() {
        // "  ab  cd" — simulate a break at pos 4 (right before the second
        // group of spaces). The "line after the break" is "  cd" whose
        // indentation is 2 columns.
        val state = createState("  ab  cd")
        val ctx = IndentContext(state, simulateBreak = DocPos(4))
        // pos after the break: indentation of the after-break content.
        assertEquals(2, ctx.lineIndent(DocPos(4), 1))
        // Without a simulated break the whole line's indent is 2 as well,
        // but here we prove the before-break side (bias sees text before).
        val ctxBefore = IndentContext(state, simulateBreak = DocPos(6))
        // pos 6 is after break 6 (bias>=0, break<=pos) => after content "cd".
        assertEquals(0, ctxBefore.lineIndent(DocPos(6), 1))
    }

    @Test
    fun columnHonorsSimulateBreakAndTabs() {
        val state = createState("ab\tcd")
        val tree = TreeIndentContext(state, DocPos.ZERO)
        // Column of pos 3 (after the tab): "ab" = 2 cols, tab to next stop.
        // tabSize default 4 => 2 -> 4, so column at index 3 is 4.
        assertEquals(4, tree.column(3))
    }

    @Test
    fun columnSplitLineBySimulateBreak() {
        val state = createState("foobar")
        val tree = TreeIndentContext(
            state,
            DocPos.ZERO,
            simulateBreak = DocPos(3)
        )
        // With a break at 3 and bias>=0, pos 5 sits in the after-break
        // content "bar"; its column relative to that content is 2.
        assertEquals(2, tree.column(5, 1))
    }

    @Test
    fun getIndentationServiceReturningNullFallsThrough() {
        val service1: (IndentContext, DocPos) -> Int? = { _, _ -> null }
        val service2: (IndentContext, DocPos) -> Int? = { _, _ -> 6 }
        val state = createState(
            "hello",
            ExtensionList(
                listOf(indentService.of(service1), indentService.of(service2))
            )
        )
        assertEquals(6, getIndentation(state, DocPos.ZERO))
    }
}
