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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.Popup
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.RangeSet

/**
 * Describes a tooltip to show near a document position.
 *
 * @param pos       Document position where the tooltip is anchored.
 * @param above     If true, show the tooltip above the anchor; otherwise below.
 * @param strictSide If true, don't flip the tooltip even if it goes off-screen.
 * @param content   The composable that renders the tooltip body.
 */
data class Tooltip(
    val pos: Int,
    val above: Boolean = false,
    val strictSide: Boolean = false,
    val content: @Composable () -> Unit
)

/** Facet that provides a single tooltip at a time. */
val showTooltip: Facet<Tooltip?, Tooltip?> = Facet.define(
    combine = { values -> values.firstOrNull { it != null } }
)

/** Facet that provides multiple simultaneous tooltips. */
val showTooltips: Facet<List<Tooltip>, List<Tooltip>> = Facet.define(
    combine = { values -> values.flatten() }
)

/**
 * A composable layer that renders all active tooltips using [Popup].
 *
 * Place this inside the editor's [Box] container so tooltips appear on top of
 * the content. Also renders hover tooltips from active [HoverTooltipPlugin]s.
 */
@Composable
fun TooltipLayer(view: EditorView) {
    val state = view.state
    val single = state.facet(showTooltip)
    val multi = state.facet(showTooltips)
    val all = buildList {
        if (single != null) add(single)
        addAll(multi)
        // Collect hover tooltips from active plugins
        val hoverPlugins = view.pluginHost
            ?.collectHoverTooltips() ?: emptyList()
        addAll(hoverPlugins)
    }

    for (tooltip in all) {
        val coords = view.coordsAtPos(tooltip.pos) ?: continue
        Popup(
            alignment = androidx.compose.ui.Alignment.TopStart,
            offset = androidx.compose.ui.unit.IntOffset(
                x = coords.left.toInt(),
                y = if (tooltip.above) {
                    (coords.top - 4f).toInt()
                } else {
                    (coords.bottom + 4f).toInt()
                }
            )
        ) {
            tooltip.content()
        }
    }
}

/**
 * Show a hover tooltip when the user points at text matching [source].
 *
 * @param source Function that, given a view and position, returns a tooltip
 *               or null.
 */
fun hoverTooltip(source: (EditorView, Int) -> Tooltip?): Extension {
    return ViewPlugin.define(
        create = { view -> HoverTooltipPlugin(view, source) },
        configure = {
            copy(
                decorations = { _ -> RangeSet.empty() }
            )
        }
    ).asExtension()
}

/**
 * Check whether any hover tooltips are currently active in the
 * given [view].
 */
fun hasHoverTooltips(view: EditorView): Boolean {
    return view.pluginHost?.collectHoverPlugins()
        ?.any { it.currentTooltip != null } == true
}

/**
 * Programmatically close all hover tooltips in the given [view].
 */
fun closeHoverTooltips(view: EditorView) {
    view.pluginHost?.collectHoverPlugins()?.forEach { it.clearHover() }
}

/**
 * Get all active tooltips in the given state, including both
 * facet-provided tooltips and hover tooltips.
 */
fun getTooltips(view: EditorView): List<Tooltip> {
    val state = view.state
    return buildList {
        state.facet(showTooltip)?.let { add(it) }
        addAll(state.facet(showTooltips))
        view.pluginHost?.collectHoverTooltips()?.let { addAll(it) }
    }
}

/**
 * Force repositioning of all tooltips. In Compose, tooltips are
 * automatically repositioned via state changes, so this is
 * effectively a no-op. Provided for API compatibility with
 * upstream CodeMirror.
 */
@Suppress("UNUSED_PARAMETER")
fun repositionTooltips(view: EditorView) {
    // In Compose, tooltips reposition automatically via recomposition
}

internal class HoverTooltipPlugin(
    private val view: EditorView,
    private val source: (EditorView, Int) -> Tooltip?
) : PluginValue {
    private val _currentTooltip = mutableStateOf<Tooltip?>(null)

    val currentTooltip: Tooltip? get() = _currentTooltip.value

    fun updateHover(x: Float, y: Float) {
        val pos = view.posAtCoords(x, y)
        _currentTooltip.value = if (pos != null) source(view, pos) else null
    }

    fun clearHover() {
        _currentTooltip.value = null
    }
}
