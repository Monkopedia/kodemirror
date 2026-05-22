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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.Prec
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asInsert
import com.monkopedia.kodemirror.state.define
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.LocalEditorSession
import com.monkopedia.kodemirror.view.LocalEditorTheme
import com.monkopedia.kodemirror.view.Tooltip
import com.monkopedia.kodemirror.view.keymap as keymapFacet
import com.monkopedia.kodemirror.view.showTooltip
import com.monkopedia.lsp.RenameParams
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.TextDocumentEdit
import com.monkopedia.lsp.TextDocumentIdentifier
import com.monkopedia.lsp.TextEdit
import com.monkopedia.lsp.WorkspaceEdit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The per-editor rename binding: the [client] wrapping the language server and
 * the document [uri] for this editor's file.
 *
 * Carried through state by [renameServer] so [renameSymbol] reads its
 * configuration from the editor session it runs in rather than capturing it at
 * install time. This is what keeps the command per-editor when multiple editors
 * are open — there is no module-level mutable per-editor state (mirroring the
 * per-editor design used by [definitionServer]/[referenceServer]).
 */
internal data class RenameServer(val client: LSPClient, val uri: String)

/**
 * The [Facet] carrying the per-editor [RenameServer] binding.
 *
 * Combines to the last non-null value, so an editor that installs
 * [renameSymbolExtension] gets its own [client][RenameServer.client] /
 * [uri][RenameServer.uri] while editors without it see null (and [renameSymbol]
 * no-ops, returning false).
 */
internal val renameServer: Facet<RenameServer?, RenameServer?> =
    Facet.define(combine = { values -> values.lastOrNull { it != null } })

/**
 * The state backing an open rename-input tooltip.
 *
 * Mirrors the data upstream `@codemirror/lsp-client` keeps in its rename dialog:
 * the [from]/[to] offsets of the word being renamed (so the request issues at
 * the word start and the prompt anchors there) and the original [word] used to
 * prefill the input.
 *
 * **Deviation from upstream (DOM dialog → Compose tooltip):** upstream renders a
 * panel-level `showDialog` with an `<input>`; kodemirror has no DOM, so the
 * prompt is a [Tooltip] anchored at [from] containing a Compose
 * [BasicTextField]. The behavior (prefilled + selected current name, Enter to
 * confirm, Escape to cancel) is preserved.
 */
internal data class RenameState(val from: Int, val to: Int, val word: String)

/** Effect that replaces the rename-input state (null closes the prompt). */
internal val setRenameState: StateEffectType<RenameState?> = StateEffect.define { value, mapping ->
    // Remap the anchored offsets through document changes so the prompt stays put.
    value?.copy(
        from = mapping.mapPos(DocPos(value.from)).value,
        to = mapping.mapPos(DocPos(value.to)).value
    )
}

/**
 * The [StateField] holding the open [RenameState], or null when no rename prompt
 * is showing. Provides the anchored input [Tooltip] to the [showTooltip] facet.
 */
internal val renameState: StateField<RenameState?> = StateField.define {
    create { _ -> null }
    update { state: RenameState?, tr ->
        val effect = tr.effects.firstNotNullOfOrNull { it.asType(setRenameState) }
        when {
            effect != null -> effect.value
            state != null && tr.docChanged -> state.copy(
                from = tr.changes.mapPos(DocPos(state.from)).value,
                to = tr.changes.mapPos(DocPos(state.to)).value
            )
            else -> state
        }
    }
    provide { field ->
        showTooltip.from(field) { state -> state?.let { renameTooltip(it) } }
    }
}

/** Build the anchored input [Tooltip] for an open [RenameState]. */
private fun renameTooltip(state: RenameState): Tooltip = Tooltip(
    pos = state.from,
    above = true,
    content = { RenameInput(state) }
)

