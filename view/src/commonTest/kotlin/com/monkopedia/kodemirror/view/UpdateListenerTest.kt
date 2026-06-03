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

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.extensionListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test for #103: the [EditorSession.updateListener] facet (and the
 * [onChange]/[onSelection] extensions built on it) must fire on every
 * dispatched transaction. Previously [EditorSessionImpl.dispatchTransaction]
 * never read the facet, so all three were silently dead.
 */
class UpdateListenerTest {

    @Test
    fun updateListenerFiresOnDocChange() {
        val rawUpdates = mutableListOf<ViewUpdate>()
        val changedTexts = mutableListOf<String>()

        val state = EditorState.create(
            EditorStateConfig(
                doc = "hello".asDoc(),
                extensions = extensionListOf(
                    EditorSession.updateListener.of { update -> rawUpdates.add(update) },
                    onChange { text -> changedTexts.add(text) }
                )
            )
        )
        val session = EditorSession(state)

        session.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    DocPos(5),
                    DocPos(5),
                    InsertContent.StringContent(" world")
                )
            )
        )

        // The raw facet listener fired with a doc-changed update.
        assertEquals(1, rawUpdates.size)
        assertTrue(rawUpdates.single().docChanged)
        // onChange fired with the new document text.
        assertEquals(listOf("hello world"), changedTexts)
    }

    @Test
    fun onSelectionFiresOnSelectionOnlyChangeButOnChangeDoesNot() {
        val rawUpdates = mutableListOf<ViewUpdate>()
        val changedTexts = mutableListOf<String>()
        val selections = mutableListOf<EditorSelection>()

        val state = EditorState.create(
            EditorStateConfig(
                doc = "hello".asDoc(),
                extensions = extensionListOf(
                    EditorSession.updateListener.of { update -> rawUpdates.add(update) },
                    onChange { text -> changedTexts.add(text) },
                    onSelection { selection -> selections.add(selection) }
                )
            )
        )
        val session = EditorSession(state)

        // Selection-only transaction: move the cursor, no document change.
        session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(DocPos(2))
            )
        )

        // The raw facet listener fired.
        assertEquals(1, rawUpdates.size)
        assertTrue(rawUpdates.single().selectionSet)
        assertTrue(!rawUpdates.single().docChanged)
        // onSelection fired with the moved selection; onChange did NOT.
        assertEquals(1, selections.size)
        assertEquals(DocPos(2), selections.single().main.head)
        assertEquals(emptyList(), changedTexts)
    }

    @Test
    fun noListenersRegisteredIsNoOp() {
        // Reading the unset facet must yield an empty list (no throw).
        val state = EditorState.create(EditorStateConfig(doc = "hi".asDoc()))
        val session = EditorSession(state)
        session.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    DocPos(2),
                    DocPos(2),
                    InsertContent.StringContent("!")
                )
            )
        )
        assertEquals("hi!", session.state.doc.toString())
    }
}
