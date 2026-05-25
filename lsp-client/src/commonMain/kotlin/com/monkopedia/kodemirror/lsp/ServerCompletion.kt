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

import com.monkopedia.kodemirror.autocomplete.Completion
import com.monkopedia.kodemirror.autocomplete.CompletionApplyContext
import com.monkopedia.kodemirror.autocomplete.CompletionConfig
import com.monkopedia.kodemirror.autocomplete.CompletionContext
import com.monkopedia.kodemirror.autocomplete.CompletionResult
import com.monkopedia.kodemirror.autocomplete.CompletionSource
import com.monkopedia.kodemirror.autocomplete.SuspendCompletionSource
import com.monkopedia.kodemirror.autocomplete.asyncCompletionSource
import com.monkopedia.kodemirror.autocomplete.autocompletion
import com.monkopedia.kodemirror.autocomplete.snippet
import com.monkopedia.kodemirror.autocomplete.snippets
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.lsp.CompletionItem
import com.monkopedia.lsp.CompletionItemKind
import com.monkopedia.lsp.CompletionItemTextEdit
import com.monkopedia.lsp.CompletionList
import com.monkopedia.lsp.CompletionParams
import com.monkopedia.lsp.InsertReplaceEdit
import com.monkopedia.lsp.InsertTextFormat
import com.monkopedia.lsp.MarkupContent
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.StringOr
import com.monkopedia.lsp.TextDocumentCompletionResult
import com.monkopedia.lsp.TextDocumentIdentifier
import com.monkopedia.lsp.TextEdit

/**
 * Configuration for [serverCompletion].
 *
 * Mirrors the shape of the options object upstream `@codemirror/lsp-client`'s
 * `serverCompletion` accepts.
 *
 * @param override When true, the language-server completion source *replaces*
 *   all other completion sources (upstream's `autocompletion({override: [...]})`
 *   path). When false (the default) it is installed as the editor's completion
 *   source — see the deviation note on [serverCompletion].
 * @param validFor A custom [Regex] used as the
 *   [CompletionResult.validFor][com.monkopedia.kodemirror.autocomplete.CompletionResult.validFor]
 *   for results, allowing the cached list to be reused as the user keeps typing.
 *   When null, a regex derived from the result items' prefixes is used (matching
 *   upstream's `prefixRegexp`).
 */
data class ServerCompletionConfig(
    val override: Boolean = false,
    val validFor: Regex? = null
)

/**
 * The kind→type mapping upstream `@codemirror/lsp-client` uses to translate an
 * LSP [CompletionItemKind] into the `:autocomplete`
 * [type][Completion.type] string (which selects the completion icon).
 *
 * Kinds without an entry (e.g. `Unit` only maps to `keyword`, but `File`,
 * `Folder`, `Reference`, `Event`, `Operator`, `Snippet`) leave [Completion.type]
 * null — exactly as upstream's `kindToType` table, which is intentionally
 * partial.
 */
internal fun kindToType(kind: CompletionItemKind?): String? = when (kind) {
    CompletionItemKind.TEXT -> "text"
    CompletionItemKind.METHOD -> "method"
    CompletionItemKind.FUNCTION -> "function"
    CompletionItemKind.CONSTRUCTOR -> "class"
    CompletionItemKind.FIELD -> "property"
    CompletionItemKind.VARIABLE -> "variable"
    CompletionItemKind.CLASS -> "class"
    CompletionItemKind.INTERFACE -> "interface"
    CompletionItemKind.MODULE -> "namespace"
    CompletionItemKind.PROPERTY -> "property"
    CompletionItemKind.UNIT -> "keyword"
    CompletionItemKind.VALUE -> "constant"
    CompletionItemKind.ENUM -> "constant"
    CompletionItemKind.KEYWORD -> "keyword"
    CompletionItemKind.COLOR -> "constant"
    CompletionItemKind.ENUM_MEMBER -> "constant"
    CompletionItemKind.CONSTANT -> "constant"
    CompletionItemKind.STRUCT -> "class"
    CompletionItemKind.TYPE_PARAMETER -> "type"
    else -> null
}

