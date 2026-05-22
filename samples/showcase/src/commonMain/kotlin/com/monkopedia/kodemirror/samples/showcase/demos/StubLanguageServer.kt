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
 */
package com.monkopedia.kodemirror.samples.showcase.demos

import com.monkopedia.lsp.CallHierarchyIncomingCall
import com.monkopedia.lsp.CallHierarchyIncomingCallsParams
import com.monkopedia.lsp.CallHierarchyItem
import com.monkopedia.lsp.CallHierarchyOutgoingCall
import com.monkopedia.lsp.CallHierarchyOutgoingCallsParams
import com.monkopedia.lsp.CallHierarchyPrepareParams
import com.monkopedia.lsp.CodeAction
import com.monkopedia.lsp.CodeActionParams
import com.monkopedia.lsp.CodeLens
import com.monkopedia.lsp.CodeLensParams
import com.monkopedia.lsp.ColorInformation
import com.monkopedia.lsp.ColorPresentation
import com.monkopedia.lsp.ColorPresentationParams
import com.monkopedia.lsp.CompletionItem
import com.monkopedia.lsp.CompletionItemKind
import com.monkopedia.lsp.CompletionOptions
import com.monkopedia.lsp.CompletionParams
import com.monkopedia.lsp.CreateFilesParams
import com.monkopedia.lsp.DeclarationParams
import com.monkopedia.lsp.DefinitionParams
import com.monkopedia.lsp.DeleteFilesParams
import com.monkopedia.lsp.Diagnostic
import com.monkopedia.lsp.DiagnosticSeverity
import com.monkopedia.lsp.DidChangeConfigurationParams
import com.monkopedia.lsp.DidChangeNotebookDocumentParams
import com.monkopedia.lsp.DidChangeTextDocumentParams
import com.monkopedia.lsp.DidChangeWatchedFilesParams
import com.monkopedia.lsp.DidChangeWorkspaceFoldersParams
import com.monkopedia.lsp.DidCloseNotebookDocumentParams
import com.monkopedia.lsp.DidCloseTextDocumentParams
import com.monkopedia.lsp.DidOpenNotebookDocumentParams
import com.monkopedia.lsp.DidOpenTextDocumentParams
import com.monkopedia.lsp.DidSaveNotebookDocumentParams
import com.monkopedia.lsp.DidSaveTextDocumentParams
import com.monkopedia.lsp.DocumentColorParams
import com.monkopedia.lsp.DocumentDiagnosticParams
import com.monkopedia.lsp.DocumentDiagnosticReport
import com.monkopedia.lsp.DocumentFormattingParams
import com.monkopedia.lsp.DocumentHighlight
import com.monkopedia.lsp.DocumentHighlightParams
import com.monkopedia.lsp.DocumentLink
import com.monkopedia.lsp.DocumentLinkParams
import com.monkopedia.lsp.DocumentOnTypeFormattingParams
import com.monkopedia.lsp.DocumentRangeFormattingParams
import com.monkopedia.lsp.DocumentRangesFormattingParams
import com.monkopedia.lsp.DocumentSymbolParams
import com.monkopedia.lsp.ExecuteCommandParams
import com.monkopedia.lsp.FoldingRange
import com.monkopedia.lsp.FoldingRangeParams
import com.monkopedia.lsp.Hover
import com.monkopedia.lsp.HoverParams
import com.monkopedia.lsp.ImplementationParams
import com.monkopedia.lsp.InitializeParams
import com.monkopedia.lsp.InitializeResult
import com.monkopedia.lsp.InitializeResultServerInfo
import com.monkopedia.lsp.InitializedParams
import com.monkopedia.lsp.InlayHint
import com.monkopedia.lsp.InlayHintParams
import com.monkopedia.lsp.InlineCompletionParams
import com.monkopedia.lsp.InlineValue
import com.monkopedia.lsp.InlineValueParams
import com.monkopedia.lsp.IntOrString
import com.monkopedia.lsp.LSPAny
import com.monkopedia.lsp.LanguageClient
import com.monkopedia.lsp.LanguageServer
import com.monkopedia.lsp.LinkedEditingRangeParams
import com.monkopedia.lsp.LinkedEditingRanges
import com.monkopedia.lsp.Location
import com.monkopedia.lsp.MarkupContent
import com.monkopedia.lsp.MarkupKind
import com.monkopedia.lsp.Moniker
import com.monkopedia.lsp.MonikerParams
import com.monkopedia.lsp.ParameterInformation
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.PrepareRenameParams
import com.monkopedia.lsp.PrepareRenameResult
import com.monkopedia.lsp.ProgressParams
import com.monkopedia.lsp.PublishDiagnosticsParams
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.ReferenceParams
import com.monkopedia.lsp.RenameFilesParams
import com.monkopedia.lsp.RenameParams
import com.monkopedia.lsp.SelectionRange
import com.monkopedia.lsp.SelectionRangeParams
import com.monkopedia.lsp.SemanticTokens
import com.monkopedia.lsp.SemanticTokensDeltaParams
import com.monkopedia.lsp.SemanticTokensParams
import com.monkopedia.lsp.SemanticTokensRangeParams
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.SetTraceParams
import com.monkopedia.lsp.SignatureHelp
import com.monkopedia.lsp.SignatureHelpOptions
import com.monkopedia.lsp.SignatureHelpParams
import com.monkopedia.lsp.SignatureInformation
import com.monkopedia.lsp.StringOr
import com.monkopedia.lsp.TextDocumentCodeActionResult
import com.monkopedia.lsp.TextDocumentSemanticTokensFullDeltaResult
import com.monkopedia.lsp.TextEdit
import com.monkopedia.lsp.TypeDefinitionParams
import com.monkopedia.lsp.TypeHierarchyItem
import com.monkopedia.lsp.TypeHierarchyPrepareParams
import com.monkopedia.lsp.TypeHierarchySubtypesParams
import com.monkopedia.lsp.TypeHierarchySupertypesParams
import com.monkopedia.lsp.WillSaveTextDocumentParams
import com.monkopedia.lsp.WorkDoneProgressCancelParams
import com.monkopedia.lsp.WorkspaceDiagnosticParams
import com.monkopedia.lsp.WorkspaceDiagnosticReport
import com.monkopedia.lsp.WorkspaceEdit
import com.monkopedia.lsp.WorkspaceSymbol
import com.monkopedia.lsp.WorkspaceSymbolParams
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.encodeToJsonElement

