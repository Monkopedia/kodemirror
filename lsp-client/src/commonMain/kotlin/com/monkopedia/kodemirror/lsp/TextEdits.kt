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

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.asInsert
import com.monkopedia.lsp.TextEdit

/**
 * Convert a file's [edits] to a single multi-change [ChangeSpec] against [doc].
 *
 * Each edit's LSP range is resolved to `[from, to)` offsets. For the requesting
 * file a live [mapping] (the in-flight [WorkspaceMapping]) is applied so offsets
 * stay valid under concurrent edits, matching upstream's `mapping.mapPosition`;
 * other files (and callers with no in-flight request, e.g. document formatting)
 * resolve directly against the current document by passing `mapping = null`.
 *
 * The edits are emitted highest-offset-first (by resolved `from`) so that an
 * earlier edit's offsets are never invalidated by the application of a later one
 * — the same ordering discipline the incremental document sync uses (see
 * [buildContentChanges]). Returns null when there is nothing to change.
 *
 * Shared by [applyWorkspaceEdit] (rename) and [formatDocument] (whole-document
 * formatting): both consume an LSP `TextEdit[]` against a single document, so the
 * range→offset resolution and the highest-offset-first ordering live here once.
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
