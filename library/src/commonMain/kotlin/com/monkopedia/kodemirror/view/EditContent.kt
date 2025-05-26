//package com.monkopedia.kodemirror.view
//
//import com.monkopedia.kodemirror.state.*
//import com.monkopedia.kodemirror.extension.*
//import com.monkopedia.kodemirror.decoration.*
//import com.monkopedia.kodemirror.dom.*
//import kotlinx.browser.document
//import kotlinx.browser.window
//
///**
// * Handles content editing and changes in the editor.
// * This class is responsible for managing the editing state and applying changes
// * to the editor content.
// */
//class EditContent(private val view: EditorView) {
//    private var lastKeyCode: Int = 0
//    private var lastKeyTime: Long = 0
//    private var composing: Int = -1
//    private var compositionFirstChange: Boolean = true
//    private var compositionNodes: MutableSet<Node> = mutableSetOf()
//    private var compositionEndedAt: Long = 0
//
//    /**
//     * Called when the editor receives a keydown event.
//     */
//    fun handleKeyDown(event: KeyboardEvent): Boolean {
//        lastKeyCode = event.keyCode
//        lastKeyTime = System.currentTimeMillis()
//        return false
//    }
//
//    /**
//     * Called when the editor receives a keyup event.
//     */
//    fun handleKeyUp(event: KeyboardEvent): Boolean {
//        if (event.keyCode == lastKeyCode) lastKeyCode = 0
//        return false
//    }
//
//    /**
//     * Called when a composition starts.
//     */
//    fun compositionStart(): Unit {
//        composing = 0
//        compositionFirstChange = true
//        compositionNodes.clear()
//    }
//
//    /**
//     * Called when a composition is updated.
//     */
//    fun compositionUpdate(event: CompositionEvent): Unit {
//        if (composing < 0) compositionStart()
//        composing++
//    }
//
//    /**
//     * Called when a composition ends.
//     */
//    fun compositionEnd(event: CompositionEvent): Unit {
//        if (composing < 0) return
//        composing = -1
//        compositionEndedAt = System.currentTimeMillis()
//        compositionNodes.clear()
//        scheduleRecomposition()
//    }
//
//    /**
//     * Schedule a recomposition of the editor content.
//     */
//    private fun scheduleRecomposition() {
//        window.setTimeout({
//            if (composing >= 0) return@setTimeout
//            view.observer.flush()
//            if (view.docView.hasComposition != null) {
//                view.dispatch(view.state.tr)
//            }
//        }, 50)
//    }
//
//    /**
//     * Track composition nodes and update composition state.
//     */
//    fun trackComposition(node: Node) {
//        if (composing >= 0) compositionNodes.add(node)
//    }
//
//    /**
//     * Check if a node is part of the current composition.
//     */
//    fun isComposing(node: Node): Boolean {
//        return composing >= 0 && compositionNodes.contains(node)
//    }
//
//    /**
//     * Apply a DOM change to the editor content with composition handling.
//     */
//    private fun applyDOMChangeInner(view: EditorView, change: DOMChange, selection: EditorSelection?) {
//        if (view.state.readOnly) return
//
//        // Handle composition
//        val composition = if (composing >= 0) findCompositionNode(view, change.bounds?.from ?: 0) else null
//        if (composition != null) {
//            trackComposition(composition.node)
//            if (compositionFirstChange) {
//                compositionFirstChange = false
//                // Blank the composition before applying new changes
//                val tr = view.state.tr
//                tr.replace(composition.from, composition.to, view.state.toText(""))
//                view.dispatch(tr)
//            }
//        }
//
//        // Build and apply the transaction
//        val tr = view.state.tr
//        if (change.bounds != null) {
//            val (from, to) = change.bounds
//            tr.replace(from, to, view.state.toText(change.text))
//        }
//        if (selection != null) {
//            tr.setSelection(selection)
//        }
//        if (tr.docChanged || tr.selectionSet) {
//            view.dispatch(tr)
//        }
//    }
//
//    /**
//     * Find the composition node at a given position.
//     */
//    private fun findCompositionNode(view: EditorView, pos: Int): CompositionNode? {
//        val sel = view.observer.selectionRange
//        if (sel.focusNode == null) return null
//
//        val textBefore = textNodeBefore(sel.focusNode, sel.focusOffset)
//        val textAfter = textNodeAfter(sel.focusNode, sel.focusOffset)
//        var textNode = textBefore ?: textAfter
//
//        if (textAfter != null && textBefore != null && textAfter.node != textBefore.node) {
//            val descAfter = ContentView.get(textAfter.node)
//            if (descAfter == null || (descAfter is TextView && descAfter.text != textAfter.node.nodeValue)) {
//                textNode = textAfter
//            } else if (view.docView.lastCompositionAfterCursor) {
//                val descBefore = ContentView.get(textBefore.node)
//                if (descBefore == null || (descBefore is TextView && descBefore.text != textBefore.node.nodeValue)) {
//                    textNode = textAfter
//                }
//            }
//        }
//
//        if (textNode == null) return null
//        val from = pos - textNode.offset
//        return CompositionNode(
//            from = from,
//            to = from + (textNode.node.nodeValue?.length ?: 0),
//            node = textNode.node
//        )
//    }
//
//    /**
//     * Get the text node before a given position.
//     */
//    private fun textNodeBefore(node: Node, offset: Int): TextNodeInfo? {
//        if (node.nodeType == Node.TEXT_NODE) {
//            return TextNodeInfo(node as Text, offset)
//        }
//        if (offset > 0) {
//            val before = node.childNodes[offset - 1]
//            if (before.nodeType == Node.TEXT_NODE) {
//                return TextNodeInfo(before as Text, before.nodeValue?.length ?: 0)
//            }
//        }
//        return null
//    }
//
//    /**
//     * Get the text node after a given position.
//     */
//    private fun textNodeAfter(node: Node, offset: Int): TextNodeInfo? {
//        if (node.nodeType == Node.TEXT_NODE) {
//            return TextNodeInfo(node as Text, offset)
//        }
//        if (offset < node.childNodes.length) {
//            val after = node.childNodes[offset]
//            if (after.nodeType == Node.TEXT_NODE) {
//                return TextNodeInfo(after as Text, 0)
//            }
//        }
//        return null
//    }
//
//    /**
//     * Information about a text node's position.
//     */
//    private data class TextNodeInfo(
//        val node: Text,
//        val offset: Int
//    )
//
//    /**
//     * Information about a composition node.
//     */
//    private data class CompositionNode(
//        val from: Int,
//        val to: Int,
//        val node: Text
//    )
//
//    /**
//     * Apply a DOM change to the editor content.
//     */
//    fun applyDOMChange(change: DOMChange): Boolean {
//        val transaction = buildTransaction(change) ?: return false
//        view.dispatch(transaction)
//        return true
//    }
//
//    /**
//     * Build a transaction from a DOM change.
//     */
//    private fun buildTransaction(change: DOMChange): Transaction? {
//        val sel = view.state.selection.main
//        val tr = view.state.tr
//
//        if (change.bounds != null) {
//            val (from, to) = change.bounds
//            val preferredPos = if (lastKeyCode == 8 || change.text.length < to - from) sel.to else sel.from
//            val preferredSide = if (lastKeyCode == 8) "end" else null
//
//            val diff = findDiff(
//                view.state.doc.sliceString(from, to),
//                change.text,
//                preferredPos - from,
//                preferredSide
//            )
//
//            if (diff != null) {
//                tr.replace(
//                    from + diff.from,
//                    from + diff.toA,
//                    view.state.toText(change.text.substring(diff.from, diff.toB))
//                )
//            }
//        }
//
//        if (change.newSel != null) {
//            tr.setSelection(change.newSel)
//        }
//
//        return if (tr.docChanged || tr.selectionSet) tr else null
//    }
//
//    /**
//     * Handle input events from the editor.
//     */
//    fun handleInput(event: InputEvent): Boolean {
//        // Handle spell-check corrections in EditContext mode
//        if (event.inputType == "insertReplacementText" && view.observer.editContext != null) {
//            val text = event.dataTransfer?.getData("text/plain")
//            val ranges = event.getTargetRanges()
//            if (text != null && ranges.isNotEmpty()) {
//                val r = ranges[0]
//                val from = view.posAtDOM(r.startContainer, r.startOffset)
//                val to = view.posAtDOM(r.endContainer, r.endOffset)
//                applyDOMChangeInner(view, DOMChange(from, to, view.state.toText(text)), null)
//                return true
//            }
//        }
//
//        // Handle Android backspace key
//        if (browser.chrome && browser.android) {
//            val pending = PendingKeys.find { it.inputType == event.inputType }
//            if (pending != null) {
//                view.observer.delayAndroidKey(pending.key, pending.keyCode)
//                if (pending.key == "Backspace" || pending.key == "Delete") {
//                    val startViewHeight = window.visualViewport?.height ?: 0
//                    window.setTimeout({
//                        // Backspacing near uneditable nodes on Chrome Android sometimes
//                        // closes the virtual keyboard. This tries to detect that and refocus.
//                        if ((window.visualViewport?.height ?: 0) > startViewHeight + 10 && view.hasFocus) {
//                            view.contentDOM.blur()
//                            view.focus()
//                        }
//                    }, 100)
//                }
//            }
//        }
//        return false
//    }
//
//    /**
//     * Handle beforeinput events from the editor.
//     */
//    fun handleBeforeInput(event: InputEvent): Boolean {
//        if (event.inputType == "historyUndo") {
//            view.dispatch(undo(view.state))
//            return true
//        }
//        if (event.inputType == "historyRedo") {
//            view.dispatch(redo(view.state))
//            return true
//        }
//        return false
//    }
//
//    /**
//     * Handle paste events from the editor.
//     */
//    fun handlePaste(event: ClipboardEvent): Boolean {
//        val text = event.clipboardData?.getData("text/plain")
//        if (text != null) {
//            view.dispatch(
//                view.state.tr.replaceSelection(view.state.toText(text))
//            )
//            return true
//        }
//        return false
//    }
//
//    /**
//     * Handle cut events from the editor.
//     */
//    fun handleCut(event: ClipboardEvent): Boolean {
//        val sel = view.state.selection.main
//        if (!sel.empty) {
//            event.clipboardData?.setData(
//                "text/plain",
//                view.state.sliceDoc(sel.from, sel.to)
//            )
//            view.dispatch(
//                view.state.tr.deleteSelection()
//            )
//            return true
//        }
//        return false
//    }
//
//    /**
//     * Handle copy events from the editor.
//     */
//    fun handleCopy(event: ClipboardEvent): Boolean {
//        val sel = view.state.selection.main
//        if (!sel.empty) {
//            event.clipboardData?.setData(
//                "text/plain",
//                view.state.sliceDoc(sel.from, sel.to)
//            )
//            return true
//        }
//        return false
//    }
//
//    /**
//     * Handle drop events from the editor.
//     */
//    fun handleDrop(event: DragEvent): Boolean {
//        val text = event.dataTransfer?.getData("text/plain")
//        if (text != null) {
//            val pos = view.posAtCoords(event.clientX, event.clientY)
//            if (pos != null) {
//                view.dispatch(
//                    view.state.tr.replaceRange(pos, pos, view.state.toText(text))
//                )
//                return true
//            }
//        }
//        return false
//    }
//
//    /**
//     * Handle drag events from the editor.
//     */
//    fun handleDragStart(event: DragEvent): Boolean {
//        val sel = view.state.selection.main
//        if (!sel.empty) {
//            event.dataTransfer?.setData(
//                "text/plain",
//                view.state.sliceDoc(sel.from, sel.to)
//            )
//            return true
//        }
//        return false
//    }
//
//    companion object {
//        private fun findDiff(
//            a: String,
//            b: String,
//            preferredPos: Int = -1,
//            preferredSide: String? = null
//        ): Diff? {
//            if (a == b) return null
//            let start = 0, aEnd = a.length, bEnd = b.length
//            while (start < aEnd && start < bEnd && a[start] == b[start]) start++
//            while (aEnd > start && bEnd > start && a[aEnd - 1] == b[bEnd - 1]) { aEnd--; bEnd-- }
//            if (preferredSide == "end") {
//                val adjust = Math.max(0, start - Math.max(0, a.length - preferredPos))
//                start -= adjust
//                aEnd = start
//                bEnd = start
//            } else if (preferredPos >= 0) {
//                val adjust = Math.max(0, start - preferredPos)
//                start -= adjust
//                aEnd = start
//                bEnd = start
//            }
//            return Diff(start, aEnd, bEnd)
//        }
//
//        private data class PendingKey(
//            val key: String,
//            val keyCode: Int,
//            val inputType: String
//        )
//
//        private val PendingKeys = listOf(
//            PendingKey("Backspace", 8, "deleteContentBackward"),
//            PendingKey("Enter", 13, "insertParagraph"),
//            PendingKey("Delete", 46, "deleteContentForward")
//        )
//    }
//}
//
///**
// * Represents a difference between two strings.
// */
//data class Diff(
//    val from: Int,
//    val toA: Int,
//    val toB: Int
//)
