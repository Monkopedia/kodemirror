package com.monkopedia.kodemirror.view

import {EditorView} from "./editorview"
import {inputHandler, editable} from "./extension"
import {contains, dispatchKey} from "./dom"
import browser from "./browser"
import {DOMReader, DOMPoint, LineBreakPlaceholder} from "./domreader"
import {findCompositionNode} from "./docview"
import {EditorSelection, Text, Transaction, TransactionSpec} from "@codemirror/state"
import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.extension.*
import com.monkopedia.kodemirror.dom.*
import org.w3c.dom.*

/**
 * Represents a change in the DOM content.
 */
class DOMChange(
    private val view: EditorView,
    start: Int,
    end: Int,
    val typeOver: Boolean
) {
    data class Bounds(
        val startDOM: Node?,
        val endDOM: Node?,
        val from: Int,
        val to: Int
    )

    var bounds: Bounds? = null
    var text: String = ""
    var newSel: EditorSelection? = null
    var domChanged: Boolean = start > -1

    init {
        val iHead = view.docView.impreciseHead
        val iAnchor = view.docView.impreciseAnchor

        if (view.state.readOnly && start > -1) {
            // Ignore changes when the editor is read-only
            newSel = null
        } else if (start > -1) {
            bounds = view.docView.domBoundsAround(start, end, 0)?.let { (startDOM, endDOM, from, to) ->
                Bounds(startDOM, endDOM, from, to)
            }
            if (bounds != null) {
                val selPoints = if (iHead || iAnchor) emptyList() else selectionPoints(view)
                val reader = DOMReader(selPoints, view.state)
                reader.readRange(bounds!!.startDOM, bounds!!.endDOM)
                text = reader.text
                newSel = selectionFromPoints(selPoints, bounds!!.from)
            }
        }
    }
}

/**
 * Apply a DOM change to the editor.
 */
fun applyDOMChange(view: EditorView, domChange: DOMChange): Boolean {
    var change: Change? = null
    val newSel = domChange.newSel
    val sel = view.state.selection.main
    val lastKey = if (view.inputState.lastKeyTime > Date.now() - 100) view.inputState.lastKeyCode else -1

    if (domChange.bounds != null) {
        val (from, to) = domChange.bounds!!
        var preferredPos = sel.from
        var preferredSide: String? = null

        // Prefer anchoring to end when Backspace is pressed (or on Android when something was deleted)
        if (lastKey == 8 || (browser.android && domChange.text.length < to - from)) {
            preferredPos = sel.to
            preferredSide = "end"
        }

        val diff = findDiff(
            view.state.doc.sliceString(from, to, LineBreakPlaceholder),
            domChange.text,
            preferredPos - from,
            preferredSide
        )

        if (diff != null) {
            // Chrome inserts two newlines when pressing shift-enter at the end of a line
            // DomChange drops one of those
            if (browser.chrome && lastKey == 13 &&
                diff.toB == diff.from + 2 && 
                domChange.text.substring(diff.from, diff.toB) == "$LineBreakPlaceholder$LineBreakPlaceholder") {
                diff.toB--
            }

            change = Change(
                from = from + diff.from,
                to = from + diff.toA,
                insert = view.state.toText(domChange.text.substring(diff.from, diff.toB).split(LineBreakPlaceholder))
            )
        }
    } else if (newSel != null && (!view.hasFocus && view.state.facet(editable) || newSel.main.eq(sel))) {
        return false
    }

    if (change == null && newSel == null) return false

    if (change == null && domChange.typeOver && !sel.empty && newSel?.main?.empty == true) {
        // Heuristic to notice typing over a selected character
        change = Change(
            from = sel.from,
            to = sel.to,
            insert = view.state.doc.slice(sel.from, sel.to)
        )
    }

    if (change != null) {
        return applyDOMChangeInner(view, change, newSel, lastKey)
    } else if (newSel != null) {
        var scrollIntoView = false
        var userEvent = "select"
        if (view.inputState.lastSelectionTime > Date.now() - 50) {
            if (view.inputState.lastSelectionOrigin == "select") scrollIntoView = true
            userEvent = view.inputState.lastSelectionOrigin ?: userEvent
        }
        view.dispatch(
            TransactionSpec(
                selection = newSel,
                scrollIntoView = scrollIntoView,
                userEvent = userEvent
            )
        )
        return true
    }
    return false
}

