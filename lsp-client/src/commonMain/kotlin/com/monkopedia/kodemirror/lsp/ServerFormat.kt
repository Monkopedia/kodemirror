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

import com.monkopedia.kodemirror.state.ChangeDesc
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.Prec
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.define
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.keymap as keymapFacet
import com.monkopedia.lsp.DocumentFormattingParams
import com.monkopedia.lsp.FormattingOptions
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.TextDocumentIdentifier
import com.monkopedia.lsp.TextEdit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The per-editor formatting binding: the [client] wrapping the language server
 * and the document [uri] for this editor's file.
 *
 * Carried through state by [formatServer] so [formatDocument] reads its
 * configuration from the editor session it runs in rather than capturing it at
 * install time. This is what keeps the command per-editor when multiple editors
 * are open — there is no module-level mutable per-editor state (mirroring the
 * per-editor design used by [renameServer]/[definitionServer]).
 */
internal data class FormatServer(val client: LSPClient, val uri: String)

/**
 * The [Facet] carrying the per-editor [FormatServer] binding.
 *
 * Combines to the last non-null value, so an editor that installs
 * [formatDocumentExtension] gets its own [client][FormatServer.client] /
 * [uri][FormatServer.uri] while editors without it see null (and [formatDocument]
 * no-ops, returning false).
 */
internal val formatServer: Facet<FormatServer?, FormatServer?> =
    Facet.define(combine = { values -> values.lastOrNull { it != null } })

/**
 * Whether the server advertises the `documentFormattingProvider` capability.
 *
 * Matches upstream's `hasCapability("documentFormattingProvider")` gating:
 * proceed when the client is not yet initialized (capabilities unknown) or when
 * the capability is present and truthy; bail only when it is explicitly absent or
 * `false`.
 */
private fun FormatServer.supportsFormatting(): Boolean {
    val caps: ServerCapabilities = client.serverCapabilities ?: return true
    return hasProvider(caps.documentFormattingProvider)
}

/**
 * Derive the LSP [FormattingOptions] for [state].
 *
 * Ports upstream `@codemirror/lsp-client`'s `formatDocument` option derivation:
 * - [tabSize][FormattingOptions.tabSize] comes from the editor's
 *   [tabSize][EditorState.tabSize] facet (default 4),
 * - [insertSpaces][FormattingOptions.insertSpaces] defaults to true.
 *
 * **Deviation from upstream:** upstream computes `insertSpaces` from
 * `!/\t/.test(state.facet(indentUnit))` — i.e. whether the language module's
 * indent unit contains a tab. The `:lsp-client` module does not depend on
 * `:language`, so the indent unit is not readily available here; per the issue
 * we fall back to the sensible default `insertSpaces = true` (spaces) rather than
 * pull in a module dependency just to inspect the indent string.
 */
internal fun formattingOptions(state: EditorState): FormattingOptions = FormattingOptions(
    tabSize = state.tabSize.coerceAtLeast(1).toUInt(),
    insertSpaces = true
)

/**
 * Format the whole document via the language server.
 *
 * Ports upstream `@codemirror/lsp-client`'s `formatDocument` command:
 * - resolves this editor's [FormatServer] binding from the [formatServer] facet;
 *   returns false (command not handled) when none is installed,
 * - returns false when the server explicitly lacks `documentFormattingProvider`,
 * - otherwise flushes pending edits ([LSPClient.sync]), issues
 *   `textDocument/formatting` with the derived [FormattingOptions]
 *   ([formattingOptions]), and applies the returned `TextEdit[]` as a single
 *   `format` [TransactionSpec].
 *
 * Like upstream's `withMapping`, a [WorkspaceMapping] captured with the request
 * moves the edits through anything typed while it was in flight, and the format
 * is dropped if that touched any edit (see [mapFormatEdits]). When the file isn't
 * open in the workspace the edits can't be mapped, so a response that lands on a
 * changed document is dropped.
 *
 * The suspending work runs on the [client scope][LSPClient.scope] (consistent
 * with the other LSP commands) so the request and its edit application outlive
 * the synchronous command return. Cancellation of the in-flight LSP call is
 * cooperative through structured concurrency.
 *
 * **Deviation from upstream:** range formatting and on-type formatting are out of
 * scope for this command — only whole-document formatting is implemented (see
 * issue #44).
 *
 * @return true when the command is handled (a binding exists and the server
 *   advertises the capability), so the keymap stops here; false to fall through.
 */
