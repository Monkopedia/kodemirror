package com.monkopedia.kodemirror.view

import androidx.compose.foundation.interaction.Interaction
import com.monkopedia.kodemirror.state.Either
import com.monkopedia.kodemirror.state.MapMode
import com.monkopedia.kodemirror.state.Range
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeValue
import com.monkopedia.kodemirror.state.SingleOrList
import kotlin.math.max
import kotlin.math.min

// import {MapMode, RangeValue, Range, RangeSet} from "@codemirror/state"
// import {Direction} from "./bidi"
// import {attrsEq, Attrs} from "./attributes"
// import {EditorView} from "./editorview"
// import {Rect} from "./dom"

interface DecorationSpec {
    val inclusive: Boolean?
        get() = other["inclusive"] == true
    val inclusiveStart: Boolean?
        get() = other["inclusiveStart"] == true
    val inclusiveEnd: Boolean?
        get() = other["inclusiveEnd"] == true
    val other: Map<String, Any?>
}

data class MarkDecorationSpec(
    // / Whether the mark covers its start and end position or not. This
    // / influences whether content inserted at those positions becomes
    // / part of the mark. Defaults to false.
    override val inclusive: Boolean? = null,
    // / Specify whether the start position of the marked range should be
    // / inclusive. Overrides `inclusive`, when both are present.
    override val inclusiveStart: Boolean? = null,
    // / Whether the end should be inclusive.
    override val inclusiveEnd: Boolean? = null,
    // / Add attributes to the DOM elements that hold the text in the
    // / marked range.
    val attributes: Attrs? = null,
    // / Shorthand for `{attributes: {class: value}}`.
    val cls: String? = null,
    // / Add a wrapping element around the text in the marked range. Note
    // / that there will not necessarily be a single element covering the
    // / entire range—other decorations with lower precedence might split
    // / this one if they partially overlap it, and line breaks always
    // / end decoration elements.
    val tagName: String? = null,
    // / When using sets of decorations in
    // / [`bidiIsolatedRanges`](##view.EditorView^bidiIsolatedRanges),
    // / this property provides the direction of the isolates. When null
    // / or not given, it indicates the range has `dir=auto`, and its
    // / direction should be derived from the first strong directional
    // / character in it.
    val bidiIsolate: Direction? = null,
    // / Decoration specs allow extra properties, which can be retrieved
    // / through the decoration's [`spec`](#view.Decoration.spec)
    // / property.
    override val other: Map<String, Any?> = emptyMap()
) : DecorationSpec

data class WidgetDecorationSpec(
    // / The type of widget to draw here.
    val widget: WidgetType,
    // / Which side of the given position the widget is on. When this is
    // / positive, the widget will be drawn after the cursor if the
    // / cursor is on the same position. Otherwise, it'll be drawn before
    // / it. When multiple widgets sit at the same position, their `side`
    // / values will determine their ordering—those with a lower value
    // / come first. Defaults to 0. May not be more than 10000 or less
    // / than -10000.
    val side: Int? = null,
    // / By default, to avoid unintended mixing of block and inline
    // / widgets, block widgets with a positive `side` are always drawn
    // / after all inline widgets at that position, and those with a
    // / non-positive side before inline widgets. Setting this option to
    // / `true` for a block widget will turn this off and cause it to be
    // / rendered between the inline widgets, ordered by `side`.
    val inlineOrder: Boolean? = null,
    // / Determines whether this is a block widgets, which will be drawn
    // / between lines, or an inline widget (the default) which is drawn
    // / between the surrounding text.
    // /
    // / Note that block-level decorations should not have vertical
    // / margins, and if you dynamically change their height, you should
    // / make sure to call
    // / [`requestMeasure`](#view.EditorView.requestMeasure), so that the
    // / editor can update its information about its vertical layout.
    val block: Boolean? = null,
    // / Other properties are allowed.
    override val other: Map<String, Any?> = emptyMap()
) : DecorationSpec

