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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.Prec
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.define
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.LocalEditorTheme
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.Tooltip
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.keymap
import com.monkopedia.kodemirror.view.showTooltip
import com.monkopedia.lsp.SignatureHelp
import com.monkopedia.lsp.SignatureHelpContext
import com.monkopedia.lsp.SignatureHelpParams
import com.monkopedia.lsp.SignatureHelpTriggerKind
import com.monkopedia.lsp.SignatureInformation
import com.monkopedia.lsp.TextDocumentIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Configuration for [signatureHelp].
 *
 * Mirrors the shape of the options object upstream `@codemirror/lsp-client`'s
 * `signatureHelp` accepts.
 *
 * @param keymap When true (the default), the [signatureKeymap] default bindings
 *   are installed at high precedence. Pass false to bind the
 *   [showSignatureHelp]/[nextSignature]/[prevSignature] commands yourself.
 */
data class SignatureHelpConfig(
    val keymap: Boolean = true
)

/**
 * The per-editor signature-help server binding: the [client] wrapping the
 * language server and the document [uri] for this editor's file.
 *
 * Carried through state by [signatureHelpServer] so the single, shared
 * [signatureHelpPlugin] can read its configuration from the editor it is
 * running in rather than capturing it at install time. This is what keeps
 * signature help per-editor when multiple editors are open.
 */
internal data class SignatureHelpServer(val client: LSPClient, val uri: String)

/**
 * The [Facet] carrying the per-editor [SignatureHelpServer] binding.
 *
 * Combines to the last non-null value, so an editor that installs
 * [signatureHelp] gets its own [client][SignatureHelpServer.client] /
 * [uri][SignatureHelpServer.uri] while editors without it see null (and the
 * shared [signatureHelpPlugin] no-ops).
 */
internal val signatureHelpServer: Facet<SignatureHelpServer?, SignatureHelpServer?> =
    Facet.define(combine = { values -> values.lastOrNull { it != null } })

/**
 * The inclusive-start / exclusive-end offsets into a signature label that
 * delimit the active parameter, or null when there is no active parameter to
 * highlight.
 */
internal data class ActiveParamRange(val from: Int, val to: Int)

/**
 * Resolve the `[from, to)` slice of [signature]'s [label][SignatureInformation.label]
 * that the active parameter occupies, given the help-level
 * [activeParameter][SignatureHelp.activeParameter].
 *
 * Ports upstream `@codemirror/lsp-client`'s active-parameter computation in
 * `drawSignatureTooltip`:
 * - the active parameter index is the signature's own
 *   [activeParameter][SignatureInformation.activeParameter] when present, else
 *   the help-level [activeParameter][SignatureHelp.activeParameter];
 * - the parameter's [label][com.monkopedia.lsp.ParameterInformation.label] is
 *   either an `[start, end]` offset pair into the signature label (returned
 *   as-is) or a string that is located as a substring of the signature label;
 * - returns null when there is no parameter list, the index is out of range, or
 *   the string label can't be found in the signature label.
 */
internal fun activeParamRange(
    signature: SignatureInformation,
    helpActiveParameter: UInt?
): ActiveParamRange? {
    val params = signature.parameters ?: return null
    val index = (signature.activeParameter ?: helpActiveParameter)?.toInt() ?: return null
    val param = params.getOrNull(index) ?: return null
    val label = param.label
    if (label is JsonArray && label.size == 2) {
        val from = (label[0] as? JsonPrimitive)?.intOrNull
        val to = (label[1] as? JsonPrimitive)?.intOrNull
        if (from != null && to != null) return ActiveParamRange(from, to)
        return null
    }
    val str = (label as? JsonPrimitive)?.contentOrNull ?: return null
    val found = signature.label.indexOf(str)
    if (found < 0) return null
    return ActiveParamRange(found, found + str.length)
}

