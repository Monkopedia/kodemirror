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

import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals

class LineCommandsTest {

    private fun createView(doc: String, cursor: Int = 0): EditorSession {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(cursor)
            )
        )
        return EditorSession(state)
    }

    @Test
    fun testMoveLineDown() {
        val view = createView("aaa\nbbb\nccc", cursor = 1)
        moveLineDown(view)
        assertEquals("bbb\naaa\nccc", view.state.doc.toString())
    }

    @Test
    fun testMoveLineUp() {
        val view = createView("aaa\nbbb\nccc", cursor = 5)
        moveLineUp(view)
        assertEquals("bbb\naaa\nccc", view.state.doc.toString())
    }

    @Test
    fun testMoveLineDownAtLastLine() {
        val view = createView("aaa\nbbb", cursor = 5)
        val result = moveLineDown(view)
        assertEquals(false, result)
        assertEquals("aaa\nbbb", view.state.doc.toString())
    }

    @Test
    fun testMoveLineUpAtFirstLine() {
        val view = createView("aaa\nbbb", cursor = 1)
        val result = moveLineUp(view)
        assertEquals(false, result)
        assertEquals("aaa\nbbb", view.state.doc.toString())
    }

    @Test
    fun testCopyLineDown() {
        val view = createView("aaa\nbbb\nccc", cursor = 1)
        copyLineDown(view)
        assertEquals("aaa\naaa\nbbb\nccc", view.state.doc.toString())
    }

    @Test
    fun testCopyLineUp() {
        val view = createView("aaa\nbbb\nccc", cursor = 5)
        copyLineUp(view)
        assertEquals("aaa\nbbb\nbbb\nccc", view.state.doc.toString())
    }
}
