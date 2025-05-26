package com.monkopedia.kodemirror.view

import androidx.lifecycle.Lifecycle.Event
import com.monkopedia.kodemirror.state.Side
import com.monkopedia.kodemirror.state.Text

//import {ContentView, DOMPos, ViewFlag, noChildren, mergeChildrenInto} from "./contentview"
//import {DocView} from "./docview"
//import {TextView, MarkView, inlineDOMAtPos, joinInlineInto, coordsInChildren} from "./inlineview"
//import {clientRectsFor, Rect, flattenRect, clearAttributes} from "./dom"
//import {LineDecoration, WidgetType, PointDecoration} from "./decoration"
//import {Attrs, combineAttrs, attrsEq, updateAttrs} from "./attributes"
//import browser from "./browser"
//import {EditorView} from "./editorview"
//import {Text} from "@codemirror/state"

abstract class BlockView : ContentView() {
    abstract fun covers(side: Boolean): Boolean
//    val dom: HTMLElement ?
}

class LineView : BlockView() {
    override val children: MutableList<ContentView> = mutableListOf()
    override var length: Int = 0
    val prevAttrs: Attrs ? = null
    val attrs: Attrs ? = null
    override var breakAfter = 0

    // Consumes source
    fun merge(from: Int, to: Int, source: BlockView ?, hasStart: Boolean, openStart: Int, openEnd: Int): Boolean {
//        if (source) {
//            if (!(source instanceof LineView)) return false
//            if (!this.dom) source.transferDOM(this) // Reuse source.dom when appropriate
//        }
//        if (hasStart) this.setDeco(source ? source.attrs : null)
//        mergeChildrenInto(this, from, to, source ? source.children.slice() : [], openStart, openEnd)
//        return true
        error("Needs impl")
    }

    override fun split(at: Int): LineView {
//        let end = new LineView
//        end.breakAfter = this.breakAfter
//        if (this.length == 0) return end
//        let {i, off} = this.childPos(at)
//        if (off) {
//            end.append(this.children[i].split(off), 0)
//            this.children[i].merge(off, this.children[i].length, null, false, 0, 0)
//            i++
//        }
//        for (let j = i; j < this.children.length; j++) end.append(this.children[j], 0)
//        while (i > 0 && this.children[i - 1].length == 0) this.children[--i].destroy()
//        this.children.length = i
//        this.markDirty()
//        this.length = at
//        return end
        error("Needs impl")
    }

    fun transferDOM(other: LineView) {
//        if (!this.dom) return
//        this.markDirty()
//        other.setDOM(this.dom)
//        other.prevAttrs = this.prevAttrs === undefined ? this.attrs : this.prevAttrs
//        this.prevAttrs = undefined
//        this.dom = null
        error("Needs impl")
    }

    fun setDeco(attrs: Attrs ?) {
//        if (!attrsEq(this.attrs, attrs)) {
//            if (this.dom) {
//                this.prevAttrs = this.attrs
//                this.markDirty()
//            }
//            this.attrs = attrs
//        }
        error("Needs impl")
    }

    fun append(child: ContentView, openStart: Int) {
        joinInlineInto(this, child, openStart)
    }

    // Only called when building a line view in ContentBuilder
    fun addLineDeco(deco: LineDecoration) {
//        let attrs = deco.spec.attributes, cls = deco.spec.class
//        if (attrs) this.attrs = combineAttrs(attrs, this.attrs || {})
//        if (cls) this.attrs = combineAttrs({class: cls}, this.attrs || {});
        error("Needs impl")
    }

    override fun domAtPos(pos: Int): DOMPos {
        return inlineDOMAtPos(this, pos)
    }

    fun reuseDOM(node: Node) {
//        if (node.nodeName == "DIV") {
//            this.setDOM(node)
//            this.flags |= ViewFlag.AttrsDirty | ViewFlag.NodeDirty
//        }
        error("Needs impl")
    }

    data class NodeTrack(
        val node: Node,
        val written: Boolean
    )
    fun sync(view: EditorView, track: NodeTrack? = null) {
//        if (!this.dom) {
//            this.setDOM(document.createElement("div"))
//            this.dom!.className = "cm-line"
//            this.prevAttrs = this.attrs ? null : undefined
//        } else if (this.flags & ViewFlag.AttrsDirty) {
//        clearAttributes(this.dom)
//        this.dom!.className = "cm-line"
//        this.prevAttrs = this.attrs ? null : undefined
//    }
//        if (this.prevAttrs !== undefined) {
//            updateAttrs(this.dom!, this.prevAttrs, this.attrs)
//            this.dom!.classList.add("cm-line")
//            this.prevAttrs = undefined
//        }
//        super.sync(view, track)
//        let last = this.dom!.lastChild
//        while (last && ContentView.get(last) instanceof MarkView)
//            last = last.lastChild
//        if (!last || !this.length ||
//            last.nodeName != "BR" && ContentView.get(last)?.isEditable == false &&
//            (!browser.ios || !this.children.some(ch => ch instanceof TextView))) {
//        let hack = document.createElement("BR")
//        ;(hack as any).cmIgnore = true
//        this.dom!.appendChild(hack)
//    }
        error("Needs impl")
    }

    data class MeasuredTextSize(
        val lineHeight: Int,
        val charWidth: Int,
        val textHeight: Int
    )
    fun measureTextSize(): MeasuredTextSize? {
//        if (this.children.length == 0 || this.length > 20) return null
//        let totalWidth = 0, textHeight!: number
//        for (let child of this.children) {
//        if (!(child instanceof TextView) || /[^ -~]/.test(child.text)) return null
//        let rects = clientRectsFor(child.dom!)
//        if (rects.length != 1) return null
//        totalWidth += rects[0].width
//        textHeight = rects[0].height
//    }
//        return !totalWidth ? null : {
//        lineHeight: this.dom!.getBoundingClientRect().height,
//        charWidth: totalWidth / this.length,
//        textHeight
//    }
        error("Needs impl")
    }

