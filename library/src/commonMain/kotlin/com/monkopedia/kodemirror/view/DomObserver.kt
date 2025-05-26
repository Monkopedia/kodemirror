package com.monkopedia.kodemirror.view

import browser from "./browser"
import {ContentView, ViewFlag} from "./contentview"
import {EditorView} from "./editorview"
import {editable, ViewUpdate, setEditContextFormatting, MeasureRequest} from "./extension"
import {hasSelection, getSelection, DOMSelectionState, isEquivalentPosition, dispatchKey, atElementStart} from "./dom"
import {DOMChange, applyDOMChange, applyDOMChangeInner} from "./domchange"
import type {EditContext} from "./editcontext"
import {Decoration} from "./decoration"
import {Text, EditorSelection, EditorState} from "@codemirror/state"
import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.dom.*
import com.monkopedia.kodemirror.extension.*
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.*

/**
 * Options for the mutation observer.
 */
private val observeOptions = object : MutationObserverInit {
    override var childList: Boolean = true
    override var characterData: Boolean = true
    override var subtree: Boolean = true
    override var attributes: Boolean = true
    override var characterDataOldValue: Boolean = true
}

/**
 * Whether to use character data events (for IE11 support).
 */
private val useCharData = browser.ie && browser.ie_version <= 11

/**
 * Manages DOM observation and synchronization.
 */
class DOMObserver(private val view: EditorView) {
    val dom: HTMLElement = view.contentDOM
    var win: Window = view.win

    private val observer = MutationObserver { mutations ->
        mutations.forEach { queue.add(it) }
        // IE11 will sometimes call the observer callback before actually updating the DOM
        // iOS Safari will sometimes first clear composition, deliver mutations, then reinsert text
        if ((browser.ie && browser.ie_version <= 11 || browser.ios && view.composing) &&
            mutations.any { mut ->
                mut.type == "childList" && mut.removedNodes.length > 0 ||
                mut.type == "characterData" && mut.oldValue!!.length > mut.target.nodeValue!!.length
            }
        ) {
            flushSoon()
        } else {
            flush()
        }
    }

    var active: Boolean = false
    var editContext: EditContextManager? = null

    // Selection state tracking
    val selectionRange = DOMSelectionState()
    var selectionChanged = false

    // Timing state
    var delayedFlush: Int = -1
    var resizeTimeout: Int = -1
    val queue = mutableListOf<MutationRecord>()
    var delayedAndroidKey: DelayedAndroidKey? = null
    var flushingAndroidKey: Int = -1
    var lastChange: Double = 0.0

    // Event handlers
    var onCharData: ((Event) -> Unit)? = null

    // Scroll and intersection observers
    val scrollTargets = mutableListOf<HTMLElement>()
    var intersection: IntersectionObserver? = null
    var resizeScroll: ResizeObserver? = null
    var intersecting: Boolean = false
    var gapIntersection: IntersectionObserver? = null
    var gaps = listOf<HTMLElement>()
    var printQuery: MediaQueryList? = null

    // Parent check timeout
    var parentCheck: Int = -1

    init {
        if (window.asDynamic().EditContext != null && 
            (view.constructor.asDynamic().EDIT_CONTEXT as? Boolean) != false &&
            !(browser.chrome && browser.chrome_version < 126)) {
            editContext = EditContextManager(view)
            if (view.state.facet(editable)) {
                view.contentDOM.asDynamic().editContext = editContext?.editContext
            }
        }

        if (useCharData) {
            onCharData = { event ->
                val mutEvent = event as MutationEvent
                queue.add(MutationRecord().apply {
                    target = mutEvent.target as Node
                    type = "characterData"
                    oldValue = mutEvent.prevValue
                })
                flushSoon()
            }
        }

        if (window.matchMedia != null) {
            printQuery = window.matchMedia("print")
        }

        if (js("typeof ResizeObserver") != "undefined") {
            resizeScroll = ResizeObserver { entries ->
                if ((view.docView?.lastUpdate ?: 0.0) < Date.now() - 75) onResize()
            }.also { it.observe(view.scrollDOM) }
        }

        addWindowListeners(win)
        start()

        if (js("typeof IntersectionObserver") != "undefined") {
            intersection = IntersectionObserver({ entries ->
                if (parentCheck < 0) {
                    parentCheck = window.setTimeout({ listenForScroll() }, 1000)
                }
                if (entries.isNotEmpty() && 
                    (entries.last().intersectionRatio > 0) != intersecting) {
                    intersecting = !intersecting
                    if (intersecting != view.inView) {
                        onScrollChanged(document.createEvent("Event"))
                    }
                }
            }, object : IntersectionObserverInit {
                override var threshold = arrayOf(0.0, 0.001)
            })
            intersection?.observe(dom)

            gapIntersection = IntersectionObserver({ entries ->
                if (entries.isNotEmpty() && entries.last().intersectionRatio > 0) {
                    onScrollChanged(document.createEvent("Event"))
                }
            })
        }

        listenForScroll()
        readSelectionRange()
    }