/**
 * Apply a DOM change internally.
 */
fun applyDOMChangeInner(
    view: EditorView,
    change: Change,
    newSel: EditorSelection?,
    lastKey: Int = -1
): Boolean {
    if (browser.ios && view.inputState.flushIOSKey(change)) return true
    
    val sel = view.state.selection.main

    // Android browsers don't fire reasonable key events for enter,
    // backspace, or delete. So this detects changes that look like
    // they're caused by those keys, and reinterprets them as key events.
    if (browser.android) {
        if (change.to == sel.to &&
            (change.from == sel.from || change.from == sel.from - 1 && 
             view.state.sliceDoc(change.from, sel.from) == " ") &&
            change.insert.length == 1 && change.insert.lines == 2 &&
            dispatchKey(view.contentDOM, "Enter", 13)) {
            return true
        }
        if ((change.from == sel.from - 1 && change.to == sel.to && change.insert.isEmpty() ||
             lastKey == 8 && change.insert.length < change.to - change.from && change.to > sel.head) &&
            dispatchKey(view.contentDOM, "Backspace", 8)) {
            return true
        }
        if (change.from == sel.from && change.to == sel.to + 1 && change.insert.isEmpty() &&
            dispatchKey(view.contentDOM, "Delete", 46)) {
            return true
        }
    }

    val text = change.insert.toString()
    if (view.inputState.composing >= 0) view.inputState.composing++

    var defaultTr: Transaction? = null
    val defaultInsert = {
        defaultTr ?: applyDefaultInsert(view, change, newSel).also { defaultTr = it }
    }

    if (!view.state.facet(inputHandler).any { it(view, change.from, change.to, text, defaultInsert) }) {
        view.dispatch(defaultInsert())
    }
    return true
}

/**
 * Find the difference between two strings.
 */
private fun findDiff(
    a: String,
    b: String,
    preferredPos: Int,
    preferredSide: String?
): Diff? {
    val minLen = minOf(a.length, b.length)
    var from = 0
    while (from < minLen && a[from] == b[from]) from++
    if (from == minLen && a.length == b.length) return null

    var toA = a.length
    var toB = b.length
    while (toA > 0 && toB > 0 && a[toA - 1] == b[toB - 1]) {
        toA--
        toB--
    }

    if (preferredSide == "end") {
        val adjust = maxOf(0, from - minOf(toA, toB))
        preferredPos -= toA + adjust - from
    }

    if (toA < from && a.length < b.length) {
        val move = if (preferredPos <= from && preferredPos >= toA) from - preferredPos else 0
        from -= move
        toB = from + (toB - toA)
        toA = from
    } else if (toB < from) {
        val move = if (preferredPos <= from && preferredPos >= toB) from - preferredPos else 0
        from -= move
        toA = from + (toA - toB)
        toB = from
    }

    return Diff(from, toA, toB)
}

/**
 * Apply a default insert operation.
 */
