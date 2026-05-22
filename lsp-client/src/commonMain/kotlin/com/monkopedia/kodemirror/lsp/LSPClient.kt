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

import com.monkopedia.lsp.ClientCapabilities
import com.monkopedia.lsp.InitializeParams
import com.monkopedia.lsp.InitializeParamsClientInfo
import com.monkopedia.lsp.InitializeResult
import com.monkopedia.lsp.InitializedParams
import com.monkopedia.lsp.LanguageClient
import com.monkopedia.lsp.LanguageServer
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.WorkspaceFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Configuration for an [LSPClient].
 *
 * Mirrors the shape of upstream `@codemirror/lsp-client`'s `LSPClientConfig`,
 * but omits the transport-level options (the JSON-RPC connection is replaced by
 * a directly-provided [LanguageServer], see [LSPClient]).
 *
 * @param rootUri The root URI of the workspace, sent during `initialize`.
 * @param workspaceFolders The workspace folders to advertise to the server.
 * @param clientInfo Optional client name/version reported to the server.
 * @param capabilities The [ClientCapabilities] advertised during `initialize`.
 * @param initializationOptions Optional server-specific initialization options.
 * @param createWorkspace Factory used to build the [Workspace] managed by the
 *   client. Defaults to a single-file workspace.
 */
data class LSPClientConfig(
    val rootUri: String? = null,
    val workspaceFolders: List<WorkspaceFolder>? = null,
    val clientInfo: InitializeParamsClientInfo? = DEFAULT_CLIENT_INFO,
    val capabilities: ClientCapabilities = ClientCapabilities(),
    val initializationOptions: com.monkopedia.lsp.LSPAny? = null,
    val createWorkspace: (LSPClient) -> Workspace = { client -> Workspace(client) }
) {
    companion object {
        /** Default client info reported to language servers. */
        val DEFAULT_CLIENT_INFO: InitializeParamsClientInfo =
            InitializeParamsClientInfo(name = "kodemirror-lsp-client")
    }
}

/**
 * A client that connects a kodemirror editor to a language server.
 *
 * **Deviation from upstream:** upstream `@codemirror/lsp-client` owns the
 * JSON-RPC `Transport` and serializes/deserializes LSP messages itself. In
 * kodemirror the consumer instead *provides* a [LanguageServer] — the typed
 * suspend interface from `com.monkopedia.lsp` — and this client simply wraps it.
 * That means transport, framing and JSON-RPC concerns live entirely in the
 * provided [LanguageServer] implementation (e.g. backed by `lsp-ksrpc`), and
 * this class focuses on lifecycle (`initialize`/`initialized`/`shutdown`) and
 * exposing typed suspend calls plus [serverCapabilities].
 *
 * @param server The language server implementation supplied by the consumer.
 * @param config Client configuration. See [LSPClientConfig].
 */
class LSPClient(
    val server: LanguageServer,
    val config: LSPClientConfig = LSPClientConfig()
) {
    private val initMutex = Mutex()
    private val syncMutex = Mutex()

    /**
     * Client-lifetime coroutine scope, used for work that must outlive any
     * single editor session (e.g. sending `textDocument/didClose` as a plugin
     * tears down its own scope).
     */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob())

    /** The full result of the `initialize` request, or null until initialized. */
    var initializeResult: InitializeResult? = null
        private set

    /** The [Workspace] managed by this client. */
    val workspace: Workspace = config.createWorkspace(this)

    /**
     * The [LanguageClient] the consumer registers as the client side of their
     * transport (e.g. ksrpc's `connectAsLspClient`).
     *
     * **Deviation from upstream:** as with [server], the transport lives in the
     * consumer's code rather than in this module. By handing the consumer this
     * [LanguageClient], server→client notifications — most importantly
     * `textDocument/publishDiagnostics` — are routed back into the editor: the
     * handler resolves the file/session via the [workspace] and pushes
     * diagnostics into `:lint` (see [serverDiagnostics]). Other server→client
     * methods have spec-safe defaults until later feature issues fill them in.
     */
    val languageClient: LanguageClient = LSPLanguageClient(this)

    /**
     * The capabilities reported by the server during [initialize], or null if
     * the client has not been initialized yet.
     */
    val serverCapabilities: ServerCapabilities?
        get() = initializeResult?.capabilities

    /** Whether [initialize] has completed successfully. */
    val isInitialized: Boolean
        get() = initializeResult != null

    /**
     * Perform the LSP `initialize` / `initialized` handshake.
     *
     * Idempotent: if the client is already initialized this returns the cached
     * [InitializeResult] without contacting the server again.
     */
    suspend fun initialize(): InitializeResult = initMutex.withLock {
        initializeResult?.let { return it }
        val params = InitializeParams(
            processId = null,
            clientInfo = config.clientInfo,
            rootUri = config.rootUri,
            capabilities = config.capabilities,
            initializationOptions = config.initializationOptions,
            workspaceFolders = config.workspaceFolders
        )
        val result = server.initialize(params)
        initializeResult = result
        server.initialized(InitializedParams())
        result
    }

    /**
     * Flush any pending document changes to the server.
     *
     * Mirrors upstream `@codemirror/lsp-client`'s `LSPClient.sync()`: it asks the
     * [workspace] for the set of files with unsynced changes
     * ([Workspace.syncFiles]) and emits a `textDocument/didChange` notification
     * for each, honoring the negotiated [DocumentSyncMode]. Calls are serialized
     * so notifications go out in version order.
     *
     * No-op when the client has not been initialized yet.
     */
    suspend fun sync(): Unit = syncMutex.withLock {
        if (!isInitialized) return
        for (update in workspace.syncFiles()) {
            workspace.sendChange(update)
        }
    }

    /**
     * Shut the server down via the LSP `shutdown` / `exit` sequence.
     *
     * Safe to call when not initialized — it simply returns.
     */
    suspend fun shutdown() {
        if (initializeResult == null) return
        server.shutdown()
        server.exit()
        initializeResult = null
    }
}
