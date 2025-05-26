package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.SpanIterator
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.TextIterator
import kotlin.math.max
import kotlin.math.min

// import {SpanIterator, RangeSet, Text, TextIterator} from "@codemirror/state"
// import {DecorationSet, Decoration, PointDecoration, LineDecoration, MarkDecoration, WidgetType} from "./decoration"
// import {ContentView} from "./contentview"
// import {BlockView, LineView, BlockWidgetView} from "./blockview"
// import {WidgetView, TextView, MarkView, WidgetBufferView} from "./inlineview"

const val Chunk = 512

object Buf {
    const val No = 0
    const val Yes = 1
    const val IfCursor = 2
}

interface BuiltView {
    val content: List<BlockView>
    val breakAtStart: Int
    val openStart: Int
    val openEnd: Int
}

class ContentBuilder(
    private val doc: Text,
    public var pos: Int,
    public val end: Int,
    val disallowBlockEffectsFor: List<Boolean>
) : SpanIterator<Decoration<*>>,
    BuiltView {
    override val content = mutableListOf<BlockView>()
    var curLine: LineView? = null
    override var breakAtStart = 0
    var pendingBuffer = Buf.No
    var bufferMarks = mutableListOf<MarkDecoration>()

    // Set to false directly after a widget that covers the position after it
    var atCursorPos = true
    override var openStart = -1
    override var openEnd = -1
    val cursor: TextIterator<*> = doc.iter()
    var text: String = ""
    var skip: Int = pos
    var textOff: Int = 0

    fun posCovered(): Boolean {
        if (this.content.size == 0) {
            return this.breakAtStart == 0 && this.doc.lineAt(this.pos).from != this.pos
        }
        val last = this.content[this.content.size - 1]
        return !(last.breakAfter == 0 || last is BlockWidgetView && last.deco.endSide < 0)
    }

    fun getLine(): LineView = this.curLine ?: LineView().also {
        this.curLine = it
        this.content.add(it)
        this.atCursorPos = true
    }

    fun flushBuffer(active: List<MarkDecoration> = this.bufferMarks) {
        if (this.pendingBuffer == Buf.No) {
            this.curLine!!.append(wrapMarks(WidgetBufferView(-1), active), active.size)
            this.pendingBuffer = Buf.No
        }
    }

    fun addBlockWidget(view: BlockWidgetView) {
        this.flushBuffer()
        this.curLine = null
        this.content.add(view)
    }

    fun finish(openEnd: Int) {
        if (this.pendingBuffer == Buf.No && openEnd <= this.bufferMarks.size) {
            this.flushBuffer()
        } else {
            this.pendingBuffer = Buf.No
        }
        if (!this.posCovered() &&
            !(
                openEnd != 0 &&
                    this.content.size != 0 &&
                    this.content[this.content.size - 1] is BlockWidgetView
                )
        ) {
            this.getLine()
        }
    }

    fun buildText(length: Int, active: List<MarkDecoration>, openStart: Int) {
        var length = length
        var openStart = openStart
        while (length > 0) {
            if (this.textOff == this.text.length) {
                val nextCursor = this.cursor.next(this.skip)
                val value = nextCursor.value
                val lineBreak = nextCursor.lineBreak
                val done = nextCursor.done
                this.skip = 0
                if (done) throw Error("Ran out of text content when drawing inline views")
                if (lineBreak) {
                    if (!this.posCovered()) this.getLine()
                    if (this.content.size != 0) {
                        this.content[this.content.size - 1].breakAfter = 1
                    } else {
                        this.breakAtStart = 1
                    }
                    this.flushBuffer()
                    this.curLine = null
                    this.atCursorPos = true
                    length--
                    continue
                } else {
                    this.text = value
                    this.textOff = 0
                }
            }
            val take = min(min(this.text.length - this.textOff, length), Chunk)
            this.flushBuffer(active.slice(active.size - openStart until active.size))
            this.getLine().append(
                wrapMarks(
                    TextView(this.text.slice(this.textOff until this.textOff + take)),
                    active
                ),
                openStart
            )
            this.atCursorPos = true
            this.textOff += take
            length -= take
            openStart = 0
        }
    }

    override fun span(from: Int, to: Int, active: List<Decoration<*>>, openStart: Int) {
        this.buildText(to - from, active.filterIsInstance<MarkDecoration>(), openStart)
        this.pos = to
        if (this.openStart < 0) this.openStart = openStart
    }

    override fun point(
        from: Int,
        to: Int,
        deco: Decoration<*>,
        active: List<Decoration<*>>,
        openStart: Int,
        index: Int
    ) {
        var openStart = openStart
        if (this.disallowBlockEffectsFor[index] && deco is PointDecoration) {
            if (deco.block) {
                throw IllegalArgumentException("Block decorations may not be specified via plugins")
            }
            if (to > this.doc.lineAt(this.pos).to) {
                throw IllegalArgumentException(
                    "Decorations that replace line breaks may not be specified via plugins"
                )
            }
        }
        val len = to - from
        if (deco is PointDecoration) {
            if (deco.block) {
                if (deco.startSide > 0 && !this.posCovered()) this.getLine()
                this.addBlockWidget(
                    BlockWidgetView(
                        deco.widget ?: NullWidget.block,
                        len,
                        deco
                    )
                )
            } else {
                val view = WidgetView.create(
                    deco.widget ?: NullWidget.inline,
                    len,
                    if (len != 0) 0 else deco.startSide
                )
                val cursorBefore =
                    this.atCursorPos &&
                        !view.isEditable &&
                        openStart <= active.size &&
                        (from < to || deco.startSide > 0)
                val cursorAfter =
                    !view.isEditable &&
                        (from < to || openStart > active.size || deco.startSide <= 0)
                val line = this.getLine()
                if (this.pendingBuffer == Buf.IfCursor &&
                    !cursorBefore &&
                    !view.isEditable
                ) {
                    this.pendingBuffer =
                        Buf.No
                }
                this.flushBuffer(active.filterIsInstance<MarkDecoration>())
                if (cursorBefore) {
                    line.append(
                        wrapMarks(
                            WidgetBufferView(1),
                            active.filterIsInstance<MarkDecoration>()
                        ),
                        openStart
                    )
                    openStart = active.size + max(0, openStart - active.size)
                }
                line.append(wrapMarks(view, active.filterIsInstance<MarkDecoration>()), openStart)
                this.atCursorPos = cursorAfter
                this.pendingBuffer = when {
                    !cursorAfter -> Buf.No
                    from < to || openStart > active.size -> Buf.Yes
                    else -> Buf.IfCursor
                }
                if (this.pendingBuffer != Buf.No) {
                    this.bufferMarks = active.filterIsInstance<MarkDecoration>().toMutableList()
                }
            }
        } else if (this.doc.lineAt(this.pos).from == this.pos) { // Line decoration
            this.getLine().addLineDeco(deco as LineDecoration)
        }

        if (len != 0) {
            // Advance the iterator past the replaced content
            if (this.textOff + len <= this.text.length) {
                this.textOff += len
            } else {
                this.skip += len - (this.text.length - this.textOff)
                this.text = ""
                this.textOff = 0
            }
            this.pos = to
        }
        if (this.openStart < 0) this.openStart = openStart
    }

    companion object {
        fun build(
            text: Text,
            from: Int,
            to: Int,
            decorations: List<DecorationSet>,
            dynamicDecorationMap: List<Boolean>
        ): BuiltView {
            val builder = ContentBuilder(text, from, to, dynamicDecorationMap)
            builder.openEnd = RangeSet.spans(decorations, from, to, builder)
            if (builder.openStart < 0) builder.openStart = builder.openEnd
            builder.finish(builder.openEnd)
            return builder
        }
    }
}

fun wrapMarks(view: ContentView, active: List<MarkDecoration>): ContentView {
    var view = view
    for (mark in active) view = MarkView(mark, listOf(view), view.length)
    return view
}

data class NullWidget(val tag: String) : WidgetType() {
    fun eq(other: NullWidget): Boolean = other.tag == this.tag

    //    fun toDOM() { return document.createElement(this.tag) }
//    fun updateDOM(elt: HTMLElement) { return elt.nodeName.toLowerCase() == this.tag }
    override val isHidden: Boolean
        get() {
            return true
        }

    companion object {
        val inline = NullWidget("span")
        val block = NullWidget("div")
    }
}
