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
package com.monkopedia.kodemirror.lang.javascript

import com.monkopedia.kodemirror.language.FoldRange
import com.monkopedia.kodemirror.language.codeFolding
import com.monkopedia.kodemirror.language.foldAll
import com.monkopedia.kodemirror.language.foldCode
import com.monkopedia.kodemirror.language.foldable
import com.monkopedia.kodemirror.language.foldedRanges
import com.monkopedia.kodemirror.language.indentRange
import com.monkopedia.kodemirror.language.indentUnit
import com.monkopedia.kodemirror.language.toggleFold
import com.monkopedia.kodemirror.language.unfoldCode
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.allowMultipleSelections
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Folding and indentation helpers from `:language` exercised with the real JavaScript grammar.
 * Expected values come from @codemirror/language 6.12.2 + @codemirror/lang-javascript run on the
 * same input (#450).
 */
class JsFoldTest {
    private fun state(doc: String, vararg extra: Extension): EditorState = EditorState.create(
        EditorStateConfig(
            doc = doc.asDoc(),
            extensions = ExtensionList(
                listOf(javascriptLanguage.extension, indentUnit.of(2)) + extra
            )
        )
    )

    private fun ranges(state: EditorState): List<Pair<Int, Int>> {
        val r = mutableListOf<Pair<Int, Int>>()
        foldedRanges(state).between(DocPos.ZERO, DocPos(state.doc.length)) { a, b, _ ->
            r += a to b
            null
        }
        return r
    }

    private val nested = "function f() {\n  if (x) {\n    y\n  }\n}\n"

    @Test
    fun foldableTakesTheOutermostRangeStartingOnTheLine() {
        assertEquals(
            FoldRange(DocPos(5), DocPos(15)),
            foldable(state("x = [{\n  a: 1\n}]\n"), DocPos.ZERO)
        )
    }

    @Test
    fun foldAllFoldsOnlyTopLevelRanges() {
        val view = EditorSession(state(nested, codeFolding()))
        assertTrue(foldAll(view))
        assertEquals(listOf(14 to 36), ranges(view.state))
    }

    @Test
    fun unfoldCodeUnfoldsAFoldStartingOnTheCursorLine() {
        val view = EditorSession(state(nested, codeFolding()))
        foldCode(view)
        assertEquals(listOf(14 to 36), ranges(view.state))
        view.dispatch(TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos.ZERO)))
        assertTrue(unfoldCode(view))
        assertEquals(emptyList(), ranges(view.state))
    }

    @Test
    fun foldCodeUsesEverySelectedLine() {
        val view = EditorSession(
            state("a\nfunction f() {\n  x\n}", codeFolding(), allowMultipleSelections.of(true))
        )
        val cursors = listOf(EditorSelection.cursor(DocPos(0)), EditorSelection.cursor(DocPos(3)))
        view.dispatch(
            TransactionSpec(
                selection = SelectionSpec.EditorSelectionSpec(EditorSelection.create(cursors, 0))
            )
        )
        assertTrue(foldCode(view))
        assertEquals(listOf(16 to 21), ranges(view.state))
    }

    @Test
    fun foldCodeEnablesCodeFoldingWhenMissing() {
        val view = EditorSession(state("function f() {\n  x\n}"))
        assertTrue(foldCode(view))
        assertEquals(listOf(14 to 19), ranges(view.state))
    }

    @Test
    fun toggleFoldOnAnInnerLineFoldsTheEnclosingBlock() {
        val view = EditorSession(state("function f() {\n  x\n  y\n}", codeFolding()))
        view.dispatch(TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos(18))))
        assertTrue(toggleFold(view))
        assertEquals(listOf(14 to 23), ranges(view.state))
    }

    @Test
    fun indentRangeEmptiesWhitespaceOnlyLines() {
        val s = state("if (a) {\nfoo\n    \n\tbar\n}")
        val changes = indentRange(s, DocPos.ZERO, DocPos(s.doc.length))
        assertEquals(
            "if (a) {\n  foo\n\n  bar\n}",
            s.update(TransactionSpec(changes = changes)).state.doc.toString()
        )
    }
}
