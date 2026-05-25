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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.view.LocalEditorTheme
import com.monkopedia.kodemirror.view.Tooltip
import com.monkopedia.kodemirror.view.hoverTooltip
import com.monkopedia.lsp.Hover
import com.monkopedia.lsp.HoverContents
import com.monkopedia.lsp.HoverParams
import com.monkopedia.lsp.MarkedString
import com.monkopedia.lsp.MarkupContent
import com.monkopedia.lsp.MarkupKind
import com.monkopedia.lsp.StringOr
import com.monkopedia.lsp.TextDocumentIdentifier
import kotlinx.coroutines.CancellationException

/**
 * A single rendered block of LSP hover content.
 *
 * The LSP `Hover.contents` field is a union (`MarkupContent | MarkedString |
 * MarkedString[]`, modelled by [HoverContents]); [parseHoverContents] flattens
 * it into an ordered list of these blocks for rendering.
 *
 * @param text The textual body of the block.
 * @param language When non-null, the block is a fenced code block tagged with
 *   this language (a `MarkedString` with a `language`, or a fenced block parsed
 *   out of markdown). When null, the block is markdown (or plain) prose.
 * @param markdown Whether [text] should be interpreted as markdown. Plain
 *   `string`/`PlainText` content sets this false so markdown punctuation is
 *   shown literally; `MarkupContent` of kind `markdown` sets it true.
 */
internal data class HoverBlock(
    val text: String,
    val language: String? = null,
    val markdown: Boolean = false
)

/**
 * Parse the typed `textDocument/hover` result's [contents][Hover.contents]
 * (a strict [HoverContents] union) into an ordered list of [HoverBlock]s.
 *
 * Handles every variant the LSP `Hover.contents` union allows:
 * - [MarkupContentValue][HoverContents.MarkupContentValue] — a `MarkupContent`
 *   `{kind, value}` (markdown when [kind][MarkupContent.kind] is
 *   [MARKDOWN][MarkupKind.MARKDOWN]),
 * - [MarkedStringValue][HoverContents.MarkedStringValue] — a `MarkedString`,
 *   i.e. either a `string` (rendered as a markdown block, per the LSP spec) or
 *   `{language, value}` (a fenced code block in that language),
 * - [MarkedStringArray][HoverContents.MarkedStringArray] — an array of
 *   `MarkedString` (each element handled as above).
 *
 * Empty/blank blocks are dropped. Returns an empty list when there is nothing to
 * show (which the caller treats as "no tooltip").
 */
internal fun parseHoverContents(contents: HoverContents): List<HoverBlock> = when (contents) {
    is HoverContents.MarkupContentValue ->
        markupContentBlock(contents.value)?.let { listOf(it) } ?: emptyList()
    is HoverContents.MarkedStringValue ->
        markedStringBlock(contents.value)?.let { listOf(it) } ?: emptyList()
    is HoverContents.MarkedStringArray ->
        contents.value.mapNotNull { markedStringBlock(it) }
}

/**
 * Convert one [MarkupContent] to a [HoverBlock], rendering it as markdown only
 * when its [kind][MarkupContent.kind] is [MARKDOWN][MarkupKind.MARKDOWN]. Blank
 * content yields `null` (dropped).
 */
private fun markupContentBlock(markup: MarkupContent): HoverBlock? = markup.value
    .takeIf { it.isNotBlank() }
    ?.let { HoverBlock(text = it, markdown = markup.kind == MarkupKind.MARKDOWN) }

/**
 * Convert one `MarkedString` to a [HoverBlock]: a bare `string` becomes a
 * markdown block (per the LSP spec), while `{language, value}` becomes a fenced
 * code block in that language. Blank content yields `null` (dropped).
 */
private fun markedStringBlock(marked: MarkedString): HoverBlock? = when (marked) {
    is StringOr.StringValue ->
        marked.value
            .takeIf { it.isNotBlank() }
            ?.let { HoverBlock(text = it, markdown = true) }
    is StringOr.Value ->
        marked.value.value
            .takeIf { it.isNotBlank() }
            ?.let { HoverBlock(text = it, language = marked.value.language, markdown = false) }
}

/**
 * Convert a typed LSP `string | MarkupContent` documentation value (as carried
 * by [SignatureInformation][com.monkopedia.lsp.SignatureInformation] and
 * [ParameterInformation][com.monkopedia.lsp.ParameterInformation]) into the same
 * ordered list of [HoverBlock]s used to render hover popups, so the shared
 * markdown→Compose conversion ([hoverBlockToAnnotatedString]/[HoverContent]) can
 * be reused for signature-help documentation.
 *
 * A plain `string` is rendered as non-markdown prose; a `MarkupContent` is
 * rendered as markdown only when its [kind][MarkupContent.kind] is
 * [MARKDOWN][MarkupKind.MARKDOWN]. Blank values yield an empty list.
 */
