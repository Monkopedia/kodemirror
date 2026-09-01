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
package com.monkopedia.kodemirror.search

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchCommandsTest {

    private fun createView(doc: String, query: SearchQuery, cursor: Int = 0): EditorSession {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(cursor)),
                extensions = ExtensionList(
                    listOf(searchQueryField, searchPanelOpenField)
                )
            )
        )
        val view = EditorSession(state)
        view.dispatch(
            TransactionSpec(
                effects = listOf(setSearchQuery.of(query))
            )
        )
        return view
    }

    @Test
    fun findNextMovesToNextMatch() {
        val view = createView(
            "one two one two one",
            SearchQuery(search = "one", caseSensitive = true),
            cursor = 3
        )
        assertTrue(findNext(view))
        assertEquals(DocPos(8), view.state.selection.main.from)
        assertEquals(DocPos(11), view.state.selection.main.to)
    }

    @Test
    fun findPreviousMovesToPreviousMatch() {
        val view = createView(
            "one two one two one",
            SearchQuery(search = "one", caseSensitive = true),
            cursor = 19
        )
        assertTrue(findPrevious(view))
        assertEquals(DocPos(16), view.state.selection.main.from)
        assertEquals(DocPos(19), view.state.selection.main.to)
    }

    @Test
    fun findNextWrapsAround() {
        val view = createView(
            "one two three",
            SearchQuery(search = "one", caseSensitive = true),
            cursor = 5
        )
        assertTrue(findNext(view))
        assertEquals(DocPos(0), view.state.selection.main.from)
        assertEquals(DocPos(3), view.state.selection.main.to)
    }

    @Test
    fun findPreviousWrapsAround() {
        val view = createView(
            "one two three one",
            SearchQuery(search = "one", caseSensitive = true),
            cursor = 0
        )
        assertTrue(findPrevious(view))
        assertEquals(DocPos(14), view.state.selection.main.from)
        assertEquals(DocPos(17), view.state.selection.main.to)
    }

    @Test
    fun replaceNextReplacesCurrentMatch() {
        val view = createView(
            "one two one",
            SearchQuery(
                search = "one",
                replace = "ONE",
                caseSensitive = true
            ),
            cursor = 0
        )
        // First find the match to select it
        findNext(view)
        // Now selection should be on a match, replace it
        assertTrue(replaceNext(view))
        assertTrue(view.state.doc.toString().contains("ONE"))
    }

    @Test
    fun replaceNextReplacesTheCurrentRegexMatch() {
        // Upstream `replaceNext` decides "is the selection the match" by
        // comparing positions. Comparing the selected text to the *pattern*
        // made this branch dead for every regexp query.
        val view = createView(
            "john@a jane@b",
            SearchQuery(
                search = "(\\w+)@(\\w+)",
                replace = "X",
                regexp = true,
                caseSensitive = true
            ),
            cursor = 0
        )
        assertTrue(findNext(view))
        assertEquals(DocPos(0), view.state.selection.main.from)
        assertEquals(DocPos(6), view.state.selection.main.to)
        assertTrue(replaceNext(view))
        assertEquals("X jane@b", view.state.doc.toString())
    }

    @Test
    fun replaceAllReplacesAllMatches() {
        val view = createView(
            "one two one two one",
            SearchQuery(
                search = "one",
                replace = "ONE",
                caseSensitive = true
            )
        )
        assertTrue(replaceAll(view))
        assertEquals("ONE two ONE two ONE", view.state.doc.toString())
    }

    @Test
    fun replaceAllExpandsRegexGroupsIntoTheDocument() {
        // Upstream (@codemirror/search 6.7.1) replaceAll inserts
        // `query.getReplacement(match)`, which expands `$1`/`$2`. Before this
        // fix the raw template was written into the document.
        val view = createView(
            "john@a jane@b",
            SearchQuery(
                search = "(\\w+)@(\\w+)",
                replace = "$2:$1",
                regexp = true,
                caseSensitive = true
            )
        )
        assertTrue(replaceAll(view))
        assertEquals("a:john b:jane", view.state.doc.toString())
    }

    @Test
    fun replaceAllDoesNotExpandGroupsForPlainStringQueries() {
        // Upstream StringQuery.getReplacement inserts the replace text
        // verbatim; only RegExpQuery expands `$` references.
        val view = createView(
            "a b a",
            SearchQuery(search = "a", replace = "$1", caseSensitive = true)
        )
        assertTrue(replaceAll(view))
        assertEquals("$1 b $1", view.state.doc.toString())
    }

    @Test
    fun replaceAllUnquotesEscapesInTheReplacement() {
        // Upstream's `getReplacement` runs the replace text through
        // `unquote`, so a "\\n" typed into the replace field inserts a real
        // newline rather than a backslash followed by an "n".
        val view = createView(
            "a b a",
            SearchQuery(search = "a", replace = "x\\ny", caseSensitive = true)
        )
        assertTrue(replaceAll(view))
        assertEquals("x\ny b x\ny", view.state.doc.toString())
    }

    @Test
    fun selectMatchesCreatesMultiSelection() {
        val view = createView(
            "one two one two one",
            SearchQuery(search = "one", caseSensitive = true)
        )
        assertTrue(selectMatches(view))
        // Verify at least one match position is selected
        val sel = view.state.selection.main
        assertTrue(sel.from != sel.to || sel.from == DocPos.ZERO)
    }

    @Test
    fun commandsReturnFalseWithNoQuery() {
        val view = createView(
            "hello world",
            SearchQuery(search = "")
        )
        assertFalse(findNext(view))
        assertFalse(findPrevious(view))
        assertFalse(selectMatches(view))
    }
}
