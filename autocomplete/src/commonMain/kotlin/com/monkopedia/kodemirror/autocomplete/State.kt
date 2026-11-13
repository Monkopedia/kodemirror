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
 * passes a singleton list, #137), whether the session was opened explicitly
 * (Ctrl-Space / [startCompletion]) rather than implicitly by typing, and the cursor
 * position [pos] the query ran at. The [explicit][CompletionState.explicit] flag
 * and [pos] set each result's backspace [limit][ActiveResult.limit].
 */
internal data class StartCompletion(
    val results: List<CompletionResult>,
    val explicit: Boolean,
    val pos: DocPos
)

/**
 * Effect to open the completion list with one or more source results plus whether
 * the trigger was explicit. See [StartCompletion].
 */
internal val startCompletionEffect: StateEffectType<StartCompletion> = StateEffect.define()

/** Effect to close the completion list. */
internal val closeCompletionEffect: StateEffectType<Unit> = StateEffect.define()

/**
 * One source's [result] in the open session, with upstream `ActiveResult.limit`:
 * backspacing the cursor below [limit] closes it. It is set when the result arrives
 * to `min(pos, from + (explicit ? 0 : 1))`, where `pos` is the query's cursor, and
 * mapped through every later change, so an implicit result opened at the cursor
 * (right after a `.`) may be backspaced back to its `from`.
 */
internal data class ActiveResult(val result: CompletionResult, val limit: DocPos)

/**
 * Internal state tracking the completion list.
 *
 * @param explicit Whether the active session was opened explicitly (Ctrl-Space /
 *   [startCompletion]) rather than implicitly by typing. It sets each result's
 *   backspace [limit][ActiveResult.limit], and is kept for a re-query.
 * @param requery Set, with the list closed, by a transaction after which an open
 *   result no longer applies but its source should be asked again (upstream's
 *   `Pending`): typing or backspacing left text its `validFor` rejects, or it has
 *   none. The completion plugin then re-runs the query with [explicit]. Cleared
 *   by the next transaction.
 */
internal data class CompletionState(
    val active: List<ActiveResult> = emptyList(),
    val filtered: List<FilterResult> = emptyList(),
    val selected: Int = 0,
    val open: Boolean = false,
    val explicit: Boolean = false,
    val requery: Boolean = false
) {
    val results: List<CompletionResult> get() = active.map { it.result }

    /** The completion span start — the smallest `from` across active results. */
    val from: DocPos? get() = active.minOfOrNull { it.result.from }

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
            var result = when {
                value.open && value.active.isNotEmpty() -> updateOpenCompletion(value, tr)
                value.requery -> CompletionState.empty
                else -> value
            }

            for (effect in tr.effects) {
                val startEffect = effect.asType(startCompletionEffect)
                if (startEffect != null) {
                    val start = startEffect.value
                    val slack = if (start.explicit) 0 else 1
                    val filtered = mergeCompletions(start.results, tr.state)
                    result = CompletionState(
                        active = start.results.map {
                            ActiveResult(it, minOf(start.pos, it.from + slack))
                        },
                        filtered = filtered,
                        selected = 0,
                        open = filtered.isNotEmpty(),
                        explicit = start.explicit
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
 *     range and re-checks it (see below).
 *   * Any other transaction that sets the selection — a cursor move, an applied
 *     completion, a paste — closes the completion.
 *   * Any other document change closes it if it touches a result's range, and
 *     otherwise maps the range.
 *
 * On typing or backspacing, each result is checked on its own:
 *   * It drops when the cursor is past its explicit `to` (or before its `from`), or
 *     on a backspace that started at its original `from` or left the cursor below
 *     its [limit][ActiveResult.limit].
 *   * Otherwise it stays while its validFor FULLY matches (anchored, like upstream
 *     `checkValid`) the text between its `from` and `to` (the cursor when unset).
 *     When it doesn't, or there is no validFor, upstream re-queries the source; if
 *     no result survives, the state asks for that [re-query][CompletionState.requery].
 * Survivors are re-merged + deduped, keeping the selected option selected when it
 * is still listed (upstream `CompletionDialog.build`).
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
                val active = value.active.map {
                    ActiveResult(it.result.mapped(changes), changes.mapPos(it.limit))
                }
                value.copy(
                    active = active,
                    filtered = mergeCompletions(active.map { it.result }, tr.state)
                )
            }
        }
    }

    val head = tr.state.selection.main.head
    var requery = false
    val surviving = value.active.mapNotNull { (old, limit) ->
        val cr = old.mapped(changes)
        val to = cr.to ?: head
        if (head < cr.from ||
            head > to ||
            (backspacing && (startHead == old.from || head < limit))
        ) {
            return@mapNotNull null
        }
        val validFor = cr.validFor
        if (validFor == null || !validFor.matches(tr.state.doc.sliceString(cr.from, to))) {
            requery = true
            return@mapNotNull null
        }
        ActiveResult(cr, changes.mapPos(limit))
    }
    if (surviving.isEmpty()) {
        return if (requery) {
            CompletionState(explicit = value.explicit, requery = true)
        } else {
            CompletionState.empty
        }
    }
    val filtered = mergeCompletions(surviving.map { it.result }, tr.state)
    // Upstream moves a selection other than the first option along with its option.
    val selected = if (value.selected == 0) {
        0
    } else {
        val option = value.filtered.getOrNull(value.selected)?.completion
        filtered.indexOfFirst { it.completion === option }.coerceAtLeast(0)
    }
    return value.copy(
        active = surviving,
        filtered = filtered,
        selected = selected,
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
