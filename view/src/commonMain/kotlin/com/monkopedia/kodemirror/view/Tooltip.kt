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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.RangeSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
fun TooltipLayer(session: EditorSession) {
    val impl = session as EditorSessionImpl
    val state by session::state
    val single = state.facet(showTooltip)
    val multi = state.facet(showTooltips)
    val all = buildList {
        if (single != null) add(single)
        addAll(multi)
        // Collect hover tooltips from active plugins
        val hoverPlugins = impl.pluginHost
            ?.collectHoverTooltips() ?: emptyList()
        addAll(hoverPlugins)
    }

    val theme = LocalEditorTheme.current
    val tooltipShape = RoundedCornerShape(4.dp)

    for (tooltip in all) {
        val coords = session.coordsAtPos(tooltip.pos) ?: continue
        // Position via a provider that knows the measured tooltip size and the
        // window bounds, so the tooltip is clamped on-screen and flipped
        // above/below the anchor instead of being placed at the raw caret
        // coordinate (which lands offscreen near the viewport edges, or for a
        // multi-line hover range whose end sits low in the viewport) (#110).
        val positionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset = computeTooltipOffset(
                anchorLeft = anchorBounds.left,
                anchorTop = anchorBounds.top,
                coordsLeft = coords.left.toInt(),
                coordsTop = coords.top.toInt(),
                coordsBottom = coords.bottom.toInt(),
                above = tooltip.above,
                strictSide = tooltip.strictSide,
                contentWidth = popupContentSize.width,
                contentHeight = popupContentSize.height,
                windowWidth = windowSize.width,
                windowHeight = windowSize.height
            )
        }
        Popup(popupPositionProvider = positionProvider) {
            Box(
                modifier = Modifier
                    .background(theme.tooltipBackground, tooltipShape)
                    .border(1.dp, theme.panelBorderColor, tooltipShape)
                    .padding(4.dp)
            ) {
                tooltip.content()
            }
        }
    }
}

/**
 * Compute the on-screen offset (in window coordinates) for a tooltip, clamping
 * it to the window and flipping it above/below the anchor so it stays visible.
 *
 * All inputs are pixels. [anchorLeft]/[anchorTop] are the window-space top-left
 * of the editor area the tooltip is anchored within; [coordsLeft]/[coordsTop]/
 * [coordsBottom] are the hovered caret rect within that editor area. The
 * tooltip is preferentially placed [gap] px below the caret (or above when
 * [above]); then:
 *  - x is clamped so the tooltip stays within the window horizontally;
 *  - vertically it flips to the opposite side if the preferred side would
 *    overflow the window and the opposite side fits (unless [strictSide]);
 *  - y is finally clamped into the window as a last resort (e.g. a tooltip
 *    taller than the viewport pins to the top).
 *
 * Pure geometry (no Compose types beyond the [IntOffset] result) so the
 * clamping/flipping is unit-testable without a render (#110).
 */
internal fun computeTooltipOffset(
    anchorLeft: Int,
    anchorTop: Int,
    coordsLeft: Int,
    coordsTop: Int,
    coordsBottom: Int,
    above: Boolean,
    strictSide: Boolean,
    contentWidth: Int,
    contentHeight: Int,
    windowWidth: Int,
    windowHeight: Int,
    gap: Int = 4
): IntOffset {
    val x = (anchorLeft + coordsLeft)
        .coerceIn(0, (windowWidth - contentWidth).coerceAtLeast(0))
    val belowY = anchorTop + coordsBottom + gap
    val aboveY = anchorTop + coordsTop - gap - contentHeight
    val belowFits = belowY + contentHeight <= windowHeight
    val aboveFits = aboveY >= 0
    val y = when {
        strictSide -> if (above) aboveY else belowY
        above -> if (!aboveFits && belowFits) belowY else aboveY
        else -> if (!belowFits && aboveFits) aboveY else belowY
    }.coerceIn(0, (windowHeight - contentHeight).coerceAtLeast(0))
    return IntOffset(x, y)
}

/**
 * Show a hover tooltip when the user points at text matching [source].
 *
 * @param source Function that, given a session and position, returns a tooltip
 *               or null.
 */
fun hoverTooltip(source: (EditorSession, Int) -> Tooltip?): Extension {
    return ViewPlugin.define(
        create = { session -> HoverTooltipPlugin(session, syncSource = source) },
        configure = {
            copy(
                decorations = { _ -> RangeSet.empty() }
            )
        }
    ).asExtension()
}

/**
 * Default hover delay, in milliseconds, used by the async [hoverTooltip]
 * overload. Mirrors upstream CodeMirror's `hoverTime` default.
 */
const val DEFAULT_HOVER_TIME: Long = 300

