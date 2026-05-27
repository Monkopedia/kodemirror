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
        val inlineWidgets: List<WidgetDecoration> = emptyList(),
        /**
         * Mapping from document offsets (relative to line start) to
         * expanded text offsets. When tabs are expanded, the rendered
         * text is longer than the document text. Index `i` gives the
         * expanded offset for document-relative offset `i`.
         * `null` when no expansion was needed (no tabs in the line).
         */
        val tabOffsetMap: IntArray? = null
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
                tabOffsetMap.contentEquals(other.tabOffsetMap)
        }

        override fun hashCode(): Int {
            var result = lineNumber.hashCode()
            result = 31 * result + from.hashCode()
            result = 31 * result + to.hashCode()
            result = 31 * result + content.hashCode()
            result = 31 * result + lineDecorations.hashCode()
            result = 31 * result + inlineWidgets.hashCode()
            result = 31 * result + (tabOffsetMap?.contentHashCode() ?: 0)
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
 * Convert a document-relative offset into the expanded-text offset, using the
 * tab offset map. Returns [docOffset] unchanged when [tabOffsetMap] is `null`
 * (no tabs in the line). This is the inverse of [unmapTabOffset] and mirrors
 * the forward mapping used when drawing the cursor/selection.
 */
internal fun mapTabOffset(docOffset: Int, tabOffsetMap: IntArray?): Int {
    if (tabOffsetMap == null) return docOffset
    val index = docOffset.coerceIn(0, tabOffsetMap.size - 1)
    return tabOffsetMap[index]
}

/**
 * Convert an expanded-text offset back to the original document-relative
 * offset, using the tab offset map. Returns [expandedOffset] unchanged
 * when [tabOffsetMap] is `null` (no tabs in the line).
 */
internal fun unmapTabOffset(expandedOffset: Int, tabOffsetMap: IntArray?): Int {
    if (tabOffsetMap == null) return expandedOffset
    // Binary search: find the largest doc offset whose mapped value
    // is <= expandedOffset.
    var lo = 0
    var hi = tabOffsetMap.size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (tabOffsetMap[mid] <= expandedOffset) lo = mid else hi = mid - 1
    }
    return lo
}

/**
 * Result of building line content with tab expansion info.
 */
internal data class LineContentResult(
    val content: AnnotatedString,
    val offsetMap: IntArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LineContentResult) return false
        return content == other.content &&
            offsetMap.contentEquals(other.offsetMap)
    }

    override fun hashCode(): Int {
        var result = content.hashCode()
        result = 31 * result + (offsetMap?.contentHashCode() ?: 0)
        return result
    }
}

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
 * Build line content with tab expansion, returning both the annotated
 * string and the offset mapping for selection/cursor positioning.
 */
internal fun buildLineContentWithTabs(
    lineFrom: DocPos,
    lineTo: DocPos,
    lineText: String,
    decorationSets: List<DecorationSet>,
    tabSize: Int = 4
): LineContentResult {
    val hasTabs = '\t' in lineText

    // Expand tabs to spaces and build a mapping from original offsets
    // to expanded offsets.
    val expandedText: String
    val offsetMap: IntArray?
    if (hasTabs) {
        val expanded = StringBuilder()
        val map = IntArray(lineText.length + 1)
        var col = 0
        for (i in lineText.indices) {
            map[i] = expanded.length
            if (lineText[i] == '\t') {
                val spaces = tabSize - (col % tabSize)
                repeat(spaces) { expanded.append(' ') }
                col += spaces
            } else {
                expanded.append(lineText[i])
                col++
            }
        }
        map[lineText.length] = expanded.length
        expandedText = expanded.toString()
        offsetMap = map
    } else {
        expandedText = lineText
        offsetMap = null
    }

    val builder = AnnotatedString.Builder(expandedText)
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
                    val mappedStart = if (offsetMap != null && startInLine < offsetMap.size) {
                        offsetMap[startInLine]
                    } else {
                        startInLine
                    }
                    val mappedEnd = if (offsetMap != null && endInLine < offsetMap.size) {
                        offsetMap[endInLine]
                    } else {
                        endInLine
                    }
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
        mutableMapOf<LineNumber, MutableList<WidgetDecoration>>()
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
                    .add(value)
            }
            null
        }
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
            val result = buildLineContentWithTabs(
                line.from,
                line.from + truncText.length,
                truncText,
                decorationSets,
                tabSize
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
            val inlineWidgets =
                inlineWidgetsByLine[lineNum] ?: emptyList()
            items.add(
                ColumnItem.TextLine(
                    lineNum,
                    line.from,
                    line.from + truncText.length,
                    content,
                    lineDecos,
                    inlineWidgets,
                    tabOffsetMap = result.offsetMap
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
            val result = buildLineContentWithTabs(
                line.from,
                line.to,
                line.text,
                decorationSets,
                tabSize
            )
            val lineDecos = lineDecsByLine[lineNum] ?: emptyList()
            val inlineWidgets =
                inlineWidgetsByLine[lineNum] ?: emptyList()
            items.add(
                ColumnItem.TextLine(
                    lineNum,
                    line.from,
                    line.to,
                    result.content,
                    lineDecos,
                    inlineWidgets,
                    tabOffsetMap = result.offsetMap
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
