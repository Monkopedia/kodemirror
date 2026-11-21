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

import com.monkopedia.kodemirror.state.ChangeDesc
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.lsp.TextEdit

/** An LSP [TextEdit] resolved to `[from, to)` offsets in one document. */
internal data class OffsetEdit(val from: Int, val to: Int, val text: String)

internal fun List<TextEdit>.toOffsetEdits(doc: Text): List<OffsetEdit> = map { edit ->
    val a = fromPosition(edit.range.start, doc)
    val b = fromPosition(edit.range.end, doc)
    OffsetEdit(minOf(a, b), maxOf(a, b), edit.newText)
}

internal fun OffsetEdit.toChangeSpec(): ChangeSpec =
    ChangeSpec.Single(DocPos(from), DocPos(to), InsertContent.StringContent(text))

/** Whether [changes] touched [edit]'s range, inclusive at both ends as upstream's `touchesRange`. */
internal fun ChangeDesc.touches(edit: OffsetEdit): Boolean =
    touchesRange(DocPos(edit.from), DocPos(edit.to)).toBoolean()

/**
 * Move completion edits read against the document a request saw through [changes]
 * made since, as upstream's `applyEdits` does: an edit whose range was touched is
 * dropped, and the rest start at `mapPos(from, 1)` and keep their length. With no
 * [changes] the edits are returned as they are.
 */
internal fun List<OffsetEdit>.mapThrough(changes: ChangeDesc?): List<OffsetEdit> =
    if (changes == null) {
        this
    } else {
        mapNotNull { edit ->
            if (changes.touches(edit)) return@mapNotNull null
            val moved = changes.mapPos(DocPos(edit.from), 1).value
            edit.copy(from = moved, to = moved + (edit.to - edit.from))
        }
    }
