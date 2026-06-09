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
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec

/**
 * Transaction annotation that tags a transaction where a completion was
 * accepted. Extensions can read this to react to completion events.
 */
val pickedCompletion: AnnotationType<Completion> = Annotation.define()

/** Effect to set the selected completion index. */
val setSelectedCompletion: StateEffectType<Int> = StateEffect.define()

/**
 * Effect to open the completion list with one or more source results. The
 * results are merged + deduped into a single ordered list (see
 * [mergeCompletions]); a single-source query passes a singleton list (#137).
 */
internal val startCompletionEffect: StateEffectType<List<CompletionResult>> = StateEffect.define()

/** Effect to close the completion list. */
internal val closeCompletionEffect: StateEffectType<Unit> = StateEffect.define()

/** Internal state tracking the completion list. */
internal data class CompletionState(
    val results: List<CompletionResult> = emptyList(),
    val filtered: List<FilterResult> = emptyList(),
    val selected: Int = 0,
    val open: Boolean = false
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
            var result = value

            for (effect in tr.effects) {
                val startEffect = effect.asType(startCompletionEffect)
                if (startEffect != null) {
                    val results = startEffect.value
                    val filtered = mergeCompletions(results, tr.state)
                    result = CompletionState(
                        results = results,
                        filtered = filtered,
                        selected = 0,
                        open = filtered.isNotEmpty()
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

            // Re-filter on doc change while open. Each active result is kept only
            // while its own validFor still matches the text between its from and
            // the cursor; results that fall out of validFor drop, and if none
            // survive the list closes. The survivors are re-merged + deduped.
            if (tr.docChanged && result.open && result.results.isNotEmpty()) {
                val head = tr.state.selection.main.head
                val surviving = result.results.filter { cr ->
                    val validFor = cr.validFor
                    val currentText = tr.state.doc.sliceString(cr.from, head)
                    validFor != null && validFor.containsMatchIn(currentText)
                }
                if (surviving.isEmpty()) {
                    result = CompletionState.empty
                } else {
                    val filtered = mergeCompletions(surviving, tr.state)
                    result = result.copy(
                        results = surviving,
                        filtered = filtered,
                        selected = result.selected.coerceIn(
                            0,
                            (filtered.size - 1).coerceAtLeast(0)
                        ),
                        open = filtered.isNotEmpty()
                    )
                }
            }

            result
        }
    )
)

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
