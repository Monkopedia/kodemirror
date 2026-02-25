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

import com.monkopedia.kodemirror.state.EditorState

/** The visible document range. */
data class Viewport(val from: Int, val to: Int) {
    /** True when [pos] falls inside this viewport. */
    operator fun contains(pos: Int): Boolean = pos in from..to
}

/** The kinds of vertical blocks that make up the editor layout. */
enum class BlockType {
    /** A line of text from the document. */
    Text,

    /** A block widget positioned before a line. */
    WidgetBefore,

    /** A block widget positioned after a line. */
    WidgetAfter,

    /** A block widget that replaces a range of lines. */
    WidgetRange
}

/**
 * Describes a vertical block in the editor layout — either a text line or a
 * widget.
 */
data class BlockInfo(
    /** Start document position. */
    val from: Int,
    /** End document position. */
    val to: Int,
    /** Top coordinate (pixels from editor top). */
    val top: Float,
    /** Block height in pixels. */
    val height: Float,
    /** What kind of block this is. */
    val type: BlockType,
    /** Associated widget, for widget blocks. */
    val widget: WidgetType? = null
) {
    val bottom: Float get() = top + height
}

/** How to position the viewport when scrolling to a target. */
enum class ScrollStrategy { Nearest, Start, End, Center }

/** Describes a scroll-into-view request. */
data class ScrollTarget(
    val from: Int,
    val to: Int,
    val y: ScrollStrategy = ScrollStrategy.Nearest,
    val x: ScrollStrategy = ScrollStrategy.Nearest,
    val yMargin: Float = 5f,
    val xMargin: Float = 5f
)

/**
 * A gap in the rendered document used for virtual scrolling when the document
 * has many lines that are currently off-screen.
 */
data class LineGap(val from: Int, val to: Int, val size: Float)

/**
 * Snapshot of the view's layout state.  Created fresh for each recomposition.
 */
data class ViewState(
    val state: EditorState,
    val viewport: Viewport,
    val visibleRanges: List<Pair<Int, Int>> = emptyList(),
    val lineGaps: List<LineGap> = emptyList(),
    val scrollTarget: ScrollTarget? = null
)
