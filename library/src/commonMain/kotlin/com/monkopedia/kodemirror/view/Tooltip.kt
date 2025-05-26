package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.dom.*
import kotlinx.browser.window

// Constants
private const val OUTSIDE = "-10000px"
private const val ARROW_SIZE = 7
private const val ARROW_OFFSET = 14
private const val HOVER_TIME = 300
private const val HOVER_MAX_DIST = 6
private const val TOOLTIP_MARGIN = 4

data class Measured(
    val visible: Rect,
    val parent: DOMRect,
    val pos: List<Rect?>,
    val size: List<DOMRect>,
    val space: Rect,
    val scaleX: Double,
    val scaleY: Double,
    val makeAbsolute: Boolean
)

/**
 * Describes a tooltip. Values of this type, when provided through
 * the [showTooltip] facet, control the individual tooltips on the editor.
 */
interface Tooltip {
    /** The document position at which to show the tooltip. */
    val pos: Int
    
    /** The end of the range annotated by this tooltip, if different from [pos]. */
    val end: Int?
    
    /** Creates the tooltip's DOM representation */
    fun create(view: EditorView): TooltipView
    
    /** Whether the tooltip should be shown above or below the target position. */
    val above: Boolean
    
    /** Whether the [above] option should be honored when there isn't enough space. */
    val strictSide: Boolean
    
    /** When true, shows a triangle connecting the tooltip element to position [pos]. */
    val arrow: Boolean
    
    /** Controls if tooltips are hidden when position is outside visible editor content. */
    val clip: Boolean
}

/**
 * Describes the way a tooltip is displayed.
 */
interface TooltipView {
    /** The DOM element to position over the editor. */
    val dom: HTMLElement
    
    /** Adjust the position of the tooltip relative to its anchor position. */
    val offset: Offset?
    
    /** Custom method for finding screen position */
    fun getCoords(pos: Int): Rect?
    
    /** Controls tooltip overlap behavior */
    val overlap: Boolean
    
    /** Called after tooltip is added to DOM */
    fun mount(view: EditorView) {}
    
    /** Update DOM for state changes */
    fun update(update: ViewUpdate) {}
    
    /** Called when tooltip is removed */
    fun destroy() {}
    
    /** Called when tooltip is positioned */
    fun positioned(space: Rect) {}
    
    /** Controls size restriction behavior */
    val resize: Boolean
}

data class Offset(
    val x: Int = 0,
    val y: Int = 0
)

// Facets
val showTooltip = Facet.define<Tooltip?> {
    enables = listOf(tooltipPlugin, baseTheme)
}

val showHoverTooltip = Facet.define<List<Tooltip>, List<Tooltip>> { inputs ->
    inputs.flatten()
}

// Base theme definition
val baseTheme = EditorView.baseTheme(
    ".cm-tooltip" to {
        zIndex = 500
        boxSizing = "border-box"
    },
    "&light .cm-tooltip" to {
        border = "1px solid #bbb"
        backgroundColor = "#f5f5f5"
    },
    "&light .cm-tooltip-section:not(:first-child)" to {
        borderTop = "1px solid #bbb"
    },
    "&dark .cm-tooltip" to {
        backgroundColor = "#333338"
        color = "white"
    },
    ".cm-tooltip-arrow" to {
        height = "${ARROW_SIZE}px"
        width = "${ARROW_SIZE * 2}px"
        position = "absolute"
        zIndex = -1
        overflow = "hidden"
    },
    // ... Add remaining theme definitions
)

// ... More code to follow in subsequent edits

