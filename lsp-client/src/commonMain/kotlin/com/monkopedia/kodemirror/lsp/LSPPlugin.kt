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

import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The [ViewPlugin] that ties an [LSPClient] to a single editor session for a
 * given file URI and language id.
 *
 * Mirrors upstream `@codemirror/lsp-client`'s `LSPPlugin`. On creation it
 * registers the file with the client's [Workspace] and ensures the client is
 * initialized; on destroy it unregisters the file. Feature behavior (sending
 * document changes, requesting completions/hover/diagnostics, etc.) is wired in
 * by later feature issues.
 *
 * @param session The editor session this plugin is attached to.
 * @param client The LSP client to communicate with.
 * @param uri The document URI for this editor's file.
 * @param languageId The LSP language identifier for this file.
 */
class LSPPlugin internal constructor(
    val session: EditorSession,
    val client: LSPClient,
    val uri: String,
    val languageId: String
) : PluginValue {
    private val job = SupervisorJob(session.coroutineScope.coroutineContext[Job])

    /** Coroutine scope tied to this plugin's lifecycle. */
    val scope: CoroutineScope = CoroutineScope(session.coroutineScope.coroutineContext + job)

    /** The [WorkspaceFile] this plugin manages within the client's workspace. */
    val file: WorkspaceFile

    init {
        file = client.workspace.openFile(uri, languageId, session)
        scope.launch {
            // Ensure the server handshake has completed before any feature
            // wiring attempts to use it.
            client.initialize()
            // TODO(#36): send textDocument/didOpen once document sync lands.
        }
    }

    override fun update(update: ViewUpdate) {
        // TODO(#36): forward document changes as textDocument/didChange.
        // TODO(#37..#45): trigger feature updates (diagnostics, signature help, ...).
    }

    override fun destroy() {
        // TODO(#36): send textDocument/didClose before unregistering.
        client.workspace.closeFile(uri)
        job.cancel()
    }

    companion object {
        /**
         * Build the [ViewPlugin] descriptor for an [LSPPlugin] bound to the
         * given [client]/[uri]/[languageId].
         */
        internal fun define(
            client: LSPClient,
            uri: String,
            languageId: String
        ): ViewPlugin<LSPPlugin> = ViewPlugin.define(
            create = { session -> LSPPlugin(session, client, uri, languageId) }
        )

        /**
         * Retrieve the active [LSPPlugin] for a session, or null if none is
         * attached. Useful for feature extensions added in later issues.
         */
        fun get(session: EditorSession, plugin: ViewPlugin<LSPPlugin>): LSPPlugin? =
            session.plugin(plugin)
    }
}
