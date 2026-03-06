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

val languageData: Facet<
    (EditorState, Int, Int) -> List<Map<String, Any?>>,
    List<(EditorState, Int, Int) -> List<Map<String, Any?>>>
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

val allowMultipleSelections: Facet<Boolean, Boolean> =
    Facet.define(
        combine = { values -> values.any { it } },
        static = true
    )

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

val changeFilter: Facet<
    (Transaction) -> ChangeFilterResult,
    List<(Transaction) -> ChangeFilterResult>
    > = Facet.define()

sealed interface TransactionFilterResult {
    data class Filtered(val transaction: Transaction) : TransactionFilterResult
    data class Specs(val specs: List<TransactionSpec>) : TransactionFilterResult
}

val transactionFilter: Facet<
    (Transaction) -> TransactionFilterResult,
    List<(Transaction) -> TransactionFilterResult>
    > = Facet.define()

val transactionExtender: Facet<
    (Transaction) -> TransactionExtenderResult?,
    List<(Transaction) -> TransactionExtenderResult?>
    > = Facet.define()

val invertedEffects: Facet<
    (Transaction) -> List<StateEffect<*>>,
    List<(Transaction) -> List<StateEffect<*>>>
    > = Facet.define()

val readOnly: Facet<Boolean, Boolean> = Facet.define(
    combine = { values ->
        values.firstOrNull() ?: false
    }
)