class TooltipViewManager(
    private val view: EditorView,
    private val facet: FacetReader<List<Tooltip?>>,
    private val createTooltipView: (tooltip: Tooltip, after: TooltipView?) -> TooltipView,
    private val removeTooltipView: (tooltipView: TooltipView) -> Unit
) {
    private var input: List<Tooltip?> = view.state.facet(facet)
    var tooltips: List<Tooltip> = input.filterNotNull()
    var tooltipViews: List<TooltipView>

    init {
        var prev: TooltipView? = null
        tooltipViews = tooltips.map { t -> 
            createTooltipView(t, prev).also { prev = it }
        }
    }

    fun update(update: ViewUpdate, above: MutableList<Boolean>? = null): Boolean {
        val input = update.state.facet(facet)
        val tooltips = input.filterNotNull()
        if (input === this.input) {
            tooltipViews.forEach { t -> t.update(update) }
            return false
        }

        val tooltipViews = mutableListOf<TooltipView>()
        val newAbove = if (above != null) mutableListOf<Boolean>() else null

        for (i in tooltips.indices) {
            val tip = tooltips[i]
            var known = -1
            for (j in this.tooltips.indices) {
                val other = this.tooltips[j]
                if (other.create == tip.create) known = j
            }
            if (known < 0) {
                tooltipViews[i] = createTooltipView(tip, if (i > 0) tooltipViews[i - 1] else null)
                newAbove?.add(tip.above)
            } else {
                val tooltipView = this.tooltipViews[known]
                tooltipViews[i] = tooltipView
                newAbove?.let { it[i] = above[known] }
                tooltipView.update(update)
            }
        }

        this.tooltipViews.forEach { t ->
            if (t !in tooltipViews) {
                removeTooltipView(t)
                t.destroy()
            }
        }

        newAbove?.let { new ->
            new.forEachIndexed { i, value -> above[i] = value }
            if (above.size > new.size) {
                above.subList(new.size, above.size).clear()
            }
        }

        this.input = input
        this.tooltips = tooltips
        this.tooltipViews = tooltipViews
        return true
    }
}

// Helper function to set left style
private fun setLeftStyle(element: HTMLElement, value: Double) {
    val current = element.style.left.removeSuffix("px").toDoubleOrNull()
    if (current == null || abs(value - current) > 1) {
        element.style.left = "${value}px"
    }
}

class TooltipPlugin(private val view: EditorView) : ViewPlugin {
    private val manager: TooltipViewManager
    private val above = mutableListOf<Boolean>()
    private var measureReq: MeasureRequest
    private var inView = true
    private var position: String
    private var madeAbsolute = false
    private var parent: HTMLElement? = null
    private lateinit var container: HTMLElement
    private var classes: String
    private var intersectionObserver: IntersectionObserver? = null
    private var resizeObserver: ResizeObserver? = null
    private var lastTransaction = 0L
    private var measureTimeout = -1

    init {
        val config = view.state.facet(tooltipConfig)
        position = config.position
        parent = config.parent
        classes = view.themeClasses
        createContainer()
        
        measureReq = MeasureRequest(
            read = { readMeasure() },
            write = { writeMeasure(it) },
            key = this
        )
        
        resizeObserver = if (js("typeof ResizeObserver") != "undefined") {
            ResizeObserver { measureSoon() }
        } else null
        
        manager = TooltipViewManager(
            view = view,
            facet = showTooltip,
            createTooltipView = { t, p -> createTooltip(t, p) },
            removeTooltipView = { t ->
                resizeObserver?.unobserve(t.dom)
                t.dom.remove()
            }
        )
        
        above.addAll(manager.tooltips.map { it.above })
        
        intersectionObserver = if (js("typeof IntersectionObserver") != "undefined") {
            IntersectionObserver({ entries ->
                if (Date.now() > lastTransaction - 50 && 
                    entries.isNotEmpty() && 
                    entries.last().intersectionRatio < 1) {
                    measureSoon()
                }
            }, object { val threshold = arrayOf(1) })
        } else null
        
        observeIntersection()
        view.window.addEventListener("resize", ::measureSoon)
        maybeMeasure()
    }

    private fun createContainer() {
        if (parent != null) {
            container = document.createElement("div")
            container.style.position = "relative"
            container.className = view.themeClasses
            parent!!.appendChild(container)
        } else {
            container = view.dom
        }
    }

    private fun observeIntersection() {
        intersectionObserver?.let { observer ->
            observer.disconnect()
            manager.tooltipViews.forEach { tooltip ->
                observer.observe(tooltip.dom)
            }
        }
    }

    private fun measureSoon() {
        if (measureTimeout < 0) {
            measureTimeout = window.setTimeout({
                measureTimeout = -1
                maybeMeasure()
            }, 50)
        }
    }

