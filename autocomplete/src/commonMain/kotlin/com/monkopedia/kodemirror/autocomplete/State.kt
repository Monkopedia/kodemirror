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
package com.monkopedia.kodemirror.autocomplete

import com.monkopedia.kodemirror.state.Annotation
import com.monkopedia.kodemirror.state.AnnotationType
import com.monkopedia.kodemirror.state.ChangeDesc
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.TouchesResult
import com.monkopedia.kodemirror.state.Transaction

/**
 * Transaction annotation that tags a transaction where a completion was
 * accepted. Extensions can read this to react to completion events.
 */
val pickedCompletion: AnnotationType<Completion> = Annotation.define()

/** Effect to set the selected completion index. */
val setSelectedCompletion: StateEffectType<Int> = StateEffect.define()

/**
 * Payload for [startCompletionEffect]: the freshly-queried [results] (merged +
 * deduped into a single ordered list by [mergeCompletions]; a single-source query
 * passes a singleton list, #137) plus whether the session was opened explicitly
 * (Ctrl-Space / [startCompletion]) rather than implicitly by typing. The
 * [explicit][CompletionState.explicit] flag governs the session lifecycle.
 */
internal data class StartCompletion(val results: List<CompletionResult>, val explicit: Boolean)

/**
 * Effect to open the completion list with one or more source results plus whether
 * the trigger was explicit. See [StartCompletion].
 */
internal val startCompletionEffect: StateEffectType<StartCompletion> = StateEffect.define()

/** Effect to close the completion list. */
internal val closeCompletionEffect: StateEffectType<Unit> = StateEffect.define()

/**
 * Internal state tracking the completion list.
 *
 * @param explicit Whether the active session was opened explicitly (Ctrl-Space /
 *   [startCompletion]) rather than implicitly by typing. An explicit session has a
 *   looser backspacing lifecycle: the user may delete back to (and including) the
 *   session's original [from] — an empty query re-shows all options and the session
 *   stays open — whereas an implicit session closes once the typed span is fully
 *   removed. Mirrors upstream `@codemirror/autocomplete`'s `ActiveResult`
 *   `explicit`/`limit` distinction (`limit = from + (explicit ? 0 : 1)`).
 */
internal data class CompletionState(
    val results: List<CompletionResult> = emptyList(),
    val filtered: List<FilterResult> = emptyList(),
    val selected: Int = 0,
    val open: Boolean = false,
    val explicit: Boolean = false
) {
    /** The completion span start — the smallest `from` across active results. */
    val from: DocPos? get() = results.minByOrNull { it.from.value }?.from

    companion object {
        val empty = CompletionState()
    }
}

/** State field that tracks the active completion state. */
internal val completionStateField: StateField<CompletionState> = StateField.define(
    StateFieldSpec(
        create = { CompletionState.empty },
        update = { value, tr ->
            // The open session's lifecycle comes first, as upstream's
            // `ActiveSource.update` resets before it applies this transaction's
            // effects (so a start effect in the same transaction still opens).
            var result = if (value.open && value.results.isNotEmpty()) {
                updateOpenCompletion(value, tr)
            } else {
                value
            }

            for (effect in tr.effects) {
                val startEffect = effect.asType(startCompletionEffect)
                if (startEffect != null) {
                    val results = startEffect.value.results
                    val filtered = mergeCompletions(results, tr.state)
                    result = CompletionState(
                        results = results,
                        filtered = filtered,
                        selected = 0,
                        open = filtered.isNotEmpty(),
                        explicit = startEffect.value.explicit
                    )
                }
                if (effect.asType(closeCompletionEffect) != null) {
                    result = CompletionState.empty
                }
                val selectEffect = effect.asType(setSelectedCompletion)
                if (selectEffect != null) {
                    result = result.copy(
                        selected = selectEffect.value.coerceIn(
                            0,
                            (result.filtered.size - 1).coerceAtLeast(0)
                        )
                    )
                }
            }
            result
        }
    )
)

/** [result] with its range mapped through [changes]; `to` sticks to typing at its end. */
private fun CompletionResult.mapped(changes: ChangeDesc): CompletionResult =
    copy(from = changes.mapPos(from), to = to?.let { changes.mapPos(it, 1) })