/**
 * Translate an LSP snippet template (the `newText`/`insertText` of an item whose
 * [insertTextFormat][CompletionItem.insertTextFormat] is
 * [InsertTextFormat.SNIPPET]) into the `${name}` template the `:autocomplete`
 * [snippet] applicator understands.
 *
 * LSP snippet syntax (a subset of TextMate snippets) uses numbered tab stops:
 * `$1`, `$2`, …, the final cursor stop `$0`, and placeholder stops
 * `${1:label}`. `:autocomplete`'s [snippet] supports only `${name}` named stops
 * (no nested placeholders, no choices, no variables), navigated in document
 * order via Tab/Shift-Tab.
 *
 * **Deviation:** `:autocomplete` snippets cannot represent placeholder *text*
 * inside a tab stop, so `${1:foo}` is mapped to an empty `${1}` stop (the `foo`
 * default text is dropped) and the numeric ordering is preserved only insofar as
 * the stops appear in document order. `$0` becomes the named stop `${0}`. This
 * matches upstream's own lossy translation (`text.replace(/\$(\d+)/g, "${$1}")`)
 * for the bare-`$N` case; the placeholder-text and escape handling here is a
 * faithful superset. Backslash escapes (`\$`, `\}`, `\\`) are unescaped to their
 * literal characters.
 */
internal fun lspSnippetToTemplate(snippet: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < snippet.length) {
        val c = snippet[i]
        when {
            c == '\\' && i + 1 < snippet.length -> {
                // Escaped character: emit the next char literally.
                out.append(snippet[i + 1])
                i += 2
            }
            c == '$' && i + 1 < snippet.length && snippet[i + 1] == '{' -> {
                // ${ ... } placeholder. Find the matching closing brace.
                val end = snippet.indexOf('}', i + 2)
                if (end == -1) {
                    out.append(c)
                    i++
                } else {
                    val body = snippet.substring(i + 2, end)
                    // Body is "name", "name:default", or "name|a,b,c|" (choice).
                    val name = body.substringBefore(':').substringBefore('|')
                    out.append("\${").append(name).append('}')
                    i = end + 1
                }
            }
            c == '$' && i + 1 < snippet.length && snippet[i + 1].isDigit() -> {
                // $N tab stop.
                var j = i + 1
                while (j < snippet.length && snippet[j].isDigit()) j++
                val name = snippet.substring(i + 1, j)
                out.append("\${").append(name).append('}')
                i = j
            }
            else -> {
                out.append(c)
                i++
            }
        }
    }
    return out.toString()
}

/** Extract the documentation string from an LSP `string | MarkupContent`. */
internal fun documentationText(documentation: StringOr<MarkupContent>?): String? =
    when (documentation) {
        null -> null
        is StringOr.StringValue -> documentation.value.takeIf { it.isNotEmpty() }
        is StringOr.Value -> documentation.value.value.takeIf { it.isNotEmpty() }
    }

/** The LSP [Range] of a [CompletionItemTextEdit], whichever variant it is. */
internal fun CompletionItemTextEdit.range(): Range = when (this) {
    is TextEdit -> range
    is InsertReplaceEdit -> replace
}

/** The `newText` of a [CompletionItemTextEdit], whichever variant it is. */
internal fun CompletionItemTextEdit.newText(): String = when (this) {
    is TextEdit -> newText
    is InsertReplaceEdit -> newText
}

/**
 * Decide the text a [CompletionItem] should insert, following upstream's
 * precedence: `textEdit.newText` then `textEditText` then `insertText` then
 * `label`.
 */
internal fun CompletionItem.insertionText(): String =
    textEdit?.newText() ?: textEditText ?: insertText ?: label

