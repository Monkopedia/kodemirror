package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Range
import com.monkopedia.kodemirror.state.SelectionRange
import com.monkopedia.kodemirror.state.countColumn
import com.monkopedia.kodemirror.state.findColumn
import kotlin.math.max
import kotlin.math.min
//import {Extension, EditorSelection, EditorState, countColumn, findColumn} from "@codemirror/state"
//import {EditorView} from "./editorview"
//import {MouseSelectionStyle} from "./input"
//import {ViewPlugin} from "./extension"

data class Pos (val line: Int,val  col: Int, val off: Int)

// Don't compute precise column positions for line offsets above this
// (since it could get expensive). Assume offset==column for them.
const val MaxOff = 2000

fun rectangleFor(state: EditorState, a: Pos, b: Pos): List<SelectionRange> {
    val startLine = min(a.line, b.line)
    val endLine = max(a.line, b.line)
    val ranges = mutableListOf<SelectionRange>()
    if (a.off > MaxOff || b.off > MaxOff || a.col < 0 || b.col < 0) {
        val startOff = min(a.off, b.off)
        val endOff = max(a.off, b.off)
        for (i in startLine..endLine) {
            val line = state.doc.line(i)
            if (line.length <= endOff)
                ranges.add(EditorSelection.range(line.from + startOff, line.to + endOff))
        }
    } else {
        val startCol = min(a.col, b.col)
        val endCol = max(a.col, b.col)
        for (i in startLine..endLine) {
            val line = state.doc.line(i)
            val start = findColumn(line.text, startCol, state.tabSize, true)
            if (start < 0) {
                ranges.add(EditorSelection.cursor(line.to))
            } else {
                val end = findColumn(line.text, endCol, state.tabSize)
                ranges.add(EditorSelection.range(line.from + start, line.from + end))
            }
        }
    }
    return ranges
}

fun absoluteColumn(view: EditorView, x: Int) : Int {
    val ref = view.coordsAtPos(view.viewport.from) ?: return -1
    return Math.abs((ref.left - x) / view.defaultCharacterWidth).roundToInt()
}

fun getPos(view: EditorView, event: MouseEvent): Pos {
    val offset = view.posAtCoords({x: event.clientX, y: event.clientY}, false)
    val line = view.state.doc.lineAt(offset)
    val off = offset - line.from
    val col = when {
        off > MaxOff -> -1
        off == line.length -> absoluteColumn(view, event.clientX)
        else -> countColumn(line.text, view.state.tabSize, offset - line.from)
    }
    return Pos(line= line.number, col, off)
}

fun rectangleSelectionStyle(view: EditorView, event: MouseEvent): MouseSelectionStyle? {
    var start = getPos(view, event) ?: return null
    var startSel = view.state.selection
    return object : MouseSelectionStyle {
        override fun update(update: ViewUpdate): Boolean? {
            if (update.docChanged) {
                val newStart = update.changes.mapPos(update.startState.doc.line(start.line).from)
                val newLine = update.state.doc.lineAt(newStart)
                start = {line: newLine.number, col: start.col, off: Math.min(start.off, newLine.length)}
                startSel = startSel.map(update.changes)
            }
            return null
        }

        override fun get(
            curEvent: MouseEvent,
            extend: Boolean,
            multiple: Boolean
        ): EditorSelection {
            val cur = getPos(view, curEvent) ?: return startSel
            val ranges = rectangleFor(view.state, start, cur)
            if (ranges.isEmpty()) return startSel
            return if (multiple) EditorSelection.create(ranges + startSel.ranges)
            else EditorSelection.create(ranges)
        }
    }
}

/// Create an extension that enables rectangular selections. By
/// default, it will react to left mouse drag with the Alt key held
/// down. When such a selection occurs, the text within the rectangle
/// that was dragged over will be selected, as one selection
/// [range](#state.SelectionRange) per line.
export function rectangularSelection(options?: {
    /// A custom predicate function, which takes a `mousedown` event and
    /// returns true if it should be used for rectangular selection.
    eventFilter?: (event: MouseEvent) => boolean
}): Extension {
    let filter = options?.eventFilter || (e => e.altKey && e.button == 0)
    return EditorView.mouseSelectionStyle.of((view, event) => filter(event) ? rectangleSelectionStyle(view, event) : null)
}

const keys: {[key: string]: [number, (event: KeyboardEvent | MouseEvent) => boolean]} = {
    Alt: [18, e => !!e.altKey],
    Control: [17, e => !!e.ctrlKey],
    Shift: [16, e => !!e.shiftKey],
    Meta: [91, e => !!e.metaKey]
}

const showCrosshair = {style: "cursor: crosshair"}

/// Returns an extension that turns the pointer cursor into a
/// crosshair when a given modifier key, defaulting to Alt, is held
/// down. Can serve as a visual hint that rectangular selection is
/// going to happen when paired with
/// [`rectangularSelection`](#view.rectangularSelection).
export function crosshairCursor(options: {
    key?: "Alt" | "Control" | "Shift" | "Meta"
} = {}): Extension {
    let [code, getter] = keys[options.key || "Alt"]
    let plugin = ViewPlugin.fromClass(class {
        isDown = false
        constructor(readonly view: EditorView) {}
        set(isDown: boolean) {
            if (this.isDown != isDown) {
                this.isDown = isDown
                this.view.update([])
            }
        }
    }, {
        eventObservers: {
        keydown(e) {
            this.set(e.keyCode == code || getter(e))
        },
        keyup(e) {
            if (e.keyCode == code || !getter(e)) this.set(false)
        },
        mousemove(e) {
            this.set(getter(e))
        }
    }
    })
    return [
        plugin,
        EditorView.contentAttributes.of(view => view.plugin(plugin)?.isDown ? showCrosshair : null)
    ]
}