/**
 * Carry an open completion [value] through [tr], following upstream
 * `@codemirror/autocomplete`'s `getUpdateType` / `ActiveResult.updateFor`:
 *   * Typing (`input.type`) or backspacing (`delete.backward`) maps each result's
 *     range and re-filters it (see below).
 *   * Any other transaction that sets the selection — a cursor move, an applied
 *     completion, a paste — closes the completion.
 *   * Any other document change closes it if it touches a result's range, and
 *     otherwise maps the range.
 *
 * Re-filtering applies the explicit-session lifecycle:
 *   * Backspacing before the session's original `from` closes it. An EXPLICIT
 *     session may backspace back to `from` itself (empty query, re-shows
 *     everything) and stays open; an IMPLICIT session closes once the typed span is
 *     fully removed — upstream's `limit = from + (explicit ? 0 : 1)`.
 *   * Otherwise each result is kept only while the cursor is not past its explicit
 *     `to` and its validFor still FULLY matches (anchored, like upstream
 *     `checkValid`) the text between its `from` and `to` (the cursor when unset);
 *     results that fall out of validFor (e.g. a typed space) drop, and if none
 *     survive the list closes. Survivors are re-merged + deduped, and the selection
 *     resets to 0 (upstream builds a fresh `ActiveResult`).
 */
private fun updateOpenCompletion(value: CompletionState, tr: Transaction): CompletionState {
    val changes = tr.changes
    val startHead = tr.startState.selection.main.head
    val typing = tr.isUserEvent("input.type")
    val backspacing = tr.isUserEvent("delete.backward")
    if (!typing && !backspacing) {
        return when {
            tr.selection != null -> CompletionState.empty
            !tr.docChanged -> value
            value.results.any {
                changes.touchesRange(it.from, it.to ?: startHead) != TouchesResult.No
            } -> CompletionState.empty
            else -> {
                // Untouched, so every query is unchanged: re-merging only re-points
                // the options at the mapped results that accepting reads.
                val results = value.results.map { it.mapped(changes) }
                value.copy(results = results, filtered = mergeCompletions(results, tr.state))
            }
        }
    }
    if (!tr.docChanged) return value

    val head = tr.state.selection.main.head
    val oldFrom = value.results.minOf { it.from }
    val results = value.results.map { it.mapped(changes) }
    val sessionFrom = results.minOf { it.from }
    val limit = if (value.explicit) sessionFrom else sessionFrom + 1
    if (head < sessionFrom || (backspacing && (startHead == oldFrom || head < limit))) {
        return CompletionState.empty
    }
    val surviving = results.filter { cr ->
        val validFor = cr.validFor
        val to = cr.to ?: head
        head >= cr.from &&
            head <= to &&
            validFor != null &&
            validFor.matches(tr.state.doc.sliceString(cr.from, to))
    }
    if (surviving.isEmpty()) return CompletionState.empty
    val filtered = mergeCompletions(surviving, tr.state)
    return value.copy(
        results = surviving,
        filtered = filtered,
        selected = 0,
        open = filtered.isNotEmpty()
    )
}

/** Get the completion status: "active" if completions are shown, null otherwise. */
fun completionStatus(state: EditorState): String? {
    val cs = state.field(completionStateField, require = false) ?: return null
    return if (cs.open) "active" else null
}

/** Get the current list of filtered completions. */
fun currentCompletions(state: EditorState): List<Completion> {
    val cs = state.field(completionStateField, require = false) ?: return emptyList()
    return cs.filtered.map { it.completion }
}

/** Get the currently selected completion. */
fun selectedCompletion(state: EditorState): Completion? {
    val cs = state.field(completionStateField, require = false) ?: return null
    if (!cs.open || cs.filtered.isEmpty()) return null
    return cs.filtered[cs.selected].completion
}

/** Get the index of the currently selected completion. */
fun selectedCompletionIndex(state: EditorState): Int {
    val cs = state.field(completionStateField, require = false) ?: return -1
    return if (cs.open) cs.selected else -1
}
