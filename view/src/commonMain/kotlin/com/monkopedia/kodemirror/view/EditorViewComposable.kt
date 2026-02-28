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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
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
    val pluginHost = remember(view) { ViewPluginHost(view) }
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
        pluginHost.syncToState(state, oldState)
        view.state = state
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

    CompositionLocalProvider(LocalEditorTheme provides theme) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(theme.background)
                .onKeyEvent { event ->
                    handleKeyEvent(view, event)
                }
                .pointerInput(view) {
                    detectTapGestures { offset ->
                        handleTap(view, offset)
                    }
                }
                .pointerInput(view) {
                    var startOffset = androidx.compose.ui.geometry.Offset.Zero
                    detectDragGestures(
                        onDragStart = { offset -> startOffset = offset },
                        onDrag = { _, dragAmount ->
                            startOffset += dragAmount
                        }
                    )
                }
        ) {
            var lineTopPx = 0f

            LazyColumn(state = lazyState) {
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

                            Row(modifier = Modifier.fillMaxWidth()) {
                                if (hasGutters) {
                                    GutterView(
                                        view = view,
                                        lineNumber = item.lineNumber
                                    )
                                }
                                BasicText(
                                    text = item.content,
                                    style = theme.contentTextStyle,
                                    modifier = Modifier
                                        .weight(1f)
                                        .drawSelectionOverlay(
                                            state,
                                            lineLayoutCache,
                                            theme
                                        ),
                                    onTextLayout = { result: TextLayoutResult ->
                                        lineLayoutCache.store(
                                            capturedLineNum,
                                            capturedFrom,
                                            capturedTop,
                                            result
                                        )
                                    }
                                )
                            }
                        }

                        is ColumnItem.BlockWidgetItem -> {
                            item.widget.spec.widget.Content()
                        }
                    }
                }
            }

            // Tooltip layer
            TooltipLayer(view = view)
        }
    }
}