    /**
     * Handle scroll changes.
     */
    fun onScrollChanged(e: Event) {
        view.inputState.runHandlers("scroll", e)
        if (intersecting) view.measure()
    }

    /**
     * Handle scroll events.
     */
    fun onScroll(e: Event) {
        if (intersecting) flush(false)
        editContext?.let { view.requestMeasure(it.measureReq) }
        onScrollChanged(e)
    }

    /**
     * Handle resize events.
     */
    fun onResize() {
        if (resizeTimeout < 0) {
            resizeTimeout = window.setTimeout({
                resizeTimeout = -1
                view.requestMeasure()
            }, 50)
        }
    }

    /**
     * Handle print events.
     */
    fun onPrint(event: Event) {
        if ((event.type == "change" || event.type.isEmpty()) && 
            !(event as? MediaQueryListEvent)?.matches == true) return
        
        view.viewState.printing = true
        view.measure()
        window.setTimeout({
            view.viewState.printing = false
            view.requestMeasure()
        }, 500)
    }

    /**
     * Update gap intersections.
     */
    fun updateGaps(gaps: List<HTMLElement>) {
        if (gapIntersection != null && 
            (gaps.size != this.gaps.size || this.gaps.zip(gaps).any { (a, b) -> a != b })) {
            gapIntersection?.disconnect()
            gaps.forEach { gapIntersection?.observe(it) }
            this.gaps = gaps
        }
    }

    /**
     * Handle selection changes.
     */
    fun onSelectionChange(event: Event) {
        val wasChanged = selectionChanged
        if (!readSelectionRange() || delayedAndroidKey != null) return
        
        if (view.state.facet(editable)) {
            if (view.root.activeElement != dom) return
        } else {
            if (!hasSelection(dom, selectionRange)) return
        }

        val context = selectionRange.anchorNode?.let { view.docView.nearest(it) }
        if (context?.ignoreEvent(event) == true) {
            if (!wasChanged) selectionChanged = false
            return
        }

        // Handle selection change events in specific browser cases
        if ((browser.ie && browser.ieVersion <= 11 || browser.android && browser.chrome) && 
            !view.state.selection.main.empty &&
            selectionRange.focusNode?.let { 
                isEquivalentPosition(
                    it, 
                    selectionRange.focusOffset, 
                    selectionRange.anchorNode, 
                    selectionRange.anchorOffset
                )
            } == true) {
            flushSoon()
        } else {
            flush(false)
        }
    }

    /**
     * Read the selection range.
     */
    fun readSelectionRange(): Boolean {
        val selection = getSelection(view.root) ?: return false
        
        val range = if (browser.safari && view.root.nodeType == 11 &&
            view.root.activeElement == dom) {
            safariSelectionRangeHack(view, selection)
        } else {
            selection
        } ?: selection

        if (range == null || selectionRange.eq(range)) return false
        
        val local = hasSelection(dom, range)
        
        // Handle browser-specific focus behavior
        if (local && !selectionChanged &&
            view.inputState.lastFocusTime > Date.now() - 200 &&
            view.inputState.lastTouchTime < Date.now() - 300 &&
            atElementStart(dom, range)) {
            view.inputState.lastFocusTime = 0.0
            view.docView.updateSelection()
            return false
        }
        
        selectionRange.setRange(range)
        if (local) selectionChanged = true
        return true
    }

