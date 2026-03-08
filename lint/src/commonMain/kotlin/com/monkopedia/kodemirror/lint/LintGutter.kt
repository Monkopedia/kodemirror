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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.view.EditorTheme
import com.monkopedia.kodemirror.view.GutterConfig
import com.monkopedia.kodemirror.view.GutterMarker
import com.monkopedia.kodemirror.view.Tooltip
import com.monkopedia.kodemirror.view.gutter
import com.monkopedia.kodemirror.view.hoverTooltip

/** Gutter marker that shows a colored circle for a diagnostic severity. */
internal class LintGutterMarker(val severity: Severity) : GutterMarker() {
    override fun equals(other: Any?): Boolean =
        other is LintGutterMarker && other.severity == severity

    override fun hashCode(): Int = severity.hashCode()

    @Composable
    override fun Content(theme: EditorTheme) {
        val color = when (severity) {
            Severity.HINT -> Color(0xFF2196F3)
            Severity.INFO -> Color(0xFF4CAF50)
            Severity.WARNING -> Color(0xFFFF9800)
            Severity.ERROR -> Color(0xFFF44336)
        }
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
    }
}

/**
 * Extension that adds a lint gutter column showing colored markers for
 * lines that have diagnostics.
 *
 * @param config Optional configuration for the lint gutter.
 */
fun lintGutter(config: LintGutterConfig = LintGutterConfig()): Extension {
    val gutterExt = gutter(
        GutterConfig(
            cssClass = "cm-lint-gutter",
            lineMarker = { view, linePos ->
                val diagnostics = view.state.field(lintState, require = false)
                    ?.diagnostics ?: emptyList()
                val line = view.state.doc.lineAt(linePos)
                val lineDiags = diagnostics.filter { it.from >= line.from && it.from < line.to }
                val filtered = config.markerFilter?.invoke(lineDiags) ?: lineDiags
                if (filtered.isEmpty()) {
                    null
                } else {
                    // Show the highest severity marker
                    val maxSeverity = filtered.maxOf { it.severity }
                    LintGutterMarker(maxSeverity)
                }
            },
            lineMarkerChange = { update ->
                update.transactions.any { tr ->
                    tr.effects.any { it.asType(setDiagnosticsEffect) != null }
                }
            }
        )
    )

    val hoverExt = hoverTooltip { view, pos ->
        val diagnostics = view.state.field(lintState, require = false)
            ?.diagnostics ?: emptyList()
        val line = view.state.doc.lineAt(pos)
        val lineDiags = diagnostics.filter { it.from >= line.from && it.from < line.to }
        val filtered = config.tooltipFilter?.invoke(lineDiags) ?: lineDiags
        if (filtered.isEmpty()) {
            null
        } else {
            Tooltip(pos = line.from) {
                BasicText(
                    text = filtered.joinToString("\n") { diag ->
                        "[${diag.severity.name.lowercase()}] ${diag.message}"
                    }
                )
            }
        }
    }

    return ExtensionList(listOf(gutterExt, hoverExt))
}
