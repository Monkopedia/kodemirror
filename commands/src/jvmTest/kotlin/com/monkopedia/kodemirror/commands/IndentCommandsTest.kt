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

import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals

class IndentCommandsTest {

    private fun createView(doc: String, anchor: Int, head: Int = anchor): EditorSession {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.EditorSelectionSpec(
                    EditorSelection.single(anchor, head)
                )
            )
        )
        return EditorSession(state)
    }

    @Test
    fun testIndentMore() {
        val view = createView("hello\nworld", 0, 11)
        indentMore(view)
        assertEquals("    hello\n    world", view.state.doc.toString())
    }

    @Test
    fun testIndentLess() {
        val view = createView("    hello\n    world", 0, 19)
        indentLess(view)
        assertEquals("hello\nworld", view.state.doc.toString())
    }

    @Test
    fun testIndentLessPartialSpaces() {
        val view = createView("  hello", 0, 7)
        indentLess(view)
        assertEquals("hello", view.state.doc.toString())
    }

    @Test
    fun testIndentMoreSingleLine() {
        val view = createView("hello", 2)
        indentMore(view)
        assertEquals("    hello", view.state.doc.toString())
    }

    @Test
    fun testIndentLessWithTab() {
        val view = createView("\thello", 0, 6)
        indentLess(view)
        assertEquals("hello", view.state.doc.toString())
    }
}
