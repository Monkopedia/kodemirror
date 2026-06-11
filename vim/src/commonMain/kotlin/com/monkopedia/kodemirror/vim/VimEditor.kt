/*
 * Copyright 2026 Jason Monk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Originally based on CodeMirror 6 by Marijn Haverbeke, licensed under MIT.
 * See NOTICE file for details.
 */
package com.monkopedia.kodemirror.vim

import com.monkopedia.kodemirror.commands.cursorCharBackward
import com.monkopedia.kodemirror.commands.cursorCharLeft
import com.monkopedia.kodemirror.commands.cursorLineBoundaryBackward
import com.monkopedia.kodemirror.commands.cursorLineBoundaryForward
import com.monkopedia.kodemirror.commands.indentLess
import com.monkopedia.kodemirror.commands.indentMore
import com.monkopedia.kodemirror.commands.insertNewlineAndIndent
import com.monkopedia.kodemirror.commands.redo
import com.monkopedia.kodemirror.commands.undo
import com.monkopedia.kodemirror.language.ensureSyntaxTree
import com.monkopedia.kodemirror.language.indentUnit
import com.monkopedia.kodemirror.language.matchBrackets
import com.monkopedia.kodemirror.language.syntaxTreeAvailable
import com.monkopedia.kodemirror.search.RegExpCursor
import com.monkopedia.kodemirror.search.SearchQuery
import com.monkopedia.kodemirror.search.setSearchQuery
import com.monkopedia.kodemirror.state.ChangeDesc
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.MapMode
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asInsert
import com.monkopedia.kodemirror.state.endPos
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.ViewUpdate
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------
// Event system
// ---------------------------------------------------------------------------

internal class EventHandlers {
    private val handlers: MutableMap<
        String,
        MutableList<
            (
                Array<out Any?>
            ) -> Unit
            >
        > = mutableMapOf()

    fun on(type: String, f: (Array<out Any?>) -> Unit) {
        handlers.getOrPut(type) { mutableListOf() }.add(f)
    }

    fun off(type: String, f: (Array<out Any?>) -> Unit) {
        handlers[type]?.remove(f)
    }

    fun signal(type: String, vararg args: Any?) {
        handlers[type]?.toList()?.forEach { it(args) }
    }

    fun getHandlers(type: String): List<(Array<out Any?>) -> Unit>? = handlers[type]?.toList()
}

// ---------------------------------------------------------------------------
// Word character test
// ---------------------------------------------------------------------------

private val wordCharRegex = Regex("[\\w\\p{L}\\p{N}_]")

internal fun isWordChar(ch: String): Boolean = wordCharRegex.containsMatchIn(ch)

// ---------------------------------------------------------------------------
// Operation tracking
// ---------------------------------------------------------------------------

internal class Operation {
    var depth: Int = 0
    var isVimOp: Boolean = false
    var cursorActivityHandlers: List<(Array<out Any?>) -> Unit>? = null
    var cursorActivity: Boolean = false
    var lastChange: Change? = null
    var change: Change? = null
    var changeHandlers: List<(Array<out Any?>) -> Unit>? = null
    var changeStart: DocPos? = null

    /**
     * Primary selection head captured when the operation began, used to decide
     * whether the operation actually moved the cursor (and therefore should
     * reveal it). Null when the operation was opened lazily (e.g. by a change
     * handler) without a recorded starting position.
     */
    var startSelectionHead: DocPos? = null
}

internal class Change(
    val text: List<String>
) {
    var next: Change? = null
}

// ---------------------------------------------------------------------------
// Bracket matching
// ---------------------------------------------------------------------------

private val BRACKET_MATCHING = mapOf(
    '(' to ")>",
    ')' to "(<",
    '[' to "]>",
    ']' to "[<",
    '{' to "}>",
    '}' to "{<",
    '<' to ">>",
    '>' to "<<"
)

// ---------------------------------------------------------------------------
// Search cursor wrapper
// ---------------------------------------------------------------------------

