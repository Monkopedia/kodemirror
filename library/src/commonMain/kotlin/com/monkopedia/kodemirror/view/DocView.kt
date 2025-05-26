package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.ChangeSet
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.SelectionRange
import com.monkopedia.kodemirror.decoration.DecorationSet
import com.monkopedia.kodemirror.decoration.MarkDecoration
import com.monkopedia.kodemirror.decoration.Decoration
import com.monkopedia.kodemirror.decoration.PointDecoration
import com.monkopedia.kodemirror.dom.DOMPos
import com.monkopedia.kodemirror.dom.Rect
import com.monkopedia.kodemirror.dom.isEquivalentPosition
import com.monkopedia.kodemirror.dom.getSelection
import com.monkopedia.kodemirror.dom.hasSelection
import com.monkopedia.kodemirror.dom.textRange
import com.monkopedia.kodemirror.dom.textNodeBefore
import com.monkopedia.kodemirror.dom.textNodeAfter
import com.monkopedia.kodemirror.dom.clientRectsFor
import com.monkopedia.kodemirror.dom.scrollRectIntoView
import com.monkopedia.kodemirror.extension.ChangedRange
import com.monkopedia.kodemirror.extension.ScrollTarget
import com.monkopedia.kodemirror.extension.editable
import com.monkopedia.kodemirror.extension.scrollHandler
import com.monkopedia.kodemirror.extension.getScrollMargins
import com.monkopedia.kodemirror.extension.logException
import com.monkopedia.kodemirror.extension.setEditContextFormatting
import com.monkopedia.kodemirror.bidi.Direction

data class Composition(
    val range: ChangedRange,
    val text: Text,
    val marks: List<CompositionMark>,
    val line: HTMLElement
)

data class CompositionMark(
    val node: HTMLElement,
    val deco: MarkDecoration
)

class DocView(val view: EditorView) : ContentView() {
    lateinit var children: MutableList<BlockView>
    var decorations: List<DecorationSet> = emptyList()
    var dynamicDecorationMap: MutableList<Boolean> = mutableListOf(false)
    var domChanged: DOMChanged? = null
    var hasComposition: CompositionRange? = null
    var markedForComposition: MutableSet<ContentView> = mutableSetOf()
    var editContextFormatting = Decoration.none
    var lastCompositionAfterCursor = false

    // Track a minimum width for the editor. When measuring sizes in
    // measureVisibleLineHeights, this is updated to point at the width
    // of a given element and its extent in the document. When a change
    // happens in that range, these are reset. That way, once we've seen
    // a line/element of a given length, we keep the editor wide enough
    // to fit at least that element, until it is changed, at which point
    // we forget it again.
    var minWidth = 0.0
    var minWidthFrom = 0
    var minWidthTo = 0

    // Track whether the DOM selection was set in a lossy way, so that
    // we don't mess it up when reading it back it
    var impreciseAnchor: DOMPos? = null
    var impreciseHead: DOMPos? = null
    var forceSelection = false

    lateinit var dom: HTMLElement

    // Used by the resize observer to ignore resizes that we caused
    // ourselves
    var lastUpdate = System.currentTimeMillis()

    override val length: Int get() = view.state.doc.length

    init {
        setDOM(view.contentDOM)
        children = mutableListOf(LineView())
        children[0].setParent(this)
        updateDeco()
        updateInner(listOf(ChangedRange(0, 0, 0, view.state.doc.length)), 0, null)
    }

