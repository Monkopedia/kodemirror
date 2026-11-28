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

import androidx.compose.ui.geometry.Offset
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The ranges [handleRectangularDrag] builds, independent of fonts and layout (#312, #509).
 *
 * `posAt` is the identity on x, so a drag "from 2 to 24" means document offsets 2 and 24.
 * Every expected string comes from upstream `rectangleFor` in `@codemirror/view` 6.39.16, run
 * against real `@codemirror/state` with `getPos`'s `countColumn` (tabSize 4). Ranges are
 * `anchor-head`.
 */
class RectangularDragRangesTest {

    private fun rectangle(doc: String, from: Int, to: Int): String {
        val session = EditorSession(
            EditorState.create(
                EditorStateConfig(
                    doc = doc.asDoc(),
                    extensions = ExtensionList(
                        listOf(EditorState.allowMultipleSelections.of(true))
                    )
                )
            )
        )
        handleRectangularDrag(session, Offset(from.toFloat(), 0f), Offset(to.toFloat(), 0f)) {
            it.x.toInt()
        }
        return session.state.selection.ranges.joinToString(" ") {
            "${it.anchor.value}-${it.head.value}"
        }
    }

    private val square = "abcdefgh\nabcdefgh\nabcdefgh"

    @Test
    fun upLeft() = assertEquals("2-6 11-15 20-24", rectangle(square, 24, 2))

    @Test
    fun upRight() = assertEquals("2-6 11-15 20-24", rectangle(square, 20, 6))

    @Test
    fun downLeft() = assertEquals("2-6 11-15 20-24", rectangle(square, 6, 20))

    /** A line ending before the rectangle starts gets a cursor at its end. */
    @Test
    fun shorterMiddleLine() =
        assertEquals("4-6 11-11 16-18", rectangle("abcdefgh\nab\nabcdefgh", 4, 18))

    /** A line ending inside the rectangle is selected up to its end. */
    @Test
    fun partlyShorterLine() =
        assertEquals("3-6 12-14 18-21", rectangle("abcdefgh\nabcde\nabcdefgh", 3, 21))

    /** Columns, not offsets: the tab before `abc` spans columns 0 to 4 (#509). */
    @Test
    fun tabOnTheStartLine() = assertEquals("2-4 10-12", rectangle("\tabc\nabcdefgh", 2, 12))

    @Test
    fun tabOnTheEndLine() = assertEquals("5-6 11-12", rectangle("abcdefgh\n\tabc", 5, 12))
}
