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
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.lsp.TextEdit

/**
 * Convert a file's [edits] to a single multi-change [ChangeSpec].
 *
 * Each edit's LSP range is read against [doc], the document the server computed
 * the edits for. When a [mapping] of the edits made since is given, both ends are
 * then mapped through it once with assoc -1, matching upstream's
 * `mapping.mapPosition`. Without one, [doc] must be the current document.
 *
 * The edits are emitted highest-offset-first (by resolved `from`), the same
 * ordering discipline the incremental document sync uses (see
 * [buildContentChanges]). Returns null when there is nothing to change.
 *
 * Used by [applyWorkspaceEdit] (rename).
 */
internal fun textEditsToChangeSpec(
    edits: List<TextEdit>,
    doc: Text,
    mapping: WorkspaceMapping?
): ChangeSpec? {
    if (edits.isEmpty()) return null
    val resolved = edits.toOffsetEdits(doc).map { edit ->
        if (mapping == null) {
            edit
        } else {
            edit.copy(from = mapping.mapPos(edit.from, -1), to = mapping.mapPos(edit.to, -1))
        }
    }
    return ChangeSpec.Multi(resolved.sortedByDescending { it.from }.map { it.toChangeSpec() })
}
