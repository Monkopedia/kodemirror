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

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.view.EditorSession

/**
 * Standard completion type indicators. Use [value] as the [Completion.type]
 * string, or pass the enum directly to [Completion] via [CompletionType.value].
 */
enum class CompletionType(val value: String) {
    CLASS("class"),
    CONSTANT("constant"),
    ENUM("enum"),
    FUNCTION("function"),
    INTERFACE("interface"),
    KEYWORD("keyword"),
    METHOD("method"),
    NAMESPACE("namespace"),
    PROPERTY("property"),
    TEXT("text"),
    TYPE("type"),
    VARIABLE("variable")
}

/**
 * A single completion option.
 *
 * @param label The text to insert (and the primary display text).
 * @param displayLabel Optional alternative display text.
 * @param detail Optional short description shown next to the label.
 * @param info Optional longer documentation string.
 * @param type Optional type indicator (e.g. "function", "variable", "keyword").
 * @param boost Priority boost (higher = shown earlier).
 * @param apply Optional custom apply text (overrides [label]).
 * @param section Optional grouping section.
 */
data class Completion(
    val label: String,
    val displayLabel: String? = null,
    val detail: String? = null,
    val info: String? = null,
    val type: String? = null,
    val boost: Int = 0,
    val apply: String? = null,
    val applyFn: ((CompletionApplyContext) -> Unit)? = null,
    val section: CompletionSection? = null
)

/** A named section for grouping completions. */
data class CompletionSection(
    val name: String,
    val rank: Int = 0
)

/**
 * The result of a completion source query.
 *
 * @param from Start of the text range that completions apply to.
 * @param to End of the text range. When `null`, defaults to the cursor position.
 * @param options The list of completion options.
 * @param validFor A regex or predicate that, when matching the updated text
 *                 between [from] and the cursor, allows the result to be reused.
 * @param filter Whether the results should be filtered by the editor (default true).
 */
data class CompletionResult(
    val from: DocPos,
    val to: DocPos? = null,
    val options: List<Completion>,
    val validFor: Regex? = null,
    val filter: Boolean = true
)

/**
 * Context passed to [Completion.applyFn] when a completion is accepted.
 *
 * @param session The editor session to apply the completion to.
 * @param completion The completion being applied.
 * @param from Start of the text range being replaced.
 * @param to End of the text range being replaced.
 */
data class CompletionApplyContext(
    val session: EditorSession,
    val completion: Completion,
    val from: DocPos,
    val to: DocPos
)

/** A function that provides completions for a given context. */
typealias CompletionSource = (CompletionContext) -> CompletionResult?

/**
 * A suspend function that provides completions for a given context.
 *
 * Use with [asyncCompletionSource] to create a [CompletionSource] that
 * runs in a coroutine. The coroutine is cancelled if the user types
 * while completion is pending.
 */
typealias SuspendCompletionSource = suspend (CompletionContext) -> CompletionResult?

/**
 * Wrap a [SuspendCompletionSource] into a [CompletionSource] that runs
 * the suspend function in a blocking context.
 *
 * **Note:** This blocks the calling thread. For truly non-blocking async
 * completions (e.g., LSP servers), consider using a [ViewPlugin] with its
 * own [CoroutineScope] instead.
 */
expect fun asyncCompletionSource(source: SuspendCompletionSource): CompletionSource
