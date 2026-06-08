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

import com.monkopedia.kodemirror.language.indentService
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `insertNewlineAndIndent` consults the language indent service (via
 * `getIndentation`) when one is registered, instead of the leading-whitespace
 * fallback exercised by [NewlineCommandsTest]. This is the language-aware
 * branch upstream's tests drive with `javascriptLanguage`; here we register a
 * minimal [indentService] that always asks for a fixed column count, so the
 * test needs no real language module (#116).
 */
class IndentServiceNewlineTest {

    /** A view whose indent service always requests [columns] of indentation. */
    private fun viewWithFixedIndent(
        doc: String,
        cursor: Int,
        columns: Int
    ): EditorSession {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(cursor)),
                extensions = ExtensionList(
                    listOf(indentService.of { _, _ -> columns })
                )
            )
        )
        return EditorSession(state)
    }

    @Test
    fun plainNewlineUsesIndentService() {
        // The service asks for 4 columns; the new line is indented to 4 spaces
        // rather than copying the (empty) leading whitespace of "foo".
        val v = viewWithFixedIndent("foo", cursor = 3, columns = 4)
        insertNewlineAndIndent(v)
        assertEquals("foo\n    ", v.state.doc.toString())
    }

    @Test
    fun explosionMiddleLineUsesIndentService() {
        // Between brackets: the closing bracket keeps the line's (empty) leading
        // whitespace, but the middle line is indented by the service's 4 columns.
        val v = viewWithFixedIndent("{}", cursor = 1, columns = 4)
        insertNewlineAndIndent(v)
        assertEquals("{\n    \n}", v.state.doc.toString())
    }

    @Test
    fun cursorLandsOnIndentedMiddleLine() {
        val v = viewWithFixedIndent("{}", cursor = 1, columns = 4)
        insertNewlineAndIndent(v)
        // After "{" + "\n" (1) + "    " (4) → cursor at column offset 6.
        assertEquals(6, v.state.selection.main.head.value)
    }
}