/**
 * Whether two [SignatureHelp] results describe the same set of signatures.
 *
 * Ports upstream's `sameSignatures`: the lists must be the same length and have
 * pairwise-equal [labels][SignatureInformation.label]. Used to decide whether a
 * fresh response should keep the existing tooltip position and active index.
 */
internal fun sameSignatures(a: SignatureHelp, b: SignatureHelp): Boolean {
    if (a.signatures.size != b.signatures.size) return false
    return a.signatures.indices.all { a.signatures[it].label == b.signatures[it].label }
}

/**
 * Whether the active parameter of the signature at [active] is unchanged between
 * [a] and [b]. Ports upstream's `sameActiveParam`, falling back from the
 * signature's own active parameter to the help-level one. Returns true when
 * [active] is out of range for either result (nothing to differ).
 */
internal fun sameActiveParam(a: SignatureHelp, b: SignatureHelp, active: Int): Boolean {
    val sa = a.signatures.getOrNull(active) ?: return true
    val sb = b.signatures.getOrNull(active) ?: return true
    return (sa.activeParameter ?: a.activeParameter) == (sb.activeParameter ?: b.activeParameter)
}

/**
 * The active signature index after advancing to the next signature, clamped at
 * the last index. Pure cycling logic shared by [nextSignature].
 */
internal fun nextActive(active: Int, count: Int): Int =
    if (active < count - 1) active + 1 else active

/**
 * The active signature index after moving to the previous signature, clamped at
 * the first index. Pure cycling logic shared by [prevSignature].
 */
internal fun prevActive(active: Int): Int = if (active > 0) active - 1 else active

/**
 * The state backing an active signature-help tooltip.
 *
 * Mirrors upstream's `SignatureState`: the raw [data] returned by the server,
 * the currently displayed [active] signature index (cycled by
 * [nextSignature]/[prevSignature]), and the document [pos] the tooltip anchors
 * to.
 */
internal data class SignatureState(
    val data: SignatureHelp,
    val active: Int,
    val pos: Int
)

/**
 * The new state produced by a [signatureEffect]: either a full [SignatureState]
 * to show, or null to clear the tooltip.
 */
internal val signatureEffect: StateEffectType<SignatureState?> =
    StateEffect.define { value, mapping ->
        // Remap the tooltip anchor through document changes so the tooltip stays put.
        value?.copy(pos = mapping.mapPos(DocPos(value.pos)).value)
    }

/**
 * The [StateField] holding the active [SignatureState], or null when no
 * signature help is showing. Provides the anchored [Tooltip] to the
 * [showTooltip] facet, matching upstream's `signatureState` field.
 */
internal val signatureState: StateField<SignatureState?> = StateField.define {
    create { _ -> null }
    update { sig: SignatureState?, tr ->
        val effect = tr.effects.firstNotNullOfOrNull { it.asType(signatureEffect) }
        if (effect != null) {
            effect.value
        } else if (sig != null && tr.docChanged) {
            // Keep the tooltip pinned to its (mapped) position across edits.
            sig.copy(pos = tr.changes.mapPos(DocPos(sig.pos)).value)
        } else {
            sig
        }
    }
    provide { field ->
        showTooltip.from(field) { sig -> sig?.let { signatureTooltip(it) } }
    }
}

/** Build the anchored [Tooltip] for a [SignatureState]. */
private fun signatureTooltip(state: SignatureState): Tooltip = Tooltip(
    pos = state.pos,
    above = true,
    content = { SignatureContent(state.data, state.active) }
)

/**
 * The Compose body of a signature-help tooltip: an optional `n/total` counter
 * when multiple signatures are available, the active signature's label with its
 * active parameter bolded, and any signature documentation.
 *
 * **Deviation from upstream (HTML → Compose):** upstream renders the label into
 * DOM nodes (wrapping the active parameter in a `<span>`) and injects the
 * documentation as HTML via `docToHTML`. kodemirror renders on a Compose canvas,
 * so the active parameter is highlighted with a bold [SpanStyle] and the
 * documentation reuses the shared markdown→Compose conversion ([HoverContent];
 * see its deviation note).
 */
