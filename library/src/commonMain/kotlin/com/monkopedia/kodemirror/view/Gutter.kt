package com.monkopedia.kodemirror.view

import {combineConfig, MapMode, Facet, Extension, EditorState,
RangeValue, RangeSet, RangeCursor} from "@codemirror/state"
import {EditorView} from "./editorview"
import {ViewPlugin, ViewUpdate} from "./extension"
import {BlockType, WidgetType} from "./decoration"
import {BlockInfo} from "./heightmap"
import {Direction} from "./bidi"
import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.extension.*
import com.monkopedia.kodemirror.decoration.*
import com.monkopedia.kodemirror.dom.*
import kotlinx.browser.document
import kotlinx.browser.window

/// A gutter marker represents a bit of information attached to a line
/// in a specific gutter. Your own custom markers have to extend this
/// class.
abstract class GutterMarker : RangeValue() {
    /// Compare this marker to another marker of the same type.
    open fun eq(other: GutterMarker): Boolean = false

    /// Render the DOM node for this marker, if any.
    open fun toDOM(view: EditorView): Node? = null

    /// This property can be used to add CSS classes to the gutter
    /// element that contains this marker.
    open val elementClass: String = ""

    /// Called if the marker has a `toDOM` method and its representation
    /// was removed from a gutter.
    open fun destroy(dom: Node) {}

    /// @internal
    fun compare(other: GutterMarker): Boolean {
        return this == other || this::class == other::class && this.eq(other)
    }

    init {
        mapMode = MapMode.TrackBefore
        startSide = -1
        endSide = -1
        point = true
    }
}

GutterMarker.prototype.elementClass = ""
GutterMarker.prototype.toDOM = undefined
GutterMarker.prototype.mapMode = MapMode.TrackBefore
GutterMarker.prototype.startSide = GutterMarker.prototype.endSide = -1
GutterMarker.prototype.point = true

/// Facet used to add a class to all gutter elements for a given line.
/// Markers given to this facet should _only_ define an
/// [`elementclass`](#view.GutterMarker.elementClass), not a
/// [`toDOM`](#view.GutterMarker.toDOM) (or the marker will appear
/// in all gutters for the line).
export const gutterLineClass = Facet.define<RangeSet<GutterMarker>>()

/// Facet used to add a class to all gutter elements next to a widget.
/// Should not provide widgets with a `toDOM` method.
export const gutterWidgetClass =
Facet.define<(view: EditorView, widget: WidgetType, block: BlockInfo) => GutterMarker | null>()

type alias Handlers = Map<String, (view: EditorView, line: BlockInfo, event: Event) -> Boolean>

interface GutterConfig {
    /// An extra CSS class to be added to the wrapper (`cm-gutter`)
    /// element.
    val className: String?
        get() = null
    /// Controls whether empty gutter elements should be rendered.
    /// Defaults to false.
    val renderEmptyElements: Boolean
        get() = false
    /// Retrieve a set of markers to use in this gutter.
    val markers: (view: EditorView) -> List<RangeSet<GutterMarker>>
        get() = { emptyList() }
    /// Can be used to optionally add a single marker to every line.
    val lineMarker: (view: EditorView, line: BlockInfo, otherMarkers: List<GutterMarker>) -> GutterMarker?
        get() = { _, _, _ -> null }
    /// Associate markers with block widgets in the document.
    val widgetMarker: (view: EditorView, widget: WidgetType, block: BlockInfo) -> GutterMarker?
        get() = { _, _, _ -> null }
    /// If line or widget markers depend on additional state, and should
    /// be updated when that changes, pass a predicate here that checks
    /// whether a given view update might change the line markers.
    val lineMarkerChange: ((update: ViewUpdate) -> Boolean)?
        get() = null
    /// Add a hidden spacer element that gives the gutter its base
    /// width.
    val initialSpacer: ((view: EditorView) -> GutterMarker)?
        get() = null
    /// Update the spacer element when the view is updated.
    val updateSpacer: ((spacer: GutterMarker, update: ViewUpdate) -> GutterMarker)?
        get() = null
    /// Supply event handlers for DOM events on this gutter.
    val domEventHandlers: Handlers
        get() = emptyMap()
}

