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

import com.monkopedia.kodemirror.lint.Diagnostic as LintDiagnostic
import com.monkopedia.kodemirror.lint.Severity
import com.monkopedia.kodemirror.lint.lintDisplay
import com.monkopedia.kodemirror.lint.setDiagnostics
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.lsp.ApplyWorkspaceEditParams
import com.monkopedia.lsp.ApplyWorkspaceEditResult
import com.monkopedia.lsp.ConfigurationParams
import com.monkopedia.lsp.Diagnostic as LSPDiagnostic
import com.monkopedia.lsp.DiagnosticSeverity
import com.monkopedia.lsp.IntOrString
import com.monkopedia.lsp.LSPAny
import com.monkopedia.lsp.LanguageClient
import com.monkopedia.lsp.LogMessageParams
import com.monkopedia.lsp.LogTraceParams
import com.monkopedia.lsp.MessageActionItem
import com.monkopedia.lsp.ProgressParams
import com.monkopedia.lsp.PublishDiagnosticsParams
import com.monkopedia.lsp.RegistrationParams
import com.monkopedia.lsp.ShowDocumentParams
import com.monkopedia.lsp.ShowDocumentResult
import com.monkopedia.lsp.ShowMessageParams
import com.monkopedia.lsp.ShowMessageRequestParams
import com.monkopedia.lsp.UnregistrationParams
import com.monkopedia.lsp.WorkDoneProgressCreateParams
import com.monkopedia.lsp.WorkspaceFolder
import kotlinx.serialization.json.JsonNull

/**
 * Map an LSP [DiagnosticSeverity] to a `:lint` [Severity].
 *
 * Per the LSP spec the severity is `1=Error, 2=Warning, 3=Information,
 * 4=Hint`. When the server omits the severity the spec leaves interpretation to
 * the client; following upstream `@codemirror/lsp-client` we default to
 * [Severity.ERROR] so unclassified problems are not silently downplayed.
 */
internal fun mapSeverity(severity: DiagnosticSeverity?): Severity = when (severity) {
    DiagnosticSeverity.ERROR -> Severity.ERROR
    DiagnosticSeverity.WARNING -> Severity.WARNING
    DiagnosticSeverity.INFORMATION -> Severity.INFO
    DiagnosticSeverity.HINT -> Severity.HINT
    else -> Severity.ERROR
}

/** Render an LSP diagnostic [code] for inclusion in the displayed message. */
private fun IntOrString.render(): String = when (this) {
    is IntOrString.IntValue -> value.toString()
    is IntOrString.StringValue -> value
}

/**
 * Map a single LSP [Diagnostic][LSPDiagnostic] to a `:lint`
 * [Diagnostic][LintDiagnostic], resolving its range against [doc].
 *
 * The LSP range's start/end [positions][com.monkopedia.lsp.Position] are
 * converted to document offsets with [fromPosition] (clamping out-of-range
 * values to the document, per spec). The [source] and [code] are folded into
 * the displayed message — `:lint`'s `Diagnostic` only has a single `source`
 * slot, so the more detailed `"source(code)"` prefix is carried in the message
 * the way upstream renders it.
 */
internal fun mapDiagnostic(diagnostic: LSPDiagnostic, doc: Text): LintDiagnostic {
    val from = fromPosition(diagnostic.range.start, doc)
    val to = fromPosition(diagnostic.range.end, doc)
    val code = diagnostic.code?.render()
    val message = when {
        code != null -> "${diagnostic.message} [$code]"
        else -> diagnostic.message
    }
    return LintDiagnostic(
        from = DocPos(minOf(from, to)),
        to = DocPos(maxOf(from, to)),
        severity = mapSeverity(diagnostic.severity),
        message = message,
        source = diagnostic.source
    )
}

/**
 * Map a list of LSP diagnostics to `:lint` diagnostics against [doc].
 *
 * Mirrors the per-diagnostic translation upstream `@codemirror/lsp-client`
 * performs in its `serverDiagnostics` handler.
 */
internal fun mapDiagnostics(diagnostics: List<LSPDiagnostic>, doc: Text): List<LintDiagnostic> =
    diagnostics.map { mapDiagnostic(it, doc) }