data class ReplaceDecorationSpec(
    // / An optional widget to drawn in the place of the replaced
    // / content.
    val widget: WidgetType? = null,
    // / Whether this range covers the positions on its sides. This
    // / influences whether new content becomes part of the range and
    // / whether the cursor can be drawn on its sides. Defaults to false
    // / for inline replacements, and true for block replacements.
    override val inclusive: Boolean? = null,
    // / Set inclusivity at the start.
    override val inclusiveStart: Boolean? = null,
    // / Set inclusivity at the end.
    override val inclusiveEnd: Boolean? = null,
    // / Whether this is a block-level decoration. Defaults to false.
    val block: Boolean? = null,
    // / Other properties are allowed.
    override val other: Map<String, Any?> = emptyMap()
) : DecorationSpec

data class LineDecorationSpec(
    // / DOM attributes to add to the element wrapping the line.
    val attributes: Attrs? = null,
    // / Shorthand for `{attributes: {class: value}}`.
    val cls: String? = null,
    // / Other properties are allowed.
    override val other: Map<String, Any?> = emptyMap()
) : DecorationSpec

// / Widgets added to the content are described by subclasses of this
// / class. Using a description object like that makes it possible to
// / delay creating of the DOM structure for a widget until it is
// / needed, and to avoid redrawing widgets even if the decorations
// / that define them are recreated.
abstract class WidgetType {
    // / Build the DOM structure for this widget instance.
//    abstract fun toDOM(view: EditorView): HTMLElement

    // / Compare this instance to another instance of the same type.
    // / (TypeScript can't express this, but only instances of the same
    // / specific class will be passed to this method.) This is used to
    // / avoid redrawing widgets when they are replaced by a new
    // / decoration of the same type. The default implementation just
    // / returns `false`, which will cause new instances of the widget to
    // / always be redrawn.
    fun eq(widget: WidgetType): Boolean = false

    // / Update a DOM element created by a widget of the same type (but
    // / different, non-`eq` content) to reflect this widget. May return
    // / true to indicate that it could update, false to indicate it
    // / couldn't (in which case the widget will be redrawn). The default
    // / implementation just returns false.
//    fun updateDOM(dom: HTMLElement, view: EditorView): Boolean { return false }

    // / @internal
    fun compare(other: WidgetType): Boolean = this == other || this.eq(other)

    // / The estimated height this widget will have, to be used when
    // / estimating the height of content that hasn't been drawn. May
    // / return -1 to indicate you don't know. The default implementation
    // / returns -1.
    val estimatedHeight: Int
        get() {
            return -1
        }

    // / For inline widgets that are displayed inline (as opposed to
    // / `inline-block`) and introduce line breaks (through `<br>` tags
    // / or textual newlines), this must indicate the amount of line
    // / breaks they introduce. Defaults to 0.
    val lineBreaks: Int
        get() {
            return 0
        }

    // / Can be used to configure which kinds of events inside the widget
    // / should be ignored by the editor. The default is to ignore all
    // / events.
    fun ignoreEvent(event: Interaction): Boolean = true

    // / Override the way screen coordinates for positions at/in the
    // / widget are found. `pos` will be the offset into the widget, and
    // / `side` the side of the position that is being queried—less than
    // / zero for before, greater than zero for after, and zero for
    // / directly at that position.
//    fun coordsAt(dom: HTMLElement, pos: number, side: number): Rect | null { return null }

    // / @internal
    internal open val isHidden: Boolean
        get() {
            return false
        }

    // / @internal
    internal open val editable: Boolean
        get() {
            return false
        }

    // / This is called when the an instance of the widget is removed
    // / from the editor view.
//    fun destroy(dom: HTMLElement) {}
}

// / A decoration set represents a collection of decorated ranges,
// / organized for efficient access and mapping. See
// / [`RangeSet`](#state.RangeSet) for its methods.
typealias DecorationSet = RangeSet<Decoration<*>>
typealias DecorationSetOrFactory = Either<DecorationSet, (EditorView) -> DecorationSet>
// export type DecorationSet = RangeSet<Decoration>

enum class Side(val value: Int) {
    NonIncEnd(-6e8.toInt()), // (end of non-inclusive range)
    GapStart(-5e8.toInt()),
    BlockBefore(-4e8.toInt()), // + widget side option (block widget before)
    BlockIncStart(-3e8.toInt()), // (start of inclusive block range)
    Line(-2e8.toInt()), // (line widget)
    InlineBefore(-1e8.toInt()), // + widget side (inline widget before)
    InlineIncStart(-1), // (start of inclusive inline range)
    InlineIncEnd(1), // (end of inclusive inline range)
    InlineAfter(1e8.toInt()), // + widget side (inline widget after)
    BlockIncEnd(2e8.toInt()), // (end of inclusive block range)
    BlockAfter(3e8.toInt()), // + widget side (block widget after)
    GapEnd(4e8.toInt()),
    NonIncStart(5e8.toInt()) // (start of non-inclusive range)
}