private val defaults = object : GutterConfig {
    override val className = ""
    override val renderEmptyElements = false
    override val markers = { _: EditorView -> listOf(RangeSet.empty) }
    override val lineMarker = { _: EditorView, _: BlockInfo, _: List<GutterMarker> -> null }
    override val widgetMarker = { _: EditorView, _: WidgetType, _: BlockInfo -> null }
    override val lineMarkerChange: ((ViewUpdate) -> Boolean)? = null
    override val initialSpacer: ((EditorView) -> GutterMarker)? = null
    override val updateSpacer: ((GutterMarker, ViewUpdate) -> GutterMarker)? = null
    override val domEventHandlers = emptyMap<String, (EditorView, BlockInfo, Event) -> Boolean>()
}

const activeGutters = Facet.define<GutterConfig>()

/// Define an editor gutter. The order in which the gutters appear is
/// determined by their extension priority.
export fun gutter(config: GutterConfig): Extension {
    return listOf(gutters(), activeGutters.of(config))
}

const unfixGutters = Facet.define<Boolean, Boolean> { values ->
    values.any { it }
}

/// The gutter-drawing plugin is automatically enabled when you add a
/// gutter, but you can use this function to explicitly configure it.
///
/// Unless `fixed` is explicitly set to `false`, the gutters are
/// fixed, meaning they don't scroll along with the content
/// horizontally (except on Internet Explorer, which doesn't support
/// CSS [`position:
/// sticky`](https://developer.mozilla.org/en-US/docs/Web/CSS/position#sticky)).
export fun gutters(config: GutterConfig? = null): Extension {
    val result = mutableListOf<Extension>(gutterView)
    if (config?.fixed == false) result.add(unfixGutters.of(true))
    return result
}

private val gutterView = ViewPlugin.fromClass(
    create = { view ->
        GutterView(view)
    },
    provide = { plugin ->
        EditorView.scrollMargins.of { view ->
            val value = view.plugin(plugin)
            if (value == null || value.gutters.isEmpty() || !value.fixed) return@of null
            if (view.textDirection == Direction.LTR)
                mapOf("left" to value.dom.offsetWidth * view.scaleX)
            else
                mapOf("right" to value.dom.offsetWidth * view.scaleX)
        }
    }
)

private fun asArray(val_: Any): List<Any> = if (val_ is List<*>) val_ as List<Any> else listOf(val_)

private fun advanceCursor(cursor: RangeCursor<GutterMarker>, collect: MutableList<GutterMarker>, pos: Int) {
    while (cursor.value != null && cursor.from <= pos) {
        if (cursor.from == pos) collect.add(cursor.value!!)
        cursor.next()
    }
}

private class UpdateContext(
    val gutter: SingleGutterView,
    viewport: Viewport,
    var height: Int
) {
    var cursor: RangeCursor<GutterMarker> = RangeSet.iter(gutter.markers, viewport.from)
    var i = 0

    fun addElement(view: EditorView, block: BlockInfo, markers: List<GutterMarker>) {
        val above = (block.top - height) / view.scaleY
        val height = block.height / view.scaleY
        if (i == gutter.elements.size) {
            val newElt = GutterElement(view, height, above, markers)
            gutter.elements.add(newElt)
            gutter.dom.appendChild(newElt.dom)
        } else {
            gutter.elements[i].update(view, height, above, markers)
        }
        this.height = block.bottom
        i++
    }

    fun line(view: EditorView, line: BlockInfo, extraMarkers: List<GutterMarker>) {
        val localMarkers = mutableListOf<GutterMarker>()
        advanceCursor(cursor, localMarkers, line.from)
        if (extraMarkers.isNotEmpty()) localMarkers.addAll(extraMarkers)
        val forLine = gutter.config.lineMarker(view, line, localMarkers)
        if (forLine != null) localMarkers.add(0, forLine)

        if (localMarkers.isEmpty() && !gutter.config.renderEmptyElements) return
        addElement(view, line, localMarkers)
    }

    fun widget(view: EditorView, block: BlockInfo) {
        val marker = gutter.config.widgetMarker(view, block.widget!!, block)
        val markers = if (marker != null) mutableListOf(marker) else null
        for (cls in view.state.facet(gutterWidgetClass)) {
            val marker = cls(view, block.widget!!, block)
            if (marker != null) (markers ?: mutableListOf()).add(marker)
        }
        if (markers != null) addElement(view, block, markers)
    }

    fun finish() {
        while (gutter.elements.size > i) {
            val last = gutter.elements.removeLast()
            gutter.dom.removeChild(last.dom)
            last.destroy()
        }
    }
}

