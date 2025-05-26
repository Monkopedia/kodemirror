package com.monkopedia.kodemirror.view

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.util.check

//import {Text} from "@codemirror/state"
//import {Rect, maxOffset, domIndex} from "./dom"
//import {EditorView} from "./editorview"

// Track mutated / outdated status of a view node's DOM
enum class ViewFlag(val mask: Int) {
    // At least one child is dirty
    ChildDirty(1),

    // The node itself isn't in sync with its child list
    NodeDirty(2),

    // The node's DOM attributes might have changed
    AttrsDirty(4),

    // Mask for all of the dirty flags
    Dirty(7),

    // Set temporarily during a doc view update on the nodes around the
    // composition
    Composition(8),
}

data class DOMPos(
    val node: Node, val offset: Int, val precise: Boolean = true
) {

    companion object {
        fun before(dom: Node, precise: Boolean = true): DOMPos {
            return DOMPos(dom, domIndex(dom), precise)
        }

        fun after(dom: Node, precise: Boolean = true): DOMPos {
            return DOMPos(dom, domIndex(dom) + 1, precise)
        }
    }
}

val noChildren = emptyList<ContentView>()

abstract class ContentView {
    var parent: ContentView? = null
    var dom: HTMLElement? = null
    var flags: Int = ViewFlag.NodeDirty.mask
    abstract var length: Int
    abstract val children: MutableList<ContentView>
    open var breakAfter: Int = 0

    open val overrideDOMText: Text?
        get() {
            return null
        }

    val posAtStart: Int
        get() {
            return this.parent?.posBefore(this) ?: 0
        }

    val posAtEnd: Int
        get() {
            return this.posAtStart + this.length
        }

    fun posBefore(view: ContentView): Int {
        var pos = this.posAtStart
        for (child in this.children) {
            if (child == view) return pos
            pos += child.length + child.breakAfter
        }
        throw IllegalArgumentException("Invalid child in posBefore")
    }

    fun posAfter(view: ContentView): Int {
        return this.posBefore(view) + view.length
    }

    // Will return a rectangle directly before (when side < 0), after
    // (side > 0) or directly on (when the browser supports it) the
    // given position.
    abstract fun coordsAt(_pos: Int, _side: Int): Rect?

    data class Track(
        val node: Node,
        var written: Boolean
    )

    open fun sync(view: EditorView, track: Track? = null) {
//        if (this.flags check ViewFlag.NodeDirty.mask) {
//            val parent = this.dom as Node
//            var prev: Node? = null
//            var next: Node?
//            for (child in this.children) {
//                if (child.flags check ViewFlag.Dirty.mask) {
//                    next = prev?.nextSibling ?: parent.firstChild
//                    if (child.dom != null && next != null) {
//                        val contentView = ContentView.get(next)
//                        if (!contentView || !contentView.parent && contentView.canReuseDOM(child)) {
//                            child.reuseDOM(next)
//                        }
//                    }
//                    child.sync(view, track)
//                    child.flags & = ~ViewFlag.Dirty
//                }
//                next = prev?.nextSibling ?: parent.firstChild
//                if (track != null && !track.written && track.node == parent && next != child.dom) {
//                    track.written = true
//                }
//                if (child.dom!!.parentNode == parent) {
//                    while (next && next != child.dom) next = rm(next)
//                } else {
//                    parent.insertBefore(child.dom!, next)
//                }
//                prev = child.dom!
//            }
//            next = prev ? prev.nextSibling : parent.firstChild
//            if (next && track && track.node == parent) track.written = true
//            while (next) next = rm(next)
//        } else if (this.flags & ViewFlag.ChildDirty) {
//            for (let child of this.children) if (child.flags & ViewFlag.Dirty) {
//            child.sync(view, track)
//            child.flags & = ~ViewFlag.Dirty
//        }
//        }
    }

    fun reuseDOM(_dom: Node) {}

    abstract fun domAtPos(pos: Int): DOMPos

    fun localPosFromDOM(node: Node, offset: Int): Int {
//        let after : Node | null
//        if (node == this.dom) {
//            after = this.dom.childNodes[offset]
//        } else {
//            let bias = maxOffset (node) == 0 ? 0 : offset == 0 ?-1 : 1
//            for (;;) {
//                let parent = node . parentNode !
//                if (parent == this.dom) break
//                if (bias == 0 && parent.firstChild != parent.lastChild) {
//                    if (node == parent.firstChild) bias = -1
//                    else bias = 1
//                }
//                node = parent
//            }
//            if (bias < 0) after = node
//            else after = node.nextSibling
//        }
//        if (after == this.dom !. firstChild) return 0
//        while (after && !ContentView.get(after)) after = after.nextSibling
//        if (!after) return this.length
//
//        for (let i = 0, pos = 0;; i++) {
//        let child = this.children[i]
//        if (child.dom == after) return pos
//        pos += child.length + child.breakAfter
//    }
    }

