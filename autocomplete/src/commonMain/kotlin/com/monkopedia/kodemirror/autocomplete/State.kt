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

import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec

/** Effect to set the selected completion index. */
val setSelectedCompletion: StateEffectType<Int> = StateEffect.define()

/** Effect to open the completion list with results. */
internal val startCompletionEffect: StateEffectType<CompletionResult> = StateEffect.define()

/** Effect to close the completion list. */
internal val closeCompletionEffect: StateEffectType<Unit> = StateEffect.define()

/** Internal state tracking the completion list. */
internal data class CompletionState(
    val result: CompletionResult? = null,
    val filtered: List<FilterResult> = emptyList(),
    val selected: Int = 0,
    val open: Boolean = false
) {
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
                    val completionResult = startEffect.value
                    val query = tr.state.doc.sliceString(
                        completionResult.from,
                        tr.state.selection.main.head
                    )
                    val filtered = filterCompletions(
                        completionResult.options,
                        query,
                        completionResult.filter
                    )
                    result = CompletionState(
                        result = completionResult,
                        filtered = filtered,
                        selected = 0,
                        open = filtered.isNotEmpty()
                    )
                }
                if (effect.`is`(closeCompletionEffect)) {
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

            // Re-filter on doc change if we have an open result
            if (tr.docChanged && result.open && result.result != null) {
                val cr = result.result
                val head = tr.state.selection.main.head
                val validFor = cr.validFor
                val currentText = tr.state.doc.sliceString(cr.from, head)

                if (validFor != null && validFor.containsMatchIn(currentText)) {
                    val filtered = filterCompletions(
                        cr.options,
                        currentText,
                        cr.filter
                    )
                    result = result.copy(
                        filtered = filtered,
                        selected = result.selected.coerceIn(
                            0,
                            (filtered.size - 1).coerceAtLeast(0)
                        ),
                        open = filtered.isNotEmpty()
                    )
                } else {
                    result = CompletionState.empty
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
