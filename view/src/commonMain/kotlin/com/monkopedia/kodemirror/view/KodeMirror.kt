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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.asInsert

/**
 * The main editor composable.
 *
 * Wires up the [EditorSession] with plugin hosting, layout caching, and
 * input handling, then renders the editor content.
 *
 * @param session  The [EditorSession] to display.
 * @param modifier Modifier applied to the outermost container.
 */
@Composable
fun KodeMirror(session: EditorSession, modifier: Modifier = Modifier) {
    val impl = session as EditorSessionImpl
    val state = session.state

    val pluginHost = remember(session) {
        ViewPluginHost(session).also { it.syncToState(state, null) }
    }
    val lineLayoutCache = remember(session) { LineLayoutCache() }

    // Wire up session internals
    DisposableEffect(session) {
        impl.pluginHost = pluginHost
        impl.lineLayoutCache = lineLayoutCache
        onDispose {
            pluginHost.destroy()
            lineLayoutCache.clear()
            impl.pluginHost = null
            impl.lineLayoutCache = null
        }
    }

    // Derive rendering data from current state
    val theme = state.facet(editorTheme)
    val allPanels = buildList {
        state.facet(showPanel)?.let { add(it) }
        addAll(state.facet(showPanels))
    }
    val topPanels = allPanels.filter { it.top }
    val bottomPanels = allPanels.filter { !it.top }
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

    // Track editor layout coordinates for position mapping
    var editorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Focus management
    val focusRequester = remember { FocusRequester() }

    // Prevent tap from overriding drag selection
    var recentlyDragged by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val lineHeightDp = with(density) { theme.contentTextStyle.lineHeight.toDp() }

    // Compute gutter width based on digit count + padding (5dp + 3dp)
    val configs = state.facet(gutters)
    val gutterWidthDp = if (hasGutters) {
        val maxDigits = state.doc.lines.toString().length
        val charWidthDp = with(density) {
            (theme.contentTextStyle.fontSize.toPx() * 0.65f).toDp()
        }
        val lineNumberWidth = charWidthDp * maxDigits + 8.dp
        val extraGutterWidth =
            14.dp * configs.count { it.type != GutterType.LineNumbers && it.lineMarker != null }
        lineNumberWidth + extraGutterWidth
    } else {
        0.dp
    }

    // Content height in px (top padding + items + bottom padding)
    val contentHeightPx = with(density) {
        (4.dp + lineHeightDp * columnItems.size + 4.dp).toPx()
    }

    CompositionLocalProvider(
        LocalEditorTheme provides theme,
        LocalEditorSession provides session
    ) {
        Column(modifier = modifier.fillMaxSize()) {
            for (panel in topPanels) {
                Box(Modifier.fillMaxWidth().background(theme.panelBackground)) {
                    panel.content()
                }
                Box(
                    Modifier.fillMaxWidth().height(1.dp)
                        .background(theme.panelBorderColor)
                )
            }
            Box(
                modifier = Modifier
                    .testTag("KodeMirror")
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { editorCoordinates = it }
                    .drawWithContent {
                        // Editor background
                        drawRect(theme.background)
                        // Gutter background strip — only as tall as content
                        if (hasGutters) {
                            val w = gutterWidthDp.toPx()
                            val contentH = contentHeightPx.coerceAtMost(
                                size.height
                            )
                            drawRect(
                                color = theme.gutterBackground,
                                topLeft = Offset.Zero,
                                size = androidx.compose.ui.geometry.Size(
                                    w,
                                    contentH
                                )
                            )
                            val bc = theme.gutterBorderColor
                            if (bc != Color.Transparent) {
                                drawLine(
                                    color = bc,
                                    start = Offset(w - 0.5f, 0f),
                                    end = Offset(
                                        w - 0.5f,
                                        contentH
                                    ),
                                    strokeWidth = 1f
                                )
                            }
                        }
                        drawContent()
                    }
                    .onPreviewKeyEvent { event ->
                        altPressed = event.isAltPressed
                        false
                    }
                    .pointerInput(session) {
                        detectTapGestures { offset ->
                            if (recentlyDragged) {
                                recentlyDragged = false
                                return@detectTapGestures
                            }
                            focusRequester.requestFocus()
                            handleTap(session, offset)
                        }
                    }
                    .pointerInput(session) {
                        var dragStart = androidx.compose.ui.geometry.Offset.Zero
                        var dragCurrent = androidx.compose.ui.geometry.Offset.Zero
                        detectDragGestures(
                            onDragStart = { offset ->
                                recentlyDragged = true
                                dragStart = offset
                                dragCurrent = offset
                            },
                            onDrag = { _, dragAmount ->
                                dragCurrent += dragAmount
                                if (altPressed) {
                                    handleRectangularDrag(
                                        session,
                                        dragStart,
                                        dragCurrent
                                    )
                                } else {
                                    handleDrag(
                                        session,
                                        dragStart,
                                        dragCurrent
                                    )
                                }
                                val pos = session.posAtCoords(
                                    dragCurrent.x,
                                    dragCurrent.y
                                )
                                session.plugin(dropCursorViewPlugin)
                                    ?.moveTo(pos)
                            },
                            onDragEnd = {
                                session.plugin(dropCursorViewPlugin)
                                    ?.moveTo(null)
                            },
                            onDragCancel = {
                                recentlyDragged = false
                                session.plugin(dropCursorViewPlugin)
                                    ?.moveTo(null)
                            }
                        )
                    }
                    .pointerInput(session) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(
                                    PointerEventPass.Main
                                )
                                val pos = event.changes
                                    .firstOrNull()?.position
                                if (pos != null) {
                                    val hoverTooltips =
                                        impl.pluginHost
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
                // Hidden text field for receiving IME/text input and key events
                var hiddenTextValue by remember {
                    mutableStateOf(TextFieldValue(""))
                }
                BasicTextField(
                    value = hiddenTextValue,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
                        Color.Transparent
                    ),
                    onValueChange = { newValue ->
                        val inserted = newValue.text
                        if (inserted.isNotEmpty()) {
                            val sel = session.state.selection.main
                            val from = sel.from
                            val to = sel.to
                            val newCursor = com.monkopedia.kodemirror.state.DocPos(
                                from.value + inserted.length
                            )
                            session.dispatch(
                                TransactionSpec(
                                    changes = ChangeSpec.Single(
                                        from = from,
                                        to = to,
                                        insert = inserted.asInsert()
                                    ),
                                    selection = com.monkopedia.kodemirror.state.SelectionSpec
                                        .CursorSpec(newCursor)
                                )
                            )
                        }
                        // Reset to empty for next input
                        hiddenTextValue = TextFieldValue("")
                    },
                    modifier = Modifier
                        .testTag("KodeMirror_input")
                        .size(1.dp)
                        .alpha(0f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            impl.hasFocus = focusState.isFocused
                        }
                        .onPreviewKeyEvent { event ->
                            // Try key bindings first; if handled, consume.
                            // Character input flows through BasicTextField's
                            // onValueChange via the platform TextInputService.
                            handleKeyEvent(session, event)
                        }
                )

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
                                val capturedLineNum = item.lineNumber
                                val capturedFrom = item.from
                                var textLayout by remember {
                                    mutableStateOf<TextLayoutResult?>(
                                        null
                                    )
                                }

                                val lineModifier: Modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = lineHeightDp)
                                var contentExtraModifier: Modifier = Modifier
                                    .padding(start = 6.dp, end = 2.dp)
                                for (deco in item.lineDecorations) {
                                    val bg = deco.spec.style?.background
                                    if (bg != null && bg != Color.Unspecified) {
                                        contentExtraModifier = contentExtraModifier.background(bg)
                                    }
                                }
                                Row(
                                    modifier = lineModifier,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (hasGutters) {
                                        GutterView(
                                            session = session,
                                            lineNumber = item.lineNumber.value,
                                            modifier = Modifier.width(gutterWidthDp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .then(contentExtraModifier)
                                            .drawSelectionOverlay(
                                                state,
                                                item.from.value,
                                                item.to.value,
                                                theme,
                                                textLayout
                                            )
                                            .onGloballyPositioned { contentCoords ->
                                                val editorCoords =
                                                    editorCoordinates ?: return@onGloballyPositioned
                                                val pos = editorCoords.localPositionOf(
                                                    contentCoords,
                                                    Offset.Zero
                                                )
                                                val layout = textLayout
                                                if (layout != null) {
                                                    lineLayoutCache.store(
                                                        capturedLineNum.value,
                                                        capturedFrom.value,
                                                        pos.y,
                                                        pos.x,
                                                        layout
                                                    )
                                                }
                                            }
                                    ) {
                                        BasicText(
                                            text = item.content,
                                            style = theme.contentTextStyle,
                                            onTextLayout = { result: TextLayoutResult ->
                                                textLayout = result
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

                // Trigger relayout when editor coordinates are first captured,
                // so line layout caches can be populated on the next pass.
                // (onGloballyPositioned fires bottom-up: child content boxes
                // fire before the parent, so editorCoordinates is null on the
                // first layout pass. Reading it here triggers a recomposition
                // once the parent sets it, allowing content callbacks to work.)
                remember(editorCoordinates) { editorCoordinates }

                // Sync viewport/height tracking for ViewUpdate flags
                // Evict stale cache entries for scrolled-off lines
                val firstVisible = lazyState.firstVisibleItemIndex
                val visibleCount = lazyState.layoutInfo.visibleItemsInfo.size
                LaunchedEffect(firstVisible, visibleCount) {
                    impl.lastFirstVisibleItem = firstVisible
                    val visibleLineNumbers = columnItems
                        .drop(firstVisible)
                        .take(visibleCount.coerceAtLeast(1))
                        .filterIsInstance<ColumnItem.TextLine>()
                        .map { it.lineNumber.value }
                        .toSet()
                    if (visibleLineNumbers.isNotEmpty()) {
                        lineLayoutCache.evict(visibleLineNumbers)
                    }
                }

                // Tooltip layer
                TooltipLayer(session = session)
            }
            for (panel in bottomPanels) {
                Box(
                    Modifier.fillMaxWidth().height(1.dp)
                        .background(theme.panelBorderColor)
                )
                Box(Modifier.fillMaxWidth().background(theme.panelBackground)) {
                    panel.content()
                }
            }
        }
    }
}

/**
 * Create and remember an [EditorSession] with the given document text and extensions.
 */
@Composable
fun rememberEditorSession(doc: String = "", extensions: Extension? = null): EditorSession {
    return remember {
        val config = EditorStateConfig(
            doc = doc.asDoc(),
            extensions = extensions
        )
        EditorSession(EditorState.create(config))
    }
}

/**
 * Create and remember an [EditorSession] from an [EditorStateConfig].
 */
@Composable
fun rememberEditorSession(config: EditorStateConfig): EditorSession {
    return remember { EditorSession(EditorState.create(config)) }
}
