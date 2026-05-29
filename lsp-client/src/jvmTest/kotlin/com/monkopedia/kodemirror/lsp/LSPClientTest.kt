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
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.lsp.HoverContents
import com.monkopedia.lsp.HoverParams
import com.monkopedia.lsp.MarkupContent
import com.monkopedia.lsp.MarkupKind
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.PublishDiagnosticsParams
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.TextDocumentIdentifier
import com.monkopedia.lsp.TextDocumentSyncKind
import com.monkopedia.lsp.TextDocumentSyncOptions
import com.monkopedia.lsp.Hover as LSPHover
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Exercises the [LSPClient] protocol/lifecycle seam against the scriptable
 * [TestLanguageServer]. Maps the relevant upstream `webtest-client.ts` cases to
 * our architecture (where the consumer provides a typed [com.monkopedia.lsp.LanguageServer]
 * rather than a pluggable JSON-RPC transport). Position conversion and document
 * sync mechanics are covered by [DocumentSyncTest] and are not duplicated here.
 */
class LSPClientTest {

    /** A capability set that opts into open/close + change notifications. */
    private fun syncingCaps() =
        ServerCapabilities(textDocumentSync = TextDocumentSyncKind.INCREMENTAL)

    private fun session(uri: String, doc: String, client: LSPClient): EditorSession {
        val session = EditorSession(EditorState.create(doc = doc))
        client.workspace.openFile(uri, "kotlin", session)
        return session
    }

    // --- "can connect to a server": initialize handshake ---

    @Test
    fun initializePerformsHandshakeAndRecordsCapabilities() = runBlocking {
        val fixture = TestLanguageServer(capabilities = syncingCaps())
        val client = LSPClient(fixture.server)
        assertFalse(client.isInitialized)

        val result = client.initialize()

        assertTrue(client.isInitialized)
        assertSame(result, client.initializeResult)
        assertEquals(1, fixture.initializeCount)
        // initialize is followed by the initialized notification, per the spec.
        assertTrue(fixture.initialized)
        assertEquals(
            TextDocumentSyncKind.INCREMENTAL,
            client.serverCapabilities?.textDocumentSync
        )
    }

    @Test
    fun initializeIsIdempotent() = runBlocking {
        val fixture = TestLanguageServer(capabilities = syncingCaps())
        val client = LSPClient(fixture.server)
        val first = client.initialize()
        val second = client.initialize()
        // Cached: the server is contacted exactly once.
        assertSame(first, second)
        assertEquals(1, fixture.initializeCount)
    }

    // --- "can open a file" ---

    @Test
    fun openFileSendsDidOpen() = runBlocking {
        val fixture = TestLanguageServer(capabilities = syncingCaps())
        val client = LSPClient(fixture.server)
        client.initialize()
        session("file:///a.kt", "val x = 1", client)

        client.workspace.didOpenFile("file:///a.kt")

        assertEquals(listOf("file:///a.kt"), fixture.didOpenUris)
        assertEquals(listOf("file:///a.kt"), fixture.openFiles)
    }

    @Test
    fun didOpenSuppressedWhenServerOmitsOpenClose() = runBlocking {
        // A server that explicitly opts OUT of open/close notifications.
        val fixture = TestLanguageServer(
            capabilities = ServerCapabilities(
                textDocumentSync = TextDocumentSyncOptions(openClose = false)
            )
        )
        val client = LSPClient(fixture.server)
        client.initialize()
        session("file:///a.kt", "x", client)

        client.workspace.didOpenFile("file:///a.kt")

        assertTrue(fixture.didOpenUris.isEmpty())
    }

    // --- "can update/close a file" ---

    @Test
    fun updateThenSyncSendsDidChange() = runBlocking {
        val fixture = TestLanguageServer(capabilities = syncingCaps())
        val client = LSPClient(fixture.server)
        client.initialize()
        val session = session("file:///a.kt", "abc", client)
        client.workspace.didOpenFile("file:///a.kt")

        // Record an edit, then flush.
        val changes = session.state.changes(
            com.monkopedia.kodemirror.state.ChangeSpec.Single(
                from = com.monkopedia.kodemirror.state.DocPos(3),
                insert = com.monkopedia.kodemirror.state.InsertContent.StringContent("d")
            )
        )
        client.workspace.updateFile("file:///a.kt", changes)
        client.sync()

        assertEquals(listOf("file:///a.kt"), fixture.didChangeUris)
        // Version advanced past the version-1 baseline once the change flushed.
        assertEquals(2, client.workspace.getFile("file:///a.kt")?.version)
    }

    @Test
    fun closeFileSendsDidCloseAndDropsTracking() = runBlocking {
        val fixture = TestLanguageServer(capabilities = syncingCaps())
        val client = LSPClient(fixture.server)
        client.initialize()
        session("file:///a.kt", "x", client)
        client.workspace.didOpenFile("file:///a.kt")

        client.workspace.closeFile("file:///a.kt")
        client.workspace.didCloseFile("file:///a.kt")

        assertEquals(listOf("file:///a.kt"), fixture.didCloseUris)
        assertTrue(fixture.openFiles.isEmpty())
        assertNull(client.workspace.getFile("file:///a.kt"))
    }

    // --- "can open multiple files" ---