    // Update the document view to a given state.
    fun update(update: ViewUpdate) {
        var changedRanges = update.changedRanges
        if (minWidth > 0 && changedRanges.isNotEmpty()) {
            if (!changedRanges.all { (fromA, toA) -> toA < minWidthFrom || fromA > minWidthTo }) {
                minWidth = 0.0
                minWidthFrom = 0
                minWidthTo = 0
            } else {
                minWidthFrom = update.changes.mapPos(minWidthFrom, 1)
                minWidthTo = update.changes.mapPos(minWidthTo, 1)
            }
        }

        updateEditContextFormatting(update)

        var readCompositionAt = -1
        if (view.inputState.composing >= 0 && !view.observer.editContext) {
            domChanged?.newSel?.let { readCompositionAt = it.head }
            if (readCompositionAt == -1 && !touchesComposition(update.changes, hasComposition) && !update.selectionSet)
                readCompositionAt = update.state.selection.main.head
        }
        val composition = if (readCompositionAt > -1) findCompositionRange(view, update.changes, readCompositionAt) else null
        domChanged = null

        hasComposition?.let { oldComp ->
            markedForComposition.clear()
            val (from, to) = oldComp
            changedRanges = ChangedRange(from, to, update.changes.mapPos(from, -1), update.changes.mapPos(to, 1))
                .addToSet(changedRanges.toList())
        }
        hasComposition = composition?.let { CompositionRange(it.range.fromB, it.range.toB) }

        // When the DOM nodes around the selection are moved to another
        // parent, Chrome sometimes reports a different selection through
        // getSelection than the one that it actually shows to the user.
        // This forces a selection update when lines are joined to work
        // around that. Issue #54
        if (changedRanges.any { it.fromA == it.toA }) {
            val from = changedRanges[0].fromB
            val ranges = view.state.selection.ranges
            if (ranges.any { it.from <= from && it.to >= from }) forceSelection = true
        }

        val prevDeco = decorations
        updateDeco()

        updateInner(changedRanges, update.startState.doc.length,
            if (composition != null && view.inputState.composing >= 0) composition else null)

        if (view.state.facet(editable)) {
            // Ensure the DOM selection is in sync with the editor selection
            view.observer.updateSelection(
                (prevDeco != decorations && view.state.selection.main.empty) ||
                    view.root.activeElement != view.contentDOM || forceSelection,
                false
            )
        }
    }

    // Used by update and the constructor do perform the actual DOM
    // update
    private updateInner(changes: readonly ChangedRange[], oldLength: number, composition: Composition | null) {
        this.view.viewState.mustMeasureContent = true
        this.updateChildren(changes, oldLength, composition)

        let {observer} = this.view
        observer.ignore(() => {
            // Lock the height during redrawing, since Chrome sometimes
            // messes with the scroll position during DOM mutation (though
            // no relayout is triggered and I cannot imagine how it can
            // recompute the scroll position without a layout)
            this.dom.style.height = this.view.viewState.contentHeight / this.view.scaleY + "px"
            this.dom.style.flexBasis = this.minWidth ? this.minWidth + "px" : ""
            // Chrome will sometimes, when DOM mutations occur directly
            // around the selection, get confused and report a different
            // selection from the one it displays (issue #218). This tries
            // to detect that situation.
            let track = browser.chrome || browser.ios ? {node: observer.selectionRange.focusNode!, written: false} : undefined
            this.sync(this.view, track)
            this.flags &= ~ViewFlag.Dirty
            if (track && (track.written || observer.selectionRange.focusNode != track.node)) this.forceSelection = true
            this.dom.style.height = ""
        })
        this.markedForComposition.forEach(cView => cView.flags &= ~ViewFlag.Composition)
        let gaps = []
        if (this.view.viewport.from || this.view.viewport.to < this.view.state.doc.length) for (let child of this.children)
        if (child instanceof BlockWidgetView && child.widget instanceof BlockGapWidget) gaps.push(child.dom!)
        observer.updateGaps(gaps)
    }