internal class VimSearchCursor(
    private val cm: VimEditor,
    query: Regex,
    pos: LinePos
) {
    private var last: SearchMatch? = null
    private var lastCM5Result: CM5SearchMatch? = null
    private var afterEmptyMatch = false
    private val firstOffset: DocPos
    private val source: String
    private val queryOptions: Set<RegexOption>

    init {
        val ch = pos.ch
        firstOffset = indexFromPos(cm.session.state.doc, if (ch == Int.MAX_VALUE) pos else pos)

        // Escape unmatched braces for RegExpCursor compatibility.
        // Preserve \Q...\E literal quoting blocks (from Regex.escape()).
        val braceRe =
            Regex("""(\\Q[^\\]*(?:\\(?!E)[^\\]*)*\\E)|(\\.|(\{(?:\d+(?:,\d*)?|,\d+)\}))|([{}])""")
        source = query.pattern.replace(braceRe) { mr ->
            when {
                mr.groupValues[1].isNotEmpty() -> mr.groupValues[1]
                mr.groupValues[2].isNotEmpty() -> mr.groupValues[2]
                else -> "\\" + mr.value
            }
        }
        queryOptions = query.options
    }

    data class SearchMatch(val from: DocPos, val to: DocPos, val match: List<String?>)
    data class CM5SearchMatch(val from: LinePos, val to: LinePos, val match: List<String?>)

    private fun rCursor(
        doc: Text,
        from: DocPos = DocPos.ZERO,
        to: DocPos = doc.endPos
    ): RegExpCursor {
        return RegExpCursor(doc, source, queryOptions, from, to)
    }

    private fun nextMatch(from: DocPos): SearchMatch? {
        val doc = cm.session.state.doc
        if (from.value > doc.length) return null
        val cursor = rCursor(doc, from)
        if (!cursor.hasNext()) return null
        // Capture match groups before calling next(), because next() calls advance()
        // which overwrites matchGroups with the groups for the *following* match.
        val groups = cursor.matchGroups
        val res = cursor.next()
        return SearchMatch(res.from, res.to, groups)
    }

    private val chunkSize = 10000

    private fun prevMatchInRange(from: DocPos, to: DocPos): SearchMatch? {
        val doc = cm.session.state.doc
        var size = 1
        while (true) {
            val start = DocPos(max(from.value, to.value - size * chunkSize))
            val cursor = rCursor(doc, start, to)
            var range: SearchMatch? = null
            // Capture match groups before each call to next(), because next() calls
            // advance() which overwrites matchGroups with groups for the following match.
            while (cursor.hasNext()) {
                val groups = cursor.matchGroups
                val m = cursor.next()
                range = SearchMatch(m.from, m.to, groups)
            }
            val farEnough = start == from ||
                (range != null && range.from.value > start.value + 10)
            if (range != null && farEnough) return range
            if (start == from) return null
            size++
        }
    }

    fun findNext(): List<String?>? = find(false)
    fun findPrevious(): List<String?>? = find(true)

    fun find(back: Boolean = false): List<String?>? {
        val doc = cm.session.state.doc
        if (back) {
            val endAt = if (last != null) {
                if (afterEmptyMatch) DocPos(last!!.to.value - 1) else last!!.from
            } else {
                firstOffset
            }
            last = prevMatchInRange(DocPos.ZERO, endAt)
        } else {
            val startFrom = if (last != null) {
                if (afterEmptyMatch) DocPos(last!!.to.value + 1) else last!!.to
            } else {
                firstOffset
            }
            last = nextMatch(startFrom)
        }
        lastCM5Result = last?.let {
            CM5SearchMatch(
                posFromIndex(doc, it.from),
                posFromIndex(doc, it.to),
                it.match
            )
        }
        afterEmptyMatch = last?.let { it.from == it.to } ?: false
        return last?.match
    }

    fun from(): LinePos? = lastCM5Result?.from
    fun to(): LinePos? = lastCM5Result?.to

    fun replace(text: String) {
        val l = last ?: return
        cm.dispatchChange(
            TransactionSpec(
                changes = ChangeSpec.Single(l.from, l.to, text.asInsert())
            )
        )
        last = last?.copy(to = DocPos(l.from.value + text.length))
        if (lastCM5Result != null) {
            lastCM5Result = lastCM5Result?.copy(
                to = posFromIndex(cm.session.state.doc, last!!.to)
            )
        }
    }

    val match: List<String?>? get() = lastCM5Result?.match
}

// ---------------------------------------------------------------------------
// VimEditor — the CM5-compatible wrapper around EditorSession
// ---------------------------------------------------------------------------

class VimEditor(val session: EditorSession) {

    internal val events = EventHandlers()
    internal var curOp: Operation? = null
    internal val marks: MutableMap<Int, BookmarkMarker> = mutableMapOf()
    internal var markIdCounter = 0
    internal var virtualSelection: EditorSelection? = null
    internal var lastChangeEndOffset: DocPos = DocPos.ZERO
    internal var cm6Query: SearchQuery? = null
    internal var lineHandleChanges: MutableList<ViewUpdate>? = null

