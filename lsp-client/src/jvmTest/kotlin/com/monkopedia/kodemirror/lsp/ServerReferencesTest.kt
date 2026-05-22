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
package com.monkopedia.kodemirror.lsp

import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.lsp.Location
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.LanguageServer
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ServerReferencesTest {

    private fun pos(line: Int, char: Int): Position =
        Position(line = line.toUInt(), character = char.toUInt())

    private fun range(startLine: Int, startChar: Int, endLine: Int, endChar: Int): Range =
        Range(start = pos(startLine, startChar), end = pos(endLine, endChar))

    private fun loc(uri: String, startChar: Int, endChar: Int, line: Int = 0): Location =
        Location(uri = uri, range = range(line, startChar, line, endChar))

    private fun stubServer(): LanguageServer = Proxy.newProxyInstance(
        LanguageServer::class.java.classLoader,
        arrayOf(LanguageServer::class.java)
    ) { _, method, _ ->
        error("stub LanguageServer.${method.name} should not be called in this test")
    } as LanguageServer

    // --- response -> reference locations ---

    @Test
    fun mapsTypedLocationListPreservingOrder() {
        val response = listOf(
            loc("file:///a.kt", 0, 3),
            loc("file:///b.kt", 4, 7)
        )
        val refs = toReferenceLocations(response)
        assertEquals(2, refs.size)
        assertEquals("file:///a.kt", refs[0].uri)
        assertEquals(0u, refs[0].range.start.character)
        assertEquals("file:///b.kt", refs[1].uri)
    }

    @Test
    fun mapsEmptyResponseToEmptyList() {
        assertTrue(toReferenceLocations(emptyList()).isEmpty())
    }

    // --- findCommonPrefix (ports upstream) ---

    @Test
    fun commonPrefixTrimsToLastSlash() {
        val prefix = findCommonPrefix(
            listOf("file:///proj/src/a.kt", "file:///proj/src/b.kt")
        )
        // shared up to "file:///proj/src/" -> length 17
        assertEquals("file:///proj/src/".length, prefix)
        assertEquals(
            "a.kt",
            "file:///proj/src/a.kt".substring(prefix)
        )
    }

    @Test
    fun commonPrefixDivergingDirectories() {
        val prefix = findCommonPrefix(
            listOf("file:///proj/src/a.kt", "file:///proj/test/b.kt")
        )
        assertEquals("file:///proj/".length, prefix)
    }

    @Test
    fun commonPrefixSingleUriIsWholeDirectory() {
        val prefix = findCommonPrefix(listOf("file:///proj/src/a.kt"))
        assertEquals("file:///proj/src/".length, prefix)
    }

    @Test
    fun commonPrefixEmptyListIsZero() {
        assertEquals(0, findCommonPrefix(emptyList()))
    }

    // --- buildReferenceEntries: preview + line numbers ---

    @Test
    fun buildsPreviewWithMatchedSpanForOpenFile() {
        val doc = Text.of(listOf("val foo = 1", "println(foo)"))
        // "foo" on line 1 (0-based), chars 8..11
        val locs = listOf(ReferenceLocation("file:///a.kt", range(1, 8, 1, 11)))
        val entries = buildReferenceEntries(locs) { if (it == "file:///a.kt") doc else null }
        val entry = entries.single()
        assertEquals("a.kt", entry.fileName)
        assertEquals(2, entry.lineNumber) // 1-based
        assertEquals("println(", entry.before)
        assertEquals("foo", entry.matched)
        assertEquals(")", entry.after)
    }

    @Test
    fun buildsEntryWithoutPreviewWhenFileNotOpen() {
        val locs = listOf(ReferenceLocation("file:///closed.kt", range(0, 0, 0, 4)))
        val entries = buildReferenceEntries(locs) { null }
        val entry = entries.single()
        assertNull(entry.lineNumber)
        assertEquals("", entry.matched)
        assertEquals("", entry.before)
        assertEquals("", entry.after)
    }

    // --- navigation target resolution (same-file) ---

    @Test
    fun showReferenceMovesSelectionForSameFile() {
        val client = LSPClient(stubServer())
        val state = EditorState.create(
            doc = "val foo = 1\nprintln(foo)\n",
            extensions = findReferencesExtension(client, "file:///a.kt", keymap = false)
        )
        val session = EditorSession(state)
        client.workspace.openFile("file:///a.kt", "kotlin", session)

        val entry = buildReferenceEntries(
            listOf(ReferenceLocation("file:///a.kt", range(1, 8, 1, 11)))
        ) { session.state.doc }.single()

        val binding = state.facet(referenceServer)!!
        showReference(binding, entry)
        // "val foo = 1\n" = 12, +8 = 20
        assertEquals(20, session.state.selection.main.head.value)
    }

    @Test
    fun showReferenceNoOpsForUnopenedCrossFile() {
        val client = LSPClient(stubServer())
        val state = EditorState.create(
            doc = "x",
            extensions = findReferencesExtension(client, "file:///a.kt", keymap = false)
        )
        val session = EditorSession(state)
        client.workspace.openFile("file:///a.kt", "kotlin", session)
        val binding = state.facet(referenceServer)!!
        val entry = ReferenceEntry(
            ReferenceLocation("file:///other.kt", range(0, 0, 0, 1)),
            "other.kt", 1, "", "", ""
        )
        // Default single-file workspace cannot display an unopened file: no crash, no move.
        showReference(binding, entry)
        assertEquals(0, session.state.selection.main.head.value)
    }

    // --- panel open/close ---

    @Test
    fun closeReferencePanelReturnsFalseWhenClosed() {
        val client = LSPClient(stubServer())
        val state = EditorState.create(
            doc = "x",
            extensions = findReferencesExtension(client, "file:///a.kt", keymap = false)
        )
        val session = EditorSession(state)
        assertNull(session.state.field(referencePanel, require = false))
        assertFalse(closeReferencePanel(session))
    }

    @Test
    fun setReferencePanelEffectOpensAndCloses() {
        val client = LSPClient(stubServer())
        val state = EditorState.create(
            doc = "x",
            extensions = findReferencesExtension(client, "file:///a.kt", keymap = false)
        )
        val session = EditorSession(state)
        session.dispatch(
            com.monkopedia.kodemirror.state.TransactionSpec(
                effects = listOf(
                    setReferencePanel.of(
                        ReferencePanelState(listOf(ReferenceLocation("file:///a.kt", range(0, 0, 0, 1))))
                    )
                )
            )
        )
        assertEquals(1, session.state.field(referencePanel, require = false)?.locations?.size)
        assertTrue(closeReferencePanel(session))
        assertNull(session.state.field(referencePanel, require = false))
    }

    // --- capability gating + no-binding ---

    @Test
    fun findReferencesReturnsFalseWithoutBinding() {
        val state = EditorState.create(doc = "x")
        val session = EditorSession(state)
        assertFalse(findReferences(session))
    }

    @Test
    fun editorWithoutFindReferencesHasNullBinding() {
        val state = EditorState.create(doc = "x")
        assertNull(state.facet(referenceServer))
    }

    // --- multi-instance: per-editor binding ---

    @Test
    fun twoEditorsKeepIndependentReferenceBindings() {
        val clientA = LSPClient(stubServer())
        val clientB = LSPClient(stubServer())

        val stateA = EditorState.create(
            doc = "a",
            extensions = findReferencesExtension(clientA, "file:///a.kt", keymap = false)
        )
        val stateB = EditorState.create(
            doc = "b",
            extensions = findReferencesExtension(clientB, "file:///b.kt", keymap = false)
        )

        val bindingA = stateA.facet(referenceServer)
        val bindingB = stateB.facet(referenceServer)

        assertEquals("file:///a.kt", bindingA?.uri)
        assertSame(clientA, bindingA?.client)
        assertEquals("file:///b.kt", bindingB?.uri)
        assertSame(clientB, bindingB?.client)
        assertNotSame(bindingA, bindingB)
        assertSame(clientA, stateA.facet(referenceServer)?.client)
    }

    // --- keymap ---

    @Test
    fun keymapBindsShiftF12AndEscape() {
        assertEquals(2, findReferencesKeymap.size)
        val find = findReferencesKeymap[0]
        assertEquals("Shift-F12", find.key)
        assertTrue(find.preventDefault)
        val close = findReferencesKeymap[1]
        assertEquals("Escape", close.key)
    }
}
