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
package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.CharCategory
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CursorMotionTest {

    private fun state(doc: String, pos: Int = 0): EditorState = EditorState.create(
        EditorStateConfig(
            doc = doc.asDoc(),
            selection = com.monkopedia.kodemirror.state.SelectionSpec.CursorSpec(DocPos(pos))
        )
    )

    @Test
    fun groupAtWordChar() {
        val s = state("hello world")
        assertEquals(CharCategory.Word, groupAt(s, DocPos.ZERO))
        assertEquals(CharCategory.Word, groupAt(s, DocPos(4)))
    }

    @Test
    fun groupAtSpace() {
        val s = state("hello world")
        assertEquals(CharCategory.Space, groupAt(s, DocPos(5)))
    }

    @Test
    fun groupAtPunctuation() {
        val s = state("a,b")
        assertEquals(CharCategory.Other, groupAt(s, DocPos(1)))
    }

    @Test
    fun moveByCharForward() {
        val s = state("hello", 0)
        val sel = EditorSelection.cursor(DocPos.ZERO)
        val moved = moveByChar(s, sel, forward = true)
        assertEquals(DocPos(1), moved.head)
        assertTrue(moved.empty) // cursor, not range
    }

    @Test
    fun moveByCharBackward() {
        val s = state("hello", 3)
        val sel = EditorSelection.cursor(DocPos(3))
        val moved = moveByChar(s, sel, forward = false)
        assertEquals(DocPos(2), moved.head)
    }

    @Test
    fun moveByCharAtStart() {
        val s = state("hello", 0)
        val sel = EditorSelection.cursor(DocPos.ZERO)
        val moved = moveByChar(s, sel, forward = false)
        assertEquals(DocPos.ZERO, moved.head)
    }

    @Test
    fun moveByCharAtEnd() {
        val s = state("hello", 5)
        val sel = EditorSelection.cursor(DocPos(5))
        val moved = moveByChar(s, sel, forward = true)
        assertEquals(DocPos(5), moved.head)
    }

    @Test
    fun moveByCharExtend() {
        val s = state("hello")
        val sel = EditorSelection.cursor(DocPos(1))
        val moved = moveByChar(s, sel, forward = true, extend = true)
        assertEquals(DocPos(1), moved.anchor)
        assertEquals(DocPos(2), moved.head)
        assertFalse(moved.empty)
    }

    @Test
    fun moveByGroupWord() {
        val s = state("hello world")
        val sel = EditorSelection.cursor(DocPos.ZERO)
        val moved = moveByGroup(s, sel, forward = true)
        // Should move past the entire word "hello"
        assertEquals(DocPos(5), moved.head)
    }

    @Test
    fun moveByGroupSpaces() {
        val s = state("hello world")
        val sel = EditorSelection.cursor(DocPos(5))
        val moved = moveByGroup(s, sel, forward = true)
        // Should move past the space
        assertEquals(DocPos(6), moved.head)
    }

    @Test
    fun moveByGroupBackward() {
        val s = state("hello world")
        val sel = EditorSelection.cursor(DocPos(11))
        val moved = moveByGroup(s, sel, forward = false)
        // Should move back past "world"
        assertEquals(DocPos(6), moved.head)
    }

    @Test
    fun moveByGroupExtend() {
        val s = state("hello world")
        val sel = EditorSelection.cursor(DocPos.ZERO)
        val moved = moveByGroup(s, sel, forward = true, extend = true)
        assertEquals(DocPos.ZERO, moved.anchor)
        assertEquals(DocPos(5), moved.head)
    }
}

private fun assertFalse(condition: Boolean) = kotlin.test.assertFalse(condition)