    var statusbar: Any? = null
    var dialog: Any? = null
    var vimPlugin: Any? = null
    internal var vim: VimState? = null
    var currentNotificationClose: (() -> Unit)? = null
    var closeVimNotification: (() -> Unit)? = null

    fun indexFromPos(pos: LinePos): DocPos = indexFromPos(session.state.doc, pos)
    fun posFromIndex(offset: DocPos): LinePos = posFromIndex(session.state.doc, offset)

    var openDialogFn: (
        (prefix: String, callback: (String) -> Unit, options: Map<String, Any?>) -> (() -> Unit)
    )? = null
    var openNotificationFn: ((text: String, options: Map<String, Any?>) -> (() -> Unit))? = null
}

internal var isMac: Boolean = false

internal data class BracketMatch(val to: LinePos?)
internal data class ScanResult(val pos: LinePos, val ch: String)
internal data class BookmarkOptions(val insertLeft: Boolean = false)
internal data class LineHandleImpl(val row: Int, val index: DocPos)
internal data class HardWrapOptions(
    val from: Int,
    val to: Int,
    val column: Int? = null,
    val allowMerge: Boolean = true
)

// ---------------------------------------------------------------------------
// Extension functions (extracted from VimEditor methods)
// ---------------------------------------------------------------------------

internal fun VimEditor.on(type: String, f: (Array<out Any?>) -> Unit) = events.on(type, f)
internal fun VimEditor.off(type: String, f: (Array<out Any?>) -> Unit) = events.off(type, f)
internal fun VimEditor.signal(type: String, vararg args: Any?) = events.signal(type, *args)

internal fun VimEditor.findMatchingBracket(pos: LinePos): BracketMatch {
    val state = session.state
    val hasLanguage = syntaxTreeAvailable(state)
    if (hasLanguage) {
        val offset = indexFromPos(state.doc, pos)
        var m = matchBrackets(state, offset + 1, -1)
        if (m?.end != null) {
            return BracketMatch(posFromIndex(state.doc, m.end!!.from))
        }
        m = matchBrackets(state, offset, 1)
        if (m?.end != null) {
            return BracketMatch(posFromIndex(state.doc, m.end!!.from))
        }
        return BracketMatch(null)
    }
    // Fallback: no language configured, use text-based bracket matching
    // that skips brackets inside strings and comments.
    return findMatchingBracketByText(pos)
}

/**
 * Text-based bracket matching that skips brackets inside strings and
 * block comments. Used when no language parser is configured.
 */
private fun VimEditor.findMatchingBracketByText(pos: LinePos): BracketMatch {
    val lineText = getLine(pos.line)
    if (pos.ch >= lineText.length) return BracketMatch(null)
    val bracket = lineText[pos.ch]
    val matchInfo = BRACKET_MATCHING[bracket] ?: return BracketMatch(null)
    val targetCh = matchInfo[0]
    val forward = matchInfo[1] == '>'
    val dir = if (forward) 1 else -1

    val state = session.state
    val startOffset = indexFromPos(state.doc, pos).value
    val doc = state.doc
    var depth = 1
    var scanPos = startOffset + dir
    val maxDist = 10000

    // Track string/comment state by rescanning from the beginning of each line
    while (depth > 0 && scanPos >= 0 && scanPos < doc.length &&
        kotlin.math.abs(scanPos - startOffset) < maxDist
    ) {
        val scanLinePos = posFromIndex(state.doc, DocPos(scanPos))
        val tokenType = getTokenTypeByText(scanLinePos)
        if (tokenType == "" || tokenType == "comment" || tokenType == "string") {
            val c = doc.sliceString(DocPos(scanPos), DocPos(scanPos + 1))
            if (c.length == 1) {
                if (tokenType == "") {
                    if (c[0] == bracket) {
                        depth++
                    } else if (c[0] == targetCh) {
                        depth--
                        if (depth == 0) {
                            return BracketMatch(scanLinePos)
                        }
                    }
                }
            }
        }
        scanPos += dir
    }
    return BracketMatch(null)
}

