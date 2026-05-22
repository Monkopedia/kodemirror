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

/**
 * Represents a single document tracked by a [Workspace].
 *
 * Mirrors upstream `@codemirror/lsp-client`'s `WorkspaceFile`. For now this is a
 * minimal skeleton holding the file's identity and the editor session bound to
 * it; document synchronization (open/change/close notifications, version
 * tracking) is added in a later issue.
 *
 * @param uri The document URI, as understood by the language server.
 * @param languageId The LSP language identifier (e.g. `"kotlin"`, `"json"`).
 * @param session The editor session displaying this file, if any.
 */
class WorkspaceFile(
    val uri: String,
    val languageId: String,
    val session: EditorSession? = null
) {
    /**
     * The document version last synchronized with the server. Incremented as
     * changes are sent.
     *
     * TODO(#36): drive this from document-sync notifications.
     */
    var version: Int = 0
        internal set

    /** Get the current document text, or null if no session is attached. */
    fun getText(): String? = session?.state?.doc?.toString()
}

/**
 * Tracks the set of open files for an [LSPClient].
 *
 * Mirrors upstream `@codemirror/lsp-client`'s `Workspace`, kept intentionally
 * minimal (effectively single-file) for the scaffold. The richer multi-file
 * behavior — syncing files, requesting/connecting files, and handling server
 * notifications — is layered on in later feature issues.
 *
 * @param client The owning [LSPClient].
 */
open class Workspace(
    val client: LSPClient
) {
    private val files = mutableMapOf<String, WorkspaceFile>()

    /** All currently-tracked files. */
    val openFiles: List<WorkspaceFile>
        get() = files.values.toList()

    /** Look up a tracked file by its URI. */
    fun getFile(uri: String): WorkspaceFile? = files[uri]

    /**
     * Register a file as open in the workspace.
     *
     * TODO(#36): send `textDocument/didOpen` to the server here once document
     * sync is implemented.
     */
    open fun openFile(uri: String, languageId: String, session: EditorSession?): WorkspaceFile {
        val file = WorkspaceFile(uri, languageId, session)
        files[uri] = file
        return file
    }

    /**
     * Remove a file from the workspace.
     *
     * TODO(#36): send `textDocument/didClose` to the server here once document
     * sync is implemented.
     */
    open fun closeFile(uri: String) {
        files.remove(uri)
    }
}