/**
 * An in-memory [LanguageServer] used by [LspDemo] to drive the `:lsp-client`
 * features without a real language process or JSON-RPC transport.
 *
 * **This is sample/demo code, not production code.** It returns canned, hard-coded
 * responses just rich enough to exercise every feature `languageServerSupport`
 * wires up: diagnostics, completion, hover, signature help, go-to-definition,
 * find-references, rename, and formatting. All other [LanguageServer] methods are
 * spec-safe no-op/empty defaults (mirroring how `:lsp-client`'s own
 * `LSPLanguageClient` provides defaults for the server→client methods it does not
 * implement).
 *
 * The stub is single-file: it answers for whatever [uri] the editor was opened
 * with, regardless of the request's document. After construction the consumer
 * registers the client side via [connect] so the stub can push
 * `textDocument/publishDiagnostics` back into the editor when the document is
 * opened or changed.
 */
class StubLanguageServer : LanguageServer {
    /**
     * The client side, registered via [connect]. In a real deployment this is the
     * peer of a JSON-RPC transport; here it is `LSPClient.languageClient`, so the
     * stub can deliver server→client notifications in-process.
     */
    private var client: LanguageClient? = null

    /** Wire the [client] the stub pushes server→client notifications to. */
    fun connect(client: LanguageClient) {
        this.client = client
    }

    // ── lifecycle ──

    override suspend fun initialize(params: InitializeParams): InitializeResult = InitializeResult(
        capabilities = ServerCapabilities(
            // Incremental text sync ({ openClose: true, change: 2 }, 2 = Incremental)
            // so didOpen/didChange flow with ranged edits.
            textDocumentSync = Json.parseToJsonElement("""{"openClose":true,"change":2}"""),
            completionProvider = CompletionOptions(
                triggerCharacters = listOf(".")
            ),
            hoverProvider = boolProvider(),
            signatureHelpProvider = SignatureHelpOptions(
                triggerCharacters = listOf("(", ",")
            ),
            definitionProvider = boolProvider(),
            referencesProvider = boolProvider(),
            renameProvider = boolProvider(),
            documentFormattingProvider = boolProvider()
        ),
        serverInfo = InitializeResultServerInfo(
            name = "kodemirror-stub-language-server",
            version = "1.0.0"
        )
    )

    override suspend fun initialized(params: InitializedParams) = Unit

    override suspend fun shutdown(): Nothing? = null

    override suspend fun exit() = Unit

    // ── document sync → push diagnostics ──

