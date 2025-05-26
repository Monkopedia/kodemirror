package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.dom.*
import org.w3c.dom.*

/**
 * Placeholder character used for line breaks in the text.
 */
const val LineBreakPlaceholder = '\uffff'

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
 * Reads content from the DOM, handling text nodes, line breaks, and content views.
 */
class DOMReader(
    private val points: List<DOMPoint>,
    state: EditorState
) {
    private var text: String = ""
    private val lineSeparator: String? = state.facet(EditorState.lineSeparator)

    /**
     * Append text to the accumulated content.
     */
    fun append(text: String) {
        this.text += text
    }

    /**
     * Add a line break to the accumulated content.
     */
    fun lineBreak() {
        text += LineBreakPlaceholder
    }

    /**
     * Read a range of DOM nodes.
     */
    fun readRange(start: Node?, end: Node?): DOMReader {
        if (start == null) return this
        val parent = start.parentNode!!

        var cur: Node = start
        while (true) {
            findPointBefore(parent, cur)
            val oldLen = text.length
            readNode(cur)
            val next = cur.nextSibling
            if (next == end) break

            val view = ContentView.get(cur)
            val nextView = if (next != null) ContentView.get(next) else null

            if (view != null && nextView != null) {
                if (view.breakAfter) lineBreak()
            } else if (view?.breakAfter == true || 
                      (isBlockElement(cur) && (cur.nodeName != "BR" || (cur as Element).hasAttribute("cmIgnore"))) && 
                      text.length > oldLen) {
                lineBreak()
            }

            if (next == null) break
            cur = next
        }
        findPointBefore(parent, end)
        return this
    }

    /**
     * Read a text node.
     */
    private fun readTextNode(node: Text) {
        val text = node.nodeValue!!
        
        // Update point positions for this text node
        points.filter { it.node == node }
            .forEach { it.pos = this.text.length + minOf(it.offset, text.length) }

        var off = 0
        while (true) {
            val nextBreak = if (lineSeparator != null) {
                text.indexOf(lineSeparator, off)
            } else {
                text.indexOfAny(charArrayOf('\r', '\n'), off)
            }
            val breakSize = if (lineSeparator != null) {
                lineSeparator.length
            } else if (nextBreak >= 0 && text[nextBreak] == '\r' && nextBreak + 1 < text.length && text[nextBreak + 1] == '\n') {
                2
            } else {
                1
            }

            append(text.substring(off, if (nextBreak < 0) text.length else nextBreak))
            if (nextBreak < 0) break
            lineBreak()
            if (breakSize > 1) {
                points.filter { it.node == node && it.pos > this.text.length }
                    .forEach { it.pos -= breakSize - 1 }
            }
            off = nextBreak + breakSize
        }
    }

    /**
     * Read a single DOM node.
     */
    private fun readNode(node: Node) {
        if ((node as? Element)?.hasAttribute("cmIgnore") == true) return
        
        val view = ContentView.get(node)
        val fromView = view?.overrideDOMText
        
        if (fromView != null) {
            findPointInside(node, fromView.length)
            for (i in fromView.iter()) {
                if (i.lineBreak) lineBreak()
                else append(i.value)
            }
        } else when (node.nodeType) {
            Node.TEXT_NODE -> readTextNode(node as Text)
            Node.ELEMENT_NODE -> {
                if (node.nodeName == "BR") {
                    if (node.nextSibling != null) lineBreak()
                } else {
                    readRange(node.firstChild, null)
                }
            }
        }
    }

    /**
     * Find points before a node.
     */
    private fun findPointBefore(node: Node, next: Node?) {
        points.filter { it.node == node && node.childNodes[it.offset] == next }
            .forEach { it.pos = text.length }
    }

    /**
     * Find points inside a node.
     */
    private fun findPointInside(node: Node, length: Int) {
        points.filter { 
            if (node.nodeType == Node.TEXT_NODE) {
                it.node == node
            } else {
                node.contains(it.node)
            }
        }.forEach { 
            it.pos = text.length + if (isAtEnd(node, it.node, it.offset)) length else 0 
        }
    }

    companion object {
        /**
         * Check if a point is at the end of a node.
         */
        private fun isAtEnd(parent: Node, node: Node?, offset: Int): Boolean {
            var current = node
            var currentOffset = offset
            
            while (true) {
                if (current == null || currentOffset < maxOffset(current)) return false
                if (current == parent) return true
                currentOffset = domIndex(current) + 1
                current = current.parentNode
            }
        }
    }
}
