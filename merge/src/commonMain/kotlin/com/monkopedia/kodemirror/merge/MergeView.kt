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

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.Prec
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.endPos
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.editorAttributes

/**
 * Orientation of the two editors.
 */
enum class Orientation { A_B, B_A }

/**
 * Direction for revert controls.
 */
enum class RevertDirection { A_TO_B, B_TO_A }

/**
 * Configuration for a side-by-side merge view.
 */
data class MergeViewConfig(
    /** Configuration for the first editor (A). */
    val a: EditorStateConfig,
    /** Configuration for the second editor (B). */
    val b: EditorStateConfig,
    /** Controls whether editor A or editor B is shown first. */
    val orientation: Orientation = Orientation.A_B,
    /** Controls whether revert controls are shown between changed chunks. */
    val revertControls: RevertDirection? = null,
    /** Whether to mark inserted and deleted text in changed chunks. */
    val highlightChanges: Boolean = true,
    /** Controls whether a gutter marker is shown next to changed lines. */
    val gutter: Boolean = true,
    /** When given, long stretches of unchanged text are collapsed. */
    val collapseUnchanged: CollapseConfig? = null,
    /** Options for the diff algorithm. */
    val diffConfig: DiffConfig = defaultDiffConfig
)

/**
 * A merge view manages two editors side-by-side, highlighting the
 * difference between them.
 */
class MergeView(config: MergeViewConfig) {
    /** The first editor view. */
    var a: EditorSession
        private set

    /** The second editor view. */
    var b: EditorSession
        private set

    /** The current set of changed chunks. */
    var chunks: List<Chunk>
        private set

    private val diffConf: DiffConfig = config.diffConfig
    private val revertDirection: RevertDirection? = config.revertControls

    init {
        val sharedExtensions = mutableListOf<Extension>(
            Prec.low(decorateChunks.asExtension()),
            Spacers
        )

        val configA = mutableListOf<Extension>(
            mergeConfig.of(
                MergeConfigValue(
                    side = MergeSide.A,
                    sibling = { b },
                    highlightChanges = config.highlightChanges,
                    markGutter = config.gutter
                )
            )
        )
        if (config.gutter) configA.add(changeGutter)

        val stateA = EditorState.create(
            EditorStateConfig(
                doc = config.a.doc,
                selection = config.a.selection,
                extensions = ExtensionList(
                    listOfNotNull(
                        config.a.extensions,
                        editorAttributes.of(mapOf("class" to "cm-merge-a")),
                        ExtensionList(configA),
                        ExtensionList(sharedExtensions)
                    )
                )
            )
        )

        val configB = mutableListOf<Extension>(
            mergeConfig.of(
                MergeConfigValue(
                    side = MergeSide.B,
                    sibling = { a },
                    highlightChanges = config.highlightChanges,
                    markGutter = config.gutter
                )
            )
        )
        if (config.gutter) configB.add(changeGutter)

        val stateB = EditorState.create(
            EditorStateConfig(
                doc = config.b.doc,
                selection = config.b.selection,
                extensions = ExtensionList(
                    listOfNotNull(
                        config.b.extensions,
                        editorAttributes.of(mapOf("class" to "cm-merge-b")),
                        ExtensionList(configB),
                        ExtensionList(sharedExtensions)
                    )
                )
            )
        )

        chunks = Chunk.build(stateA.doc, stateB.doc, diffConf)

        val add = ExtensionList(
            listOfNotNull(
                ChunkField.init { chunks },
                config.collapseUnchanged?.let {
                    collapseUnchanged(it.margin, it.minSize)
                }
            )
        )

        val finalStateA = stateA.update(
            TransactionSpec(
                effects = listOf(StateEffect.appendConfig.of(add))
            )
        ).state
        val finalStateB = stateB.update(
            TransactionSpec(
                effects = listOf(StateEffect.appendConfig.of(add))
            )
        ).state

        a = EditorSession(finalStateA)
        b = EditorSession(finalStateB)
    }

    /**
     * Dispatch a transaction to one of the editors, updating chunks
     * if the document changed.
     */
    fun dispatch(tr: Transaction, target: EditorSession) {
        if (tr.docChanged) {
            val changes = tr.changes
            chunks = if (target === a) {
                Chunk.updateA(chunks, tr.newDoc, b.state.doc, changes, diffConf)
            } else {
                Chunk.updateB(chunks, a.state.doc, tr.newDoc, changes, diffConf)
            }
            target.dispatchTransaction(tr)
            target.dispatch(
                TransactionSpec(
                    effects = listOf(setChunks.of(chunks))
                )
            )
            val other = if (target === a) b else a
            other.dispatch(
                TransactionSpec(
                    effects = listOf(setChunks.of(chunks))
                )
            )
        } else {
            target.dispatchTransaction(tr)
        }
    }

    /**
     * Revert a specific chunk, copying content from source to destination.
     */
    fun revertChunk(chunk: Chunk) {
        val toA = revertDirection == RevertDirection.B_TO_A
        val source = if (toA) b else a
        val dest = if (toA) a else b
        val srcFrom = if (toA) chunk.fromB else chunk.fromA
        val srcTo = if (toA) chunk.toB else chunk.toA
        val destFrom = if (toA) chunk.fromA else chunk.fromB
        val destTo = if (toA) chunk.toA else chunk.toB

        var insert = source.state.sliceDoc(srcFrom, maxOf(srcFrom, srcTo - 1))
        if (srcFrom != srcTo && destTo <= dest.state.doc.endPos) {
            insert += source.state.lineBreak
        }

        dest.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    destFrom,
                    minOf(dest.state.doc.endPos, destTo),
                    InsertContent.StringContent(insert)
                ),
                userEvent = "revert"
            )
        )
    }

    /**
     * Clean up both editor views.
     */
    fun dispose() {
        // Editor views are managed by Compose lifecycle
    }
}
