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

import com.monkopedia.kodemirror.autocomplete.CompletionApplyContext
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.exceptionSink
import com.monkopedia.lsp.CompletionItem
import com.monkopedia.lsp.CompletionItemKind
import com.monkopedia.lsp.CompletionOptions
import com.monkopedia.lsp.LanguageServer
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.TextEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive

/**
 * Accepting a lazy completion issues `completionItem/resolve` and applies the
 * result once it arrives (#434). The editor can change, or go away, during that
 * round-trip.
 *
 * Upstream `@codemirror/lsp-client` (6.3.1) has no resolve step. Its closest
 * equivalents, `additionalTextEdits` in `applyEdits` and `formatDocument`, map
 * server positions through the edits made since the request and drop an edit
 * whose range those edits touched. These tests hold the port's resolve to the
 * same rule.
 *
 * The scope is a plain [Job], like the composition scope behind
 * [EditorSession.coroutineScope], with a handler that records anything uncaught.
 */
class ResolveThenApplyTest {
    private val logged = mutableListOf<String>()
    private val unhandled = mutableListOf<Throwable>()
    private val scope = CoroutineScope(
        Job() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> unhandled += e }
    )

    // Offsets: line 1 (the empty line) starts at 10; "trans" is 29..34.
    private val startText = "package p\n\nval x = 1\nval y = trans"

    private val lazyItem = CompletionItem(
        label = "transform",
        kind = CompletionItemKind.FUNCTION,
        data = JsonPrimitive("transform")
    )

    /** Resolves to "transform" plus an auto-import on the empty line 1. */
    private val resolvedItem = lazyItem.copy(
        insertText = "transform",
        additionalTextEdits = listOf(
            TextEdit(
                range = Range(Position(1u, 0u), Position(1u, 0u)),
                newText = "import foo.transform\n"
            )
        )
    )

    private class TestSession(state: EditorState, override val coroutineScope: CoroutineScope) :
        EditorSession by EditorSession(state)

    private fun client(resolve: suspend (CompletionItem) -> CompletionItem): LSPClient {
        val fixture = TestLanguageServer(
            capabilities = ServerCapabilities(
                completionProvider = CompletionOptions(resolveProvider = true)
            )
        )
        val server = object : LanguageServer by fixture.server {
            override suspend fun completionItemResolve(params: CompletionItem): CompletionItem =
                resolve(params)
        }
        return LSPClient(server).also { runBlocking { it.initialize() } }
    }

    /**
     * An editor on [startText] whose edits reach the workspace, as [LSPPlugin]
     * forwards them. With [tracked] false the file is never opened.
     */
    private fun session(client: LSPClient, tracked: Boolean = true): EditorSession {
        val state = EditorState.create(
            EditorStateConfig(
                doc = startText.asDoc(),
                extensions = ExtensionList(
                    listOf(
                        exceptionSink.of { logged += it.message.orEmpty() },
                        EditorSession.updateListener.of { update ->
                            client.workspace.updateFile(URI, update.changes)
                        }
                    )
                )
            )
        )
        return TestSession(state, scope).also {
            if (tracked) client.workspace.openFile(URI, "kotlin", it)
        }
    }

    /** Accept [lazyItem] over "trans" (29..34). */
    private fun accept(session: EditorSession, client: LSPClient) {
        val completion = mapCompletionItem(
            lazyItem,
            session.state.doc,
            client,
            resolveProvider = true,
            uri = URI
        )
        completion.applyFn!!(CompletionApplyContext(session, completion, DocPos(29), DocPos(34)))
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

    private fun assertScopeSurvived() {
        assertEquals(emptyList(), unhandled)
        assertTrue(scope.isActive, "the editor's scope survives")
    }

    @Test
    fun theDocShrinkingDuringResolveAppliesAtTheMappedPositions() {
        val gate = CompletableDeferred<Unit>()
        val client = client {
            gate.await()
            resolvedItem
        }
        val session = session(client)
        accept(session, client)
        // Delete "val x = 1\n" while the resolve is in flight: "trans" moves to 19..24,
        // and the old 29..34 is past the end of the 24-character doc.
        session.edit(11, 21)
        gate.complete(Unit)

        assertScopeSurvived()
        assertEquals(emptyList(), logged)
        val text = session.state.doc.toString()
        assertEquals("package p\nimport foo.transform\n\nval y = transform", text)
        assertEquals(text.length, session.state.selection.main.head.value)
    }

    @Test
    fun anAutoImportWhoseRangeWasEditedDuringResolveIsDropped() {
        val gate = CompletableDeferred<Unit>()
        val client = client {
            gate.await()
            resolvedItem
        }
        val session = session(client)
        accept(session, client)
        // Type on the empty line the import targets.
        session.edit(10, 10, "// c")
        gate.complete(Unit)

        assertScopeSurvived()
        assertEquals("package p\n// c\nval x = 1\nval y = transform", session.state.doc.toString())
    }

    @Test
    fun aFailedResolveIsLoggedAndTheNextCompletionWorks() {
        var calls = 0
        val client = client {
            if (calls++ == 0) throw RuntimeException("boom-resolve")
            resolvedItem
        }
        val session = session(client)
        accept(session, client)

        assertScopeSurvived()
        assertEquals(listOf("boom-resolve"), logged)
        // The unresolved item still inserts its label.
        assertEquals("package p\n\nval x = 1\nval y = transform", session.state.doc.toString())

        session.edit(29, 38, "trans")
        accept(session, client)
        assertScopeSurvived()
        assertEquals(listOf("boom-resolve"), logged)
        assertEquals(
            "package p\nimport foo.transform\n\nval x = 1\nval y = transform",
            session.state.doc.toString()
        )
    }

    @Test
    fun textTypedAtTheEndOfTheRangeDuringResolveIsReplaced() {
        val gate = CompletableDeferred<Unit>()
        val client = client {
            gate.await()
            resolvedItem
        }
        val session = session(client)
        accept(session, client)
        // Keep typing the word while the resolve is in flight: `to` maps right, so
        // the replace range grows to cover "transf".
        session.edit(34, 34, "f")
        gate.complete(Unit)

        assertScopeSurvived()
        assertEquals(
            "package p\nimport foo.transform\n\nval x = 1\nval y = transform",
            session.state.doc.toString()
        )
    }

    @Test
    fun disposingTheEditorDuringResolveAppliesNothingAndLogsNothing() {
        val client = client { awaitCancellation() }
        val session = session(client)
        accept(session, client)
        scope.cancel()

        assertEquals(emptyList(), logged)
        assertEquals(emptyList(), unhandled)
        assertEquals(startText, session.state.doc.toString())
    }

    @Test
    fun anUntrackedDocThatChangedDuringResolveIsLeftAlone() {
        val gate = CompletableDeferred<Unit>()
        val client = client {
            gate.await()
            resolvedItem
        }
        // Not opened in the workspace, so the edits can't be mapped.
        val session = session(client, tracked = false)
        accept(session, client)
        session.edit(11, 21)
        gate.complete(Unit)

        assertScopeSurvived()
        assertEquals(emptyList(), logged)
        assertEquals("package p\n\nval y = trans", session.state.doc.toString())
    }

    private companion object {
        const val URI = "file:///a.kt"
    }
}