    /**
     * Set the selection range.
     */
    fun setSelectionRange(anchor: DOMPos, head: DOMPos) {
        selectionRange.set(anchor.node, anchor.offset, head.node, head.offset)
        selectionChanged = false
    }

    /**
     * Clear the selection range.
     */
    fun clearSelectionRange() {
        selectionRange.set(null, 0, null, 0)
    }

    /**
     * Listen for scroll events.
     */
    fun listenForScroll() {
        parentCheck = -1
        var i = 0
        var changed: MutableList<HTMLElement>? = null
        
        var dom: Node? = this.dom
        while (dom != null) {
            if (dom.nodeType == Node.ELEMENT_NODE) {
                if (changed == null && i < scrollTargets.size && scrollTargets[i] == dom) {
                    i++
                } else {
                    if (changed == null) changed = scrollTargets.subList(0, i).toMutableList()
                    changed.add(dom as HTMLElement)
                }
                dom = (dom as? HTMLElement)?.assignedSlot ?: dom.parentNode
            } else if (dom.nodeType == Node.DOCUMENT_FRAGMENT_NODE) {
                dom = (dom as? ShadowRoot)?.host
            } else {
                break
            }
        }

        if (i < scrollTargets.size && changed == null) {
            changed = scrollTargets.subList(0, i).toMutableList()
        }

        if (changed != null) {
            scrollTargets.forEach { it.removeEventListener("scroll", ::onScroll) }
            scrollTargets.clear()
            scrollTargets.addAll(changed)
            scrollTargets.forEach { it.addEventListener("scroll", ::onScroll) }
        }
    }

    /**
     * Ignore mutations during a function call.
     */
    fun <T> ignore(f: () -> T): T {
        if (!active) return f()
        try {
            stop()
            return f()
        } finally {
            start()
            clear()
        }
    }

    /**
     * Start observing mutations.
     */
    fun start() {
        if (active) return
        observer.observe(dom, observeOptions)
        if (useCharData) {
            dom.addEventListener("DOMCharacterDataModified", onCharData!!)
        }
        active = true
    }

    /**
     * Stop observing mutations.
     */
    fun stop() {
        if (!active) return
        active = false
        observer.disconnect()
        if (useCharData) {
            dom.removeEventListener("DOMCharacterDataModified", onCharData!!)
        }
    }

    /**
     * Clear pending mutations.
     */
    fun clear() {
        processRecords()
        queue.clear()
        selectionChanged = false
    }

    /**
     * Handle Android key events.
     */
    fun delayAndroidKey(key: String, keyCode: Int, force: Boolean = false) {
        if (!force && delayedAndroidKey?.key == key) return
        delayedAndroidKey?.let {
            clearDelayedAndroidKey()
            forceFlush()
        }
        delayedAndroidKey = DelayedAndroidKey(
            key = key,
            keyCode = keyCode,
            force = lastChange < Date.now() - 50 || delayedAndroidKey?.force == true
        )
        if (force) forceFlush()
    }

    /**
     * Clear delayed Android key.
     */
    fun clearDelayedAndroidKey() {
        window.clearTimeout(flushingAndroidKey)
        delayedAndroidKey = null
        flushingAndroidKey = -1
    }

    /**
     * Schedule a flush soon.
     */
    fun flushSoon() {
        if (delayedFlush < 0) {
            delayedFlush = window.setTimeout({
                delayedFlush = -1
                flush()
            }, 50)
        }
    }

    /**
     * Force a flush.
     */
    fun forceFlush() {
        if (delayedFlush >= 0) {
            window.clearTimeout(delayedFlush)
            delayedFlush = -1
        }
        flush()
    }

    /**
     * Get pending mutation records.
     */
    fun pendingRecords(): List<MutationRecord> {
        observer.takeRecords().forEach { queue.add(it) }
        return queue
    }

