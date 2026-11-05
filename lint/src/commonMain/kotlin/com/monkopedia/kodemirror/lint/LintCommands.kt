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

import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding

/** Open the lint diagnostics panel. */
val openLintPanel: (EditorSession) -> Boolean = { view ->
    view.dispatch(
        TransactionSpec(
            effects = listOf(openPanelEffect.of(true))
        )
    )
    true
}

/** Close the lint diagnostics panel. */
val closeLintPanel: (EditorSession) -> Boolean = { view ->
    val open = view.state.field(lintPanelOpen, require = false) ?: false
    if (open) {
        view.dispatch(
            TransactionSpec(
                effects = listOf(closePanelEffect.of(true))
            )
        )
        true
    } else {
        false
    }
}

/**
 * Select the next diagnostic after the selection, wrapping to the first one. Returns false when
 * there is none, or when wrapping would select the range that is already selected.
 */
val nextDiagnostic: (EditorSession) -> Boolean = { view ->
    val diags = view.state.field(lintState, require = false)?.diagnostics.orEmpty()
    val sel = view.state.selection.main
    val after = sel.to + 1
    val next = diags.firstOrNull { it.to >= after }
        ?: diags.firstOrNull()?.takeUnless { it.from == sel.from && it.to == sel.to }
    if (next != null) selectDiagnostic(view, next)
    next != null
}

/**
 * Select the nearest diagnostic ending before the selection, wrapping to the last one. Returns
 * false when there is none, or when wrapping would land where the selection already starts.
 */
val previousDiagnostic: (EditorSession) -> Boolean = { view ->
    val diags = view.state.field(lintState, require = false)?.diagnostics.orEmpty()
    val sel = view.state.selection.main
    var prev: Diagnostic? = null
    var last: Diagnostic? = null
    for (diag in diags) {
        if (diag.to < sel.to && (prev == null || prev.from < diag.from)) prev = diag
        if (last == null || diag.from > last.from) last = diag
    }
    val target = when {
        last == null -> null
        prev == null && last.from == sel.from -> null
        else -> prev ?: last
    }
    if (target != null) selectDiagnostic(view, target)
    target != null
}

private fun selectDiagnostic(view: EditorSession, diag: Diagnostic) {
    view.dispatch(
        TransactionSpec(
            selection = SelectionSpec.EditorSelectionSpec(
                EditorSelection.single(diag.from, diag.to)
            ),
            scrollIntoView = true
        )
    )
}

/** Default lint keymap bindings. */
val lintKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Ctrl-Shift-m", run = { view ->
        val open = view.state.field(lintPanelOpen, require = false) ?: false
        if (open) closeLintPanel(view) else openLintPanel(view)
    }),
    KeyBinding(key = "F8", run = nextDiagnostic)
)
