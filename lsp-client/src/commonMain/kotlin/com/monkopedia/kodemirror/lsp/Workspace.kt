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

import com.monkopedia.kodemirror.state.ChangeSet
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.lsp.DidChangeTextDocumentParams
import com.monkopedia.lsp.DidCloseTextDocumentParams
import com.monkopedia.lsp.DidOpenTextDocumentParams
import com.monkopedia.lsp.TextDocumentIdentifier
import com.monkopedia.lsp.TextDocumentItem
import com.monkopedia.lsp.VersionedTextDocumentIdentifier

/**
 * A pending update for a file, produced by [Workspace.syncFiles].
 *
 * Mirrors upstream `@codemirror/lsp-client`'s `WorkspaceFileUpdate`. It carries
 * the file along with the document as the server last saw it ([prevDoc]) and the
 * accumulated [changes] to send. [LSPClient.sync] consumes these to emit
 * `textDocument/didChange` notifications.
 *
 * @param file The file being updated.
 * @param prevDoc The document as last synchronized with the server.
 * @param changes The changes to apply to [prevDoc] to reach [WorkspaceFile.doc].
 */
data class WorkspaceFileUpdate(
    val file: WorkspaceFile,
    val prevDoc: Text,
    val changes: ChangeSet
)

/**
 * Represents a single document tracked by a [Workspace].
 *
 * Mirrors upstream `@codemirror/lsp-client`'s `WorkspaceFile`. It holds the
 * file's identity, the editor session bound to it, the document text last
 * synchronized to the server, the synced [version], and any pending changes that
 * have not yet been flushed via [Workspace.syncFiles].
 *
 * @param uri The document URI, as understood by the language server.
 * @param languageId The LSP language identifier (e.g. `"kotlin"`, `"json"`).
 * @param session The editor session displaying this file, if any.
 */
class WorkspaceFile internal constructor(
    val uri: String,
    val languageId: String,
    val session: EditorSession?,
    initialDoc: Text
) {
    /**
     * The document version last synchronized with the server. Incremented each
     * time changes are flushed to the server.
     */
    var version: Int = 1
        internal set

    /** The document text as the server last saw it. */
    var doc: Text = initialDoc
        internal set

    /** Pending, not-yet-synchronized changes (relative to [doc]), or null. */
    internal var pendingChanges: ChangeSet? = null

    /** Live mappings handed out for in-flight requests against this file. */
    private val mappings = mutableListOf<WorkspaceMapping>()

    /** Get the current document text, or null if no session is attached. */
    fun getText(): String? = session?.state?.doc?.toString()

    /**
     * Record that the document changed by [change]. Accumulates onto any pending
     * changes and updates outstanding [WorkspaceMapping]s so in-flight requests
     * can be remapped.
     */
    internal fun applyChange(change: ChangeSet) {
        if (change.empty) return
        val pending = pendingChanges
        pendingChanges = if (pending == null) change else pending.compose(change)
        for (mapping in mappings) mapping.addChanges(change.desc)
    }

    /** Create and register a live [WorkspaceMapping] for this file. */
    internal fun createMapping(): WorkspaceMapping {
        val mapping = WorkspaceMapping(uri)
        mappings.add(mapping)
        return mapping
    }

    /** Stop tracking [mapping], so it no longer receives change updates. */
    internal fun releaseMapping(mapping: WorkspaceMapping) {
        mappings.remove(mapping)
    }

    /**
     * Take any pending changes as a [WorkspaceFileUpdate], advancing [version]
     * and [doc] to the post-change state, or null when there is nothing to sync.
     */
    internal fun takeUpdate(): WorkspaceFileUpdate? {
        val pending = pendingChanges ?: return null
        pendingChanges = null
        val prevDoc = doc
        val newDoc = pending.apply(prevDoc)
        doc = newDoc
        version += 1
        return WorkspaceFileUpdate(this, prevDoc, pending)
    }
}

