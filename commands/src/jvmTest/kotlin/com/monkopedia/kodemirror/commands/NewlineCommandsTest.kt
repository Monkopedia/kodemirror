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

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ported from upstream `@codemirror/commands` `test/test-commands.ts`
 * (`insertNewlineKeepIndent` and the bracket-explosion behaviour of
 * `insertNewlineAndIndent`). Part of #116 — both commands were ported but
 * untested.
 *
 * These cases use a plain [EditorState] with no language extension, so the
 * indentation comes from the leading-whitespace fallback (no indent service).
 * The amount-of-indent cases that need a language indent service
 * (`getIndentation`) are tracked separately. A single `|` marks the cursor;
 * `<…>` an anchored selection range.
 */
class NewlineCommandsTest {

    private fun view(docWithCursor: String): EditorSession {
        // Supports a single `|` cursor, or `<` … `|` for a selection whose
        // anchor is at `<` and head at `|`.
        val anchorMark = docWithCursor.indexOf('<')
        val headMark = docWithCursor.indexOf('|')
        require(headMark >= 0) { "cursor mark `|` required" }
        val doc = docWithCursor.replace("<", "").replace("|", "")
        val selection = if (anchorMark >= 0) {
            // Strip-order: `<` precedes `|` in the raw string; once `<` is
            // removed the head index shifts left by one.
            val anchor = anchorMark
            val head = headMark - 1
            SelectionSpec.CursorSpec(DocPos(anchor), DocPos(head))
        } else {
            SelectionSpec.CursorSpec(DocPos(headMark))
        }
        return EditorSession(
            EditorState.create(
                EditorStateConfig(doc = doc.asDoc(), selection = selection)
            )
        )
    }

    private fun assertDoc(v: EditorSession, expected: String) {
        assertEquals(expected, v.state.doc.toString())
    }

    // ── insertNewlineKeepIndent ──

    @Test
    fun keepIndentCopiesLeadingWhitespace() {
        val v = view("    foo|")
        insertNewlineKeepIndent(v)
        assertDoc(v, "    foo\n    ")
    }

    @Test
    fun keepIndentWithZeroIndent() {
        val v = view("foo|")
        insertNewlineKeepIndent(v)
        assertDoc(v, "foo\n")
    }

    @Test
    fun keepIndentReplacesSelection() {
        val v = view("  <ab|")
        insertNewlineKeepIndent(v)
        assertDoc(v, "  \n  ")
    }

    // ── insertNewlineAndIndent bracket explosion ──

    @Test
    fun explodesEmptyBrackets() {
        val v = view("{|}")
        insertNewlineAndIndent(v)
        assertDoc(v, "{\n\n}")
    }

    @Test
    fun explodesBracketsPreservingOuterIndent() {
        val v = view("  {|}")
        insertNewlineAndIndent(v)
        assertDoc(v, "  {\n  \n  }")
    }

    @Test
    fun doesNotExplodeWhenNotBetweenBrackets() {
        val v = view("{x|}")
        insertNewlineAndIndent(v)
        assertDoc(v, "{x\n}")
    }

    @Test
    fun parenAndSquareBracketsAlsoExplode() {
        val vp = view("(|)")
        insertNewlineAndIndent(vp)
        assertDoc(vp, "(\n\n)")
        val vs = view("[|]")
        insertNewlineAndIndent(vs)
        assertDoc(vs, "[\n\n]")
    }
}