/**
 * Show a hover tooltip computed by a **suspending** [source].
 *
 * This is the asynchronous counterpart to the synchronous [hoverTooltip]: where
 * the synchronous overload demands a tooltip be produced immediately, this one
 * lets [source] suspend — for example to issue a network/IPC request such as an
 * LSP `textDocument/hover` — before returning a [Tooltip] (or null for "no
 * tooltip here").
 *
 * Upstream CodeMirror's hover source is promise-based (`(view, pos, side) =>
 * Tooltip | Promise<Tooltip | null> | null`); kodemirror's
 * [synchronous overload][hoverTooltip] cannot express that, so this overload is
 * the Kotlin/coroutine adaptation of upstream's async hover path.
 *
 * Behavior matches upstream's hover machinery:
 * - The request is debounced by [hoverTime] (the pointer must rest for that long
 *   before [source] is invoked).
 * - Moving to a different position cancels any in-flight request, so a slow
 *   server response for an abandoned position never pops a stale tooltip.
 *
 * @param hoverTime Delay before [source] is invoked, in milliseconds. Pass
 *   [DEFAULT_HOVER_TIME] for upstream's default; an explicit value is required
 *   so a bare trailing lambda is not ambiguous with the synchronous
 *   [hoverTooltip] overload.
 * @param source Suspending function returning a tooltip for the position, or
 *               null.
 */
fun hoverTooltip(hoverTime: Long, source: suspend (EditorSession, Int) -> Tooltip?): Extension {
    return ViewPlugin.define(
        create = { session ->
            HoverTooltipPlugin(session, asyncSource = source, hoverTime = hoverTime)
        },
        configure = {
            copy(
                decorations = { _ -> RangeSet.empty() }
            )
        }
    ).asExtension()
}

/**
 * Check whether any hover tooltips are currently active in the
 * given [session].
 */
fun hasHoverTooltips(session: EditorSession): Boolean {
    val impl = session as EditorSessionImpl
    return impl.pluginHost?.collectHoverPlugins()
        ?.any { it.currentTooltip != null } == true
}

/**
 * Programmatically close all hover tooltips in the given [session].
 */
fun closeHoverTooltips(session: EditorSession) {
    val impl = session as EditorSessionImpl
    impl.pluginHost?.collectHoverPlugins()?.forEach { it.clearHover() }
}

/**
 * Get all active tooltips in the given state, including both
 * facet-provided tooltips and hover tooltips.
 */
fun getTooltips(session: EditorSession): List<Tooltip> {
    val impl = session as EditorSessionImpl
    val state = session.state
    return buildList {
        state.facet(showTooltip)?.let { add(it) }
        addAll(state.facet(showTooltips))
        impl.pluginHost?.collectHoverTooltips()?.let { addAll(it) }
    }
}

/**
 * Force repositioning of all tooltips. In Compose, tooltips are
 * automatically repositioned via state changes, so this is
 * effectively a no-op. Provided for API compatibility with
 * upstream CodeMirror.
 */
@Suppress("UNUSED_PARAMETER")
fun repositionTooltips(session: EditorSession) {
    // In Compose, tooltips reposition automatically via recomposition
}

internal class HoverTooltipPlugin(
    private val session: EditorSession,
    private val syncSource: ((EditorSession, Int) -> Tooltip?)? = null,
    private val asyncSource: (suspend (EditorSession, Int) -> Tooltip?)? = null,
    private val hoverTime: Long = DEFAULT_HOVER_TIME
) : PluginValue {
    private val _currentTooltip = mutableStateOf<Tooltip?>(null)

    /** Lifecycle scope for in-flight async hover requests. */
    private val job = SupervisorJob(session.coroutineScope.coroutineContext[Job])
    private val scope: CoroutineScope =
        CoroutineScope(session.coroutineScope.coroutineContext + job)

    /** The most recent in-flight async hover request, cancelled on move. */
    private var pending: Job? = null

    /** The position the current/pending tooltip is anchored at. */
    private var lastPos: Int? = null

    val currentTooltip: Tooltip? get() = _currentTooltip.value

    fun updateHover(x: Float, y: Float) {
        val pos = session.posAtCoords(x, y)
        updateHoverAtPos(pos)
    }

    fun updateHoverAtPos(docPos: Int?) {
        if (docPos == lastPos) return
        lastPos = docPos
        // Any move cancels an outstanding async request so a late response for
        // an abandoned position can't pop a stale tooltip (matches upstream).
        pending?.cancel()
        pending = null

        val async = asyncSource
        if (async != null) {
            // Clear immediately, then debounce by [hoverTime] before requesting.
            _currentTooltip.value = null
            if (docPos == null) return
            pending = scope.launch {
                delay(hoverTime)
                val result = async(session, docPos)
                // Only show if the pointer is still resting on this position.
                if (lastPos == docPos) _currentTooltip.value = result
            }
        } else {
            val sync = syncSource
            _currentTooltip.value =
                if (docPos != null && sync != null) sync(session, docPos) else null
        }
    }

    fun clearHover() {
        pending?.cancel()
        pending = null
        lastPos = null
        _currentTooltip.value = null
    }

    override fun destroy() {
        job.cancel()
    }
}
