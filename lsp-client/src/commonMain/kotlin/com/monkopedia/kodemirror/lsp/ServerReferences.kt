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

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.Prec
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.define
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.keymap as keymapFacet
import com.monkopedia.lsp.Location
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.ReferenceContext
import com.monkopedia.lsp.ReferenceParams
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.TextDocumentIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The per-editor find-references binding: the [client] wrapping the language
 * server and the document [uri] for this editor's file.
 *
 * Carried through state by [referenceServer] so [findReferences] can read its
 * configuration from the editor session it runs in rather than capturing it at
 * install time. This is what keeps the command per-editor when multiple editors
 * are open — there is no module-level mutable per-editor state (mirroring the
 * per-editor design used by [definitionServer]).
 */
internal data class ReferenceServer(val client: LSPClient, val uri: String)

/**
 * The [Facet] carrying the per-editor [ReferenceServer] binding.
 *
 * Combines to the last non-null value, so an editor that installs
 * [findReferencesExtension] gets its own [client][ReferenceServer.client] /
 * [uri][ReferenceServer.uri] while editors without it see null (and
 * [findReferences] no-ops, returning false).
 */
internal val referenceServer: Facet<ReferenceServer?, ReferenceServer?> =
    Facet.define(combine = { values -> values.lastOrNull { it != null } })

/**
 * A single reference location reported by `textDocument/references`: the target
 * document [uri] and the [range] of the reference within it.
 *
 * Mirrors upstream's `ReferenceLocation`, but keeps the raw URI/range rather than
 * eagerly binding to a `WorkspaceFile`/view — the panel resolves a preview and
 * navigates lazily so it works for files that are not currently open.
 */
data class ReferenceLocation(val uri: String, val range: Range)

/**
 * The per-editor reference-panel state: the [locations] to show, or null when no
 * panel is open.
 *
 * Mirrors upstream's `referencePanel` `StateField`. The panel is open exactly
 * when this is non-null; [findReferences] sets it and [closeReferencePanel]
 * clears it via [setReferencePanel].
 */
internal data class ReferencePanelState(val locations: List<ReferenceLocation>)

/** Effect that replaces the reference-panel state (null closes the panel). */
internal val setReferencePanel: StateEffectType<ReferencePanelState?> = StateEffect.define()

/** State field holding the open reference panel, or null when closed. */
internal val referencePanel: StateField<ReferencePanelState?> = StateField.define(
    StateFieldSpec(
        create = { null },
        update = { value, tr ->
            var result = value
            for (effect in tr.effects) {
                val asSet = effect.asType(setReferencePanel)
                if (asSet != null) result = asSet.value
            }
            result
        }
    )
)

/**
 * Convert the raw `textDocument/references` result into [ReferenceLocation]s.
 *
 * The typed [LanguageServer][com.monkopedia.lsp.LanguageServer] already returns a
 * `List<Location>` (unlike the definition family's JSON union), so this is a
 * straightforward map preserving server order.
 */
internal fun toReferenceLocations(locations: List<Location>): List<ReferenceLocation> =
    locations.map { ReferenceLocation(it.uri, it.range) }

/**
 * The length of the longest common directory prefix shared by all [uris],
 * trimmed back to the last `'/'`.
 *
 * Ports upstream's `findCommonPrefix`: reference entries are shown grouped under
 * file headers with this shared prefix stripped, so only the distinguishing tail
 * of each URI is displayed. Returns 0 for an empty list.
 */
internal fun findCommonPrefix(uris: List<String>): Int {
    if (uris.isEmpty()) return 0
    val first = uris[0]
    var prefix = first.length
    for (i in 1 until uris.size) {
        val uri = uris[i]
        var j = 0
        val end = minOf(prefix, uri.length)
        while (j < end && first[j] == uri[j]) j++
        prefix = j
    }
    while (prefix > 0 && first[prefix - 1] != '/') prefix--
    return prefix
}

/**
 * A fully-resolved entry in the reference panel, ready to render.
 *
 * @param location The reference's URI and range.
 * @param fileName The URI with the [common prefix][findCommonPrefix] stripped.
 * @param lineNumber The 1-based line number of the reference, or null when the
 *   target file is not open (so its document is unavailable for a preview).
 * @param before The line text before the matched span (trimmed to a leading
 *   window, matching upstream's 50-char look-behind).
 * @param matched The matched span's text.
 * @param after The line text after the matched span (trimmed to upstream's
 *   trailing window).
 */
internal data class ReferenceEntry(
    val location: ReferenceLocation,
    val fileName: String,
    val lineNumber: Int?,
    val before: String,
    val matched: String,
    val after: String
)

/**
 * Build the renderable [ReferenceEntry] list for [locations], resolving line
 * previews from the document [resolveDoc] returns for each URI (null when the
 * file is not open).
 *
 * Ports upstream's per-entry construction in `createReferencePanel`: file names
 * have the [common prefix][findCommonPrefix] stripped, and for open files the
 * matched text plus up to ~50 chars of leading and a trailing window of context
 * are sliced out of the reference's line.
 */
