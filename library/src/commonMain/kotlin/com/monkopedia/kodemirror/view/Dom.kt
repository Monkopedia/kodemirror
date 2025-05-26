package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.dom.*
import org.w3c.dom.*
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * Get the selection from a document or shadow root.
 */
fun getSelection(root: Node): Selection? {
    val target = when {
        root.nodeType == 11 -> { // Shadow root
            if ((root as? ShadowRoot)?.getSelection != null) root else root.ownerDocument
        }
        else -> root as? Document
    }
    return target?.getSelection()
}

/**
 * Check if a DOM node contains another node.
 */
fun contains(dom: Node, node: Node?): Boolean {
    return node?.let { n ->
        dom == n || dom.contains(if (n.nodeType != 1) n.parentNode else n)
    } ?: false
}

/**
 * Check if a DOM element has a selection.
 */
fun hasSelection(dom: HTMLElement, selection: Selection): Boolean {
    if (selection.anchorNode == null) return false
    return try {
        contains(dom, selection.anchorNode)
    } catch (_: Throwable) {
        false
    }
}

/**
 * Get client rects for a node.
 */
fun clientRectsFor(dom: Node): DOMRectList {
    return when (dom.nodeType) {
        3 -> textRange(dom as Text, 0, dom.nodeValue?.length ?: 0).getClientRects()
        1 -> (dom as HTMLElement).getClientRects()
        else -> emptyList<DOMRect>() as DOMRectList
    }
}

/**
 * Check if two DOM positions are equivalent.
 */
fun isEquivalentPosition(node: Node, off: Int, targetNode: Node?, targetOff: Int): Boolean {
    return targetNode?.let { target ->
        scanFor(node, off, target, targetOff, -1) || scanFor(node, off, target, targetOff, 1)
    } ?: false
}

/**
 * Get the index of a node among its siblings.
 */
fun domIndex(node: Node): Int {
    var index = 0
    var current = node.previousSibling
    while (current != null) {
        index++
        current = current.previousSibling
    }
    return index
}

/**
 * Check if a node is a block element.
 */
fun isBlockElement(node: Node): Boolean {
    return node.nodeType == 1 && Regex("^(DIV|P|LI|UL|OL|BLOCKQUOTE|DD|DT|H\\d|SECTION|PRE)$").matches(node.nodeName)
}

/**
 * Scan for equivalent positions.
 */
private fun scanFor(node: Node, off: Int, targetNode: Node, targetOff: Int, dir: Int): Boolean {
    var currentNode = node
    var currentOff = off
    
    while (true) {
        if (currentNode == targetNode && currentOff == targetOff) return true
        
        if (currentOff == (if (dir < 0) 0 else maxOffset(currentNode))) {
            if (currentNode.nodeName == "DIV") return false
            val parent = currentNode.parentNode ?: return false
            if (parent.nodeType != 1) return false
            currentOff = domIndex(currentNode) + (if (dir < 0) 0 else 1)
            currentNode = parent
        } else if (currentNode.nodeType == 1) {
            currentNode = currentNode.childNodes[currentOff + (if (dir < 0) -1 else 0)] ?: return false
            if (currentNode.nodeType == 1 && (currentNode as HTMLElement).contentEditable == "false") return false
            currentOff = if (dir < 0) maxOffset(currentNode) else 0
        } else {
            return false
        }
    }
}

/**
 * Get the maximum offset for a node.
 */
fun maxOffset(node: Node): Int {
    return when (node.nodeType) {
        3 -> node.nodeValue?.length ?: 0
        else -> node.childNodes.length
    }
}

/**
 * Basic rectangle type.
 */
data class Rect(
    val left: Double = 0.0,
    val right: Double = 0.0,
    val top: Double = 0.0,
    val bottom: Double = 0.0
)

/**
 * Flatten a rectangle to a vertical line on one of its sides.
 */
fun flattenRect(rect: Rect, left: Boolean): Rect {
    val x = if (left) rect.left else rect.right
    return rect.copy(left = x, right = x)
}

/**
 * Get the window's viewport rectangle.
 */
fun windowRect(win: Window): Rect {
    val vp = win.visualViewport
    return if (vp != null) {
        Rect(left = 0.0, right = vp.width, top = 0.0, bottom = vp.height)
    } else {
        Rect(left = 0.0, right = win.innerWidth.toDouble(), top = 0.0, bottom = win.innerHeight.toDouble())
    }
}

