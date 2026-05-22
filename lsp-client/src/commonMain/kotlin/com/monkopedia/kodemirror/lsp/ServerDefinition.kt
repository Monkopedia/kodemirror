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
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.define
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.keymap as keymapFacet
import com.monkopedia.lsp.BooleanOr
import com.monkopedia.lsp.DeclarationParams
import com.monkopedia.lsp.DefinitionParams
import com.monkopedia.lsp.ImplementationParams
import com.monkopedia.lsp.Location
import com.monkopedia.lsp.LocationLink
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.TextDocumentIdentifier
import com.monkopedia.lsp.TypeDefinitionParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * The per-editor go-to-definition binding: the [client] wrapping the language
 * server and the document [uri] for this editor's file.
 *
 * Carried through state by [definitionServer] so the [jumpToDefinition] family
 * of commands can read their configuration from the editor session they run in
 * rather than capturing it at install time. This is what keeps the jump commands
 * per-editor when multiple editors are open — there is no module-level mutable
 * per-editor state (mirroring the per-editor design used by signature help).
 */
internal data class DefinitionServer(val client: LSPClient, val uri: String)

/**
 * The [Facet] carrying the per-editor [DefinitionServer] binding.
 *
 * Combines to the last non-null value, so an editor that installs
 * [definitionJumps] gets its own [client][DefinitionServer.client] /
 * [uri][DefinitionServer.uri] while editors without it see null (and the jump
 * commands no-op, returning false).
 */
internal val definitionServer: Facet<DefinitionServer?, DefinitionServer?> =
    Facet.define(combine = { values -> values.lastOrNull { it != null } })

/**
 * A normalized navigation target parsed out of an LSP definition-family result.
 *
 * The LSP `textDocument/{definition,declaration,typeDefinition,implementation}`
 * result is the union `Location | Location[] | LocationLink[] | null`. This
 * collapses whichever shape was returned (and, for arrays, the first element,
 * matching upstream) into a single URI + the LSP [range] whose
 * [start][Range.start] the cursor should jump to.
 *
 * @param uri The target document URI.
 * @param range The target range; navigation moves to its start.
 */
internal data class JumpTarget(val uri: String, val range: Range)

