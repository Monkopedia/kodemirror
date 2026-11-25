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
package com.monkopedia.kodemirror.language

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.lezer.common.NodeIterator
import com.monkopedia.kodemirror.lezer.common.NodeProp
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetUpdate
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.endPos
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.EditorTheme
import com.monkopedia.kodemirror.view.GutterConfig
import com.monkopedia.kodemirror.view.GutterMarker
import com.monkopedia.kodemirror.view.GutterType
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.LocalContentTextStyle
import com.monkopedia.kodemirror.view.LocalEditorTheme
import com.monkopedia.kodemirror.view.ReplaceDecorationSpec
import com.monkopedia.kodemirror.view.WidgetType
import com.monkopedia.kodemirror.view.decorations
import com.monkopedia.kodemirror.view.gutter

/** A range that can be folded. */
data class FoldRange(val from: DocPos, val to: DocPos)

/**
 * Facet for registering fold range providers.
 *
 * A fold service receives the state and a line-start position,
 * and returns a [FoldRange] if that line can be folded.
 */
val foldService:
    Facet<(EditorState, DocPos) -> FoldRange?, List<(EditorState, DocPos) -> FoldRange?>> =
    Facet.define()

/**
 * A node prop that attaches fold information to node types.
 *
 * The function receives a [SyntaxNode] and the state, and returns
 * a [FoldRange] or null.
 */
val foldNodeProp: NodeProp<(SyntaxNode, EditorState) -> FoldRange?> = NodeProp()

/**
 * Helper that creates a fold range covering the inside of a node
 * (excluding the first and last characters, typically brackets).
 */
fun foldInside(node: SyntaxNode): FoldRange? {
    val first = node.firstChild ?: return null
    val last = node.lastChild ?: return null
    if (first.to >= last.from) return null
    return FoldRange(DocPos(first.to), DocPos(if (last.type.isError) node.to else last.from))
}

/** Effect to fold a range. */
val foldEffect: StateEffectType<FoldRange> = StateEffect.define(
    map = { range, changes ->
        val from: DocPos = changes.mapPos(range.from, 1)
        val to: DocPos = changes.mapPos(range.to, -1)
        if (from < to) FoldRange(from, to) else null
    }
)

/** Effect to unfold a range. */
val unfoldEffect: StateEffectType<FoldRange> = StateEffect.define(
    map = { range, changes ->
        val from: DocPos = changes.mapPos(range.from, 1)
        val to: DocPos = changes.mapPos(range.to, -1)
        if (from < to) FoldRange(from, to) else null
    }
)

private class FoldWidget : WidgetType() {
    @Composable
    override fun Content() {
        val theme = LocalEditorTheme.current
        val contentStyle = LocalContentTextStyle.current
        val shape = RoundedCornerShape(2.dp)
        Box(
            modifier = Modifier
                .padding(horizontal = 1.dp)
                .background(theme.foldPlaceholderBackground, shape)
                .border(1.dp, theme.foldPlaceholderColor.copy(alpha = 0.3f), shape)
                .padding(horizontal = 1.dp)
        ) {
            BasicText(
                text = "\u2026",
                style = contentStyle.copy(
                    color = theme.foldPlaceholderColor
                )
            )
        }
    }

    override fun equals(other: Any?): Boolean = other is FoldWidget

    override fun hashCode(): Int = this::class.hashCode()
}

private val foldWidget: Decoration = Decoration.replace(
    ReplaceDecorationSpec(widget = FoldWidget())
)

/**
 * State field that tracks the set of currently folded ranges.
 * Folded ranges are represented as [ReplaceDecoration]s.
 *
 * Mirrors upstream: a fold that already exists is not added again, an unfold removes only
 * the exactly matching range, and folds touched by a deletion or by the new main selection
 * head are cleared.
 */
