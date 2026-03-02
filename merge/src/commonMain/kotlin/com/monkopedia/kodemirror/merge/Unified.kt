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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.ChangeSet
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.Prec
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.EditorView
import com.monkopedia.kodemirror.view.GutterConfig
import com.monkopedia.kodemirror.view.GutterMarker
import com.monkopedia.kodemirror.view.LineDecorationSpec
import com.monkopedia.kodemirror.view.WidgetDecorationSpec
import com.monkopedia.kodemirror.view.WidgetType
import com.monkopedia.kodemirror.view.decorations
import com.monkopedia.kodemirror.view.editorAttributes
import com.monkopedia.kodemirror.view.gutter
import kotlin.math.max
import kotlin.math.min

// -- Configuration --

/**
 * Configuration for a unified merge view.
 */
data class UnifiedMergeConfig(
    /** The other document to compare the editor content with. */
    val original: Text,
    /** Whether to mark inserted and deleted text in changed chunks. Defaults to true. */
    val highlightChanges: Boolean = true,
    /** Controls whether a gutter marker is shown next to changed lines. */
    val gutter: Boolean = true,
    /** Whether to syntax-highlight deleted lines. Defaults to true. */
    val syntaxHighlightDeletions: Boolean = true,
    /** When enabled, chunks with only inline changes display them inline. */
    val allowInlineDiffs: Boolean = false,
    /** Max size for syntax-highlighted deletions. Defaults to 3000. */
    val syntaxHighlightDeletionsMaxLength: Int = 3000,
    /** Whether to show accept/reject buttons. Defaults to true. */
    val mergeControls: Boolean = true,
    /** Options for the diff algorithm. */
    val diffConfig: DiffConfig = defaultDiffConfig,
    /** When given, long stretches of unchanged text are collapsed. */
    val collapseUnchanged: CollapseConfig? = null
)

/**
 * Configuration for collapsing unchanged text.
 */
data class CollapseConfig(
    /** Number of lines to leave visible after/before a change. */
    val margin: Int = 3,
    /** Minimum amount of collapsible lines. */
    val minSize: Int = 4
)

// -- Gutter marker for deleted chunks --

private val deletedChunkGutterMarker = object : GutterMarker() {
    override val elementClass: String = "cm-deletedLineGutter"

    @Composable
    override fun Content(theme: com.monkopedia.kodemirror.view.EditorTheme) {
    }
}

private val unifiedChangeGutter: Extension = Prec.low(
    gutter(
        GutterConfig(
            cssClass = "cm-changeGutter"
        )
    )
)

// -- Original document state field --

/**
 * Data class for original doc update effects.
 */
data class OriginalDocUpdate(val doc: Text, val changes: ChangeSet)

/**
 * The state effect used to signal changes in the original doc in a
 * unified merge view.
 */
val updateOriginalDoc: StateEffectType<OriginalDocUpdate> = StateEffect.define()

/**
 * Create an effect that updates the original document being compared against.
 */
fun originalDocChangeEffect(
    state: EditorState,
    changes: ChangeSet
): StateEffect<OriginalDocUpdate> {
    return updateOriginalDoc.of(
        OriginalDocUpdate(
            doc = changes.apply(getOriginalDoc(state)),
            changes = changes
        )
    )
}

private val originalDoc: StateField<Text> = StateField.define(
    StateFieldSpec(
        create = { Text.empty },
        update = { doc, tr ->
            var result = doc
            for (e in tr.effects) {
                e.asType(updateOriginalDoc)?.let { result = it.value.doc }
            }
            result
        }
    )
)

/**
 * Get the original document from a unified merge editor's state.
 */
fun getOriginalDoc(state: EditorState): Text {
    return state.field(originalDoc)
}

// -- Deletion widget --

private class DeletionWidget(
    private val chunk: Chunk,
    private val state: EditorState
) : WidgetType() {
    override fun eq(other: WidgetType): Boolean =
        other is DeletionWidget && other.chunk.changes === chunk.changes

    override val estimatedHeight: Int get() = 30

    @Composable
    override fun Content() {
        val origDoc = state.field(originalDoc)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MergeColors.deletedChunkBackground)
                .padding(4.dp)
        ) {
            if (chunk.fromA < chunk.toA) {
                val text = origDoc.sliceString(
                    chunk.fromA,
                    max(chunk.fromA, chunk.endA)
                )
                BasicText(text)
            }
        }
    }
}