    private updateChildren(changes: readonly ChangedRange[], oldLength: number, composition: Composition | null) {
        let ranges = composition ? composition.range.addToSet(changes.slice()) : changes
        let cursor = this.childCursor(oldLength)
        for (let i = ranges.length - 1;; i--) {
        let next = i >= 0 ? ranges[i] : null
        if (!next) break
        let {fromA, toA, fromB, toB} = next, content, breakAtStart, openStart, openEnd
        if (composition && composition.range.fromB < toB && composition.range.toB > fromB) {
            let before = ContentBuilder.build(this.view.state.doc, fromB, composition.range.fromB, this.decorations,
            this.dynamicDecorationMap)
            let after = ContentBuilder.build(this.view.state.doc, composition.range.toB, toB, this.decorations,
            this.dynamicDecorationMap)
            breakAtStart = before.breakAtStart
            openStart = before.openStart; openEnd = after.openEnd
            let compLine = this.compositionView(composition)
            if (after.breakAtStart) {
                compLine.breakAfter = 1
            } else if (after.content.length &&
                compLine.merge(compLine.length, compLine.length, after.content[0], false, after.openStart, 0)) {
                compLine.breakAfter = after.content[0].breakAfter
                after.content.shift()
            }
            if (before.content.length &&
                compLine.merge(0, 0, before.content[before.content.length - 1], true, 0, before.openEnd)) {
                before.content.pop()
            }
            content = before.content.concat(compLine).concat(after.content)
        } else {
            ;({content, breakAtStart, openStart, openEnd} =
                ContentBuilder.build(this.view.state.doc, fromB, toB, this.decorations, this.dynamicDecorationMap))
        }
        let {i: toI, off: toOff} = cursor.findPos(toA, 1)
        let {i: fromI, off: fromOff} = cursor.findPos(fromA, -1)
        replaceRange(this, fromI, fromOff, toI, toOff, content, breakAtStart, openStart, openEnd)
    }
        if (composition) this.fixCompositionDOM(composition)
    }

    private fun updateEditContextFormatting(update: ViewUpdate) {
        editContextFormatting = editContextFormatting.map(update.changes)
        for (tr in update.transactions) {
            for (effect in tr.effects) {
                if (effect.is(setEditContextFormatting)) {
                    editContextFormatting = effect.value
                }
            }
        }
    }

    private fun compositionView(composition: Composition): LineView {
        var cur: ContentView = TextView(composition.text.nodeValue!!)
        cur.flags = cur.flags or ViewFlag.Composition
        for (mark in composition.marks) {
            cur = MarkView(mark.deco, listOf(cur), cur.length)
        }
        val line = LineView()
        line.append(cur, 0)
        return line
    }

    private fun fixCompositionDOM(composition: Composition) {
        val fix = { dom: Node, cView: ContentView ->
            cView.flags = cView.flags or ViewFlag.Composition or
                (if (cView.children.any { it.flags and ViewFlag.Dirty != 0 }) ViewFlag.ChildDirty else 0)
            markedForComposition.add(cView)
            val prev = ContentView.get(dom)
            if (prev != null && prev != cView) prev.dom = null
            cView.setDOM(dom)
        }
        val pos = childPos(composition.range.fromB, 1)
        var cView: ContentView = children[pos.i]
        fix(composition.line, cView)
        for (i in composition.marks.indices.reversed() downTo -1) {
            val childPos = cView.childPos(pos.off, 1)
            cView = cView.children[childPos.i]
            fix(if (i >= 0) composition.marks[i].node else composition.text, cView)
        }
    }

    // Sync the DOM selection to this.state.selection
    fun updateSelection(mustRead: Boolean = false, fromPointer: Boolean = false) {
        if (mustRead || view.observer.selectionRange.focusNode == null) view.observer.readSelectionRange()
        val activeElt = view.root.activeElement
        val focused = activeElt == dom
        val selectionNotFocus = !focused && 
            !(view.state.facet(editable) || dom.tabIndex > -1) &&
            hasSelection(dom, view.observer.selectionRange) && 
            !(activeElt != null && dom.contains(activeElt))
        
        if (!(focused || fromPointer || selectionNotFocus)) return
        val force = forceSelection
        forceSelection = false

        val main = view.state.selection.main
        val anchor = moveToLine(domAtPos(main.anchor))
        val head = if (main.empty) anchor else moveToLine(domAtPos(main.head))

        // Always reset on Firefox when next to an uneditable node to
        // avoid invisible cursor bugs (#111)
        if (browser.gecko && main.empty && hasComposition == null && betweenUneditable(anchor)) {
            val dummy = document.createTextNode("")
            view.observer.ignore {
                anchor.node.insertBefore(dummy, anchor.node.childNodes[anchor.offset] ?: null)
            }
            anchor = DOMPos(dummy, 0)
            head = anchor
            force = true
        }

        val domSel = view.observer.selectionRange
        // If the selection is already here, or in an equivalent position, don't touch it
        if (force || domSel.focusNode == null || (
            !isEquivalentPosition(anchor.node, anchor.offset, domSel.anchorNode, domSel.anchorOffset) ||
            !isEquivalentPosition(head.node, head.offset, domSel.focusNode, domSel.focusOffset)
        ) && !suppressWidgetCursorChange(domSel, main)) {
            view.observer.ignore {
                // Selection.extend can be used to create an 'inverted' selection
                // (one where the focus is before the anchor), but not all
                // browsers support it yet.
                val domSel = getSelection(view.root)
                if (domSel == null) return@ignore
                if (main.anchor > main.head && domSel.extend) {
                    domSel.collapse(head.node, head.offset)
                    domSel.extend(anchor.node, anchor.offset)
                } else {
                    val range = document.createRange()
                    if (main.anchor > main.head) {
                        range.setEnd(anchor.node, anchor.offset)
                        range.setStart(head.node, head.offset)
                    } else {
                        range.setEnd(head.node, head.offset)
                        range.setStart(anchor.node, anchor.offset)
                    }
                    domSel.removeAllRanges()
                    domSel.addRange(range)
                }
            }
            impreciseAnchor = anchor
            impreciseHead = head
        }

        if (view.inputState.lastFocusTime > Date.now() - 50) {
            view.inputState.lastFocusTime = 0
            if (view.docView.impreciseHead?.node?.nodeType == 3) {
                view.contentDOM.blur()
                view.contentDOM.focus()
            }
        }
    }