/**
 * The Compose body of the rename prompt: a label and a prefilled, selected text
 * input. Enter confirms (issues the rename and closes the prompt), Escape
 * cancels (closes the prompt). The field grabs focus when shown.
 *
 * **Deviation from upstream (HTML dialog → Compose):** upstream's `showDialog`
 * produces a focused `<input>` with a submit button; this renders the equivalent
 * with a [BasicTextField] whose key events are intercepted for Enter/Escape.
 */
@Composable
internal fun RenameInput(state: RenameState) {
    val session = LocalEditorSession.current
    val theme = LocalEditorTheme.current
    val style = TextStyle(color = theme.foreground)
    var value by remember(state) {
        // Prefill with the current name, fully selected (matches upstream input.select()).
        mutableStateOf(TextFieldValue(state.word, selection = TextRange(0, state.word.length)))
    }
    val focusRequester = remember(state) { FocusRequester() }
    Row(modifier = Modifier.padding(2.dp)) {
        Column {
            BasicText(text = "New name", style = style)
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                textStyle = style,
                cursorBrush = SolidColor(theme.foreground),
                modifier = Modifier
                    .width(160.dp)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter -> {
                                confirmRename(session, state, value.text)
                                true
                            }
                            Key.Escape -> {
                                cancelRename(session)
                                true
                            }
                            else -> false
                        }
                    }
            )
        }
    }
    LaunchedEffect(state) {
        focusRequester.requestFocus()
    }
}

/** Close the rename prompt without renaming. */
internal fun cancelRename(session: EditorSession) {
    if (session.state.field(renameState, require = false) == null) return
    session.dispatch(TransactionSpec(effects = listOf(setRenameState.of(null))))
}

/**
 * Confirm a rename: close the prompt and, when [newName] is a non-empty change,
 * issue the `textDocument/rename` request and apply the resulting edit.
 */
internal fun confirmRename(session: EditorSession, state: RenameState, newName: String) {
    session.dispatch(TransactionSpec(effects = listOf(setRenameState.of(null))))
    val binding = session.state.facet(renameServer) ?: return
    if (newName.isEmpty()) return
    doRename(session, binding, state, newName)
}

/**
 * Whether the server advertises the `renameProvider` capability.
 *
 * Matches upstream's `hasCapability("renameProvider") === false` gating: proceed
 * when the client is not yet initialized (capabilities unknown) or when the
 * capability is present and truthy; bail only when it is explicitly absent or
 * `false`.
 */
private fun RenameServer.supportsRename(): Boolean {
    val caps: ServerCapabilities = client.serverCapabilities ?: return true
    return hasProvider(caps.renameProvider)
}

/**
 * Prompt the user for a new name for the symbol at the cursor and, on
 * confirmation, ask the language server to rename it.
 *
 * Ports upstream `@codemirror/lsp-client`'s `renameSymbol` command:
 * - resolves this editor's [RenameServer] binding from the [renameServer] facet;
 *   returns false (command not handled) when none is installed,
 * - returns false when the cursor is not over a word
 *   ([wordAt][com.monkopedia.kodemirror.state.EditorState.wordAt]) or the server
 *   explicitly lacks `renameProvider`,
 * - otherwise opens an input prompt prefilled with the current word (the
 *   [RenameState] tooltip). Confirming the prompt ([confirmRename]) flushes
 *   pending edits ([LSPClient.sync]), issues `textDocument/rename` at the word
 *   start, and applies the resulting [WorkspaceEdit].
 *
 * **Deviation from upstream:** upstream optionally reuses an already-open dialog
 * (`getDialog`) and reprefills it; kodemirror simply re-opens the prompt (the
 * effect replaces any prior [RenameState]).
 *
 * @return true when the command is handled (a binding + word exist and the
 *   server advertises the capability), so the keymap stops here; false to fall
 *   through.
 */
fun renameSymbol(session: EditorSession): Boolean {
    val binding = session.state.facet(renameServer) ?: return false
    val head = session.state.selection.main.head
    val word = session.state.wordAt(head) ?: return false
    if (!binding.supportsRename()) return false
    val text = session.state.sliceDoc(word.from, word.to)
    session.dispatch(
        TransactionSpec(
            effects = listOf(
                setRenameState.of(RenameState(word.from.value, word.to.value, text))
            )
        )
    )
    return true
}

