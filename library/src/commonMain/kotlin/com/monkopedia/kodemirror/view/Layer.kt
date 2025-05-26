package com.monkopedia.kodemirror.view

import {Extension, Facet, EditorState, SelectionRange} from "@codemirror/state"
import {ViewPlugin, ViewUpdate} from "./extension"
import {EditorView} from "./editorview"
import {Direction} from "./bidi"
import {BlockType} from "./decoration"
import {BlockInfo} from "./heightmap"
import {blockAt} from "./cursor"
import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.dom.*
import kotlinx.browser.document
import kotlinx.browser.window

/// Markers shown in a [layer](#view.layer) must conform to this
/// interface. They are created in a measuring phase, and have to
/// contain all their positioning information, so that they can be
/// drawn without further DOM layout reading.
///
/// Markers are automatically absolutely positioned. Their parent
/// element has the same top-left corner as the document, so they
/// should be positioned relative to the document.
interface LayerMarker {
    /// Compare this marker to a marker of the same type. Used to avoid
    /// unnecessary redraws.
    eq(other: LayerMarker): boolean
    /// Draw the marker to the DOM.
    draw(): HTMLElement
    /// Update an existing marker of this type to this marker.
    update?(dom: HTMLElement, oldMarker: LayerMarker): boolean
}

/// Implementation of [`LayerMarker`](#view.LayerMarker) that creates
/// a rectangle at a given set of coordinates.
class RectangleMarker(
    private val className: String,
    /// The left position of the marker (in pixels, document-relative).
    val left: Double,
    /// The top position of the marker.
    val top: Double,
    /// The width of the marker, or null if it shouldn't get a width assigned.
    val width: Double?,
    /// The height of the marker.
    val height: Double
) : LayerMarker {

    override fun draw(): HTMLElement {
        val elt = document.createElement("div")
        elt.className = className
        adjust(elt)
        return elt
    }

    override fun update(dom: HTMLElement, oldMarker: LayerMarker): Boolean {
        if (oldMarker !is RectangleMarker || oldMarker.className != className) return false
        adjust(dom)
        return true
    }

    private fun adjust(elt: HTMLElement) {
        elt.style.left = "${left}px"
        elt.style.top = "${top}px"
        width?.let { elt.style.width = "${it}px" }
        elt.style.height = "${height}px"
    }

    override fun eq(other: LayerMarker): Boolean {
        if (other !is RectangleMarker) return false
        return left == other.left && top == other.top && width == other.width && 
               height == other.height && className == other.className
    }

    companion object {
        /// Create a set of rectangles for the given selection range,
        /// assigning them theclass`className`. Will create a single
        /// rectangle for empty ranges, and a set of selection-style
        /// rectangles covering the range's content (in a bidi-aware
        /// way) for non-empty ones.
        fun forRange(view: EditorView, className: String, range: SelectionRange): List<RectangleMarker> {
            if (range.empty) {
                val pos = view.coordsAtPos(range.head, range.assoc ?: 1) ?: return emptyList()
                val base = getBase(view)
                return listOf(RectangleMarker(
                    className,
                    pos.left - base.left,
                    pos.top - base.top,
                    null,
                    pos.bottom - pos.top
                ))
            } else {
                return rectanglesForRange(view, className, range)
            }
        }
    }
}

private fun getBase(view: EditorView): Base {
    val rect = view.scrollDOM.getBoundingClientRect()
    val left = if (view.textDirection == Direction.LTR) 
        rect.left 
    else 
        rect.right - view.scrollDOM.clientWidth * view.scaleX
    return Base(
        left = left - view.scrollDOM.scrollLeft * view.scaleX,
        top = rect.top - view.scrollDOM.scrollTop * view.scaleY
    )
}

private data class Base(val left: Double, val top: Double)