internal fun VimEditor.scanForBracket(
    where: LinePos,
    dir: Int,
    @Suppress("UNUSED_PARAMETER") style: Any? = null,
    config: Map<String, Any?>? = null
): ScanResult? {
    val maxScanLen = (config?.get("maxScanLineLength") as? Int) ?: 10000
    val maxScanLines = (config?.get("maxScanLines") as? Int) ?: 1000
    val re = (config?.get("bracketRegex") as? Regex) ?: Regex("[(){}\\[\\]]")

    val stack = mutableListOf<String>()
    val lineEnd = if (dir > 0) {
        min(where.line + maxScanLines, lastLine() + 1)
    } else {
        max(firstLine() - 1, where.line - maxScanLines)
    }
    var lineNo = where.line
    while (lineNo != lineEnd) {
        val line = getLine(lineNo)
        if (line.isEmpty()) {
            lineNo += dir
            continue
        }
        if (line.length > maxScanLen) {
            lineNo += dir
            continue
        }

        val end = if (dir > 0) line.length else -1
        var pos = if (dir > 0) 0 else line.length - 1
        if (lineNo == where.line) pos = where.ch - if (dir < 0) 1 else 0

        while (pos != end) {
            val ch = line[pos].toString()
            if (re.containsMatchIn(ch)) {
                val match = BRACKET_MATCHING[ch[0]]
                if (match != null && (match[1] == '>') == (dir > 0)) {
                    stack.add(ch)
                } else if (stack.isEmpty()) {
                    return ScanResult(LinePos(lineNo, pos), ch)
                } else {
                    stack.removeLastOrNull()
                }
            }
            pos += dir
        }
        lineNo += dir
    }
    return null
}

internal fun VimEditor.addOverlay(overlay: SearchOverlay): SearchQuery? {
    val query = overlay.query
    val cm6Query = SearchQuery(
        regexp = true,
        search = query.pattern,
        caseSensitive = !query.options.contains(RegexOption.IGNORE_CASE)
    )
    if (cm6Query.valid) {
        this.cm6Query = cm6Query
        session.dispatch(
            TransactionSpec(effects = listOf(setSearchQuery.of(cm6Query)))
        )
        return cm6Query
    }
    return null
}

internal fun VimEditor.removeOverlay(@Suppress("UNUSED_PARAMETER") overlay: Any? = null) {
    val q = cm6Query ?: return
    session.dispatch(
        TransactionSpec(effects = listOf(setSearchQuery.of(q)))
    )
}

internal fun VimEditor.getSearchCursor(query: Regex, pos: LinePos): VimSearchCursor =
    VimSearchCursor(this, query, pos)

internal fun VimEditor.getLineHandle(row: Int): LineHandleImpl {
    if (lineHandleChanges == null) lineHandleChanges = mutableListOf()
    return LineHandleImpl(row, indexFromPos(LinePos(row, 0)))
}

internal fun VimEditor.getLineNumber(handle: LineHandleImpl): Int? {
    val updates = lineHandleChanges ?: return null
    var offset: DocPos? = handle.index
    for (update in updates) {
        offset = update.changes.mapPos(offset!!, 1, MapMode.TrackAfter)
        if (offset == null) return null
    }
    val pos = posFromIndex(session.state.doc, offset!!)
    return if (pos.ch == 0) pos.line else null
}

internal fun VimEditor.releaseLineHandles() {
    lineHandleChanges = null
}

internal fun VimEditor.hardWrap(options: HardWrapOptions): Int {
    val max = options.column ?: (getOption("textwidth") as? Int) ?: 80
    val allowMerge = options.allowMerge

    var row = min(options.from, options.to)
    val endRow = intArrayOf(max(options.from, options.to))

    while (row <= endRow[0]) {
        val line = getLine(row)
        if (line.length > max) {
            val space = findSpace(line, max, 5)
            if (space != null) {
                val indentation = Regex("^\\s*").find(line)?.value ?: ""
                replaceRange(
                    "\n$indentation",
                    LinePos(row, space.start),
                    LinePos(row, space.end)
                )
            }
            endRow[0]++
        } else if (allowMerge && Regex("\\S").containsMatchIn(line) && row != endRow[0]) {
            val nextLine = getLine(row + 1)
            if (nextLine.isNotEmpty() && Regex("\\S").containsMatchIn(nextLine)) {
                val trimmedLine = line.trimEnd()
                val trimmedNextLine = nextLine.trimStart()
                val mergedLine = "$trimmedLine $trimmedNextLine"

                val space = findSpace(mergedLine, max, 5)
                if ((space != null && space.start > trimmedLine.length) ||
                    mergedLine.length < max
                ) {
                    replaceRange(
                        " ",
                        LinePos(row, trimmedLine.length),
                        LinePos(row + 1, nextLine.length - trimmedNextLine.length)
                    )
                    row--
                    endRow[0]--
                } else if (trimmedLine.length < line.length) {
                    replaceRange(
                        "",
                        LinePos(row, trimmedLine.length),
                        LinePos(row, line.length)
                    )
                }
            }
        }
        row++
    }
    return row
}

