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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Transaction

/**
 * The main editor composable.
 *
 * Creates and retains an [EditorView] instance, syncs it with [state] on each
 * recomposition, and renders the editor content.
 *
 * @param state    The current [EditorState] to display.
 * @param onUpdate Called with every [Transaction] the editor dispatches.
 * @param modifier Modifier applied to the outermost container.
 */
@Composable
fun EditorView(state: EditorState, onUpdate: (Transaction) -> Unit, modifier: Modifier = Modifier) {
    val view = remember { EditorView(state, onUpdate) }
    val pluginHost = remember(view) {
        ViewPluginHost(view).also { it.syncToState(state, null) }
    }
    val lineLayoutCache = remember(view) { LineLayoutCache() }

    // Wire up view internals
    DisposableEffect(view) {
        view.pluginHost = pluginHost
        view.lineLayoutCache = lineLayoutCache
        onDispose {
            pluginHost.destroy()
            lineLayoutCache.clear()
            view.pluginHost = null
            view.lineLayoutCache = null
        }
    }

    // Sync state + plugins whenever state changes
    LaunchedEffect(state) {
        val oldState = view.state
        if (oldState !== state) {
            pluginHost.syncToState(state, oldState)
            view.state = state
        }
    }

    // Derive rendering data from current state
    val theme = state.facet(editorTheme)
    val hasGutters = state.facet(gutters).isNotEmpty()
    val viewport = Viewport(0, state.doc.length)
    val extensionDecos = state.facet(decorations)
    val pluginDecos = pluginHost.collectDecorations()
    val allDecos = extensionDecos + pluginDecos
    val columnItems = remember(state, allDecos) {
        buildColumnItems(state, viewport, allDecos)
    }

    val lazyState = rememberLazyListState()

    // Track Alt key state for rectangular selection
    var altPressed by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val lineHeightDp = with(density) { theme.contentTextStyle.lineHeight.toDp() }

    CompositionLocalProvider(LocalEditorTheme provides theme) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(theme.background)
                .onFocusChanged { focusState ->
                    view.hasFocus = focusState.isFocused
                }
                .onPreviewKeyEvent { event ->
                    altPressed = event.isAltPressed
                    false
                }
                .onKeyEvent { event ->
                    handleKeyEvent(view, event)
                }
                .pointerInput(view) {
                    detectTapGestures { offset ->
                        handleTap(view, offset)
                    }
                }
                .pointerInput(view) {
                    var dragStart = androidx.compose.ui.geometry.Offset.Zero
                    var dragCurrent = androidx.compose.ui.geometry.Offset.Zero
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStart = offset
                            dragCurrent = offset
                        },
                        onDrag = { _, dragAmount ->
                            dragCurrent += dragAmount
                            if (altPressed) {
                                handleRectangularDrag(
                                    view,
                                    dragStart,
                                    dragCurrent
                                )
                            } else {
                                handleDrag(
                                    view,
                                    dragStart,
                                    dragCurrent
                                )
                            }
                            val pos = view.posAtCoords(
                                dragCurrent.x,
                                dragCurrent.y
                            )
                            view.plugin(dropCursorViewPlugin)
                                ?.moveTo(pos)
                        },
                        onDragEnd = {
                            view.plugin(dropCursorViewPlugin)
                                ?.moveTo(null)
                        },
                        onDragCancel = {
                            view.plugin(dropCursorViewPlugin)
                                ?.moveTo(null)
                        }
                    )
                }
                .pointerInput(view) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(
                                PointerEventPass.Main
                            )
                            val pos = event.changes
                                .firstOrNull()?.position
                            if (pos != null) {
                                val hoverTooltips =
                                    view.pluginHost
                                        ?.collectHoverPlugins()
                                        ?: emptyList()
                                for (plugin in hoverTooltips) {
                                    plugin.updateHover(pos.x, pos.y)
                                }
                            }
                        }
                    }
                }
        ) {
            var lineTopPx = 0f

            LazyColumn(
                state = lazyState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    vertical = 4.dp
                )
            ) {
                items(
                    items = columnItems,
                    key = { item ->
                        when (item) {
                            is ColumnItem.TextLine -> "line-${item.lineNumber}"
                            is ColumnItem.BlockWidgetItem -> "widget-${item.from}-${item.type}"
                        }
                    }
                ) { item ->
                    when (item) {
                        is ColumnItem.TextLine -> {
                            val capturedTop = lineTopPx
                            val capturedLineNum = item.lineNumber
                            val capturedFrom = item.from
                            var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

                            var lineModifier: Modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = lineHeightDp)
                            for (deco in item.lineDecorations) {
                                val bg = deco.spec.style?.background
                                if (bg != null && bg != Color.Unspecified) {
                                    lineModifier = lineModifier.background(bg)
                                }
                            }
                            Row(
                                modifier = lineModifier,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hasGutters) {
                                    GutterView(
                                        view = view,
                                        lineNumber = item.lineNumber
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 6.dp, end = 2.dp)
                                        .drawSelectionOverlay(
                                            state,
                                            item.from,
                                            item.to,
                                            theme,
                                            textLayout
                                        )
                                ) {
                                    BasicText(
                                        text = item.content,
                                        style = theme.contentTextStyle,
                                        onTextLayout = { result: TextLayoutResult ->
                                            textLayout = result
                                            lineLayoutCache.store(
                                                capturedLineNum,
                                                capturedFrom,
                                                capturedTop,
                                                result
                                            )
                                        }
                                    )
                                    for (widget in item.inlineWidgets) {
                                        widget.spec.widget.Content()
                                    }
                                }
                            }
                        }

                        is ColumnItem.BlockWidgetItem -> {
                            item.widget.spec.widget.Content()
                        }
                    }
                }
            }

            // Sync viewport/height tracking for ViewUpdate flags
            val firstVisible = lazyState.firstVisibleItemIndex
            LaunchedEffect(firstVisible) {
                view.lastFirstVisibleItem = firstVisible
            }

            // Tooltip layer
            TooltipLayer(view = view)
        }
    }
}