private class SingleGutterView(
    val view: EditorView,
    val config: Required<GutterConfig>
) {
    val dom = document.createElement("div").apply {
        className = "cm-gutter" + (config.className?.let { " $it" } ?: "")
        for ((prop, handler) in config.domEventHandlers) {
            addEventListener(prop) { event ->
                var target = event.target as HTMLElement
                val y = if (target != dom && dom.contains(target)) {
                    while (target.parentNode != dom) target = target.parentNode as HTMLElement
                    val rect = target.getBoundingClientRect()
                    (rect.top + rect.bottom) / 2
                } else {
                    (event as MouseEvent).clientY
                }
                val line = view.lineBlockAtHeight(y - view.documentTop)
                if (handler(view, line, event)) event.preventDefault()
            }
        }
    }

    val elements = mutableListOf<GutterElement>()
    var markers = asArray(config.markers(view)).map { it as RangeSet<GutterMarker> }
    var spacer: GutterElement? = null

    init {
        config.initialSpacer?.let { initialSpacer ->
            spacer = GutterElement(view, 0, 0, listOf(initialSpacer(view))).apply {
                dom.style.cssText += "visibility: hidden; pointer-events: none"
                this@SingleGutterView.dom.appendChild(dom)
            }
        }
    }

    fun update(update: ViewUpdate): Boolean {
        val prevMarkers = markers
        markers = asArray(config.markers(update.view)).map { it as RangeSet<GutterMarker> }
        if (spacer != null && config.updateSpacer != null) {
            val updated = config.updateSpacer!!(spacer!!.markers[0], update)
            if (updated != spacer!!.markers[0]) spacer!!.update(update.view, 0, 0, listOf(updated))
        }
        val vp = update.view.viewport
        return !RangeSet.eq(markers, prevMarkers, vp.from, vp.to) ||
            (config.lineMarkerChange?.invoke(update) ?: false)
    }

    fun destroy() {
        elements.forEach { it.destroy() }
    }
}

private class GutterElement(
    view: EditorView,
    height: Int,
    above: Int,
    markers: List<GutterMarker>
) {
    val dom = document.createElement("div").apply {
        className = "cm-gutterElement"
    }
    var height = -1
    var above = 0
    var markers = emptyList<GutterMarker>()

    init {
        update(view, height, above, markers)
    }

    fun update(view: EditorView, height: Int, above: Int, markers: List<GutterMarker>) {
        if (this.height != height) {
            this.height = height
            dom.style.height = "${height}px"
        }
        if (this.above != above) {
            this.above = above
            dom.style.marginTop = if (above > 0) "${above}px" else ""
        }
        if (!sameMarkers(this.markers, markers)) setMarkers(view, markers)
    }

    private fun setMarkers(view: EditorView, markers: List<GutterMarker>) {
        var cls = "cm-gutterElement"
        var domPos = dom.firstChild
        var iNew = 0
        var iOld = 0

        while (true) {
            val skipTo = iOld
            val marker = if (iNew < markers.size) markers[iNew++] else null
            var matched = false

            if (marker != null) {
                val c = marker.elementClass
                if (c.isNotEmpty()) cls += " $c"
                for (i in iOld until this.markers.size) {
                    if (this.markers[i].compare(marker)) {
                        skipTo = i
                        matched = true
                        break
                    }
                }
            } else {
                skipTo = this.markers.size
            }

            while (iOld < skipTo) {
                val next = this.markers[iOld++]
                if (next.toDOM != null) {
                    next.destroy(domPos!!)
                    val after = domPos.nextSibling
                    domPos.remove()
                    domPos = after
                }
            }

            if (marker == null) break

            if (marker.toDOM != null) {
                if (matched) {
                    domPos = domPos?.nextSibling
                } else {
                    dom.insertBefore(marker.toDOM(view)!!, domPos)
                }
            }

            if (matched) iOld++
        }

        dom.className = cls
        this.markers = markers
    }

    fun destroy() {
        setMarkers(null as EditorView, emptyList()) // First argument not used unless creating markers
    }
}

private fun sameMarkers(a: List<GutterMarker>, b: List<GutterMarker>): Boolean {
    if (a.size != b.size) return false
    for (i in a.indices) {
        if (!a[i].compare(b[i])) return false
    }
    return true
}