@Composable
internal fun SignatureContent(data: SignatureHelp, active: Int) {
    val theme = LocalEditorTheme.current
    val baseStyle = TextStyle(color = theme.foreground)
    val signature = data.signatures.getOrNull(active) ?: return
    val range = activeParamRange(signature, data.activeParameter)
    Column(modifier = Modifier.padding(2.dp)) {
        if (data.signatures.size > 1) {
            BasicText(text = "${active + 1}/${data.signatures.size}", style = baseStyle)
        }
        BasicText(text = signatureLabel(signature.label, range), style = baseStyle)
        for (block in documentationBlocks(signature.documentation)) {
            BasicText(text = hoverBlockToAnnotatedString(block), style = baseStyle)
        }
    }
}

/**
 * Render a signature [label] with the slice given by [range] (the active
 * parameter) bolded. When [range] is null the label is returned verbatim.
 */
internal fun signatureLabel(label: String, range: ActiveParamRange?) = buildAnnotatedString {
    if (range == null || range.to <= range.from ||
        range.from !in 0..label.length || range.to !in 0..label.length
    ) {
        append(label)
        return@buildAnnotatedString
    }
    append(label.substring(0, range.from))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
        append(label.substring(range.from, range.to))
    }
    append(label.substring(range.to))
}

/**
 * The [ViewPlugin] that drives signature help for a single editor, requesting
 * `textDocument/signatureHelp` from the language server and dispatching
 * [signatureEffect]s into [signatureState].
 *
 * The plugin reads its per-editor [SignatureHelpServer] binding (client + uri)
 * from the [signatureHelpServer] facet of the session it is running in, so a
 * single shared [signatureHelpPlugin] handle works correctly for every open
 * editor. When no binding is present (the facet is null) every operation is a
 * no-op.
 *
 * Ports upstream `@codemirror/lsp-client`'s `signaturePlugin`:
 * - On an `input.type` transaction that inserts a server
 *   [triggerCharacter][com.monkopedia.lsp.SignatureHelpOptions.triggerCharacters]
 *   (or a [retriggerCharacter][com.monkopedia.lsp.SignatureHelpOptions.retriggerCharacters]
 *   while help is already showing), it fires a `TriggerCharacter` request.
 * - While help is showing and the selection moves, it debounces a
 *   `ContentChange` re-trigger by [RETRIGGER_DELAY] ms.
 * - [showSignatureHelp] fires an `Invoked` request.
 *
 * **Cancellation:** there is no explicit `$/cancelRequest`. A new request (or a
 * selection/edit that invalidates the in-flight one) cancels the previous
 * coroutine; the in-flight `textDocumentSignatureHelp` call is cancelled
 * cooperatively through structured concurrency, consistent with
 * [serverHover]/[serverCompletionSource].
 */