/**
 * Map a single LSP [CompletionItem] to an `:autocomplete` [Completion].
 *
 * Mirrors upstream `@codemirror/lsp-client`'s per-item mapping in
 * `serverCompletionSource`:
 * - `label` is the insertion text (`textEdit.newText`/`textEditText`/
 *   `insertText`/`label`), except for snippets where the display label is the
 *   item's own [label][CompletionItem.label].
 * - `type` is derived from [kind][CompletionItem.kind] via [kindToType].
 * - `detail`/`sortText` are carried through.
 * - `documentation` (string or markdown) becomes the [info][Completion.info].
 * - When [insertTextFormat][CompletionItem.insertTextFormat] is
 *   [Snippet][InsertTextFormat.SNIPPET], an [applyFn][Completion.applyFn] is
 *   installed that expands the LSP snippet template (see [lspSnippetToTemplate]).
 * - [additionalTextEdits][CompletionItem.additionalTextEdits] are applied
 *   alongside the primary insertion via a custom [applyFn] (see the note on
 *   [serverCompletionSource] — upstream does not currently apply these).
 *
 * **Deviations:** `:autocomplete`'s [Completion] has no slot for
 * `commitCharacters`, so [commitCharacters][CompletionItem.commitCharacters] /
 * [command][CompletionItem.command] are dropped (and noted) here. The
 * `documentation` markup kind is also flattened to its raw `value` string
 * because [Completion.info] is a plain string.
 *
 * @param item The LSP completion item to map.
 * @param doc The document the completion applies to, used to resolve any
 *   [additionalTextEdits][CompletionItem.additionalTextEdits] ranges.
 */
internal fun mapCompletionItem(item: CompletionItem, doc: Text): Completion {
    val text = item.insertionText()
    val isSnippet = item.insertTextFormat == InsertTextFormat.SNIPPET
    val extraEdits = item.additionalTextEdits.orEmpty()

    val type = kindToType(item.kind)
    val info = documentationText(item.documentation)

    if (isSnippet) {
        // Snippet: display the item's own label, expand the template on apply.
        val template = lspSnippetToTemplate(text)
        val baseApply = snippet(template)
        val applyFn: (CompletionApplyContext) -> Unit = if (extraEdits.isEmpty()) {
            baseApply
        } else {
            { ctx ->
                applyAdditionalTextEdits(ctx, doc, extraEdits)
                baseApply(ctx)
            }
        }
        return Completion(
            label = item.label,
            detail = item.detail,
            info = info,
            type = type,
            applyFn = applyFn
        )
    }

    // Plain-text completion. The label *is* the inserted text (upstream sets
    // both label and the implicit apply text to `text`).
    val applyFn: ((CompletionApplyContext) -> Unit)? = if (extraEdits.isEmpty()) {
        null
    } else {
        { ctx ->
            applyAdditionalTextEdits(ctx, doc, extraEdits)
            insertPlainText(ctx, text)
        }
    }
    return Completion(
        label = text,
        detail = item.detail,
        info = info,
        type = type,
        applyFn = applyFn
    )
}

/** Insert [text] over the completion's replace range (plain, non-snippet path). */
private fun insertPlainText(ctx: CompletionApplyContext, text: String) {
    ctx.session.dispatch(
        TransactionSpec(
            changes = ChangeSpec.Single(ctx.from, ctx.to, InsertContent.StringContent(text)),
            selection = SelectionSpec.CursorSpec(ctx.from + text.length),
            userEvent = "input.complete"
        )
    )
}

/**
 * Apply an LSP item's [additionalTextEdits] (e.g. an auto-import added at the
 * top of the file) as a single transaction against the editor session in
 * [ctx]. Edits are resolved against [doc] with [fromPosition].
 */
private fun applyAdditionalTextEdits(
    ctx: CompletionApplyContext,
    doc: Text,
    edits: List<TextEdit>
) {
    if (edits.isEmpty()) return
    val specs = edits.map { edit ->
        val from = fromPosition(edit.range.start, doc)
        val to = fromPosition(edit.range.end, doc)
        ChangeSpec.Single(
            DocPos(minOf(from, to)),
            DocPos(maxOf(from, to)),
            InsertContent.StringContent(edit.newText)
        )
    }
    ctx.session.dispatch(
        TransactionSpec(
            changes = ChangeSpec.Multi(specs),
            userEvent = "input.complete"
        )
    )
}