val foldState: StateField<DecorationSet> = StateField.define(
    StateFieldSpec(
        create = { RangeSet.empty() },
        update = { decos, tr ->
            var folded = decos
            if (tr.isUserEvent("delete")) {
                tr.changes.iterChangedRanges({ fromA, toA, _, _ ->
                    folded = clearTouchedFolds(folded, fromA.value, toA.value)
                })
            }
            folded = folded.map(tr.changes)
            for (effect in tr.effects) {
                val fold = effect.asType(foldEffect)?.value
                val unfold = effect.asType(unfoldEffect)?.value
                if (fold != null && !foldExists(folded, fold.from, fold.to)) {
                    folded = folded.update(
                        RangeSetUpdate(add = listOf(foldWidget.range(fold.from, fold.to)))
                    )
                } else if (unfold != null) {
                    folded = folded.update(
                        RangeSetUpdate(
                            filter = { from, to, _ ->
                                from != unfold.from.value || to != unfold.to.value
                            },
                            filterFrom = unfold.from.value,
                            filterTo = unfold.to.value
                        )
                    )
                }
            }
            // Clear folded ranges that cover the selection head
            val selection = tr.selection
            if (selection != null) {
                val head = selection.main.head.value
                folded = clearTouchedFolds(folded, head, head)
            }
            folded
        },
        provide = { field -> decorations.from(field) }
    )
)

private fun clearTouchedFolds(folded: DecorationSet, from: Int, to: Int): DecorationSet {
    var touched = false
    folded.between(DocPos(from), DocPos(to)) { a, b, _ ->
        if (a < to && b > from) touched = true
        null
    }
    if (!touched) return folded
    return folded.update(
        RangeSetUpdate(filterFrom = from, filterTo = to, filter = { a, b, _ ->
            a >= to || b <= from
        })
    )
}

private fun foldExists(folded: DecorationSet, from: DocPos, to: DocPos): Boolean {
    var found = false
    folded.between(from, from) { a, b, _ ->
        if (a == from.value && b == to.value) found = true
        null
    }
    return found
}

/** The first (lowest-starting) folded range touching [from]..[to], if any. */
private fun findFold(state: EditorState, from: DocPos, to: DocPos): FoldRange? {
    var found: FoldRange? = null
    state.field(foldState, require = false)?.between(from, to) { a, b, _ ->
        val prev = found
        if (prev == null || prev.from.value > a) found = FoldRange(DocPos(a), DocPos(b))
        null
    }
    return found
}

/** Add [codeFolding] to the state alongside [effects] when it isn't installed yet. */
private fun maybeEnable(state: EditorState, effects: List<StateEffect<*>>): List<StateEffect<*>> =
    if (state.field(foldState, require = false) != null) {
        effects
    } else {
        effects + StateEffect.appendConfig.of(codeFolding())
    }

/**
 * The line block at [pos]: the document line, widened to whole lines across any folded range
 * that touches it, since a fold merges the lines it spans into one visual block.
 */
private fun lineBlockAt(state: EditorState, pos: DocPos): FoldRange {
    var from = state.doc.lineAt(pos).from
    var to = state.doc.lineAt(pos).to
    var widened = true
    while (widened) {
        widened = false
        foldedRanges(state).between(from, to) { a, b, _ ->
            if (a < from.value) {
                from = state.doc.lineAt(DocPos(a)).from
                widened = true
            }
            if (b > to.value) {
                to = state.doc.lineAt(DocPos(b)).to
                widened = true
            }
            null
        }
    }
    return FoldRange(from, to)
}

/** The line blocks holding a selection head, each listed once. */
private fun selectedLines(state: EditorState): List<FoldRange> {
    val lines = mutableListOf<FoldRange>()
    for (range in state.selection.ranges) {
        val head = range.head
        if (lines.any { it.from <= head && it.to >= head }) continue
        lines.add(lineBlockAt(state, head))
    }
    return lines
}

/**
 * Core code folding extension that wires the fold state.
 */
fun codeFolding(): Extension = foldState

/**
 * Query the currently folded ranges in a state.
 */
fun foldedRanges(state: EditorState): DecorationSet =
    state.field(foldState, require = false) ?: RangeSet.empty()

/**
 * Find a foldable range for the line starting at [lineStart] and ending at [lineEnd],
 * using registered fold services and tree-based fold props.
 */
