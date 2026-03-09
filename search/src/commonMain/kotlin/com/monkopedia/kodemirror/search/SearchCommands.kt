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

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.endPos
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding

/** Open the search panel. */
val openSearchPanel: (EditorSession) -> Boolean = { view ->
    view.dispatch(
        TransactionSpec(
            effects = listOf(toggleSearchPanel.of(true))
        )
    )
    true
}

/** Close the search panel. */
val closeSearchPanel: (EditorSession) -> Boolean = { view ->
    if (searchPanelOpen(view.state)) {
        view.dispatch(
            TransactionSpec(
                effects = listOf(toggleSearchPanel.of(false))
            )
        )
        true
    } else {
        false
    }
}

/** Move the selection to the next match. */
val findNext: (EditorSession) -> Boolean = { view ->
    findMatch(view, forward = true)
}

/** Move the selection to the previous match. */
val findPrevious: (EditorSession) -> Boolean = { view ->
    findMatch(view, forward = false)
}

private fun findMatch(view: EditorSession, forward: Boolean): Boolean {
    val query = getSearchQuery(view.state)
    if (!query.valid) return false

    val state = view.state
    val from = if (forward) state.selection.main.to else DocPos.ZERO
    val to = if (forward) state.doc.endPos else state.selection.main.from
    val cursor = query.getCursor(state, from, to)

    val match = if (forward) {
        if (cursor.hasNext()) cursor.next() else null
    } else {
        // For backward search, collect all matches and take the last one
        var last: SearchMatch? = null
        for (m in cursor) {
            last = m
        }
        last
    }

    // Wrap around if no match found
    val wrappedMatch = match ?: run {
        val wrapFrom = if (forward) DocPos.ZERO else state.selection.main.from
        val wrapTo = if (forward) state.selection.main.to else state.doc.endPos
        val wrapCursor = query.getCursor(state, wrapFrom, wrapTo)
        if (forward) {
            if (wrapCursor.hasNext()) wrapCursor.next() else null
        } else {
            var last: SearchMatch? = null
            for (m in wrapCursor) {
                last = m
            }
            last
        }
    }

    if (wrappedMatch != null) {
        view.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(
                    wrappedMatch.from,
                    wrappedMatch.to
                ),
                scrollIntoView = true,
                userEvent = "select.search"
            )
        )
        return true
    }
    return false
}

/** Replace the current match (if any) and move to the next one. */
val replaceNext: (EditorSession) -> Boolean = { view ->
    val state = view.state
    if (state.readOnly) {
        false
    } else {
        val query = getSearchQuery(state)
        if (!query.valid) {
            false
        } else {
            val sel = state.selection.main
            val replacement = query.replace
            // Check if current selection matches the query
            val selText = state.doc.sliceString(sel.from, sel.to)
            val matches = if (query.caseSensitive) {
                selText == query.search
            } else {
                selText.equals(query.search, ignoreCase = true)
            }
            if (matches && !sel.empty) {
                view.dispatch(
                    TransactionSpec(
                        changes = ChangeSpec.Single(
                            sel.from,
                            sel.to,
                            InsertContent.StringContent(replacement)
                        ),
                        userEvent = "input.replace"
                    )
                )
                findMatch(view, forward = true)
                true
            } else {
                findMatch(view, forward = true)
            }
        }
    }
}

/** Replace all matches in the document. */
val replaceAll: (EditorSession) -> Boolean = { view ->
    val state = view.state
    if (state.readOnly) {
        false
    } else {
        val query = getSearchQuery(state)
        if (!query.valid) {
            false
        } else {
            val changes = mutableListOf<ChangeSpec>()
            val cursor = query.getCursor(state)
            for (match in cursor) {
                changes.add(
                    ChangeSpec.Single(
                        match.from,
                        match.to,
                        InsertContent.StringContent(query.replace)
                    )
                )
            }
            if (changes.isNotEmpty()) {
                view.dispatch(
                    TransactionSpec(
                        changes = ChangeSpec.Multi(changes),
                        userEvent = "input.replace.all"
                    )
                )
            }
            true
        }
    }
}

/** Select all matches as a multi-selection. */
val selectMatches: (EditorSession) -> Boolean = { view ->
    val query = getSearchQuery(view.state)
    if (!query.valid) {
        false
    } else {
        val state = view.state
        val ranges = mutableListOf<com.monkopedia.kodemirror.state.SelectionRange>()
        val cursor = query.getCursor(state)
        for (match in cursor) {
            ranges.add(EditorSelection.range(match.from, match.to))
        }
        if (ranges.isNotEmpty()) {
            view.dispatch(
                TransactionSpec(
                    selection = SelectionSpec.EditorSelectionSpec(
                        EditorSelection.create(ranges)
                    ),
                    userEvent = "select.search.matches"
                )
            )
            true
        } else {
            false
        }
    }
}

/** Select all instances of the currently selected text. */
val selectSelectionMatches: (EditorSession) -> Boolean = { view ->
    val state = view.state
    val sel = state.selection.main
    val selectedText = state.doc.sliceString(sel.from, sel.to)
    if (selectedText.isEmpty()) {
        false
    } else {
        val ranges = mutableListOf<com.monkopedia.kodemirror.state.SelectionRange>()
        val docText = state.doc.toString()
        var searchFrom = 0
        while (true) {
            val idx = docText.indexOf(selectedText, searchFrom)
            if (idx < 0) break
            ranges.add(
                EditorSelection.range(
                    DocPos(idx),
                    DocPos(idx + selectedText.length)
                )
            )
            searchFrom = idx + selectedText.length
        }
        if (ranges.size > 1) {
            view.dispatch(
                TransactionSpec(
                    selection = SelectionSpec.EditorSelectionSpec(
                        EditorSelection.create(ranges)
                    ),
                    userEvent = "select.search.selectionMatches"
                )
            )
            true
        } else {
            false
        }
    }
}

/** Default search keymap bindings. */
val searchKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Mod-f", run = openSearchPanel),
    KeyBinding(key = "Escape", run = closeSearchPanel),
    KeyBinding(key = "Mod-g", run = findNext),
    KeyBinding(key = "Mod-Shift-g", run = findPrevious),
    KeyBinding(key = "Mod-Shift-l", run = selectMatches)
)
