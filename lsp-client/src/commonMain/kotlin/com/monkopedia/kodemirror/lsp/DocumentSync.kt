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

import com.monkopedia.kodemirror.state.ChangeSet
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.ServerCapabilitiesTextDocumentSync
import com.monkopedia.lsp.TextDocumentContentChangeEvent
import com.monkopedia.lsp.TextDocumentContentChangeEventRange
import com.monkopedia.lsp.TextDocumentContentChangeEventVariant
import com.monkopedia.lsp.TextDocumentSyncKind
import com.monkopedia.lsp.TextDocumentSyncOptions

/**
 * Convert a kodemirror document offset to an LSP [Position].
 *
 * Ports upstream `@codemirror/lsp-client`'s `LSPPlugin.toPosition`. LSP
 * positions are `{line, character}` pairs where `line` is 0-based and
 * `character` is a 0-based UTF-16 code-unit offset within that line. Kotlin
 * [String]/[Char] are already UTF-16 code units, so within a line the offset is
 * a direct character index.
 *
 * @param offset The document offset (a 0-based character offset).
 * @param doc The document the offset refers to.
 */
fun toPosition(offset: Int, doc: Text): Position {
    val line = doc.lineAt(DocPos(offset.coerceIn(0, doc.length)))
    return Position(
        line = (line.number.value - 1).toUInt(),
        character = (offset - line.from.value).coerceAtLeast(0).toUInt()
    )
}

/**
 * Convert an LSP [Position] to a kodemirror document offset.
 *
 * Ports upstream `@codemirror/lsp-client`'s `LSPPlugin.fromPosition`. The line
 * is 1-based in kodemirror but 0-based in LSP, and `character` is a UTF-16
 * code-unit offset within the line. Out-of-range line/character values are
 * clamped to the document, mirroring the LSP spec.
 *
 * @param pos The LSP position to convert.
 * @param doc The document the position refers to.
 */
fun fromPosition(pos: Position, doc: Text): Int {
    val lineNumber = (pos.line.toInt() + 1).coerceIn(1, doc.lines)
    val line = doc.line(LineNumber(lineNumber))
    val character = pos.character.toInt().coerceIn(0, line.length)
    return line.from.value + character
}

/** Build an LSP [Range] from two offsets in [doc]. */
internal fun toRange(from: Int, to: Int, doc: Text): Range =
    Range(start = toPosition(from, doc), end = toPosition(to, doc))

/**
 * How a document's changes should be synchronized to the server, as negotiated
 * through the server's [textDocumentSync][ServerCapabilities.textDocumentSync]
 * capability.
 *
 * Mirrors the semantics upstream `@codemirror/lsp-client` derives from the same
 * capability: whether open/close notifications are sent, and whether content is
 * synced incrementally, in full, or not at all.
 *
 * @param openClose Whether `textDocument/didOpen` and `textDocument/didClose`
 *   notifications should be sent.
 * @param change How content changes are synced ([TextDocumentSyncKind.NONE],
 *   [TextDocumentSyncKind.FULL], or [TextDocumentSyncKind.INCREMENTAL]).
 */
data class DocumentSyncMode(
    val openClose: Boolean,
    val change: TextDocumentSyncKind
) {
    /** True when content changes should be synchronized at all. */
    val syncsChanges: Boolean
        get() = change != TextDocumentSyncKind.NONE

    companion object {
        /**
         * Resolve the [DocumentSyncMode] from a server's [capabilities].
         *
         * The `textDocumentSync` capability is, per the LSP spec, either the
         * [TextDocumentSyncKind] number (legacy form) or a
         * [TextDocumentSyncOptions] object (`{openClose, change}`), modelled by
         * the [ServerCapabilitiesTextDocumentSync] union. When it is absent the
         * spec defaults to no syncing; we follow upstream and still advertise
         * open/close while defaulting [change] to [TextDocumentSyncKind.NONE].
         */
        fun forCapabilities(capabilities: ServerCapabilities?): DocumentSyncMode =
            when (val sync = capabilities?.textDocumentSync) {
                null -> DocumentSyncMode(openClose = true, change = TextDocumentSyncKind.NONE)
                is TextDocumentSyncKind ->
                    DocumentSyncMode(openClose = true, change = sync)
                is TextDocumentSyncOptions ->
                    DocumentSyncMode(
                        openClose = sync.openClose ?: false,
                        change = sync.change ?: TextDocumentSyncKind.NONE
                    )
            }
    }
}

/**
 * Build the list of [TextDocumentContentChangeEvent]s for a `textDocument/didChange`
 * notification, honoring the negotiated [mode].
 *
 * Mirrors upstream's incremental-vs-full decision: when the server supports
 * [incremental][TextDocumentSyncKind.INCREMENTAL] sync, each changed range from
 * [changes] is emitted as a ranged content change (computed against [prevDoc]);
 * otherwise the whole [newDoc] is sent as a single full-content change.
 *
 * Incremental changes are emitted highest-offset-first so that the
 * [prevDoc]-relative ranges of earlier (lower) edits are not invalidated by the
 * application of later (higher) edits, matching the sequential-application
 * semantics the LSP spec requires within a single notification.
 *
 * @param changes The document changes (in [prevDoc] coordinates).
 * @param prevDoc The document as the server last saw it.
 * @param newDoc The document after [changes] are applied.
 * @param mode The negotiated synchronization mode.
 */
internal fun buildContentChanges(
    changes: ChangeSet,
    prevDoc: Text,
    newDoc: Text,
    mode: DocumentSyncMode
): List<TextDocumentContentChangeEvent> {
    if (mode.change == TextDocumentSyncKind.INCREMENTAL) {
        val events = mutableListOf<TextDocumentContentChangeEventRange>()
        changes.iterChanges(
            { fromA, toA, _, _, inserted ->
                events.add(
                    TextDocumentContentChangeEventRange(
                        range = toRange(fromA.value, toA.value, prevDoc),
                        text = inserted.toString()
                    )
                )
            },
            individual = true
        )
        // Apply higher offsets first so earlier ranges remain valid.
        return events.asReversed().toList()
    }
    return listOf(TextDocumentContentChangeEventVariant(text = newDoc.toString()))
}
