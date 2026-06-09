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
package com.monkopedia.kodemirror.commands

import com.monkopedia.kodemirror.language.IndentContext
import com.monkopedia.kodemirror.language.indentService
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.asSpec
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ports upstream `@codemirror/commands` `indentSelection` tests, adapted to a
 * fixed indent service instead of a real language module (the same pattern as
 * [IndentServiceNewlineTest], see #135). Upstream drives these with
 * `javascriptLanguage` (2-space indentation); here a minimal [indentService]
 * supplies the desired column count, so the test needs no language module.
 */
class IndentSelectionTest {

    /**
     * Build a session whose indent service answers with the given column
     * count for any position (or null to express "no opinion"). [selection]
     * defaults to a single cursor at [cursor].
     */
    private fun viewWithIndent(
        doc: String,
        selection: SelectionSpec,
        indent: (IndentContext, DocPos) -> Int?
    ): EditorSession {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = selection,
                extensions = ExtensionList(
                    listOf(indentService.of(indent))
                )
            )
        )
        return EditorSession(state)
    }

    private fun cursor(at: Int) = SelectionSpec.CursorSpec(DocPos(at))

    @Test
    fun autoIndentsCurrentLine() {
        // "if (0)\nfoo()" with the cursor at the end. The service asks for 2
        // columns; "foo()" gains a 2-space indent. (Upstream: same shape.)
        val doc = "if (0)\nfoo()"
        val v = viewWithIndent(doc, cursor(doc.length)) { _, _ -> 2 }
        indentSelection(v)
        assertEquals("if (0)\n  foo()", v.state.doc.toString())
        // Cursor stays at the (now shifted) end of "foo()".
        assertEquals("if (0)\n  foo()".length, v.state.selection.main.head.value)
    }

    @Test
    fun movesCursorAheadOfIndentation() {
        // "if (0)\n foo()" with the cursor inside the old single-space indent.
        // Re-indenting to 2 spaces moves the cursor to just after the indent.
        val doc = "if (0)\n foo()"
        // Cursor between the leading space and "foo" → offset 8.
        val v = viewWithIndent(doc, cursor(8)) { _, _ -> 2 }
        indentSelection(v)
        assertEquals("if (0)\n  foo()", v.state.doc.toString())
        // After "if (0)\n" (7) + "  " (2) → cursor at offset 9, before "foo".
        assertEquals(9, v.state.selection.main.head.value)
    }

    @Test
    fun indentsBlocksOfLines() {
        // Three lines selected; each is re-indented to the service's 2 columns.
        val doc = "if (0) {\none\ntwo\nthree\n}"
        // Select from start of "one" to end of "three".
        val from = "if (0) {\n".length
        val to = "if (0) {\none\ntwo\nthree".length
        val v = viewWithIndent(
            doc,
            EditorSelection.single(DocPos(from), DocPos(to)).asSpec()
        ) { _, _ -> 2 }
        indentSelection(v)
        assertEquals("if (0) {\n  one\n  two\n  three\n}", v.state.doc.toString())
    }

    @Test
    fun relativeIndentationAndNullOpinion() {
        // Relative case: the service answers with the line's number * 2 columns
        // so successive selected lines get increasing indentation, except line
        // 3 for which it returns null (no opinion) and is therefore untouched.
        // (Upstream feeds prior changes back via overrideIndentation; kodemirror
        // skips null-opinion lines, so we additionally assert the skip here.)
        val doc = "a\nb\nc\nd"
        val v = viewWithIndent(
            doc,
            EditorSelection.single(DocPos(0), DocPos(doc.length)).asSpec()
        ) { ctx, pos ->
            val number = ctx.state.doc.lineAt(pos).number.value
            if (number == 3) null else (number - 1) * 2
        }
        indentSelection(v)
        // line 1 → 0, line 2 → 2, line 3 → null (unchanged), line 4 → 6.
        assertEquals("a\n  b\nc\n      d", v.state.doc.toString())
    }
}