    /**
     * Process mutation records.
     */
    fun processRecords(): ProcessedRecords {
        val records = pendingRecords()
        if (records.isNotEmpty()) queue.clear()

        var from = -1
        var to = -1
        var typeOver = false
        
        for (record in records) {
            val range = readMutation(record) ?: continue
            if (range.typeOver) typeOver = true
            if (from == -1) {
                from = range.from
                to = range.to
            } else {
                from = minOf(range.from, from)
                to = maxOf(range.to, to)
            }
        }
        
        return ProcessedRecords(from, to, typeOver)
    }

    /**
     * Read DOM changes.
     */
    fun readChange(): DOMChange? {
        val (from, to, typeOver) = processRecords()
        val newSel = selectionChanged && hasSelection(dom, selectionRange)
        
        if (from < 0 && !newSel) return null
        if (from > -1) lastChange = Date.now()
        
        view.inputState.lastFocusTime = 0.0
        selectionChanged = false
        
        val change = DOMChange(view, from, to, typeOver)
        view.docView.domChanged = DOMChangedInfo(newSel = change.newSel?.main)
        return change
    }

    /**
     * Flush changes.
     */
    fun flush(readSelection: Boolean = true): Boolean {
        if (delayedFlush >= 0 || delayedAndroidKey != null) return false

        if (readSelection) readSelectionRange()

        val domChange = readChange() ?: run {
            view.requestMeasure()
            return false
        }
        
        val startState = view.state
        val handled = applyDOMChange(view, domChange)
        
        if (view.state == startState &&
            (domChange.domChanged || 
             domChange.newSel?.let { !it.main.eq(view.state.selection.main) } == true)) {
            view.update(emptyList())
        }
        
        return handled
    }

    /**
     * Read a mutation.
     */
    fun readMutation(rec: MutationRecord): MutationRange? {
        val cView = view.docView.nearest(rec.target) ?: return null
        if (cView.ignoreMutation(rec)) return null
        
        cView.markDirty(rec.type == "attributes")
        if (rec.type == "attributes") cView.flags = cView.flags or ViewFlag.AttrsDirty

        return when (rec.type) {
            "childList" -> {
                val childBefore = findChild(cView, rec.previousSibling ?: rec.target.previousSibling, -1)
                val childAfter = findChild(cView, rec.nextSibling ?: rec.target.nextSibling, 1)
                MutationRange(
                    from = childBefore?.let { cView.posAfter(it) } ?: cView.posAtStart,
                    to = childAfter?.let { cView.posBefore(it) } ?: cView.posAtEnd,
                    typeOver = false
                )
            }
            "characterData" -> MutationRange(
                from = cView.posAtStart,
                to = cView.posAtEnd,
                typeOver = rec.target.nodeValue == rec.oldValue
            )
            else -> null
        }
    }

    /**
     * Set the window.
     */
    fun setWindow(win: Window) {
        if (win != this.win) {
            removeWindowListeners(this.win)
            this.win = win
            addWindowListeners(this.win)
        }
    }

    /**
     * Add window listeners.
     */
    fun addWindowListeners(win: Window) {
        win.addEventListener("resize", ::onResize)
        if (printQuery != null) {
            if (printQuery!!.asDynamic().addEventListener != null) {
                printQuery!!.addEventListener("change", ::onPrint)
            } else {
                printQuery!!.addListener(::onPrint)
            }
        } else {
            win.addEventListener("beforeprint", ::onPrint)
        }
        win.addEventListener("scroll", ::onScroll)
        win.document.addEventListener("selectionchange", ::onSelectionChange)
    }

    /**
     * Remove window listeners.
     */
    fun removeWindowListeners(win: Window) {
        win.removeEventListener("scroll", ::onScroll)
        win.removeEventListener("resize", ::onResize)
        if (printQuery != null) {
            if (printQuery!!.asDynamic().removeEventListener != null) {
                printQuery!!.removeEventListener("change", ::onPrint)
            } else {
                printQuery!!.removeListener(::onPrint)
            }
        } else {
            win.removeEventListener("beforeprint", ::onPrint)
        }
        win.document.removeEventListener("selectionchange", ::onSelectionChange)
    }

    /**
     * Update the observer.
     */
    fun update(update: ViewUpdate) {
        editContext?.let { context ->
            context.update(update)
            if (update.startState.facet(editable) != update.state.facet(editable)) {
                update.view.contentDOM.asDynamic().editContext = 
                    if (update.state.facet(editable)) context.editContext else null
            }
        }
    }

