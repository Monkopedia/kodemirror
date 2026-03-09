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
 * Facet that registers language data providers. Each provider receives the
 * editor state, a position, and a side hint, and returns a list of property
 * maps describing language-specific data (e.g. autocomplete, indentation
 * rules) at that position.
 */
val languageData: Facet<
    (EditorState, DocPos, Int) -> List<Map<String, Any?>>,
    List<(EditorState, DocPos, Int) -> List<Map<String, Any?>>>
    > = Facet.define()

/**
 * Subtype of Command that doesn't require access to the actual
 * editor view.
 */
typealias StateCommand =
    (target: StateCommandTarget) -> Boolean

data class StateCommandTarget(
    val state: EditorState,
    val dispatch: (Transaction) -> Unit
)

/**
 * Facet that controls whether the editor allows multiple selections.
 * When any provider supplies `true`, multiple selections are enabled.
 */
val allowMultipleSelections: Facet<Boolean, Boolean> =
    Facet.define(
        combine = { values -> values.any { it } },
        static = true
    )

/**
 * Facet that configures the line separator used by the editor. When not
 * provided, the editor auto-detects the separator from the document
 * content. Only the first provided value is used.
 */
val lineSeparator: Facet<String, String?> = Facet.define(
    combine = { values ->
        values.firstOrNull()
    },
    static = true
)

sealed interface ChangeFilterResult {
    data object Accept : ChangeFilterResult
    data object Reject : ChangeFilterResult
    data class Ranges(val ranges: IntArray) : ChangeFilterResult
}

/**
 * Facet that registers change filters. Each filter is called with a
 * transaction and can accept, reject, or restrict the ranges affected
 * by the change.
 */
val changeFilter: Facet<
    (Transaction) -> ChangeFilterResult,
    List<(Transaction) -> ChangeFilterResult>
    > = Facet.define()

sealed interface TransactionFilterResult {
    data class Filtered(val transaction: Transaction) : TransactionFilterResult
    data class Specs(val specs: List<TransactionSpec>) : TransactionFilterResult
}

/**
 * Facet that registers transaction filters. Each filter can modify or
 * replace a transaction before it is applied to the state. Filters can
 * return either a modified transaction or a list of transaction specs.
 */
val transactionFilter: Facet<
    (Transaction) -> TransactionFilterResult,
    List<(Transaction) -> TransactionFilterResult>
    > = Facet.define()

/**
 * Facet that registers transaction extenders. Each extender can add
 * additional effects or annotations to a transaction after filters
 * have run. Returns `null` to leave the transaction unchanged.
 */
val transactionExtender: Facet<
    (Transaction) -> TransactionExtenderResult?,
    List<(Transaction) -> TransactionExtenderResult?>
    > = Facet.define()

/**
 * Facet that registers inverted effect providers. Each provider maps a
 * transaction to a list of effects that should be applied when the
 * transaction is undone, enabling custom undo/redo behavior for
 * state effects.
 */
val invertedEffects: Facet<
    (Transaction) -> List<StateEffect<*>>,
    List<(Transaction) -> List<StateEffect<*>>>
    > = Facet.define()

/**
 * Facet that makes the editor read-only. When the first provided value
 * is `true`, the editor rejects user changes.
 */
val readOnly: Facet<Boolean, Boolean> = Facet.define(
    combine = { values ->
        values.firstOrNull() ?: false
    }
)