/**
 * Tracks the set of open files for an [LSPClient] and synchronizes their
 * contents with the language server.
 *
 * Mirrors upstream `@codemirror/lsp-client`'s `Workspace`. Files are registered
 * via [openFile] (which sends `textDocument/didOpen`) and removed via [closeFile]
 * (which sends `textDocument/didClose`). Editor changes are recorded with
 * [updateFile] and later flushed with [syncFiles] / [LSPClient.sync], honoring
 * the server's negotiated [DocumentSyncMode].
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
     * Obtain a live [WorkspaceMapping] for [uri] that tracks edits applied after
     * this call, or null if no such file is open. A feature that issues an LSP
     * request should capture a mapping when it sends the request and use it to
     * remap the response's positions; call [releaseMapping] when done.
     */
    fun getMapping(uri: String): WorkspaceMapping? = files[uri]?.createMapping()

    /** Release a [mapping] previously obtained from [getMapping]. */
    fun releaseMapping(mapping: WorkspaceMapping) {
        files[mapping.uri]?.releaseMapping(mapping)
    }

    /**
     * Register a file as open in the workspace, snapshotting its current text as
     * the version-1 baseline. Synchronous so it can run from a plugin's
     * constructor; the `textDocument/didOpen` notification is sent separately by
     * [didOpenFile] once the server handshake has completed.
     */
    open fun openFile(uri: String, languageId: String, session: EditorSession?): WorkspaceFile {
        val initialDoc = session?.state?.doc ?: Text.empty
        val file = WorkspaceFile(uri, languageId, session, initialDoc)
        files[uri] = file
        return file
    }

    /**
     * Send the `textDocument/didOpen` notification for [uri], if it is open and
     * the server's sync capability advertises open/close notifications
     * ([DocumentSyncMode.openClose]).
     */
    open suspend fun didOpenFile(uri: String) {
        val file = files[uri] ?: return
        val mode = DocumentSyncMode.forCapabilities(client.serverCapabilities)
        if (!mode.openClose) return
        client.server.textDocumentDidOpen(
            DidOpenTextDocumentParams(
                textDocument = TextDocumentItem(
                    uri = file.uri,
                    languageId = file.languageId,
                    version = file.version,
                    text = file.doc.toString()
                )
            )
        )
    }

    /**
     * Record an editor [change] against the file [uri].
     *
     * Pending changes accumulate until flushed by [syncFiles] / [LSPClient.sync].
     */
    open fun updateFile(uri: String, change: ChangeSet) {
        files[uri]?.applyChange(change)
    }

    /**
     * Take all pending file updates, advancing each file's version/doc. Returns
     * the updates that should be sent to the server as `textDocument/didChange`.
     *
     * Mirrors upstream `Workspace.syncFiles()`.
     */
    open fun syncFiles(): List<WorkspaceFileUpdate> = files.values.mapNotNull { it.takeUpdate() }

    /**
     * Remove a file from the workspace. Synchronous so it can run from a
     * plugin's `destroy`; the `textDocument/didClose` notification is sent
     * separately by [didCloseFile].
     */
    open fun closeFile(uri: String) {
        files.remove(uri)
    }

    /**
     * Send the `textDocument/didClose` notification for [uri], if the server's
     * sync capability advertises open/close notifications
     * ([DocumentSyncMode.openClose]).
     */
    open suspend fun didCloseFile(uri: String) {
        val mode = DocumentSyncMode.forCapabilities(client.serverCapabilities)
        if (!mode.openClose) return
        client.server.textDocumentDidClose(
            DidCloseTextDocumentParams(
                textDocument = TextDocumentIdentifier(uri = uri)
            )
        )
    }

    /**
     * Send the `textDocument/didChange` notification for a single file [update],
     * honoring the negotiated [DocumentSyncMode].
     */
    internal suspend fun sendChange(update: WorkspaceFileUpdate) {
        val mode = DocumentSyncMode.forCapabilities(client.serverCapabilities)
        if (!mode.syncsChanges) return
        val contentChanges = buildContentChanges(
            changes = update.changes,
            prevDoc = update.prevDoc,
            newDoc = update.file.doc,
            mode = mode
        )
        client.server.textDocumentDidChange(
            DidChangeTextDocumentParams(
                textDocument = VersionedTextDocumentIdentifier(
                    uri = update.file.uri,
                    version = update.file.version
                ),
                contentChanges = contentChanges
            )
        )
    }
}