// -- Inline diff support --

private class InlineDeletionWidget(private val text: String) : WidgetType() {
    override fun eq(other: WidgetType): Boolean =
        other is InlineDeletionWidget && other.text == text

    @Composable
    override fun Content() {
        BasicText(
            text = text,
            style = TextStyle(background = MergeColors.deletedText)
        )
    }
}

private val inlineChangedLineGutterMarker = object : GutterMarker() {
    override val elementClass: String = "cm-inlineChangedLineGutter"

    @Composable
    override fun Content(theme: com.monkopedia.kodemirror.view.EditorTheme) {
    }
}

private val inlineChangedLine = Decoration.line(
    LineDecorationSpec(cssClass = "cm-inlineChangedLine")
)

private fun chunkCanDisplayInline(state: EditorState, chunk: Chunk): List<Pair<Int, Decoration>>? {
    val a = state.field(originalDoc)
    val b = state.doc
    val linesA = a.lineAt(chunk.endA).number - a.lineAt(chunk.fromA).number + 1
    val linesB = b.lineAt(chunk.endB).number - b.lineAt(chunk.fromB).number + 1
    if (linesA != linesB || linesA >= 10) return null

    val deco = mutableListOf<Pair<Int, Decoration>>()
    var deleteCount = 0
    val bA = chunk.fromA
    val bB = chunk.fromB

    for (ch in chunk.changes) {
        if (ch.fromA < ch.toA) {
            deleteCount += ch.toA - ch.fromA
            val deleted = a.sliceString(bA + ch.fromA, bA + ch.toA)
            if (deleted.contains('\n')) return null
            deco.add(
                Pair(
                    bB + ch.fromB,
                    Decoration.widget(
                        WidgetDecorationSpec(
                            widget = InlineDeletionWidget(deleted),
                            side = -1
                        )
                    )
                )
            )
        }
        if (ch.fromB < ch.toB) {
            deco.add(Pair(bB + ch.fromB, changedText))
        }
    }

    return if (deleteCount < (chunk.endA - chunk.fromA - linesA * 2)) deco else null
}

private fun overrideChunkInline(
    state: EditorState,
    chunk: Chunk,
    builder: RangeSetBuilder<Decoration>,
    gutterBuilder: RangeSetBuilder<GutterMarker>?
): Boolean {
    val inline = chunkCanDisplayInline(state, chunk) ?: return false
    var i = 0
    var line = state.doc.lineAt(chunk.fromB)
    while (true) {
        gutterBuilder?.add(line.from, line.from, inlineChangedLineGutterMarker)
        builder.add(line.from, line.from, inlineChangedLine)
        while (i < inline.size && inline[i].first <= line.to) {
            val (pos, dec) = inline[i]
            builder.add(pos, pos, dec)
            i++
        }
        if (line.to >= chunk.endB) break
        line = state.doc.lineAt(line.to + 1)
    }
    return true
}

// -- Deleted chunks state field --

private fun buildDeletedChunks(state: EditorState): DecorationSet {
    val builder = RangeSetBuilder<Decoration>()
    for (ch in state.field(ChunkField)) {
        builder.add(
            ch.fromB,
            ch.fromB,
            Decoration.widget(
                WidgetDecorationSpec(
                    widget = DeletionWidget(ch, state),
                    block = true,
                    side = -1
                )
            )
        )
    }
    return builder.finish()
}

private val deletedChunks: StateField<DecorationSet> = StateField.define(
    StateFieldSpec(
        create = { state -> buildDeletedChunks(state) },
        update = { deco, tr ->
            if (tr.state.field(ChunkField, require = false) !=
                tr.startState.field(ChunkField, require = false)
            ) {
                buildDeletedChunks(tr.state)
            } else {
                deco
            }
        },
        provide = { f ->
            decorations.from(f) { it }
        }
    )
)

// -- Accept / Reject commands --

