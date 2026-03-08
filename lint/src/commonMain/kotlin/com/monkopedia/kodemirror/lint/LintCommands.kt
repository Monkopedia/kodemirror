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

/** Jump to the next diagnostic after the cursor. */
val nextDiagnostic: (EditorSession) -> Boolean = { view ->
    val state = view.state
    val diags = state.field(lintState, require = false)?.diagnostics ?: emptyList()
    val pos = state.selection.main.head
    val next = diags.firstOrNull { it.from > pos } ?: diags.firstOrNull()
    if (next != null) {
        view.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(next.from),
                scrollIntoView = true,
                userEvent = "select.lint"
            )
        )
        true
    } else {
        false
    }
}

/** Jump to the previous diagnostic before the cursor. */
val previousDiagnostic: (EditorSession) -> Boolean = { view ->
    val state = view.state
    val diags = state.field(lintState, require = false)?.diagnostics ?: emptyList()
    val pos = state.selection.main.head
    val prev = diags.lastOrNull { it.from < pos } ?: diags.lastOrNull()
    if (prev != null) {
        view.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(prev.from),
                scrollIntoView = true,
                userEvent = "select.lint"
            )
        )
        true
    } else {
        false
    }
}

/** Default lint keymap bindings. */
val lintKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Ctrl-Shift-m", run = { view ->
        val open = view.state.field(lintPanelOpen, require = false) ?: false
        if (open) closeLintPanel(view) else openLintPanel(view)
    }),
    KeyBinding(key = "F8", run = nextDiagnostic)
)