    data class DOMContext(
        val startDOM: Node?,
        val endDOM: Node?,
        val from: Int,
        val to: Int
    )

    fun domBoundsAround(from: Int, to: Int, offset: Int = 0): DOMContext? {
//        let fromI = - 1, fromStart = -1, toI = -1, toEnd = -1
//        for (let i = 0, pos = offset, prevEnd = offset; i < this.children.length; i++) {
//        let child = this.children[i], end = pos+child.length
//        if (pos < from && end > to) return child.domBoundsAround(from, to, pos)
//        if (end >= from && fromI == -1) {
//            fromI = i
//            fromStart = pos
//        }
//        if (pos > to && child.dom !. parentNode == this.dom) {
//        toI = i
//        toEnd = prevEnd
//        break
//    }
//        prevEnd = end
//        pos = end + child.breakAfter
//    }
//
//        return {
//            from: fromStart, to: toEnd < 0 ? offset+this.length : toEnd,
//            startDOM: (fromI ? this.children[fromI-1].dom!.nextSibling : null) || this.dom!.firstChild,
//            endDOM: toI < this.children.length && toI >= 0 ? this.children[toI].dom : null
//        }
    }

    fun markDirty(andParent: Boolean = false) {
        this.flags = this.flags or ViewFlag.NodeDirty.mask
        this.markParentsDirty(andParent)
    }

    fun markParentsDirty(startChild: Boolean) {
        var parent = this.parent
        var childList = startChild
        while (parent != null) {
            if (childList) parent.flags = parent.flags or ViewFlag.NodeDirty.mask
            if (parent.flags check ViewFlag.ChildDirty.mask) return
            parent.flags = parent.flags or ViewFlag.ChildDirty.mask
            childList = false
            parent = parent.parent
        }
    }

    fun setParent(parent: ContentView) {
        if (this.parent != parent) {
            this.parent = parent
            if (this.flags check ViewFlag.Dirty.mask) this.markParentsDirty(true)
        }
    }

    fun setDOM(dom: Node) {
        if (this.dom == dom) return
        // TODO: Lookup
//        if (this.dom) (this.dom as any).cmView = null
        this.dom = dom
//        ;(dom as any).cmView = this
    }

    val rootView: ContentView
        get() {
            var v: ContentView = this
            while (true) {
                val parent = v.parent
                if (parent == null) return v
                v = parent
            }
        }

    fun replaceChildren(from: Int, to: Int, children: List<ContentView> = noChildren) {
        this.markDirty()
        for (i in from until to) {
            val child = this.children[from]
            if (child.parent == this && children.indexOf(child) < 0) child.destroy()
            this.children.remove(child)
        }
        this.children.addAll(children)
        children.forEach { it.setParent(this) }
//        if (children.size < 250) {
//            this.children
//            this.children.splice(from, to - from, ... children
//        }
//        else this.children =
//            ([] as ContentView[]).concat(this.children.slice(0, from), children, this.children.slice(to))
//        for (let i = 0; i < children.length; i++) children[i].setParent(this)
    }

    open fun ignoreMutation(_rec: MutationRecord): Boolean {
        return false
    }

    open fun ignoreEvent(_event: KeyEvent): Boolean {
        return false
    }

    fun childCursor(pos: Int = this.length): ChildCursor {
        return ChildCursor(this.children, pos, this.children.size)
    }

    fun childPos(pos: Int, bias: Int = 1): ChildCursor {
        return this.childCursor().findPos(pos, bias)
    }

    override fun toString(): String {
        val name = this::class.simpleName.toString().replace("View", "")
        return name + (
            when {
                this.children.isNotEmpty() -> "(" + this.children.joinToString() + ")"
                this.length != 0 -> "[${this.length}]"
//                "[" + (if (name == "Text") (this as Any).text else this.length) + "]"
                else -> ""
            }) +
            (if (this.breakAfter != 0) "#" else "")
    }

    open val isEditable: Boolean
        get() {
            return true
        }

    open val isWidget: Boolean
    get() { return false }

    open val isHidden: Boolean
        get() {
            return false
        }

    open fun merge(
        from: Int,
        to: Int,
        source: ContentView?,
        hasStart: Boolean,
        openStart: Int,
        openEnd: Int
    ): Boolean {
        return false
    }

    open fun become(other: ContentView): Boolean {
        return false
    }

    open fun canReuseDOM(other: ContentView): Boolean {
        return this::class == other::class &&
            !((this.flags or other.flags) check ViewFlag.Composition.mask)
    }

    abstract fun split(at: Int): ContentView

    // When this is a zero-length view with a side, this should return a
    // number <= 0 to indicate it is before its position, or a
    // number > 0 when after its position.
    open fun getSide(): Int {
        return 0
    }

    open fun destroy() {
        for (child in this.children) {
            if (child.parent == this) child.destroy()
        }
        this.parent = null
    }