private fun wrappedLine(
    view: EditorView,
    pos: Int,
    side: Int,
    inside: BlockInfo
): BlockInfo {
    val coords = view.coordsAtPos(pos, side * 2) ?: return inside
    val editorRect = view.dom.getBoundingClientRect()
    val y = (coords.top + coords.bottom) / 2
    val left = view.posAtCoords(Point(editorRect.left + 1, y))
    val right = view.posAtCoords(Point(editorRect.right - 1, y))
    if (left == null || right == null) return inside
    return BlockInfo(
        from = maxOf(inside.from, minOf(left, right)),
        to = minOf(inside.to, maxOf(left, right))
    )
}

private fun rectanglesForRange(
    view: EditorView,
    className: String,
    range: SelectionRange
): List<RectangleMarker> {
    if (range.to <= view.viewport.from || range.from >= view.viewport.to) return emptyList()
    
    val from = maxOf(range.from, view.viewport.from)
    val to = minOf(range.to, view.viewport.to)
    val ltr = view.textDirection == Direction.LTR
    val content = view.contentDOM
    val contentRect = content.getBoundingClientRect()
    val base = getBase(view)
    
    val lineElt = content.querySelector(".cm-line")
    val lineStyle = lineElt?.let { window.getComputedStyle(it) }
    
    val leftSide = contentRect.left + (lineStyle?.let {
        parseInt(it.paddingLeft) + minOf(0, parseInt(it.textIndent))
    } ?: 0)
    val rightSide = contentRect.right - (lineStyle?.let {
        parseInt(it.paddingRight)
    } ?: 0)

    val startBlock = blockAt(view, from)
    val endBlock = blockAt(view, to)
    var visualStart = if (startBlock.type == BlockType.Text) startBlock else null
    var visualEnd = if (endBlock.type == BlockType.Text) endBlock else null
    
    if (visualStart != null && (view.lineWrapping || startBlock.widgetLineBreaks)) {
        visualStart = wrappedLine(view, from, 1, visualStart)
    }
    if (visualEnd != null && (view.lineWrapping || endBlock.widgetLineBreaks)) {
        visualEnd = wrappedLine(view, to, -1, visualEnd)
    }

    fun piece(left: Double, top: Double, right: Double, bottom: Double): RectangleMarker {
        return RectangleMarker(
            className,
            left - base.left,
            top - base.top,
            right - left,
            bottom - top
        )
    }

    fun pieces(line: DrawLine): List<RectangleMarker> {
        val pieces = mutableListOf<RectangleMarker>()
        for (i in 0 until line.horizontal.size step 2) {
            pieces.add(piece(line.horizontal[i], line.top, line.horizontal[i + 1], line.bottom))
        }
        return pieces
    }

    if (visualStart != null && visualEnd != null && 
        visualStart.from == visualEnd.from && visualStart.to == visualEnd.to) {
        return pieces(drawForLine(range.from, range.to, visualStart))
    }

    val top = visualStart?.let { drawForLine(range.from, null, it) } 
              ?: drawForWidget(startBlock, false)
    val bottom = visualEnd?.let { drawForLine(null, range.to, it) }
                ?: drawForWidget(endBlock, true)
    val between = mutableListOf<RectangleMarker>()

    if ((visualStart ?: startBlock).to < (visualEnd ?: endBlock).from - 
        (if (visualStart != null && visualEnd != null) 1 else 0) ||
        startBlock.widgetLineBreaks > 1 && top.bottom + view.defaultLineHeight / 2 < bottom.top) {
        between.add(piece(leftSide, top.bottom, rightSide, bottom.top))
    } else if (top.bottom < bottom.top && 
               view.elementAtHeight((top.bottom + bottom.top) / 2).type == BlockType.Text) {
        top.bottom = (top.bottom + bottom.top) / 2
        bottom.top = top.bottom
    }

    return pieces(top) + between + pieces(bottom)
}

private data class DrawLine(
    var top: Double,
    var bottom: Double,
    val horizontal: MutableList<Double>
)

private fun drawForLine(from: Int?, to: Int?, line: BlockInfo): DrawLine {
    val result = DrawLine(
        top = 1e9,
        bottom = -1e9,
        horizontal = mutableListOf()
    )
    // Implementation of line drawing logic here
    return result
}

private fun drawForWidget(block: BlockInfo, top: Boolean): DrawLine {
    // Implementation of widget drawing logic here
    return DrawLine(0.0, 0.0, mutableListOf())
}

