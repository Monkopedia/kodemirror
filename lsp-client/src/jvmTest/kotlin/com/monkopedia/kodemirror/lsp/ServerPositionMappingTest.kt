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
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.lsp.BooleanOr
import com.monkopedia.lsp.DocumentFormattingParams
import com.monkopedia.lsp.LanguageServer
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.TextEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Server positions applied after a round-trip are mapped through the edits the
 * user made in the meantime, as upstream `@codemirror/lsp-client` 6.3.1 maps
 * them (#435).
 */
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

    private fun client(capabilities: ServerCapabilities): LSPClient {
        val fixture = TestLanguageServer(capabilities = capabilities)
        val server = object : LanguageServer by fixture.server {
            override suspend fun textDocumentFormatting(
                params: DocumentFormattingParams
            ): List<TextEdit> {
                gate.pass()
                return formatEdits
            }
        }
        return LSPClient(server).also { runBlocking { it.initialize() } }
    }

    /** An editor on [text], opened in the workspace, whose edits reach it as [LSPPlugin] forwards them. */
    private fun session(client: LSPClient, text: String, extension: Extension): EditorSession {
        val state = EditorState.create(
            EditorStateConfig(
                doc = text.asDoc(),
                extensions = ExtensionList(
                    listOf(
                        extension,
                        EditorSession.updateListener.of { update ->
                            client.workspace.updateFile(URI, update.changes)
                        }
                    )
                )
            )
        )
        return EditorSession(state).also { client.workspace.openFile(URI, "kotlin", it) }
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

    private companion object {
        const val URI = "file:///a.kt"
    }
}