export type ScrollStrategy = "nearest" | "start" | "end" | "center"

export function getScale(elt: HTMLElement, rect: DOMRect) {
    let scaleX = rect . width / elt . offsetWidth
        let scaleY = rect . height / elt . offsetHeight
        if (scaleX > 0.995 && scaleX < 1.005 || !isFinite(scaleX) || Math.abs(rect.width - elt.offsetWidth) < 1) scaleX =
            1
    if (scaleY > 0.995 && scaleY < 1.005 || !isFinite(scaleY) || Math.abs(rect.height - elt.offsetHeight) < 1) scaleY =
        1
    return { scaleX, scaleY }
}

export function scrollRectIntoView(dom: HTMLElement, rect: Rect, side: -1 | 1,
x: ScrollStrategy, y: ScrollStrategy,
xMargin: number, yMargin: number, ltr: boolean) {
    let doc = dom . ownerDocument !, win = doc.defaultView || window

    for (let cur: any = dom, stop = false; cur && !stop;) {
        if (cur.nodeType == 1) { // Element
            let bounding : Rect, top = cur == doc.body
            let scaleX = 1, scaleY = 1
            if (top) {
                bounding = windowRect(win)
            } else {
                if ( / ^(fixed| sticky)$/.test(getComputedStyle(cur).position)) stop = true
                if (cur.scrollHeight <= cur.clientHeight && cur.scrollWidth <= cur.clientWidth) {
                    cur = cur.assignedSlot || cur.parentNode
                    continue
                }
                let rect = cur . getBoundingClientRect ()
                ;({ scaleX, scaleY } = getScale(cur, rect))
                // Make sure scrollbar width isn't included in the rectangle
                bounding = {
                    left: rect.left, right: rect.left+cur.clientWidth * scaleX,
                    top: rect.top, bottom: rect.top+cur.clientHeight * scaleY
                }
            }

            let moveX = 0, moveY = 0
            if (y == "nearest") {
                if (rect.top < bounding.top) {
                    moveY = -(bounding.top - rect.top + yMargin)
                    if (side > 0 && rect.bottom > bounding.bottom + moveY)
                        moveY = rect.bottom - bounding.bottom + moveY + yMargin
                } else if (rect.bottom > bounding.bottom) {
                    moveY = rect.bottom - bounding.bottom + yMargin
                    if (side < 0 && (rect.top - moveY) < bounding.top)
                        moveY = -(bounding.top + moveY - rect.top + yMargin)
                }
            } else {
                let rectHeight = rect . bottom -rect.top, boundingHeight = bounding.bottom-bounding.top
                let targetTop =
                y == "center" && rectHeight <= boundingHeight ? rect.top+rectHeight / 2-boundingHeight / 2 :
                y == "start" || y == "center" && side < 0 ? rect.top-yMargin :
                rect.bottom - boundingHeight + yMargin
                moveY = targetTop - bounding.top
            }
            if (x == "nearest") {
                if (rect.left < bounding.left) {
                    moveX = -(bounding.left - rect.left + xMargin)
                    if (side > 0 && rect.right > bounding.right + moveX)
                        moveX = rect.right - bounding.right + moveX + xMargin
                } else if (rect.right > bounding.right) {
                    moveX = rect.right - bounding.right + xMargin
                    if (side < 0 && rect.left < bounding.left + moveX)
                        moveX = -(bounding.left + moveX - rect.left + xMargin)
                }
            } else {
                let targetLeft =
                x == "center" ? rect.left+(rect.right-rect.left) / 2-(bounding.right-bounding.left) / 2 :
                (x == "start") == ltr ? rect.left-xMargin :
                rect.right - (bounding.right - bounding.left) + xMargin
                moveX = targetLeft - bounding.left
            }
            if (moveX || moveY) {
                if (top) {
                    win.scrollBy(moveX, moveY)
                } else {
                    let movedX = 0, movedY = 0
                    if (moveY) {
                        let start = cur . scrollTop
                            cur.scrollTop += moveY / scaleY
                        movedY = (cur.scrollTop - start) * scaleY
                    }
                    if (moveX) {
                        let start = cur . scrollLeft
                            cur.scrollLeft += moveX / scaleX
                        movedX = (cur.scrollLeft - start) * scaleX
                    }
                    rect = {
                        left: rect.left-movedX, top: rect.top-movedY,
                        right: rect.right-movedX, bottom: rect.bottom-movedY
                    } as ClientRect
                    if (movedX && Math.abs(movedX - moveX) < 1) x = "nearest"
                    if (movedY && Math.abs(movedY - moveY) < 1) y = "nearest"
                }
            }
            if (top) break
            cur = cur.assignedSlot || cur.parentNode
        } else if (cur.nodeType == 11) { // A shadow root
            cur = cur.host
        } else {
            break
        }
    }
}

