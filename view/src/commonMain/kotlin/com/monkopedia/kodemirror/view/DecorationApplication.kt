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

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.LineNumber

/**
 * Represents one item in the LazyColumn that renders the editor content.
 */
internal sealed class ColumnItem {
    /**
     * A line of document text, rendered as an [AnnotatedString].
     *
     * @param lineNumber 1-based line number.
     * @param from       Start position in the document.
     * @param to         End position (exclusive) in the document.
     * @param content    The annotated text for this line.
     */
    data class TextLine(
        val lineNumber: LineNumber,
        val from: DocPos,
        val to: DocPos,
        val content: AnnotatedString,
        val lineDecorations: List<LineDecoration> = emptyList(),
        /**
         * Inline widgets of this line in rendered order. Widget `i` is drawn
         * at the placeholder `inlineWidgetId(i)` in [content].
         */
        val inlineWidgets: List<WidgetDecoration> = emptyList(),
        /**
         * Mapping between document offsets (relative to line start) and
         * offsets in [content]. `null` when the two coincide (no tabs and no
         * inline widgets in the line).
         */
        val offsetMap: LineOffsetMap? = null
    ) : ColumnItem() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TextLine) return false
            return lineNumber == other.lineNumber &&
                from == other.from &&
                to == other.to &&
                content == other.content &&
                lineDecorations == other.lineDecorations &&
                inlineWidgets == other.inlineWidgets &&
                offsetMap == other.offsetMap
        }

        override fun hashCode(): Int {
            var result = lineNumber.hashCode()
            result = 31 * result + from.hashCode()
            result = 31 * result + to.hashCode()
            result = 31 * result + content.hashCode()
            result = 31 * result + lineDecorations.hashCode()
            result = 31 * result + inlineWidgets.hashCode()
            result = 31 * result + (offsetMap?.hashCode() ?: 0)
            return result
        }
    }

    /**
     * A block widget placed before, after, or instead of a line.
     *
     * @param from   Document position where the widget is anchored.
     * @param widget The widget to render.
     * @param type   Whether this is before/after/replacing a line.
     */
    data class BlockWidgetItem(
        val from: DocPos,
        val widget: WidgetDecoration,
        val type: BlockType = BlockType.WidgetBefore
    ) : ColumnItem()
}

/**
 * Maps between document offsets within a line and offsets in the line's
 * rendered text, which differ when tabs are expanded to spaces or inline
 * widgets are inserted as placeholder characters.
 *
 * @param toRendered Index `i` is the rendered offset of the caret at
 *   document offset `i`. Widgets with `side <= 0` at `i` lie before it, those
 *   with `side > 0` after it, as in upstream CodeMirror.
 * @param toDoc Index `r` is the document offset for rendered offset `r`. An
 *   offset inside a widget resolves to the widget's position; one inside an
 *   expanded tab resolves to the tab.
 */
internal class LineOffsetMap(val toRendered: IntArray, val toDoc: IntArray) {
    override fun equals(other: Any?): Boolean = other is LineOffsetMap &&
        toRendered.contentEquals(other.toRendered) &&
        toDoc.contentEquals(other.toDoc)

    override fun hashCode(): Int = 31 * toRendered.contentHashCode() + toDoc.contentHashCode()
}

/**
 * Convert a document-relative offset into the rendered-text offset. Returns
 * [docOffset] unchanged when [offsetMap] is `null`. Mirrors the forward
 * mapping used when drawing the cursor/selection.
 */
internal fun toRenderedOffset(docOffset: Int, offsetMap: LineOffsetMap?): Int {
    if (offsetMap == null) return docOffset
    val map = offsetMap.toRendered
    return map[docOffset.coerceIn(0, map.size - 1)]
}

/**
 * Convert a rendered-text offset back to the document-relative offset.
 * Returns [renderedOffset] unchanged when [offsetMap] is `null`.
 */
internal fun toDocOffset(renderedOffset: Int, offsetMap: LineOffsetMap?): Int {
    if (offsetMap == null) return renderedOffset
    val map = offsetMap.toDoc
    return map[renderedOffset.coerceIn(0, map.size - 1)]
}

/** Placeholder id of the [index]th inline widget of a line. */
internal fun inlineWidgetId(index: Int): String = "kodemirror-widget-$index"

/** An inline widget anchored [offset] characters into its line. */
internal class LineWidget(val offset: Int, val widget: WidgetDecoration)

/**
 * Result of building line content with tab expansion info.
 */
internal data class LineContentResult(val content: AnnotatedString, val offsetMap: LineOffsetMap?)

/**
 * Build an [AnnotatedString] for one document line, applying [MarkDecoration]s
 * from [decorationSets].
 *
 * @param lineFrom       Start position of the line in the document.
 * @param lineTo         End position of the line in the document.
 * @param lineText       The text content of the line.
 * @param decorationSets Active decoration sets.
 * @param tabSize        Number of spaces per tab stop (default 4).
 */
