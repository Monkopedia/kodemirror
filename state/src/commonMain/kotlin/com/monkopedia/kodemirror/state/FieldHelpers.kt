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
package com.monkopedia.kodemirror.state

/**
 * A handle grouping a [StateField] with its associated [StateEffectType]s
 * for a boolean toggle pattern.
 *
 * Dispatch [toggle] with `Unit` to flip the value, or [set] with a
 * specific `Boolean` to force it.
 *
 * ```kotlin
 * val (panelField, togglePanel, setPanel) = toggleField(default = false)
 *
 * // Toggle:
 * session.dispatch(TransactionSpec(effects = listOf(togglePanel.of(Unit))))
 *
 * // Force on:
 * session.dispatch(TransactionSpec(effects = listOf(setPanel.of(true))))
 *
 * // Read:
 * val isOpen = state.field(panelField)
 * ```
 */
data class ToggleFieldHandle(
    val field: StateField<Boolean>,
    val toggle: StateEffectType<Unit>,
    val set: StateEffectType<Boolean>
)

/**
 * Create a boolean [StateField] with [toggle] and [set] effects.
 *
 * @param default the initial value of the field
 * @return a [ToggleFieldHandle] with the field and effect types
 */
fun toggleField(default: Boolean = false): ToggleFieldHandle {
    val toggle = StateEffect.define<Unit>()
    val set = StateEffect.define<Boolean>()
    val field = StateField.define(
        StateFieldSpec(
            create = { default },
            update = { value, tr ->
                var result = value
                for (effect in tr.effects) {
                    effect.asType(toggle)?.let { result = !result }
                    effect.asType(set)?.let { result = it.value }
                }
                result
            }
        )
    )
    return ToggleFieldHandle(field, toggle, set)
}

/**
 * A handle grouping a [StateField] with associated [StateEffectType]s
 * for a set-based pattern.
 *
 * ```kotlin
 * val bookmarks = setField<Int>()
 *
 * // Add a bookmark:
 * session.dispatch(TransactionSpec(effects = listOf(bookmarks.add.of(42))))
 *
 * // Remove a bookmark:
 * session.dispatch(TransactionSpec(effects = listOf(bookmarks.remove.of(42))))
 *
 * // Clear all:
 * session.dispatch(TransactionSpec(effects = listOf(bookmarks.clear.of(Unit))))
 *
 * // Read:
 * val current = state.field(bookmarks.field)
 * ```
 */
data class SetFieldHandle<T>(
    val field: StateField<Set<T>>,
    val add: StateEffectType<T>,
    val remove: StateEffectType<T>,
    val clear: StateEffectType<Unit>
)

/**
 * Create a [StateField] backed by a [Set] with [add], [remove], and [clear] effects.
 *
 * @param default the initial set value
 * @return a [SetFieldHandle] with the field and effect types
 */
fun <T> setField(default: Set<T> = emptySet()): SetFieldHandle<T> {
    val add = StateEffect.define<T>()
    val remove = StateEffect.define<T>()
    val clear = StateEffect.define<Unit>()
    val field = StateField.define(
        StateFieldSpec(
            create = { default },
            update = { value, tr ->
                var result = value
                for (effect in tr.effects) {
                    effect.asType(add)?.let { result = result + it.value }
                    effect.asType(remove)?.let { result = result - it.value }
                    effect.asType(clear)?.let { result = emptySet() }
                }
                result
            }
        )
    )
    return SetFieldHandle(field, add, remove, clear)
}