    fun update(update: ViewUpdate) {
        if (update.transactions.isNotEmpty()) lastTransaction = Date.now()
        val updated = manager.update(update, above)
        if (updated) observeIntersection()
        
        var shouldMeasure = updated || update.geometryChanged
        val newConfig = update.state.facet(tooltipConfig)
        
        if (newConfig.position != position && !madeAbsolute) {
            position = newConfig.position
            manager.tooltipViews.forEach { t -> t.dom.style.position = position }
            shouldMeasure = true
        }
        
        if (newConfig.parent != parent) {
            parent?.let { container.remove() }
            parent = newConfig.parent
            createContainer()
            manager.tooltipViews.forEach { t -> container.appendChild(t.dom) }
            shouldMeasure = true
        } else if (parent != null && view.themeClasses != classes) {
            classes = container.className = view.themeClasses
        }
        
        if (shouldMeasure) maybeMeasure()
    }

    private fun createTooltip(tooltip: Tooltip, prev: TooltipView?): TooltipView {
        val tooltipView = tooltip.create(view)
        val before = prev?.dom
        tooltipView.dom.classList.add("cm-tooltip")
        
        if (tooltip.arrow && tooltipView.dom.querySelector(".cm-tooltip > .cm-tooltip-arrow") == null) {
            val arrow = document.createElement("div")
            arrow.className = "cm-tooltip-arrow"
            tooltipView.dom.appendChild(arrow)
        }
        
        tooltipView.dom.style.position = position
        tooltipView.dom.style.top = OUTSIDE
        tooltipView.dom.style.left = "0px"
        container.insertBefore(tooltipView.dom, before)
        
        tooltipView.mount(view)
        resizeObserver?.observe(tooltipView.dom)
        return tooltipView
    }

    fun destroy() {
        view.window.removeEventListener("resize", ::measureSoon)
        manager.tooltipViews.forEach { tooltipView ->
            tooltipView.dom.remove()
            tooltipView.destroy()
        }
        parent?.let { container.remove() }
        resizeObserver?.disconnect()
        intersectionObserver?.disconnect()
        window.clearTimeout(measureTimeout)
    }

    private fun readMeasure(): Measured {
        var scaleX = 1.0
        var scaleY = 1.0
        var makeAbsolute = false
        
        if (position == "fixed" && manager.tooltipViews.isNotEmpty()) {
            val dom = manager.tooltipViews[0].dom
            if (browser.gecko) {
                makeAbsolute = dom.offsetParent != container.ownerDocument.body
            } else if (dom.style.top == OUTSIDE && dom.style.left == "0px") {
                val rect = dom.getBoundingClientRect()
                makeAbsolute = abs(rect.top + 10000) > 1 || abs(rect.left) > 1
            }
        }
        
        if (makeAbsolute || position == "absolute") {
            parent?.let { parent ->
                val rect = parent.getBoundingClientRect()
                if (rect.width > 0 && rect.height > 0) {
                    scaleX = rect.width / parent.offsetWidth
                    scaleY = rect.height / parent.offsetHeight
                }
            } ?: run {
                val viewState = view.viewState
                scaleX = viewState.scaleX
                scaleY = viewState.scaleY
            }
        }
        
        val visible = view.scrollDOM.getBoundingClientRect()
        val margins = getScrollMargins(view)
        
        return Measured(
            visible = Rect(
                left = visible.left + margins.left,
                top = visible.top + margins.top,
                right = visible.right - margins.right,
                bottom = visible.bottom - margins.bottom
            ),
            parent = if (parent != null) container.getBoundingClientRect() else view.dom.getBoundingClientRect(),
            pos = manager.tooltips.mapIndexed { i, t ->
                val tv = manager.tooltipViews[i]
                tv.getCoords(t.pos) ?: view.coordsAtPos(t.pos)
            },
            size = manager.tooltipViews.map { it.dom.getBoundingClientRect() },
            space = view.state.facet(tooltipConfig).tooltipSpace(view),
            scaleX = scaleX,
            scaleY = scaleY,
            makeAbsolute = makeAbsolute
        )
    }

