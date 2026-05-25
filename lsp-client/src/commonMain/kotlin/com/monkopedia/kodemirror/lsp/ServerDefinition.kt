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
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.Prec
import com.monkopedia.kodemirror.state.define
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.keymap as keymapFacet
import com.monkopedia.lsp.BooleanOr
import com.monkopedia.lsp.DeclarationParams
import com.monkopedia.lsp.DefinitionLink
import com.monkopedia.lsp.DefinitionParams
import com.monkopedia.lsp.ImplementationParams
import com.monkopedia.lsp.Location
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.SingleOrArray
import com.monkopedia.lsp.TextDocumentDeclarationResult
import com.monkopedia.lsp.TextDocumentDefinitionResult
import com.monkopedia.lsp.TextDocumentIdentifier
import com.monkopedia.lsp.TextDocumentImplementationResult
import com.monkopedia.lsp.TextDocumentTypeDefinitionResult
import com.monkopedia.lsp.TypeDefinitionParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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

/**
 * Collapse a `Definition`/`Declaration` value — the LSP `Location | Location[]`
 * union, modelled by the lsp library as [SingleOrArray]`<`[Location]`>` — into
 * the first navigable [JumpTarget].
 *
 * For the array shape the **first** element is used, matching upstream's
 * `Array.isArray(response) ? response[0] : response`; an empty array yields null.
 */
internal fun SingleOrArray<Location>.firstJumpTarget(): JumpTarget? {
    val location = when (this) {
        is SingleOrArray.Single -> value
        is SingleOrArray.Multiple -> value.firstOrNull()
    } ?: return null
    return JumpTarget(location.uri, location.range)
}

/**
 * Collapse a `DefinitionLink[]`/`DeclarationLink[]` value (a list of
 * [DefinitionLink], which is [LocationLink]) into the first navigable
 * [JumpTarget], or null for an empty list.
 *
 * For a [LocationLink][com.monkopedia.lsp.LocationLink] the
 * `targetSelectionRange` is used (the range that should be revealed/selected when
 * the link is followed, per the LSP spec), with its `targetUri`.
 */
internal fun List<DefinitionLink>.firstJumpTarget(): JumpTarget? {
    val link = firstOrNull() ?: return null
    return JumpTarget(link.targetUri, link.targetSelectionRange)
}

/**
 * Collapse a `textDocument/definition` result into the first navigable
 * [JumpTarget].
 *
 * Since the RC4 lsp bump the typed
 * [LanguageServer][com.monkopedia.lsp.LanguageServer] returns the strict sealed
 * [TextDocumentDefinitionResult] (was a raw `JsonElement`). The
 * [DefinitionValue][TextDocumentDefinitionResult.DefinitionValue] branch carries
 * a `Location | Location[]`; the
 * [DefinitionLinkArray][TextDocumentDefinitionResult.DefinitionLinkArray] branch
 * carries a `LocationLink[]`. A `null` result (server returned nothing) → null.
 */
internal fun parseDefinitionResult(result: TextDocumentDefinitionResult?): JumpTarget? =
    when (result) {
        null -> null
        is TextDocumentDefinitionResult.DefinitionValue -> result.value.firstJumpTarget()
        is TextDocumentDefinitionResult.DefinitionLinkArray -> result.value.firstJumpTarget()
    }

/** As [parseDefinitionResult], for the `textDocument/declaration` result. */
internal fun parseDeclarationResult(result: TextDocumentDeclarationResult?): JumpTarget? =
    when (result) {
        null -> null
        is TextDocumentDeclarationResult.DeclarationValue -> result.value.firstJumpTarget()
        is TextDocumentDeclarationResult.DeclarationLinkArray -> result.value.firstJumpTarget()
    }

/** As [parseDefinitionResult], for the `textDocument/typeDefinition` result. */
internal fun parseTypeDefinitionResult(result: TextDocumentTypeDefinitionResult?): JumpTarget? =
    when (result) {
        null -> null
        is TextDocumentTypeDefinitionResult.DefinitionValue -> result.value.firstJumpTarget()
        is TextDocumentTypeDefinitionResult.DefinitionLinkArray -> result.value.firstJumpTarget()
    }

/** As [parseDefinitionResult], for the `textDocument/implementation` result. */
internal fun parseImplementationResult(result: TextDocumentImplementationResult?): JumpTarget? =
    when (result) {
        null -> null
        is TextDocumentImplementationResult.DefinitionValue -> result.value.firstJumpTarget()
        is TextDocumentImplementationResult.DefinitionLinkArray -> result.value.firstJumpTarget()
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
 *   cursor (each caller maps its strict sealed result to a [JumpTarget]) and
 *   navigates ([navigateToTarget]).
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
    crossinline request: suspend (DefinitionServer, Position) -> JumpTarget?
): Boolean {
    val binding = session.state.facet(definitionServer) ?: return false
    if (!binding.serverSupports(capability)) return false
    val pos = session.state.selection.main.head.value
    binding.client.scope.launch {
        binding.client.sync()
        val position = toPosition(pos, session.state.doc)
        val target = try {
            request(binding, position)
        } catch (e: CancellationException) {
            throw e
        } ?: return@launch
        navigateToTarget(binding, target)
    }
    return true
}

/**
 * Move the user to [target], delegating to the shared [navigateToLocation]
 * helper (same-file vs cross-file resolution, position mapping, scroll into
 * view) that go-to-definition and find-references both use.
 */
private fun navigateToTarget(binding: DefinitionServer, target: JumpTarget) {
    navigateToLocation(
        client = binding.client,
        sourceUri = binding.uri,
        targetUri = target.uri,
        position = target.range.start,
        userEvent = "select.definition"
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
        parseDefinitionResult(
            binding.client.server.textDocumentDefinition(
                DefinitionParams(
                    textDocument = TextDocumentIdentifier(uri = binding.uri),
                    position = position
                )
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
        parseDeclarationResult(
            binding.client.server.textDocumentDeclaration(
                DeclarationParams(
                    textDocument = TextDocumentIdentifier(uri = binding.uri),
                    position = position
                )
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
        parseTypeDefinitionResult(
            binding.client.server.textDocumentTypeDefinition(
                TypeDefinitionParams(
                    textDocument = TextDocumentIdentifier(uri = binding.uri),
                    position = position
                )
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
        parseImplementationResult(
            binding.client.server.textDocumentImplementation(
                ImplementationParams(
                    textDocument = TextDocumentIdentifier(uri = binding.uri),
                    position = position
                )
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