internal fun buildLineContent(
    lineFrom: DocPos,
    lineTo: DocPos,
    lineText: String,
    decorationSets: List<DecorationSet>,
    tabSize: Int = 4
): AnnotatedString = buildLineContentWithTabs(
    lineFrom,
    lineTo,
    lineText,
    decorationSets,
    tabSize
).content

/**
 * Build line content with tab expansion and inline widget placeholders,
 * returning both the annotated string and the offset mapping for
 * selection/cursor positioning.
 *
 * @param widgets Inline widgets on this line, sorted by offset and then by
 *   side (upstream order). Widget `i` becomes placeholder [inlineWidgetId]`(i)`.
 */
internal fun buildLineContentWithTabs(
    lineFrom: DocPos,
    lineTo: DocPos,
    lineText: String,
    decorationSets: List<DecorationSet>,
    tabSize: Int = 4,
    widgets: List<LineWidget> = emptyList()
): LineContentResult {
    val builder = AnnotatedString.Builder()
    val offsetMap: LineOffsetMap?
    if ('\t' !in lineText && widgets.isEmpty()) {
        builder.append(lineText)
        offsetMap = null
    } else {
        val toRendered = IntArray(lineText.length + 1)
        val toDoc = ArrayList<Int>(lineText.length + widgets.size + 1)
        var next = 0
        fun appendWidgets(offset: Int, before: Boolean) {
            while (next < widgets.size &&
                widgets[next].offset == offset &&
                (!before || widgets[next].widget.spec.side <= 0)
            ) {
                toDoc.add(offset)
                builder.appendInlineContent(inlineWidgetId(next))
                next++
            }
        }
        var col = 0
        for (i in 0..lineText.length) {
            appendWidgets(i, before = true)
            toRendered[i] = builder.length
            appendWidgets(i, before = false)
            if (i == lineText.length) break
            if (lineText[i] == '\t') {
                val spaces = tabSize - (col % tabSize)
                repeat(spaces) {
                    toDoc.add(i)
                    builder.append(' ')
                }
                col += spaces
            } else {
                toDoc.add(i)
                builder.append(lineText[i])
                col++
            }
        }
        toDoc.add(lineText.length)
        offsetMap = LineOffsetMap(toRendered, toDoc.toIntArray())
    }

    val lineFromVal = lineFrom.value

    // Collect all MarkDecorations that overlap this line.
    // Iterate in reverse so that higher-precedence decoration sets
    // (earlier in the list) are applied last and win in addStyle().
    for (set in decorationSets.asReversed()) {
        set.between(lineFrom, lineTo) { from, to, value ->
            if (value is MarkDecoration && value.spec.style != null) {
                val startInLine =
                    (from - lineFromVal).coerceIn(0, lineText.length)
                val endInLine =
                    (to - lineFromVal).coerceIn(0, lineText.length)
                if (startInLine < endInLine) {
                    val mappedStart = toRenderedOffset(startInLine, offsetMap)
                    val mappedEnd = toRenderedOffset(endInLine, offsetMap)
                    if (mappedStart < mappedEnd) {
                        builder.addStyle(
                            value.spec.style,
                            mappedStart,
                            mappedEnd
                        )
                    }
                }
            }
            null // continue iteration
        }
    }

    return LineContentResult(builder.toAnnotatedString(), offsetMap)
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
internal fun buildColumnItems(
    state: EditorState,
    viewport: Viewport,
    decorationSets: List<DecorationSet>
): List<ColumnItem> {
    val items = mutableListOf<ColumnItem>()
    val doc = state.doc
    val tabSize = state.tabSize
    val viewFrom = DocPos(viewport.from)
    val viewTo = DocPos(viewport.to)

    // Collect replace decorations (fold ranges) sorted by start position.
    // Only include multi-line replacements; single-line replacements
    // (e.g. tab characters) are handled inline by buildLineContent().
    data class ReplaceRange(val from: Int, val to: Int, val widget: WidgetType?)
    val replaceRanges = mutableListOf<ReplaceRange>()
    for (set in decorationSets) {
        set.between(viewFrom, viewTo) { from, to, value ->
            if (value is ReplaceDecoration && from < to) {
                val fromLine = doc.lineAt(DocPos(from)).number
                val toLine = doc.lineAt(
                    DocPos(to.coerceAtMost(doc.length))
                ).number
                // Only treat as fold range if it spans multiple lines
                if (fromLine != toLine) {
                    replaceRanges.add(
                        ReplaceRange(from, to, value.spec.widget)
                    )
                }
            }
            null
        }
    }
    replaceRanges.sortBy { it.from }

    // Collect block widgets indexed by document position
    val blockWidgetsBefore = mutableMapOf<DocPos, MutableList<WidgetDecoration>>()
    val blockWidgetsAfter = mutableMapOf<DocPos, MutableList<WidgetDecoration>>()
    for (set in decorationSets) {
        set.between(viewFrom, viewTo) { from, _, value ->
            if (value is WidgetDecoration && value.spec.block) {
                val pos = DocPos(from)
                val list = if (value.spec.side >= 0) {
                    blockWidgetsAfter.getOrPut(pos) { mutableListOf() }
                } else {
                    blockWidgetsBefore.getOrPut(pos) { mutableListOf() }
                }
                list.add(value)
            }
            null
        }
    }

    // Collect line decorations
    val lineDecsByLine = mutableMapOf<LineNumber, MutableList<LineDecoration>>()
    // Collect inline widget decorations
    val inlineWidgetsByLine =
        mutableMapOf<LineNumber, MutableList<LineWidget>>()
    for (set in decorationSets) {
        set.between(viewFrom, viewTo) { from, _, value ->
            if (value is LineDecoration) {
                val line = doc.lineAt(DocPos(from))
                lineDecsByLine.getOrPut(line.number) { mutableListOf() }
                    .add(value)
            }
            if (value is WidgetDecoration && !value.spec.block) {
                val line = doc.lineAt(DocPos(from))
                inlineWidgetsByLine.getOrPut(line.number) { mutableListOf() }
                    .add(LineWidget(from - line.from.value, value))
            }
            null
        }
    }
    // Widgets at one position render in side order, and for equal sides in
    // decoration-set precedence order (stable sort), as upstream orders them.
    for (widgets in inlineWidgetsByLine.values) {
        widgets.sortWith(compareBy({ it.offset }, { it.widget.spec.side }))
    }

    // Walk visible lines
    var lineNum = doc.lineAt(viewFrom).number
    val lastLine =
        doc.lineAt(DocPos(viewport.to.coerceAtMost(doc.length))).number

    while (lineNum <= lastLine) {
        val line = doc.line(lineNum)

        // Check if this line starts or is inside a replace range
        val replace = replaceRanges.firstOrNull {
            it.from < line.to.value && it.to > line.from.value
        }
        if (replace != null && line.from.value >= replace.from) {
            // This line is entirely inside a replace range — skip it
            lineNum = lineNum + 1
            continue
        }

        // Emit block-before widgets
        blockWidgetsBefore[line.from]?.forEach { w ->
            items.add(
                ColumnItem.BlockWidgetItem(
                    line.from,
                    w,
                    BlockType.WidgetBefore
                )
            )
        }

        // Check if a replace range starts on this line
        val replaceOnLine = replaceRanges.firstOrNull {
            it.from >= line.from.value && it.from <= line.to.value
        }
        if (replaceOnLine != null) {
            // Truncate line at replace start and append fold placeholder
            val truncPos = replaceOnLine.from - line.from.value
            val truncText = line.text.substring(
                0,
                truncPos.coerceAtMost(line.text.length)
            )
            // Widgets inside the folded range are hidden with it.
            val inlineWidgets = inlineWidgetsByLine[lineNum]
                ?.filter { it.offset <= truncText.length }
                .orEmpty()
            val result = buildLineContentWithTabs(
                line.from,
                line.from + truncText.length,
                truncText,
                decorationSets,
                tabSize,
                inlineWidgets
            )
            val content = if (replaceOnLine.widget != null) {
                AnnotatedString.Builder().apply {
                    append(result.content)
                    pushStyle(SpanStyle(color = Color.Gray))
                    append("\u2026")
                    pop()
                }.toAnnotatedString()
            } else {
                result.content
            }
            val lineDecos = lineDecsByLine[lineNum] ?: emptyList()
            items.add(
                ColumnItem.TextLine(
                    lineNum,
                    line.from,
                    line.from + truncText.length,
                    content,
                    lineDecos,
                    inlineWidgets.map { it.widget },
                    offsetMap = result.offsetMap
                )
            )

            // Skip ahead past the replace range to the line
            // containing the end
            val endLine = doc.lineAt(
                DocPos(replaceOnLine.to.coerceAtMost(doc.length))
            )
            lineNum = endLine.number
        } else {
            // Normal line
            val inlineWidgets = inlineWidgetsByLine[lineNum].orEmpty()
            val result = buildLineContentWithTabs(
                line.from,
                line.to,
                line.text,
                decorationSets,
                tabSize,
                inlineWidgets
            )
            val lineDecos = lineDecsByLine[lineNum] ?: emptyList()
            items.add(
                ColumnItem.TextLine(
                    lineNum,
                    line.from,
                    line.to,
                    result.content,
                    lineDecos,
                    inlineWidgets.map { it.widget },
                    offsetMap = result.offsetMap
                )
            )
            lineNum = lineNum + 1
        }

        // Emit block-after widgets
        blockWidgetsAfter[line.from]?.forEach { w ->
            items.add(
                ColumnItem.BlockWidgetItem(
                    line.from,
                    w,
                    BlockType.WidgetAfter
                )
            )
        }
    }

    return items
}