internal class SignatureHelpPlugin(
    private val session: EditorSession
) : PluginValue {
    private val job = SupervisorJob(session.coroutineScope.coroutineContext[Job])
    private val scope: CoroutineScope =
        CoroutineScope(session.coroutineScope.coroutineContext + job)

    /** The most recent in-flight request, cancelled when superseded. */
    private var pending: Job? = null

    /** The most recent debounced re-trigger job, cancelled when superseded. */
    private var delayed: Job? = null

    /** This editor's signature-help binding, or null when none is installed. */
    private val server: SignatureHelpServer?
        get() = session.state.facet(signatureHelpServer)

    override fun update(update: ViewUpdate) {
        val client = update.state.facet(signatureHelpServer)?.client ?: return
        val sigState = update.state.field(signatureState)

        var triggerCharacter: String? = null
        if (update.docChanged && update.transactions.any { it.isUserEvent("input.type") }) {
            val serverConf = client.serverCapabilities?.signatureHelpProvider
            val triggers = serverConf?.triggerCharacters.orEmpty() +
                (if (sigState != null) serverConf?.retriggerCharacters.orEmpty() else emptyList())
            if (triggers.isNotEmpty()) {
                update.changes.iterChanges({ _, _, _, _, inserted ->
                    val ins = inserted.toString()
                    if (ins.isNotEmpty()) {
                        for (ch in triggers) if (ch.isNotEmpty() && ins.contains(ch)) {
                            triggerCharacter = ch
                        }
                    }
                })
            }
        }

        when {
            triggerCharacter != null -> startRequest(
                SignatureHelpContext(
                    triggerKind = SignatureHelpTriggerKind.TRIGGER_CHARACTER,
                    isRetrigger = sigState != null,
                    triggerCharacter = triggerCharacter,
                    activeSignatureHelp = sigState?.data
                )
            )
            sigState != null && update.selectionSet -> {
                delayed?.cancel()
                delayed = scope.launch {
                    delay(RETRIGGER_DELAY)
                    startRequest(
                        SignatureHelpContext(
                            triggerKind = SignatureHelpTriggerKind.CONTENT_CHANGE,
                            isRetrigger = true,
                            activeSignatureHelp = sigState.data
                        )
                    )
                }
            }
        }
    }

    /** Issue a signature-help request with the given [context] and update state. */
    fun startRequest(context: SignatureHelpContext) {
        delayed?.cancel()
        delayed = null
        pending?.cancel()
        val pos = session.state.selection.main.head.value
        pending = scope.launch {
            val result = getSignatureHelp(pos, context) ?: run {
                if (session.state.field(signatureState) != null) {
                    dispatchEffect(null)
                }
                return@launch
            }
            if (result.signatures.isEmpty()) {
                if (session.state.field(signatureState) != null) dispatchEffect(null)
                return@launch
            }
            val cur = session.state.field(signatureState)
            val same = cur != null && sameSignatures(cur.data, result)
            val contentChange = context.triggerKind == SignatureHelpTriggerKind.CONTENT_CHANGE
            val active = if (same && contentChange) {
                cur.active
            } else {
                result.activeSignature?.toInt() ?: 0
            }
            // Nothing changed: leave the existing tooltip untouched.
            if (same && sameActiveParam(cur.data, result, active)) return@launch
            dispatchEffect(
                SignatureState(
                    data = result,
                    active = active,
                    pos = if (same) cur.pos else pos
                )
            )
        }
    }

    /**
     * Request `textDocument/signatureHelp` at [pos] with [context], or null when
     * this editor has no [signatureHelpServer] binding or the server has no
     * `signatureHelpProvider` capability. Rethrows [CancellationException] so
     * cancellation propagates cooperatively.
     */
    private suspend fun getSignatureHelp(pos: Int, context: SignatureHelpContext): SignatureHelp? {
        val binding = server ?: return null
        val client = binding.client
        if (client.serverCapabilities?.signatureHelpProvider == null) return null
        client.sync()
        val params = SignatureHelpParams(
            textDocument = TextDocumentIdentifier(uri = binding.uri),
            position = toPosition(pos, session.state.doc),
            context = context
        )
        return try {
            client.server.textDocumentSignatureHelp(params)
        } catch (e: CancellationException) {
            throw e
        }
    }

    private fun dispatchEffect(state: SignatureState?) {
        session.dispatch(TransactionSpec(effects = listOf(signatureEffect.of(state))))
    }

    override fun destroy() {
        job.cancel()
    }

    companion object {
        /**
         * Debounce, in milliseconds, before a selection move while signature
         * help is showing re-triggers a `ContentChange` request. Mirrors
         * upstream's 250ms `setTimeout`.
         */
        const val RETRIGGER_DELAY: Long = 250
    }
}

/**
 * The single, shared [ViewPlugin] handle for signature help.
 *
 * Defined once at module scope and installed by every [signatureHelp] call. The
 * plugin captures no client/uri — each instance reads its per-editor binding
 * from the [signatureHelpServer] facet of the session it is created for. Because
 * the handle is shared and stateless across editors, [showSignatureHelp]
 * resolves the correct per-editor plugin via [EditorSession.plugin] for every
 * open editor (no module-level mutable state to clobber).
 */