    /**
     * Destroy the observer.
     */
    fun destroy() {
        stop()
        intersection?.disconnect()
        gapIntersection?.disconnect()
        resizeScroll?.disconnect()
        scrollTargets.forEach { it.removeEventListener("scroll", ::onScroll) }
        removeWindowListeners(win)
        window.clearTimeout(parentCheck)
        window.clearTimeout(resizeTimeout)
        window.clearTimeout(delayedFlush)
        window.clearTimeout(flushingAndroidKey)
        editContext?.let {
            view.contentDOM.asDynamic().editContext = null
            it.destroy()
        }
    }
}

/**
 * Find a child view.
 */
private fun findChild(cView: ContentView, dom: Node?, dir: Int): ContentView? {
    var current = dom
    while (current != null) {
        val curView = ContentView.get(current)
        if (curView != null && curView.parent == cView) return curView
        val parent = current.parentNode
        current = if (parent != cView.dom) parent else {
            if (dir > 0) current.nextSibling else current.previousSibling
        }
    }
    return null
}

/**
 * Build a selection range from a static range.
 */
private fun buildSelectionRangeFromRange(view: EditorView, range: StaticRange): SelectionRange {
    var anchorNode = range.startContainer
    var anchorOffset = range.startOffset
    var focusNode = range.endContainer
    var focusOffset = range.endOffset
    
    val curAnchor = view.docView.domAtPos(view.state.selection.main.anchor)
    
    // Flip the range if its end matches the current anchor
    if (isEquivalentPosition(curAnchor.node, curAnchor.offset, focusNode, focusOffset)) {
        val temp = anchorNode to anchorOffset
        anchorNode = focusNode
        anchorOffset = focusOffset
        focusNode = temp.first
        focusOffset = temp.second
    }
    
    return object : SelectionRange {
        override val anchorNode = anchorNode
        override val anchorOffset = anchorOffset
        override val focusNode = focusNode
        override val focusOffset = focusOffset
    }
}

/**
 * Represents a mutation range.
 */
private data class MutationRange(
    val from: Int,
    val to: Int,
    val typeOver: Boolean
)

/**
 * Represents processed mutation records.
 */
private data class ProcessedRecords(
    val from: Int,
    val to: Int,
    val typeOver: Boolean
)

/**
 * Used to work around a Safari Selection/shadow DOM bug (#414).
 */
private fun safariSelectionRangeHack(view: EditorView, selection: Selection): Selection? {
    if (selection.asDynamic().getComposedRanges != null) {
        val range = selection.asDynamic().getComposedRanges(view.root)[0] as? StaticRange
        if (range != null) return buildSelectionRangeFromRange(view, range)
    }

    var found: StaticRange? = null
    
    // Safari hack to get selection in shadow root using execCommand
    fun read(event: InputEvent) {
        event.preventDefault()
        event.stopImmediatePropagation()
        found = event.asDynamic().getTargetRanges()[0]
    }
    
    view.contentDOM.addEventListener("beforeinput", ::read, true)
    view.dom.ownerDocument.execCommand("indent")
    view.contentDOM.removeEventListener("beforeinput", ::read, true)
    
    return found?.let { buildSelectionRangeFromRange(view, it) }
}

/**
 * Information about DOM changes.
 */
private data class DOMChangedInfo(
    val newSel: SelectionRange? = null
)

/**
 * Represents a delayed Android key event.
 */
data class DelayedAndroidKey(
    val key: String,
    val keyCode: Int,
    val force: Boolean = false
)

/**
 * Manages EditContext functionality.
 */
class EditContextManager(private val view: EditorView) {
    val editContext: EditContext = window.asDynamic().EditContext(object {
        val text = view.state.doc.sliceString(from, to)
        val selectionStart = toContextPos(maxOf(from, minOf(to, view.state.selection.main.anchor)))
        val selectionEnd = toContextPos(view.state.selection.main.head)
    }).unsafeCast<EditContext>()