    @Test
    fun multipleFilesTrackedAndAnnouncedIndependently() = runBlocking {
        val fixture = TestLanguageServer(capabilities = syncingCaps())
        val client = LSPClient(fixture.server)
        client.initialize()
        session("file:///a.kt", "a", client)
        session("file:///b.kt", "b", client)

        client.workspace.didOpenFile("file:///a.kt")
        client.workspace.didOpenFile("file:///b.kt")

        assertEquals(setOf("file:///a.kt", "file:///b.kt"), fixture.openFiles.toSet())
        assertEquals(
            setOf("file:///a.kt", "file:///b.kt"),
            client.workspace.openFiles.map { it.uri }.toSet()
        )
        // Closing one leaves the other open on both sides of the seam.
        client.workspace.closeFile("file:///a.kt")
        client.workspace.didCloseFile("file:///a.kt")
        assertEquals(listOf("file:///b.kt"), fixture.openFiles)
        assertEquals(listOf("file:///b.kt"), client.workspace.openFiles.map { it.uri })
    }

    // --- "can display messages in the editor" -> window/showMessage routing ---

    @Test
    fun showMessageNotificationIsHandledGracefully() = runBlocking {
        // Our LSPLanguageClient currently treats window/showMessage as a no-op
        // (spec-safe default until a message-surface feature lands); assert the
        // notification is accepted without throwing. (See PR body: surfacing it
        // into the editor UI is N/A in the current design.)
        val fixture = TestLanguageServer(capabilities = syncingCaps())
        val client = LSPClient(fixture.server)
        client.languageClient.windowShowMessage(
            com.monkopedia.lsp.ShowMessageParams(
                type = com.monkopedia.lsp.MessageType.INFO,
                message = "hello"
            )
        )
    }

    // --- unknown / out-of-feature server->client requests handled gracefully ---

    @Test
    fun unhandledServerRequestsReturnSpecSafeDefaults() = runBlocking {
        // ksrpc's typed LanguageClient interface makes a truly "unknown method"
        // impossible (it would not compile), so upstream's "reports invalid
        // methods" has no analog. What we CAN assert is that server->client
        // requests outside an implemented feature return the documented
        // spec-safe defaults rather than crashing.
        val fixture = TestLanguageServer()
        val client = LSPClient(fixture.server)
        val lc = client.languageClient
        assertFalse(lc.windowShowDocument(com.monkopedia.lsp.ShowDocumentParams(uri = "file:///x")).success)
        assertFalse(
            lc.workspaceApplyEdit(
                com.monkopedia.lsp.ApplyWorkspaceEditParams(
                    edit = com.monkopedia.lsp.WorkspaceEdit()
                )
            ).applied
        )
        assertTrue(lc.workspaceWorkspaceFolders().isEmpty())
    }

    @Test
    fun publishDiagnosticsForUnknownFileIsIgnored() = runBlocking {
        // A server->client notification referencing a file the workspace does
        // not track must be dropped, not crash.
        val fixture = TestLanguageServer(capabilities = syncingCaps())
        val client = LSPClient(fixture.server)
        client.languageClient.textDocumentPublishDiagnostics(
            PublishDiagnosticsParams(uri = "file:///never-opened.kt", diagnostics = emptyList())
        )
    }

    // --- error routing: a throwing server method surfaces to the caller ---

    @Test
    fun serverInitializeExceptionPropagatesToCaller() {
        val fixture = TestLanguageServer(
            throwingMethods = setOf("initialize"),
            throwable = IllegalStateException("Broken Pipe")
        )
        val client = LSPClient(fixture.server)
        val thrown = assertFailsWith<IllegalStateException> {
            runBlocking { client.initialize() }
        }
        assertEquals("Broken Pipe", thrown.message)
        // The failed handshake left the client uninitialized.
        assertFalse(client.isInitialized)
    }

    @Test
    fun serverRequestExceptionPropagatesToCaller() {
        // The analog of upstream's "routes exceptions from Transport.send to the
        // request promise": a typed suspend call that the server fails rethrows
        // to the caller rather than being swallowed.
        val fixture = TestLanguageServer(
            capabilities = syncingCaps(),
            throwingMethods = setOf("textDocumentHover"),
            throwable = IllegalStateException("File not open")
        )
        val client = LSPClient(fixture.server)
        val thrown = assertFailsWith<IllegalStateException> {
            runBlocking {
                client.server.textDocumentHover(
                    HoverParams(
                        textDocument = TextDocumentIdentifier(uri = "file:///a.kt"),
                        position = Position(0u, 0u)
                    )
                )
            }
        }
        assertEquals("File not open", thrown.message)
    }

    @Test
    fun serverHoverResponseRoutesValueToCaller() = runBlocking {
        // Complement to the error-routing test: a value returned by the server
        // reaches the caller through the same suspend bridge.
        val hover = LSPHover(
            contents = HoverContents.MarkupContentValue(
                MarkupContent(kind = MarkupKind.MARKDOWN, value = "**docs**")
            )
        )
        val fixture = TestLanguageServer(
            capabilities = syncingCaps(),
            responses = mapOf("textDocumentHover" to hover)
        )
        val client = LSPClient(fixture.server)
        val result = client.server.textDocumentHover(
            HoverParams(
                textDocument = TextDocumentIdentifier(uri = "file:///a.kt"),
                position = Position(0u, 0u)
            )
        )
        assertSame(hover, result)
    }
}
