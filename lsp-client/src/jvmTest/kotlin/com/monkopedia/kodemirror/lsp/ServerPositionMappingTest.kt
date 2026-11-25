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

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.monkopedia.kodemirror.autocomplete.Completion
import com.monkopedia.kodemirror.autocomplete.CompletionApplyContext
import com.monkopedia.kodemirror.autocomplete.CompletionContext
import com.monkopedia.kodemirror.autocomplete.acceptCompletion
import com.monkopedia.kodemirror.autocomplete.completionStatus
import com.monkopedia.kodemirror.autocomplete.startCompletion
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.lsp.BooleanOr
import com.monkopedia.lsp.CompletionItem
import com.monkopedia.lsp.CompletionList
import com.monkopedia.lsp.CompletionOptions
import com.monkopedia.lsp.CompletionParams
import com.monkopedia.lsp.DefinitionParams
import com.monkopedia.lsp.DocumentFormattingParams
import com.monkopedia.lsp.InsertTextFormat
import com.monkopedia.lsp.LanguageServer
import com.monkopedia.lsp.Location
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.ReferenceParams
import com.monkopedia.lsp.RenameParams
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.SingleOrArray
import com.monkopedia.lsp.TextDocumentCompletionResult
import com.monkopedia.lsp.TextDocumentDefinitionResult
import com.monkopedia.lsp.TextEdit
import com.monkopedia.lsp.WorkspaceEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Server positions applied after a round-trip are mapped through the edits the
 * user made in the meantime, as upstream `@codemirror/lsp-client` 6.3.1 maps
 * them (#435).
 */
@OptIn(ExperimentalTestApi::class)
class ServerPositionMappingTest {

    /** Holds a request until the test has made its edits. */
    private class Gate {
        val received = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        suspend fun pass() {
            received.complete(Unit)
            release.await()
        }
    }

    private val gate = Gate()
    private var formatEdits: List<TextEdit> = emptyList()
    private var completionItems: List<CompletionItem> = emptyList()
    private var renameEdit = WorkspaceEdit()

    /** Each completion request, in order, waits on the next of these, if any. */
    private val completionGates = ArrayDeque<CompletableDeferred<Unit>>()

    /** The `a` declared at 4 in [navigationText]. */
    private val target = Location(uri = URI, range = Range(Position(0u, 4u), Position(0u, 5u)))

    private fun client(capabilities: ServerCapabilities): LSPClient {
        val fixture = TestLanguageServer(capabilities = capabilities)
        val server = object : LanguageServer by fixture.server {
            override suspend fun textDocumentFormatting(
                params: DocumentFormattingParams
            ): List<TextEdit> {
                gate.pass()
                return formatEdits
            }

            override suspend fun textDocumentDefinition(
                params: DefinitionParams
            ): TextDocumentDefinitionResult {
                gate.pass()
                return TextDocumentDefinitionResult.DefinitionValue(SingleOrArray.Single(target))
            }

            override suspend fun textDocumentReferences(params: ReferenceParams): List<Location> {
                gate.pass()
                return listOf(target)
            }

            override suspend fun textDocumentRename(params: RenameParams): WorkspaceEdit {
                gate.pass()
                return renameEdit
            }

            override suspend fun textDocumentCompletion(
                params: CompletionParams
            ): TextDocumentCompletionResult {
                completionGates.removeFirstOrNull()?.await()
                return TextDocumentCompletionResult.CompletionListValue(
                    CompletionList(isIncomplete = false, items = completionItems)
                )
            }
        }
        return LSPClient(server).also { runBlocking { it.initialize() } }
    }

    /** An editor on [text], opened in the workspace, whose edits reach it as [LSPPlugin] forwards them. */
    private fun session(
        client: LSPClient,
        text: String,
        extension: Extension,
        uri: String = URI
    ): EditorSession {
        val state = EditorState.create(
            EditorStateConfig(
                doc = text.asDoc(),
                extensions = ExtensionList(
                    listOf(
                        extension,
                        EditorSession.updateListener.of { update ->
                            client.workspace.updateFile(uri, update.changes)
                        }
                    )
                )
            )
        )
        return TestSession(state).also { client.workspace.openFile(uri, "kotlin", it) }
    }

    /** Runs launched coroutines inline (the real scope needs a composable). */
    private class TestSession(state: EditorState) : EditorSession by EditorSession(state) {
        override val coroutineScope = CoroutineScope(Dispatchers.Unconfined)
    }

    private fun EditorSession.edit(from: Int, to: Int, insert: String = "") = dispatch(
        TransactionSpec(
            changes = ChangeSpec.Single(
                DocPos(from),
                DocPos(to),
                InsertContent.StringContent(insert)
            )
        )
    )

    /** Run [userEdits] while the request [start] issued is in flight, then let it finish. */
    private fun duringRequest(client: LSPClient, start: () -> Unit, userEdits: () -> Unit) =
        runBlocking {
            start()
            withTimeout(5000) { gate.received.await() }
            userEdits()
            gate.release.complete(Unit)
            withTimeout(5000) { client.scope.coroutineContext.job.children.forEach { it.join() } }
        }

    private fun edit(line: Int, from: Int, to: Int, text: String) = TextEdit(
        range = Range(Position(line.toUInt(), from.toUInt()), Position(line.toUInt(), to.toUInt())),
        newText = text
    )

    // --- formatDocument ---

    private fun formatClient() = client(
        ServerCapabilities(documentFormattingProvider = BooleanOr.BooleanValue(true))
    )

    @Test
    fun formatEditsAreMappedThroughTextTypedBeforeThem() {
        val client = formatClient()
        val session = session(client, "val x = 1\nval   y=2", formatDocumentExtension(client, URI))
        formatEdits = listOf(edit(1, 0, 9, "val y = 2"))
        duringRequest(client, { formatDocument(session) }) { session.edit(0, 0, "// c\n") }

        assertEquals("// c\nval x = 1\nval y = 2", session.state.doc.toString())
    }

    @Test
    fun formatIsDroppedWhenTypingTouchedAnyEdit() {
        val client = formatClient()
        val session = session(client, "val   x=1\nval   y=2", formatDocumentExtension(client, URI))
        formatEdits = listOf(edit(0, 0, 9, "val x = 1"), edit(1, 0, 9, "val y = 2"))
        // Type inside the second edit's range: neither edit applies.
        duringRequest(client, { formatDocument(session) }) { session.edit(14, 14, "z") }

        assertEquals("val   x=1\nval z  y=2", session.state.doc.toString())
    }

    @Test
    fun formatAppliesWhenNothingChanged() {
        val client = formatClient()
        val session = session(client, "val   x=1\nval   y=2", formatDocumentExtension(client, URI))
        formatEdits = listOf(edit(0, 0, 9, "val x = 1"), edit(1, 0, 9, "val y = 2"))
        duringRequest(client, { formatDocument(session) }) {}

        assertEquals("val x = 1\nval y = 2", session.state.doc.toString())
    }

    // --- completion additionalTextEdits ---

    // "trans" is 29..34, and the auto-import goes on the empty line at 10.
    private val completionText = "package p\n\nval x = 1\nval y = trans"
    private val autoImport = edit(1, 0, 0, "import foo.transform\n")

    private fun completionClient() =
        client(ServerCapabilities(completionProvider = CompletionOptions()))

    /** Query the server source at the end of "trans" and return its one option. */
    private fun query(client: LSPClient, session: EditorSession, pos: Int = 34): Completion =
        runBlocking {
            val context = CompletionContext(session.state, DocPos(pos), explicit = true)
            serverCompletionSource(client, URI)(context)!!.options.single()
        }

    /** Accept [completion] over [from]..[to], the range `:autocomplete` has mapped to now. */
    private fun EditorSession.accept(completion: Completion, from: Int, to: Int) =
        completion.applyFn!!(CompletionApplyContext(this, completion, DocPos(from), DocPos(to)))

    private val eagerItem = CompletionItem(
        label = "transform",
        insertText = "transform",
        additionalTextEdits = listOf(autoImport)
    )

    @Test
    fun eagerAutoImportAndInsertionLandTogether() {
        val client = completionClient()
        val session = session(client, completionText, ExtensionList(emptyList()))
        completionItems = listOf(eagerItem)
        session.accept(query(client, session), 29, 34)

        val text = session.state.doc.toString()
        assertEquals("package p\nimport foo.transform\n\nval x = 1\nval y = transform", text)
        assertEquals(text.length, session.state.selection.main.head.value)
    }

    @Test
    fun eagerAutoImportIsMappedThroughEditsBeforeAccept() {
        val client = completionClient()
        val session = session(client, completionText, ExtensionList(emptyList()))
        completionItems = listOf(eagerItem)
        val completion = query(client, session)
        session.edit(0, 0, "// c\n")
        session.accept(completion, 34, 39)

        assertEquals(
            "// c\npackage p\nimport foo.transform\n\nval x = 1\nval y = transform",
            session.state.doc.toString()
        )
    }

    @Test
    fun eagerAutoImportWhoseRangeWasEditedBeforeAcceptIsDropped() {
        val client = completionClient()
        val session = session(client, completionText, ExtensionList(emptyList()))
        completionItems = listOf(eagerItem)
        val completion = query(client, session)
        session.edit(10, 10, "// c")
        session.accept(completion, 33, 38)

        assertEquals("package p\n// c\nval x = 1\nval y = transform", session.state.doc.toString())
    }

    /**
     * An editor on [completionText] with the cursor at [cursor], mounted in a
     * [KodeMirror] so `:autocomplete`'s plugin runs its queries.
     */
    private fun ComposeUiTest.mountedCompletion(
        client: LSPClient,
        cursor: Int = 34
    ): EditorSession {
        val session = EditorSession(
            EditorState.create(
                EditorStateConfig(
                    doc = completionText.asDoc(),
                    selection = SelectionSpec.CursorSpec(DocPos(cursor)),
                    extensions = ExtensionList(
                        listOf(
                            serverCompletion(client, URI),
                            EditorSession.updateListener.of { update ->
                                client.workspace.updateFile(URI, update.changes)
                            }
                        )
                    )
                )
            )
        )
        client.workspace.openFile(URI, "kotlin", session)
        setContent { KodeMirror(session = session) }
        waitForIdle()
        return session
    }

    @Test
    fun oneQueryRunsWhenStartedTwiceAndItsImportMapsThroughALaterEdit() = runDesktopComposeUiTest {
        val client = completionClient()
        val session = mountedCompletion(client)
        completionItems = listOf(eagerItem)
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        completionGates += listOf(first, second)
        // `:autocomplete` keeps one query per source in flight (#422): the second
        // start doesn't send a request while the first is out. Replaying it onto the
        // first result asks again, once that result is in.
        runOnIdle {
            assertTrue(startCompletion(session))
            assertTrue(startCompletion(session))
        }
        waitForIdle()
        assertEquals(listOf(second), completionGates.toList())
        first.complete(Unit)
        second.complete(Unit)
        waitUntil(timeoutMillis = 5000) { completionStatus(session.state) == "active" }
        runOnIdle {
            session.edit(0, 0, "// c\n")
            assertTrue(acceptCompletion(session))
        }

        assertEquals(
            "// c\npackage p\nimport foo.transform\n\nval x = 1\nval y = transform",
            session.state.doc.toString()
        )
    }

    @Test
    fun anEditBetweenTheTriggerAndTheQueryDropsTheEagerImport() {
        val client = completionClient()
        val session = session(client, completionText, ExtensionList(emptyList()))
        completionItems = listOf(eagerItem)
        // The query's state is from before this edit, so its mapping can't see it.
        val context = CompletionContext(session.state, DocPos(34), explicit = true)
        session.edit(0, 0, "// c\n")
        val completion =
            runBlocking { serverCompletionSource(client, URI)(context)!!.options.single() }
        session.accept(completion, 34, 39)

        // Dropped rather than misplaced: an unmapped import would split "package".
        assertEquals(
            "// c\npackage p\n\nval x = 1\nval y = transform",
            session.state.doc.toString()
        )
    }

    // --- snippets with additionalTextEdits ---

    private fun snippetItem(vararg edits: TextEdit) = CompletionItem(
        label = "transform",
        insertText = "transform(\$1)",
        insertTextFormat = InsertTextFormat.SNIPPET,
        additionalTextEdits = edits.toList()
    )

    @Test
    fun aSnippetExpandsWhereAnImportAboveMovedTheText() {
        val client = completionClient()
        val session = session(client, completionText, ExtensionList(emptyList()))
        completionItems = listOf(snippetItem(autoImport))
        session.accept(query(client, session), 29, 34)

        val text = session.state.doc.toString()
        assertEquals("package p\nimport foo.transform\n\nval x = 1\nval y = transform()", text)
        assertEquals(text.length - 1, session.state.selection.main.head.value)
    }

    @Test
    fun aSnippetExpandsAfterAnImportInsertedAtItsStart() {
        val client = completionClient()
        // "trans" is 11..16, at the start of the line the import goes on.
        val session = session(client, "package p\n\ntrans", ExtensionList(emptyList()))
        completionItems = listOf(snippetItem(edit(2, 0, 0, "import foo.transform\n")))
        session.accept(query(client, session, pos = 16), 11, 16)

        assertEquals("package p\n\nimport foo.transform\ntransform()", session.state.doc.toString())
    }

    @Test
    fun aSnippetExpandsBeforeAnEditInsertedAtItsEnd() {
        val client = completionClient()
        val session = session(client, completionText, ExtensionList(emptyList()))
        completionItems = listOf(snippetItem(edit(3, 13, 13, " // auto")))
        session.accept(query(client, session), 29, 34)

        assertEquals(
            "package p\n\nval x = 1\nval y = transform() // auto",
            session.state.doc.toString()
        )
    }

    // --- definition / references navigation ---

    private val navigationText = "val a = 1\nval b = a"

    private fun navigationClient() = client(
        ServerCapabilities(
            definitionProvider = BooleanOr.BooleanValue(true),
            referencesProvider = BooleanOr.BooleanValue(true)
        )
    )

    /** Type at the target (4) and above it, so the target moves to 9 or, past the "x", 10. */
    private fun EditorSession.editAroundTarget() {
        edit(4, 4, "x")
        edit(0, 0, "// c\n")
    }

    private fun EditorSession.cursorOnUse() =
        dispatch(TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos(18))))

    private fun EditorSession.showTheReference() {
        val panel = state.field(referencePanel)!!
        val entry = buildReferenceEntries(panel.locations) { null }.single()
        showReference(state.facet(referenceServer)!!, entry, panel.sourceMapping)
    }

    @Test
    fun definitionTargetIsMappedThroughEditsDuringTheRequest() {
        val client = navigationClient()
        val session = session(client, navigationText, definitionJumps(client, URI))
        session.cursorOnUse()
        duringRequest(client, { jumpToDefinition(session) }) { session.editAroundTarget() }

        // Upstream maps with the default assoc -1, so the cursor stays before the "x".
        assertEquals(9, session.state.selection.main.head.value)
    }

    @Test
    fun referenceIsMappedThroughEditsDuringTheRequest() {
        val client = navigationClient()
        val session = session(client, navigationText, findReferencesExtension(client, URI))
        session.cursorOnUse()
        duringRequest(client, { findReferences(session) }) { session.editAroundTarget() }
        session.showTheReference()

        // Upstream's showReference maps with assoc 1, past the "x".
        assertEquals(10, session.state.selection.main.head.value)
    }

    @Test
    fun referenceIsMappedThroughEditsWhileThePanelIsOpen() {
        val client = navigationClient()
        val session = session(client, navigationText, findReferencesExtension(client, URI))
        session.cursorOnUse()
        duringRequest(client, { findReferences(session) }) {}
        session.editAroundTarget()
        session.showTheReference()

        assertEquals(10, session.state.selection.main.head.value)
    }

    // --- rename ---

    // "foo" is declared at 4..7 and used at 12..15 and 18..21.
    private val renameText = "val foo = 1\nfoo + foo"
    private val renameEdits =
        listOf(edit(0, 4, 7, "bar"), edit(1, 0, 3, "bar"), edit(1, 6, 9, "bar"))

    private fun renameClient() =
        client(ServerCapabilities(renameProvider = BooleanOr.BooleanValue(true)))

    /** Rename the "foo" declared at 4 to "bar", with the server answering [edits] by URI. */
    private fun renameDuring(
        client: LSPClient,
        session: EditorSession,
        edits: Map<String, List<TextEdit>> = mapOf(URI to renameEdits),
        userEdits: () -> Unit
    ) {
        renameEdit = WorkspaceEdit(changes = edits)
        duringRequest(client, {
            confirmRename(session, RenameState(4, 7, "foo"), "bar")
        }, userEdits)
    }

    private fun renameSession(client: LSPClient) =
        session(client, renameText, renameSymbolExtension(client, URI, keymap = false))

    @Test
    fun renameEditsAreShiftedOnceByTextTypedBeforeThem() {
        val client = renameClient()
        val session = renameSession(client)
        renameDuring(client, session) { session.edit(0, 0, "// c\n") }

        assertEquals("// c\nval bar = 1\nbar + bar", session.state.doc.toString())
    }

    @Test
    fun aRenameEditTheUserTypedInsideIsStillApplied() {
        val client = renameClient()
        val session = renameSession(client)
        // Upstream drops no rename edit: "fxoo" is replaced whole.
        renameDuring(client, session) { session.edit(5, 5, "x") }

        assertEquals("val bar = 1\nbar + bar", session.state.doc.toString())
    }

    @Test
    fun aRenamedSymbolTheUserDeletedGetsTheNewName() {
        val client = renameClient()
        val session = renameSession(client)
        // Both ends map to 4, so upstream inserts the new name where "foo" was.
        renameDuring(client, session) { session.edit(4, 7) }

        assertEquals("val bar = 1\nbar + bar", session.state.doc.toString())
    }

    @Test
    fun textTypedAtARenameEditsStartIsReplacedWithIt() {
        val client = renameClient()
        val session = renameSession(client)
        // `from` maps with assoc -1, so it stays before the "x".
        renameDuring(client, session) { session.edit(4, 4, "x") }

        assertEquals("val bar = 1\nbar + bar", session.state.doc.toString())
    }

    @Test
    fun textTypedAtARenameEditsEndIsKept() {
        val client = renameClient()
        val session = renameSession(client)
        // `to` maps with assoc -1, so it stays before the "x".
        renameDuring(client, session) { session.edit(7, 7, "x") }

        assertEquals("val barx = 1\nbar + bar", session.state.doc.toString())
    }

    @Test
    fun aRenameEditInAnotherOpenFileIsMappedThroughItsOwnEdits() {
        val client = renameClient()
        val session = renameSession(client)
        val other = session(client, "val x = foo()\n", ExtensionList(emptyList()), uri = OTHER_URI)
        val edits = mapOf(URI to renameEdits, OTHER_URI to listOf(edit(0, 8, 11, "bar")))
        renameDuring(client, session, edits) {
            other.edit(0, 0, "// c\n")
            session.edit(0, 0, "// c\n")
        }

        assertEquals("// c\nval bar = 1\nbar + bar", session.state.doc.toString())
        assertEquals("// c\nval x = bar()\n", other.state.doc.toString())
    }

    @Test
    fun aFileOpenedDuringTheRenameTakesItsEditsAgainstItsCurrentText() {
        val client = renameClient()
        val session = renameSession(client)
        val edits = mapOf(URI to renameEdits, OTHER_URI to listOf(edit(0, 0, 3, "bar")))
        lateinit var other: EditorSession
        renameDuring(client, session, edits) {
            other = session(client, "foo()\n", ExtensionList(emptyList()), uri = OTHER_URI)
        }

        assertEquals("bar()\n", other.state.doc.toString())
    }

    @Test
    fun aFileReopenedDuringTheRenameGetsItsEditsInTheNewEditor() {
        val client = renameClient()
        val session = renameSession(client)
        lateinit var reopened: EditorSession
        renameDuring(client, session) {
            client.workspace.closeFile(URI)
            reopened =
                session(client, renameText, renameSymbolExtension(client, URI, keymap = false))
        }

        assertEquals("val bar = 1\nbar + bar", reopened.state.doc.toString())
        assertEquals(renameText, session.state.doc.toString())
    }

    private companion object {
        const val URI = "file:///a.kt"
        const val OTHER_URI = "file:///b.kt"
    }
}