internal fun documentationBlocks(documentation: StringOr<MarkupContent>?): List<HoverBlock> =
    when (documentation) {
        null -> emptyList()
        is StringOr.StringValue ->
            documentation.value
                .takeIf { it.isNotBlank() }
                ?.let { listOf(HoverBlock(text = it, markdown = false)) }
                ?: emptyList()
        is StringOr.Value -> {
            val markup = documentation.value
            markup.value
                .takeIf { it.isNotBlank() }
                ?.let {
                    listOf(HoverBlock(text = it, markdown = markup.kind == MarkupKind.MARKDOWN))
                }
                ?: emptyList()
        }
    }

/**
 * Styling knobs for rendering a [HoverBlock] to an [AnnotatedString], so the
 * pure markdown→annotated-string conversion can be unit-tested without a Compose
 * runtime (the per-style attributes are resolved by the caller).
 */
internal class HoverMarkdownStyles(
    val bold: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold),
    val italic: SpanStyle = SpanStyle(fontStyle = FontStyle.Italic),
    val code: SpanStyle = SpanStyle(fontFamily = FontFamily.Monospace),
    val heading: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold)
)

/**
 * Convert one [block]'s text to an [AnnotatedString].
 *
 * **Deviation from upstream (HTML → Compose):** upstream `@codemirror/lsp-client`
 * renders hover `MarkupContent` to HTML via `docToHTML` and injects it into the
 * tooltip DOM. kodemirror's editor renders on a Compose canvas with no DOM, so
 * there is nothing to inject HTML into. Instead this performs a small, dependency
 * -free markdown→[AnnotatedString] conversion covering the inline/structure
 * constructs that appear in practice in hover popups:
 *
 * - `**bold**` / `__bold__`
 * - `*italic*` / `_italic_`
 * - `` `inline code` ``
 * - `# heading` … `###### heading` (the marker is stripped, the line bolded)
 * - fenced code blocks delimited by ``` are rendered monospace (the info string
 *   is dropped)
 *
 * A `MarkedString` with a `language` (a [HoverBlock] with non-null
 * [language][HoverBlock.language]) is always rendered as monospace code with no
 * markdown interpretation. Non-markdown ([markdown][HoverBlock.markdown] = false)
 * prose is emitted verbatim. Anything not in the list above (links, lists,
 * tables, images, raw HTML) is rendered as its literal source text rather than
 * being interpreted — this is the documented limit of the conversion.
 */
internal fun hoverBlockToAnnotatedString(
    block: HoverBlock,
    styles: HoverMarkdownStyles = HoverMarkdownStyles()
): AnnotatedString {
    if (block.language != null) {
        // Fenced/typed code block: monospace, no markdown.
        return buildAnnotatedString {
            withStyle(styles.code) { append(block.text) }
        }
    }
    if (!block.markdown) {
        return AnnotatedString(block.text)
    }
    return buildAnnotatedString {
        val lines = block.text.split("\n")
        var inFence = false
        var first = true
        for (line in lines) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                inFence = !inFence
                continue
            }
            if (!first) append('\n')
            first = false
            when {
                inFence -> withStyle(styles.code) { append(line) }
                trimmed.startsWith("#") -> {
                    val heading = trimmed.trimStart('#').trimStart()
                    withStyle(styles.heading) { appendInlineMarkdown(heading, styles) }
                }
                else -> appendInlineMarkdown(line, styles)
            }
        }
    }
}

/**
 * Append [text] to the builder, interpreting inline markdown emphasis
 * (`**`/`__` bold, `*`/`_` italic) and `` ` `` inline code. Unmatched markers
 * are emitted literally.
 */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    styles: HoverMarkdownStyles
) {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(styles.code) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(c)
                    i++
                }
            }
            (c == '*' || c == '_') && i + 1 < text.length && text[i + 1] == c -> {
                val marker = "$c$c"
                val end = text.indexOf(marker, i + 2)
                if (end > i + 1) {
                    withStyle(styles.bold) {
                        appendInlineMarkdown(text.substring(i + 2, end), styles)
                    }
                    i = end + 2
                } else {
                    append(c)
                    i++
                }
            }
            c == '*' || c == '_' -> {
                val end = text.indexOf(c, i + 1)
                if (end > i) {
                    withStyle(styles.italic) {
                        appendInlineMarkdown(text.substring(i + 1, end), styles)
                    }
                    i = end + 1
                } else {
                    append(c)
                    i++
                }
            }
            else -> {
                append(c)
                i++
            }
        }
    }
}

