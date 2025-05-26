package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.dom.*
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.DOMRect
import org.w3c.dom.Element
import org.w3c.dom.events.Event

/**
 * Event fired when text content is updated.
 */
external interface TextUpdateEvent : Event {
    val updateRangeStart: Int
    val updateRangeEnd: Int
    val text: String
    val selectionStart: Int
    val selectionEnd: Int
}

/**
 * Event fired when text formatting is updated.
 */
external interface TextFormatUpdateEvent : Event {
    val formatRangeStart: Int
    val formatRangeEnd: Int
    val underlineStyle: String?
    val textDecorationLine: String?
    val textDecorationStyle: String?
    val textDecorationColor: String?
}

/**
 * Event fired when character bounds are updated.
 */
external interface CharacterBoundsUpdateEvent : Event {
    val rangeStart: Int
    val rangeEnd: Int
}

/**
 * Event fired during composition.
 */
external interface CompositionEvent : Event {
    val data: String
}

/**
 * Options for creating an EditContext.
 */
external interface EditContextOptions {
    var text: String?
    var selectionStart: Int?
    var selectionEnd: Int?
}

/**
 * The EditContext API provides a way to integrate with the platform's text input
 * and composition system. It allows for better control over text input handling
 * and IME composition.
 */
external class EditContext(options: EditContextOptions = definedExternally) {
    /**
     * Update the text content in the specified range.
     */
    fun updateText(rangeStart: Int, rangeEnd: Int, text: String)

    /**
     * Update the selection range.
     */
    fun updateSelection(start: Int, end: Int)

    /**
     * Update the bounds of the control area.
     */
    fun updateControlBounds(controlBound: DOMRect)

    /**
     * Update the bounds of the selection.
     */
    fun updateSelectionBounds(selectionBound: DOMRect)

    /**
     * Update the bounds of individual characters.
     */
    fun updateCharacterBounds(rangeStart: Int, characterBounds: Array<DOMRect>)

    /**
     * Get the list of attached elements.
     */
    fun attachedElements(): Array<Element>

    /**
     * The current text content.
     */
    val text: String

    /**
     * The start of the current selection.
     */
    val selectionStart: Int

    /**
     * The end of the current selection.
     */
    val selectionEnd: Int

    /**
     * The start of the current composition range.
     */
    val compositionRangeStart: Int

    /**
     * The end of the current composition range.
     */
    val compositionRangeEnd: Int

    /**
     * Whether there is an active composition.
     */
    val isInComposition: Boolean

    /**
     * The bounds of the control area.
     */
    val controlBound: DOMRect

    /**
     * The bounds of the selection.
     */
    val selectionBound: DOMRect

    /**
     * The start position for character bounds.
     */
    val characterBoundsRangeStart: Int

    /**
     * Get the bounds of all characters.
     */
    fun characterBounds(): Array<DOMRect>

    /**
     * Add an event listener.
     */
    fun addEventListener(type: String, handler: (Event) -> Unit)

    /**
     * Remove an event listener.
     */
    fun removeEventListener(type: String, handler: (Event) -> Unit)
}

/**
 * Extension to add EditContext support to HTMLElement.
 */
external var HTMLElement.editContext: EditContext?

/**
 * Extension to add EditContext constructor to Window.
 */
external val Window.EditContext: dynamic

/**
 * Manages the EditContext integration with the editor.
 * Handles text updates, selection changes, and composition events.
 */
class EditContextManager(private val view: EditorView) {
    private val editContext: EditContext
    private val measureReq: MeasureRequest<Unit>
    private var from: Int = 0
    private var to: Int = 0
    private var pendingContextChange: ContextChange? = null
    private var composing: CompositionState? = null
    private val handlers = mutableMapOf<String, (Event) -> Unit>()