private data class SpaceResult(val start: Int, val end: Int)

private fun findSpace(line: String, max: Int, min: Int): SpaceResult? {
    if (line.length < max) return null
    val before = line.substring(0, max)
    val after = line.substring(max)

    val spaceAfter = Regex("^(?:(\\s+)|(\\S+)(\\s+))").find(after)
    val spaceBefore = Regex("(?:(\\s+)|(\\s+)(\\S+))$").find(before)

    var start = 0
    var end = 0

    if (spaceBefore != null && spaceBefore.groupValues[2].isEmpty()) {
        start = max - spaceBefore.groupValues[1].length
        end = max
    }
    if (spaceAfter != null && spaceAfter.groupValues[2].isEmpty()) {
        if (start == 0) start = max
        end = max + spaceAfter.groupValues[1].length
    }
    if (start != 0) return SpaceResult(start, end)

    if (spaceBefore != null && spaceBefore.groupValues[2].isNotEmpty() &&
        spaceBefore.range.first > min
    ) {
        return SpaceResult(
            spaceBefore.range.first,
            spaceBefore.range.first + spaceBefore.groupValues[2].length
        )
    }

    if (spaceAfter != null && spaceAfter.groupValues[2].isNotEmpty()) {
        val s = max + spaceAfter.groupValues[2].length
        return SpaceResult(s, s + spaceAfter.groupValues[3].length)
    }
    return null
}

internal fun VimEditor.onChange(update: ViewUpdate) {
    lineHandleChanges?.add(update)
    for ((_, m) in marks) {
        m.update(update.changes)
    }
    virtualSelection?.let { vs ->
        virtualSelection = EditorSelection.create(
            vs.ranges.map { it.map(update.changes) },
            vs.mainIndex
        )
    }
    val op = curOp ?: Operation().also { curOp = it }
    update.changes.iterChanges(
        f = { _: DocPos, _: DocPos, fromB: DocPos, toB: DocPos, text: Text ->
            if (op.changeStart == null || op.changeStart!! > fromB) {
                op.changeStart = fromB
            }
            lastChangeEndOffset = toB
            val change = Change(text.lineSequence().map { line -> line.text }.toList())
            if (op.lastChange == null) {
                op.change = change
                op.lastChange = change
            } else {
                op.lastChange!!.next = change
                op.lastChange = change
            }
        },
        individual = true
    )
    if (op.changeHandlers == null) {
        op.changeHandlers = events.getHandlers("change")
    }
}

internal fun VimEditor.onSelectionChange() {
    val op = curOp ?: Operation().also { curOp = it }
    if (op.cursorActivityHandlers == null) {
        op.cursorActivityHandlers = events.getHandlers("cursorActivity")
    }
    op.cursorActivity = true
}

internal fun VimEditor.onBeforeEndOperation() {
    val op = curOp ?: return
    if (op.change != null) {
        op.changeHandlers?.forEach { it(arrayOf(this, op.change)) }
    }
    if (op.cursorActivity) {
        op.cursorActivityHandlers?.forEach { it(arrayOf(this, null)) }
    }
    // Reveal the primary selection when a vim operation moved the cursor.
    //
    // In-operation motions dispatch with scrollIntoView = false (because
    // curOp != null in setCursor), so the operation must reveal the cursor
    // itself when it ends. We key this off whether the operation actually
    // moved the primary head — comparing the head captured at the start of
    // the operation against the final head — rather than the cursorActivity
    // flag. cursorActivity is only set when the VimExtension plugin's update()
    // has already run onSelectionChange(); that timing is unreliable for
    // in-operation motions like gg/G (and never happens in headless mode), so
    // relying on it left the cursor offscreen. Gating on real movement also
    // avoids the spurious "jump to top" reveal: an operation that did not move
    // the cursor no longer yanks the viewport to a stale head. The no-argument
    // scrollIntoView() resolves its target to the (final) current selection
    // head, so this always reveals the destination, never offset 0.
    val finalHead = session.state.selection.main.head
    val movedCursor = op.startSelectionHead != null && op.startSelectionHead != finalHead
    val scrollIntoView = op.isVimOp && movedCursor
    curOp = null
    if (scrollIntoView) {
        this.scrollIntoView()
    }
}