private fun applyDefaultInsert(
    view: EditorView,
    change: Change,
    newSel: EditorSelection?
): Transaction {
    val startState = view.state
    val sel = startState.selection.main

    val tr = if (change.from >= sel.from && change.to <= sel.to &&
        change.to - change.from >= (sel.to - sel.from) / 3 &&
        (newSel == null || (newSel.main.empty && newSel.main.from == change.from + change.insert.length)) &&
        view.inputState.composing < 0) {
        
        val before = if (sel.from < change.from) startState.sliceDoc(sel.from, change.from) else ""
        val after = if (sel.to > change.to) startState.sliceDoc(change.to, sel.to) else ""
        
        startState.replaceSelection(view.state.toText(
            before + change.insert.sliceString(0, null, view.state.lineBreak) + after
        ))
    } else {
        val changes = startState.changes(change)
        val mainSel = if (newSel?.main?.to ?: 0 <= changes.newLength) newSel?.main else null

        // Try to apply composition change to all cursors
        if (startState.selection.ranges.size > 1 && view.inputState.composing >= 0 &&
            change.to <= sel.to && change.to >= sel.to - 10) {
            
            val replaced = view.state.sliceDoc(change.from, change.to)
            val compositionRange = newSel?.let { findCompositionNode(view, it.main.head) }?.let { comp ->
                val dLen = change.insert.length - (change.to - change.from)
                CompositionRange(comp.from, comp.to - dLen)
            } ?: view.state.doc.lineAt(sel.head)

            val offset = sel.to - change.to
            val size = sel.to - sel.from

            startState.changeByRange { range ->
                if (range.from == sel.from && range.to == sel.to) {
                    ChangeSet(changes = changes, range = mainSel ?: range.map(changes))
                } else {
                    val to = range.to - offset
                    val from = to - replaced.length
                    if (range.to - range.from != size || view.state.sliceDoc(from, to) != replaced ||
                        range.to >= compositionRange.from && range.from <= compositionRange.to) {
                        ChangeSet(range = range)
                    } else {
                        val rangeChanges = startState.changes(Change(from, to, change.insert))
                        val selOff = range.to - sel.to
                        ChangeSet(
                            changes = rangeChanges,
                            range = if (mainSel == null) range.map(rangeChanges) else
                                EditorSelection.range(
                                    maxOf(0, mainSel.anchor + selOff),
                                    maxOf(0, mainSel.head + selOff)
                                )
                        )
                    }
                }
            }
        } else {
            TransactionSpec(
                changes = changes,
                selection = mainSel?.let { startState.selection.replaceRange(it) }
            )
        }
    }

    var userEvent = "input.type"
    if (view.composing ||
        (view.inputState.compositionPendingChange && 
         view.inputState.compositionEndedAt > Date.now() - 50)) {
        view.inputState.compositionPendingChange = false
        userEvent += ".compose"
        if (view.inputState.compositionFirstChange) {
            userEvent += ".start"
            view.inputState.compositionFirstChange = false
        }
    }

    return startState.update(tr, userEvent = userEvent, scrollIntoView = true)
}

/**
 * Represents a change in the document.
 */
private data class Change(
    val from: Int,
    val to: Int,
    val insert: Text
)

/**
 * Represents a difference between two strings.
 */
private data class Diff(
    val from: Int,
    var toA: Int,
    var toB: Int
)

/**
 * Represents a composition range.
 */
private data class CompositionRange(
    val from: Int,
    val to: Int
)

/**
 * Represents a set of changes.
 */
private data class ChangeSet(
    val changes: Changes? = null,
    val range: SelectionRange
)

/**
 * Represents a point in the DOM.
 */
data class DOMPoint(
    val node: Node,
    val offset: Int
) {
    var pos: Int = -1
}

/**
 * Get selection points from the editor view.
 */
private fun selectionPoints(view: EditorView): List<DOMPoint> {
    val result = mutableListOf<DOMPoint>()
    if (view.root.activeElement != view.contentDOM) return result
    
    val (anchorNode, anchorOffset, focusNode, focusOffset) = view.observer.selectionRange
    if (anchorNode != null) {
        result.add(DOMPoint(anchorNode, anchorOffset))
        if (focusNode != anchorNode || focusOffset != anchorOffset) {
            focusNode?.let { result.add(DOMPoint(it, focusOffset)) }
        }
    }
    return result
}

/**
 * Create a selection from DOM points.
 */
private fun selectionFromPoints(points: List<DOMPoint>, base: Int): EditorSelection? {
    if (points.isEmpty()) return null
    val anchor = points[0].pos
    val head = if (points.size == 2) points[1].pos else anchor
    if (anchor < 0 || head < 0) return null
    return EditorSelection.single(anchor + base, head + base)
}