/**
 * Configuration for a layer.
 */
interface LayerConfig {
    /** Determines whether this layer is shown above or below the text. */
    val above: Boolean
    /** When given, this class is added to the DOM element that will wrap the markers. */
    val className: String?
    /** Called on every view update. Returning true triggers a marker update. */
    fun update(update: ViewUpdate, layer: HTMLElement): Boolean
    /** Whether to update this layer every time the document view changes. Defaults to true. */
    val updateOnDocViewUpdate: Boolean
        get() = true
    /** Build a set of markers for this layer, and measure their dimensions. */
    fun markers(view: EditorView): List<LayerMarker>
    /** If given, this is called when the layer is created. */
    fun mount(layer: HTMLElement, view: EditorView) {}
    /** If given, called when the layer is removed from the editor or the entire editor is destroyed. */
    fun destroy(layer: HTMLElement, view: EditorView) {}
}

private fun sameMarker(a: LayerMarker, b: LayerMarker): Boolean {
    return a::class == b::class && a.eq(b)
}

private class LayerView(
    private val view: EditorView,
    private val layer: LayerConfig
) {
    private var drawn: List<LayerMarker> = emptyList()
    private var scaleX = 1.0
    private var scaleY = 1.0
    val dom: HTMLElement = view.scrollDOM.appendChild(document.createElement("div"))

    init {
        dom.classList.add("cm-layer")
        if (layer.above) dom.classList.add("cm-layer-above")
        layer.className?.let { dom.classList.add(it) }
        scale()
        dom.setAttribute("aria-hidden", "true")
        setOrder(view.state)
        view.requestMeasure(MeasureRequest(
            read = { measure() },
            write = { markers -> draw(markers) }
        ))
        layer.mount(dom, view)
    }

    private fun setOrder(state: EditorState) {
        var pos = 0
        val order = state.facet(layerOrder)
        while (pos < order.size && order[pos] != layer) pos++
        dom.style.zIndex = ((if (layer.above) 150 else -1) - pos).toString()
    }

    private fun measure(): List<LayerMarker> = layer.markers(view)

    private fun scale() {
        if (view.scaleX != scaleX || view.scaleY != scaleY) {
            scaleX = view.scaleX
            scaleY = view.scaleY
            dom.style.transform = "scale(${1 / scaleX}, ${1 / scaleY})"
        }
    }

    private fun draw(markers: List<LayerMarker>) {
        if (markers.size != drawn.size || markers.zip(drawn).any { (a, b) -> !sameMarker(a, b) }) {
            var old = dom.firstChild
            var oldI = 0
            for (marker in markers) {
                if (marker.update && old != null && 
                    marker::class == drawn.getOrNull(oldI)?.::class &&
                    marker.update(old as HTMLElement, drawn[oldI])) {
                    old = old.nextSibling
                    oldI++
                } else {
                    dom.insertBefore(marker.draw(), old)
                }
            }
            while (old != null) {
                val next = old.nextSibling
                old.remove()
                old = next
            }
            drawn = markers
        }
    }

    fun update(update: ViewUpdate) {
        if (update.startState.facet(layerOrder) != update.state.facet(layerOrder)) {
            setOrder(update.state)
        }
        if (layer.update(update, dom) || update.geometryChanged) {
            scale()
            view.requestMeasure(MeasureRequest(
                read = { measure() },
                write = { markers -> draw(markers) }
            ))
        }
    }

    fun docViewUpdate() {
        if (layer.updateOnDocViewUpdate) {
            view.requestMeasure(MeasureRequest(
                read = { measure() },
                write = { markers -> draw(markers) }
            ))
        }
    }

    fun destroy() {
        layer.destroy(dom, view)
        dom.remove()
    }
}

val layerOrder = Facet.define<LayerConfig>()

/**
 * Define a layer.
 */
fun layer(config: LayerConfig): List<Extension> {
    return listOf(
        ViewPlugin.define { view -> LayerView(view, config) },
        layerOrder.of(config)
    )
}