// / The different types of blocks that can occur in an editor view.
enum class BlockType {
    // / A line of text.
    Text,

    // / A block widget associated with the position after it.
    WidgetBefore,

    // / A block widget associated with the position before it.
    WidgetAfter,

    // / A block widget [replacing](#view.Decoration^replace) a range of content.
    WidgetRange
}

// / A decoration provides information on how to draw or style a piece
// / of content. You'll usually use it wrapped in a
// / [`Range`](#state.Range), which adds a start and end position.
// / @nonabstract
abstract class Decoration<T : DecorationSpec>
protected constructor(
    // / @internal
    override val startSide: Int,
    // / @internal
    override val endSide: Int,
    // / @internal
    internal val widget: WidgetType?,
    // / The config object used to create this decoration. You can
    // / include additional properties in there to store metadata about
    // / your decoration.
    val spec: T
) : RangeValue {

    // / @internal
    override val point: Boolean get() = false

    // / @internal
    internal open val heightRelevant: Boolean
        get() {
            return false
        }

    abstract fun eq(other: Decoration<*>): Boolean

    // / @internal
    internal val hasHeight: Boolean
        get() {
            return if (this.widget != null) this.widget.estimatedHeight > -1 else false
        }

    companion object {
        // / Create a mark decoration, which influences the styling of the
        // / content in its range. Nested mark decorations will cause nested
        // / DOM elements to be created. Nesting order is determined by
        // / precedence of the [facet](#view.EditorView^decorations), with
        // / the higher-precedence decorations creating the inner DOM nodes.
        // / Such elements are split on line boundaries and on the boundaries
        // / of lower-precedence decorations.
        fun mark(spec: MarkDecorationSpec): Decoration<MarkDecorationSpec> = MarkDecoration(spec)

        // / Create a widget decoration, which displays a DOM element at the
        // / given position.
        fun widget(spec: WidgetDecorationSpec): Decoration<DecorationSpec> {
            var side = max(-10000, min(10000, spec.side ?: 0))
            val block = spec.block == true
            side +=
                (
                    if (block &&
                        spec.inlineOrder != true
                    ) {
                        (
                            if (side >
                                0
                            ) {
                                Side.BlockAfter
                            } else {
                                Side.BlockBefore
                            }
                            )
                    } else {
                        (
                            if (side >
                                0
                            ) {
                                Side.InlineAfter
                            } else {
                                Side.InlineBefore
                            }
                            )
                    }
                    ).value
            return PointDecoration(spec, side, side, block, spec.widget, false)
        }

        // / Create a replace decoration which replaces the given range with
        // / a widget, or simply hides it.
        fun replace(spec: ReplaceDecorationSpec): Decoration<DecorationSpec> {
            val block = spec.block == true
            val startSide: Int
            val endSide: Int
            if (spec.other["isBlockGap"] == true) {
                startSide = Side.GapStart.value
                endSide = Side.GapEnd.value
            } else {
                val (start, end) = getInclusive(spec, block)
                startSide = when {
                    start -> (if (block) Side.BlockIncStart else Side.InlineIncStart)
                    else -> Side.NonIncStart
                }.value - 1
                endSide = when {
                    end -> (if (block) Side.BlockIncEnd else Side.InlineIncEnd)
                    else -> Side.NonIncEnd
                }.value + 1
            }
            return PointDecoration(spec, startSide, endSide, block, spec.widget, true)
        }

        // / Create a line decoration, which can add DOM attributes to the
        // / line starting at the given position.
        fun line(spec: LineDecorationSpec): Decoration<LineDecorationSpec> = LineDecoration(spec)

        // / Build a [`DecorationSet`](#view.DecorationSet) from the given
        // / decorated range or ranges. If the ranges aren't already sorted,
        // / pass `true` for `sort` to make the library sort them for you.
        fun set(of: SingleOrList<Range<Decoration<*>>>, sort: Boolean = false): DecorationSet =
            RangeSet.of(of, sort)

        // / The empty set of decorations.
        val none: DecorationSet = RangeSet.empty()
    }
}

