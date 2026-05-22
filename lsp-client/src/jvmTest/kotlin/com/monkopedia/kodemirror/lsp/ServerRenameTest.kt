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

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.lsp.LanguageServer
import com.monkopedia.lsp.OptionalVersionedTextDocumentIdentifier
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.TextDocumentEdit
import com.monkopedia.lsp.TextEdit
import com.monkopedia.lsp.WorkspaceEdit
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ServerRenameTest {

    private fun pos(line: Int, char: Int): Position =
        Position(line = line.toUInt(), character = char.toUInt())

    private fun range(startLine: Int, startChar: Int, endLine: Int, endChar: Int): Range =
        Range(start = pos(startLine, startChar), end = pos(endLine, endChar))

    private fun edit(startLine: Int, startChar: Int, endLine: Int, endChar: Int, newText: String) =
        TextEdit(range = range(startLine, startChar, endLine, endChar), newText = newText)

    private fun stubServer(): LanguageServer = Proxy.newProxyInstance(
        LanguageServer::class.java.classLoader,
        arrayOf(LanguageServer::class.java)
    ) { _, method, _ ->
        error("stub LanguageServer.${method.name} should not be called in this test")
    } as LanguageServer

    // --- collectFileEdits: changes vs documentChanges ---

    @Test
    fun collectFileEditsPrefersChangesMap() {
        val e = edit(0, 0, 0, 3, "bar")
        val workspaceEdit = WorkspaceEdit(changes = mapOf("file:///a.kt" to listOf(e)))
        val collected = collectFileEdits(workspaceEdit)
        assertEquals(setOf("file:///a.kt"), collected.keys)
        assertEquals(listOf(e), collected["file:///a.kt"])
    }

    @Test
    fun collectFileEditsFlattensDocumentChanges() {
        val e1 = edit(0, 0, 0, 3, "bar")
        val e2 = edit(1, 0, 1, 3, "bar")
        val workspaceEdit = WorkspaceEdit(
            documentChanges = listOf(
                TextDocumentEdit(
                    textDocument = OptionalVersionedTextDocumentIdentifier(
                        uri = "file:///a.kt",
                        version = 1
                    ),
                    edits = listOf(e1, e2)
                )
            )
        )
        val collected = collectFileEdits(workspaceEdit)
        assertEquals(listOf(e1, e2), collected["file:///a.kt"])
    }

    @Test
    fun collectFileEditsEmptyForEmptyEdit() {
        assertTrue(collectFileEdits(WorkspaceEdit()).isEmpty())
    }

    // --- textEditsToChangeSpec: range -> offset, multi-edit ordering ---

    @Test
    fun textEditsConvertSingleEditRangeToOffsets() {
        val doc = Text.of(listOf("val foo = 1"))
        // "foo" is chars 4..7
        val spec = textEditsToChangeSpec(listOf(edit(0, 4, 0, 7, "bar")), doc, mapping = null)
        val result = EditorState.create(doc = "val foo = 1").changes(spec).apply(doc)
        assertEquals("val bar = 1", result.toString())
    }

    @Test
    fun textEditsApplyMultipleEditsHighestOffsetFirst() {
        // Two occurrences of "foo"; applying low-offset-first would shift the second.
        val doc = Text.of(listOf("foo + foo"))
        val edits = listOf(
            edit(0, 0, 0, 3, "longer"),
            edit(0, 6, 0, 9, "longer")
        )
        val spec = textEditsToChangeSpec(edits, doc, mapping = null)!!
        val result = EditorState.create(doc = "foo + foo").changes(spec).apply(doc)
        assertEquals("longer + longer", result.toString())
    }

    @Test
    fun textEditsAcrossLines() {
        val doc = Text.of(listOf("foo", "foo"))
        val edits = listOf(
            edit(0, 0, 0, 3, "x"),
            edit(1, 0, 1, 3, "y")
        )
        val spec = textEditsToChangeSpec(edits, doc, mapping = null)!!
        val result = EditorState.create(doc = "foo\nfoo").changes(spec).apply(doc)
        assertEquals("x\ny", result.toString())
    }

    @Test
    fun textEditsEmptyReturnsNull() {
        assertNull(textEditsToChangeSpec(emptyList(), Text.of(listOf("x")), mapping = null))
    }

    // --- applyWorkspaceEdit: current file dispatch ---

    @Test
    fun applyWorkspaceEditRenamesCurrentFile() {
        val client = LSPClient(stubServer())
        val state = EditorState.create(
            doc = "val foo = foo",
            extensions = renameSymbolExtension(client, "file:///a.kt", keymap = false)
        )
        val session = EditorSession(state)
        client.workspace.openFile("file:///a.kt", "kotlin", session)

        val workspaceEdit = WorkspaceEdit(
            changes = mapOf(
                "file:///a.kt" to listOf(
                    edit(0, 4, 0, 7, "bar"),
                    edit(0, 10, 0, 13, "bar")
                )
            )
        )
        applyWorkspaceEdit(client, "file:///a.kt", workspaceEdit, requestMapping = null)
        assertEquals("val bar = bar", session.state.doc.toString())
    }

    @Test
    fun applyWorkspaceEditSkipsUnopenedCrossFile() {
        val client = LSPClient(stubServer())
        val state = EditorState.create(
            doc = "val foo = 1",
            extensions = renameSymbolExtension(client, "file:///a.kt", keymap = false)
        )
        val session = EditorSession(state)
        client.workspace.openFile("file:///a.kt", "kotlin", session)

        val workspaceEdit = WorkspaceEdit(
            changes = mapOf(
                "file:///a.kt" to listOf(edit(0, 4, 0, 7, "bar")),
                // Not opened in the default single-file workspace: skipped gracefully.
                "file:///other.kt" to listOf(edit(0, 0, 0, 3, "bar"))
            )
        )
        applyWorkspaceEdit(client, "file:///a.kt", workspaceEdit, requestMapping = null)
        // Current file edited, no crash for the missing cross-file target.
        assertEquals("val bar = 1", session.state.doc.toString())
    }

    // --- renameSymbol command: gating + prompt state ---

    @Test
    fun renameSymbolReturnsFalseWithoutBinding() {
        val state = EditorState.create(doc = "foo")
        val session = EditorSession(state)
        assertFalse(renameSymbol(session))
    }

    @Test
    fun renameSymbolReturnsFalseWhenNotOnWord() {
        val client = LSPClient(stubServer())
        val state = EditorState.create(
            doc = "   foo",
            extensions = renameSymbolExtension(client, "file:///a.kt", keymap = false)
        )
        val session = EditorSession(state)
        // Cursor at offset 0 (whitespace), not over a word.
        assertFalse(renameSymbol(session))
    }

    @Test
    fun renameSymbolOpensPromptPrefilledWithWord() {
        val client = LSPClient(stubServer())
        val state = EditorState.create(
            doc = "val foo = 1",
            extensions = renameSymbolExtension(client, "file:///a.kt", keymap = false)
        )
        val session = EditorSession(state)
        client.workspace.openFile("file:///a.kt", "kotlin", session)
        // Move cursor into "foo" (offset 5).
        session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(
                    DocPos(5)
                )
            )
        )
        assertTrue(renameSymbol(session))
        val rename = session.state.field(renameState, require = false)
        assertEquals("foo", rename?.word)
        assertEquals(4, rename?.from)
        assertEquals(7, rename?.to)
    }

    @Test
    fun cancelRenameClearsPrompt() {
        val client = LSPClient(stubServer())
        val state = EditorState.create(
            doc = "foo",
            extensions = renameSymbolExtension(client, "file:///a.kt", keymap = false)
        )
        val session = EditorSession(state)
        client.workspace.openFile("file:///a.kt", "kotlin", session)
        assertTrue(renameSymbol(session))
        assertTrue(cancelRenameCommand(session))
        assertNull(session.state.field(renameState, require = false))
        // Falls through when nothing is open.
        assertFalse(cancelRenameCommand(session))
    }

    @Test
    fun renameStateRemapsThroughEdits() {
        val client = LSPClient(stubServer())
        val state = EditorState.create(
            doc = "val foo = 1",
            extensions = renameSymbolExtension(client, "file:///a.kt", keymap = false)
        )
        val session = EditorSession(state)
        client.workspace.openFile("file:///a.kt", "kotlin", session)
        session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(
                    DocPos(5)
                )
            )
        )
        assertTrue(renameSymbol(session))
        // Insert 3 chars at the very start; the anchored range should shift by 3.
        session.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = DocPos(0),
                    insert = InsertContent.StringContent("xxx")
                )
            )
        )
        val rename = session.state.field(renameState, require = false)
        assertEquals(7, rename?.from)
        assertEquals(10, rename?.to)
    }

    // --- capability gating ---

    @Test
    fun editorWithoutRenameHasNullBinding() {
        val state = EditorState.create(doc = "x")
        assertNull(state.facet(renameServer))
    }

    // --- multi-instance: per-editor binding ---

    @Test
    fun twoEditorsKeepIndependentRenameBindings() {
        val clientA = LSPClient(stubServer())
        val clientB = LSPClient(stubServer())

        val stateA = EditorState.create(
            doc = "a",
            extensions = renameSymbolExtension(clientA, "file:///a.kt", keymap = false)
        )
        val stateB = EditorState.create(
            doc = "b",
            extensions = renameSymbolExtension(clientB, "file:///b.kt", keymap = false)
        )

        val bindingA = stateA.facet(renameServer)
        val bindingB = stateB.facet(renameServer)

        assertEquals("file:///a.kt", bindingA?.uri)
        assertSame(clientA, bindingA?.client)
        assertEquals("file:///b.kt", bindingB?.uri)
        assertSame(clientB, bindingB?.client)
        assertNotSame(bindingA, bindingB)
    }

    // --- keymap ---

    @Test
    fun keymapBindsF2AndEscape() {
        assertEquals(2, renameKeymap.size)
        val rename = renameKeymap[0]
        assertEquals("F2", rename.key)
        assertTrue(rename.preventDefault)
        assertEquals("Escape", renameKeymap[1].key)
    }
}
