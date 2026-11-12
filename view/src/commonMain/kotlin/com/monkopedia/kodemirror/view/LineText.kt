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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * The text of one editor line, with its inline widgets drawn at their
 * document positions.
 *
 * [text] holds a placeholder character for each widget (see
 * [buildLineContentWithTabs]), so text layout reserves the widget's width at
 * its position and the caret, selection and hit-testing account for it. Each
 * widget is measured first and its placeholder sized to match; the widget is
 * then placed at its placeholder's rectangle, so it is composed only once.
 *
 * A placeholder is never taller than the line, so a tall widget overflows the
 * line downward instead of growing it: lines keep the fixed height the editor's
 * scrolling relies on.
 */
@Composable
internal fun LineText(
    text: AnnotatedString,
    inlineWidgets: List<WidgetDecoration>,
    style: TextStyle,
    softWrap: Boolean,
    onTextLayout: (TextLayoutResult) -> Unit
) {
    if (inlineWidgets.isEmpty()) {
        BasicText(text = text, style = style, softWrap = softWrap, onTextLayout = onTextLayout)
        return
    }
    val cache = remember { InlineContentCache() }
    SubcomposeLayout { constraints ->
        val widgetConstraints = Constraints(maxWidth = constraints.maxWidth)
        val widgets = subcompose(LineTextSlot.Widgets) {
            for (widget in inlineWidgets) {
                Box { widget.spec.widget.Content() }
            }
        }.map { it.measure(widgetConstraints) }

        val maxHeightPx = if (style.lineHeight.isSp) style.lineHeight.toPx() else Float.MAX_VALUE
        val sizes = widgets.map { IntSize(it.width, it.height) }
        if (sizes != cache.sizes) {
            cache.sizes = sizes
            cache.content = sizes.withIndex().associate { (i, size) ->
                inlineWidgetId(i) to InlineTextContent(
                    Placeholder(
                        width = size.width.toSp(),
                        height = size.height.toFloat().coerceAtMost(maxHeightPx).toSp(),
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    ),
                    // The widget itself is placed below, not composed in here.
                    ::NoInlineContent
                )
            }
        }
        val inlineContent = cache.content
        val textPlaceable = subcompose(LineTextSlot.Text) {
            BasicText(
                text = text,
                style = style,
                softWrap = softWrap,
                inlineContent = inlineContent,
                onTextLayout = {
                    cache.layout = it
                    onTextLayout(it)
                }
            )
        }.single().measure(constraints)

        layout(textPlaceable.width, textPlaceable.height) {
            textPlaceable.place(0, 0)
            val rects = cache.layout?.placeholderRects.orEmpty()
            widgets.forEachIndexed { i, widget ->
                val rect = rects.getOrNull(i) ?: return@forEachIndexed
                widget.place(rect.left.roundToInt(), rect.top.roundToInt())
            }
        }
    }
}

private enum class LineTextSlot { Widgets, Text }

@Suppress("UNUSED_PARAMETER")
@Composable
private fun NoInlineContent(id: String) {}

/** Inline content for the last measured widget sizes, and the text's latest layout. */
private class InlineContentCache {
    var sizes: List<IntSize>? = null
    var content: Map<String, InlineTextContent> = emptyMap()
    var layout: TextLayoutResult? = null
}