class MarkDecoration(startEnd: Pair<Boolean, Boolean>, spec: MarkDecorationSpec) :
    Decoration<MarkDecorationSpec>(
        (if (startEnd.first) Side.InlineIncStart else Side.NonIncStart).value,
        (if (startEnd.second) Side.InlineIncEnd else Side.NonIncEnd).value,
        null,
        spec
    ) {
    val tagName: String = spec.tagName ?: "span"
    val cls: String = spec.cls ?: ""
    val attrs: Attrs? = spec.attributes

    constructor(spec: MarkDecorationSpec) : this(getInclusive(spec), spec)

    override fun eq(other: Decoration<*>): Boolean = this === other ||
        other is MarkDecoration &&
        this.tagName == other.tagName &&
        (cls() == other.cls()) &&
        attrsEq(this.attrs, other.attrs, "class")

    private fun cls() = this.cls.takeIf { it.isNotBlank() } ?: this.attrs?.get("cls")?.toString()

    override val point: Boolean
        get() = false
    // TODO: Build support for these checks in range
//    fun range(from: Int, to: Int = from) {
//        if (from >= to) throw IllegalArgumentException ("Mark decorations may not be empty")
//        return super.range(from, to)
//    }
}

class LineDecoration constructor(spec: LineDecorationSpec) :
    Decoration<LineDecorationSpec>(Side.Line.value, Side.Line.value, null, spec) {

    override val mapMode: MapMode
        get() = MapMode.TrackBefore
    override val point: Boolean
        get() = true

    override fun eq(other: Decoration<*>): Boolean = other is LineDecoration &&
        this.spec.cls == other.spec.cls &&
        attrsEq(this.spec.attributes, other.spec.attributes)

    // TODO: Build support for these checks in range
//    fun range(from: Int, to: Int = from) {
//        if (to != from) throw new RangeError ("Line decoration ranges must be zero-length")
//        return super.range(from, to)
//    }
}

class PointDecoration(
    spec: DecorationSpec,
    startSide: Int,
    endSide: Int,
    val block: Boolean,
    widget: WidgetType?,
    val isReplace: Boolean
) : Decoration<DecorationSpec>(startSide, endSide, widget, spec) {
    override val mapMode: MapMode
        get() = when {
            !block -> MapMode.TrackDel
            startSide <= 0 -> MapMode.TrackBefore
            else -> MapMode.TrackAfter
        }

    override val point: Boolean
        get() = true

    // Only relevant when this.block == true
    val type: BlockType
        get() {
            return when {
                this.startSide != this.endSide -> BlockType.WidgetRange
                this.startSide <= 0 -> BlockType.WidgetBefore
                else -> BlockType.WidgetAfter
            }
        }

    override val heightRelevant: Boolean
        get() {
            return this.block ||
                this.widget != null &&
                (this.widget.estimatedHeight >= 5 || this.widget.lineBreaks > 0)
        }

    override fun eq(other: Decoration<*>): Boolean = other is PointDecoration &&
        widgetsEq(this.widget, other.widget) &&
        this.block == other.block &&
        this.startSide == other.startSide &&
        this.endSide == other.endSide

    // TODO: Build support for these checks in range
//    fun range(from: Int, to: Int = from)
//    {
//        if (this.isReplace && (from > to || (from == to && this.startSide > 0 && this.endSide <= 0)))
//            throw new RangeError ("Invalid range for replacement decoration")
//        if (!this.isReplace && to != from)
//            throw new RangeError ("Widget decorations can only have zero-length ranges")
//        return super.range(from, to)
//    }
}

fun getInclusive(spec: DecorationSpec, block: Boolean = false): Pair<Boolean, Boolean> {
    val start = spec.inclusiveStart ?: spec.inclusive ?: block
    val end = spec.inclusiveEnd ?: spec.inclusive ?: block
    return start to end
}

fun widgetsEq(a: WidgetType?, b: WidgetType?): Boolean =
    a == b || (a != null && b != null && a.compare(b))

fun addRange(from: Int, to: Int, ranges: MutableList<Int>, margin: Int = 0) {
    val last = ranges.size - 1
    if (last >= 0 && ranges[last] + margin >= from) {
        ranges[last] = max(ranges[last], to)
    } else {
        ranges.add(from)
        ranges.add(to)
    }
}
