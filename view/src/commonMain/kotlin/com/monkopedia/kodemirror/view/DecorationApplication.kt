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

import androidx.compose.ui.text.AnnotatedString
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.SpanIterator

/**
 * Represents one item in the LazyColumn that renders the editor content.
 */
sealed class ColumnItem {
    /**
     * A line of document text, rendered as an [AnnotatedString].
     *
     * @param lineNumber 1-based line number.
     * @param from       Start position in the document.
     * @param to         End position (exclusive) in the document.
     * @param content    The annotated text for this line.
     */
    data class TextLine(
        val lineNumber: Int,
        val from: Int,
        val to: Int,
        val content: AnnotatedString,
        val lineDecorations: List<LineDecoration> = emptyList()
    ) : ColumnItem()

    /**
     * A block widget placed before, after, or instead of a line.
     *
     * @param from   Document position where the widget is anchored.
     * @param widget The widget to render.
     * @param type   Whether this is before/after/replacing a line.
     */
    data class BlockWidgetItem(
        val from: Int,
        val widget: WidgetDecoration,
        val type: BlockType = BlockType.WidgetBefore
    ) : ColumnItem()
}

/**
 * Build an [AnnotatedString] for one document line, applying [MarkDecoration]s
 * from [decorationSets].
 *
 * @param line           The [com.monkopedia.kodemirror.state.Line] to render.
 * @param decorationSets Active decoration sets.
 */
fun buildLineContent(
    lineFrom: Int,
    lineTo: Int,
    lineText: String,
    decorationSets: List<DecorationSet>
): AnnotatedString {
    val builder = AnnotatedString.Builder(lineText)

    // Collect all MarkDecorations that overlap this line
    for (set in decorationSets) {
        set.between(lineFrom, lineTo) { from, to, value ->
            if (value is MarkDecoration && value.spec.style != null) {
                val startInLine = (from - lineFrom).coerceIn(0, lineText.length)
                val endInLine = (to - lineFrom).coerceIn(0, lineText.length)
                if (startInLine < endInLine) {
                    builder.addStyle(value.spec.style, startInLine, endInLine)
                }
            }
            null // continue iteration
        }
    }

    return builder.toAnnotatedString()
}

/**
 * Build the complete list of [ColumnItem]s for the visible document range.
 *
 * Iterates line-by-line, applying decorations, and inserts block widgets
 * at the appropriate positions.
 *
 * @param state         Current editor state.
 * @param viewport      The currently visible portion of the document.
 * @param decorationSets All active decoration sets.
 */
fun buildColumnItems(
    state: EditorState,
    viewport: Viewport,
    decorationSets: List<DecorationSet>
): List<ColumnItem> {
    val items = mutableListOf<ColumnItem>()
    val doc = state.doc

    // Collect block widgets indexed by document position
    val blockWidgetsBefore = mutableMapOf<Int, MutableList<WidgetDecoration>>()
    val blockWidgetsAfter = mutableMapOf<Int, MutableList<WidgetDecoration>>()
    for (set in decorationSets) {
        set.between(viewport.from, viewport.to) { from, _, value ->
            if (value is WidgetDecoration && value.spec.block) {
                val list = if (value.spec.side >= 0) {
                    blockWidgetsAfter.getOrPut(from) { mutableListOf() }
                } else {
                    blockWidgetsBefore.getOrPut(from) { mutableListOf() }
                }
                list.add(value)
            }
            null
        }
    }

    // Collect line decorations
    val lineDecsByLine = mutableMapOf<Int, MutableList<LineDecoration>>()
    for (set in decorationSets) {
        set.between(viewport.from, viewport.to) { from, _, value ->
            if (value is LineDecoration) {
                val line = doc.lineAt(from)
                lineDecsByLine.getOrPut(line.number) { mutableListOf() }.add(value)
            }
            null
        }
    }

    // Walk visible lines
    var lineNum = doc.lineAt(viewport.from).number
    val lastLine = doc.lineAt(viewport.to.coerceAtMost(doc.length)).number

    while (lineNum <= lastLine) {
        val line = doc.line(lineNum)

        // Emit block-before widgets
        blockWidgetsBefore[line.from]?.forEach { w ->
            items.add(ColumnItem.BlockWidgetItem(line.from, w, BlockType.WidgetBefore))
        }

        // Emit the text line
        val content = buildLineContent(line.from, line.to, line.text, decorationSets)
        val lineDecos = lineDecsByLine[lineNum] ?: emptyList()
        items.add(ColumnItem.TextLine(lineNum, line.from, line.to, content, lineDecos))

        // Emit block-after widgets
        blockWidgetsAfter[line.from]?.forEach { w ->
            items.add(ColumnItem.BlockWidgetItem(line.from, w, BlockType.WidgetAfter))
        }

        lineNum++
    }

    return items
}

/**
 * Internal span iterator used for building annotated strings via
 * [RangeSet.spans].
 */
private class MarkSpanIterator(
    private val lineFrom: Int,
    private val lineLength: Int,
    private val builder: AnnotatedString.Builder
) : SpanIterator<Decoration> {
    override fun span(from: Int, to: Int, active: List<Decoration>, openStart: Int) {
        val startInLine = (from - lineFrom).coerceIn(0, lineLength)
        val endInLine = (to - lineFrom).coerceIn(0, lineLength)
        if (startInLine >= endInLine) return

        for (dec in active) {
            if (dec is MarkDecoration) {
                dec.spec.style?.let { style ->
                    builder.addStyle(style, startInLine, endInLine)
                }
            }
        }
    }

    override fun point(
        from: Int,
        to: Int,
        value: Decoration,
        active: List<Decoration>,
        openStart: Int,
        index: Int
    ) {
        // Inline widgets are handled separately by the composable
    }
}
