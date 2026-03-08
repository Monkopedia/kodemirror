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

import com.monkopedia.kodemirror.view.EditorSession

/** Severity levels for diagnostics. */
enum class Severity { HINT, INFO, WARNING, ERROR }

/**
 * A diagnostic message attached to a range in the document.
 *
 * @param from Start of the problematic range.
 * @param to End of the problematic range.
 * @param severity The severity level.
 * @param message Human-readable description of the issue.
 * @param source Optional name of the lint source (e.g. "eslint").
 * @param actions Optional quick-fix actions.
 * @param markClass Optional CSS class for the mark decoration.
 */
data class Diagnostic(
    val from: Int,
    val to: Int,
    val severity: Severity,
    val message: String,
    val source: String? = null,
    val actions: List<Action> = emptyList(),
    val markClass: String? = null
)

/** A quick-fix action that can be applied to resolve a diagnostic. */
data class Action(
    val name: String,
    val apply: (EditorSession) -> Unit
)

/** Configuration for the linter. */
data class LintConfig(
    val delay: Long = 750,
    val markerFilter: ((diagnostics: List<Diagnostic>) -> List<Diagnostic>)? = null,
    val tooltipFilter: ((diagnostics: List<Diagnostic>) -> List<Diagnostic>)? = null,
    val autoPanel: Boolean = false
)

/** Configuration for the lint gutter. */
data class LintGutterConfig(
    val hoverTime: Long = 300,
    val markerFilter: ((diagnostics: List<Diagnostic>) -> List<Diagnostic>)? = null,
    val tooltipFilter: ((diagnostics: List<Diagnostic>) -> List<Diagnostic>)? = null
)

/** A lint source function that produces diagnostics for the current editor state. */
typealias LintSource = (EditorSession) -> List<Diagnostic>
