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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.asInsert
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The main editor composable.
 *
 * Wires up the [EditorSession] with plugin hosting, layout caching, and
 * input handling, then renders the editor content.
 *
 * ## Height contract
 *
 * The editor fills the height it is given. Callers should place it under a
 * **bounded** vertical constraint — e.g. a parent that constrains height, or
 * `Modifier.height(...)` / `Modifier.heightIn(max = ...)`. Under a bounded
 * height the inner line list scrolls and caret reveal (scroll-into-view) keeps
 * the caret on screen, matching CodeMirror 6, where the editor scroller is
 * given a height (often a `max-height`).
 *
 * If the editor is placed under an **unbounded** vertical constraint — e.g. a
 * parent with `Modifier.verticalScroll(...)`, `wrapContentHeight()`, or inside
 * another vertically scrolling container — there is no bounded height to fill.
 * In that case the editor grows to its full content height (the whole document
 * is laid out and the outer container scrolls). It will NOT collapse to zero
 * height. Note that under unbounded constraints the editor's own
 * scroll-into-view cannot keep the caret visible (there is nothing to scroll
 * within the editor); the surrounding scroll container governs visibility.
 *
 * @param session  The [EditorSession] to display.
 * @param modifier Modifier applied to the outermost container.
 */
@Composable
fun KodeMirror(session: EditorSession, modifier: Modifier = Modifier) {
    val impl = session as EditorSessionImpl
    val state by session::state

    // Attach the coroutine scope to the session BEFORE constructing the plugin
    // host. syncToState() builds the view plugins, and some plugins (e.g.
    // HoverTooltipPlugin) read session.coroutineScope eagerly in their
    // initializer; if the scope isn't attached yet that read throws
    // "EditorSession is not attached to a KodeMirror composable" (#92).
    val compositionScope = rememberCoroutineScope()
    impl.backingCoroutineScope = compositionScope

    val pluginHost = remember(session) {
        ViewPluginHost(session).also { it.syncToState(state, null) }
    }
    val lineLayoutCache = remember(session) { LineLayoutCache() }
    // Deferred layouts: onGloballyPositioned fires bottom-up (children before
    // parent), so editorCoordinates is null on the first layout pass. We store
    // the child coordinates here and populate the cache once the parent fires.
    val pendingLineLayouts = remember(session) {
        mutableMapOf<Int, PendingLineLayout>()
    }
    // TextLayoutResult per line, populated from onTextLayout (always reliable).
    // Used as a fallback for tap positioning when lineLayoutCache is incomplete.
    val textLayoutResults = remember(session) {
        mutableMapOf<Int, TextLayoutResult>()
    }

    val clipboardManager = LocalClipboardManager.current

    // Wire up session internals eagerly (during composition, not in a side
    // effect) so that TooltipLayer and the hover pointerInput block can access
    // pluginHost on the very first frame.  DisposableEffect still handles
    // cleanup on dispose; the repeated assignments on recomposition are
    // harmless because remember() always returns the same instances.
    impl.pluginHost = pluginHost
    impl.lineLayoutCache = lineLayoutCache
    impl.clipboardManager = clipboardManager

    DisposableEffect(session) {
        onDispose {
            pluginHost.destroy()
            lineLayoutCache.clear()
            impl.pluginHost = null
            impl.lineLayoutCache = null
            impl.backingCoroutineScope = null
            impl.clipboardManager = null
        }
    }

    // Derive rendering data from current state
    val theme = state.facet(editorTheme)
    val contentStyle = state.facet(editorContentStyle).let { style ->
        if (style.color == Color.Unspecified) style.copy(color = theme.foreground) else style
    }
    val allPanels = buildList {
        state.facet(showPanel)?.let { add(it) }
        addAll(state.facet(showPanels))
    }
    val topPanels = allPanels.filter { it.top }
    val bottomPanels = allPanels.filter { !it.top }
    val hasGutters = state.facet(gutters).isNotEmpty()
    // Line-wrapping mode. Default (false) matches CodeMirror 6: long lines do
    // not wrap and the content scrolls horizontally. When the `lineWrapping`
    // extension is present, lines soft-wrap onto multiple visual rows instead.
    val wrapLines = state.facet(lineWrappingFacet)
    val viewport = Viewport(0, state.doc.length)
    // Compute columnItems when state changes. Decorations (both from
    // facets and plugins) are derived from state, so `state` is a
    // sufficient remember key. Avoid using list concatenation results
    // as keys — the `+` operator creates a new reference every
    // composition, defeating memoization.
    val columnItems = remember(state) {
        val extensionDecos = state.facet(decorations)
        val pluginDecos = pluginHost.collectDecorations()
        buildColumnItems(state, viewport, extensionDecos + pluginDecos)
    }

    val lazyState = rememberLazyListState()
    // Shared horizontal scroll state for the content area. In the default
    // no-wrap mode this lets long lines extend past the viewport and remain
    // reachable; all lines share one state so they scroll together while the
    // gutter (rendered outside the scroll region) stays fixed.
    val horizontalScrollState = rememberScrollState()

    val currentColumnItems = rememberUpdatedState(columnItems)

    // Track Alt key state for rectangular selection
    var altPressed by remember { mutableStateOf(false) }

    // Track editor layout coordinates for position mapping
    var editorCoordinates: LayoutCoordinates? by remember { mutableStateOf(null) }

    // Focus management
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the hidden text field when the editor first appears
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        platformFocusInput()
    }

    // Track whether the platform callback already handled this keydown.
    // The document-level listener fires in capture phase BEFORE Skiko
    // processes the event. If the callback handled the key, onPreviewKeyEvent
    // should skip to avoid double-handling.
    val keyProcessedByCallback = remember { booleanArrayOf(false) }

    // Register a platform-level key handler that receives ALL keydown events.
    // This is the primary input path on wasmJs because Playwright (and some
    // headless environments) dispatch key events to BODY rather than the
    // canvas in the shadow DOM, so Skiko never generates Compose KeyEvents.
    // For real users (events targeting the canvas), both this callback AND
    // onPreviewKeyEvent fire — the flag prevents double-handling.
    DisposableEffect(session) {
        val token = platformRegisterKeyHandler handler@{ key, ctrl, alt, meta, shift ->
            // Clear the flag at the start of each key event.
            // It will be set to true only if we handle this key.
            keyProcessedByCallback[0] = false
            val handled = handleRawKeyEvent(session, key, ctrl, alt, meta, shift)
            if (handled) {
                keyProcessedByCallback[0] = true
            }
            handled
        }
        onDispose {
            platformUnregisterKeyHandler(token)
        }
    }

    val density = LocalDensity.current
    val lineHeightDp = with(density) { contentStyle.lineHeight.toDp() }

    // Compute gutter width based on digit count + padding (5dp + 3dp)
    val configs = state.facet(gutters)
    val gutterWidthDp = if (hasGutters) {
        val maxDigits = state.doc.lines.toString().length
        val charWidthDp = with(density) {
            (contentStyle.fontSize.toPx() * 0.65f).toDp()
        }
        val lineNumberWidth = charWidthDp * maxDigits +
            theme.layout.gutterStartPadding + theme.layout.gutterEndPadding
        val extraGutterWidth =
            theme.layout.customGutterWidth *
                configs.count { it.type != GutterType.LineNumbers && it.lineMarker != null }
        lineNumberWidth + extraGutterWidth
    } else {
        0.dp
    }

    // Content height in px (top padding + items + bottom padding)
    val contentHeightPx = with(density) {
        (
            theme.layout.contentTopPadding + lineHeightDp * columnItems.size +
                theme.layout.contentBottomPadding
            ).toPx()
    }

    // Honor transaction.scrollIntoView: when a dispatched transaction requests
    // it, scroll the line list so the primary selection head becomes visible.
    // This drives caret-reveal on cursor moves (#33) and the search-jump reveal
    // for vim `n`/`N` and similar selection jumps (#58). Without this, an
    // off-screen target lands invisibly because the LazyColumn never scrolls.
    //
    // In no-wrap mode this also scrolls HORIZONTALLY so the caret stays in view
    // when it moves past the right or left content edge (#69), the horizontal
    // analog of the vertical reveal above.
    //
    // Observe via snapshotFlow (outside composition) rather than keying a
    // LaunchedEffect directly on the request value: running a scroll while the
    // LazyColumn is still mid-(sub)composition can trip the Compose runtime.
    val contentStartPaddingPx = with(density) { 6.dp.toPx() }
    val gutterWidthPx = with(density) { gutterWidthDp.toPx() }
    LaunchedEffect(session) {
        snapshotFlow { impl.scrollRequest }
            .collect { request ->
                if (request == null) return@collect
                val items = currentColumnItems.value
                val targetIndex = items.indexOfFirst { item ->
                    when (item) {
                        is ColumnItem.TextLine ->
                            request.target >= item.from.value &&
                                request.target <= item.to.value
                        is ColumnItem.BlockWidgetItem -> false
                    }
                }
                if (targetIndex >= 0) {
                    scrollItemIntoView(lazyState, targetIndex)

                    // Horizontal reveal (no-wrap mode only). Wrapped lines never
                    // overflow horizontally, so there is nothing to scroll.
                    if (!wrapLines) {
                        val line = items[targetIndex] as? ColumnItem.TextLine
                        val layout = line?.let {
                            textLayoutResults[it.lineNumber.value]
                        }
                        val coordsWidth = editorCoordinates?.size?.width
                        if (line != null && layout != null && coordsWidth != null) {
                            // Map the document offset within the line to the
                            // expanded-text offset (tab expansion), exactly as
                            // the cursor is drawn, then read its x in line-local
                            // pixels (0 = line start, before the 6.dp padding).
                            val offsetInLine = (request.target - line.from.value)
                                .coerceIn(0, line.to.value - line.from.value)
                            val mappedOffset = mapTabOffset(
                                offsetInLine,
                                line.tabOffsetMap
                            )
                            val cursorX = layout.getCursorRect(mappedOffset).left

                            // Content viewport width = editor width minus the
                            // gutter and the 6.dp content start padding. This
                            // mirrors posFromVisibleItems' contentStartPx so the
                            // forward (offset->x) and inverse (x->offset) maps
                            // agree.
                            val gutter = if (hasGutters) gutterWidthPx else 0f
                            val viewportPx =
                                coordsWidth - gutter - contentStartPaddingPx
                            if (viewportPx > 0f) {
                                val scroll =
                                    horizontalScrollState.value.toFloat()
                                val marginPx = 24f
                                val target = when {
                                    cursorX < scroll + marginPx ->
                                        cursorX - marginPx
                                    cursorX > scroll + viewportPx - marginPx ->
                                        cursorX - viewportPx + marginPx
                                    else -> null
                                }
                                if (target != null) {
                                    val clamped = target.coerceIn(
                                        0f,
                                        horizontalScrollState.maxValue.toFloat()
                                    )
                                    horizontalScrollState.scrollTo(
                                        clamped.roundToInt()
                                    )
                                }
                            }
                        }
                    }
                }
                impl.consumeScrollRequest(request)
            }
    }

    // Mirror the horizontal scroll offset into the session for test
    // observability (parallels lastFirstVisibleItem / lastVisibleItemCount).
    LaunchedEffect(session) {
        snapshotFlow { horizontalScrollState.value }
            .collect { impl.lastHorizontalScrollPx = it }
    }

    CompositionLocalProvider(
        LocalEditorTheme provides theme,
        LocalContentTextStyle provides contentStyle,
        LocalEditorSession provides session
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .boundUnconstrainedHeight(contentHeightPx)
        ) {
            for (panel in topPanels) {
                Box(Modifier.fillMaxWidth().background(theme.panelBackground)) {
                    panel.content(this)
                }
                Box(
                    Modifier.fillMaxWidth().height(theme.layout.panelBorderWidth)
                        .background(theme.panelBorderColor)
                )
            }
            Box(
                modifier = Modifier
                    .testTag("KodeMirror")
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        editorCoordinates = coords
                        // Process pending line layouts synchronously.
                        // onGloballyPositioned fires bottom-up: children
                        // stored their data in pendingLineLayouts because
                        // editorCoordinates was null when they fired. Now
                        // that the parent has coordinates, drain the map.
                        if (pendingLineLayouts.isNotEmpty()) {
                            val pending = pendingLineLayouts.toMap()
                            pendingLineLayouts.clear()
                            for ((_, p) in pending) {
                                if (p.coords.isAttached) {
                                    val pos = coords.localPositionOf(
                                        p.coords,
                                        Offset.Zero
                                    )
                                    lineLayoutCache.store(
                                        p.lineNumber,
                                        p.lineFrom,
                                        pos.y,
                                        pos.x,
                                        p.result
                                    )
                                }
                            }
                        }
                    }
                    .drawEditorBackground(
                        theme = theme,
                        hasGutters = hasGutters,
                        gutterWidthDp = gutterWidthDp,
                        contentHeightPx = contentHeightPx
                    )
                    .onPreviewKeyEvent { event ->
                        altPressed = event.isAltPressed
                        false
                    }
                    .pointerInput(session) {
                        // Unified tap/drag handler: always request focus on
                        // pointer down BEFORE deciding if it's a tap or drag.
                        // This fixes the race condition (issue #2) where
                        // separate tap/drag coroutines competed for the DOWN
                        // event and focus could be skipped.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            // Always focus immediately on any pointer down
                            focusRequester.requestFocus()
                            platformFocusInput()

                            val downPosition = down.position
                            var isDrag = false
                            var dragStart = downPosition
                            var dragCurrent = downPosition

                            // Try to detect if this becomes a drag
                            val slopChange = awaitTouchSlopOrCancellation(
                                down.id
                            ) { change, overSlop ->
                                change.consume()
                                isDrag = true
                                dragCurrent = change.position
                            }

                            if (slopChange != null && isDrag) {
                                // It's a drag — handle drag selection
                                dragStart = downPosition
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
                                session.plugin(dropCursorViewPlugin)
                                    ?.moveTo(
                                        session.posAtCoords(
                                            dragCurrent.x,
                                            dragCurrent.y
                                        )
                                    )

                                drag(slopChange.id) { change ->
                                    change.consume()
                                    dragCurrent = change.position
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
                                    session.plugin(dropCursorViewPlugin)
                                        ?.moveTo(
                                            session.posAtCoords(
                                                dragCurrent.x,
                                                dragCurrent.y
                                            )
                                        )
                                }
                                // Drag ended
                                session.plugin(dropCursorViewPlugin)
                                    ?.moveTo(null)
                            } else if (!isDrag) {
                                // Pointer released without exceeding slop —
                                // treat as a tap for cursor positioning.
                                val pos = posFromVisibleItems(
                                    downPosition,
                                    lazyState,
                                    columnItems,
                                    textLayoutResults,
                                    hasGutters,
                                    gutterWidthDp,
                                    density,
                                    if (wrapLines) 0 else horizontalScrollState.value
                                )
                                    ?: session.posAtCoords(
                                        downPosition.x, downPosition.y
                                    )
                                    ?: return@awaitEachGesture
                                session.dispatch(
                                    TransactionSpec(
                                        selection = SelectionSpec
                                            .CursorSpec(DocPos(pos))
                                    )
                                )
                            } else {
                                // Drag cancelled
                                session.plugin(dropCursorViewPlugin)
                                    ?.moveTo(null)
                            }
                        }
                    }
                    .pointerInput(session) {
                        awaitPointerEventScope {
                            var lastHoverDocPos: Int? = -1
                            while (true) {
                                val event = awaitPointerEvent(
                                    PointerEventPass.Main
                                )
                                val pos = event.changes
                                    .firstOrNull()?.position
                                if (pos != null) {
                                    val docPos = session.posAtCoords(pos.x, pos.y)
                                    if (docPos != lastHoverDocPos) {
                                        lastHoverDocPos = docPos
                                        val hoverTooltips =
                                            impl.pluginHost
                                                ?.collectHoverPlugins()
                                                ?: emptyList()
                                        for (plugin in hoverTooltips) {
                                            plugin.updateHoverAtPos(docPos)
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                EditorContent(
                    lazyState = lazyState,
                    focusRequester = focusRequester,
                    columnItems = columnItems,
                    hasGutters = hasGutters,
                    gutterWidthDp = gutterWidthDp,
                    lineHeightDp = lineHeightDp,
                    wrapLines = wrapLines,
                    horizontalScrollState = horizontalScrollState,
                    lineLayoutCache = lineLayoutCache,
                    pendingLineLayouts = pendingLineLayouts,
                    editorCoordinates = editorCoordinates,
                    textLayoutResults = textLayoutResults,
                    keyProcessedByCallback = keyProcessedByCallback
                )
                // Visible horizontal scrollbar for no-wrap mode (#65). Only
                // shown when there is actual horizontal overflow and line
                // wrapping is off — otherwise the long-line content clips at the
                // right edge with no affordance on the Skiko canvas.
                if (!wrapLines && horizontalScrollState.maxValue > 0) {
                    HorizontalScrollbar(
                        scrollState = horizontalScrollState,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = if (hasGutters) gutterWidthDp else 0.dp)
                    )
                }
            }
            for (panel in bottomPanels) {
                Box(
                    Modifier.fillMaxWidth().height(theme.layout.panelBorderWidth)
                        .background(theme.panelBorderColor)
                )
                Box(Modifier.fillMaxWidth().background(theme.panelBackground)) {
                    panel.content(this)
                }
            }
        }
    }
}

/** Inner content of the editor: hidden text field, line list, and tooltip layer. */
@Composable
private fun EditorContent(
    lazyState: LazyListState,
    focusRequester: FocusRequester,
    columnItems: List<ColumnItem>,
    hasGutters: Boolean,
    gutterWidthDp: Dp,
    lineHeightDp: Dp,
    wrapLines: Boolean,
    horizontalScrollState: ScrollState,
    lineLayoutCache: LineLayoutCache,
    pendingLineLayouts: MutableMap<Int, PendingLineLayout>,
    editorCoordinates: LayoutCoordinates?,
    textLayoutResults: MutableMap<Int, TextLayoutResult>,
    keyProcessedByCallback: BooleanArray
) {
    val session = LocalEditorSession.current
    val impl = session as EditorSessionImpl
    val state by session::state
    val theme = LocalEditorTheme.current
    val contentStyle = LocalContentTextStyle.current
    // Shared widest-line width: every no-wrap line is measured to this min so
    // all per-line horizontalScroll modifiers share one consistent scroll
    // range. Without it, short lines (laid out last) clobber the shared
    // ScrollState.maxValue to 0, breaking scrolling and the scrollbar (#67).
    val maxLineWidthPx = remember { mutableStateOf(0) }
    // Laid-out width of the per-line viewport (the weight(1f) box). In no-wrap
    // mode the content box scrolls horizontally, so it is measured with an
    // infinite max width and cannot learn the viewport width itself. We capture
    // the viewport's actual width here so a short line's content (and thus its
    // line-decoration background highlight) can fill the full viewport width
    // rather than stopping at the text width (#85). The viewport width is
    // weight-determined (independent of content width), so feeding it back into
    // the content min-width is loop-safe.
    val viewportWidthPx = remember { mutableStateOf(0) }
    val density = LocalDensity.current

    // Hidden text field for receiving IME/text input and key events
    var hiddenTextValue by remember {
        mutableStateOf(TextFieldValue(""))
    }
    // Flag to suppress onValueChange when onPreviewKeyEvent consumed the key.
    // On wasmJs, returning true from onPreviewKeyEvent does NOT call
    // preventDefault() on the DOM event, so the browser still generates an
    // input event that triggers onValueChange. This flag bridges the gap.
    val suppressInput = remember { booleanArrayOf(false) }
    BasicTextField(
        value = hiddenTextValue,
        cursorBrush = SolidColor(Color.Transparent),
        onValueChange = { newValue ->
            if (suppressInput[0]) {
                suppressInput[0] = false
                hiddenTextValue = TextFieldValue("")
                return@BasicTextField
            }
            // Block text input when editor is read-only
            if (!session.editable) {
                hiddenTextValue = TextFieldValue("")
                return@BasicTextField
            }
            // Check if a vim-like extension wants to suppress text input.
            val shouldSuppress = session.state.facet(inputSuppressor)
                .any { it.invoke() }
            if (shouldSuppress) {
                hiddenTextValue = TextFieldValue("")
                return@BasicTextField
            }
            // Filter control characters (Tab, etc.) that leak through
            // when their key events aren't consumed by the keymap.
            // Preserve newlines — they are valid input (e.g. paste).
            val inserted = newValue.text.filter {
                it == '\n' || !it.isISOControl()
            }
            if (inserted.isNotEmpty()) {
                val sel = session.state.selection.main
                val from = sel.from
                val to = sel.to
                val newCursor = DocPos(from.value + inserted.length)
                session.dispatch(
                    TransactionSpec(
                        changes = ChangeSpec.Single(
                            from = from,
                            to = to,
                            insert = inserted.asInsert()
                        ),
                        selection = SelectionSpec.CursorSpec(newCursor),
                        userEvent = "input.type"
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
                // If the document-level callback already handled this
                // keydown, skip to avoid double-handling.
                if (event.type == KeyEventType.KeyDown &&
                    keyProcessedByCallback[0]
                ) {
                    keyProcessedByCallback[0] = false
                    suppressInput[0] = true
                    return@onPreviewKeyEvent true
                }
                val consumed = handleKeyEvent(session, event)
                if (consumed) {
                    suppressInput[0] = true
                } else if (event.type == KeyEventType.KeyDown && session.editable) {
                    // When the keymap doesn't consume a printable character,
                    // insert it directly. On wasmJs with canvas focus, the
                    // browser doesn't generate a text input event on the
                    // BasicTextField's backing element, so onValueChange
                    // won't fire. This bridges the gap.
                    // Don't insert for modified keys (Ctrl+A, Alt+X, etc.)
                    // — those are shortcuts, not text input.
                    val char = keyEventLayoutKey(event)
                        ?: keyEventCharacter(event)?.toString()
                    if (char != null && char.length == 1 &&
                        !char[0].isISOControl() &&
                        !event.isCtrlPressed &&
                        !event.isMetaPressed &&
                        !event.isAltPressed
                    ) {
                        val sel = session.state.selection.main
                        val newCursor = DocPos(sel.from.value + char.length)
                        session.dispatch(
                            TransactionSpec(
                                changes = ChangeSpec.Single(
                                    from = sel.from,
                                    to = sel.to,
                                    insert = char.asInsert()
                                ),
                                selection = SelectionSpec.CursorSpec(newCursor),
                                userEvent = "input.type"
                            )
                        )
                        suppressInput[0] = true
                        return@onPreviewKeyEvent true
                    }
                }
                consumed
            }
    )

    LazyColumn(
        state = lazyState,
        contentPadding = PaddingValues(
            top = theme.layout.contentTopPadding,
            bottom = theme.layout.contentBottomPadding
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
                        mutableStateOf<TextLayoutResult?>(null)
                    }

                    // In no-wrap mode (CM6 default) each line occupies a single
                    // visual row of fixed height. In wrap mode the line may grow
                    // to multiple rows, so only pin a minimum height.
                    val lineModifier: Modifier = if (wrapLines) {
                        Modifier.fillMaxWidth().heightIn(min = lineHeightDp)
                    } else {
                        Modifier.fillMaxWidth().height(lineHeightDp)
                    }
                    var contentExtraModifier: Modifier = Modifier
                        .padding(start = 6.dp, end = 2.dp)
                    var hasLineBackground = false
                    for (deco in item.lineDecorations) {
                        val bg = deco.spec.style?.background
                        if (bg != null && bg != Color.Unspecified) {
                            contentExtraModifier = contentExtraModifier.background(bg)
                            hasLineBackground = true
                        }
                    }
                    // Test hook (#85): record the laid-out width of a
                    // background-highlighted line's content box so the
                    // regression test can assert the highlight fills the
                    // viewport in no-wrap mode rather than stopping at the text.
                    if (hasLineBackground && !wrapLines) {
                        contentExtraModifier = contentExtraModifier.onSizeChanged {
                            impl.lastActiveLineContentWidthPx = it.width
                        }
                    }
                    Row(
                        modifier = lineModifier,
                        // On a wrapped (multi-row) line, top-align so the gutter
                        // line number sits beside the first visual row rather
                        // than being centered across all rows.
                        verticalAlignment = if (wrapLines) {
                            Alignment.Top
                        } else {
                            Alignment.CenterVertically
                        }
                    ) {
                        if (hasGutters) {
                            GutterView(
                                session = session,
                                lineNumber = item.lineNumber.value,
                                modifier = Modifier.width(gutterWidthDp)
                            )
                        }
                        // Viewport box: fills the remaining width and clips to
                        // it. In no-wrap mode it scrolls horizontally so long
                        // lines stay reachable; the gutter (outside this box)
                        // stays fixed. In wrap mode there is no horizontal
                        // scroll — lines wrap to fit the width instead.
                        var viewportModifier: Modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                        if (!wrapLines) {
                            // Capture the viewport's actual visible width from
                            // the weight(1f) box (before the horizontalScroll,
                            // which would otherwise measure the child with an
                            // infinite max width). Used as an additional content
                            // min-width so highlights fill the viewport (#85).
                            viewportModifier = viewportModifier
                                .onSizeChanged { viewportWidthPx.value = it.width }
                                .horizontalScroll(horizontalScrollState)
                        }
                        Box(modifier = viewportModifier) {
                            // Content box holds the actual text at its natural
                            // width (no-wrap) or constrained to the viewport
                            // width (wrap). Selection overlay and layout capture
                            // live here so the recorded left offset already
                            // accounts for the horizontal scroll position,
                            // letting click->offset resolve across the full line.
                            var contentModifier: Modifier = Modifier
                                .fillMaxHeight()
                            contentModifier = if (wrapLines) {
                                contentModifier.fillMaxWidth()
                            } else {
                                // Min width = the wider of the widest line and
                                // the viewport. The first keeps long lines at
                                // their full natural width (uniform scroll range,
                                // #68); the second makes a short line's content
                                // (and its line-decoration background) fill the
                                // viewport instead of stopping at the text (#85).
                                val minW = with(density) {
                                    maxOf(
                                        maxLineWidthPx.value,
                                        viewportWidthPx.value
                                    ).toDp()
                                }
                                contentModifier
                                    .wrapContentWidth(
                                        align = Alignment.Start,
                                        unbounded = true
                                    )
                                    .widthIn(min = minW)
                            }
                            Box(
                                modifier = contentModifier
                                    .then(contentExtraModifier)
                                    .drawSelectionOverlay(
                                        state,
                                        item.from.value,
                                        item.to.value,
                                        theme,
                                        textLayout,
                                        item.tabOffsetMap
                                    )
                                    .onGloballyPositioned { contentCoords ->
                                        val layout = textLayout
                                            ?: return@onGloballyPositioned
                                        val editorCoords = editorCoordinates
                                        if (editorCoords != null &&
                                            editorCoords.isAttached
                                        ) {
                                            val pos = editorCoords.localPositionOf(
                                                contentCoords,
                                                Offset.Zero
                                            )
                                            lineLayoutCache.store(
                                                capturedLineNum.value,
                                                capturedFrom.value,
                                                pos.y,
                                                pos.x,
                                                layout
                                            )
                                            pendingLineLayouts
                                                .remove(capturedLineNum.value)
                                        } else {
                                            pendingLineLayouts[capturedLineNum.value] =
                                                PendingLineLayout(
                                                    capturedLineNum.value,
                                                    capturedFrom.value,
                                                    contentCoords,
                                                    layout
                                                )
                                        }
                                    }
                            ) {
                                BasicText(
                                    text = item.content,
                                    style = contentStyle,
                                    softWrap = wrapLines,
                                    onTextLayout = { result: TextLayoutResult ->
                                        textLayout = result
                                        textLayoutResults[capturedLineNum.value] =
                                            result
                                        if (!wrapLines &&
                                            result.size.width > maxLineWidthPx.value
                                        ) {
                                            maxLineWidthPx.value = result.size.width
                                        }
                                    }
                                )
                                for (widget in item.inlineWidgets) {
                                    widget.spec.widget.Content()
                                }
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

    // Sync viewport/height tracking for ViewUpdate flags.
    // Evict stale cache entries for scrolled-off lines.
    // IMPORTANT: lazyState.layoutInfo must NOT be read during
    // composition — it produces a new object every layout pass,
    // creating an infinite recomposition/layout cycle. Use
    // snapshotFlow to observe it outside of composition.
    val firstVisible = lazyState.firstVisibleItemIndex
    LaunchedEffect(firstVisible) {
        impl.lastFirstVisibleItem = firstVisible
    }
    LaunchedEffect(session) {
        snapshotFlow {
            lazyState.layoutInfo.visibleItemsInfo.let { items ->
                items.firstOrNull()?.index to items.size
            }
        }.collect { (startIdx, count) ->
            val startIndex = startIdx ?: 0
            impl.lastFirstVisibleItem = startIndex
            impl.lastVisibleItemCount = count
            val visibleLineNumbers = columnItems
                .drop(startIndex)
                .take(count.coerceAtLeast(1))
                .filterIsInstance<ColumnItem.TextLine>()
                .map { it.lineNumber.value }
                .toSet()
            if (visibleLineNumbers.isNotEmpty()) {
                lineLayoutCache.evict(visibleLineNumbers)
            }
        }
    }

    // Tooltip layer
    TooltipLayer(session = session)
}

/**
 * A custom horizontal scrollbar built entirely from commonMain Compose
 * primitives so it compiles on every target (jvm, android, wasmJs, native).
 *
 * The desktop/skiko `HorizontalScrollbar` is NOT usable here because the
 * androidMain source set depends on jvmMain, and that API does not exist on
 * Android. This draws a thin track with a draggable thumb instead.
 *
 * Thumb sizing: the visible fraction of the content is
 * `viewportPx / (viewportPx + scrollState.maxValue)`, so the thumb width is
 * `trackWidth * viewportFraction`. The thumb offset maps the scroll position
 * onto the remaining track travel: `(value / maxValue) * (trackWidth - thumb)`.
 *
 * Dragging maps thumb-track pixels back to content pixels by the inverse ratio
 * `(trackWidth - thumb)` of travel ↔ `maxValue` of scroll, so a full thumb
 * sweep scrolls the full content.
 */
@Composable
private fun HorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val theme = LocalEditorTheme.current
    val scope = rememberCoroutineScope()
    val thumbColor = theme.foreground.copy(alpha = 0.35f)
    val trackColor = theme.foreground.copy(alpha = 0.08f)

    var trackWidthPx by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .testTag("KodeMirror_hscroll")
            .height(10.dp)
            .background(trackColor)
            .onSizeChanged { trackWidthPx = it.width }
    ) {
        val maxValue = scrollState.maxValue
        val trackWidth = trackWidthPx.toFloat()
        if (trackWidth > 0f && maxValue > 0) {
            // Visible fraction of the content -> thumb length as a fraction of
            // the track. The total content width in px is viewport + maxValue.
            val viewportFraction = trackWidth / (trackWidth + maxValue)
            val minThumbPx = with(LocalDensity.current) { 24.dp.toPx() }
            val thumbWidthPx = (trackWidth * viewportFraction)
                .coerceIn(minThumbPx.coerceAtMost(trackWidth), trackWidth)
            val travel = trackWidth - thumbWidthPx
            val thumbOffsetPx = if (maxValue > 0 && travel > 0f) {
                (scrollState.value.toFloat() / maxValue) * travel
            } else {
                0f
            }
            val density = LocalDensity.current
            val thumbWidthDp = with(density) { thumbWidthPx.toDp() }

            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                    .width(thumbWidthDp)
                    .height(8.dp)
                    .padding(vertical = 1.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(thumbColor)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { deltaPx ->
                            if (travel > 0f) {
                                // Map thumb-track pixels back to content
                                // pixels: full travel == full maxValue scroll.
                                val scrollDelta = deltaPx * (maxValue / travel)
                                scope.launch {
                                    scrollState.scrollBy(scrollDelta)
                                }
                            }
                        }
                    )
            )
        }
    }
}

/**
 * Guard the editor [Column] against an unbounded (`Infinity`) incoming
 * `maxHeight` (#33).
 *
 * The editor fills the height it is given. Under a bounded height this is a
 * no-op — the original constraints pass through unchanged and the inner line
 * list scrolls. But under an unbounded vertical constraint (a parent with
 * `Modifier.verticalScroll(...)`, `wrapContentHeight()`, or another scrolling
 * container) two things break:
 *  - `Column` `weight(1f)` distributes the *remaining* (infinite) space, which
 *    collapses the content Box — and the editor — to zero height; and
 *  - the inner `LazyColumn` throws, because a vertically-scrollable component
 *    may not be measured with an infinite `maxHeight`.
 *
 * To degrade gracefully we replace an infinite `maxHeight` with the document's
 * natural content height ([contentHeightPx], top/bottom padding + all lines).
 * The editor then grows to fit the whole document and the surrounding scroll
 * container scrolls it, instead of collapsing or crashing.
 */
private fun Modifier.boundUnconstrainedHeight(contentHeightPx: Float): Modifier =
    layout { measurable, constraints ->
        val effective = if (constraints.maxHeight == Constraints.Infinity) {
            constraints.copy(
                maxHeight = contentHeightPx.roundToInt().coerceAtLeast(0)
            )
        } else {
            constraints
        }
        val placeable = measurable.measure(effective)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }

/** Draw editor and gutter backgrounds behind content. */
private fun Modifier.drawEditorBackground(
    theme: EditorTheme,
    hasGutters: Boolean,
    gutterWidthDp: Dp,
    contentHeightPx: Float
): Modifier = drawWithContent {
    drawRect(theme.background)
    if (hasGutters) {
        val w = gutterWidthDp.toPx()
        val contentH = contentHeightPx.coerceAtMost(size.height)
        drawRect(
            color = theme.gutterBackground,
            topLeft = Offset.Zero,
            size = Size(w, contentH)
        )
        val bc = theme.gutterBorderColor
        if (bc != Color.Transparent) {
            drawLine(
                color = bc,
                start = Offset(w - 0.5f, 0f),
                end = Offset(w - 0.5f, contentH),
                strokeWidth = 1f
            )
        }
    }
    drawContent()
}

/**
 * Create and remember an [EditorSession] with the given document text and extensions.
 *
 * **Initial-value-only parameters:** [doc] and [extensions] are used only when the session
 * is first created. Subsequent recompositions with different values have no effect — the
 * session retains the state from its initial creation, including cursor position, selection,
 * and undo history.
 *
 * This matches CodeMirror 6's model, where an [EditorState] (and the view that owns it) is
 * created once and then mutated exclusively through transactions. To update the document or
 * reconfigure extensions after creation, dispatch a transaction via [EditorSession.dispatch]:
 *
 * ```kotlin
 * // Update document content
 * session.dispatch(TransactionSpec(changes = ChangeSpec.Single(0, session.state.doc.length, newDoc.asInsert())))
 *
 * // Reconfigure extensions
 * session.dispatch(TransactionSpec(effects = listOf(StateEffect.reconfigure(newExtensions))))
 * ```
 *
 * @param doc        Initial document text. Ignored after first composition.
 * @param extensions Initial set of extensions. Ignored after first composition.
 * @param onUpdate   Callback invoked for every transaction dispatched to the session.
 */
@Composable
fun rememberEditorSession(
    doc: String = "",
    extensions: Extension? = null,
    onUpdate: (Transaction) -> Unit = {}
): EditorSession {
    return remember {
        val config = EditorStateConfig(
            doc = doc.asDoc(),
            extensions = extensions
        )
        EditorSession(EditorState.create(config), onUpdate)
    }
}

/**
 * Create and remember an [EditorSession] from an [EditorStateConfig].
 *
 * **Initial-value-only parameter:** [config] is used only when the session is first created.
 * Subsequent recompositions with a different [config] have no effect — the session retains
 * the state from its initial creation, including cursor position, selection, and undo history.
 *
 * This matches CodeMirror 6's model, where an [EditorState] (and the view that owns it) is
 * created once and then mutated exclusively through transactions. To update the document or
 * reconfigure extensions after creation, dispatch a transaction via [EditorSession.dispatch].
 *
 * @param config   Initial editor state configuration. Ignored after first composition.
 * @param onUpdate Callback invoked for every transaction dispatched to the session.
 */
@Composable
fun rememberEditorSession(
    config: EditorStateConfig,
    onUpdate: (Transaction) -> Unit = {}
): EditorSession {
    return remember { EditorSession(EditorState.create(config), onUpdate) }
}

/**
 * Scroll [lazyState] the minimal amount needed to bring [targetIndex] fully
 * into the visible viewport.
 *
 * Mirrors CodeMirror 6's `scrollIntoView` "nearest" behavior: if the item is
 * already fully visible, do nothing; if it is above the viewport, align it to
 * the top; if it is below the viewport, align it to the bottom. Items larger
 * than the viewport are aligned to the top so their start is visible.
 */
internal suspend fun scrollItemIntoView(lazyState: LazyListState, targetIndex: Int) {
    val layoutInfo = lazyState.layoutInfo
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) {
        // Nothing laid out yet (e.g. first frame). Best-effort: jump to the item.
        lazyState.scrollToItem(targetIndex)
        return
    }
    // Bounds of the usable viewport (inside content padding).
    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset
    val viewportHeight = viewportEnd - viewportStart

    val existing = visible.firstOrNull { it.index == targetIndex }
    val firstVisibleIndex = visible.first().index

    // Decide whether the target is above the viewport, below it, or already
    // visible. For an item that is laid out, use its measured bounds; for one
    // scrolled off-screen, infer direction from its index.
    val above: Boolean
    val below: Boolean
    if (existing != null) {
        above = existing.offset < viewportStart
        below = existing.offset + existing.size > viewportEnd
    } else {
        above = targetIndex < firstVisibleIndex
        below = !above
    }

    when {
        // Above the viewport (or fully off-screen above): align top edge to
        // the viewport top.
        above -> lazyState.scrollToItem(targetIndex)
        // Below the viewport (or off-screen below): bring the item to the top
        // first (guarantees it is laid out and visible), then, if it fits with
        // room to spare, scroll backward so it sits at the viewport bottom and
        // the preceding context above the caret stays on screen — matching
        // CM6's "nearest" alignment.
        below -> {
            lazyState.scrollToItem(targetIndex)
            val now = lazyState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == targetIndex }
            if (now != null) {
                val slack = (viewportHeight - now.size).toFloat()
                if (slack > 0f) {
                    // scrollBy(negative) moves content down, revealing earlier
                    // items above and pushing the target toward the bottom.
                    lazyState.scrollBy(-slack)
                }
            }
        }
        // else: already fully visible, nothing to do.
    }
}

/** Layout info saved when [onGloballyPositioned] fires before the parent. */
private class PendingLineLayout(
    val lineNumber: Int,
    val lineFrom: Int,
    val coords: LayoutCoordinates,
    val result: TextLayoutResult
)

/**
 * Compute a document position from a tap offset using the [LazyColumn]'s own
 * layout info. This is reliable even when [LineLayoutCache] hasn't been
 * populated yet (first render / page switch).
 */
private fun posFromVisibleItems(
    offset: Offset,
    lazyState: LazyListState,
    columnItems: List<ColumnItem>,
    textLayoutResults: Map<Int, TextLayoutResult>,
    hasGutters: Boolean,
    gutterWidthDp: Dp,
    density: Density,
    horizontalScrollPx: Int
): Int? {
    val contentStartPx = with(density) {
        (if (hasGutters) gutterWidthDp.toPx() else 0f) + 6.dp.toPx()
    }
    for (info in lazyState.layoutInfo.visibleItemsInfo) {
        val item = columnItems.getOrNull(info.index)
            as? ColumnItem.TextLine ?: continue
        val itemTop = info.offset.toFloat()
        val itemBottom = itemTop + info.size.toFloat()
        if (offset.y >= itemTop && offset.y < itemBottom) {
            val layout = textLayoutResults[item.lineNumber.value]
                ?: return item.from.value // no layout yet, return line start
            val localY = offset.y - itemTop
            // Add the horizontal scroll offset: the click x is in viewport
            // space, but the text is shifted left by the scroll amount, so the
            // true position within the line is x - contentStart + scroll.
            val localX = (offset.x - contentStartPx + horizontalScrollPx)
                .coerceAtLeast(0f)
            val expandedOffset = layout.getOffsetForPosition(
                Offset(localX, localY)
            )
            val charOffset = unmapTabOffset(
                expandedOffset,
                item.tabOffsetMap
            )
            return item.from.value +
                charOffset.coerceIn(0, item.to.value - item.from.value)
        }
    }
    return null
}