/**
 * Issue `textDocument/rename` for the word described by [state] and apply the
 * server's [WorkspaceEdit].
 *
 * Ports upstream's `doRename`. The suspending work runs on the
 * [client scope][LSPClient.scope] (not the session's) because the edit may touch
 * files in other editor sessions that must outlive the requesting one. A live
 * [WorkspaceMapping][Workspace.getMapping] is captured for the requesting file so
 * the edit's offsets are mapped through any edits that happen while the request
 * is in flight (mirroring upstream's `withMapping`). Cross-file edits are routed
 * through [Workspace.displayFile]; offsets there are resolved against the target
 * session's current document.
 */
private fun doRename(
    session: EditorSession,
    binding: RenameServer,
    state: RenameState,
    newName: String
) {
    val client = binding.client
    val pos = state.from
    client.scope.launch {
        client.sync()
        val mapping = client.workspace.getMapping(binding.uri)
        try {
            val response = try {
                client.server.textDocumentRename(
                    RenameParams(
                        textDocument = TextDocumentIdentifier(uri = binding.uri),
                        position = toPosition(pos, session.state.doc),
                        newName = newName
                    )
                )
            } catch (e: CancellationException) {
                throw e
            }
            applyWorkspaceEdit(client, binding.uri, response, mapping)
        } finally {
            if (mapping != null) client.workspace.releaseMapping(mapping)
        }
    }
}

/**
 * Apply a rename [WorkspaceEdit] to the workspace.
 *
 * Ports upstream's edit-application loop in `doRename`. For each touched file the
 * edits are converted to a single multi-change [TransactionSpec] and dispatched
 * into the file's editor session (the requesting file's session for [sourceUri],
 * otherwise the session [Workspace.displayFile] surfaces). Files the workspace
 * can't surface are skipped, degrading gracefully on the default single-file
 * [Workspace] exactly as cross-file navigation does (see [navigateToLocation]).
 *
 * Both forms of the edit are handled: the `changes` map (preferred — it is what
 * the kodemirror client advertises support for) and, as a fallback, plain
 * [TextDocumentEdit]s in `documentChanges`. Resource operations
 * (create/rename/delete file) in `documentChanges` are not applied — see the
 * deviation note on [collectFileEdits].
 *
 * @param sourceUri The requesting file's URI; its session receives the
 *   [requestMapping]-mapped edits so concurrent in-flight edits stay consistent.
 * @param requestMapping The live mapping captured when the request was issued, or
 *   null when no mapping is available. Used only for [sourceUri].
 */
internal fun applyWorkspaceEdit(
    client: LSPClient,
    sourceUri: String,
    edit: WorkspaceEdit,
    requestMapping: WorkspaceMapping?
) {
    for ((uri, edits) in collectFileEdits(edit)) {
        if (edits.isEmpty()) continue
        val targetSession: EditorSession? = if (uri == sourceUri) {
            client.workspace.getFile(sourceUri)?.session
        } else {
            client.workspace.displayFile(uri)
        }
        if (targetSession == null) continue
        val mapping = if (uri == sourceUri) requestMapping else null
        val spec = textEditsToChangeSpec(edits, targetSession.state.doc, mapping)
            ?: continue
        targetSession.dispatch(
            TransactionSpec(changes = spec, userEvent = "rename")
        )
    }
}

/**
 * Collect the per-file [TextEdit] lists from a [WorkspaceEdit], in a stable
 * insertion order.
 *
 * Prefers the `changes` map (the simple form the kodemirror client advertises
 * support for, matching what upstream consumes). When `changes` is absent but
 * `documentChanges` is present, the plain [TextDocumentEdit]s are flattened.
 *
 * **Deviation / follow-up:** resource operations (create/rename/delete file) that
 * may appear in `documentChanges` are ignored — kodemirror's single-file default
 * [Workspace] has no notion of creating files. Annotated edits collapse to their
 * plain range/text. Document versions are not checked.
 */
