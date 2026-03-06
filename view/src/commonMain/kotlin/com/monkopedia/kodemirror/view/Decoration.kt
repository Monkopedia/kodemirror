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

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeValue

/** A set of decorations. */
typealias DecorationSet = RangeSet<Decoration>

/** Spec for a mark decoration (inline text styling). */
data class MarkDecorationSpec(
    val inclusive: Boolean = false,
    val inclusiveStart: Boolean = false,
    val inclusiveEnd: Boolean = false,
    val style: SpanStyle? = null,
    val paragraphStyle: ParagraphStyle? = null,
    val tagName: String? = null,
    val cssClass: String? = null,
    val attributes: Map<String, String>? = null
)

/** Spec for a widget decoration (inline widget). */
data class WidgetDecorationSpec(
    val widget: WidgetType,
    val side: Int = 0,
    val inlineOrder: Boolean = false,
    val block: Boolean = false
)

/** Spec for a line decoration (whole-line styling). */
data class LineDecorationSpec(
    val attributes: Map<String, String>? = null,
    val cssClass: String? = null,
    val style: SpanStyle? = null
)

/** Spec for a replace decoration (hides/replaces a range). */
data class ReplaceDecorationSpec(
    val widget: WidgetType? = null,
    val inclusive: Boolean = false,
    val inclusiveStart: Boolean = false,
    val inclusiveEnd: Boolean = false,
    val block: Boolean = false
)

/**
 * Base class for custom inline/block widgets.
 * Subclasses implement [Content] as a Compose composable instead of toDOM().
 */
abstract class WidgetType {
    /**
     * Render the widget as a composable.
     */
    @Composable
    abstract fun Content()

    /** Compare this widget to another for equality. */
    open fun eq(other: WidgetType): Boolean = this === other

    /** Estimated height in pixels, or -1 if unknown. */
    open val estimatedHeight: Int get() = -1

    /** Number of line breaks the widget introduces. */
    open val lineBreaks: Int get() = 0

    /** Whether the widget should ignore pointer events. */
    open val ignoreEvents: Boolean get() = false

    /** Called when the widget is no longer needed. */
    open fun destroy() {}
}

/**
 * Base class for all decoration types.
 */
sealed class Decoration : RangeValue() {
    companion object {
        /** Create a mark (inline text style) decoration. */
        fun mark(spec: MarkDecorationSpec): MarkDecoration = MarkDecoration(spec)

        /**
         * Create a mark decoration with a [SpanStyle].
         *
         * ```kotlin
         * val bold = Decoration.mark(
         *     style = SpanStyle(fontWeight = FontWeight.Bold)
         * )
         * ```
         */
        fun mark(
            style: SpanStyle,
            inclusive: Boolean = false,
            inclusiveStart: Boolean = false,
            inclusiveEnd: Boolean = false,
            cssClass: String? = null
        ): MarkDecoration = MarkDecoration(
            MarkDecorationSpec(
                style = style,
                inclusive = inclusive,
                inclusiveStart = inclusiveStart,
                inclusiveEnd = inclusiveEnd,
                cssClass = cssClass
            )
        )

        /** Create a widget (inline composable) decoration. */
        fun widget(spec: WidgetDecorationSpec): WidgetDecoration = WidgetDecoration(spec)

        /** Create a line decoration (whole-line attributes). */
        fun line(spec: LineDecorationSpec): LineDecoration = LineDecoration(spec)

        /**
         * Create a line decoration with a [SpanStyle].
         *
         * ```kotlin
         * val activeLine = Decoration.line(
         *     style = SpanStyle(background = Color(0x20000000))
         * )
         * ```
         */
        fun line(style: SpanStyle, cssClass: String? = null): LineDecoration = LineDecoration(
            LineDecorationSpec(style = style, cssClass = cssClass)
        )

        /** Create a replace decoration (hide/replace a range). */
        fun replace(spec: ReplaceDecorationSpec): ReplaceDecoration = ReplaceDecoration(spec)
    }
}

/** A mark decoration applies styling to a range of text. */
class MarkDecoration(val spec: MarkDecorationSpec) : Decoration() {
    override val startSide: Int
        get() = if (spec.inclusiveStart || spec.inclusive) -1 else 0

    override val endSide: Int
        get() = if (spec.inclusiveEnd || spec.inclusive) 1 else 0

    override fun eq(other: RangeValue): Boolean = other is MarkDecoration && spec == other.spec
}

/** A widget decoration places a composable inside the text flow. */
class WidgetDecoration(val spec: WidgetDecorationSpec) : Decoration() {
    override val point: Boolean get() = true
    override val startSide: Int get() = if (spec.block) -200 else spec.side
    override val endSide: Int get() = if (spec.block) 200 else spec.side

    override fun eq(other: RangeValue): Boolean = other is WidgetDecoration &&
        spec.widget.eq(other.spec.widget) &&
        spec.side == other.spec.side &&
        spec.block == other.spec.block
}

/** A line decoration adds attributes to the containing line element. */
class LineDecoration(val spec: LineDecorationSpec) : Decoration() {
    override val startSide: Int get() = -1
    override val endSide: Int get() = -1

    override fun eq(other: RangeValue): Boolean = other is LineDecoration && spec == other.spec
}

/** A replace decoration hides a range of text, optionally showing a widget. */
class ReplaceDecoration(val spec: ReplaceDecorationSpec) : Decoration() {
    override val point: Boolean get() = true
    override val startSide: Int get() = if (spec.inclusiveStart || spec.inclusive) -1 else -100
    override val endSide: Int get() = if (spec.inclusiveEnd || spec.inclusive) 1 else 100

    override fun eq(other: RangeValue): Boolean {
        if (other !is ReplaceDecoration) return false
        val ow = spec.widget
        val tw = other.spec.widget
        val widgetEq = when {
            ow == null && tw == null -> true
            ow != null && tw != null -> ow.eq(tw)
            else -> false
        }
        return widgetEq &&
            spec.inclusive == other.spec.inclusive &&
            spec.block == other.spec.block
    }
}