/**
 * In a unified merge view, accept the chunk under the given position
 * or the cursor. This chunk will no longer be highlighted unless it
 * is edited again.
 */
fun acceptChunk(view: EditorView, pos: Int? = null): Boolean {
    val state = view.state
    val at = pos ?: state.selection.main.head
    val chunk = state.field(ChunkField).find { it.fromB <= at && it.endB >= at }
        ?: return false
    val insert = state.sliceDoc(chunk.fromB, max(chunk.fromB, chunk.toB - 1))
    val orig = state.field(originalDoc)
    val insertStr = if (chunk.fromB != chunk.toB && chunk.toA <= orig.length) {
        insert + state.lineBreak
    } else {
        insert
    }
    val changes = ChangeSet.of(
        ChangeSpec.Single(
            chunk.fromA,
            min(orig.length, chunk.toA),
            InsertContent.StringContent(insertStr)
        ),
        orig.length
    )
    view.dispatch(
        TransactionSpec(
            effects = listOf(
                updateOriginalDoc.of(OriginalDocUpdate(changes.apply(orig), changes))
            ),
            userEvent = "accept"
        )
    )
    return true
}

/**
 * In a unified merge view, reject the chunk under the given position
 * or the cursor. Reverts that range to the content it has in the
 * original document.
 */
fun rejectChunk(view: EditorView, pos: Int? = null): Boolean {
    val state = view.state
    val at = pos ?: state.selection.main.head
    val chunk = state.field(ChunkField).find { it.fromB <= at && it.endB >= at }
        ?: return false
    val orig = state.field(originalDoc)
    val insert = orig.sliceString(chunk.fromA, max(chunk.fromA, chunk.toA - 1))
    val insertStr = if (chunk.fromA != chunk.toA && chunk.toB <= state.doc.length) {
        insert + state.lineBreak
    } else {
        insert
    }
    view.dispatch(
        TransactionSpec(
            changes = ChangeSpec.Single(
                chunk.fromB,
                min(state.doc.length, chunk.toB),
                InsertContent.StringContent(insertStr)
            ),
            userEvent = "revert"
        )
    )
    return true
}

// -- Main entry point --

/**
 * Create an extension that causes the editor to display changes
 * between its content and the given original document. Changed
 * chunks will be highlighted, with uneditable widgets displaying the
 * original text displayed above the new text.
 */
fun unifiedMergeView(config: UnifiedMergeConfig): Extension {
    val orig = config.original
    val diffConf = config.diffConfig

    val extensions = mutableListOf<Extension>(
        Prec.low(decorateChunks.asExtension()),
        deletedChunks,
        editorAttributes.of(mapOf("class" to "cm-merge-b")),
        computeChunks.of { chunks, tr ->
            var result = chunks
            for (e in tr.effects) {
                e.asType(updateOriginalDoc)?.let { eff ->
                    result = Chunk.updateA(
                        result,
                        eff.value.doc,
                        tr.startState.doc,
                        eff.value.changes,
                        diffConf
                    )
                }
            }
            if (tr.docChanged) {
                result = Chunk.updateB(
                    result,
                    tr.state.field(originalDoc),
                    tr.newDoc,
                    tr.changes,
                    diffConf
                )
            }
            result
        },
        mergeConfig.of(
            MergeConfigValue(
                highlightChanges = config.highlightChanges,
                markGutter = config.gutter,
                syntaxHighlightDeletions = config.syntaxHighlightDeletions,
                syntaxHighlightDeletionsMaxLength = config.syntaxHighlightDeletionsMaxLength,
                mergeControls = config.mergeControls,
                overrideChunk = if (config.allowInlineDiffs) ::overrideChunkInline else null,
                side = MergeSide.B
            )
        ),
        originalDoc.init { orig },
        ChunkField.init { state -> Chunk.build(orig, state.doc, diffConf) }
    )

    if (config.gutter) {
        extensions.add(unifiedChangeGutter)
    }
    config.collapseUnchanged?.let { collapse ->
        extensions.add(collapseUnchanged(collapse.margin, collapse.minSize))
    }

    return ExtensionList(extensions)
}