fun formatDocument(session: EditorSession): Boolean {
    val binding = session.state.facet(formatServer) ?: return false
    if (!binding.supportsFormatting()) return false
    val client = binding.client
    val targetSession = client.workspace.getFile(binding.uri)?.session ?: session
    val options = formattingOptions(session.state)
    val startDoc = targetSession.state.doc
    val mapping = client.workspace.getMapping(binding.uri)
    client.scope.launch {
        try {
            client.sync()
            val edits = try {
                client.server.textDocumentFormatting(
                    DocumentFormattingParams(
                        textDocument = TextDocumentIdentifier(uri = binding.uri),
                        options = options
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } ?: return@launch
            if (mapping == null && targetSession.state.doc != startDoc) return@launch
            val spec = mapFormatEdits(edits, startDoc, mapping?.changes) ?: return@launch
            targetSession.dispatch(TransactionSpec(changes = spec, userEvent = "format"))
        } finally {
            mapping?.let { client.workspace.releaseMapping(it) }
        }
    }
    return true
}

/**
 * The format's [edits], read against [startDoc] (the document the request saw)
 * and moved through the [changes] made since, as one [ChangeSpec]. Null when
 * there is nothing to apply, or when [changes] touched any edit's range: the
 * user has edited code the server was reformatting, so the whole format is
 * dropped.
 *
 * Ports upstream's `formatDocument`, which maps `from` with assoc 1 and `to`
 * with assoc -1 after the touched check. **Deviation:** upstream first converts
 * each position with `mapping.mapPosition`, which already maps it through the
 * same changes, and then maps it again, so an untouched edit after a user edit
 * moves twice. This maps once.
 */
internal fun mapFormatEdits(
    edits: List<TextEdit>,
    startDoc: Text,
    changes: ChangeDesc?
): ChangeSpec? {
    if (edits.isEmpty()) return null
    val mapped = edits.toOffsetEdits(startDoc).map { edit ->
        if (changes == null) return@map edit
        if (changes.touches(edit)) return null
        val from = changes.mapPos(DocPos(edit.from), 1).value
        edit.copy(from = from, to = maxOf(from, changes.mapPos(DocPos(edit.to), -1).value))
    }
    return ChangeSpec.Multi(mapped.sortedByDescending { it.from }.map { it.toChangeSpec() })
}

/**
 * The default formatting key bindings, matching upstream's `formatKeymap`:
 * `Shift-Alt-f` runs [formatDocument] (preventing the default action).
 */
val formatKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Shift-Alt-f", run = ::formatDocument, preventDefault = true)
)

/**
 * Build the editor [Extension] that enables LSP whole-document formatting for the
 * file at [uri] served by [client].
 *
 * Mirrors how upstream `@codemirror/lsp-client` wires formatting: it carries this
 * editor's per-editor binding through the [formatServer] facet so [formatDocument]
 * resolves its client/uri from the session it runs in (no module-level mutable
 * state), and — unless [keymap] is false — installs the [formatKeymap]
 * (`Shift-Alt-f`) at high precedence.
 *
 * @param client The LSP client wrapping the language server.
 * @param uri The document URI for this editor's file.
 * @param keymap When true (the default), the [formatKeymap] binding is installed
 *   at high precedence. Pass false to bind [formatDocument] yourself.
 */
fun formatDocumentExtension(client: LSPClient, uri: String, keymap: Boolean = true): Extension {
    val extensions = buildList {
        add(formatServer.of(FormatServer(client, uri)))
        if (keymap) add(Prec.high(keymapFacet.of(formatKeymap)))
    }
    return ExtensionList(extensions)
}
