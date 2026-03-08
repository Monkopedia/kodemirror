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

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Prec
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.RangeSetUpdate
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.DecorationSource
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.EditorTheme
import com.monkopedia.kodemirror.view.GutterConfig
import com.monkopedia.kodemirror.view.GutterMarker
import com.monkopedia.kodemirror.view.LineDecorationSpec
import com.monkopedia.kodemirror.view.MarkDecorationSpec
import com.monkopedia.kodemirror.view.PluginSpec
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ReplaceDecorationSpec
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.WidgetType
import com.monkopedia.kodemirror.view.decorations
import com.monkopedia.kodemirror.view.gutter
import kotlin.math.max
import kotlin.math.min

// -- Decoration constants --

private val changedLine = Decoration.line(LineDecorationSpec(cssClass = "cm-changedLine"))
val changedText: Decoration = Decoration.mark(
    MarkDecorationSpec(
        cssClass = "cm-changedText",
        style = SpanStyle(background = MergeColors.changedTextB)
    )
)
private val inserted = Decoration.mark(
    MarkDecorationSpec(
        cssClass = "cm-insertedLine",
        style = SpanStyle(background = MergeColors.changedLineB)
    )
)
private val deleted = Decoration.mark(
    MarkDecorationSpec(
        cssClass = "cm-deletedLine",
        style = SpanStyle(background = MergeColors.changedLineA)
    )
)

// -- Gutter marker --

private val changedLineGutterMarker = object : GutterMarker() {
    override val elementClass: String = "cm-changedLineGutter"

    @Composable
    override fun Content(theme: EditorTheme) {
        // Rendered via CSS class
    }
}

// -- Chunk decoration plugin --

private class ChunkDecoPlugin(view: EditorSession) : PluginValue, DecorationSource {
    override var decorations: DecorationSet = RangeSet.empty()
        private set
    var gutterDecos: RangeSet<GutterMarker> = RangeSet.empty()
        private set

    init {
        rebuild(view)
    }

    override fun update(update: ViewUpdate) {
        if (update.docChanged ||
            chunksChanged(update.startState, update.state) ||
            configChanged(update.startState, update.state)
        ) {
            rebuild(update.session)
        }
    }

    private fun rebuild(view: EditorSession) {
        val result = getChunkDeco(view)
        decorations = result.first
        gutterDecos = result.second
    }
}

val decorateChunks: ViewPlugin<PluginValue> = ViewPlugin.define(
    PluginSpec(
        create = { view -> ChunkDecoPlugin(view) },
        decorations = { v -> (v as? DecorationSource)?.decorations ?: RangeSet.empty() }
    )
)

val changeGutter: Extension = Prec.low(
    gutter(
        GutterConfig(
            cssClass = "cm-changeGutter",
            lineMarker = { _, _ ->
                // Simplified: would need plugin access for full implementation
                null
            }
        )
    )
)

// -- Helpers --

private fun chunksChanged(s1: EditorState, s2: EditorState): Boolean =
    s1.field(ChunkField, require = false) != s2.field(ChunkField, require = false)

private fun configChanged(s1: EditorState, s2: EditorState): Boolean =
    s1.facet(mergeConfig) != s2.facet(mergeConfig)

private fun buildChunkDeco(
    chunk: Chunk,
    doc: Text,
    isA: Boolean,
    highlight: Boolean,
    builder: RangeSetBuilder<Decoration>,
    gutterBuilder: RangeSetBuilder<GutterMarker>?
) {
    val from = if (isA) chunk.fromA else chunk.fromB
    val to = if (isA) chunk.toA else chunk.toB
    var changeI = 0

    if (from != to) {
        builder.add(from, from, changedLine)
        builder.add(from, to, if (isA) deleted else inserted)
        gutterBuilder?.add(from, from, changedLineGutterMarker)

        val iter = doc.iterRange(from, to - 1)
        var pos = from
        while (!iter.next().done) {
            if (iter.lineBreak) {
                pos++
                builder.add(pos, pos, changedLine)
                gutterBuilder?.add(pos, pos, changedLineGutterMarker)
                continue
            }
            val lineEnd = pos + iter.value.length
            if (highlight) {
                while (changeI < chunk.changes.size) {
                    val nextChange = chunk.changes[changeI]
                    val nextFrom =
                        from + if (isA) nextChange.fromA else nextChange.fromB
                    val nextTo =
                        from + if (isA) nextChange.toA else nextChange.toB
                    val chFrom = max(pos, nextFrom)
                    val chTo = min(lineEnd, nextTo)
                    if (chFrom < chTo) builder.add(chFrom, chTo, changedText)
                    if (nextTo < lineEnd) {
                        changeI++
                    } else {
                        break
                    }
                }
            }
            pos = lineEnd
        }
    }
}