    private fun writeMeasure(measured: Measured) {
        if (measured.makeAbsolute) {
            madeAbsolute = true
            position = "absolute"
            manager.tooltipViews.forEach { t -> t.dom.style.position = "absolute" }
        }

        val (visible, space, scaleX, scaleY) = measured
        val others = mutableListOf<Rect>()

        for (i in manager.tooltips.indices) {
            val tooltip = manager.tooltips[i]
            val tView = manager.tooltipViews[i]
            val dom = tView.dom
            val pos = measured.pos[i]
            val size = measured.size[i]

            // Hide tooltips that are outside of the editor
            if (pos == null || tooltip.clip != false && (
                pos.bottom <= maxOf(visible.top, space.top) ||
                pos.top >= minOf(visible.bottom, space.bottom) ||
                pos.right < maxOf(visible.left, space.left) - 0.1 ||
                pos.left > minOf(visible.right, space.right) + 0.1
            )) {
                dom.style.top = OUTSIDE
                continue
            }

            val arrow = if (tooltip.arrow) tView.dom.querySelector(".cm-tooltip-arrow") as HTMLElement? else null
            val arrowHeight = if (arrow != null) ARROW_SIZE else 0
            val width = size.right - size.left
            val height = knownHeight[tView] ?: (size.bottom - size.top)
            val offset = tView.offset ?: Offset()
            val ltr = view.textDirection == Direction.LTR

            val left = if (size.width > space.right - space.left) {
                if (ltr) space.left else space.right - size.width
            } else {
                if (ltr) {
                    maxOf(space.left, minOf(pos.left - (if (arrow != null) ARROW_OFFSET else 0) + offset.x, space.right - width))
                } else {
                    minOf(maxOf(space.left, pos.left - width + (if (arrow != null) ARROW_OFFSET else 0) - offset.x), space.right - width)
                }
            }

            var above = this.above[i]
            if (!tooltip.strictSide && (
                if (above) {
                    pos.top - height - arrowHeight - offset.y < space.top
                } else {
                    pos.bottom + height + arrowHeight + offset.y > space.bottom
                }
            ) && above == (space.bottom - pos.bottom > pos.top - space.top)) {
                above = !above
                this.above[i] = above
            }

            val spaceVert = (if (above) pos.top - space.top else space.bottom - pos.bottom) - arrowHeight
            if (spaceVert < height && tView.resize != false) {
                if (spaceVert < view.defaultLineHeight) {
                    dom.style.top = OUTSIDE
                    continue
                }
                knownHeight[tView] = height
                dom.style.height = "${height / scaleY}px"
            } else if (dom.style.height.isNotEmpty()) {
                dom.style.height = ""
            }

            var top = if (above) {
                pos.top - height - arrowHeight - offset.y
            } else {
                pos.bottom + arrowHeight + offset.y
            }

            val right = left + width
            if (tView.overlap != true) {
                for (r in others) {
                    if (r.left < right && r.right > left && r.top < top + height && r.bottom > top) {
                        top = if (above) {
                            r.top - height - 2 - arrowHeight
                        } else {
                            r.bottom + arrowHeight + 2
                        }
                    }
                }
            }

            if (position == "absolute") {
                dom.style.top = "${(top - measured.parent.top) / scaleY}px"
                setLeftStyle(dom, (left - measured.parent.left) / scaleX)
            } else {
                dom.style.top = "${top / scaleY}px"
                setLeftStyle(dom, left / scaleX)
            }

            arrow?.let {
                val arrowLeft = pos.left + (if (ltr) offset.x else -offset.x) - (left + ARROW_OFFSET - ARROW_SIZE)
                it.style.left = "${arrowLeft / scaleX}px"
            }

            if (tView.overlap != true) {
                others.add(Rect(left, top, right, top + height))
            }

            dom.classList.toggle("cm-tooltip-above", above)
            dom.classList.toggle("cm-tooltip-below", !above)
            tView.positioned(measured.space)
        }
    }

    private fun maybeMeasure() {
        if (manager.tooltips.isNotEmpty()) {
            if (view.inView) view.requestMeasure(measureReq)
            if (inView != view.inView) {
                inView = view.inView
                if (!inView) {
                    manager.tooltipViews.forEach { tv ->
                        tv.dom.style.top = OUTSIDE
                    }
                }
            }
        }
    }

    companion object {
        val eventObservers = mapOf(
            "scroll" to { plugin: TooltipPlugin -> plugin.maybeMeasure() }
        )
    }
}

