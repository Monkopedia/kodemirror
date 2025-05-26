package com.monkopedia.kodemirror.view

import androidx.compose.foundation.interaction.Interaction
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.util.check
import kotlin.math.max

//import {Text as DocText} from "@codemirror/state"
//import {ContentView, DOMPos, ViewFlag, mergeChildrenInto, noChildren} from "./contentview"
//import {WidgetType, MarkDecoration} from "./decoration"
//import {Rect, flattenRect, textRange, clientRectsFor, clearAttributes} from "./dom"
//import {DocView} from "./docview"
//import browser from "./browser"
//import {EditorView} from "./editorview"

const val MaxJoinLen = 256

class TextView(var text: String) : ContentView() {
    override val children = mutableListOf<ContentView>()
    override val dom: HTMLText? = null

    override var length: Int
        get() {
            return this.text.length
        }
        set(value) {}

//    fun createDOM(textDOM: Node? = null) {
//        this.setDOM(textDOM || document.createTextNode(this.text))
//    }

    override fun sync(view: EditorView, track: Track?) {
//        if (this.dom == null) this.createDOM()
//        if (this.dom!.nodeValue != this.text) {
//            if (track && track.node == this.dom) track.written = true
//            this.dom!.nodeValue = this.text
//        }
    }

//    fun reuseDOM(dom: Node) {
//        if (dom.nodeType == 3) this.createDOM(dom)
//    }

    fun merge(from: Int, to: Int, source: ContentView?): Boolean {
        if (this.flags check ViewFlag.Composition.mask) return false
        if (source != null && source !is TextView) return false
        if (source != null && (this.length - (to - from) + source.length > MaxJoinLen ||
                (source.flags check ViewFlag.Composition.mask))
        ) {
            return false
        }
        this.text = this.text.slice(0 until from) + ((source as TextView?)?.text ?: "") +
            this.text.slice(to until this.text.length)
        this.markDirty()
        return true
    }

    override fun split(from: Int): TextView {
        val result = TextView(this.text.slice(from until this.text.length))
        this.text = this.text.slice(0 until from)
        this.markDirty()
        result.flags = result.flags or (this.flags and ViewFlag.Composition.mask)
        return result
    }

    //    fun localPosFromDOM(node: Modifier.Node, offset: Int): Int
//    {
//        return node == this.dom ? offset : offset ? this.text.length : 0
//    }
//
//    fun domAtPos(pos: Int): DOMPos { return DOMPos (this.dom!, pos) }
//
//    fun domBoundsAround(_from: Int, _to: Int, offset: Int) {
//        return { from: offset, to: offset+this.length, startDOM: this.dom, endDOM: this.dom!.nextSibling }
//    }
//
    override fun coordsAt(pos: Int, side: Int): Rect? {
        return textCoords(this.dom!!, pos, side)
    }
}

class MarkView(
    val mark: MarkDecoration,
    public override val children: MutableList<ContentView> = mutableListOf(),
    public override var length: Int = 0
) : ContentView() {
//    val dom: HTMLElement ? = null

    init {
        for (ch in children) ch.setParent(this)
    }

//    fun setAttrs(dom: HTMLElement) {
//        clearAttributes(dom)
//        if (this.mark.class) dom . className = this.mark.class
//        if(
//            this.
//            mark.
//            attrs
//        ) for (let name in this.mark.attrs) dom.setAttribute(name, this.mark.attrs[name])
//        return dom
//    }

    override fun canReuseDOM(other: ContentView): Boolean {
        return super.canReuseDOM(other) && !((this.flags or other.flags) check ViewFlag.Composition.mask)
    }

//    fun reuseDOM(node: Modifier.Node) {
//        if (node.nodeName == this.mark.tagName.toUpperCase()) {
//            this.setDOM(node)
//            this.flags | = ViewFlag.AttrsDirty | ViewFlag.NodeDirty
//        }
//    }

    override fun sync(view: EditorView, track: Track?) {
//        if (!this.dom) this.setDOM(this.setAttrs(document.createElement(this.mark.tagName)))
//        else if (this.flags & ViewFlag.AttrsDirty) this.setAttrs(this.dom)
        super.sync(view, track)
    }

    override fun merge(
        from: Int,
        to: Int,
        source: ContentView?,
        _hasStart: Boolean,
        openStart: Int,
        openEnd: Int
    ): Boolean {
        if (source != null && (!(source is MarkView && source.mark.eq(this.mark)) ||
                (from != 0 && openStart <= 0) || (to < this.length && openEnd <= 0))
        )
            return false
        mergeChildrenInto(
            this,
            from,
            to,
            source?.children?.toMutableList() ?: mutableListOf(), openStart - 1, openEnd - 1
        )
        this.markDirty()
        return true
    }

    override fun split(from: Int): MarkView {
        val result = mutableListOf<ContentView>()
        var off = 0
        var detachFrom = -1
        for ((i, elt) in this.children.withIndex()) {
            val end = off + elt.length
            if (end > from) result.add(if (off < from) elt.split(from - off) else elt)
            if (detachFrom < 0 && off >= from) detachFrom = i
            off = end
        }
        val length = this.length - from
        this.length = from
        if (detachFrom > -1) {
            while (detachFrom < this.children.size) {
                this.children.removeAt(detachFrom)
            }
            this.markDirty()
        }
        return MarkView(this.mark, result, length)
    }

    override fun domAtPos(pos: Int): DOMPos {
        return inlineDOMAtPos(this, pos)
    }

    override fun coordsAt(pos: Int, side: Int): Rect? {
        return coordsInChildren(this, pos, side)
    }
}