/** Facet used to provide markers to the line number gutter. */
val lineNumberMarkers = Facet.define<RangeSet<GutterMarker>>()

/** Facet used to create markers in the line number gutter next to widgets. */
val lineNumberWidgetMarker = Facet.define<(view: EditorView, widget: WidgetType, block: BlockInfo) -> GutterMarker?>()

interface LineNumberConfig {
    /** How to display line numbers. Defaults to simply converting them to string. */
    val formatNumber: (lineNo: Int, state: EditorState) -> String
        get() = { lineNo, _ -> lineNo.toString() }

    /** Supply event handlers for DOM events on this gutter. */
    val domEventHandlers: Handlers
        get() = emptyMap()
}

private val lineNumberConfig = Facet.define<LineNumberConfig, LineNumberConfig> { values ->
    object : LineNumberConfig {
        override val formatNumber = values.lastOrNull()?.formatNumber ?: { lineNo, _ -> lineNo.toString() }
        override val domEventHandlers = values.fold(emptyMap<String, (EditorView, BlockInfo, Event) -> Boolean>()) { acc, config ->
            val result = acc.toMutableMap()
            for ((event, add) in config.domEventHandlers) {
                val exists = result[event]
                result[event] = if (exists != null) {
                    { view, line, event -> exists(view, line, event) || add(view, line, event) }
                } else {
                    add
                }
            }
            result
        }
    }
}

private class NumberMarker(val number: String) : GutterMarker() {
    override fun eq(other: GutterMarker): Boolean = other is NumberMarker && other.number == number
    override fun toDOM(view: EditorView): Node = document.createTextNode(number)
}

private fun formatNumber(view: EditorView, number: Int): String {
    return view.state.facet(lineNumberConfig).formatNumber(number, view.state)
}

private val lineNumberGutter = activeGutters.compute(listOf(lineNumberConfig)) { state ->
    object : GutterConfig {
        override val className = "cm-lineNumbers"
        override val renderEmptyElements = false
        override val markers = { view: EditorView -> listOf(view.state.facet(lineNumberMarkers)) }
        override val lineMarker = { view: EditorView, line: BlockInfo, others: List<GutterMarker> ->
            if (others.any { it.toDOM != null }) null
            else NumberMarker(formatNumber(view, view.state.doc.lineAt(line.from).number))
        }
        override val widgetMarker = { view: EditorView, widget: WidgetType, block: BlockInfo ->
            for (m in view.state.facet(lineNumberWidgetMarker)) {
                val result = m(view, widget, block)
                if (result != null) return@widgetMarker result
            }
            null
        }
        override val lineMarkerChange = { update: ViewUpdate ->
            update.startState.facet(lineNumberConfig) != update.state.facet(lineNumberConfig)
        }
        override val initialSpacer = { view: EditorView ->
            NumberMarker(formatNumber(view, maxLineNumber(view.state.doc.lines)))
        }
        override val updateSpacer = { spacer: GutterMarker, update: ViewUpdate ->
            val max = formatNumber(update.view, maxLineNumber(update.view.state.doc.lines))
            if (max == (spacer as NumberMarker).number) spacer else NumberMarker(max)
        }
        override val domEventHandlers = state.facet(lineNumberConfig).domEventHandlers
    }
}

/**
 * Create a line number gutter extension.
 */
fun lineNumbers(config: LineNumberConfig = object : LineNumberConfig {}): Extension {
    return listOf(
        lineNumberConfig.of(config),
        gutters(),
        lineNumberGutter
    )
}

private fun maxLineNumber(lines: Int): Int {
    var last = 9
    while (last < lines) last = last * 10 + 9
    return last
}

private val activeLineGutterMarker = object : GutterMarker() {
    override val elementClass = "cm-activeLineGutter"
}

private val activeLineGutterHighlighter = gutterLineClass.compute(listOf("selection")) { state ->
    val marks = mutableListOf<Range<GutterMarker>>()
    var last = -1
    for (range in state.selection.ranges) {
        val linePos = state.doc.lineAt(range.head).from
        if (linePos > last) {
            last = linePos
            marks.add(activeLineGutterMarker.range(linePos))
        }
    }
    RangeSet.of(marks)
}

/**
 * Returns an extension that adds a `cm-activeLineGutter` class to
 * all gutter elements on the [active line](#view.highlightActiveLine).
 */
fun highlightActiveLineGutter(): Extension {
    return activeLineGutterHighlighter
}