    override suspend fun textDocumentDidOpen(params: DidOpenTextDocumentParams) {
        publishSampleDiagnostics(params.textDocument.uri)
    }

    override suspend fun textDocumentDidChange(params: DidChangeTextDocumentParams) {
        publishSampleDiagnostics(params.textDocument.uri)
    }

    override suspend fun textDocumentDidClose(params: DidCloseTextDocumentParams) = Unit

    override suspend fun textDocumentDidSave(params: DidSaveTextDocumentParams) = Unit

    override suspend fun textDocumentWillSave(params: WillSaveTextDocumentParams) = Unit

    /** Push a couple of canned diagnostics back to the editor for [uri]. */
    private suspend fun publishSampleDiagnostics(uri: String) {
        client?.textDocumentPublishDiagnostics(
            PublishDiagnosticsParams(
                uri = uri,
                diagnostics = listOf(
                    Diagnostic(
                        range = range(0u, 0u, 0u, 5u),
                        severity = DiagnosticSeverity.WARNING,
                        code = IntOrString.StringValue("stub.greeting"),
                        source = "stub-server",
                        message = "Stub diagnostic: 'greet' could be a built-in keyword."
                    ),
                    Diagnostic(
                        range = range(2u, 0u, 2u, 4u),
                        severity = DiagnosticSeverity.INFORMATION,
                        source = "stub-server",
                        message = "Stub diagnostic: consider documenting 'main'."
                    )
                )
            )
        )
    }

    // ── completion ──

    override suspend fun textDocumentCompletion(params: CompletionParams): JsonElement {
        val items = listOf(
            CompletionItem(
                label = "greet",
                kind = CompletionItemKind.FUNCTION,
                detail = "fun greet(name: String): String",
                documentation = StringOr.Value(
                    MarkupContent(
                        kind = MarkupKind.MARKDOWN,
                        value = "Returns a **greeting** for the given name."
                    )
                ),
                insertText = "greet"
            ),
            CompletionItem(
                label = "main",
                kind = CompletionItemKind.KEYWORD,
                detail = "entry point",
                insertText = "main"
            ),
            CompletionItem(
                label = "name",
                kind = CompletionItemKind.VARIABLE,
                detail = "val name: String",
                insertText = "name"
            )
        )
        return Json.encodeToJsonElement(
            kotlinx.serialization.builtins.ListSerializer(CompletionItem.serializer()),
            items
        )
    }

    override suspend fun completionItemResolve(params: CompletionItem): CompletionItem = params

    // ── hover ──

    override suspend fun textDocumentHover(params: HoverParams): Hover = Hover(
        contents = Json.encodeToJsonElement(
            MarkupContent.serializer(),
            MarkupContent(
                kind = MarkupKind.MARKDOWN,
                value = buildString {
                    appendLine("```")
                    appendLine("fun greet(name: String): String")
                    appendLine("```")
                    append("Builds a greeting from the supplied **name**.")
                }
            )
        ),
        range = range(0u, 0u, 0u, 5u)
    )

    // ── signature help ──

    override suspend fun textDocumentSignatureHelp(params: SignatureHelpParams): SignatureHelp =
        SignatureHelp(
            signatures = listOf(
                SignatureInformation(
                    label = "greet(name: String, polite: Boolean = true): String",
                    documentation = StringOr.Value(
                        MarkupContent(
                            kind = MarkupKind.MARKDOWN,
                            value = "Builds a greeting for `name`."
                        )
                    ),
                    parameters = listOf(
                        ParameterInformation(
                            label = Json.encodeToJsonElement("name: String"),
                            documentation = StringOr.StringValue("The name to greet.")
                        ),
                        ParameterInformation(
                            label = Json.encodeToJsonElement("polite: Boolean = true"),
                            documentation = StringOr.StringValue("Whether to be polite.")
                        )
                    )
                )
            ),
            activeSignature = 0u,
            activeParameter = 0u
        )

    // ── definition ──

    override suspend fun textDocumentDefinition(params: DefinitionParams): JsonElement =
        Json.encodeToJsonElement(
            Location.serializer(),
            // Jump to the definition of `greet` on the first line of the sample doc.
            Location(uri = params.textDocument.uri, range = range(0u, 4u, 0u, 9u))
        )

    // ── references ──

    override suspend fun textDocumentReferences(params: ReferenceParams): List<Location> = listOf(
        Location(uri = params.textDocument.uri, range = range(0u, 4u, 0u, 9u)),
        Location(uri = params.textDocument.uri, range = range(3u, 4u, 3u, 9u))
    )