internal fun buildReferenceEntries(
    locations: List<ReferenceLocation>,
    resolveDoc: (String) -> Text?
): List<ReferenceEntry> {
    val prefixLen = findCommonPrefix(locations.map { it.uri })
    return locations.map { loc ->
        val fileName = loc.uri.substring(prefixLen)
        val doc = resolveDoc(loc.uri)
        if (doc == null) {
            ReferenceEntry(loc, fileName, lineNumber = null, before = "", matched = "", after = "")
        } else {
            val from = fromPosition(loc.range.start, doc)
            val to = fromPosition(loc.range.end, doc).coerceAtLeast(from)
            val line = doc.lineAt(DocPos(from))
            val lineStart = line.from.value
            val colFrom = (from - lineStart).coerceIn(0, line.length)
            val colTo = (to - lineStart).coerceIn(colFrom, line.length)
            val before = line.text.substring(maxOf(0, colFrom - 50), colFrom)
            val matched = line.text.substring(colFrom, colTo)
            val after = line.text.substring(
                colTo,
                minOf(line.length, maxOf(colTo, 100 - before.length))
            )
            ReferenceEntry(loc, fileName, line.number.value, before, matched, after)
        }
    }
}

/**
 * Whether the server advertises the `referencesProvider` capability.
 *
 * Matches upstream's `hasCapability("referencesProvider") === false` gating:
 * proceed when the client is not yet initialized (capabilities unknown) or when
 * the capability is present and truthy; bail only when it is explicitly absent or
 * `false`.
 */
private fun ReferenceServer.supportsReferences(): Boolean {
    val caps: ServerCapabilities = client.serverCapabilities ?: return true
    return hasProvider(caps.referencesProvider)
}

/**
 * Ask the server to locate all references to the symbol at the cursor and, when
 * any are returned, show them in a panel.
 *
 * Ports upstream `@codemirror/lsp-client`'s `findReferences` command:
 * - resolves this editor's [ReferenceServer] binding from the [referenceServer]
 *   facet; returns false (command not handled) when none is installed,
 * - returns false when the server explicitly lacks `referencesProvider`,
 * - otherwise flushes pending edits ([LSPClient.sync]), issues
 *   `textDocument/references` at the cursor (with `includeDeclaration = true`,
 *   matching upstream), and opens the [reference panel][findReferencesExtension]
 *   with the resulting locations.
 *
 * The suspending work runs on the [client scope][LSPClient.scope] (not the
 * session's) because navigating to a result may target a different editor session
 * that must outlive the requesting one. Cancellation of the in-flight LSP call is
 * cooperative through structured concurrency, consistent with the other LSP
 * features.
 *
 * @return true when the command is handled (a binding exists and the server
 *   advertises the capability), so the keymap stops here; false to fall through.
 */
fun findReferences(session: EditorSession): Boolean {
    val binding = session.state.facet(referenceServer) ?: return false
    if (!binding.supportsReferences()) return false
    val pos = session.state.selection.main.head.value
    binding.client.scope.launch {
        binding.client.sync()
        val position = toPosition(pos, session.state.doc)
        val response = try {
            binding.client.server.textDocumentReferences(
                ReferenceParams(
                    textDocument = TextDocumentIdentifier(uri = binding.uri),
                    position = position,
                    context = ReferenceContext(includeDeclaration = true)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } ?: return@launch
        val locations = toReferenceLocations(response)
        if (locations.isEmpty()) return@launch
        session.dispatch(
            TransactionSpec(
                effects = listOf(setReferencePanel.of(ReferencePanelState(locations)))
            )
        )
    }
    return true
}

/**
 * Close the reference panel, if it is open.
 *
 * Ports upstream's `closeReferencePanel` command: returns false (so a keymap can
 * fall through) when no panel is open, otherwise clears the
 * [referencePanel][referencePanel] state and returns true.
 */
fun closeReferencePanel(session: EditorSession): Boolean {
    if (session.state.field(referencePanel, require = false) == null) return false
    session.dispatch(
        TransactionSpec(
            effects = listOf(setReferencePanel.of(null))
        )
    )
    return true
}

/**
 * Navigate to the reference [entry], using the shared [navigateToLocation] helper
 * (same-file vs cross-file resolution, position mapping, scroll into view).
 *
 * Ports upstream's reference-panel `showReference`.
 */
internal fun showReference(binding: ReferenceServer, entry: ReferenceEntry) {
    navigateToLocation(
        client = binding.client,
        sourceUri = binding.uri,
        targetUri = entry.location.uri,
        position = entry.location.range.start,
        userEvent = "select.reference"
    )
}

/**
 * The default find-references key bindings, matching upstream's
 * `findReferencesKeymap`: `Shift-F12` runs [findReferences] (preventing the
 * default action) and `Escape` runs [closeReferencePanel].
 */
val findReferencesKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Shift-F12", run = ::findReferences, preventDefault = true),
    KeyBinding(key = "Escape", run = ::closeReferencePanel)
)

/**
 * Build the editor [Extension] that enables LSP find-references for the file at
 * [uri] served by [client].
 *
 * Mirrors how upstream `@codemirror/lsp-client` wires find-references: it carries
 * this editor's per-editor binding through the [referenceServer] facet so
 * [findReferences] resolves its client/uri from the session it runs in (no
 * module-level mutable state), registers the reference-panel [StateField] plus
 * its `showPanels` provider, and — unless [keymap] is false — installs the
 * [findReferencesKeymap] (`Shift-F12` / `Escape`) at high precedence.
 *
 * @param client The LSP client wrapping the language server.
 * @param uri The document URI for this editor's file.
 * @param keymap When true (the default), the [findReferencesKeymap] bindings are
 *   installed at high precedence. Pass false to bind the commands yourself.
 */
fun findReferencesExtension(client: LSPClient, uri: String, keymap: Boolean = true): Extension {
    val extensions = buildList {
        add(referenceServer.of(ReferenceServer(client, uri)))
        add(referencePanel)
        add(referencePanelProvider())
        if (keymap) add(Prec.high(keymapFacet.of(findReferencesKeymap)))
    }
    return ExtensionList(extensions)
}
