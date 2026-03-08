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
package com.monkopedia.kodemirror.merge

import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.StateCommand
import com.monkopedia.kodemirror.state.StateCommandTarget
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.GutterMarker

/**
 * Which side of a merge this editor represents.
 */
enum class MergeSide { A, B }

/**
 * Internal merge configuration.
 */
data class MergeConfigValue(
    val sibling: (() -> EditorSession)? = null,
    val highlightChanges: Boolean = true,
    val markGutter: Boolean = true,
    val syntaxHighlightDeletions: Boolean = true,
    val syntaxHighlightDeletionsMaxLength: Int = 3000,
    val mergeControls: Boolean = true,
    val overrideChunk: (
        (
            EditorState,
            Chunk,
            RangeSetBuilder<Decoration>,
            RangeSetBuilder<GutterMarker>?
        ) -> Boolean
    )? = null,
    val side: MergeSide = MergeSide.B
)

val mergeConfig: Facet<MergeConfigValue, MergeConfigValue> = Facet.define(
    combine = { values -> values.firstOrNull() ?: MergeConfigValue() }
)

val setChunks: StateEffectType<List<Chunk>> = StateEffect.define()

val computeChunks: Facet<
    (List<Chunk>, Transaction) -> List<Chunk>,
    List<(List<Chunk>, Transaction) -> List<Chunk>>
    > = Facet.define()

val ChunkField: StateField<List<Chunk>> = StateField.define(
    StateFieldSpec(
        create = { emptyList() },
        update = { current, tr ->
            var result = current
            for (e in tr.effects) {
                e.asType(setChunks)?.let { result = it.value }
            }
            for (comp in tr.state.facet(computeChunks)) {
                result = comp(result, tr)
            }
            result
        }
    )
)

/**
 * Result of [getChunks].
 */
data class ChunkResult(
    val chunks: List<Chunk>,
    val side: MergeSide?
)

/**
 * Get the changed chunks for the merge view that this editor is part
 * of, plus the side it is on if it is part of a MergeView.
 */
fun getChunks(state: EditorState): ChunkResult? {
    val field = state.field(ChunkField, require = false) ?: return null
    val conf = state.facet(mergeConfig)
    return ChunkResult(field, conf.side)
}

private fun moveByChunk(dir: Int): StateCommand = { target: StateCommandTarget ->
    val state = target.state
    val chunks = state.field(ChunkField, require = false)
    val conf = state.facet(mergeConfig)
    if (chunks == null || chunks.isEmpty()) {
        false
    } else {
        val head = state.selection.main.head
        var pos = 0
        for (i in chunks.indices.reversed()) {
            val chunk = chunks[i]
            val from = if (conf.side == MergeSide.B) chunk.fromB else chunk.fromA
            val to = if (conf.side == MergeSide.B) chunk.toB else chunk.toA
            if (to < head) {
                pos = i + 1
                break
            }
            if (from <= head) {
                if (chunks.size == 1) {
                    pos = -1
                    break
                }
                pos = i + if (dir < 0) 0 else 1
                break
            }
        }
        if (pos == -1) {
            false
        } else {
            val nextIdx =
                (pos + if (dir < 0) chunks.size - 1 else 0) % chunks.size
            val next = chunks[nextIdx]
            val from =
                if (conf.side == MergeSide.B) next.fromB else next.fromA
            val to =
                if (conf.side == MergeSide.B) next.toB else next.toA
            target.dispatch(
                state.update(
                    TransactionSpec(
                        selection = SelectionSpec.CursorSpec(from),
                        scrollIntoView = true,
                        userEvent = "select.byChunk"
                    )
                )
            )
            true
        }
    }
}

/**
 * Move the selection to the next changed chunk.
 */
val goToNextChunk: StateCommand = moveByChunk(1)

/**
 * Move the selection to the previous changed chunk.
 */
val goToPreviousChunk: StateCommand = moveByChunk(-1)