    init {
        resetRange(view.state)

        editContext = window.EditContext(object : EditContextOptions {
            override var text = view.state.doc.sliceString(from, to)
            override var selectionStart = toContextPos(
                maxOf(from, minOf(to, view.state.selection.main.anchor))
            )
            override var selectionEnd = toContextPos(view.state.selection.main.head)
        })

        handlers["textupdate"] = { event ->
            val e = event as TextUpdateEvent
            val anchor = view.state.selection.main.anchor
            var from = toEditorPos(e.updateRangeStart)
            var to = toEditorPos(e.updateRangeEnd)

            if (view.inputState.composing >= 0 && composing == null) {
                composing = CompositionState(
                    contextBase = e.updateRangeStart,
                    editorBase = from,
                    drifted = false
                )
            }

            val change = ContextChange(
                from = from,
                to = to,
                insert = view.state.toText(e.text)
            )

            // If window doesn't include anchor, assume changes adjacent to side go up to anchor
            if (change.from == this.from && anchor < this.from) change.from = anchor
            else if (change.to == this.to && anchor > this.to) change.to = anchor

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

            // If transaction didn't flush our change, revert it
            if (pendingContextChange != null) {
                revertPending(view.state)
                setSelection(view.state)
            }
        }

        measureReq = MeasureRequest { view ->
            editContext.updateControlBounds(view.contentDOM.getBoundingClientRect())
            val sel = getSelection(view.root)
            if (sel != null && sel.rangeCount > 0) {
                editContext.updateSelectionBounds(sel.getRangeAt(0).getBoundingClientRect())
            }
        }

        handlers.forEach { (event, handler) ->
            editContext.addEventListener(event, handler)
        }
    }

    fun applyEdits(update: ViewUpdate): Boolean {
        var off = 0
        var abort = false
        val pending = pendingContextChange

        update.changes.iterChanges { fromA, toA, _fromB, _toB, insert ->
            if (abort) return@iterChanges

            val dLen = insert.length - (toA - fromA)
            if (pending != null && toA >= pending.to) {
                if (pending.from == fromA && pending.to == toA && pending.insert.eq(insert)) {
                    pendingContextChange = null // Match
                    off += dLen
                    this.to += dLen
                    return@iterChanges
                } else { // Mismatch, revert
                    pendingContextChange = null
                    revertPending(update.state)
                }
            }

            val newFromA = fromA + off
            val newToA = toA + off
            if (newToA <= this.from) { // Before window
                this.from += dLen
                this.to += dLen
            } else if (newFromA < this.to) { // Overlaps with window
                if (newFromA < this.from || newToA > this.to || (this.to - this.from) + insert.length > MaxSize) {
                    abort = true
                    return@iterChanges
                }
                editContext.updateText(toContextPos(newFromA), toContextPos(newToA), insert.toString())
                this.to += dLen
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

    private fun resetRange(state: EditorState) {
        val head = state.selection.main.head
        from = maxOf(0, head - Margin)
        to = minOf(state.doc.length, head + Margin)
    }

    private fun reset(state: EditorState) {
        resetRange(state)
        editContext.updateText(0, editContext.text.length, state.doc.sliceString(from, to))
        setSelection(state)
    }

    private fun revertPending(state: EditorState) {
        val pending = pendingContextChange!!
        pendingContextChange = null
        editContext.updateText(
            toContextPos(pending.from),
            toContextPos(pending.from + pending.insert.length),
            state.doc.sliceString(pending.from, pending.to)
        )
    }

    private fun setSelection(state: EditorState) {
        val main = state.selection.main
        val start = toContextPos(maxOf(from, minOf(to, main.anchor)))
        val end = toContextPos(main.head)
        if (editContext.selectionStart != start || editContext.selectionEnd != end) {
            editContext.updateSelection(start, end)
        }
    }

    private fun toContextPos(pos: Int, len: Int = to - from): Int {
        return if (composing != null && pos >= composing!!.editorBase) {
            composing!!.contextBase + (pos - composing!!.editorBase)
        } else {
            maxOf(0, minOf(len, pos - from))
        }
    }

    private fun toEditorPos(pos: Int, len: Int = to - from): Int {
        return if (composing != null && pos >= composing!!.contextBase) {
            composing!!.editorBase + (pos - composing!!.contextBase)
        } else {
            from + maxOf(0, minOf(len, pos))
        }
    }

    private fun rangeIsValid(state: EditorState): Boolean {
        val head = state.selection.main.head
        return head >= from - Margin && head <= to + Margin
    }

    companion object {
        private const val MaxSize = 500
        private const val Margin = 250
    }

    private data class ContextChange(
        var from: Int,
        var to: Int,
        val insert: Text
    )

    private data class CompositionState(
        val contextBase: Int,
        var editorBase: Int,
        var drifted: Boolean
    )
}
