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

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.monkopedia.kodemirror.state.Facet

/**
 * Describes a panel to show above or below the editor content.
 *
 * @param top     If true, show above the editor; otherwise below.
 * @param content The composable to render inside the panel.
 */
data class Panel(
    val top: Boolean = false,
    val content: @Composable () -> Unit
)

/** Facet that provides panels to display around the editor. */
val showPanel: Facet<Panel?, Panel?> = Facet.define(
    combine = { values -> values.firstOrNull { it != null } }
)

/** Facet for multiple simultaneous panels. */
val showPanels: Facet<List<Panel>, List<Panel>> = Facet.define(
    combine = { values -> values.flatten() }
)

/**
 * A layout composable that wraps the editor content with top/bottom panels.
 *
 * Usage:
 * ```kotlin
 * PanelLayout(view) {
 *     EditorView(state, onUpdate, modifier = Modifier.weight(1f))
 * }
 * ```
 */
@Composable
fun PanelLayout(view: EditorView, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = view.state
    val single = state.facet(showPanel)
    val multi = state.facet(showPanels)
    val all = buildList {
        if (single != null) add(single)
        addAll(multi)
    }
    val topPanels = all.filter { it.top }
    val bottomPanels = all.filter { !it.top }

    Column(modifier = modifier) {
        for (panel in topPanels) {
            panel.content()
        }
        content()
        for (panel in bottomPanels) {
            panel.content()
        }
    }
}