internal fun VimEditor.openDialog(
    template: String,
    callback: ((String) -> Unit)?,
    options: Map<String, Any?> = emptyMap()
): () -> Unit {
    return openDialogFn?.invoke(template, callback ?: {}, options) ?: {}
}

internal fun VimEditor.openNotification(
    template: String,
    options: Map<String, Any?> = emptyMap()
): () -> Unit {
    return openNotificationFn?.invoke(template, options) ?: {}
}

internal fun VimEditor.firstLine(): Int = 0
internal fun VimEditor.lastLine(): Int = session.state.doc.lines - 1
internal fun VimEditor.lineCount(): Int = session.state.doc.lines
internal fun VimEditor.defaultTextHeight(): Float = 20f

internal fun VimEditor.focus() {
    // In Compose, focus is managed differently. This is a no-op for now.
}

internal fun VimEditor.getLine(row: Int): String {
    val doc = session.state.doc
    if (row < 0 || row >= doc.lines) return ""
    return doc.line(LineNumber(row + 1)).text
}

internal fun VimEditor.getValue(): String = session.state.doc.toString()

internal fun VimEditor.getRange(s: LinePos, e: LinePos): String {
    val doc = session.state.doc
    val from = indexFromPos(doc, s)
    val to = indexFromPos(doc, e)
    val lo = if (from <= to) from else to
    val hi = if (from <= to) to else from
    return session.state.sliceDoc(lo, hi)
}

internal fun VimEditor.clipPos(p: LinePos): LinePos {
    val doc = session.state.doc
    var ch = p.ch
    var lineNumber = p.line + 1
    if (lineNumber < 1) {
        lineNumber = 1
        ch = 0
    }
    if (lineNumber > doc.lines) {
        lineNumber = doc.lines
        ch = Int.MAX_VALUE
    }
    val line = doc.line(LineNumber(lineNumber))
    ch = min(max(0, ch), line.to.value - line.from.value)
    return LinePos(lineNumber - 1, ch)
}

internal fun VimEditor.getCursor(p: String? = null): LinePos {
    val sel = session.state.selection.main
    val offset = when (p) {
        "head", null -> sel.head
        "anchor" -> sel.anchor
        "start" -> sel.from
        "end" -> sel.to
        else -> error("Invalid cursor type: $p")
    }
    return posFromIndex(session.state.doc, offset)
}

internal fun VimEditor.listSelections(): List<LinePosRange> {
    val doc = session.state.doc
    return session.state.selection.ranges.map { r ->
        LinePosRange(
            anchor = posFromIndex(doc, r.anchor),
            head = posFromIndex(doc, r.head)
        )
    }
}

internal fun VimEditor.getSelection(): String = getSelections().joinToString("\n")

internal fun VimEditor.getSelections(): List<String> {
    return session.state.selection.ranges.map { r ->
        session.state.sliceDoc(r.from, r.to)
    }
}

internal fun VimEditor.somethingSelected(): Boolean = session.state.selection.ranges.any {
    !it.empty
}

internal fun VimEditor.getLastEditEnd(): LinePos = posFromIndex(lastChangeEndOffset)

internal fun VimEditor.dispatchChange(spec: TransactionSpec) {
    if (session.state.readOnly) return
    session.dispatch(spec)
}

/**
 * Lightweight change tracking for headless / test environments.
 * Updates line handles, marks, virtual selections, and lastChangeEndOffset.
 * Does NOT touch [curOp] or fire change/cursorActivity handlers.
 */
internal fun VimEditor.onChangeLight(update: ViewUpdate) {
    lineHandleChanges?.add(update)
    for ((_, m) in marks) {
        m.update(update.changes)
    }
    virtualSelection?.let { vs ->
        virtualSelection = EditorSelection.create(
            vs.ranges.map { it.map(update.changes) },
            vs.mainIndex
        )
    }
    update.changes.iterChanges(
        f = { _: DocPos, _: DocPos, _: DocPos, toB: DocPos, _: Text ->
            lastChangeEndOffset = toB
        },
        individual = true
    )
}

internal fun VimEditor.replaceRange(text: String, s: LinePos, e: LinePos? = null) {
    val end = e ?: s
    val doc = session.state.doc
    val from = indexFromPos(doc, s)
    val to = indexFromPos(doc, end)
    val lo = if (from <= to) from else to
    val hi = if (from <= to) to else from
    dispatchChange(
        TransactionSpec(changes = ChangeSpec.Single(lo, hi, text.asInsert()))
    )
}