/**
 * Find scrollable parent elements.
 */
fun scrollableParents(dom: HTMLElement): ScrollableParents {
    val doc = dom.ownerDocument
    var x: HTMLElement? = null
    var y: HTMLElement? = null
    
    var cur: Node? = dom.parentNode
    while (cur != null) {
        if (cur == doc.body || (x != null && y != null)) {
            break
        } else if (cur.nodeType == 1) {
            val element = cur as HTMLElement
            if (y == null && element.scrollHeight > element.clientHeight) y = element
            if (x == null && element.scrollWidth > element.clientWidth) x = element
            cur = element.assignedSlot ?: element.parentNode
        } else if (cur.nodeType == 11) {
            cur = (cur as ShadowRoot).host
        } else {
            break
        }
    }
    return ScrollableParents(x, y)
}

/**
 * Represents scrollable parent elements.
 */
data class ScrollableParents(
    val x: HTMLElement?,
    val y: HTMLElement?
)

/**
 * Represents a selection range.
 */
interface SelectionRange {
    val focusNode: Node?
    val focusOffset: Int
    val anchorNode: Node?
    val anchorOffset: Int
}

/**
 * Maintains selection state.
 */
class DOMSelectionState : SelectionRange {
    override var anchorNode: Node? = null
    override var anchorOffset: Int = 0
    override var focusNode: Node? = null
    override var focusOffset: Int = 0

    /**
     * Check if this selection state equals another.
     */
    fun eq(domSel: SelectionRange): Boolean {
        return anchorNode == domSel.anchorNode && 
               anchorOffset == domSel.anchorOffset &&
               focusNode == domSel.focusNode && 
               focusOffset == domSel.focusOffset
    }

    /**
     * Set the selection range.
     */
    fun setRange(range: SelectionRange) {
        val anchorNode = range.anchorNode
        val focusNode = range.focusNode
        // Clip offsets to node size to avoid crashes when Safari reports bogus offsets
        set(
            anchorNode = anchorNode,
            anchorOffset = minOf(range.anchorOffset, if (anchorNode != null) maxOffset(anchorNode) else 0),
            focusNode = focusNode,
            focusOffset = minOf(range.focusOffset, if (focusNode != null) maxOffset(focusNode) else 0)
        )
    }

    /**
     * Set the selection state.
     */
    fun set(anchorNode: Node?, anchorOffset: Int, focusNode: Node?, focusOffset: Int) {
        this.anchorNode = anchorNode
        this.anchorOffset = anchorOffset
        this.focusNode = focusNode
        this.focusOffset = focusOffset
    }
}

private var preventScrollSupported: Boolean? = null

/**
 * Get the root node (document or shadow root).
 */
fun getRoot(node: Node?): Node? {
    var current = node
    while (current != null) {
        if (current.nodeType == 9 || (current.nodeType == 11 && (current as ShadowRoot).host != null)) {
            return current
        }
        current = (current as? Element)?.assignedSlot ?: current.parentNode
    }
    return null
}

/**
 * Clear all attributes from an element.
 */
fun clearAttributes(node: HTMLElement) {
    while (node.attributes.length > 0) {
        node.removeAttributeNode(node.attributes[0])
    }
}

/**
 * Check if selection is at the start of an element.
 */
fun atElementStart(doc: HTMLElement, selection: SelectionRange): Boolean {
    var node = selection.focusNode
    var offset = selection.focusOffset
    
    if (node == null || selection.anchorNode != node || selection.anchorOffset != offset) return false
    
    // Safari can report bogus offsets
    offset = minOf(offset, maxOffset(node))
    
    while (true) {
        if (offset > 0) {
            if (node.nodeType != 1) return false
            val prev = node.childNodes[offset - 1]
            if ((prev as? HTMLElement)?.contentEditable == "false") {
                offset--
            } else {
                node = prev
                offset = maxOffset(node)
            }
        } else if (node == doc) {
            return true
        } else {
            offset = domIndex(node)
            node = node.parentNode ?: return false
        }
    }
}

