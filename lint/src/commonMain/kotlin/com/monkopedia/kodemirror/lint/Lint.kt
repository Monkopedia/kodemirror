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

import androidx.compose.foundation.text.BasicText
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.Slot
import com.monkopedia.kodemirror.view.LocalEditorSession
import com.monkopedia.kodemirror.view.Panel
import com.monkopedia.kodemirror.view.Tooltip
import com.monkopedia.kodemirror.view.hoverTooltip
import com.monkopedia.kodemirror.view.keymap
import com.monkopedia.kodemirror.view.showPanels

/**
 * Create a linter extension.
 *
 * @param source Function that produces diagnostics for the current view.
 * @param config Linter configuration.
 */
fun linter(source: LintSource, config: LintConfig = LintConfig()): Extension {
    val linterPlugin = createLinterPlugin(source, config)

    val panelProvider = showPanels.compute(
        listOf(Slot.FieldSlot(lintPanelOpen))
    ) { state ->
        val open = state.field(lintPanelOpen, require = false) ?: false
        if (open) {
            listOf(
                Panel(top = false) {
                    val view = LocalEditorSession.current
                    LintPanelContent(view)
                }
            )
        } else {
            emptyList()
        }
    }

    val diagnosticHover = hoverTooltip { view, pos ->
        val diags = view.state.field(lintState, require = false)
            ?.diagnostics ?: emptyList()
        val docPos = DocPos(pos)
        val atPos = diags.filter { docPos >= it.from && docPos <= it.to }
        val filtered = config.tooltipFilter?.invoke(atPos) ?: atPos
        if (filtered.isEmpty()) {
            null
        } else {
            Tooltip(pos = pos) {
                BasicText(
                    text = filtered.joinToString("\n") { diag ->
                        "[${diag.severity.name.lowercase()}] ${diag.message}"
                    }
                )
            }
        }
    }

    return ExtensionList(
        listOf(
            lintState,
            lintPanelOpen,
            linterPlugin.asExtension(),
            panelProvider,
            diagnosticHover,
            keymap.of(lintKeymap)
        )
    )
}
