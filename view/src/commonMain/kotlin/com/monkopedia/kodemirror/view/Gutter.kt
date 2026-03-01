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
package com.monkopedia.kodemirror.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Facet

/** A gutter marker contributes to a specific line's gutter column. */
abstract class GutterMarker {
    /** Whether this marker is equivalent to another. */
    open fun eq(other: GutterMarker): Boolean = this === other

    /** Render the marker content. */
    @Composable
    abstract fun Content(theme: EditorTheme)

    /** True if this marker produces DOM-visible content. */
    open val elementClass: String? get() = null
}

/** Configuration for a gutter column. */
data class GutterConfig(
    /** CSS class applied to the gutter container. */
    val cssClass: String? = null,
    /** Called for each visible line to get the marker, or null. */
    val lineMarker: ((EditorView, Int) -> GutterMarker?)? = null,
    /** Called when the gutter is clicked. */
    val lineMarkerChange: ((ViewUpdate) -> Boolean)? = null,
    /** Padding on the right of gutter content. */
    val renderEmptyElements: Boolean = false,
    /** Initial marker for the gutter (shown before any lines). */
    val initialSpacer: ((EditorView) -> GutterMarker)? = null,
    /** Marker shown when the gutter is active (cursor on line). */
    val updateSpacer: ((GutterMarker, ViewUpdate) -> GutterMarker)? = null
)

/** Facet that collects all registered gutter configurations. */
val gutters: Facet<GutterConfig, List<GutterConfig>> = Facet.define()

/** Create an extension that adds a gutter column. */
fun gutter(config: GutterConfig): Extension = gutters.of(config)

/**
 * A composable that renders all registered gutter columns alongside the
 * editor content.
 *
 * Place this to the left of the [EditorView] composable in a [Row].
 */
@Composable
fun GutterView(view: EditorView, lineNumber: Int, modifier: Modifier = Modifier) {
    val theme = LocalEditorTheme.current
    val isActive = view.state.doc.lineAt(view.state.selection.main.head).number == lineNumber
    val line = view.state.doc.line(lineNumber)
    val configs = view.state.facet(gutters)

    Row(
        modifier = modifier
            .background(theme.gutterBackground)
            .drawBehind {
                val borderColor = theme.gutterBorderColor
                if (borderColor != Color.Transparent) {
                    drawLine(
                        color = borderColor,
                        start = Offset(size.width - 0.5f, 0f),
                        end = Offset(size.width - 0.5f, size.height),
                        strokeWidth = 1f
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Render custom gutter markers from configs
        for (config in configs) {
            val lineMarkerFn = config.lineMarker
            if (lineMarkerFn != null) {
                val marker = lineMarkerFn(view, line.from)
                if (marker != null) {
                    marker.Content(theme)
                }
            }
        }
        // Line number column
        Box(
            modifier = Modifier.padding(start = 5.dp, end = 3.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            BasicText(
                text = lineNumber.toString(),
                style = theme.contentTextStyle.copy(
                    color = if (isActive) {
                        theme.gutterActiveForeground
                    } else {
                        theme.gutterForeground
                    },
                    textAlign = TextAlign.End
                )
            )
        }
    }
}

/**
 * Extension that adds line numbers to the editor gutter.
 */
val lineNumbers: Extension = gutter(
    GutterConfig(
        cssClass = "cm-lineNumbers",
        // line numbers rendered by GutterView composable
        lineMarker = { _, _ -> null }
    )
)

/**
 * Extension that highlights the active line's gutter entry.
 * Works in conjunction with [highlightActiveLine].
 */
val highlightActiveLineGutter: Extension = gutter(
    GutterConfig(
        cssClass = "cm-activeLineGutter"
    )
)