internal fun VimEditor.replaceSelection(text: String) {
    dispatchChange(session.state.replaceSelection(text))
}

internal fun VimEditor.replaceSelections(replacements: List<String>) {
    val ranges = session.state.selection.ranges
    val changes = ranges.mapIndexed { i, r ->
        ChangeSpec.Single(r.from, r.to, (replacements.getOrElse(i) { "" }).asInsert())
    }
    dispatchChange(
        TransactionSpec(changes = ChangeSpec.Multi(changes))
    )
}

internal fun VimEditor.setCursor(line: Int, ch: Int = 0) {
    val offset = indexFromPos(session.state.doc, LinePos(line, ch))
    session.dispatch(
        TransactionSpec(
            selection = SelectionSpec.CursorSpec(offset),
            scrollIntoView = curOp == null
        )
    )
    if (curOp != null && curOp?.isVimOp != true) {
        onBeforeEndOperation()
    } else if (curOp == null && vimPlugin == null) {
        // In headless/test environments without the VimExtension plugin,
        // selection changes from external setCursor calls won't trigger
        // the plugin's update() handler.  Fire selectionChange manually
        // so handleExternalSelection can synchronise vim state.
        onSelectionChange()
        onBeforeEndOperation()
    }
}

internal fun VimEditor.setCursor(pos: LinePos) = setCursor(pos.line, pos.ch)

internal fun VimEditor.setSelections(
    selections: List<LinePosRange>,
    primIndex: Int? = null,
    addToHistory: Boolean = true
) {
    val doc = session.state.doc
    val ranges = selections.map { x ->
        val head = indexFromPos(doc, x.head)
        val anchor = indexFromPos(doc, x.anchor)
        if (head == anchor) {
            EditorSelection.cursor(head, 1)
        } else {
            EditorSelection.range(anchor, head)
        }
    }
    session.dispatch(
        TransactionSpec(
            selection = SelectionSpec.EditorSelectionSpec(
                EditorSelection.create(ranges, primIndex ?: 0)
            ),
            annotations = if (addToHistory) {
                emptyList()
            } else {
                listOf(Transaction.addToHistory.of(false))
            }
        )
    )
}

internal fun VimEditor.setSelection(
    anchor: LinePos,
    head: LinePos,
    options: Map<String, Any?>? = null
) {
    setSelections(listOf(LinePosRange(anchor, head)), 0)
    if (options?.get("origin") == "*mouse") {
        onBeforeEndOperation()
    }
}

internal fun VimEditor.scrollIntoView(
    pos: LinePos? = null,
    @Suppress("UNUSED_PARAMETER") margin: Int? = null
) {
    if (pos != null) {
        val offset = indexFromPos(pos)
        session.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(offset),
                scrollIntoView = true
            )
        )
    } else {
        session.dispatch(TransactionSpec(scrollIntoView = true))
    }
}

internal fun VimEditor.overWriteSelection(text: String) {
    val doc = session.state.doc
    val sel = session.state.selection
    val ranges = sel.ranges.map { x ->
        if (x.empty) {
            val ch = if (x.to < doc.endPos) doc.sliceString(x.from, x.to + 1) else ""
            if (ch.isNotEmpty() && !ch.contains('\n')) {
                EditorSelection.range(x.from, x.to + 1)
            } else {
                x
            }
        } else {
            x
        }
    }
    session.dispatch(
        TransactionSpec(
            selection = SelectionSpec.EditorSelectionSpec(
                EditorSelection.create(ranges, sel.mainIndex)
            )
        )
    )
    replaceSelection(text)
}

internal fun <T> VimEditor.operation(fn: () -> T): T {
    if (curOp == null) {
        curOp = Operation().also {
            it.startSelectionHead = session.state.selection.main.head
        }
    }
    curOp!!.depth++
    try {
        return fn()
    } finally {
        curOp?.let {
            it.depth--
            if (it.depth == 0) {
                onBeforeEndOperation()
            }
        }
    }
}

internal fun VimEditor.setOption(name: String, value: Any?) {
    when (name) {
        "keyMap" -> vim?.keyMap = value as? String
        "textwidth" -> vim?.textwidth = value as? Int
    }
}

internal fun VimEditor.getOption(name: String): Any? = when (name) {
    "firstLineNumber" -> 1
    "tabSize" -> session.state.tabSize
    "readOnly" -> session.state.readOnly
    "indentWithTabs" -> false
    "indentUnit" -> session.state.facet(indentUnit).coerceAtLeast(2)
    "textwidth" -> vim?.textwidth
    "keyMap" -> vim?.keyMap ?: "vim"
    else -> null
}