internal fun collectFileEdits(edit: WorkspaceEdit): Map<String, List<TextEdit>> {
    edit.changes?.let { return it }
    val docChanges = edit.documentChanges ?: return emptyMap()
    val result = LinkedHashMap<String, MutableList<TextEdit>>()
    for (change in docChanges) {
        if (change is TextDocumentEdit) {
            val list = result.getOrPut(change.textDocument.uri) { mutableListOf() }
            for (e in change.edits) if (e is TextEdit) list.add(e)
        }
    }
    return result
}

/**
 * Convert a file's [edits] to a single multi-change [ChangeSpec] against [doc].
 *
 * Each edit's LSP range is resolved to `[from, to)` offsets. For the requesting
 * file a live [mapping] (the in-flight [WorkspaceMapping]) is applied so offsets
 * stay valid under concurrent edits, matching upstream's `mapping.mapPosition`;
 * other files resolve directly against their current document.
 *
 * The edits are emitted highest-offset-first (by resolved `from`) so that an
 * earlier edit's offsets are never invalidated by the application of a later one
 * — the same ordering discipline the incremental document sync uses (see
 * [buildContentChanges]). Returns null when there is nothing to change.
 */
internal fun textEditsToChangeSpec(
    edits: List<TextEdit>,
    doc: Text,
    mapping: WorkspaceMapping?
): ChangeSpec? {
    if (edits.isEmpty()) return null
    val resolved = edits.map { e ->
        val from: Int
        val to: Int
        if (mapping != null) {
            from = mapping.mapPosition(e.range.start, doc)
            to = mapping.mapPosition(e.range.end, doc)
        } else {
            from = fromPosition(e.range.start, doc)
            to = fromPosition(e.range.end, doc)
        }
        Triple(minOf(from, to), maxOf(from, to), e.newText)
    }.sortedByDescending { it.first }
    val specs = resolved.map { (from, to, insert) ->
        ChangeSpec.Single(from = DocPos(from), to = DocPos(to), insert = insert.asInsert())
    }
    return ChangeSpec.Multi(specs)
}

/**
 * The default rename key bindings, matching upstream's `renameKeymap`: `F2` runs
 * [renameSymbol] (preventing the default action). `Escape` cancels an open rename
 * prompt ([cancelRename]).
 */
val renameKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "F2", run = ::renameSymbol, preventDefault = true),
    KeyBinding(key = "Escape", run = { session -> cancelRenameCommand(session) })
)

/**
 * Escape-key command form of [cancelRename]: returns true (consuming the key)
 * only when a prompt was open, so the binding falls through otherwise.
 */
internal fun cancelRenameCommand(session: EditorSession): Boolean {
    if (session.state.field(renameState, require = false) == null) return false
    cancelRename(session)
    return true
}

/**
 * Build the editor [Extension] that enables LSP rename for the file at [uri]
 * served by [client].
 *
 * Mirrors how upstream `@codemirror/lsp-client` wires rename: it carries this
 * editor's per-editor binding through the [renameServer] facet so [renameSymbol]
 * resolves its client/uri from the session it runs in (no module-level mutable
 * state), registers the [renameState] field (which provides the input tooltip to
 * the [showTooltip] facet), and — unless [keymap] is false — installs the
 * [renameKeymap] (`F2` / `Escape`) at high precedence.
 *
 * @param client The LSP client wrapping the language server.
 * @param uri The document URI for this editor's file.
 * @param keymap When true (the default), the [renameKeymap] bindings are
 *   installed at high precedence. Pass false to bind [renameSymbol] yourself.
 */
fun renameSymbolExtension(client: LSPClient, uri: String, keymap: Boolean = true): Extension {
    val extensions = buildList {
        add(renameServer.of(RenameServer(client, uri)))
        add(renameState)
        if (keymap) add(Prec.high(keymapFacet.of(renameKeymap)))
    }
    return ExtensionList(extensions)
}