    val measureReq = MeasureRequest<Unit>(
        read = { view ->
            editContext.updateControlBounds(view.contentDOM.getBoundingClientRect())
            val sel = getSelection(view.root)
            if (sel != null && sel.rangeCount > 0) {
                editContext.updateSelectionBounds(sel.getRangeAt(0).getBoundingClientRect())
            }
        }
    )

    // Document window state
    var from: Int = 0
    var to: Int = 0
    
    // Pending context change
    var pendingContextChange: ContextChange? = null
    
    // Event handlers
    val handlers = mutableMapOf<String, (dynamic) -> Unit>()
    
    // Composition state
    var composing: ComposingState? = null

    init {
        resetRange(view.state)
        setupHandlers()
    }

    private fun setupHandlers() {
        handlers["textupdate"] = { e ->
            val anchor = view.state.selection.main.anchor
            val from = toEditorPos(e.updateRangeStart)
            val to = toEditorPos(e.updateRangeEnd)
            
            if (view.inputState.composing >= 0 && composing == null) {
                composing = ComposingState(
                    contextBase = e.updateRangeStart,
                    editorBase = from,
                    drifted = false
                )
            }
            
            val change = ContextChange(
                from = from,
                to = to,
                insert = Text.of(e.text.split("\n"))
            )
            
            // Handle anchor-relative changes
            if (change.from == this.from && anchor < this.from) {
                change.from = anchor
            } else if (change.to == this.to && anchor > this.to) {
                change.to = anchor
            }

            // Skip empty changes
            if (change.from == change.to && change.insert.isEmpty()) return@let

            pendingContextChange = change
            if (!view.state.readOnly) {
                val newLen = this.to - this.from + (change.to - change.from + change.insert.length)
                applyDOMChangeInner(
                    view,
                    change,
                    EditorSelection.single(
                        toEditorPos(e.selectionStart, newLen),
                        toEditorPos(e.selectionEnd, newLen)
                    )
                )
            }
            
            // Revert if change wasn't flushed
            if (pendingContextChange != null) {
                revertPending(view.state)
                setSelection(view.state)
            }
        }

        handlers["characterboundsupdate"] = { e ->
            val rects = mutableListOf<DOMRect>()
            var prev: DOMRect? = null
            
            for (i in toEditorPos(e.rangeStart) until toEditorPos(e.rangeEnd)) {
                val rect = view.coordsForChar(i)
                prev = (rect?.let { DOMRect(it.left, it.top, it.right - it.left, it.bottom - it.top) }
                    ?: prev
                    ?: DOMRect())
                rects.add(prev)
            }
            
            editContext.updateCharacterBounds(e.rangeStart, rects.toTypedArray())
        }

        handlers["textformatupdate"] = { e ->
            val deco = mutableListOf<Decoration>()
            
            e.getTextFormats().forEach { format ->
                val lineStyle = format.underlineStyle
                val thickness = format.underlineThickness
                
                if (lineStyle != "None" && thickness != "None") {
                    val from = toEditorPos(format.rangeStart)
                    val to = toEditorPos(format.rangeEnd)
                    
                    if (from < to) {
                        val style = buildString {
                            append("text-decoration: underline ")
                            when (lineStyle) {
                                "Dashed" -> append("dashed ")
                                "Squiggle" -> append("wavy ")
                            }
                            append(if (thickness == "Thin") "1" else "2")
                            append("px")
                        }
                        deco.add(Decoration.mark(object { val attributes = object { val style = style } }).range(from, to))
                    }
                }
            }
            
            view.dispatch(object { val effects = setEditContextFormatting.of(Decoration.set(deco)) })
        }

        handlers["compositionstart"] = {
            if (view.inputState.composing < 0) {
                view.inputState.composing = 0
                view.inputState.compositionFirstChange = true
            }
        }

        handlers["compositionend"] = {
            view.inputState.composing = -1
            view.inputState.compositionFirstChange = null
            composing?.let {
                val drifted = it.drifted
                composing = null
                if (drifted) reset(view.state)
            }
        }

        handlers.forEach { (event, handler) ->
            editContext.addEventListener(event, handler)
        }
    }