    companion object {
        fun get(node: Node): ContentView? {
//            return (node as any).cmView
        }
    }
}

// Remove a DOM node and return its next sibling.
fun rm(dom: Node): Node? {
//    let next = dom . nextSibling
//        dom.parentNode!.removeChild(dom)
//    return next
}

data class ChildCursor(
    val children: List<ContentView>,
    var pos: Int,
    var i: Int,
    var off: Int = 0
) {

    fun findPos(pos: Int, bias: Int = 1): ChildCursor = apply {
        while (true) {
            if (pos > this.pos || pos == this.pos &&
                (bias > 0 || this.i == 0 || this.children[this.i - 1].breakAfter != 0)
            ) {
                this.off = pos - this.pos
                return this
            }
            val next = this.children[--this.i]
            this.pos -= next.length + next.breakAfter
        }
    }
}

fun replaceRange(
    parent: ContentView,
    fromI: Int,
    fromOff: Int,
    toI: Int,
    toOff: Int,
    insert: MutableList<ContentView>,
    breakAtStart: Int,
    openStart: Int,
    openEnd: Int
) {
    val children = parent.children
    val before = children.getOrNull(fromI)
    val last = insert.lastOrNull()
    var openStart = openStart
    var openEnd = openEnd
    var toI = toI
    var fromI = fromI
    var breakAtStart = breakAtStart
    val breakAtEnd = last?.breakAfter ?: breakAtStart
    var toOff = toOff
    // Change within a single child
    if (fromI == toI &&
        before != null &&
        breakAtStart == 0 &&
        breakAtEnd == 0 &&
        insert.size < 2 &&
        before.merge(fromOff, toOff, last, fromOff == 0, openStart, openEnd)
    ) {
        return
    }

    if (toI < children.size) {
        var after = children[toI]
        // Make sure the end of the child after the update is preserved in `after`
        if ((toOff < after.length || after.breakAfter != 0 && (last?.breakAfter ?: 0) != 0)) {
            // If we're splitting a child, separate part of it to avoid that
            // being mangled when updating the child before the update.
            if (fromI == toI) {
                after = after.split(toOff)
                toOff = 0
            }
            // If the element after the replacement should be merged with
            // the last replacing element, update `content`
            if (breakAtEnd == 0 && last != null && after.merge(0, toOff, last, true, 0, openEnd)) {
                insert[insert.size - 1] = after
            } else {
                // Remove the start of the after element, if necessary, and
                // add it to `content`.
                if (toOff != 0 || after.children.isNotEmpty() && after.children[0].length != 0) {
                    after.merge(0, toOff, null, false, 0, openEnd)
                }
                insert.add(after)
            }
        } else if (after.breakAfter != 0) {
            // The element at `toI` is entirely covered by this range.
            // Preserve its line break, if any.
            if (last != null) last.breakAfter = 1
            else breakAtStart = 1
        }
        // Since we've handled the next element from the current elements
        // now, make sure `toI` points after that.
        toI++
    }

    if (before != null) {
        before.breakAfter = breakAtStart
        if (fromOff > 0) {
            if (breakAtStart == 0 &&
                insert.isNotEmpty() &&
                before.merge(fromOff, before.length, insert[0], false, openStart, 0)
            ) {
                before.breakAfter = insert.removeFirst().breakAfter
            } else if (fromOff < before.length || before.children.isNotEmpty() && before.children.last().length == 0) {
                before.merge(fromOff, before.length, null, false, openStart, 0)
            }
            fromI++
        }
    }

    // Try to merge widgets on the boundaries of the replacement
    while (fromI < toI && insert.isNotEmpty()) {
        if (children[toI - 1].become(insert.last())) {
            toI--
            insert.removeLast()
            openEnd = if (insert.isNotEmpty()) 0 else openStart
        } else if (children[fromI].become(insert[0])) {
            fromI++
            insert.removeFirst()
            openStart = if (insert.isNotEmpty()) 0 else openEnd
        } else {
            break
        }
    }
    if (insert.isEmpty() &&
        fromI != 0 &&
        toI < children.size &&
        children[fromI - 1].breakAfter == 0 &&
        children[toI].merge(0, 0, children[fromI - 1], false, openStart, openEnd) ) {
        fromI--
    }

    if (fromI < toI || insert.isNotEmpty()) {
        parent.replaceChildren(fromI, toI, insert)
    }
}

fun mergeChildrenInto(parent: ContentView, from: Int, to: Int, insert: MutableList<ContentView>, openStart: Int, openEnd: Int) {
    val cur = parent . childCursor ()
    val (_, _, toI, toOff ) = cur.findPos(to, 1)
    val (_, _,  fromI,  fromOff ) = cur.findPos(from, -1)
    var dLen = from -to
    for (view in insert) dLen += view.length
    parent.length += dLen

    replaceRange(parent, fromI, fromOff, toI, toOff, insert, 0, openStart, openEnd)
}