    // If a zero-length widget is inserted next to the cursor during
    // composition, avoid moving it across it and disrupting the
    // composition.
    fun suppressWidgetCursorChange(sel: DOMSelectionState, cursor: SelectionRange): Boolean {
        return this.hasComposition && cursor.empty &&
            isEquivalentPosition(sel.focusNode!, sel.focusOffset, sel.anchorNode, sel.anchorOffset) &&
            this.posFromDOM(sel.focusNode!, sel.focusOffset) == cursor.head
    }

    fun enforceCursorAssoc() {
        if (this.hasComposition) return
        let {view} = this, cursor = view.state.selection.main
        let sel = getSelection(view.root)
        let {anchorNode, anchorOffset} = view.observer.selectionRange
        if (!sel || !cursor.empty || !cursor.assoc || !sel.modify) return
        let line = LineView.find(this, cursor.head)
        if (!line) return
        let lineStart = line.posAtStart
            if (cursor.head == lineStart || cursor.head == lineStart + line.length) return
        let before = this.coordsAt(cursor.head, -1), after = this.coordsAt(cursor.head, 1)
        if (!before || !after || before.bottom > after.top) return
        let dom = this.domAtPos(cursor.head + cursor.assoc)
        sel.collapse(dom.node, dom.offset)
        sel.modify("move", cursor.assoc < 0 ? "forward" : "backward", "lineboundary")
        // This can go wrong in corner cases like single-character lines,
        // so check and reset if necessary.
        view.observer.readSelectionRange()
        let newRange = view.observer.selectionRange
            if (view.docView.posFromDOM(newRange.anchorNode!, newRange.anchorOffset) != cursor.from)
                sel.collapse(anchorNode, anchorOffset)
    }

    // If a position is in/near a block widget, move it to a nearby text
    // line, since we don't want the cursor inside a block widget.
    fun moveToLine(pos: DOMPos): DOMPos {
        // Block widgets will return positions before/after them, which
        // are thus directly in the document DOM element.
        let dom = this.dom!, newPos
        if (pos.node != dom) return pos
        for (let i = pos.offset; !newPos && i < dom.childNodes.length; i++) {
        let view = ContentView.get(dom.childNodes[i])
        if (view instanceof LineView) newPos = view.domAtPos(0)
    }
        for (let i = pos.offset - 1; !newPos && i >= 0; i--) {
        let view = ContentView.get(dom.childNodes[i])
        if (view instanceof LineView) newPos = view.domAtPos(view.length)
    }
        return newPos ? new DOMPos(newPos.node, newPos.offset, true) : pos
    }

    fun nearest(dom: Node): ContentView | null {
        for (let cur: Node | null = dom; cur;) {
        let domView = ContentView.get(cur)
        if (domView && domView.rootView == this) return domView
        cur = cur.parentNode
    }
        return null
    }