    fun applyEdits(update: ViewUpdate): Boolean {
        var off = 0
        var abort = false
        var pending = pendingContextChange
        
        update.changes.iterChanges { fromA, toA, _fromB, _toB, insert ->
            if (abort) return@iterChanges

            val dLen = insert.length - (toA - fromA)
            if (pending != null && toA >= pending.to) {
                if (pending.from == fromA && pending.to == toA && pending.insert.eq(insert)) {
                    pending = null
                    pendingContextChange = null
                    off += dLen
                    this.to += dLen
                    return@iterChanges
                } else {
                    pending = null
                    revertPending(update.state)
                }
            }

            val adjustedFromA = fromA + off
            val adjustedToA = toA + off
            
            when {
                adjustedToA <= from -> {
                    from += dLen
                    to += dLen
                }
                adjustedFromA < to -> {
                    if (adjustedFromA < from || adjustedToA > to || 
                        (to - from) + insert.length > CxVp.MaxSize) {
                        abort = true
                        return@iterChanges
                    }
                    editContext.updateText(
                        toContextPos(adjustedFromA),
                        toContextPos(adjustedToA),
                        insert.toString()
                    )
                    to += dLen
                }
            }
            off += dLen
        }
        
        if (pending != null && !abort) revertPending(update.state)
        return !abort
    }

    fun update(update: ViewUpdate) {
        val reverted = pendingContextChange
        if (composing != null && (composing!!.drifted || update.transactions.any { tr ->
            !tr.isUserEvent("input.type") && tr.changes.touchesRange(from, to)
        })) {
            composing!!.drifted = true
            composing!!.editorBase = update.changes.mapPos(composing!!.editorBase)
        } else if (!applyEdits(update) || !rangeIsValid(update.state)) {
            pendingContextChange = null
            reset(update.state)
        } else if (update.docChanged || update.selectionSet || reverted != null) {
            setSelection(update.state)
        }
        
        if (update.geometryChanged || update.docChanged || update.selectionSet) {
            update.view.requestMeasure(measureReq)
        }
    }

    fun resetRange(state: EditorState) {
        val head = state.selection.main.head
        from = maxOf(0, head - CxVp.Margin)
        to = minOf(state.doc.length, head + CxVp.Margin)
    }

    fun reset(state: EditorState) {
        resetRange(state)
        editContext.updateText(0, editContext.text.length, state.doc.sliceString(from, to))
        setSelection(state)
    }

    fun revertPending(state: EditorState) {
        val pending = pendingContextChange!!
        pendingContextChange = null
        editContext.updateText(
            toContextPos(pending.from),
            toContextPos(pending.from + pending.insert.length),
            state.doc.sliceString(pending.from, pending.to)
        )
    }

    fun setSelection(state: EditorState) {
        val main = state.selection.main
        val start = toContextPos(maxOf(from, minOf(to, main.anchor)))
        val end = toContextPos(main.head)
        if (editContext.selectionStart != start || editContext.selectionEnd != end) {
            editContext.updateSelection(start, end)
        }
    }

    fun rangeIsValid(state: EditorState): Boolean {
        val head = state.selection.main.head
        return !(from > 0 && head - from < CxVp.MinMargin ||
            to < state.doc.length && to - head < CxVp.MinMargin ||
            to - from > CxVp.Margin * 3)
    }

    fun toEditorPos(contextPos: Int, clipLen: Int = to - from): Int {
        val pos = minOf(contextPos, clipLen)
        return composing?.let { c ->
            if (c.drifted) c.editorBase + (pos - c.contextBase) else pos + from
        } ?: (pos + from)
    }

    fun toContextPos(editorPos: Int): Int {
        return composing?.let { c ->
            if (c.drifted) c.contextBase + (editorPos - c.editorBase) else editorPos - from
        } ?: (editorPos - from)
    }

    fun destroy() {
        handlers.forEach { (event, handler) ->
            editContext.removeEventListener(event, handler)
        }
    }

    /**
     * Represents a context change.
     */
    data class ContextChange(
        var from: Int,
        var to: Int,
        val insert: Text
    )

    /**
     * Represents composition state.
     */
    data class ComposingState(
        val contextBase: Int,
        var editorBase: Int,
        var drifted: Boolean
    )
}
