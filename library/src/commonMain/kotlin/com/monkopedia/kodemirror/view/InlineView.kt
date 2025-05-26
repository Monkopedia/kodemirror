package com.monkopedia.kodemirror.view

import androidx.compose.foundation.interaction.Interaction
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.util.check
import kotlin.math.max
import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.dom.*
import kotlinx.browser.document
import kotlinx.browser.window

//import {Text as DocText} from "@codemirror/state"
//import {ContentView, DOMPos, ViewFlag, mergeChildrenInto, noChildren} from "./contentview"
//import {WidgetType, MarkDecoration} from "./decoration"
//import {Rect, flattenRect, textRange, clientRectsFor, clearAttributes} from "./dom"
//import {DocView} from "./docview"
//import browser from "./browser"
//import {EditorView} from "./editorview"

const val MaxJoinLen = 256

/**
 * Text view that displays a piece of the document content.
 */
class TextView(var text: String) : ContentView() {
    override val children = mutableListOf<ContentView>()
    override val dom: HTMLText? = null

    override var length: Int
        get() {
            return this.text.length
        }
        set(value) {}

    override fun sync(view: EditorView, track: Track?) {
        if (this.dom == null) {
            setDOM(document.createTextNode(this.text))
        }
        if ((this.dom as? Text)?.nodeValue != this.text) {
            if (track?.node == this.dom) track.written = true
            (this.dom as? Text)?.nodeValue = this.text
        }
    }

    fun merge(from: Int, to: Int, source: ContentView?): Boolean {
        if (this.flags check ViewFlag.Composition.mask) return false
        if (source != null && source !is TextView) return false
        if (source != null && (this.length - (to - from) + source.length > MaxJoinLen ||
                (source.flags check ViewFlag.Composition.mask))) {
            return false
        }
        this.text = this.text.substring(0, from) + ((source as? TextView)?.text ?: "") +
            this.text.substring(to)
        this.markDirty()
        return true
    }

    override fun split(from: Int): TextView {
        val result = TextView(this.text.substring(from))
        this.text = this.text.substring(0, from)
        this.markDirty()
        result.flags = result.flags or (this.flags and ViewFlag.Composition.mask)
        return result
    }

    override fun coordsAt(pos: Int, side: Int): Rect? {
        return textCoords(this.dom as? Text, pos, side)
    }
}

/**
 * A view for a piece of text marked with a specific decoration.
 */
class MarkView(
    val mark: MarkDecoration,
    public override val children: MutableList<ContentView> = mutableListOf(),
    public override var length: Int = 0
) : ContentView() {
//    val dom: HTMLElement ? = null

    init {
        for (ch in children) ch.setParent(this)
    }

    override fun canReuseDOM(other: ContentView): Boolean {
        return super.canReuseDOM(other) && !((this.flags or other.flags) check ViewFlag.Composition.mask)
    }

    override fun sync(view: EditorView, track: Track?) {
        if (this.dom == null) {
            setDOM(document.createElement(this.mark.tagName).also { setAttrs(it) })
        } else if (this.flags check ViewFlag.AttrsDirty) {
            setAttrs(this.dom as HTMLElement)
        }
        super.sync(view, track)
    }

    private fun setAttrs(dom: HTMLElement) {
        clearAttributes(dom)
        this.mark.class?.let { dom.className = it }
        this.mark.attrs?.forEach { (name, value) -> dom.setAttribute(name, value) }
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
        return MarkView(this.mark, result.toMutableList(), length)
    }

    override fun domAtPos(pos: Int): DOMPos {
        return inlineDOMAtPos(this, pos)
    }

    override fun coordsAt(pos: Int, side: Int): Rect? {
        return coordsInChildren(this, pos, side)
    }
}

fun textCoords(text: Text?, pos: Int, side: Int): Rect? {
    if (text == null) return null
    var pos = pos
    val length = text.nodeValue?.length ?: 0
    if (pos > length) pos = length
    var from = pos
    var to = pos
    var flatten = 0
    if (pos == 0 && side < 0 || pos == length && side >= 0) {
        if (pos > 0) {
            from--
            flatten = 1
        } else if (to < length) {
            to++
            flatten = -1
        }
    } else {
        if (side < 0) from-- else if (to < length) to++
    }
    val rects = textRange(text, from, to).getClientRects()
    if (rects.isEmpty()) return null
    val rect = rects[if (flatten != 0) if (flatten < 0) 0 else rects.size - 1 else if (side >= 0) 0 else rects.size - 1]
    return if (flatten != 0) flattenRect(rect, flatten < 0) else rect
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
        if (this.dom == null || !this.widget.updateDOM(this.dom as HTMLElement, view)) {
            if (this.dom != null && this.prevWidget != null) this.prevWidget?.destroy(this.dom as HTMLElement)
            this.prevWidget = null
            this.setDOM(this.widget.toDOM(view))
            if (!this.widget.editable) (this.dom as? HTMLElement)?.contentEditable = "false"
        }
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
            if (!this.widget.compare(other.widget)) this.markDirty(true)
            if (this.dom != null && this.prevWidget == null) this.prevWidget = this.widget
            this.widget = other.widget
            this.length = other.length
            return true
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
        return if (this.length == 0 || pos == 0 && this.side > 0)
            DOMPos.before(this.dom!!)
        else
            DOMPos.after(this.dom!!, pos == this.length)
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
        if (this.dom != null) this.widget.destroy(this.dom as HTMLElement)
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
            val dom = document.createElement("img")
            dom.className = "cm-widgetBuffer"
            dom.setAttribute("aria-hidden", "true")
            this.setDOM(dom)
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