/**
 * The Compose body of an LSP hover tooltip: each parsed [HoverBlock] rendered as
 * a line of styled text. See [hoverBlockToAnnotatedString] for the conversion and
 * its documented HTML→Compose deviation.
 */
@Composable
internal fun HoverContent(blocks: List<HoverBlock>) {
    val theme = LocalEditorTheme.current
    val baseStyle = TextStyle(color = theme.foreground)
    Column(modifier = Modifier.padding(2.dp)) {
        for (block in blocks) {
            BasicText(
                text = hoverBlockToAnnotatedString(block),
                style = baseStyle
            )
        }
    }
}

/**
 * Compute the document position the hover tooltip should anchor to, honoring an
 * LSP [Hover.range] when the server supplies one (and remapping it through any
 * edits that happened while the request was in flight via [mapping]). When no
 * range is given the tooltip anchors at the pointer [pos], matching upstream.
 *
 * @param hover The server's hover result.
 * @param pos The document offset the pointer was over when the request fired.
 * @param prevDoc The document the request was issued against.
 * @param mapping Live mapping from the request-time document to the current one.
 */
internal fun hoverTooltipPos(
    hover: Hover,
    pos: Int,
    prevDoc: com.monkopedia.kodemirror.state.Text,
    mapping: WorkspaceMapping?
): Int {
    val range = hover.range ?: return pos
    val start = fromPosition(range.start, prevDoc)
    return mapping?.mapPos(start) ?: start
}

/**
 * Build the editor [Extension] that shows LSP hover information from the language
 * server wrapped by [client] for the file at [uri].
 *
 * Ports upstream `@codemirror/lsp-client`'s `hoverTooltips`. On a hover that
 * rests for [hoverTime] ms it:
 * 1. checks the server advertises the `hoverProvider` capability (else no-op),
 * 2. flushes pending edits ([LSPClient.sync]) so the server resolves against the
 *    current text,
 * 3. captures a [WorkspaceMapping] so the response range can be remapped if the
 *    document changes while the request is in flight,
 * 4. issues `textDocument/hover` at the pointer position,
 * 5. parses the [contents][Hover.contents] [HoverContents] union
 *    ([parseHoverContents]) and
 *    renders it to Compose ([HoverContent]; see its HTML→Compose deviation
 *    note), anchored at the [Hover.range] start when present.
 *
 * The async hover bridge uses `:view`'s suspending
 * [hoverTooltip][com.monkopedia.kodemirror.view.hoverTooltip] overload, which
 * debounces by [hoverTime] and cancels an in-flight request when the pointer
 * moves — matching upstream's hover-delay + cancel-on-move semantics. There is
 * no explicit `$/cancelRequest`; the in-flight call is cancelled cooperatively
 * through structured concurrency (consistent with [serverCompletionSource]).
 *
 * @param client The LSP client wrapping the language server.
 * @param uri The document URI for this editor's file.
 * @param hoverTime Delay, in milliseconds, before a resting pointer triggers the
 *   request. Defaults to `:view`'s
 *   [DEFAULT_HOVER_TIME][com.monkopedia.kodemirror.view.DEFAULT_HOVER_TIME].
 */
fun serverHover(
    client: LSPClient,
    uri: String,
    hoverTime: Long = com.monkopedia.kodemirror.view.DEFAULT_HOVER_TIME
): Extension = hoverTooltip(hoverTime) hover@{ session, pos ->
    if (client.serverCapabilities?.hoverProvider == null) return@hover null

    // Flush pending edits so the server resolves against current text.
    client.sync()

    val prevDoc = session.state.doc
    val mapping = client.workspace.getMapping(uri)
    try {
        val params = HoverParams(
            textDocument = TextDocumentIdentifier(uri = uri),
            position = toPosition(pos, prevDoc)
        )
        val hover = try {
            client.server.textDocumentHover(params)
        } catch (e: CancellationException) {
            throw e
        }
        val blocks = parseHoverContents(hover.contents)
        if (blocks.isEmpty()) return@hover null
        val anchor = hoverTooltipPos(hover, pos, prevDoc, mapping)
        Tooltip(pos = anchor, content = { HoverContent(blocks) })
    } finally {
        mapping?.let { client.workspace.releaseMapping(it) }
    }
}