/**
 * Check if an element is scrolled to the bottom.
 */
fun isScrolledToBottom(elt: HTMLElement): Boolean {
    return elt.scrollTop > maxOf(1, elt.scrollHeight - elt.clientHeight - 4)
}

export function textNodeBefore(startNode: Node, startOffset: number): { node: Text, offset: number } | null {
    for (let node = startNode, offset = startOffset;;) {
        if (node.nodeType == 3 && offset > 0) {
            return { node: node as Text, offset: offset }
        } else if (node.nodeType == 1 && offset > 0) {
            if ((node as HTMLElement).contentEditable == "false") return null
            node = node.childNodes[offset - 1]
            offset = maxOffset(node)
        } else if (node.parentNode && !isBlockElement(node)) {
            offset = domIndex(node)
            node = node.parentNode
        } else {
            return null
        }
    }
}

export function textNodeAfter(startNode: Node, startOffset: number): { node: Text, offset: number } | null {
    for (let node = startNode, offset = startOffset;;) {
        if (node.nodeType == 3 && offset < node.nodeValue !. length) {
        return { node: node as Text, offset: offset }
    } else if (node.nodeType == 1 && offset < node.childNodes.length) {
        if ((node as HTMLElement).contentEditable == "false") return null
        node = node.childNodes[offset]
        offset = 0
    } else if (node.parentNode && !isBlockElement(node)) {
        offset = domIndex(node) + 1
        node = node.parentNode
    } else {
        return null
    }
    }
}

private var scratchRange: Range? = null

/**
 * Create a text range.
 */
fun textRange(node: Text, from: Int, to: Int = from): Range {
    val range = scratchRange ?: document.createRange().also { scratchRange = it }
    range.setEnd(node, to)
    range.setStart(node, from)
    return range
}

/**
 * Dispatch keyboard events.
 */
fun dispatchKey(elt: HTMLElement, name: String, code: Int, mods: KeyboardEvent? = null): Boolean {
    val options = KeyboardEventInit().apply {
        key = name
        this.code = name
        keyCode = code
        which = code
        cancelable = true
        
        mods?.let {
            altKey = it.altKey
            ctrlKey = it.ctrlKey
            shiftKey = it.shiftKey
            metaKey = it.metaKey
        }
    }
    
    val down = KeyboardEvent("keydown", options).apply {
        asDynamic().synthetic = true
    }
    elt.dispatchEvent(down)
    
    val up = KeyboardEvent("keyup", options).apply {
        asDynamic().synthetic = true
    }
    elt.dispatchEvent(up)
    
    return down.defaultPrevented || up.defaultPrevented
}

/**
 * Focus an element while preventing scroll.
 */
fun focusPreventScroll(dom: HTMLElement) {
    // Try using setActive for IE
    (dom.asDynamic().setActive as? Function0<Unit>)?.let { 
        it()
        return
    }
    
    if (preventScrollSupported != null) {
        dom.focus(object : FocusOptions {
            override var preventScroll: Boolean = preventScrollSupported == true
        })
        return
    }
    
    // Store scroll positions
    val stack = mutableListOf<Any>()
    var cur: Node? = dom
    while (cur != null) {
        stack.add(cur)
        stack.add((cur as? HTMLElement)?.scrollTop ?: 0)
        stack.add((cur as? HTMLElement)?.scrollLeft ?: 0)
        if (cur == cur.ownerDocument) break
        cur = cur.parentNode
    }
    
    // Try to focus with preventScroll
    dom.focus(object : FocusOptions {
        override var preventScroll: Boolean
            get() {
                preventScrollSupported = true
                return true
            }
    })
    
    // If preventScroll is not supported, restore scroll positions
    if (preventScrollSupported != true) {
        preventScrollSupported = false
        var i = 0
        while (i < stack.size) {
            val elt = stack[i++] as? HTMLElement ?: continue
            val top = stack[i++] as Int
            val left = stack[i++] as Int
            if (elt.scrollTop != top) elt.scrollTop = top
            if (elt.scrollLeft != left) elt.scrollLeft = left
        }
    }
}