/**
 * Build a [Regex] that matches word characters optionally prefixed by any
 * non-word prefixes found in [items], for use as a result's
 * [validFor][CompletionResult.validFor].
 *
 * Ports upstream `prefixRegexp`: it samples up to ~50 items, collects the
 * leading non-word run of each sampled insertion text, and produces
 * `^(?:prefixA|prefixB)?\w*$`. When no non-word prefixes are found it returns
 * `^\w*$`.
 */
internal fun prefixRegexp(items: List<CompletionItem>): Regex {
    if (items.isEmpty()) return Regex("^\\w*$")
    val step = ((items.size + 49) / 50).coerceAtLeast(1)
    val prefixes = mutableListOf<String>()
    var i = 0
    while (i < items.size) {
        val text = items[i].insertionText()
        val first = text.firstOrNull()
        if (first != null && !isWordChar(first)) {
            val prefix = text.takeWhile { !isWordChar(it) }
            if (prefix !in prefixes) prefixes.add(prefix)
        }
        i += step
    }
    if (prefixes.isEmpty()) return Regex("^\\w*$")
    val alts = prefixes.joinToString("|") { escapeRegexLiteral(it) }
    return Regex("^(?:$alts)?\\w*$")
}

private fun isWordChar(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

private fun escapeRegexLiteral(s: String): String = buildString {
    for (c in s) {
        if (!c.isWhitespace() && !isWordChar(c)) append('\\')
        append(c)
    }
}

/**
 * Resolve the `[from, to)` range a completion result applies to, following
 * upstream's `completionResultRange`.
 *
 * Preference order: the first item's [textEdit][CompletionItem.textEdit] range,
 * else the word at the cursor, else an empty range at the cursor. LSP edit
 * ranges are single-line and contain the cursor, so the range is resolved on the
 * cursor's line via the start/end `character` offsets (mirroring upstream's
 * `line.from + range.start.character`).
 */
internal fun completionResultRange(
    context: CompletionContext,
    items: List<CompletionItem>
): Pair<DocPos, DocPos> {
    val pos = context.pos
    if (items.isEmpty()) return pos to pos
    val range: Range? = items.firstOrNull()?.textEdit?.range()
    if (range == null) {
        val word = context.state.wordAt(pos)
        return if (word != null) word.from to word.to else pos to pos
    }
    val line = context.state.doc.lineAt(pos)
    val from = line.from + range.start.character.toInt()
    val to = line.from + range.end.character.toInt()
    return from to to
}

/**
 * Normalize the typed `textDocument/completion` result into a [CompletionList].
 *
 * Since the RC4 lsp bump the typed
 * [LanguageServer][com.monkopedia.lsp.LanguageServer] returns the strict sealed
 * [TextDocumentCompletionResult] (was a raw `JsonElement`). The LSP result union
 * is still `CompletionItem[] | CompletionList | null`:
 * - [CompletionListValue][TextDocumentCompletionResult.CompletionListValue]
 *   carries the [CompletionList] verbatim (preserving its `isIncomplete`),
 * - [CompletionItemArray][TextDocumentCompletionResult.CompletionItemArray] is
 *   wrapped into a list with `isIncomplete = false`,
 * - a `null` result (the server returning nothing) becomes null here.
 */
internal fun parseCompletionResult(result: TextDocumentCompletionResult?): CompletionList? =
    when (result) {
        null -> null
        is TextDocumentCompletionResult.CompletionListValue -> result.value
        is TextDocumentCompletionResult.CompletionItemArray ->
            CompletionList(isIncomplete = false, items = result.value)
    }

/**
 * The suspend completion source that requests completions from the language
 * server wrapped by [client] for the file at [uri].
 *
 * Ports upstream `@codemirror/lsp-client`'s `serverCompletionSource`:
 * - If the server has no `completionProvider` capability, returns null.
 * - For implicit (non-explicit) triggers, only fires when the character before
 *   the cursor is an identifier char (`[A-Za-z_]`) or one of the server's
 *   advertised `triggerCharacters`.
 * - Flushes pending document changes ([LSPClient.sync]) before requesting.
 * - Requests `textDocument/completion` at the cursor, maps the result via
 *   [mapCompletionItem], and resolves the apply range via
 *   [completionResultRange].
 * - Uses [config]'s `validFor` or, failing that, [prefixRegexp].
 *
 * **Cancellation:** there is no explicit `$/cancelRequest`. The autocomplete
 * machinery cancels the coroutine running this source when the user types
 * again (the agreed design); the in-flight `textDocumentCompletion` call is
 * cancelled cooperatively through structured concurrency. (Upstream instead
 * sends an explicit cancel notification.)
 *
 * **`isIncomplete`:** when the server marks the list incomplete, the result is
 * returned with `validFor = null` so `:autocomplete` re-queries on the next
 * keystroke instead of filtering the stale list; complete lists get the
 * `validFor` regex so they can be filtered locally.
 */
fun serverCompletionSource(
    client: LSPClient,
    uri: String,
    config: ServerCompletionConfig = ServerCompletionConfig()
): SuspendCompletionSource = source@{ context ->
    if (client.serverCapabilities?.completionProvider == null) return@source null

    val doc = context.state.doc
    if (!context.explicit) {
        val pos = context.pos.value
        val triggerChar = if (pos > 0) doc.sliceString(DocPos(pos - 1), DocPos(pos)) else ""
        val triggers = client.serverCapabilities?.completionProvider?.triggerCharacters
        val isIdentChar = triggerChar.length == 1 && isWordChar(triggerChar[0])
        if (!isIdentChar && (triggers == null || triggerChar !in triggers)) return@source null
    }

    // Flush pending edits so the server completes against current text.
    client.sync()

    val params = CompletionParams(
        textDocument = TextDocumentIdentifier(uri = uri),
        position = toPosition(context.pos.value, doc)
    )
    val raw = client.server.textDocumentCompletion(params)
    val result = parseCompletionResult(raw) ?: return@source null

    val items = result.items
    val (from, to) = completionResultRange(context, items)
    val options = items.map { mapCompletionItem(it, doc) }
    val validFor = if (result.isIncomplete) null else (config.validFor ?: prefixRegexp(items))

    CompletionResult(
        from = from,
        to = to,
        options = options,
        validFor = validFor
    )
}

/**
 * Register the language-server [completion source][serverCompletionSource] as
 * the editor's autocompletion source.
 *
 * Ports upstream `@codemirror/lsp-client`'s `serverCompletion`. The returned
 * [Extension] installs `:autocomplete`'s [autocompletion] (configured with the
 * server source as the [override][CompletionConfig.override] source) plus
 * [snippets] support so snippet completions can be tab-navigated.
 *
 * **Deviation from upstream:** kodemirror's `CompletionContext` does not expose
 * the editor view/session, so the source cannot look the [LSPPlugin] up from the
 * context the way upstream does. Instead [client]/[uri] are captured here. As a
 * consequence the non-`override` upstream path (registering the source as
 * editor *language data* alongside other sources) is not available — kodemirror
 * `:autocomplete` only consults [CompletionConfig.override], so this always
 * installs the source there. [ServerCompletionConfig.override] is accepted for
 * API parity but does not change behavior; see the follow-up note below.
 *
 * @param client The LSP client wrapping the language server.
 * @param uri The document URI for this editor's file.
 * @param config Completion configuration. See [ServerCompletionConfig].
 */
fun serverCompletion(
    client: LSPClient,
    uri: String,
    config: ServerCompletionConfig = ServerCompletionConfig()
): Extension {
    val source: CompletionSource = asyncCompletionSource(
        serverCompletionSource(client, uri, config)
    )
    return ExtensionList(
        listOf(
            autocompletion(CompletionConfig(override = listOf(source))),
            snippets()
        )
    )
}
