package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.extension.*
import com.monkopedia.kodemirror.dom.*
import kotlinx.browser.document

/**
 * State effect to set the drop cursor position.
 */
private val setDropCursorPos = StateEffect.define<Int?> { pos, mapping ->
    pos?.let { mapping.mapPos(it) }
}

/**
 * State field to track the drop cursor position.
 */
private val dropCursorPos = StateField.define<Int?>(
    create = { null },
    update = { pos, tr ->
        pos?.let { tr.changes.mapPos(it) }?.let { mappedPos ->
            tr.effects.fold(mappedPos) { p, e ->
                if (e.isEffect(setDropCursorPos)) e.value as Int? else p
            }
        }
    }
)

/**
 * Plugin to draw and manage the drop cursor.
 */
private val drawDropCursor = ViewPlugin.fromClass(
    create = { view ->
        DropCursorView(view)
    }
)

/**
 * View class that handles the drop cursor rendering and events.
 */
private class DropCursorView(private val view: EditorView) {
    private var cursor: Element? = null
    private val measureReq = MeasureRequest(
        read = { readPos() },
        write = { pos -> drawCursor(pos) }
    )

    fun update(update: ViewUpdate) {
        val cursorPos = update.state.field(dropCursorPos)
        if (cursorPos == null) {
            cursor?.let {
                it.remove()
                cursor = null
            }
        } else {
            if (cursor == null) {
                cursor = document.createElement("div").also {
                    it.className = "cm-dropCursor"
                    view.scrollDOM.appendChild(it)
                }
            }
            if (update.startState.field(dropCursorPos) != cursorPos || 
                update.docChanged || 
                update.geometryChanged) {
                view.requestMeasure(measureReq)
            }
        }
    }

    private fun readPos(): CursorPos? {
        val pos = view.state.field(dropCursorPos) ?: return null
        val rect = view.coordsAtPos(pos) ?: return null
        val outer = view.scrollDOM.getBoundingClientRect()
        
        return CursorPos(
            left = rect.left - outer.left + view.scrollDOM.scrollLeft * view.scaleX,
            top = rect.top - outer.top + view.scrollDOM.scrollTop * view.scaleY,
            height = rect.bottom - rect.top
        )
    }

    private fun drawCursor(pos: CursorPos?) {
        cursor?.let { cursor ->
            if (pos != null) {
                cursor.style.left = "${pos.left / view.scaleX}px"
                cursor.style.top = "${pos.top / view.scaleY}px"
                cursor.style.height = "${pos.height / view.scaleY}px"
            } else {
                cursor.style.left = "-100000px"
            }
        }
    }

    fun destroy() {
        cursor?.remove()
    }

    fun setDropPos(pos: Int?) {
        if (view.state.field(dropCursorPos) != pos) {
            view.dispatch(effects = setDropCursorPos.of(pos))
        }
    }

    val eventHandlers = mapOf(
        "dragover" to { event: DragEvent ->
            setDropPos(view.posAtCoords(event.clientX, event.clientY))
        },
        "dragleave" to { event: DragEvent ->
            if (event.target == view.contentDOM || 
                !view.contentDOM.contains(event.relatedTarget as? Element)) {
                setDropPos(null)
            }
        },
        "dragend" to { _: DragEvent ->
            setDropPos(null)
        },
        "drop" to { _: DragEvent ->
            setDropPos(null)
        }
    )
}

private data class CursorPos(
    val left: Double,
    val top: Double,
    val height: Double
)

/**
 * Creates an extension that draws a cursor at the current drop position when something is
 * dragged over the editor.
 */
fun dropCursor(): Extension {
    return listOf(dropCursorPos, drawDropCursor)
}