// WeakMap for storing known heights
private val knownHeight = WeakMap<TooltipView, Double>()

class HoverTooltipHost(private val view: EditorView) : TooltipView {
    override val dom = document.createElement("div").apply {
        classList.add("cm-tooltip-hover")
    }
    private var mounted = false
    private val manager: TooltipViewManager

    init {
        manager = TooltipViewManager(
            view = view,
            facet = showHoverTooltip,
            createTooltipView = { t, p -> createHostedView(t, p) },
            removeTooltipView = { t -> t.dom.remove() }
        )
    }

    private fun createHostedView(tooltip: Tooltip, prev: TooltipView?): TooltipView {
        val hostedView = tooltip.create(view)
        hostedView.dom.classList.add("cm-tooltip-section")
        dom.insertBefore(hostedView.dom, prev?.dom?.nextSibling ?: dom.firstChild)
        if (mounted) hostedView.mount(view)
        return hostedView
    }

    override fun mount(view: EditorView) {
        manager.tooltipViews.forEach { it.mount(view) }
        mounted = true
    }

    override fun positioned(space: Rect) {
        manager.tooltipViews.forEach { it.positioned(space) }
    }

    override fun update(update: ViewUpdate) {
        manager.update(update)
    }

    override fun destroy() {
        manager.tooltipViews.forEach { it.destroy() }
    }

    private fun <K : Any> passProp(name: K): K? {
        var value: K? = null
        for (view in manager.tooltipViews) {
            val given = view[name]
            if (given != null) {
                if (value == null) value = given
                else if (value != given) return null
            }
        }
        return value
    }

    override val offset get() = passProp(TooltipView::offset)
    override fun getCoords(pos: Int) = passProp(TooltipView::getCoords)?.invoke(pos)
    override val overlap get() = passProp(TooltipView::overlap) ?: false
    override val resize get() = passProp(TooltipView::resize) ?: true
}

// Compute the hover tooltip host
val showHoverTooltipHost = showTooltip.compute(listOf(showHoverTooltip)) { state ->
    val tooltips = state.facet(showHoverTooltip)
    if (tooltips.isEmpty()) null
    else Tooltip(
        pos = tooltips.minOf { it.pos },
        end = tooltips.maxOf { it.end ?: it.pos },
        create = { HoverTooltipHost(it) },
        above = tooltips[0].above,
        arrow = tooltips.any { it.arrow }
    )
}

// Helper functions for tooltip positioning
private fun isInTooltip(tooltip: HTMLElement, event: MouseEvent): Boolean {
    val rect = tooltip.getBoundingClientRect()
    var top = rect.top
    var bottom = rect.bottom
    
    tooltip.querySelector(".cm-tooltip-arrow")?.let { arrow ->
        val arrowRect = (arrow as HTMLElement).getBoundingClientRect()
        top = minOf(arrowRect.top, top)
        bottom = maxOf(arrowRect.bottom, bottom)
    }
    
    return event.clientX >= rect.left - TOOLTIP_MARGIN &&
           event.clientX <= rect.right + TOOLTIP_MARGIN &&
           event.clientY >= top - TOOLTIP_MARGIN &&
           event.clientY <= bottom + TOOLTIP_MARGIN
}

private fun isOverRange(
    view: EditorView,
    from: Int,
    to: Int,
    x: Double,
    y: Double,
    margin: Int
): Boolean {
    val rect = view.scrollDOM.getBoundingClientRect()
    val docBottom = view.documentTop + view.documentPadding.top + view.contentHeight
    
    if (rect.left > x || rect.right < x || rect.top > y || minOf(rect.bottom, docBottom) < y) {
        return false
    }
    
    val pos = view.posAtCoords(x, y, false)
    return pos in from..to
}

// Tooltip configuration
data class TooltipConfig(
    val position: String = "fixed",
    val parent: HTMLElement? = null,
    val tooltipSpace: (EditorView) -> Rect = { view ->
        Rect(0.0, 0.0, view.window.innerWidth.toDouble(), view.window.innerHeight.toDouble())
    }
)