fun textCoords(text: Text, pos: Int, side: Int): Rect? {
    var pos = pos
    val length = text.nodeValue!!.length
    if (pos > length) pos = length
    var from = pos
    var to = pos
    val flatten = 0
    if (pos == 0 && side < 0 || pos == length && side >= 0) {
//        if (!(browser.chrome || browser.gecko)) { // These browsers reliably return valid rectangles for empty ranges
//            if (pos) {
//                from--; flatten = 1
//            } // FIXME this is wrong in RTL text
//            else if (to < length) {
//                to++; flatten = -1
//            }
//        }
    } else {
        if (side < 0) from--; else if (to < length) to++
    }
    val rects = textRange(text, from, to).getClientRects()
    if (rects.isEmpty()) return null
    val rect = rects[if (if (flatten != 0) flatten < 0 else side >= 0) 0 else rects.length - 1]
//    if (browser.safari && !flatten && rect.width == 0) {
//        rect = Array.prototype.find.call(rects, r => r . width) ?: rect
//    }
    return if (flatten != 0) flattenRect(rect!!, flatten < 0) else rect
}

// Also used for collapsed ranges that don't have a placeholder widget!
class WidgetView(var widget: WidgetType, override var length: Int, val side: Int) : ContentView() {
    override val children = mutableListOf<ContentView>()

    //    dom!: HTMLElement ?
    var prevWidget: WidgetType? = null


    override fun split(from: Int): ContentView {
        val result = WidgetView.create(this.widget, this.length - from, this.side)
        this.length -= from
        return result
    }

    override fun sync(view: EditorView, track: Track?) {
//        if (!this.dom || !this.widget.updateDOM(this.dom, view)) {
//            if (this.dom && this.prevWidget) this.prevWidget.destroy(this.dom)
//            this.prevWidget = null
//            this.setDOM(this.widget.toDOM(view))
//            if (!this.widget.editable) this.dom!.contentEditable = "false"
//        }
    }

    fun getSide(): Int {
        return this.side
    }

    override fun merge(
        from: Int,
        to: Int,
        source: ContentView?,
        hasStart: Boolean,
        openStart: Int,
        openEnd: Int
    ): Boolean {
        if (source != null && (!(source is WidgetView) || !this.widget.compare(source.widget) ||
                from > 0 && openStart <= 0 || to < this.length && openEnd <= 0)
        )
            return false
        this.length = from + (source?.length ?: 0) + (this.length - to)
        return true
    }

    override fun become(other: ContentView): Boolean {
        if (other is WidgetView && other.side == this.side &&
            this.widget::class == other.widget::class
        ) {
//            if (!this.widget.compare(other.widget)) this.markDirty(true)
//            if (this.dom && this.prevWidget == null) this.prevWidget = this.widget
//            this.widget = other.widget
//            this.length = other.length
//            return true
        }
        return false
    }

    override fun ignoreMutation(_rec: MutationRecord): Boolean {
        return true
    }

    fun ignoreEvent(event: Interaction): Boolean {
        return this.widget.ignoreEvent(event)
    }

    override val overrideDOMText: Text?
        get() {
            if (this.length == 0) return Text.empty
            var top: ContentView = this
            while (top.parent != null) top = top.parent
            val view = (top as DocView).view
            val text: Text? = view?.state?.doc
            val start = this.posAtStart
            return text?.slice(start, start + this.length) ?: Text.empty
        }

    override fun domAtPos(pos: Int): DOMPos {
        return (this.length ? pos == 0 : this.side > 0)
        ? DOMPos.before(this.dom!)
        : DOMPos.after(this.dom!, pos == this.length)
    }

    fun domBoundsAround(): Rect? {
        return null
    }

    override fun coordsAt(pos: Int, side: Int): Rect? {
        this.widget.coordsAt(this.dom!!, pos, side)?.let { return it }
        val rects = this.dom!!.getClientRects()
        var rect: Rect? = null
        if (rects.isEmpty()) return null
        val fromBack = if (this.side != 0) this.side < 0 else pos > 0
        for (i in if (fromBack) rects.indices.reversed() else rects.indices) {
            rect = rects[i]
            if (rect.top!! < rect.bottom!!) break
        }
        return flattenRect(rect, !fromBack)
    }

