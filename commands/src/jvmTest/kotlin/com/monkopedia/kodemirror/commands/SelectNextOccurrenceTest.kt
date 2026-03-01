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
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.allowMultipleSelections
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectNextOccurrenceTest {

    private fun createView(doc: String, cursor: Int = 0): EditorView {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(cursor),
                extensions = ExtensionList(
                    listOf(allowMultipleSelections.of(true))
                )
            )
        )
        return EditorView(state)
    }

    private fun createViewWithSelection(doc: String, anchor: Int, head: Int): EditorView {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.EditorSelectionSpec(
                    EditorSelection.single(anchor, head)
                ),
                extensions = ExtensionList(
                    listOf(allowMultipleSelections.of(true))
                )
            )
        )
        return EditorView(state)
    }

    @Test
    fun selectsWordAtCursor() {
        val view = createView("hello world", cursor = 2)
        assertTrue(selectNextOccurrence(view))
        val sel = view.state.selection.main
        assertEquals(0, sel.from)
        assertEquals(5, sel.to)
    }

    @Test
    fun selectsNextOccurrence() {
        // "foo bar foo baz foo"
        val view = createViewWithSelection("foo bar foo baz foo", 0, 3)
        assertTrue(selectNextOccurrence(view))
        assertEquals(2, view.state.selection.ranges.size)
        val added = view.state.selection.ranges[1]
        assertEquals(8, added.from)
        assertEquals(11, added.to)
    }

    @Test
    fun wrapsAroundDocument() {
        // Select the last "foo", next should wrap to the first
        val view = createViewWithSelection("foo bar foo", 8, 11)
        assertTrue(selectNextOccurrence(view))
        assertEquals(2, view.state.selection.ranges.size)
        val wrapped = view.state.selection.ranges.find { it.from == 0 }
        assertEquals(0, wrapped?.from)
        assertEquals(3, wrapped?.to)
    }

    @Test
    fun skipsAlreadySelectedRanges() {
        // Two "foo" already selected, third invocation finds the last one
        val doc = "foo bar foo baz foo"
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.EditorSelectionSpec(
                    EditorSelection.create(
                        listOf(
                            EditorSelection.range(0, 3),
                            EditorSelection.range(8, 11)
                        ),
                        1
                    )
                ),
                extensions = ExtensionList(
                    listOf(allowMultipleSelections.of(true))
                )
            )
        )
        val view = EditorView(state)
        assertTrue(selectNextOccurrence(view))
        assertEquals(3, view.state.selection.ranges.size)
        val last = view.state.selection.ranges[2]
        assertEquals(16, last.from)
        assertEquals(19, last.to)
    }

    @Test
    fun handlesNoMoreOccurrences() {
        // Only one "unique" in doc, first call selects it, second is no-op
        val view = createView("unique word", cursor = 2)
        assertTrue(selectNextOccurrence(view))
        // Now "unique" is selected; try to find next
        assertFalse(selectNextOccurrence(view))
    }

    @Test
    fun returnsFalseWithNoWordAtCursor() {
        // Cursor between two spaces — no word in either direction
        val view = createView("  ", cursor = 1)
        assertFalse(selectNextOccurrence(view))
    }

    @Test
    fun selectsExactTextMatch() {
        // Manually select "ar", find next exact "ar"
        val view = createViewWithSelection("bar bard ar art", 1, 3)
        assertTrue(selectNextOccurrence(view))
        val ranges = view.state.selection.ranges
        assertEquals(2, ranges.size)
        // Should find the standalone "ar" at 5 (inside "bard") or at 9
        // "bar bard ar art" -> positions: b=0,a=1,r=2, =3,b=4,a=5,r=6,d=7, =8,a=9,r=10, =11,a=12,r=13,t=14
        // "ar" at 1-3 (already selected), next "ar" at 5-7 (in "bard")
        assertEquals(5, ranges[1].from)
        assertEquals(7, ranges[1].to)
    }

    @Test
    fun multipleInvocationsAddAll() {
        val doc = "aa bb aa cc aa"
        val view = createView(doc, cursor = 0)
        // First: select word "aa"
        assertTrue(selectNextOccurrence(view))
        assertEquals(1, view.state.selection.ranges.size)
        // Second: add next "aa"
        assertTrue(selectNextOccurrence(view))
        assertEquals(2, view.state.selection.ranges.size)
        // Third: add last "aa"
        assertTrue(selectNextOccurrence(view))
        assertEquals(3, view.state.selection.ranges.size)
        // Fourth: no more
        assertFalse(selectNextOccurrence(view))
    }
}
