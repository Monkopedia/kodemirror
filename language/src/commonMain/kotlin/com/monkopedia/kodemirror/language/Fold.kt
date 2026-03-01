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

import com.monkopedia.kodemirror.lezer.common.NodeProp
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.EditorView
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.ReplaceDecorationSpec
import com.monkopedia.kodemirror.view.WidgetType

/** A range that can be folded. */
data class FoldRange(val from: Int, val to: Int)

/**
 * Facet for registering fold range providers.
 *
 * A fold service receives the state and a line-start position,
 * and returns a [FoldRange] if that line can be folded.
 */
val foldService: Facet<(EditorState, Int) -> FoldRange?, List<(EditorState, Int) -> FoldRange?>> =
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
    return FoldRange(first.to, last.from)
}

/** Effect to fold a range. */
val foldEffect: StateEffectType<FoldRange> = StateEffect.define(
    map = { range, changes ->
        val from = changes.mapPos(range.from, 1)
        val to = changes.mapPos(range.to, -1)
        if (from < to) FoldRange(from, to) else null
    }
)

/** Effect to unfold a range. */
val unfoldEffect: StateEffectType<FoldRange> = StateEffect.define(
    map = { range, changes ->
        val from = changes.mapPos(range.from, 1)
        val to = changes.mapPos(range.to, -1)
        if (from < to) FoldRange(from, to) else null
    }
)

private class FoldWidget : WidgetType() {
    @androidx.compose.runtime.Composable
    override fun Content() {
        androidx.compose.foundation.text.BasicText(
            text = "\u2026",
            style = com.monkopedia.kodemirror.view.LocalEditorTheme.current.contentTextStyle.copy(
                color = androidx.compose.ui.graphics.Color.Gray
            )
        )
    }

    override fun eq(other: WidgetType): Boolean = other is FoldWidget
}

/**
 * State field that tracks the set of currently folded ranges.
 * Folded ranges are represented as [ReplaceDecoration]s.
 */
val foldState: StateField<DecorationSet> = StateField.define(
    StateFieldSpec(
        create = { RangeSet.empty() },
        update = { decos, tr ->
            var result = decos.map(tr.changes)
            for (effect in tr.effects) {
                val fold = effect.asType(foldEffect)
                if (fold != null) {
                    val range = fold.value
                    val widget = FoldWidget()
                    val deco = Decoration.replace(
                        ReplaceDecorationSpec(widget = widget)
                    )
                    val builder = RangeSetBuilder<Decoration>()
                    result.between(0, tr.newDoc.length) { from, to, value ->
                        builder.add(from, to, value)
                        true
                    }
                    builder.add(range.from, range.to, deco)
                    result = builder.finish()
                }
                val unfold = effect.asType(unfoldEffect)
                if (unfold != null) {
                    val range = unfold.value
                    val builder = RangeSetBuilder<Decoration>()
                    result.between(0, tr.newDoc.length) { from, to, value ->
                        // Keep all ranges except those overlapping with the unfold range
                        if (from < range.from || to > range.to) {
                            builder.add(from, to, value)
                        }
                        true
                    }
                    result = builder.finish()
                }
            }
            result
        }
    )
)

/**
 * Core code folding extension that wires the fold state.
 */
fun codeFolding(): Extension {
    return foldState
}

/**
 * Query the currently folded ranges in a state.
 */
fun foldedRanges(state: EditorState): DecorationSet {
    return state.field(foldState, require = false) ?: RangeSet.empty()
}

/**
 * Find a foldable range at the given line position, using registered
 * fold services and tree-based fold props.
 */
fun foldable(state: EditorState, lineStart: Int): FoldRange? {
    // Try fold services first
    for (service in state.facet(foldService)) {
        val range = service(state, lineStart)
        if (range != null) return range
    }

    // Try tree-based folding
    val tree = syntaxTree(state)
    val line = state.doc.lineAt(lineStart)
    val lineEnd = line.to
    if (tree.length < lineEnd) return null

    return syntaxFolding(tree, state, lineStart, lineEnd)
}