internal fun VimEditor.toggleOverwrite(on: Boolean) {
    vim!!.overwrite = on
}

internal fun VimEditor.execCommand(name: String) {
    when (name) {
        "cursorCharLeft" -> cursorCharLeft(session)
        "redo" -> runHistoryCommand(false)
        "undo" -> runHistoryCommand(true)
        "newlineAndIndent" -> insertNewlineAndIndent(session)
        "indentAuto" -> indentMore(session)
        "goLineLeft" -> cursorLineBoundaryBackward(session)
        "goLineRight" -> {
            cursorLineBoundaryForward(session)
            val st = session.state
            val cur = st.selection.main.head
            if (cur < st.doc.endPos && st.sliceDoc(cur, cur + 1) != "\n") {
                cursorCharBackward(session)
            }
        }
    }
}

private fun VimEditor.runHistoryCommand(revert: Boolean) {
    if (curOp != null) {
        curOp!!.changeStart = null
    }
    if (revert) undo(session) else redo(session)
    val changeStartIndex = curOp?.changeStart
    if (changeStartIndex != null) {
        session.dispatch(
            TransactionSpec(selection = SelectionSpec.CursorSpec(changeStartIndex))
        )
    }
}

internal fun VimEditor.indentLine(line: Int, more: Boolean = false) {
    if (more) indentMore(session) else indentLess(session)
}

internal fun VimEditor.indentMore() = indentMore(session)
internal fun VimEditor.indentLess() = indentLess(session)

internal fun VimEditor.setBookmark(cursor: LinePos, options: BookmarkOptions? = null): Marker {
    val assoc = if (options?.insertLeft == true) 1 else -1
    val offset = indexFromPos(cursor)
    val bm = BookmarkMarker(this, offset, assoc)
    bm.id = markIdCounter++
    marks[bm.id] = bm
    return bm
}

internal fun VimEditor.getTokenTypeAt(pos: LinePos): String {
    val offset = indexFromPos(pos)
    val tree = ensureSyntaxTree(session.state, offset.value)
    val node = tree?.resolve(offset.value)
    val type = node?.type?.name ?: ""
    return when {
        type.contains("comment", ignoreCase = true) -> "comment"
        type.contains("string", ignoreCase = true) -> "string"
        type.isNotEmpty() -> ""
        // Fallback: no syntax tree available, use text-based heuristic
        else -> getTokenTypeByText(pos)
    }
}

/**
 * Text-based heuristic for detecting whether a position is inside a string
 * or block comment. Used as a fallback when no language parser is configured.
 *
 * Scans the line text up to [pos] to detect unclosed double-quote strings
 * and `/ * ... * /` block comments.
 */
private fun VimEditor.getTokenTypeByText(pos: LinePos): String {
    val lineText = getLine(pos.line)
    val ch = pos.ch.coerceAtMost(lineText.length)
    var inString = false
    var inComment = false
    var i = 0
    while (i < ch) {
        if (inComment) {
            if (i + 1 < lineText.length && lineText[i] == '*' && lineText[i + 1] == '/') {
                inComment = false
                i += 2
                continue
            }
        } else if (inString) {
            if (lineText[i] == '\\') {
                i += 2 // skip escaped character
                continue
            }
            if (lineText[i] == '"') {
                inString = false
            }
        } else {
            if (lineText[i] == '"') {
                inString = true
            } else if (
                i + 1 < lineText.length && lineText[i] == '/' && lineText[i + 1] == '*'
            ) {
                inComment = true
                i += 2
                continue
            }
        }
        i++
    }
    return when {
        inComment -> "comment"
        inString -> "string"
        else -> ""
    }
}

// ---------------------------------------------------------------------------
// BookmarkMarker: tracks a position through document changes
// ---------------------------------------------------------------------------

internal class BookmarkMarker(
    private val cm: VimEditor,
    private var offset: DocPos?,
    private val assoc: Int
) : Marker {
    internal var id: Int = 0

    override fun find(): LinePos? {
        val off = offset ?: return null
        return cm.posFromIndex(off)
    }

    override fun clear() {
        cm.marks.remove(id)
    }

    internal fun update(changes: ChangeDesc) {
        offset?.let {
            offset = changes.mapPos(it, assoc, MapMode.TrackDel)
        }
    }
}