private val definitionJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Parse the raw definition-family result (which the typed
 * [LanguageServer][com.monkopedia.lsp.LanguageServer] returns as a [JsonElement])
 * into the first navigable [JumpTarget], or null when there is nothing to jump
 * to.
 *
 * Handles every shape the result union allows:
 * - `null` / JSON `null` → null,
 * - a single `Location` object (`{uri, range}`),
 * - a single `LocationLink` object (`{targetUri, targetSelectionRange, ...}`),
 * - an array of `Location` or `LocationLink` (the **first** element is used,
 *   matching upstream's `Array.isArray(response) ? response[0] : response`).
 *
 * For a [LocationLink] the [targetSelectionRange][LocationLink.targetSelectionRange]
 * is used (the range that should be revealed/selected when the link is followed,
 * per the LSP spec), with its [targetUri][LocationLink.targetUri].
 */
internal fun parseDefinitionResult(element: JsonElement?): JumpTarget? = when (element) {
    null, JsonNull -> null
    is JsonObject -> parseLocationElement(element)
    is JsonArray -> element.firstOrNull()?.let { parseLocationElement(it) }
    else -> null
}

/**
 * Parse a single element of a definition-family result — either a [Location]
 * (`{uri, range}`) or a [LocationLink] (`{targetUri, targetSelectionRange, …}`),
 * disambiguated by which key is present (`uri` vs `targetUri`).
 */
private fun parseLocationElement(element: JsonElement): JumpTarget? {
    val obj = element as? JsonObject ?: return null
    if (obj.containsKey("targetUri")) {
        val link = runCatching {
            definitionJson.decodeFromJsonElement(LocationLink.serializer(), obj)
        }.getOrNull() ?: return null
        return JumpTarget(link.targetUri, link.targetSelectionRange)
    }
    val loc = runCatching {
        definitionJson.decodeFromJsonElement(Location.serializer(), obj)
    }.getOrNull() ?: return null
    return JumpTarget(loc.uri, loc.range)
}

/**
 * Whether a [ServerCapabilities] field carrying one of the definition-family
 * provider capabilities is present (the server supports the feature).
 *
 * Ports upstream `@codemirror/lsp-client`'s `hasCapability`: a capability is
 * supported when its value is truthy, i.e. present and not the boolean `false`.
 * A `BooleanOr` options object (`Value`) always counts as supported; a bare
 * boolean counts only when true; null/absent counts as unsupported.
 */
internal fun hasProvider(capability: BooleanOr<*>?): Boolean = when (capability) {
    null -> false
    is BooleanOr.BooleanValue -> capability.value
    is BooleanOr.Value<*> -> true
}

/** Resolve the definition-family provider capability selected by [select]. */
private inline fun DefinitionServer.serverSupports(
    select: (ServerCapabilities) -> BooleanOr<*>?
): Boolean {
    val caps = client.serverCapabilities ?: return true // not initialized: match upstream (proceed)
    return hasProvider(select(caps))
}

/**
 * The shared implementation behind the four jump commands.
 *
 * Ports upstream `@codemirror/lsp-client`'s `jumpToOrigin`:
 * - resolves this editor's [DefinitionServer] binding from the [definitionServer]
 *   facet; returns false (command not handled) when none is installed,
 * - returns false when the server explicitly lacks the [capability],
 * - otherwise flushes pending edits ([LSPClient.sync]), issues [request] at the
 *   cursor, parses the result union ([parseDefinitionResult]) and navigates
 *   ([navigateToTarget]).
 *
 * The suspending work runs on the [client scope][LSPClient.scope] (not the
 * session's) because a cross-file jump targets a different editor session, and
 * the navigation must outlive the requesting session if it is torn down. A new
 * jump does not cancel an in-flight one (each is an explicit user gesture);
 * cancellation of the in-flight LSP call is cooperative through structured
 * concurrency, consistent with the other LSP features.
 *
 * @return true when the command is handled (a binding exists and the server
 *   advertises the capability), so the keymap stops here; false to fall through.
 */
private inline fun jumpToOrigin(
    session: EditorSession,
    crossinline capability: (ServerCapabilities) -> BooleanOr<*>?,
    crossinline request: suspend (DefinitionServer, Position) -> JsonElement
): Boolean {
    val binding = session.state.facet(definitionServer) ?: return false
    if (!binding.serverSupports(capability)) return false
    val pos = session.state.selection.main.head.value
    binding.client.scope.launch {
        binding.client.sync()
        val position = toPosition(pos, session.state.doc)
        val result = try {
            request(binding, position)
        } catch (e: CancellationException) {
            throw e
        }
        val target = parseDefinitionResult(result) ?: return@launch
        navigateToTarget(binding, target)
    }
    return true
}

/**
 * Move the user to [target].
 *
 * Ports upstream `jumpToOrigin`'s navigation tail:
 * - **Same file** (target URI equals the binding's [uri][DefinitionServer.uri]):
 *   resolve the target range start against the file's current document
 *   ([fromPosition]) and dispatch a selection move with scroll-into-view on the
 *   binding's own file session.
 * - **Cross file:** ask the [Workspace] to surface the target file
 *   ([Workspace.displayFile]); if it returns a session, move that session's
 *   selection to the target start (resolved against *that* session's document).
 *
 * **Workspace limitation:** the default [Workspace] is single-file — its
 * [displayFile][Workspace.displayFile] only returns a session for a file that is
 * already open (it cannot open arbitrary files). For such a workspace a cross-file
 * jump degrades gracefully to a no-op. To support real cross-file navigation, a
 * consumer must subclass [Workspace] and override [displayFile][Workspace.displayFile]
 * to create/find and reveal an editor for the URI (mirroring upstream's
 * `Workspace.displayFile`).
 */
private fun navigateToTarget(binding: DefinitionServer, target: JumpTarget) {
    val workspace = binding.client.workspace
    val targetSession: EditorSession? = if (target.uri == binding.uri) {
        workspace.getFile(binding.uri)?.session
    } else {
        workspace.displayFile(target.uri)
    }
    if (targetSession == null) return
    val offset = fromPosition(target.range.start, targetSession.state.doc)
    targetSession.dispatch(
        TransactionSpec(
            selection = SelectionSpec.CursorSpec(DocPos(offset)),
            scrollIntoView = true,
            userEvent = "select.definition"
        )
    )
}

/**
 * Jump to the definition of the symbol at the cursor.
 *
 * Ports upstream `@codemirror/lsp-client`'s `jumpToDefinition` command (requires
 * the server's `definitionProvider` capability). Returns false when no
 * [definitionJumps] extension is installed in [session] or the server lacks the
 * capability. To support cross-file jumps, override
 * [Workspace.displayFile] (see [navigateToTarget]).
 */
fun jumpToDefinition(session: EditorSession): Boolean = jumpToOrigin(
    session,
    capability = { it.definitionProvider },
    request = { binding, position ->
        binding.client.server.textDocumentDefinition(
            DefinitionParams(
                textDocument = TextDocumentIdentifier(uri = binding.uri),
                position = position
            )
        )
    }
)

/**
 * Jump to the declaration of the symbol at the cursor.
 *
 * Ports upstream's `jumpToDeclaration` command (requires the server's
 * `declarationProvider` capability). See [jumpToDefinition].
 */
fun jumpToDeclaration(session: EditorSession): Boolean = jumpToOrigin(
    session,
    capability = { it.declarationProvider },
    request = { binding, position ->
        binding.client.server.textDocumentDeclaration(
            DeclarationParams(
                textDocument = TextDocumentIdentifier(uri = binding.uri),
                position = position
            )
        )
    }
)

/**
 * Jump to the type definition of the symbol at the cursor.
 *
 * Ports upstream's `jumpToTypeDefinition` command (requires the server's
 * `typeDefinitionProvider` capability). See [jumpToDefinition].
 */
fun jumpToTypeDefinition(session: EditorSession): Boolean = jumpToOrigin(
    session,
    capability = { it.typeDefinitionProvider },
    request = { binding, position ->
        binding.client.server.textDocumentTypeDefinition(
            TypeDefinitionParams(
                textDocument = TextDocumentIdentifier(uri = binding.uri),
                position = position
            )
        )
    }
)

/**
 * Jump to the implementation of the symbol at the cursor.
 *
 * Ports upstream's `jumpToImplementation` command (requires the server's
 * `implementationProvider` capability). See [jumpToDefinition].
 */
fun jumpToImplementation(session: EditorSession): Boolean = jumpToOrigin(
    session,
    capability = { it.implementationProvider },
    request = { binding, position ->
        binding.client.server.textDocumentImplementation(
            ImplementationParams(
                textDocument = TextDocumentIdentifier(uri = binding.uri),
                position = position
            )
        )
    }
)

/**
 * The default go-to-definition key bindings, matching upstream's
 * `jumpToDefinitionKeymap`: `F12` runs [jumpToDefinition] (preventing the default
 * action).
 */
val jumpToDefinitionKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "F12", run = ::jumpToDefinition, preventDefault = true)
)

/**
 * Build the editor [Extension] that enables the LSP go-to-definition family
 * ([jumpToDefinition] / [jumpToDeclaration] / [jumpToTypeDefinition] /
 * [jumpToImplementation]) for the file at [uri] served by [client].
 *
 * Mirrors how upstream `@codemirror/lsp-client` wires the jump commands: it
 * carries this editor's per-editor binding through the [definitionServer] facet
 * so the commands resolve their client/uri from the session they run in (no
 * module-level mutable state), and — unless [keymap] is false — installs the
 * [jumpToDefinitionKeymap] (`F12`) at high precedence.
 *
 * @param client The LSP client wrapping the language server.
 * @param uri The document URI for this editor's file.
 * @param keymap When true (the default), the [jumpToDefinitionKeymap] binding is
 *   installed at high precedence. Pass false to bind the jump commands yourself.
 */
fun definitionJumps(client: LSPClient, uri: String, keymap: Boolean = true): Extension {
    val extensions = buildList {
        add(definitionServer.of(DefinitionServer(client, uri)))
        if (keymap) add(Prec.high(keymapFacet.of(jumpToDefinitionKeymap)))
    }
    return ExtensionList(extensions)
}
