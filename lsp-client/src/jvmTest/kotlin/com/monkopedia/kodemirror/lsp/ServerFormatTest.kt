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
import com.monkopedia.lsp.BooleanOr
import com.monkopedia.lsp.InitializeResult
import com.monkopedia.lsp.LanguageServer
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.TextEdit
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class ServerFormatTest {

    private fun pos(line: Int, char: Int): Position =
        Position(line = line.toUInt(), character = char.toUInt())

    private fun range(startLine: Int, startChar: Int, endLine: Int, endChar: Int): Range =
        Range(start = pos(startLine, startChar), end = pos(endLine, endChar))

    private fun edit(startLine: Int, startChar: Int, endLine: Int, endChar: Int, newText: String) =
        TextEdit(range = range(startLine, startChar, endLine, endChar), newText = newText)

    /**
     * A do-nothing [LanguageServer] used only to construct distinct [LSPClient]s;
     * [formatDocumentExtension] merely stores the client in the [formatServer]
     * facet, it does not contact the server at install time.
     */
    private fun stubServer(): LanguageServer = Proxy.newProxyInstance(
        LanguageServer::class.java.classLoader,
        arrayOf(LanguageServer::class.java)
    ) { _, method, _ ->
        error("stub LanguageServer.${method.name} should not be called in this test")
    } as LanguageServer

    /**
     * A [LanguageServer] that initializes (advertising [documentFormatting] via
     * `documentFormattingProvider`) and answers `textDocument/formatting` with
     * [editsToReturn]. All other suspend methods no-op via a coroutine-aware
     * reflective handler. Used to exercise the async [formatDocument] path
     * end-to-end.
     */
    private fun formattingServer(
        documentFormatting: Boolean,
        editsToReturn: List<TextEdit>
    ): LanguageServer {
        val handler = InvocationHandler { _, method: Method, args: Array<Any?>? ->
            // Suspend methods receive a trailing Continuation; complete it
            // synchronously with the result for that method.
            @Suppress("UNCHECKED_CAST")
            val continuation = args?.lastOrNull() as? Continuation<Any?>
            val result: Any? = when (method.name) {
                "initialize" -> InitializeResult(
                    capabilities = ServerCapabilities(
                        documentFormattingProvider = BooleanOr.BooleanValue(documentFormatting)
                    )
                )
                "initialized" -> Unit
                "textDocumentFormatting" -> editsToReturn
                else -> Unit
            }
            continuation?.resume(result)
            COROUTINE_SUSPENDED
        }
        return Proxy.newProxyInstance(
            LanguageServer::class.java.classLoader,
            arrayOf(LanguageServer::class.java),
            handler
        ) as LanguageServer
    }

    private fun openSession(client: LSPClient, doc: String, uri: String): EditorSession {
        val state = EditorState.create(
            doc = doc,
            extensions = formatDocumentExtension(client, uri, keymap = false)
        )
        val session = EditorSession(state)
        client.workspace.openFile(uri, "kotlin", session)
        return session
    }

    /** Run [block], then wait (up to a second) for the session doc to reach [expected]. */
    private fun awaitDoc(session: EditorSession, expected: String, block: () -> Unit) =
        runBlocking {
            block()
            withTimeout(2000) {
                while (session.state.doc.toString() != expected) {
                    kotlinx.coroutines.yield()
                }
            }
            assertEquals(expected, session.state.doc.toString())
        }

    // --- FormattingOptions derivation ---

    @Test
    fun formattingOptionsDerivesTabSizeFromState() {
        val state = EditorState.create(
            doc = "x",
            extensions = EditorState.tabSize.of(2)
        )
        val options = formattingOptions(state)
        assertEquals(2u, options.tabSize)
        assertTrue(options.insertSpaces)
    }

    @Test
    fun formattingOptionsDefaultTabSize() {
        // Default tabSize facet value is 4.
        val options = formattingOptions(EditorState.create(doc = "x"))
        assertEquals(4u, options.tabSize)
        assertTrue(options.insertSpaces)
    }

    // --- textEditsToChangeSpec applied to a document (apply TextEdits -> expected doc) ---

    @Test
    fun formatEditsRewriteWholeDocument() {
        val doc = Text.of(listOf("val   x=1"))
        // Server replaces the whole line with formatted text.
        val edits = listOf(edit(0, 0, 0, 9, "val x = 1"))
        val spec = textEditsToChangeSpec(edits, doc, mapping = null)!!
        val result = EditorState.create(doc = "val   x=1").changes(spec).apply(doc)
        assertEquals("val x = 1", result.toString())
    }

    @Test
    fun formatEditsApplyMultipleHighestOffsetFirst() {
        val doc = Text.of(listOf("a;b;c"))
        val edits = listOf(
            edit(0, 1, 0, 2, " ; "),
            edit(0, 3, 0, 4, " ; ")
        )
        val spec = textEditsToChangeSpec(edits, doc, mapping = null)!!
        val result = EditorState.create(doc = "a;b;c").changes(spec).apply(doc)
        assertEquals("a ; b ; c", result.toString())
    }

    // --- formatDocument command gating ---

    @Test
    fun formatDocumentReturnsFalseWithoutBinding() {
        val state = EditorState.create(doc = "x")
        val session = EditorSession(state)
        assertFalse(formatDocument(session))
    }

    @Test
    fun formatDocumentReturnsFalseWhenCapabilityAbsent() {
        val client =
            LSPClient(formattingServer(documentFormatting = false, editsToReturn = listOf()))
        // Initialize so serverCapabilities is populated (capability explicitly false).
        runBlocking { client.initialize() }
        val session = openSession(client, "val x=1", "file:///a.kt")
        assertFalse(formatDocument(session))
    }

    @Test
    fun formatDocumentProceedsWhenCapabilityPresent() {
        val client = LSPClient(
            formattingServer(
                documentFormatting = true,
                editsToReturn = listOf(edit(0, 0, 0, 7, "val x = 1"))
            )
        )
        runBlocking { client.initialize() }
        val session = openSession(client, "val x=1", "file:///a.kt")
        awaitDoc(session, "val x = 1") {
            assertTrue(formatDocument(session))
        }
    }

    @Test
    fun formatDocumentProceedsWhenUninitialized() {
        // Not initialized: capabilities unknown -> command is handled (returns
        // true), matching upstream's "proceed when capabilities unknown".
        val client = LSPClient(
            formattingServer(
                documentFormatting = true,
                editsToReturn = listOf(edit(0, 0, 0, 7, "val x = 1"))
            )
        )
        val session = openSession(client, "val x=1", "file:///a.kt")
        assertTrue(formatDocument(session))
    }

    // --- multi-instance: per-editor binding ---

    @Test
    fun editorWithoutFormatExtensionHasNullBinding() {
        val state = EditorState.create(doc = "x")
        assertNull(state.facet(formatServer))
    }

    @Test
    fun twoEditorsKeepIndependentFormatBindings() {
        val clientA = LSPClient(stubServer())
        val clientB = LSPClient(stubServer())

        val stateA = EditorState.create(
            doc = "a",
            extensions = formatDocumentExtension(clientA, "file:///a.kt", keymap = false)
        )
        val stateB = EditorState.create(
            doc = "b",
            extensions = formatDocumentExtension(clientB, "file:///b.kt", keymap = false)
        )

        val bindingA = stateA.facet(formatServer)
        val bindingB = stateB.facet(formatServer)

        assertEquals("file:///a.kt", bindingA?.uri)
        assertSame(clientA, bindingA?.client)
        assertEquals("file:///b.kt", bindingB?.uri)
        assertSame(clientB, bindingB?.client)
        assertNotSame(bindingA, bindingB)
    }

    // --- keymap ---

    @Test
    fun keymapBindsShiftAltF() {
        assertEquals(1, formatKeymap.size)
        val binding = formatKeymap[0]
        assertEquals("Shift-Alt-f", binding.key)
        assertTrue(binding.preventDefault)
    }
}