    override fun coordsAt(pos: Int, side: Int): Rect ? {
//        let rect = coordsInChildren(this, pos, side)
//        // Correct rectangle height for empty lines when the returned
//        // height is larger than the text height.
//        if (!this.children.length && rect && this.parent) {
//            let {heightOracle} = this.parent.view.viewState, height = rect.bottom - rect.top
//            if (Math.abs(height - heightOracle.lineHeight) < 2 && heightOracle.textHeight < height) {
//                let dist = (height - heightOracle.textHeight) / 2
//                return {top: rect.top + dist, bottom: rect.bottom - dist, left: rect.left, right: rect.left}
//            }
//        }
//        return rect
        error("Needs impl")
    }

    override fun become(other: ContentView): Boolean {
//        return other instanceof LineView && this.children.length == 0 && other.children.length == 0 &&
//            attrsEq(this.attrs, other.attrs) && this.breakAfter == other.breakAfter
        error("Needs impl")
    }

    fun covers(): Boolean { return true }

    companion object {

    fun find(docView: DocView, pos: Int): LineView ? {
//        for (let i = 0, off = 0; i < docView.children.length; i++) {
//        let block = docView.children[i], end = off + block.length
//        if (end >= pos) {
//            if (block instanceof LineView) return block
//            if (end > pos) break
//        }
//        off = end + block.breakAfter
//    }
//        return null
        error("Needs impl")
    }
    }
}

class BlockWidgetView(val widget: WidgetType, override var length: Int, val deco: PointDecoration) : BlockView() {
    override var breakAfter = 0
    val prevWidget: WidgetType ? = null

    override fun merge(from: Int, to: Int, source: ContentView ?, _takeDeco: Boolean, openStart: Int, openEnd: Int): Boolean {
//        if (source && (!(source instanceof BlockWidgetView) || !this.widget.compare(source.widget) ||
//                from > 0 && openStart <= 0 || to < this.length && openEnd <= 0))
//            return false
//        this.length = from + (source ? source.length : 0) + (this.length - to)
//        return true
        error("Needs impl")
    }

    override fun domAtPos(pos: Int): DOMPos {
//        return pos == 0 ? DOMPos.before(this.dom!) : DOMPos.after(this.dom!, pos == this.length)
        error("Needs impl")
    }

    override fun split(at: Int): ContentView {
//        let len = this.length - at
//        this.length = at
//        let end = new BlockWidgetView(this.widget, len, this.deco)
//        end.breakAfter = this.breakAfter
//        return end
        error("Needs impl")
    }

    override val children: MutableList<ContentView>
        get() = mutableListOf()

    fun sync(view: EditorView) {
//        if (!this.dom || !this.widget.updateDOM(this.dom, view)) {
//            if (this.dom && this.prevWidget) this.prevWidget.destroy(this.dom)
//            this.prevWidget = null
//            this.setDOM(this.widget.toDOM(view))
//            if (!this.widget.editable) this.dom!.contentEditable = "false"
//        }
        error("Needs impl")
    }

    override val overrideDOMText: Text
        get() {
//        return this.parent ? this.parent!.view.state.doc.slice(this.posAtStart, this.posAtEnd) : Text.empty
            error("Needs impl")
    }

    fun domBoundsAround(): Nothing? { return null }

    override fun become(other: ContentView): Boolean {
//        if (other instanceof BlockWidgetView &&
//            other.widget.constructor == this.widget.constructor) {
//            if (!other.widget.compare(this.widget)) this.markDirty(true)
//            if (this.dom && !this.prevWidget) this.prevWidget = this.widget
//            this.widget = other.widget
//            this.length = other.length
//            this.deco = other.deco
//            this.breakAfter = other.breakAfter
//            return true
//        }
//        return false
        error("Needs impl")
    }

    override fun ignoreMutation(_rec: MutationRecord): Boolean {
        return true }
    fun ignoreEvent(event: Event): Boolean { return this.widget.ignoreEvent(event) }

    override val isEditable: Boolean
        get() = false

    override val isWidget: Boolean
        get() = true

    override fun coordsAt(pos: Int, side: Int): Rect? {
//        let custom = this.widget.coordsAt(this.dom!, pos, side)
//        if (custom) return custom
//        if (this.widget instanceof BlockGapWidget) return null
//        return flattenRect(this.dom!.getBoundingClientRect(), this.length ? pos == 0 : side <= 0)
        error("Needs impl")
    }

    override fun destroy() {
        super.destroy()
//        if (this.dom) this.widget.destroy(this.dom)
        error("Needs impl")
    }

    fun covers(side: Side): Boolean {
//        let {startSide, endSide} = this.deco
//        return startSide == endSide ? false : side < 0 ? startSide < 0 : endSide > 0
        error("Needs impl")
    }
}

class BlockGapWidget() : WidgetType() {
    constructor(height: Int)

    fun toDOM() {
        let elt = document.createElement("div")
        elt.className = "cm-gap"
        this.updateDOM(elt)
        return elt
    }

    eq(other: BlockGapWidget) { return other.height == this.height }

    updateDOM(elt: HTMLElement) {
        elt.style.height = this.height + "px"
        return true
    }

    get editable() { return true }

    get estimatedHeight() { return this.height }

    ignoreEvent() { return false }
}
