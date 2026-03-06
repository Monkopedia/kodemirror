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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorView

internal val openPanelEffect: StateEffectType<Boolean> = StateEffect.define()
internal val closePanelEffect: StateEffectType<Boolean> = StateEffect.define()

/** State field tracking whether the lint panel is open. */
internal val lintPanelOpen: StateField<Boolean> = StateField.define(
    StateFieldSpec(
        create = { false },
        update = { value, tr ->
            var result = value
            for (effect in tr.effects) {
                if (effect.asType(openPanelEffect) != null) result = true
                if (effect.asType(closePanelEffect) != null) result = false
            }
            result
        }
    )
)

/** Composable diagnostics panel content. */
@Composable
internal fun LintPanelContent(view: EditorView) {
    val diagnostics = view.state.field(lintState, require = false)?.diagnostics ?: emptyList()

    Column(modifier = Modifier.padding(4.dp)) {
        Row(modifier = Modifier.padding(bottom = 4.dp)) {
            BasicText("Diagnostics (${diagnostics.size})")
            BasicText(
                " [Close]",
                modifier = Modifier.clickable { closeLintPanel(view) }
            )
        }
        if (diagnostics.isEmpty()) {
            BasicText("No diagnostics")
        } else {
            LazyColumn {
                items(diagnostics) { diag ->
                    DiagnosticRow(diag, view)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(diag: Diagnostic, view: EditorView) {
    val severityColor = when (diag.severity) {
        Severity.HINT -> Color(0xFF2196F3)
        Severity.INFO -> Color(0xFF4CAF50)
        Severity.WARNING -> Color(0xFFFF9800)
        Severity.ERROR -> Color(0xFFF44336)
    }
    val severityLabel = when (diag.severity) {
        Severity.HINT -> "hint"
        Severity.INFO -> "info"
        Severity.WARNING -> "warn"
        Severity.ERROR -> "error"
    }

    Row(
        modifier = Modifier
            .padding(vertical = 1.dp)
            .clickable {
                view.dispatch(
                    TransactionSpec(
                        selection = SelectionSpec.CursorSpec(diag.from),
                        scrollIntoView = true,
                        userEvent = "select.lint"
                    )
                )
            }
    ) {
        BasicText(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = severityColor)) {
                    append("[$severityLabel]")
                }
                append(" ")
                if (diag.source != null) {
                    withStyle(SpanStyle(color = Color.Gray)) {
                        append("${diag.source}: ")
                    }
                }
                append(diag.message)
            }
        )
    }
}