    fun posFromDOM(node: Node, offset: number): number {
        let view = this.nearest(node)
        if (!view) throw new RangeError("Trying to find position for a DOM position outside of the document")
        return view.localPosFromDOM(node, offset) + view.posAtStart
    }

    fun domAtPos(pos: number): DOMPos {
        let {i, off} = this.childCursor().findPos(pos, -1)
        for (; i < this.children.length - 1;) {
        let child = this.children[i]
        if (off < child.length || child instanceof LineView) break
        i++; off = 0
    }
        return this.children[i].domAtPos(off)
    }

    fun coordsAt(pos: number, side: number): Rect | null {
        let best = null, bestPos = 0
        for (let off = this.length, i = this.children.length - 1; i >= 0; i--) {
        let child = this.children[i], end = off - child.breakAfter, start = end - child.length
        if (end < pos) break
        if (start <= pos && (start < pos || child.covers(-1)) && (end > pos || child.covers(1)) &&
            (!best || child instanceof LineView && !(best instanceof LineView && side >= 0))) {
            best = child
            bestPos = start
        } else if (best && start == pos && end == pos && child instanceof BlockWidgetView && Math.abs(side) < 2) {
            if (child.deco.startSide < 0) break
            else if (i) best = null
        }
        off = start
    }
        return best ? best.coordsAt(pos - bestPos, side) : null
    }

    fun coordsForChar(pos: number) {
        let {i, off} = this.childPos(pos, 1), child: ContentView = this.children[i]
        if (!(child instanceof LineView)) return null
        while (child.children.length) {
            let {i, off: childOff} = child.childPos(off, 1)
            for (;; i++) {
                if (i == child.children.length) return null
                if ((child = child.children[i]).length) break
            }
            off = childOff
        }
        if (!(child instanceof TextView)) return null
        let end = findClusterBreak(child.text, off)
        if (end == off) return null
        let rects = textRange(child.dom as Text, off, end).getClientRects()
        for (let i = 0; i < rects.length; i++) {
        let rect = rects[i]
        if (i == rects.length - 1 || rect.top < rect.bottom && rect.left < rect.right) return rect
    }
        return null
    }

    fun measureVisibleLineHeights(viewport: {from: number, to: number}) {
        let result = [], {from, to} = viewport
        let contentWidth = this.view.contentDOM.clientWidth
        let isWider = contentWidth > Math.max(this.view.scrollDOM.clientWidth, this.minWidth) + 1
        let widest = -1, ltr = this.view.textDirection == Direction.LTR
        for (let pos = 0, i = 0; i < this.children.length; i++) {
        let child = this.children[i], end = pos + child.length
        if (end > to) break
        if (pos >= from) {
            let childRect = child.dom!.getBoundingClientRect()
            result.push(childRect.height)
            if (isWider) {
                let last = child.dom!.lastChild
                    let rects = last ? clientRectsFor(last) : []
                if (rects.length) {
                    let rect = rects[rects.length - 1]
                    let width = ltr ? rect.right - childRect.left : childRect.right - rect.left
                    if (width > widest) {
                        widest = width
                        this.minWidth = contentWidth
                        this.minWidthFrom = pos
                        this.minWidthTo = end
                    }
                }
            }
        }
        pos = end + child.breakAfter
    }
        return result
    }

    fun textDirectionAt(pos: number) {
        let {i} = this.childPos(pos, 1)
        return getComputedStyle(this.children[i].dom!).direction == "rtl" ? Direction.RTL : Direction.LTR
    }

    fun measureTextSize(): TextSizeInfo? {
        for (child in children) {
            if (child is LineView) {
                val measure = child.measureTextSize()
                if (measure != null) return measure
            }
        }
        return null
    }

    fun childCursor(pos: Int = length): ChildCursor {
        // Move back to start of last element when possible, so that
        // `ChildCursor.findPos` doesn't have to deal with the edge case
        // of being after the last element.
        var i = children.size
        var off = pos
        while (i > 0) {
            val child = children[i - 1]
            val start = off - child.length - child.breakAfter
            if (off > start) break
            i--
            off = start
        }
        return ChildCursor(children, children.size, pos)
    }