fun foldable(
    state: EditorState,
    lineStart: DocPos,
    lineEnd: DocPos = state.doc.lineAt(lineStart).to
): FoldRange? {
    for (service in state.facet(foldService)) {
        val range = service(state, lineStart)
        if (range != null) return range
    }
    return syntaxFolding(state, lineStart.value, lineEnd.value)
}

/**
 * Walk the nodes covering [end] from innermost to outermost, keeping the outermost foldable
 * range that starts on the line, and stopping at the first node starting before the line once
 * one is found.
 */
private fun syntaxFolding(state: EditorState, start: Int, end: Int): FoldRange? {
    val tree = syntaxTree(state)
    if (tree.length < end) return null
    var found: FoldRange? = null
    var iter: NodeIterator? = tree.resolveStack(end, 1)
    while (iter != null) {
        val cur = iter.node
        iter = iter.next
        if (cur.to <= end || cur.from > end) continue
        if (found != null && cur.from < start) break
        val prop = cur.type.prop(foldNodeProp)
        if (prop != null &&
            (cur.to < tree.length - 50 || tree.length == state.doc.length || !isUnfinished(cur))
        ) {
            val value = prop(cur, state)
            if (value != null &&
                value.from.value <= end &&
                value.from.value >= start &&
                value.to.value > end
            ) {
                found = value
            }
        }
    }
    return found
}

private fun isUnfinished(node: SyntaxNode): Boolean {
    val ch = node.lastChild ?: return false
    return ch.to == node.to && ch.type.isError
}

// --- Fold commands ---

/** Fold the lines that are selected, if possible. */
val foldCode: (EditorSession) -> Boolean = fold@{ view ->
    for (line in selectedLines(view.state)) {
        val range = foldable(view.state, line.from, line.to) ?: continue
        view.dispatch(
            TransactionSpec(effects = maybeEnable(view.state, listOf(foldEffect.of(range))))
        )
        return@fold true
    }
    false
}

/** Unfold folded ranges on selected lines. */
val unfoldCode: (EditorSession) -> Boolean = unfold@{ view ->
    if (view.state.field(foldState, require = false) == null) return@unfold false
    val effects = selectedLines(view.state).mapNotNull { line ->
        findFold(view.state, line.from, line.to)?.let { unfoldEffect.of(it) }
    }
    if (effects.isNotEmpty()) view.dispatch(TransactionSpec(effects = effects))
    effects.isNotEmpty()
}

/**
 * Find the foldable region containing the given line block, looking backwards through the
 * preceding lines for one that reaches into it.
 */
private fun foldableContainer(state: EditorState, lineBlock: FoldRange): FoldRange? {
    var line = lineBlock
    while (true) {
        val foldableRegion = foldable(state, line.from, line.to)
        if (foldableRegion != null && foldableRegion.to > lineBlock.from) return foldableRegion
        if (line.from == DocPos.ZERO) return null
        line = lineBlockAt(state, line.from - 1)
    }
}

/**
 * Toggle folding at cursors. Unfolds if there is an existing fold starting in that line, tries
 * to find a foldable range around it otherwise.
 */
val toggleFold: (EditorSession) -> Boolean = { view ->
    val effects = mutableListOf<StateEffect<*>>()
    for (line in selectedLines(view.state)) {
        val folded = findFold(view.state, line.from, line.to)
        if (folded != null) {
            effects.add(unfoldEffect.of(folded))
        } else {
            foldableContainer(view.state, line)?.let { effects.add(foldEffect.of(it)) }
        }
    }
    if (effects.isNotEmpty()) {
        view.dispatch(TransactionSpec(effects = maybeEnable(view.state, effects)))
    }
    effects.isNotEmpty()
}

/**
 * Fold all top-level foldable ranges. Ranges nested inside a range that gets folded are
 * skipped.
 */
