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

import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList

/**
 * Public entry point: build the editor [Extension] that connects an editor for
 * a single file to a language server through [client].
 *
 * Mirrors upstream `@codemirror/lsp-client`'s `languageServerSupport`. It
 * registers the [LSPPlugin], server-pushed diagnostics, and server completion;
 * the remaining feature extensions (hover, signature help, rename, etc.) are
 * appended to the returned [ExtensionList] by later issues.
 *
 * @param client The [LSPClient] wrapping the provided language server.
 * @param uri The document URI for this editor's file.
 * @param languageId The LSP language identifier for this file
 *   (e.g. `"kotlin"`, `"json"`).
 * @return An [Extension] to include in the editor state's configuration.
 */
fun languageServerSupport(client: LSPClient, uri: String, languageId: String): Extension {
    val plugin = LSPPlugin.define(client, uri, languageId)
    return ExtensionList(
        listOf(
            // The plugin owns document sync (didOpen/didChange/didClose,
            // version tracking, and position mapping) — see #36.
            plugin.asExtension(),
            // Render diagnostics the server pushes via publishDiagnostics — see
            // #37. The notification handler lives on client.languageClient.
            serverDiagnostics(),
            // Request completions from the server as the editor's autocomplete
            // source — see #38.
            serverCompletion(client, uri)
            // TODO(#39): hover tooltips
            // TODO(#40): signature help
            // TODO(#41): go-to-definition
            // TODO(#42): find references
            // TODO(#43): rename
            // TODO(#44): formatting
            // TODO(#45): code actions
        )
    )
}
