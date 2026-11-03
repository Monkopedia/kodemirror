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
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [snippet] application against @codemirror/autocomplete 6.20.1 on the same
 * inputs (#383).
 */
class SnippetTest {

    private fun apply(
        template: String,
        doc: String = "",
        from: Int = 0,
        to: Int = from
    ): EditorSession {
        val session = EditorSession(
            EditorState.create(
                EditorStateConfig(
                    doc = doc.asDoc(),
                    selection = SelectionSpec.CursorSpec(DocPos(to)),
                    extensions = snippets()
                )
            )
        )
        snippet(
            template
        )(CompletionApplyContext(session, Completion("x"), DocPos(from), DocPos(to)))
        return session
    }

    private fun EditorSession.selString() = state.selection.ranges.joinToString(",") {
        if (it.empty) "${it.head.value}" else "${it.anchor.value}-${it.head.value}"
    }

    private fun EditorSession.fields() = state.field(snippetState, require = false)
        ?.fields?.map { listOf(it.from.value, it.to.value) }

    @Test
    fun fieldsAreNotMappedTwiceIntoANonEmptyDocument() {
        val s = apply("f(\${}, \${})", "0123456789")
        assertEquals("f(, )0123456789", s.state.doc.toString())
        assertEquals("2", s.selString())
        assertEquals(listOf(listOf(2, 2), listOf(4, 4)), s.fields())
        nextSnippetField(s)
        assertEquals("4", s.selString())
    }

    @Test
    fun replacingTextAtTheStartDoesNotThrow() {
        // The JS `for` snippet over "fo" threw "Position 9 is out of range".
        val s = apply("for (\${index} = 0; \${index} < \${end}; \${index}++)", "fo", 0, 2)
        assertEquals("for (index = 0; index < end; index++)", s.state.doc.toString())
        val main = s.state.selection.main
        assertEquals(DocPos(5) to DocPos(10), main.anchor to main.head)
    }

    @Test
    fun placeholderTextIsInserted() {
        val s = apply("foo(\${1:a}, \${2:b})")
        assertEquals("foo(a, b)", s.state.doc.toString())
        assertEquals("4-5", s.selString())
        assertEquals(listOf(listOf(4, 5), listOf(7, 8)), s.fields())
    }

    @Test
    fun numberedFieldsAreVisitedInNumberOrder() {
        val s = apply("f(\${2:second}, \${1:first})")
        assertEquals("f(second, first)", s.state.doc.toString())
        assertEquals("10-15", s.selString())
    }

    @Test
    fun newLinesTakeTheLineIndentPlusOneUnitPerTab() {
        val s = apply("if (\${}) {\n\t\${}\n}", "  x", 2, 3)
        assertEquals("  if () {\n    \n  }", s.state.doc.toString())
    }

    @Test
    fun escapedBracesAreLiteral() {
        val s = apply("a\\{b\\}\${x}")
        assertEquals("a{b}x", s.state.doc.toString())
    }

    @Test
    fun movingTheCursorOutOfTheFieldEndsTheSnippet() {
        val s = apply("f(\${a}, \${b})", "0123456789")
        assertNotNull(s.state.field(snippetState, require = false))
        s.dispatch(TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos(0))))
        assertNull(s.state.field(snippetState, require = false))
    }

    @Test
    fun typingInsideTheFieldKeepsTheSnippet() {
        val s = apply("f(\${a}, \${b})")
        s.dispatch(s.state.replaceSelection("z").copy(userEvent = "input.type"))
        assertEquals("f(z, b)", s.state.doc.toString())
        assertNotNull(s.state.field(snippetState, require = false))
        nextSnippetField(s)
        assertEquals("5-6", s.selString())
    }

    @Test
    fun templateWithoutFieldsIsInsertedAsIs() {
        val s = apply("abc", "x", 1)
        assertEquals("xabc", s.state.doc.toString())
        assertEquals("4", s.selString())
    }
}
