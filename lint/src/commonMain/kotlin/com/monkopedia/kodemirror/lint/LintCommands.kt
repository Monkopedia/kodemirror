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
package com.monkopedia.kodemirror.lint

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.RangeValue
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding

/** Open the lint diagnostics panel. */
val openLintPanel: (EditorSession) -> Boolean = { view ->
    view.dispatch(
        TransactionSpec(
            effects = listOf(openPanelEffect.of(true))
        )
    )
    true
}

/** Close the lint diagnostics panel. */
val closeLintPanel: (EditorSession) -> Boolean = { view ->
    val open = view.state.field(lintPanelOpen, require = false) ?: false
    if (open) {
        view.dispatch(
            TransactionSpec(
                effects = listOf(closePanelEffect.of(true))
            )
        )
        true
    } else {
        false
    }
}

/**
 * One piece of the decoration set upstream `LintState.init` builds, covering [diagnostics], with
 * the sides of the decoration upstream uses for it: a widget (`Side.InlineBefore`) for a
 * zero-width piece, otherwise a mark that is inclusive at its end when a diagnostic continues past
 * it. The sides decide which [RangeSet] layer a piece lands in, and so the order `between` visits
 * pieces in, which the commands below depend on exactly as upstream's do.
 */
private class LintPiece(val diagnostics: List<Diagnostic>, widget: Boolean, inclusiveEnd: Boolean) :
    RangeValue() {
    override val startSide: Int = if (widget) -100_000_000 else 500_000_000
    override val endSide: Int = if (widget) {
        -100_000_000
    } else if (inclusiveEnd) {
        1
    } else {
        -600_000_000
    }
    override val point: Boolean = widget

    fun carries(diagnostic: Diagnostic) = diagnostics.any { it === diagnostic }
}

/**
 * Split [diagnostics] into the non-overlapping pieces upstream navigates over, as
 * `LintState.init` does: sorted by `from` then `to`, split wherever the set of covering diagnostics
 * changes, cut off at the document end, and with a point or a short line-break-only range turned
 * into a zero-width piece at its start.
 */
private fun lintPieces(diagnostics: List<Diagnostic>, doc: Text): RangeSet<LintPiece> {
    val sorted = diagnostics.sortedWith(compareBy({ it.from }, { it.to }))
    val docEnd = DocPos(doc.length)
    val builder = RangeSetBuilder<LintPiece>()
    val active = mutableListOf<Diagnostic>()
    var pos = DocPos.ZERO
    var i = 0
    while (i < sorted.size || active.isNotEmpty()) {
        val next = sorted.getOrNull(i)
        val from: DocPos
        var to: DocPos
        if (active.isNotEmpty()) {
            from = pos
            to = active.minOf { it.to }
            if (next != null && next.from > from) to = minOf(to, next.from)
        } else {
            from = next!!.from
            if (from > docEnd) break
            to = next.to
            active += next
            i++
        }
        while (i < sorted.size) {
            val candidate = sorted[i]
            if (candidate.from == from && (candidate.to > candidate.from || candidate.to == from)) {
                active += candidate
                i++
                to = minOf(to, candidate.to)
            } else {
                to = minOf(to, candidate.from)
                break
            }
        }
        to = minOf(to, docEnd)
        val widget = active.any { it.from == from && (it.to == to || to == docEnd) } &&
            (
                from == to ||
                    (to.value - from.value < 10 && doc.sliceString(from, to).all { it == '\n' })
                )
        val piece = LintPiece(active.toList(), widget, inclusiveEnd = active.any { it.to > to })
        builder.add(from, if (widget) from else to, piece)
        pos = to
        if (pos == docEnd) break
        active.removeAll { it.to <= pos }
    }
    return builder.finish()
}

/**
 * Upstream `findDiagnostic`: the first piece reaching [after], extended over the pieces visited
 * after it while they carry the same diagnostic.
 */
private fun findDiagnostic(pieces: RangeSet<LintPiece>, after: DocPos): Pair<DocPos, DocPos>? {
    var found: Triple<Int, Int, Diagnostic>? = null
    pieces.between(after, DocPos(1_000_000_000)) { from, to, piece ->
        val current = found
        when {
            current == null -> {
                found = Triple(from, to, piece.diagnostics.first())
                null
            }
            !piece.carries(current.third) -> false
            else -> {
                found = current.copy(second = to)
                null
            }
        }
    }
    return found?.let { DocPos(it.first) to DocPos(it.second) }
}

/**
 * Select the next diagnostic after the selection, wrapping to the first one. Returns false when
 * there is none, or when wrapping would select the range that is already selected.
 */
val nextDiagnostic: (EditorSession) -> Boolean = { view ->
    val field = view.state.field(lintState, require = false)
    val sel = view.state.selection.main
    val pieces = lintPieces(field?.diagnostics.orEmpty(), view.state.doc)
    val next = findDiagnostic(pieces, sel.to + 1)
        ?: findDiagnostic(pieces, DocPos.ZERO)?.takeUnless {
            it.first == sel.from &&
                it.second == sel.to
        }
    if (next != null) selectRange(view, next.first, next.second)
    next != null
}

/**
 * Select the nearest diagnostic ending before the selection, wrapping to the last one. Returns
 * false when there is none, or when wrapping would land where the selection already starts.
 */
val previousDiagnostic: (EditorSession) -> Boolean = { view ->
    val state = view.state
    val sel = state.selection.main
    var prev: Pair<Int, Int>? = null
    var last: Pair<Int, Int>? = null
    val pieces =
        lintPieces(state.field(lintState, require = false)?.diagnostics.orEmpty(), state.doc)
    pieces.between(DocPos.ZERO, DocPos(state.doc.length)) { from, to, _ ->
        val p = prev
        val l = last
        if (to < sel.to.value && (p == null || p.first < from)) prev = from to to
        if (l == null || from > l.first) last = from to to
        null
    }
    val l = last
    val target = when {
        l == null -> null
        prev == null && l.first == sel.from.value -> null
        else -> prev ?: l
    }
    target?.let { (from, to) -> selectRange(view, DocPos(from), DocPos(to)) }
    target != null
}

private fun selectRange(view: EditorSession, from: DocPos, to: DocPos) {
    view.dispatch(
        TransactionSpec(
            selection = SelectionSpec.EditorSelectionSpec(EditorSelection.single(from, to)),
            scrollIntoView = true
        )
    )
}

/** Default lint keymap bindings. */
val lintKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Ctrl-Shift-m", run = { view ->
        val open = view.state.field(lintPanelOpen, require = false) ?: false
        if (open) closeLintPanel(view) else openLintPanel(view)
    }),
    KeyBinding(key = "F8", run = nextDiagnostic)
)