    // ── rename ──

    override suspend fun textDocumentRename(params: RenameParams): WorkspaceEdit {
        val newName = params.newName
        return WorkspaceEdit(
            changes = mapOf(
                params.textDocument.uri to listOf(
                    TextEdit(range = range(0u, 4u, 0u, 9u), newText = newName),
                    TextEdit(range = range(3u, 4u, 3u, 9u), newText = newName)
                )
            )
        )
    }

    // ── formatting ──

    override suspend fun textDocumentFormatting(params: DocumentFormattingParams): List<TextEdit> =
        listOf(
            // Canned edit: prepend a header comment at the start of the document.
            TextEdit(range = range(0u, 0u, 0u, 0u), newText = "// formatted by stub server\n")
        )

    // ─────────────────────────────────────────────────────────────────────────
    // Spec-safe no-op / empty defaults for every other LanguageServer method.
    // The client only calls methods whose capability the stub advertised, so
    // these are never reached in this demo; they exist to satisfy the interface.
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun textDocumentImplementation(params: ImplementationParams): JsonElement =
        JsonNull

    override suspend fun textDocumentTypeDefinition(params: TypeDefinitionParams): JsonElement =
        JsonNull

    override suspend fun textDocumentDocumentColor(
        params: DocumentColorParams
    ): List<ColorInformation> = emptyList()

    override suspend fun textDocumentColorPresentation(
        params: ColorPresentationParams
    ): List<ColorPresentation> = emptyList()

    override suspend fun textDocumentFoldingRange(
        params: FoldingRangeParams
    ): List<FoldingRange> = emptyList()

    override suspend fun textDocumentDeclaration(params: DeclarationParams): JsonElement = JsonNull

    override suspend fun textDocumentSelectionRange(
        params: SelectionRangeParams
    ): List<SelectionRange> = emptyList()

    override suspend fun textDocumentPrepareCallHierarchy(
        params: CallHierarchyPrepareParams
    ): List<CallHierarchyItem> = emptyList()

    override suspend fun callHierarchyIncomingCalls(
        params: CallHierarchyIncomingCallsParams
    ): List<CallHierarchyIncomingCall> = emptyList()

    override suspend fun callHierarchyOutgoingCalls(
        params: CallHierarchyOutgoingCallsParams
    ): List<CallHierarchyOutgoingCall> = emptyList()

    override suspend fun textDocumentSemanticTokensFull(
        params: SemanticTokensParams
    ): SemanticTokens = SemanticTokens(`data` = emptyList())

    override suspend fun textDocumentSemanticTokensFullDelta(
        params: SemanticTokensDeltaParams
    ): TextDocumentSemanticTokensFullDeltaResult = SemanticTokens(`data` = emptyList())

    override suspend fun textDocumentSemanticTokensRange(
        params: SemanticTokensRangeParams
    ): SemanticTokens = SemanticTokens(`data` = emptyList())

    override suspend fun textDocumentLinkedEditingRange(
        params: LinkedEditingRangeParams
    ): LinkedEditingRanges = LinkedEditingRanges(ranges = emptyList())

    override suspend fun workspaceWillCreateFiles(params: CreateFilesParams): WorkspaceEdit =
        WorkspaceEdit()

    override suspend fun workspaceWillRenameFiles(params: RenameFilesParams): WorkspaceEdit =
        WorkspaceEdit()

    override suspend fun workspaceWillDeleteFiles(params: DeleteFilesParams): WorkspaceEdit =
        WorkspaceEdit()

    override suspend fun textDocumentMoniker(params: MonikerParams): List<Moniker> = emptyList()

    override suspend fun textDocumentPrepareTypeHierarchy(
        params: TypeHierarchyPrepareParams
    ): List<TypeHierarchyItem> = emptyList()

    override suspend fun typeHierarchySupertypes(
        params: TypeHierarchySupertypesParams
    ): List<TypeHierarchyItem> = emptyList()

    override suspend fun typeHierarchySubtypes(
        params: TypeHierarchySubtypesParams
    ): List<TypeHierarchyItem> = emptyList()

    override suspend fun textDocumentInlineValue(
        params: InlineValueParams
    ): List<InlineValue> = emptyList()

    override suspend fun textDocumentInlayHint(params: InlayHintParams): List<InlayHint> =
        emptyList()

    override suspend fun inlayHintResolve(params: InlayHint): InlayHint = params

