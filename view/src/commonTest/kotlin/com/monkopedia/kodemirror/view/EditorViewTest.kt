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

import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorViewTest {

    private fun makeState(doc: String = ""): EditorState =
        EditorState.create(EditorStateConfig(doc = doc.asDoc()))

    @Test
    fun viewCreation() {
        val state = makeState("hello world")
        val view = EditorView(state)
        assertEquals(state, view.state)
    }

    @Test
    fun dispatchTransaction() {
        val state = makeState("hello")
        var lastTr: com.monkopedia.kodemirror.state.Transaction? = null
        val view = EditorView(state) { lastTr = it }

        view.dispatch(
            TransactionSpec(
                changes = com.monkopedia.kodemirror.state.ChangeSpec.Single(
                    5,
                    5,
                    com.monkopedia.kodemirror.state.InsertContent.StringContent(" world")
                )
            )
        )

        assertNotNull(lastTr)
        assertEquals("hello world", view.state.doc.toString())
    }

    @Test
    fun onUpdateCalledOnDispatch() {
        val state = makeState()
        var callCount = 0
        val view = EditorView(state) { callCount++ }
        view.dispatch(TransactionSpec())
        assertEquals(1, callCount)
    }

    @Test
    fun editableDefault() {
        val state = makeState()
        val view = EditorView(state)
        assertTrue(view.editable)
    }

    @Test
    fun editableFacet() {
        val state = EditorState.create(
            EditorStateConfig(extensions = editable.of(false))
        )
        val view = EditorView(state)
        assertTrue(!view.editable)
    }

    @Test
    fun coordsAtPosWithoutCache() {
        val state = makeState("hello")
        val view = EditorView(state)
        // No cache wired up → null
        assertNull(view.coordsAtPos(0))
    }

    @Test
    fun posAtCoordsWithoutCache() {
        val state = makeState("hello")
        val view = EditorView(state)
        assertNull(view.posAtCoords(0f, 0f))
    }

    @Test
    fun pluginNullWithoutHost() {
        val state = makeState()
        val view = EditorView(state)
        val plugin = ViewPlugin.define<PluginValue>(create = { _ -> object : PluginValue {} })
        assertNull(view.plugin(plugin))
    }

    @Test
    fun viewUpdateFields() {
        val state = makeState("hello")
        val view = EditorView(state)
        val tr = state.update(TransactionSpec())
        val update = ViewUpdate(view, tr.state, listOf(tr))

        assertEquals(state, update.startState)
        assertEquals(tr.state, update.state)
        assertEquals(false, update.docChanged)
    }

    @Test
    fun viewUpdateDocChanged() {
        val state = makeState("hi")
        val view = EditorView(state)
        val tr = state.update(
            TransactionSpec(
                changes = com.monkopedia.kodemirror.state.ChangeSpec.Single(
                    2,
                    2,
                    com.monkopedia.kodemirror.state.InsertContent.StringContent("!")
                )
            )
        )
        val update = ViewUpdate(view, tr.state, listOf(tr))
        assertTrue(update.docChanged)
    }

    @Test
    fun rectData() {
        val rect = Rect(0f, 0f, 100f, 20f)
        assertEquals(100f, rect.width)
        assertEquals(20f, rect.height)
    }
}