private fun syntaxFolding(
    tree: com.monkopedia.kodemirror.lezer.common.Tree,
    state: EditorState,
    lineStart: Int,
    lineEnd: Int
): FoldRange? {
    var onlyInner = false
    var cur: SyntaxNode? = tree.resolveInner(lineEnd, 1)
    while (cur != null) {
        if (cur.to <= lineEnd || cur.from > lineEnd) {
            cur = cur.parent
            continue
        }
        if (cur.from < lineStart) onlyInner = true
        val strategy = cur.type.prop(foldNodeProp)
        if (strategy != null &&
            (cur.to < tree.length - 50 || tree.length == state.doc.length || !onlyInner)
        ) {
            val range = strategy(cur, state)
            if (range != null &&
                range.from <= lineEnd &&
                range.from >= lineStart &&
                range.to > lineEnd
            ) {
                return range
            }
        }
        cur = cur.parent
    }
    return null
}

// --- Fold commands ---

/** Fold the code at the current cursor line. */
val foldCode: (EditorView) -> Boolean = { view ->
    val state = view.state
    val line = state.doc.lineAt(state.selection.main.head)
    val range = foldable(state, line.from)
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
}

/** Unfold the code at the current cursor position. */
val unfoldCode: (EditorView) -> Boolean = { view ->
    val state = view.state
    val pos = state.selection.main.head
    val folded = foldedRanges(state)
    var found = false
    folded.between(pos, pos) { from, to, _ ->
        view.dispatch(
            TransactionSpec(
                effects = listOf(unfoldEffect.of(FoldRange(from, to)))
            )
        )
        found = true
        false
    }
    found
}

/** Toggle fold at the current cursor line. */
val toggleFold: (EditorView) -> Boolean = { view ->
    val state = view.state
    val pos = state.selection.main.head
    val folded = foldedRanges(state)
    var isFolded = false
    folded.between(pos, pos) { _, _, _ ->
        isFolded = true
        false
    }
    if (isFolded) unfoldCode(view) else foldCode(view)
}

/** Fold all foldable ranges in the document. */
val foldAll: (EditorView) -> Boolean = { view ->
    val state = view.state
    val effects = mutableListOf<StateEffect<*>>()
    for (lineNum in 1..state.doc.lines) {
        val line = state.doc.line(lineNum)
        val range = foldable(state, line.from)
        if (range != null) {
            effects.add(foldEffect.of(range))
        }
    }
    if (effects.isNotEmpty()) {
        view.dispatch(TransactionSpec(effects = effects))
        true
    } else {
        false
    }
}

/** Unfold all folded ranges. */
val unfoldAll: (EditorView) -> Boolean = { view ->
    val state = view.state
    val folded = foldedRanges(state)
    val effects = mutableListOf<StateEffect<*>>()
    folded.between(0, state.doc.length) { from, to, _ ->
        effects.add(unfoldEffect.of(FoldRange(from, to)))
        true
    }
    if (effects.isNotEmpty()) {
        view.dispatch(TransactionSpec(effects = effects))
        true
    } else {
        false
    }
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
fun foldGutter(): Extension {
    return ExtensionList(
        listOf(
            codeFolding(),
            com.monkopedia.kodemirror.view.gutter(
                com.monkopedia.kodemirror.view.GutterConfig(
                    cssClass = "cm-foldGutter",
                    lineMarker = { view, lineFrom ->
                        val state = view.state
                        val folded = foldedRanges(state)
                        var hasFold = false
                        folded.between(lineFrom, lineFrom) { _, _, _ ->
                            hasFold = true
                            false
                        }
                        if (hasFold) {
                            FoldGutterMarker(folded = true)
                        } else {
                            val canFold = foldable(state, lineFrom) != null
                            if (canFold) FoldGutterMarker(folded = false) else null
                        }
                    },
                    lineMarkerChange = { update ->
                        update.docChanged || update.transactions.any { tr ->
                            tr.effects.any { it.`is`(foldEffect) || it.`is`(unfoldEffect) }
                        }
                    }
                )
            )
        )
    )
}

private class FoldGutterMarker(val folded: Boolean) :
    com.monkopedia.kodemirror.view.GutterMarker() {
    @androidx.compose.runtime.Composable
    override fun Content(theme: com.monkopedia.kodemirror.view.EditorTheme) {
        androidx.compose.foundation.text.BasicText(
            text = if (folded) "\u25B6" else "\u25BC",
            style = theme.contentTextStyle.copy(
                color = theme.gutterForeground
            )
        )
    }

    override fun eq(other: com.monkopedia.kodemirror.view.GutterMarker): Boolean =
        other is FoldGutterMarker && folded == other.folded
}
