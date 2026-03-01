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

import com.monkopedia.kodemirror.state.Facet

/**
 * Configuration for the autocompletion extension.
 *
 * @param activateOnTyping Whether to show completions as the user types.
 * @param selectOnOpen Whether to select the first completion on open.
 * @param closeOnBlur Whether to close the completion list when the editor loses focus.
 * @param maxRenderedOptions Maximum number of options to render at once.
 * @param icons Whether to show type icons next to completions.
 * @param override Optional override completion sources that replace the defaults.
 */
data class CompletionConfig(
    val activateOnTyping: Boolean = true,
    val selectOnOpen: Boolean = true,
    val closeOnBlur: Boolean = true,
    val maxRenderedOptions: Int = 100,
    val icons: Boolean = true,
    val override: List<CompletionSource>? = null
)

/** Facet for completion configuration. */
val completionConfig: Facet<CompletionConfig, CompletionConfig> = Facet.define(
    combine = { values -> values.firstOrNull() ?: CompletionConfig() }
)