val tooltipConfig = Facet.define<TooltipConfig, TooltipConfig> { values ->
    TooltipConfig(
        position = if (browser.ios) "absolute" else values.firstOrNull()?.position ?: "fixed",
        parent = values.firstOrNull()?.parent,
        tooltipSpace = values.firstOrNull()?.tooltipSpace ?: { view ->
            Rect(0.0, 0.0, view.window.innerWidth.toDouble(), view.window.innerHeight.toDouble())
        }
    )
}

// Hover tooltip source type
typealias HoverTooltipSource = (view: EditorView, pos: Int, side: Int) -> Tooltip?

class HoverPlugin(
    private val view: EditorView,
    private val source: HoverTooltipSource,
    private val field: StateField<List<Tooltip>>,
    private val setHover: StateEffectType<List<Tooltip>>,
    private val hoverTime: Int
) {
    private var lastMove = MousePosition(0.0, 0.0, view.dom, 0L)
    private var hoverTimeout = -1
    private var restartTimeout = -1
    private var pending: PendingHover? = null

    init {
        view.dom.addEventListener("mouseleave", ::mouseleave)
        view.dom.addEventListener("mousemove", ::mousemove)
    }

    fun update() {
        pending?.let {
            pending = null
            clearTimeout(restartTimeout)
            restartTimeout = setTimeout({ startHover() }, 20)
        }
    }

    val active: List<Tooltip>
        get() = view.state.field(field)

    private fun checkHover() {
        hoverTimeout = -1
        if (active.isNotEmpty()) return
        
        val hovered = Date.now() - lastMove.time
        if (hovered < hoverTime) {
            hoverTimeout = setTimeout({ checkHover() }, hoverTime - hovered)
        } else {
            startHover()
        }
    }

    private fun startHover() {
        clearTimeout(restartTimeout)
        val desc = view.docView.nearest(lastMove.target)
        if (desc == null) return

        val (pos, side) = when {
            desc is WidgetView -> desc.posAtStart to 1
            else -> {
                val pos = view.posAtCoords(lastMove.x, lastMove.y) ?: return
                val posCoords = view.coordsAtPos(pos) ?: return
                
                if (lastMove.y < posCoords.top || lastMove.y > posCoords.bottom ||
                    lastMove.x < posCoords.left - view.defaultCharacterWidth ||
                    lastMove.x > posCoords.right + view.defaultCharacterWidth) {
                    return
                }
                
                val bidi = view.bidiSpans(view.state.doc.lineAt(pos))
                    .find { it.from <= pos && it.to >= pos }
                val rtl = if (bidi?.dir == Direction.RTL) -1 else 1
                pos to (if (lastMove.x < posCoords.left) -rtl else rtl)
            }
        }

        val open = source(view, pos, side)
        
        if (open is Promise<*>) {
            val pending = PendingHover(pos).also { this.pending = it }
            open.then({ result ->
                if (this.pending == pending) {
                    this.pending = null
                    if (result != null && (result !is List<*> || result.isNotEmpty())) {
                        view.dispatch(effects = setHover.of(result.asList()))
                    }
                }
            }, { e -> logException(view.state, e, "hover tooltip") })
        } else if (open != null && (open !is List<*> || open.isNotEmpty())) {
            view.dispatch(effects = setHover.of(open.asList()))
        }
    }

    val tooltip: TooltipView?
        get() {
            val plugin = view.plugin(tooltipPlugin)
            val index = plugin?.manager?.tooltips?.indexOfFirst { it.create == HoverTooltipHost::create } ?: -1
            return if (index > -1) plugin?.manager?.tooltipViews?.get(index) else null
        }

    private fun mousemove(event: MouseEvent) {
        lastMove = MousePosition(
            x = event.clientX,
            y = event.clientY,
            target = event.target as HTMLElement,
            time = Date.now()
        )
        
        if (hoverTimeout < 0) {
            hoverTimeout = setTimeout({ checkHover() }, hoverTime)
        }
        
        val activeTooltips = active
        val currentTooltip = tooltip
        
        if ((activeTooltips.isNotEmpty() && currentTooltip != null && 
             !isInTooltip(currentTooltip.dom, event)) || pending != null) {
            val pos = (activeTooltips.firstOrNull() ?: pending)?.pos ?: return
            val end = activeTooltips.firstOrNull()?.end ?: pos
            
            if (pos == end) {
                if (view.posAtCoords(lastMove.x, lastMove.y) != pos) {
                    view.dispatch(effects = setHover.of(emptyList()))
                    pending = null
                }
            } else if (!isOverRange(view, pos, end, event.clientX, event.clientY, HOVER_MAX_DIST)) {
                view.dispatch(effects = setHover.of(emptyList()))
                pending = null
            }
        }
    }

    private fun mouseleave(event: MouseEvent) {
        clearTimeout(hoverTimeout)
        hoverTimeout = -1
        
        if (active.isNotEmpty()) {
            val currentTooltip = tooltip
            val inTooltip = currentTooltip?.dom?.contains(event.relatedTarget as? HTMLElement)
            
            if (!inTooltip) {
                view.dispatch(effects = setHover.of(emptyList()))
            } else {
                watchTooltipLeave(currentTooltip!!.dom)
            }
        }
    }

    private fun watchTooltipLeave(tooltip: HTMLElement) {
        val watch = { event: MouseEvent ->
            tooltip.removeEventListener("mouseleave", watch)
            if (active.isNotEmpty() && !view.dom.contains(event.relatedTarget as? HTMLElement)) {
                view.dispatch(effects = setHover.of(emptyList()))
            }
        }
        tooltip.addEventListener("mouseleave", watch)
    }

    fun destroy() {
        clearTimeout(hoverTimeout)
        view.dom.removeEventListener("mouseleave", ::mouseleave)
        view.dom.removeEventListener("mousemove", ::mousemove)
    }

    private data class MousePosition(
        val x: Double,
        val y: Double,
        val target: HTMLElement,
        val time: Long
    )

    private data class PendingHover(val pos: Int)
}