/**
 * The [LanguageClient] implementation that an [LSPClient] hands to the consumer
 * to register with their transport.
 *
 * **Wiring (consumer-facing):** the JSON-RPC transport lives in the consumer's
 * code (e.g. ksrpc's `connectAsLspClient`). The consumer registers
 * [LSPClient.languageClient] as the client side of that connection, so the
 * server's `textDocument/publishDiagnostics` notifications are delivered to
 * [textDocumentPublishDiagnostics]. That handler looks the file up in the
 * client's [Workspace] by URI and, if it has an attached editor session, maps
 * the diagnostics to `:lint` diagnostics and pushes them in with
 * [setDiagnostics] — which the [serverDiagnostics] extension renders.
 *
 * Mirrors upstream `@codemirror/lsp-client`, where the client's notification
 * handler for `publishDiagnostics` feeds `@codemirror/lint`. The remaining
 * server→client requests are not part of the diagnostics feature (#37); they
 * have spec-safe default responses here and are filled in by later issues.
 */
internal class LSPLanguageClient(private val client: LSPClient) : LanguageClient {
    override suspend fun textDocumentPublishDiagnostics(params: PublishDiagnosticsParams) {
        val file = client.workspace.getFile(params.uri) ?: return
        val session = file.session ?: return
        val doc = session.state.doc
        val mapped = mapDiagnostics(params.diagnostics, doc)
        setDiagnostics(session, mapped)
    }

    // --- Server→client requests/notifications outside the diagnostics scope. ---
    // Spec-safe defaults; individual features wire these up in later issues.

    override suspend fun workspaceWorkspaceFolders(): List<WorkspaceFolder> =
        client.config.workspaceFolders ?: emptyList()

    override suspend fun workspaceConfiguration(params: ConfigurationParams): List<LSPAny> =
        params.items.map { JsonNull }

    override suspend fun workspaceFoldingRangeRefresh(): Nothing? = null

    override suspend fun windowWorkDoneProgressCreate(
        params: WorkDoneProgressCreateParams
    ): Nothing? = null

    override suspend fun workspaceSemanticTokensRefresh(): Nothing? = null

    override suspend fun windowShowDocument(params: ShowDocumentParams): ShowDocumentResult =
        ShowDocumentResult(success = false)

    override suspend fun workspaceInlineValueRefresh(): Nothing? = null

    override suspend fun workspaceInlayHintRefresh(): Nothing? = null

    override suspend fun workspaceDiagnosticRefresh(): Nothing? = null

    override suspend fun clientRegisterCapability(params: RegistrationParams): Nothing? = null

    override suspend fun clientUnregisterCapability(params: UnregistrationParams): Nothing? = null

    override suspend fun windowShowMessageRequest(
        params: ShowMessageRequestParams
    ): MessageActionItem = MessageActionItem(title = "")

    override suspend fun workspaceCodeLensRefresh(): Nothing? = null

    override suspend fun workspaceApplyEdit(
        params: ApplyWorkspaceEditParams
    ): ApplyWorkspaceEditResult = ApplyWorkspaceEditResult(applied = false)

    override suspend fun windowShowMessage(params: ShowMessageParams) = Unit

    override suspend fun windowLogMessage(params: LogMessageParams) = Unit

    override suspend fun telemetryEvent(params: LSPAny) = Unit

    override suspend fun logTrace(params: LogTraceParams) = Unit

    override suspend fun progress(params: ProgressParams) = Unit
}

/**
 * Editor extension that renders LSP diagnostics pushed by the language server.
 *
 * Mirrors upstream `@codemirror/lsp-client`'s `serverDiagnostics()`. It installs
 * `:lint`'s diagnostic-display extension ([lintDisplay]) — the underline
 * decorations, hover tooltip, lint panel and keymap — but no local lint source,
 * because the diagnostics arrive from the server via
 * [LSPClient.languageClient]'s `textDocument/publishDiagnostics` handler rather
 * than from a local linter. Folded into [languageServerSupport].
 */
fun serverDiagnostics(): Extension = lintDisplay()