private fun getChunkDeco(view: EditorSession): Pair<DecorationSet, RangeSet<GutterMarker>> {
    val chunks = view.state.field(ChunkField)
    val conf = view.state.facet(mergeConfig)
    val isA = conf.side == MergeSide.A
    val builder = RangeSetBuilder<Decoration>()
    val gutterBuilder =
        if (conf.markGutter) RangeSetBuilder<GutterMarker>() else null

    for (chunk in chunks) {
        val override = conf.overrideChunk
        if (override == null || !override(
                view.state,
                chunk,
                builder,
                gutterBuilder
            )
        ) {
            buildChunkDeco(
                chunk,
                view.state.doc,
                isA,
                conf.highlightChanges,
                builder,
                gutterBuilder
            )
        }
    }
    return Pair(builder.finish(), gutterBuilder?.finish() ?: RangeSet.empty())
}

// -- Spacer support --

val adjustSpacers: StateEffectType<DecorationSet> = StateEffect.define { v, m ->
    v.map(m)
}

val Spacers: StateField<DecorationSet> = StateField.define(
    StateFieldSpec(
        create = { RangeSet.empty<Decoration>() },
        update = { spacers, tr ->
            var result = spacers
            for (e in tr.effects) {
                e.asType(adjustSpacers)?.let { result = it.value }
            }
            if (result === spacers && tr.docChanged) {
                result.map(tr.changes)
            } else {
                result
            }
        },
        provide = { f ->
            decorations.from(f) { it }
        }
    )
)

// -- Collapse unchanged --

/**
 * A state effect that expands the section of collapsed unchanged
 * code starting at the given position.
 */
val uncollapseUnchanged: StateEffectType<Int> = StateEffect.define { v, m ->
    m.mapPos(v)
}

private class CollapseWidget(val lines: Int) : WidgetType() {
    override fun equals(other: Any?): Boolean = other is CollapseWidget && other.lines == lines

    override fun hashCode(): Int = lines

    @Composable
    override fun Content() {
        // Compose rendering placeholder — shows "$lines unchanged lines"
    }

    override val estimatedHeight: Int get() = 27
}

private val CollapsedRanges: StateField<DecorationSet> = StateField.define(
    StateFieldSpec(
        create = { RangeSet.empty<Decoration>() },
        update = { deco, tr ->
            var result = deco.map(tr.changes)
            for (e in tr.effects) {
                e.asType(uncollapseUnchanged)?.let { eff ->
                    result = result.update(
                        RangeSetUpdate(
                            filter = { from, _, _ -> from != eff.value }
                        )
                    )
                }
            }
            result
        },
        provide = { f ->
            decorations.from(f) { it }
        }
    )
)

/**
 * Collapse unchanged sections in a merge view.
 */
fun collapseUnchanged(margin: Int = 3, minSize: Int = 4): Extension {
    return CollapsedRanges.init { state ->
        buildCollapsedRanges(state, margin, minSize)
    }
}

private fun buildCollapsedRanges(state: EditorState, margin: Int, minLines: Int): DecorationSet {
    val builder = RangeSetBuilder<Decoration>()
    val isA = state.facet(mergeConfig).side == MergeSide.A
    val chunks = state.field(ChunkField)
    var prevLine = 1
    for (i in 0..chunks.size) {
        val chunk = if (i < chunks.size) chunks[i] else null
        val collapseFrom = if (i > 0) prevLine + margin else 1
        val collapseTo = if (chunk != null) {
            state.doc.lineAt(if (isA) chunk.fromA else chunk.fromB).number - 1 - margin
        } else {
            state.doc.lines
        }
        val lines = collapseTo - collapseFrom + 1
        if (lines >= minLines) {
            builder.add(
                state.doc.line(collapseFrom).from,
                state.doc.line(collapseTo).to,
                Decoration.replace(
                    ReplaceDecorationSpec(
                        widget = CollapseWidget(lines),
                        block = true
                    )
                )
            )
        }
        if (chunk == null) break
        prevLine = state.doc.lineAt(
            min(state.doc.length, if (isA) chunk.toA else chunk.toB)
        ).number
    }
    return builder.finish()
}