val foldAll: (EditorSession) -> Boolean = { view ->
    val state = view.state
    val effects = mutableListOf<StateEffect<*>>()
    var pos = DocPos.ZERO
    while (pos < state.doc.endPos) {
        val line = lineBlockAt(state, pos)
        val range = foldable(state, line.from, line.to)
        if (range != null) effects.add(foldEffect.of(range))
        pos = (if (range != null) lineBlockAt(state, range.to) else line).to + 1
    }
    if (effects.isNotEmpty()) {
        view.dispatch(TransactionSpec(effects = maybeEnable(state, effects)))
    }
    effects.isNotEmpty()
}

/** Unfold all folded ranges. */
val unfoldAll: (EditorSession) -> Boolean = unfold@{ view ->
    val field = view.state.field(foldState, require = false)
    if (field == null || field.size == 0) return@unfold false
    val effects = mutableListOf<StateEffect<*>>()
    field.between(DocPos.ZERO, view.state.doc.endPos) { from, to, _ ->
        effects.add(unfoldEffect.of(FoldRange(DocPos(from), DocPos(to))))
        null
    }
    view.dispatch(TransactionSpec(effects = effects))
    true
}

/** Default fold key bindings. */
val foldKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Ctrl-Shift-[", mac = "Meta-Alt-[", run = foldCode),
    KeyBinding(key = "Ctrl-Shift-]", mac = "Meta-Alt-]", run = unfoldCode),
    KeyBinding(key = "Ctrl-Alt-[", run = foldAll),
    KeyBinding(key = "Ctrl-Alt-]", run = unfoldAll)
)

/**
 * Extension that adds a gutter column with fold indicators.
 *
 * Shows a clickable indicator (triangle) next to lines that can be
 * folded or unfolded.
 */
fun foldGutter(): Extension = extensionListOf(
    codeFolding(),
    gutter(
        GutterConfig(
            type = GutterType.Custom("fold"),
            lineMarker = { view, lineFrom ->
                val state = view.state
                val lineFromPos = DocPos(lineFrom)
                val line = state.doc.lineAt(lineFromPos)
                val folded = foldedRanges(state)
                var hasFold = false
                folded.between(lineFromPos, line.to) { from, _, _ ->
                    if (from >= lineFrom && DocPos(from) <= line.to) {
                        hasFold = true
                        false
                    } else {
                        true
                    }
                }
                if (hasFold) {
                    FoldGutterMarker(folded = true)
                } else {
                    val canFold = foldable(state, lineFromPos) != null
                    if (canFold) FoldGutterMarker(folded = false) else null
                }
            },
            lineMarkerChange = { update ->
                update.docChanged ||
                    update.transactions.any { tr ->
                        tr.effects.any {
                            it.asType(foldEffect) != null || it.asType(unfoldEffect) != null
                        }
                    }
            },
            lineMarkerClick = { view, lineFrom ->
                val state = view.state
                val lineFromPos = DocPos(lineFrom)
                val line = state.doc.lineAt(lineFromPos)
                val folded = foldedRanges(state)
                var wasFolded = false
                folded.between(lineFromPos, line.to) { from, to, _ ->
                    if (from >= lineFrom && DocPos(from) <= line.to) {
                        view.dispatch(
                            TransactionSpec(
                                effects = listOf(
                                    unfoldEffect.of(
                                        FoldRange(DocPos(from), DocPos(to))
                                    )
                                )
                            )
                        )
                        wasFolded = true
                        false
                    } else {
                        true
                    }
                }
                if (!wasFolded) {
                    val range = foldable(state, lineFromPos)
                    if (range != null) {
                        view.dispatch(
                            TransactionSpec(
                                effects = listOf(foldEffect.of(range))
                            )
                        )
                        true
                    } else {
                        false
                    }
                } else {
                    true
                }
            }
        )
    )
)

private class FoldGutterMarker(val folded: Boolean) : GutterMarker() {
    @Composable
    override fun Content(theme: EditorTheme) {
        val contentStyle = LocalContentTextStyle.current
        BasicText(
            text = if (folded) "\u203A" else "\u2304",
            style = contentStyle.copy(
                color = theme.gutterForeground
            )
        )
    }

    override fun equals(other: Any?): Boolean = other is FoldGutterMarker && folded == other.folded

    override fun hashCode(): Int = folded.hashCode()
}