/**
 * Creates a tooltip extension with the given configuration.
 */
fun tooltips(config: TooltipConfig = TooltipConfig()): Extension {
    return tooltipConfig.of(config)
}

/**
 * Sets up a hover tooltip that shows up when the pointer hovers over ranges of text.
 */
fun hoverTooltip(
    source: HoverTooltipSource,
    options: HoverTooltipOptions = HoverTooltipOptions()
): Extension {
    val setHover = StateEffect.define<List<Tooltip>>()
    val hoverState = StateField.define<List<Tooltip>> {
        create { emptyList() }
        update { value, tr ->
            if (value.isNotEmpty()) {
                when {
                    options.hideOnChange && (tr.docChanged || tr.selection) -> emptyList()
                    options.hideOn != null -> value.filterNot { options.hideOn.invoke(tr, it) }
                    tr.docChanged -> value.mapNotNull { tooltip ->
                        val newPos = tr.changes.mapPos(tooltip.pos, -1, MapMode.TrackDel)
                        if (newPos != null) {
                            tooltip.copy(
                                pos = newPos,
                                end = tooltip.end?.let { tr.changes.mapPos(it) }
                            )
                        } else null
                    }
                    else -> value
                }
            } else emptyList()
        }
        provide { showHoverTooltip.from(it) }
    }

    return Extension(
        hoverState,
        ViewPlugin.define { view ->
            HoverPlugin(
                view = view,
                source = source,
                field = hoverState,
                setHover = setHover,
                hoverTime = options.hoverTime ?: HOVER_TIME
            )
        },
        showHoverTooltipHost
    )
}

data class HoverTooltipOptions(
    val hideOn: ((Transaction, Tooltip) -> Boolean)? = null,
    val hideOnChange: Boolean = false,
    val hoverTime: Int? = null
)

// Utility functions
fun getTooltip(view: EditorView, tooltip: Tooltip): TooltipView? {
    val plugin = view.plugin(tooltipPlugin)
    val found = plugin?.manager?.tooltips?.indexOf(tooltip) ?: -1
    return if (found >= 0) plugin?.manager?.tooltipViews?.get(found) else null
}

fun hasHoverTooltips(state: EditorState): Boolean {
    return state.facet(showHoverTooltip).isNotEmpty()
}

val closeHoverTooltipEffect = StateEffect.define<Unit>()
val closeHoverTooltips = closeHoverTooltipEffect.of(Unit)

fun repositionTooltips(view: EditorView) {
    view.plugin(tooltipPlugin)?.maybeMeasure()
}
