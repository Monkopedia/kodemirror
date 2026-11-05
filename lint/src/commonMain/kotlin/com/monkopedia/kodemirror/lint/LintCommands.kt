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

/** One piece of the decoration set upstream `LintState.init` builds, covering [diagnostics]. */
private class LintSegment(val from: DocPos, val to: DocPos, val diagnostics: List<Diagnostic>)

/**
 * Split [diagnostics] into the non-overlapping pieces upstream navigates over, as
 * `LintState.init` does: sorted by `from` then `to`, split wherever the set of covering diagnostics
 * changes, cut off at the document end, and with a point or a short line-break-only range turned
 * into a zero-width piece at its start.
 */
private fun lintSegments(diagnostics: List<Diagnostic>, doc: Text): List<LintSegment> {
    val sorted = diagnostics.sortedWith(compareBy({ it.from }, { it.to }))
    val docEnd = DocPos(doc.length)
    val segments = mutableListOf<LintSegment>()
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
        segments += LintSegment(from, if (widget) from else to, active.toList())
        pos = to
        if (pos == docEnd) break
        active.removeAll { it.to <= pos }
    }
    return segments
}

/**
 * Upstream `findDiagnostic`: the first piece reaching [after], extended over the pieces right after
 * it that carry the same diagnostic.
 */
private fun findDiagnostic(segments: List<LintSegment>, after: DocPos): Pair<DocPos, DocPos>? {
    val start = segments.indexOfFirst { it.to >= after }
    if (start < 0) return null
    val diagnostic = segments[start].diagnostics.first()
    var to = segments[start].to
    for (segment in segments.subList(start + 1, segments.size)) {
        if (diagnostic !in segment.diagnostics) break
        to = segment.to
    }
    return segments[start].from to to
}

/**
 * Select the next diagnostic after the selection, wrapping to the first one. Returns false when
 * there is none, or when wrapping would select the range that is already selected.
 */
val nextDiagnostic: (EditorSession) -> Boolean = { view ->
    val field = view.state.field(lintState, require = false)
    val sel = view.state.selection.main
    val segments = lintSegments(field?.diagnostics.orEmpty(), view.state.doc)
    val next = findDiagnostic(segments, sel.to + 1)
        ?: findDiagnostic(segments, DocPos.ZERO)?.takeUnless {
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
    val field = view.state.field(lintState, require = false)
    val sel = view.state.selection.main
    var prev: LintSegment? = null
    var last: LintSegment? = null
    for (segment in lintSegments(field?.diagnostics.orEmpty(), view.state.doc)) {
        if (segment.to < sel.to && (prev == null || prev.from < segment.from)) prev = segment
        if (last == null || segment.from > last.from) last = segment
    }
    val target = when {
        last == null -> null
        prev == null && last.from == sel.from -> null
        else -> prev ?: last
    }
    if (target != null) selectRange(view, target.from, target.to)
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