private val signatureHelpPlugin: ViewPlugin<SignatureHelpPlugin> =
    ViewPlugin.define(create = { session -> SignatureHelpPlugin(session) })

/**
 * Explicitly prompt the server to provide signature help at the cursor.
 *
 * Ports upstream `@codemirror/lsp-client`'s `showSignatureHelp` command: fires an
 * `Invoked` request through this editor's [SignatureHelpPlugin]. Returns false
 * when no signature-help extension is installed in [session].
 */
fun showSignatureHelp(session: EditorSession): Boolean {
    val plugin = session.plugin(signatureHelpPlugin) ?: return false
    val field = session.state.field(signatureState)
    plugin.startRequest(
        SignatureHelpContext(
            triggerKind = SignatureHelpTriggerKind.INVOKED,
            isRetrigger = field != null,
            activeSignatureHelp = field?.data
        )
    )
    return true
}

/**
 * If a signature tooltip with multiple signatures is showing, move to the next
 * one. Ports upstream's `nextSignature`. Returns false when no tooltip is active.
 */
fun nextSignature(session: EditorSession): Boolean {
    val field = session.state.field(signatureState) ?: return false
    val next = nextActive(field.active, field.data.signatures.size)
    if (next != field.active) {
        session.dispatch(
            TransactionSpec(effects = listOf(signatureEffect.of(field.copy(active = next))))
        )
    }
    return true
}

/**
 * If a signature tooltip with multiple signatures is showing, move to the
 * previous one. Ports upstream's `prevSignature`. Returns false when no tooltip
 * is active.
 */
fun prevSignature(session: EditorSession): Boolean {
    val field = session.state.field(signatureState) ?: return false
    val prev = prevActive(field.active)
    if (prev != field.active) {
        session.dispatch(
            TransactionSpec(effects = listOf(signatureEffect.of(field.copy(active = prev))))
        )
    }
    return true
}

/**
 * The default signature-help key bindings, matching upstream's
 * `signatureKeymap`:
 * - `Mod-Shift-Space` ([showSignatureHelp]),
 * - `Mod-Shift-ArrowUp` ([prevSignature]),
 * - `Mod-Shift-ArrowDown` ([nextSignature]).
 *
 * (`Mod` is `Ctrl` on non-Mac platforms and `Meta`/Cmd on macOS.)
 */
val signatureKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Mod-Shift-Space", run = ::showSignatureHelp),
    KeyBinding(key = "Mod-Shift-ArrowUp", run = ::prevSignature),
    KeyBinding(key = "Mod-Shift-ArrowDown", run = ::nextSignature)
)

/**
 * Build the editor [Extension] that enables LSP signature help from the language
 * server wrapped by [client] for the file at [uri].
 *
 * Ports upstream `@codemirror/lsp-client`'s `signatureHelp`. It installs the
 * [signatureState] field (which provides the tooltip to the [showTooltip] facet)
 * and the [SignatureHelpPlugin] (which requests help on trigger characters /
 * explicit invocation and cycles signatures), and — unless
 * [keymap][SignatureHelpConfig.keymap] is false — the [signatureKeymap] bindings
 * at high precedence.
 *
 * @param client The LSP client wrapping the language server.
 * @param uri The document URI for this editor's file.
 * @param config Signature-help configuration. See [SignatureHelpConfig].
 */
fun signatureHelp(
    client: LSPClient,
    uri: String,
    config: SignatureHelpConfig = SignatureHelpConfig()
): Extension {
    val extensions = buildList {
        add(signatureHelpServer.of(SignatureHelpServer(client, uri)))
        add(signatureHelpPlugin.asExtension())
        add(signatureState)
        if (config.keymap) add(Prec.high(keymap.of(signatureKeymap)))
    }
    return ExtensionList(extensions)
}