    override val isEditable: Boolean
        get() {
            return false
        }

    override val isWidget: Boolean
        get() {
            return true
        }

    override val isHidden: Boolean
        get() {
            return this.widget.isHidden
        }

    override fun destroy() {
        super.destroy()
        if (this.dom != null) this.widget.destroy(this.dom)
    }

    companion object {
        fun create(widget: WidgetType, length: Int, side: Int): WidgetView {
            return WidgetView(widget, length, side)
        }
    }
}

// These are drawn around uneditable widgets to avoid a number of
// browser bugs that show up when the cursor is directly next to
// uneditable inline content.
class WidgetBufferView(val side: Int) : ContentView() {
    override val children = mutableListOf<ContentView>()
    override val dom: HTMLElement? = null


    override val length: Int
        get() {
            return 0
        }

    fun merge(): Boolean {
        return false
    }

    override fun become(other: ContentView): Boolean {
        return other is WidgetBufferView && other.side == this.side
    }

    override fun split(at: Int): ContentView {
        return WidgetBufferView(this.side)
    }

    override fun sync(view: EditorView, track: Track?) {
        if (this.dom == null) {
//            val dom = document . createElement ("img")
//            dom.className = "cm-widgetBuffer"
//            dom.setAttribute("aria-hidden", "true")
//            this.setDOM(dom)
        }
    }

    override fun getSide() = this.side

    override fun domAtPos(pos: Int): DOMPos {
        return if (this.side > 0) DOMPos.before(this.dom!!) else DOMPos.after(this.dom!!)
    }

    fun localPosFromDOM(): Int {
        return 0
    }

    fun domBoundsAround(): Rect? {
        return null
    }

    override fun coordsAt(_pos: Int, _side: Int): Rect? {
        return this.dom!!.getBoundingClientRect()
    }

    override val overrideDOMText: Text
        get() {
            return Text.empty
        }

    override val isHidden: Boolean
        get() {
            return true
        }
}


fun inlineDOMAtPos(parent: ContentView, pos: Int): DOMPos {
    val dom = parent.dom!!
    val children = parent.children
    var off = 0
    var lastI: Int = 0
    for (i in children.indices) {
        lastI = i
        val child = children[i]
        val end = off + child.length
        if (end == off && child.getSide() <= 0) continue
        if (pos in (off + 1)..<end && child.dom!!.parentNode == dom) return child.domAtPos(pos - off)
        if (pos <= off) break
        off = end
    }
    for (j in lastI downTo 1) {
        val prev = children[j - 1]
        if (prev.dom!!.parentNode == dom) return prev.domAtPos(prev.length)
    }
    for (j in lastI until children.size) {
        val next = children[j]
        if (next.dom!!.parentNode == dom) return next.domAtPos(0)
    }
    return DOMPos(dom, 0)
}

// Assumes `view`, if a mark view, has precisely 1 child.
fun joinInlineInto(parent: ContentView, view: ContentView, open: Int) {
    val children = parent.children
    if (open > 0 && view is MarkView && children.isNotEmpty() &&
        (children.last() as? MarkView)?.mark?.eq(view.mark) == true
    ) {
        joinInlineInto(children.last(), view.children[0], open - 1)
    } else {
        children.add(view)
        view.setParent(parent)
    }
    parent.length += view.length
}

fun coordsInChildren(view: ContentView, pos: Int, side: Int): Rect? {
    var before: ContentView? = null
    var beforePos = -1
    var after: ContentView? = null
    var afterPos = -1
    fun scan(view: ContentView, pos: Int) {
        var off = 0
        for (i in view.children.indices) {
            if (off > pos) break
            val child = view.children[i]
            val end = off + child.length
            if (end >= pos) {
                if (child.children.isNotEmpty()) {
                    scan(child, pos - off)
                } else if ((after == null || after!!.isHidden && side > 0) &&
                    (end > pos || off == end && child.getSide() > 0)
                ) {
                    after = child
                    afterPos = pos - off
                } else if (off < pos || (off == end && child.getSide() < 0) && !child.isHidden) {
                    before = child
                    beforePos = pos - off
                }
            }
            off = end
        }
    }
    scan(view, pos)
    val target = (if (side < 0) before else after) ?: before ?: after
    if (target != null) {
        return target.coordsAt(max(0, if (target == before) beforePos else afterPos), side)
    }
    return fallbackRect(view)
}

fun fallbackRect(view: ContentView): Rect? {
    val last = view . dom !!. lastChild
        ?: return (view.dom as HTMLElement).getBoundingClientRect()
    val rects = clientRectsFor (last)
    return rects.lastOrNull()
}