    fun computeBlockGapDeco(): DecorationSet {
        val deco = mutableListOf<Decoration>()
        val vs = view.viewState
        var pos = 0
        var i = 0
        while (true) {
            val next = if (i < vs.viewports.size) vs.viewports[i].from else null
            if (next == null) break
            if (next > pos) {
                val widget = BlockGapWidget(vs.heightMap, pos, next, view)
                val dec = Decoration.widget({
                    widget = widget,
                    block = true,
                    side = -1
                })
                deco.add(dec.range(pos))
            }
            pos = vs.viewports[i].to
            i++
        }
        if (pos < view.state.doc.length) {
            val widget = BlockGapWidget(vs.heightMap, pos, view.state.doc.length, view)
            val dec = Decoration.widget({
                widget = widget,
                block = true,
                side = 1
            })
            deco.add(dec.range(pos))
        }
        return RangeSet.of(deco)
    }

    fun updateDeco(): List<DecorationSet> {
        var i = 1
        val allDeco = view.state.facet(decorationsFacet).map { d ->
            val dynamic = run {
                dynamicDecorationMap[i++] = d is Function<*>
                d is Function<*>
            }
            if (dynamic) (d as (EditorView) -> DecorationSet)(view) else d as DecorationSet
        }
        var dynamicOuter = false
        val outerDeco = view.state.facet(outerDecorations).map { d ->
            val dynamic = d is Function<*>
            if (dynamic) dynamicOuter = true
            if (dynamic) (d as (EditorView) -> DecorationSet)(view) else d as DecorationSet
        }
        if (outerDeco.isNotEmpty()) {
            dynamicDecorationMap[i++] = dynamicOuter
            allDeco.add(RangeSet.join(outerDeco))
        }
        decorations = listOf(
            editContextFormatting,
            *allDeco.toTypedArray(),
            computeBlockGapDeco(),
            view.viewState.lineGapDeco
        )
        while (i < decorations.size) dynamicDecorationMap[i++] = false
        return decorations
    }

    fun scrollIntoView(target: ScrollTarget) {
        if (target.isSnapshot) {
            val ref = view.viewState.lineBlockAt(target.range.head)
            view.scrollDOM.scrollTop = ref.top - target.yMargin
            view.scrollDOM.scrollLeft = target.xMargin
            return
        }

        for (handler in view.state.facet(scrollHandler)) {
            try {
                if (handler(view, target.range, target)) return
            } catch (e: Exception) {
                logException(view.state, e, "scroll handler")
            }
        }

        val range = target.range
        var rect = coordsAt(range.head, if (range.empty) range.assoc else if (range.head > range.anchor) -1 else 1)
        if (rect == null) return
        var other: Rect? = null
        if (!range.empty && (other = coordsAt(range.anchor, if (range.anchor > range.head) -1 else 1)) != null) {
            rect = Rect(
                left = minOf(rect.left, other.left),
                top = minOf(rect.top, other.top),
                right = maxOf(rect.right, other.right),
                bottom = maxOf(rect.bottom, other.bottom)
            )
        }

        val margins = getScrollMargins(view)
        val targetRect = Rect(
            left = rect.left - margins.left,
            top = rect.top - margins.top,
            right = rect.right + margins.right,
            bottom = rect.bottom + margins.bottom
        )

        val { offsetWidth, offsetHeight } = view.scrollDOM
        scrollRectIntoView(
            view.scrollDOM,
            targetRect,
            if (range.head < range.anchor) -1 else 1,
            target.x,
            target.y,
            maxOf(minOf(target.xMargin, offsetWidth), -offsetWidth),
            maxOf(minOf(target.yMargin, offsetHeight), -offsetHeight),
            view.textDirection == Direction.LTR
        )
    }

    // Will never be called but needs to be present
    fun split(): ContentView {
        // Implementation needed
        throw UnsupportedOperationException("Method not implemented")
    }
}

fun betweenUneditable(pos: DOMPos): Boolean {
    return pos.node.nodeType == 1 && pos.node.firstChild &&
        (pos.offset == 0 || (pos.node.childNodes[pos.offset - 1] as HTMLElement).contentEditable == "false") &&
        (pos.offset == pos.node.childNodes.length || (pos.node.childNodes[pos.offset] as HTMLElement).contentEditable == "false")
}