    override suspend fun textDocumentDiagnostic(
        params: DocumentDiagnosticParams
    ): DocumentDiagnosticReport =
        com.monkopedia.lsp.RelatedFullDocumentDiagnosticReport(kind = "full", items = emptyList())

    override suspend fun workspaceDiagnostic(
        params: WorkspaceDiagnosticParams
    ): WorkspaceDiagnosticReport = WorkspaceDiagnosticReport(items = emptyList())

    override suspend fun textDocumentInlineCompletion(
        params: InlineCompletionParams
    ): JsonElement = JsonNull

    override suspend fun textDocumentWillSaveWaitUntil(
        params: WillSaveTextDocumentParams
    ): List<TextEdit> = emptyList()

    override suspend fun textDocumentDocumentHighlight(
        params: DocumentHighlightParams
    ): List<DocumentHighlight> = emptyList()

    override suspend fun textDocumentDocumentSymbol(params: DocumentSymbolParams): JsonElement =
        buildJsonArray { }

    override suspend fun textDocumentCodeAction(
        params: CodeActionParams
    ): List<TextDocumentCodeActionResult> = emptyList()

    override suspend fun codeActionResolve(params: CodeAction): CodeAction = params

    override suspend fun workspaceSymbol(params: WorkspaceSymbolParams): JsonElement =
        buildJsonArray { }

    override suspend fun workspaceSymbolResolve(params: WorkspaceSymbol): WorkspaceSymbol = params

    override suspend fun textDocumentCodeLens(params: CodeLensParams): List<CodeLens> = emptyList()

    override suspend fun codeLensResolve(params: CodeLens): CodeLens = params

    override suspend fun textDocumentDocumentLink(
        params: DocumentLinkParams
    ): List<DocumentLink> = emptyList()

    override suspend fun documentLinkResolve(params: DocumentLink): DocumentLink = params

    override suspend fun textDocumentRangeFormatting(
        params: DocumentRangeFormattingParams
    ): List<TextEdit> = emptyList()

    override suspend fun textDocumentRangesFormatting(
        params: DocumentRangesFormattingParams
    ): List<TextEdit> = emptyList()

    override suspend fun textDocumentOnTypeFormatting(
        params: DocumentOnTypeFormattingParams
    ): List<TextEdit> = emptyList()

    override suspend fun textDocumentPrepareRename(
        params: PrepareRenameParams
    ): PrepareRenameResult = Json.encodeToJsonElement(Range.serializer(), range(0u, 4u, 0u, 9u))

    override suspend fun workspaceExecuteCommand(params: ExecuteCommandParams): LSPAny = JsonNull

    override suspend fun workspaceDidChangeWorkspaceFolders(
        params: DidChangeWorkspaceFoldersParams
    ) = Unit

    override suspend fun windowWorkDoneProgressCancel(params: WorkDoneProgressCancelParams) = Unit

    override suspend fun workspaceDidCreateFiles(params: CreateFilesParams) = Unit

    override suspend fun workspaceDidRenameFiles(params: RenameFilesParams) = Unit

    override suspend fun workspaceDidDeleteFiles(params: DeleteFilesParams) = Unit

    override suspend fun notebookDocumentDidOpen(params: DidOpenNotebookDocumentParams) = Unit

    override suspend fun notebookDocumentDidChange(params: DidChangeNotebookDocumentParams) = Unit

    override suspend fun notebookDocumentDidSave(params: DidSaveNotebookDocumentParams) = Unit

    override suspend fun notebookDocumentDidClose(params: DidCloseNotebookDocumentParams) = Unit

    override suspend fun workspaceDidChangeConfiguration(
        params: DidChangeConfigurationParams
    ) = Unit

    override suspend fun workspaceDidChangeWatchedFiles(
        params: DidChangeWatchedFilesParams
    ) = Unit

    override suspend fun setTrace(params: SetTraceParams) = Unit

    override suspend fun progress(params: ProgressParams) = Unit
}

/** Advertise a capability as a plain boolean `true`. */
private fun boolProvider(): com.monkopedia.lsp.BooleanOr<Nothing> =
    com.monkopedia.lsp.BooleanOr.BooleanValue(true)

/** Build an LSP [Range] from 0-based line/character pairs. */
private fun range(
    startLine: UInt,
    startChar: UInt,
    endLine: UInt,
    endChar: UInt
): Range = Range(
    start = Position(line = startLine, character = startChar),
    end = Position(line = endLine, character = endChar)
)