export fun findCompositionNode(view: EditorView, headPos: number): {from: number, to: number, node: Text} | null {
    let sel = view.observer.selectionRange
        if (!sel.focusNode) return null
    let textBefore = textNodeBefore(sel.focusNode, sel.focusOffset)
    let textAfter = textNodeAfter(sel.focusNode, sel.focusOffset)
    let textNode = textBefore || textAfter
        if (textAfter && textBefore && textAfter.node != textBefore.node) {
            let descAfter = ContentView.get(textAfter.node)
            if (!descAfter || descAfter instanceof TextView && descAfter.text != textAfter.node.nodeValue) {
                textNode = textAfter
            } else if (view.docView.lastCompositionAfterCursor) {
                let descBefore = ContentView.get(textBefore.node)
                if (!(!descBefore || descBefore instanceof TextView && descBefore.text != textBefore.node.nodeValue))
                    textNode = textAfter
            }
        }
    view.docView.lastCompositionAfterCursor = textNode != textBefore

    if (!textNode) return null
    let from = headPos - textNode.offset
    return {from, to: from + textNode.node.nodeValue!.length, node: textNode.node}
}

fun findCompositionRange(view: EditorView, changes: ChangeSet, headPos: number): Composition | null {
    let found = findCompositionNode(view, headPos)
    if (!found) return null
    let {node: textNode, from, to} = found, text = textNode.nodeValue!
    // Don't try to preserve multi-line compositions
    if (/[\n\r]/.test(text)) return null
    if (view.state.doc.sliceString(found.from, found.to) != text) return null

    let inv = changes.invertedDesc
        let range = new ChangedRange(inv.mapPos(from), inv.mapPos(to), from, to)
    let marks: {node: HTMLElement, deco: MarkDecoration}[] = []
    for (let parent = textNode.parentNode as HTMLElement;; parent = parent.parentNode as HTMLElement) {
        let parentView = ContentView.get(parent)
        if (parentView instanceof MarkView)
            marks.push({node: parent, deco: parentView.mark})
        else if (parentView instanceof LineView || parent.nodeName == "DIV" && parent.parentNode == view.contentDOM)
            return {range, text: textNode, marks, line: parent as HTMLElement}
        else if (parent != view.contentDOM)
            marks.push({node: parent, deco: new MarkDecoration({
                inclusive: true,
                attributes: getAttrs(parent),
                tagName: parent.tagName.toLowerCase()
            })})
        else
            return null
    }
}

const enum NextTo { Before = 1, After = 2 }

fun nextToUneditable(node: Node, offset: number) {
    if (node.nodeType != 1) return 0
    return (offset && (node.childNodes[offset - 1] as any).contentEditable == "false" ? NextTo.Before : 0) |
    (offset < node.childNodes.length && (node.childNodes[offset] as any).contentEditable == "false" ? NextTo.After : 0)
}

class DecorationComparator {
    changes: number[] = []
    compareRange(from: number, to: number) { addRange(from, to, this.changes) }
    comparePoint(from: number, to: number) { addRange(from, to, this.changes) }
    boundChange(pos: number) { addRange(pos, pos, this.changes) }
}

fun findChangedDeco(a: readonly DecorationSet[], b: readonly DecorationSet[], diff: ChangeSet) {
    let comp = new DecorationComparator
    RangeSet.compare(a, b, diff, comp)
    return comp.changes
}

fun inUneditable(node: Node | null, inside: HTMLElement): Boolean {
    for (let cur = node; cur && cur != inside; cur = (cur as HTMLElement).assignedSlot || cur.parentNode) {
        if (cur.nodeType == 1 && (cur as HTMLElement).contentEditable == 'false') {
            return true;
        }
    }
    return false;
}

fun touchesComposition(changes: ChangeSet, composition: null | {from: number, to: number}): Boolean {
    let touched = false
    if (composition) changes.iterChangedRanges((from, to) => {
        if (from < composition!.to && to > composition!.from) touched = true
    })
    return touched
}

private data class TextSizeInfo(
    val lineHeight: Double,
    val charWidth: Double,
    val textHeight: Double
)

private data class ViewportRange(
    val from: Int,
    val to: Int
)
