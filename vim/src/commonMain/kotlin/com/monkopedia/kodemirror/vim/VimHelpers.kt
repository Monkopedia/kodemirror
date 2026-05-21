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

import com.monkopedia.kodemirror.state.DocPos
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

internal val validMarks = listOf("<", ">")
internal val validRegisters = mutableListOf("-", "\"", ".", ":", "_", "/", "+")
internal val latinCharRegex = Regex("^\\w$")
internal val upperCaseChars: Regex = try {
    Regex("^[\\p{Lu}]$")
} catch (_: Throwable) {
    Regex("^[A-Z]$")
}

internal val numberRegex = Regex("[\\d]")

internal val wordCharTest: List<(String) -> Boolean> = listOf(
    { ch -> isWordChar(ch) },
    { ch -> ch.isNotEmpty() && !isWordChar(ch) && !Regex("\\s").containsMatchIn(ch) }
)

internal val bigWordCharTest: List<(String) -> Boolean> = listOf(
    { ch -> Regex("\\S").containsMatchIn(ch) }
)

// ---------------------------------------------------------------------------
// Options system
// ---------------------------------------------------------------------------

internal val vimOptions: MutableMap<String, VimOption> = mutableMapOf()

internal fun defineOption(
    name: String,
    defaultValue: Any?,
    type: String? = null,
    aliases: List<String>? = null,
    callback: ((Any?, VimEditor?) -> Any?)? = null
) {
    if (defaultValue == null && callback == null) {
        error("defaultValue is required unless callback is provided")
    }
    val optType = type ?: "string"
    val option = VimOption(
        type = optType,
        defaultValue = defaultValue,
        callback = callback
    )
    vimOptions[name] = option
    aliases?.forEach { alias ->
        vimOptions[alias] = option
    }
    if (defaultValue != null) {
        setOption(name, defaultValue)
    }
}

internal fun setOption(
    name: String,
    value: Any?,
    cm: VimEditor? = null,
    cfg: Map<String, String>? = null
): Any? {
    val option = vimOptions[name] ?: return Error("Unknown option: $name")
    val scope = cfg?.get("scope")
    var effectiveValue = value
    if (option.type == "boolean") {
        if (effectiveValue != null && effectiveValue != true && effectiveValue != false) {
            return Error("Invalid argument: $name=$effectiveValue")
        } else if (effectiveValue != false) {
            effectiveValue = true
        }
    }
    val cb = option.callback
    if (cb != null) {
        if (scope != "local") {
            cb.invoke(effectiveValue, null)
        }
        if (scope != "global" && cm != null) {
            cb.invoke(effectiveValue, cm)
        }
    } else {
        if (scope != "local") {
            option.value = if (option.type == "boolean") {
                effectiveValue == true
            } else {
                effectiveValue
            }
        }
        if (scope != "global" && cm != null) {
            cm.vim?.options?.set(name, VimOption(value = effectiveValue))
        }
    }
    return null
}

internal fun getOption(
    name: String,
    cm: VimEditor? = null,
    cfg: Map<String, String>? = null
): Any? {
    val option = vimOptions[name] ?: return Error("Unknown option: $name")
    val scope = cfg?.get("scope")
    val getCb = option.callback
    if (getCb != null) {
        val local = if (cm != null) getCb.invoke(null, cm) else null
        if (scope != "global" && local != null) {
            return local
        }
        if (scope != "local") {
            return getCb.invoke(null, null)
        }
        return null
    } else {
        val local = if (scope != "global") {
            cm?.vim?.options?.get(name)
        } else {
            null
        }
        return local?.value ?: if (scope != "local") option.value else null
    }
}

// ---------------------------------------------------------------------------
// Simple utility functions
// ---------------------------------------------------------------------------

internal fun isLine(cm: VimEditor, line: Int): Boolean = line in cm.firstLine()..cm.lastLine()

internal fun isLowerCase(k: String): Boolean = Regex("^[a-z]$").matches(k)

internal fun isUpperCase(k: String): Boolean = upperCaseChars.matches(k)

internal fun isNumber(k: String): Boolean = numberRegex.containsMatchIn(k)

internal fun isMatchableSymbol(k: String): Boolean = "()[]{}".contains(k)

internal fun isWhiteSpaceString(k: String): Boolean = Regex("^\\s*$").matches(k)

internal fun isEndOfSentenceSymbol(k: String): Boolean = ".?!".contains(k)

internal fun <T> inArray(value: T, arr: List<T>): Boolean = value in arr

internal fun lineLength(cm: VimEditor, lineNum: Int): Int = cm.getLine(lineNum).length

internal fun trim(s: String): String = s.trim()

internal fun escapeRegex(s: String): String =
    s.replace(Regex("([.?*+\$\\[\\]/\\\\(){}|\\-])"), "\\\\$1")

internal fun copyCursor(cur: LinePos): LinePos = LinePos(cur.line, cur.ch)

internal fun cursorIsBetween(cur1: LinePos, cur2: LinePos, cur3: LinePos): Boolean {
    val cur1before2 = cursorIsBefore(cur1, cur2)
    val cur2before3 = cursorIsBefore(cur2, cur3)
    return cur1before2 && cur2before3
}

internal fun offsetCursor(cur: LinePos, offsetLine: Int, offsetCh: Int): LinePos {
    // Use Long arithmetic to avoid integer overflow when ch is Int.MAX_VALUE
    val newCh = cur.ch.toLong() + offsetCh.toLong()
    return LinePos(cur.line + offsetLine, newCh.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
}

internal fun repeatFn(cm: VimEditor, fn: (VimEditor) -> Unit, repeat: Int): () -> Unit = {
    for (i in 0 until repeat) {
        fn(cm)
    }
}

// ---------------------------------------------------------------------------
// Clipboard cursor content
// ---------------------------------------------------------------------------

/**
 * Clips cursor to ensure that line is within the buffer's range
 * and is not inside surrogate pair.
 * If in insert/visual mode, allow cur.ch == lineLength.
 */
internal fun clipCursorToContent(cm: VimEditor, cur: LinePos, oldCur: LinePos? = null): LinePos {
    val vim = cm.vim
    val includeLineBreak = vim != null && (vim.insertMode || vim.visualMode)
    val line = min(max(cm.firstLine(), cur.line), cm.lastLine())
    val text = cm.getLine(line)
    val maxCh = max(0, text.length - 1 + if (includeLineBreak) 1 else 0)
    var ch = min(max(0, cur.ch), maxCh)
    // prevent cursor from entering surrogate pair
    if (ch < text.length) {
        val charCode = text[ch].code
        if (charCode in 0xDC00..0xDFFF) {
            val direction = if (oldCur != null && oldCur.line == line && oldCur.ch > ch) -1 else 1
            ch += direction
            if (ch > maxCh) ch -= 2
        }
    }
    return LinePos(line, ch)
}

/**
 * Updates selection for surrogate characters when start and end are
 * on the same line and close together.
 */
internal fun updateSelectionForSurrogateCharacters(
    cm: VimEditor,
    curStart: LinePos,
    curEnd: LinePos
): SurrogateResult {
    if (curStart.line == curEnd.line && curStart.ch >= curEnd.ch - 1) {
        val text = cm.getLine(curStart.line)
        if (curStart.ch < text.length) {
            val charCode = text[curStart.ch].code
            if (charCode in 0xD800..0xD8FF) {
                return SurrogateResult(curStart, LinePos(curEnd.line, curEnd.ch + 1))
            }
        }
    }
    return SurrogateResult(curStart, curEnd)
}

internal data class SurrogateResult(val start: LinePos, val end: LinePos)

// ---------------------------------------------------------------------------
// Copy args
// ---------------------------------------------------------------------------

internal fun copyArgs(args: ActionArgs): ActionArgs = args.copy()
internal fun copyArgs(args: MotionArgs): MotionArgs = args.copy()
internal fun copyArgs(args: OperatorArgs): OperatorArgs = args.copy()

// ---------------------------------------------------------------------------
// Command matching
// ---------------------------------------------------------------------------

internal data class CommandMatchResult(
    val partial: List<VimKeyCommand>,
    val full: List<VimKeyCommand>
)

internal fun commandMatches(
    keys: String,
    keyMap: List<VimKeyCommand>,
    context: String?,
    inputState: InputState,
    noremapActive: Boolean = false
): CommandMatchResult {
    var ctx = context
    if (inputState.operator != null) ctx = "operatorPending"
    val partial = mutableListOf<VimKeyCommand>()
    val full = mutableListOf<VimKeyCommand>()
    val startIndex = if (noremapActive) keyMap.size - defaultKeymapLength else 0
    for (i in startIndex until keyMap.size) {
        val command = keyMap[i]
        if (ctx == "insert" && command.context != "insert") continue
        if (command.context != null && command.context != ctx) continue
        if (inputState.operator != null && command is ActionCommand) continue
        val match = commandMatch(keys, command.keys)
        if (match == "partial") partial.add(command)
        if (match == "full") full.add(command)
    }
    return CommandMatchResult(partial, full)
}

internal fun commandMatch(pressed: String, mapped: String): String? {
    val isLastCharacter = mapped.endsWith("<character>")
    val isLastRegister = mapped.endsWith("<register>")
    if (isLastCharacter || isLastRegister) {
        val prefixLen = mapped.length - if (isLastCharacter) 11 else 10
        val pressedPrefix = pressed.take(prefixLen)
        val mappedPrefix = mapped.take(prefixLen)
        return if (pressedPrefix == mappedPrefix && pressed.length > prefixLen) {
            "full"
        } else if (mappedPrefix.startsWith(pressedPrefix)) {
            "partial"
        } else {
            null
        }
    } else {
        return if (pressed == mapped) {
            "full"
        } else if (mapped.startsWith(pressed)) {
            "partial"
        } else {
            null
        }
    }
}

internal fun lastChar(keys: String): String {
    val match = Regex("^.*(<[^>]+>)$").find(keys)
    var selectedCharacter = match?.groupValues?.get(1) ?: keys.takeLast(1)
    if (selectedCharacter.length > 1) {
        selectedCharacter = when (selectedCharacter) {
            "<CR>", "<S-CR>" -> "\n"
            "<Space>", "<S-Space>" -> " "
            else -> ""
        }
    }
    return selectedCharacter
}

// ---------------------------------------------------------------------------
// Extend line to column (for block paste)
// ---------------------------------------------------------------------------

internal fun extendLineToColumn(cm: VimEditor, lineNum: Int, column: Int) {
    val endCh = lineLength(cm, lineNum)
    val spaces = " ".repeat(column - endCh)
    cm.setCursor(LinePos(lineNum, endCh))
    cm.replaceRange(spaces, cm.getCursor())
}

// ---------------------------------------------------------------------------
// Select block
// ---------------------------------------------------------------------------

internal fun selectBlock(cm: VimEditor, selectionEnd: LinePos): LinePos {
    val ranges = cm.listSelections()
    val head = cm.clipPos(selectionEnd)
    val isClipped = !cursorEqual(selectionEnd, head)
    val curHead = cm.getCursor("head")
    val primIndex = getIndex(ranges, curHead)
    val wasClipped = if (primIndex >= 0) {
        cursorEqual(ranges[primIndex].head, ranges[primIndex].anchor)
    } else {
        false
    }
    val max = ranges.size - 1
    val index = if (max - primIndex > primIndex) max else 0
    val base = if (ranges.isNotEmpty()) copyCursor(ranges[index].anchor) else LinePos(0, 0)

    val firstLine = min(base.line, head.line)
    val lastLine = max(base.line, head.line)
    var baseCh = base.ch
    var headCh = head.ch

    val dir = if (ranges.isNotEmpty()) ranges[index].head.ch - baseCh else 0
    val newDir = headCh - baseCh
    if (dir > 0 && newDir <= 0) {
        baseCh++
        if (!isClipped) headCh--
    } else if (dir < 0 && newDir >= 0) {
        baseCh--
        if (!wasClipped) headCh++
    } else if (dir < 0 && newDir == -1) {
        baseCh--
        headCh++
    }
    val selections = mutableListOf<LinePosRange>()
    for (line in firstLine..lastLine) {
        selections.add(LinePosRange(anchor = LinePos(line, baseCh), head = LinePos(line, headCh)))
    }
    cm.setSelections(selections)
    return LinePos(base.line, baseCh)
}

// ---------------------------------------------------------------------------
// Select for insert
// ---------------------------------------------------------------------------

internal fun selectForInsert(cm: VimEditor, head: LinePos, height: Int) {
    val sel = mutableListOf<LinePosRange>()
    for (i in 0 until height) {
        val lineHead = offsetCursor(head, i, 0)
        sel.add(LinePosRange(anchor = lineHead, head = lineHead))
    }
    // Position the cursor for insert mode without recording a history boundary.
    // A history-recorded selection here would stamp `selectionsAfter` on the
    // preceding operator-delete event, preventing the subsequent inserted text
    // from grouping with it — so `u` would take two steps to revert a single
    // change command (c/cw/s/…). See #23.
    cm.setSelections(sel, 0, addToHistory = false)
}

// ---------------------------------------------------------------------------
// getIndex
// ---------------------------------------------------------------------------

internal fun getIndex(ranges: List<LinePosRange>, cursor: LinePos, end: String? = null): Int {
    for (i in ranges.indices) {
        val atAnchor = end != "head" && cursorEqual(ranges[i].anchor, cursor)
        val atHead = end != "anchor" && cursorEqual(ranges[i].head, cursor)
        if (atAnchor || atHead) return i
    }
    return -1
}

// ---------------------------------------------------------------------------
// getSelectedAreaRange
// ---------------------------------------------------------------------------

internal fun getSelectedAreaRange(
    cm: VimEditor,
    @Suppress("UNUSED_PARAMETER") vim: VimState
): Pair<LinePos, LinePos> {
    val selections = cm.listSelections()
    val start = selections.first()
    val end = selections.last()
    val selectionStart = if (cursorIsBefore(start.anchor, start.head)) {
        start.anchor
    } else {
        start.head
    }
    val selectionEnd = if (cursorIsBefore(end.anchor, end.head)) end.head else end.anchor
    return selectionStart to selectionEnd
}

// ---------------------------------------------------------------------------
// updateLastSelection
// ---------------------------------------------------------------------------

internal fun updateLastSelection(cm: VimEditor, vim: VimState) {
    var anchor = vim.sel.anchor
    var head = vim.sel.head
    // To accommodate the effect of lastPastedText in the last selection
    if (vim.lastPastedText != null) {
        val idx = cm.indexFromPos(anchor)
        head = cm.posFromIndex(DocPos(idx.value + vim.lastPastedText!!.length))
        vim.lastPastedText = null
    }
    vim.lastSelection = VimLastSelection(
        anchorMark = cm.setBookmark(anchor),
        headMark = cm.setBookmark(head),
        anchor = copyCursor(anchor),
        head = copyCursor(head),
        visualMode = vim.visualMode,
        visualLine = vim.visualLine,
        visualBlock = vim.visualBlock
    )
}

// ---------------------------------------------------------------------------
// expandSelection
// ---------------------------------------------------------------------------

internal fun expandSelection(
    cm: VimEditor,
    start: LinePos,
    end: LinePos,
    move: Boolean = false
): Pair<LinePos, LinePos> {
    val sel = cm.vim!!.sel
    var headVar = if (move) start else sel.head
    var anchorVar = if (move) start else sel.anchor

    var s = start
    var e = end
    if (cursorIsBefore(e, s)) {
        val tmp = e
        e = s
        s = tmp
    }
    if (cursorIsBefore(headVar, anchorVar)) {
        headVar = cursorMin(s, headVar)
        anchorVar = cursorMax(anchorVar, e)
    } else {
        anchorVar = cursorMin(s, anchorVar)
        headVar = cursorMax(headVar, e)
        headVar = offsetCursor(headVar, 0, -1)
        if (headVar.ch == -1 && headVar.line != cm.firstLine()) {
            headVar = LinePos(headVar.line - 1, lineLength(cm, headVar.line - 1))
        }
    }
    return anchorVar to headVar
}

// ---------------------------------------------------------------------------
// updateCmSelection
// ---------------------------------------------------------------------------

internal fun updateCmSelection(cm: VimEditor, sel: LinePosRange? = null, mode: String? = null) {
    val vim = cm.vim!!
    val actualSel = sel ?: vim.sel
    val actualMode = mode ?: when {
        vim.visualLine -> "line"
        vim.visualBlock -> "block"
        else -> "char"
    }
    val cmSel = makeCmSelection(cm, actualSel, actualMode)
    cm.setSelections(cmSel.ranges, cmSel.primary)
}

internal fun makeCmSelection(
    cm: VimEditor,
    sel: LinePosRange,
    mode: String,
    exclusive: Boolean = false
): CmSelectionResult {
    val head = copyCursor(sel.head)
    val anchor = copyCursor(sel.anchor)
    return when (mode) {
        "char" -> {
            val headOffset = if (!exclusive && !cursorIsBefore(sel.head, sel.anchor)) 1 else 0
            val anchorOffset = if (cursorIsBefore(sel.head, sel.anchor)) 1 else 0
            val h = offsetCursor(sel.head, 0, headOffset)
            val a = offsetCursor(sel.anchor, 0, anchorOffset)
            CmSelectionResult(mutableListOf(LinePosRange(anchor = a, head = h)), 0)
        }
        "line" -> {
            var a = anchor
            var h = head
            if (!cursorIsBefore(sel.head, sel.anchor)) {
                a = LinePos(a.line, 0)
                val lastLine = cm.lastLine()
                h = if (h.line > lastLine) {
                    LinePos(lastLine, lineLength(cm, lastLine))
                } else {
                    LinePos(h.line, lineLength(cm, h.line))
                }
            } else {
                h = LinePos(h.line, 0)
                a = LinePos(a.line, lineLength(cm, a.line))
            }
            CmSelectionResult(mutableListOf(LinePosRange(anchor = a, head = h)), 0)
        }
        "block" -> {
            val top = min(anchor.line, head.line)
            var fromCh = anchor.ch
            val bottom = max(anchor.line, head.line)
            var toCh = head.ch
            if (fromCh < toCh) {
                // Guard against Int.MAX_VALUE overflow (JS uses Infinity)
                if (toCh < Int.MAX_VALUE) toCh += 1
            } else {
                if (fromCh < Int.MAX_VALUE) fromCh += 1
            }
            val height = bottom - top + 1
            val primary = if (head.line == top) 0 else height - 1
            val ranges = mutableListOf<LinePosRange>()
            for (i in 0 until height) {
                ranges.add(
                    LinePosRange(anchor = LinePos(top + i, fromCh), head = LinePos(top + i, toCh))
                )
            }
            CmSelectionResult(ranges, primary)
        }
        else -> error("Invalid mode: $mode")
    }
}

// ---------------------------------------------------------------------------
// getHead
// ---------------------------------------------------------------------------

internal fun getHead(cm: VimEditor): LinePos {
    var cur = cm.getCursor("head")
    if (cm.getSelection().length == 1) {
        cur = cursorMin(cur, cm.getCursor("anchor"))
    }
    return cur
}

// ---------------------------------------------------------------------------
// exitVisualMode
// ---------------------------------------------------------------------------

internal fun exitVisualMode(cm: VimEditor, moveHead: Boolean = true) {
    val vim = cm.vim!!
    if (moveHead) {
        cm.setCursor(clipCursorToContent(cm, vim.sel.head))
    }
    updateLastSelection(cm, vim)
    vim.visualMode = false
    vim.visualLine = false
    vim.visualBlock = false
    if (!vim.insertMode) {
        cm.signal("vim-mode-change", mapOf("mode" to "normal"))
    }
}

// ---------------------------------------------------------------------------
// clipToLine
// ---------------------------------------------------------------------------

internal fun clipToLine(cm: VimEditor, curStart: LinePos, curEnd: LinePos): LinePos {
    val selection = cm.getRange(curStart, curEnd)
    var endLine = curEnd.line
    var endCh = curEnd.ch
    if (Regex("\\n\\s*$").containsMatchIn(selection)) {
        val lines = selection.split('\n').toMutableList()
        lines.removeLastOrNull() // pop trailing whitespace-only line
        var line = lines.removeLastOrNull()
        while (lines.isNotEmpty() && line != null && isWhiteSpaceString(line)) {
            endLine--
            endCh = 0
            line = lines.removeLastOrNull()
        }
        if (line != null && line.isNotEmpty()) {
            endLine--
            endCh = lineLength(cm, endLine)
        } else {
            endCh = 0
        }
    }
    return LinePos(endLine, endCh)
}

// ---------------------------------------------------------------------------
// expandSelectionToLine
// ---------------------------------------------------------------------------

internal fun expandSelectionToLine(
    @Suppress("UNUSED_PARAMETER") cm: VimEditor,
    curStart: LinePos,
    curEnd: LinePos
): Pair<LinePos, LinePos> {
    return LinePos(curStart.line, 0) to LinePos(curEnd.line + 1, 0)
}

// ---------------------------------------------------------------------------
// findFirstNonWhiteSpaceCharacter
// ---------------------------------------------------------------------------

internal fun findFirstNonWhiteSpaceCharacter(text: String?): Int {
    if (text == null) return 0
    val firstNonWS = text.indexOfFirst { !it.isWhitespace() }
    return if (firstNonWS == -1) text.length else firstNonWS
}

// ---------------------------------------------------------------------------
// expandWordUnderCursor
// ---------------------------------------------------------------------------

internal data class WordBounds(val start: LinePos, val end: LinePos)

internal fun expandWordUnderCursor(
    cm: VimEditor,
    inclusive: Boolean = false,
    innerWord: Boolean = false,
    bigWord: Boolean = false,
    noSymbol: Boolean = false,
    multiline: Boolean = false,
    cursor: LinePos? = null
): WordBounds? {
    val cur = cursor ?: getHead(cm)
    val line = cm.getLine(cur.line)
    val endLineRef = arrayOf(line)
    val startLineNumber = cur.line
    var endLineNumber = startLineNumber
    var idx = cur.ch

    var wordOnNextLine: FindWordResult? = null
    var test: (String) -> Boolean = if (noSymbol) wordCharTest[0] else bigWordCharTest[0]
    if (innerWord && idx < line.length && Regex("\\s").containsMatchIn(line[idx].toString())) {
        test = { ch -> Regex("\\s").containsMatchIn(ch) }
    } else {
        while (idx < line.length && !test(line[idx].toString())) {
            idx++
            if (idx >= line.length) {
                if (!multiline) return null
                idx--
                wordOnNextLine = findWord(cm, cur, true, bigWord, true)
                break
            }
        }
        if (bigWord) {
            test = bigWordCharTest[0]
        } else {
            test = wordCharTest[0]
            if (idx < line.length && !test(line[idx].toString())) {
                test = wordCharTest[1]
            }
        }
    }

    var end = idx
    var start = idx
    while (start >= 0 && start < line.length && test(line[start].toString())) start--
    start++
    if (wordOnNextLine != null) {
        end = wordOnNextLine.to
        endLineNumber = wordOnNextLine.line
        endLineRef[0] = cm.getLine(endLineNumber)
        if (endLineRef[0].isEmpty() && end == 0) end++
    } else {
        while (end < line.length && test(line[end].toString())) end++
    }

    if (inclusive) {
        val wordEnd = end
        val startsWithSpace = cur.ch <= start && cur.ch < line.length &&
            Regex("\\s").containsMatchIn(line[cur.ch].toString())
        if (!startsWithSpace) {
            while (end < endLineRef[0].length &&
                Regex("\\s").containsMatchIn(endLineRef[0][end].toString())
                ) end++
        }
        if (wordEnd == end || startsWithSpace) {
            val wordStart = start
            while (start > 0 &&
                Regex("\\s").containsMatchIn(line[start - 1].toString())
                ) start--
            if (start == 0 && !startsWithSpace) start = wordStart
        }
    }

    return WordBounds(start = LinePos(startLineNumber, start), end = LinePos(endLineNumber, end))
}

// ---------------------------------------------------------------------------
// expandTagUnderCursor (stub - requires XML fold support)
// ---------------------------------------------------------------------------

internal fun expandTagUnderCursor(
    @Suppress("UNUSED_PARAMETER") cm: VimEditor,
    head: LinePos,
    @Suppress("UNUSED_PARAMETER") inclusive: Boolean = false
): WordBounds {
    // Tag matching is not supported in Kodemirror yet
    return WordBounds(start = head, end = head)
}

// ---------------------------------------------------------------------------
// recordJumpPosition
// ---------------------------------------------------------------------------

internal fun recordJumpPosition(cm: VimEditor, oldCur: LinePos, newCur: LinePos) {
    if (!cursorEqual(oldCur, newCur)) {
        cm.vimContext.jumpList.add(cm, oldCur, newCur)
    }
}

// ---------------------------------------------------------------------------
// recordLastCharacterSearch
// ---------------------------------------------------------------------------

internal fun recordLastCharacterSearch(cm: VimEditor, increment: Int, args: MotionArgs) {
    cm.vimContext.lastCharacterSearch.increment = increment
    cm.vimContext.lastCharacterSearch.forward = args.forward == true
    cm.vimContext.lastCharacterSearch.selectedCharacter = args.selectedCharacter ?: ""
}

// ---------------------------------------------------------------------------
// Symbol navigation
// ---------------------------------------------------------------------------

internal val symbolToMode: Map<String, String> = mapOf(
    "(" to "bracket", ")" to "bracket", "{" to "bracket", "}" to "bracket",
    "[" to "section", "]" to "section",
    "*" to "comment", "/" to "comment",
    "m" to "method", "M" to "method",
    "#" to "preprocess"
)

internal class FindSymbolState(
    var lineText: String,
    var nextCh: String,
    var lastCh: String?,
    var index: Int,
    var symb: String,
    var reverseSymb: String?,
    var forward: Boolean?,
    var depth: Int,
    var curMoveThrough: Boolean
)

internal interface FindSymbolMode {
    fun init(state: FindSymbolState) {}
    fun isComplete(state: FindSymbolState): Boolean
}

internal val findSymbolModes: Map<String, FindSymbolMode> = mapOf(
    "bracket" to object : FindSymbolMode {
        override fun isComplete(state: FindSymbolState): Boolean {
            if (state.nextCh == state.symb) {
                state.depth++
                if (state.depth >= 1) return true
            } else if (state.nextCh == state.reverseSymb) {
                state.depth--
            }
            return false
        }
    },
    "section" to object : FindSymbolMode {
        override fun init(state: FindSymbolState) {
            state.curMoveThrough = true
            state.symb = if ((if (state.forward == true) "]" else "[") == state.symb) "{" else "}"
        }
        override fun isComplete(state: FindSymbolState): Boolean {
            return state.index == 0 && state.nextCh == state.symb
        }
    },
    "comment" to object : FindSymbolMode {
        override fun isComplete(state: FindSymbolState): Boolean {
            val found = state.lastCh == "*" && state.nextCh == "/"
            state.lastCh = state.nextCh
            return found
        }
    },
    "method" to object : FindSymbolMode {
        override fun init(state: FindSymbolState) {
            state.symb = if (state.symb == "m") "{" else "}"
            state.reverseSymb = if (state.symb == "{") "}" else "{"
        }
        override fun isComplete(state: FindSymbolState): Boolean {
            return state.nextCh == state.symb
        }
    },
    "preprocess" to object : FindSymbolMode {
        override fun init(state: FindSymbolState) {
            state.index = 0
        }
        override fun isComplete(state: FindSymbolState): Boolean {
            if (state.nextCh == "#") {
                val token = Regex("^#(\\w+)").find(state.lineText)?.groupValues?.get(1)
                if (token == "endif") {
                    if (state.forward == true && state.depth == 0) return true
                    state.depth++
                } else if (token == "if") {
                    if (state.forward != true && state.depth == 0) return true
                    state.depth--
                }
                if (token == "else" && state.depth == 0) return true
            }
            return false
        }
    }
)

internal fun findSymbol(cm: VimEditor, repeat: Int, forward: Boolean?, symb: String): LinePos {
    val cur = copyCursor(cm.getCursor())
    val increment = if (forward == true) 1 else -1
    val endLine = if (forward == true) cm.lineCount() else -1
    var curCh = cur.ch
    var line = cur.line
    var lineText = cm.getLine(line)
    val reverseMap = if (forward == true) {
        mapOf(")" to "(", "}" to "{")
    } else {
        mapOf("(" to ")", "{" to "}")
    }
    val state = FindSymbolState(
        lineText = lineText,
        nextCh = if (curCh < lineText.length) lineText[curCh].toString() else "",
        lastCh = null,
        index = curCh,
        symb = symb,
        reverseSymb = reverseMap[symb],
        forward = forward,
        depth = 0,
        curMoveThrough = false
    )
    val mode = symbolToMode[symb] ?: return cur
    val symbolMode = findSymbolModes[mode] ?: return cur
    symbolMode.init(state)
    var remaining = repeat
    while (line != endLine && remaining > 0) {
        state.index += increment
        state.nextCh = if (state.index >= 0 && state.index < state.lineText.length) {
            state.lineText[state.index].toString()
        } else {
            ""
        }
        if (state.nextCh.isEmpty()) {
            line += increment
            state.lineText = cm.getLine(line)
            if (increment > 0) {
                state.index = 0
            } else {
                val lineLen = state.lineText.length
                state.index = if (lineLen > 0) lineLen - 1 else 0
            }
            state.nextCh = if (state.lineText.isNotEmpty() && state.index < state.lineText.length) {
                state.lineText[state.index].toString()
            } else {
                ""
            }
        }
        if (symbolMode.isComplete(state)) {
            remaining--
            if (remaining == 0) {
                return LinePos(line, state.index)
            }
        }
    }
    if (state.nextCh.isNotEmpty() || state.curMoveThrough) {
        return LinePos(line, state.index)
    }
    return cur
}

// ---------------------------------------------------------------------------
// findWord
// ---------------------------------------------------------------------------

internal data class FindWordResult(val from: Int, val to: Int, val line: Int)

internal fun findWord(
    cm: VimEditor,
    cur: LinePos,
    forward: Boolean,
    bigWord: Boolean?,
    emptyLineIsWord: Boolean?
): FindWordResult? {
    var lineNum = cur.line
    var pos = cur.ch
    var line = cm.getLine(lineNum)
    val dir = if (forward) 1 else -1
    val charTests = if (bigWord == true) bigWordCharTest else wordCharTest

    if (emptyLineIsWord == true && line.isEmpty()) {
        lineNum += dir
        line = cm.getLine(lineNum)
        if (!isLine(cm, lineNum)) return null
        pos = if (forward) 0 else line.length
    }

    while (true) {
        if (emptyLineIsWord == true && line.isEmpty()) {
            return FindWordResult(from = 0, to = 0, line = lineNum)
        }
        val stop = if (dir > 0) line.length else -1
        var wordStart = stop
        var wordEnd = stop
        while (pos != stop) {
            var foundWord = false
            for (i in charTests.indices) {
                if (pos >= 0 && pos < line.length && charTests[i](line[pos].toString())) {
                    wordStart = pos
                    while (pos != stop && pos >= 0 && pos < line.length &&
                        charTests[i](line[pos].toString())
                    ) {
                        pos += dir
                    }
                    wordEnd = pos
                    foundWord = wordStart != wordEnd
                    if (wordStart == cur.ch && lineNum == cur.line &&
                        wordEnd == wordStart + dir
                    ) {
                        continue
                    } else {
                        return FindWordResult(
                            from = min(wordStart, wordEnd + 1),
                            to = max(wordStart, wordEnd),
                            line = lineNum
                        )
                    }
                }
            }
            if (!foundWord) {
                pos += dir
            }
        }
        lineNum += dir
        if (!isLine(cm, lineNum)) return null
        line = cm.getLine(lineNum)
        pos = if (dir > 0) 0 else line.length
    }
}

// ---------------------------------------------------------------------------
// moveToWord
// ---------------------------------------------------------------------------

internal fun moveToWord(
    cm: VimEditor,
    cur: LinePos,
    repeat: Int,
    forward: Boolean,
    wordEnd: Boolean,
    bigWord: Boolean
): LinePos? {
    val curStart = copyCursor(cur)
    val words = mutableListOf<FindWordResult>()
    var rpt = repeat
    if (forward && !wordEnd || !forward && wordEnd) {
        rpt++
    }
    val emptyLineIsWord = !(forward && wordEnd)
    var currentCur = cur
    for (i in 0 until rpt) {
        val word = findWord(cm, currentCur, forward, bigWord, emptyLineIsWord)
        if (word == null) {
            val eodCh = lineLength(cm, cm.lastLine())
            words.add(
                if (forward) {
                    FindWordResult(line = cm.lastLine(), from = eodCh, to = eodCh)
                } else {
                    FindWordResult(line = 0, from = 0, to = 0)
                }
            )
            break
        }
        words.add(word)
        currentCur = LinePos(word.line, if (forward) word.to - 1 else word.from)
    }
    val shortCircuit = words.size != rpt
    val firstWord = words.firstOrNull() ?: return null
    var lastWord = words.removeLastOrNull() ?: return null
    return when {
        forward && !wordEnd -> {
            val wordMoved = firstWord.from != curStart.ch ||
                firstWord.line != curStart.line
            if (!shortCircuit && wordMoved) {
                lastWord = words.removeLastOrNull() ?: return null
            }
            LinePos(lastWord.line, lastWord.from)
        }
        forward && wordEnd -> LinePos(lastWord.line, lastWord.to - 1)
        !forward && wordEnd -> {
            if (!shortCircuit && (firstWord.to != curStart.ch || firstWord.line != curStart.line)) {
                lastWord = words.removeLastOrNull() ?: return null
            }
            LinePos(lastWord.line, lastWord.to)
        }
        else -> LinePos(lastWord.line, lastWord.from) // b
    }
}

// ---------------------------------------------------------------------------
// moveToEol
// ---------------------------------------------------------------------------

internal fun moveToEol(
    cm: VimEditor,
    head: LinePos,
    motionArgs: MotionArgs,
    vim: VimState,
    keepHPos: Boolean
): LinePos {
    val retval = LinePos(head.line + motionArgs.repeat - 1, Int.MAX_VALUE)
    val end = cm.clipPos(retval)
    if (!keepHPos) {
        vim.lastHPos = Int.MAX_VALUE
        vim.lastHSPos = 0 // charCoords not available, approximate
    }
    return retval
}

// ---------------------------------------------------------------------------
// moveToCharacter
// ---------------------------------------------------------------------------

internal fun moveToCharacter(
    cm: VimEditor,
    repeat: Int,
    forward: Boolean?,
    character: String?,
    head: LinePos? = null
): LinePos? {
    if (character == null) return null
    val cur = head ?: cm.getCursor()
    var start = cur.ch
    var idx = -1
    for (i in 0 until repeat) {
        val line = cm.getLine(cur.line)
        idx = charIdxInLine(start, line, character, forward == true, true)
        if (idx == -1) return null
        start = idx
    }
    return if (idx != -1) LinePos(cm.getCursor().line, idx) else null
}

// ---------------------------------------------------------------------------
// moveToColumn
// ---------------------------------------------------------------------------

internal fun moveToColumn(cm: VimEditor, repeat: Int): LinePos {
    val line = cm.getCursor().line
    return clipCursorToContent(cm, LinePos(line, repeat - 1))
}

// ---------------------------------------------------------------------------
// updateMark
// ---------------------------------------------------------------------------

internal fun updateMark(cm: VimEditor, vim: VimState, markName: String, pos: LinePos) {
    if (!inArray(markName, validMarks) && !latinCharRegex.matches(markName)) {
        return
    }
    vim.marks[markName]?.clear()
    vim.marks[markName] = cm.setBookmark(pos)
}

// ---------------------------------------------------------------------------
// charIdxInLine
// ---------------------------------------------------------------------------

internal fun charIdxInLine(
    start: Int,
    line: String,
    character: String,
    forward: Boolean,
    includeChar: Boolean
): Int {
    var idx: Int
    if (forward) {
        idx = line.indexOf(character, start + 1)
        if (idx != -1 && !includeChar) idx -= 1
    } else {
        idx = line.lastIndexOf(character, start - 1)
        if (idx != -1 && !includeChar) idx += 1
    }
    return idx
}

// ---------------------------------------------------------------------------
// findParagraph
// ---------------------------------------------------------------------------

internal data class ParagraphRange(val start: LinePos, val end: LinePos)

internal fun findParagraph(
    cm: VimEditor,
    head: LinePos,
    repeat: Int,
    dir: Int,
    inclusive: Boolean
): ParagraphRange {
    var line = head.line
    val minLine = cm.firstLine()
    val maxLine = cm.lastLine()
    var i = line
    var remaining = repeat

    fun isEmpty(l: Int): Boolean = cm.getLine(l).isEmpty()

    fun isBoundary(l: Int, d: Int, any: Boolean = false): Boolean {
        val next = l + d
        if (next < minLine || next > maxLine) return false
        return if (any) {
            isEmpty(l) != isEmpty(next)
        } else {
            !isEmpty(l) && isEmpty(next)
        }
    }

    if (dir != 0) {
        while (i in minLine..maxLine && remaining > 0) {
            if (isBoundary(i, dir)) remaining--
            i += dir
        }
        return ParagraphRange(start = LinePos(i, 0), end = head)
    }

    val vim = cm.vim!!
    if (vim.visualLine && isBoundary(line, 1, true)) {
        val anchor = vim.sel.anchor
        if (isBoundary(anchor.line, -1, true)) {
            if (!inclusive || anchor.line != line) {
                line += 1
            }
        }
    }
    val startState = isEmpty(line)
    i = line
    while (i <= maxLine && remaining > 0) {
        if (isBoundary(i, 1, true)) {
            if (!inclusive || isEmpty(i) != startState) {
                remaining--
            }
        }
        i++
    }
    val end = LinePos(i, 0)
    val adjustedStartState = if (i > maxLine && !startState) true else startState
    val adjustedInclusive = if (i > maxLine && !startState) false else inclusive
    i = line
    while (i > minLine) {
        if (!adjustedInclusive || isEmpty(i) == adjustedStartState || i == line) {
            if (isBoundary(i, -1, true)) break
        }
        i--
    }
    val start = LinePos(i, 0)
    return ParagraphRange(start = start, end = end)
}

// ---------------------------------------------------------------------------
// getSentence
// ---------------------------------------------------------------------------

internal fun getSentence(
    cm: VimEditor,
    cur: LinePos,
    repeat: Int,
    dir: Int,
    inclusive: Boolean
): LinePos {
    data class Index(var line: String?, var ln: Int, var pos: Int, var dir: Int)

    fun nextChar(curr: Index) {
        if (curr.line == null) return
        if (curr.pos + curr.dir < 0 || curr.pos + curr.dir >= (curr.line?.length ?: 0)) {
            curr.line = null
        } else {
            curr.pos += curr.dir
        }
    }

    fun forward(cm: VimEditor, ln: Int, pos: Int, d: Int): Pair<Int, Int> {
        val line = cm.getLine(ln)
        val curr = Index(line = line, ln = ln, pos = pos, dir = d)
        if (curr.line?.isEmpty() == true) return curr.ln to curr.pos
        var lastSentencePos = curr.pos
        nextChar(curr)
        while (curr.line != null) {
            lastSentencePos = curr.pos
            if (isEndOfSentenceSymbol(curr.line!![curr.pos].toString())) {
                if (!inclusive) {
                    return curr.ln to (curr.pos + 1)
                } else {
                    nextChar(curr)
                    while (curr.line != null) {
                        if (isWhiteSpaceString(curr.line!![curr.pos].toString())) {
                            lastSentencePos = curr.pos
                            nextChar(curr)
                        } else {
                            break
                        }
                    }
                    return curr.ln to (lastSentencePos + 1)
                }
            }
            nextChar(curr)
        }
        return curr.ln to (lastSentencePos + 1)
    }

    fun reverse(cm: VimEditor, ln: Int, pos: Int, d: Int): Pair<Int, Int> {
        val line = cm.getLine(ln)
        val curr = Index(line = line, ln = ln, pos = pos, dir = d)
        if (curr.line?.isEmpty() == true) return curr.ln to curr.pos
        var lastSentencePos = curr.pos
        nextChar(curr)
        while (curr.line != null) {
            val ch = curr.line!![curr.pos].toString()
            if (!isWhiteSpaceString(ch) && !isEndOfSentenceSymbol(ch)) {
                lastSentencePos = curr.pos
            } else if (isEndOfSentenceSymbol(ch)) {
                if (!inclusive) {
                    return curr.ln to lastSentencePos
                } else {
                    if (curr.pos + 1 < curr.line!!.length &&
                        isWhiteSpaceString(curr.line!![curr.pos + 1].toString())
                    ) {
                        return curr.ln to (curr.pos + 1)
                    } else {
                        return curr.ln to lastSentencePos
                    }
                }
            }
            nextChar(curr)
        }
        curr.line = line
        return if (inclusive && curr.line != null && curr.pos < curr.line!!.length &&
            isWhiteSpaceString(curr.line!![curr.pos].toString())
        ) {
            curr.ln to curr.pos
        } else {
            curr.ln to lastSentencePos
        }
    }

    var currLn = cur.line
    var currPos = cur.ch
    var remaining = repeat
    while (remaining > 0) {
        val result = if (dir < 0) {
            reverse(cm, currLn, currPos, dir)
        } else {
            forward(cm, currLn, currPos, dir)
        }
        currLn = result.first
        currPos = result.second
        remaining--
    }
    return LinePos(currLn, currPos)
}

// ---------------------------------------------------------------------------
// findSentence
// ---------------------------------------------------------------------------

internal fun findSentence(cm: VimEditor, cur: LinePos, repeat: Int, dir: Int): LinePos {
    data class Idx(
        var line: String?,
        var ln: Int,
        var pos: Int,
        var dir: Int
    )

    fun nextChar(cm: VimEditor, idx: Idx) {
        if (idx.line == null) return
        if (idx.pos + idx.dir < 0 || idx.pos + idx.dir >= (idx.line?.length ?: 0)) {
            idx.ln += idx.dir
            if (!isLine(cm, idx.ln)) {
                idx.line = null
                return
            }
            idx.line = cm.getLine(idx.ln)
            idx.pos = if (idx.dir > 0) 0 else (idx.line?.length ?: 1) - 1
        } else {
            idx.pos += idx.dir
        }
    }

    fun forward(cm: VimEditor, ln: Int, pos: Int, d: Int): Pair<Int, Int> {
        val line = cm.getLine(ln)
        var stop = line.isEmpty()
        val curr = Idx(line = line, ln = ln, pos = pos, dir = d)
        var lastValidLn = curr.ln
        var lastValidPos = curr.pos
        val skipEmptyLines = curr.line?.isEmpty() == true
        nextChar(cm, curr)
        while (curr.line != null) {
            lastValidLn = curr.ln
            lastValidPos = curr.pos
            if (curr.line?.isEmpty() == true && !skipEmptyLines) {
                return curr.ln to curr.pos
            } else if (stop && curr.line?.isNotEmpty() == true &&
                curr.pos < curr.line!!.length &&
                !isWhiteSpaceString(curr.line!![curr.pos].toString())
            ) {
                return curr.ln to curr.pos
            } else if (curr.pos < (curr.line?.length ?: 0) &&
                isEndOfSentenceSymbol(curr.line!![curr.pos].toString()) && !stop &&
                (
                    curr.pos == curr.line!!.length - 1 ||
                        isWhiteSpaceString(curr.line!![curr.pos + 1].toString())
                    )
            ) {
                stop = true
            }
            nextChar(cm, curr)
        }
        val lastLine = cm.getLine(lastValidLn)
        lastValidPos = 0
        for (i in lastLine.length - 1 downTo 0) {
            if (!isWhiteSpaceString(lastLine[i].toString())) {
                lastValidPos = i
                break
            }
        }
        return lastValidLn to lastValidPos
    }

    fun reverse(cm: VimEditor, ln: Int, pos: Int, d: Int): Pair<Int, Int> {
        val line = cm.getLine(ln)
        val curr = Idx(line = line, ln = ln, pos = pos, dir = d)
        var lastValidLn = curr.ln
        var lastValidPos: Int? = null
        val skipEmptyLines = curr.line?.isEmpty() == true
        var skipEmptyVar = skipEmptyLines
        nextChar(cm, curr)
        while (curr.line != null) {
            if (curr.line?.isEmpty() == true && !skipEmptyVar) {
                return if (lastValidPos != null) {
                    lastValidLn to lastValidPos
                } else {
                    curr.ln to curr.pos
                }
            } else if (curr.pos >= 0 && curr.pos < (curr.line?.length ?: 0) &&
                isEndOfSentenceSymbol(curr.line!![curr.pos].toString()) &&
                lastValidPos != null &&
                !(curr.ln == lastValidLn && curr.pos + 1 == lastValidPos)
            ) {
                return lastValidLn to lastValidPos
            } else if (curr.line?.isNotEmpty() == true &&
                curr.pos >= 0 && curr.pos < curr.line!!.length &&
                !isWhiteSpaceString(curr.line!![curr.pos].toString())
            ) {
                skipEmptyVar = false
                lastValidLn = curr.ln
                lastValidPos = curr.pos
            }
            nextChar(cm, curr)
        }
        val lastLine = cm.getLine(lastValidLn)
        lastValidPos = 0
        for (i in lastLine.indices) {
            if (!isWhiteSpaceString(lastLine[i].toString())) {
                lastValidPos = i
                break
            }
        }
        return lastValidLn to (lastValidPos ?: 0)
    }

    var currLn = cur.line
    var currPos = cur.ch
    var remaining = repeat
    while (remaining > 0) {
        val result = if (dir < 0) {
            reverse(cm, currLn, currPos, dir)
        } else {
            forward(cm, currLn, currPos, dir)
        }
        currLn = result.first
        currPos = result.second
        remaining--
    }
    return LinePos(currLn, currPos)
}

// ---------------------------------------------------------------------------
// selectCompanionObject
// ---------------------------------------------------------------------------

internal fun selectCompanionObject(
    cm: VimEditor,
    head: LinePos,
    symb: String,
    inclusive: Boolean
): WordBounds? {
    val bracketRegexp = when (symb) {
        "(", ")" -> Regex("[()]")
        "[", "]" -> Regex("[\\[\\]]")
        "{", "}" -> Regex("[{}]")
        "<", ">" -> Regex("[<>]")
        else -> return null
    }
    val openSym = when (symb) {
        "(", ")" -> "("
        "[", "]" -> "["
        "{", "}" -> "{"
        "<", ">" -> "<"
        else -> return null
    }
    val curChar = if (head.ch < cm.getLine(head.line).length) {
        cm.getLine(head.line)[head.ch].toString()
    } else {
        ""
    }
    val offset = if (curChar == openSym) 1 else 0

    val startBracket = cm.scanForBracket(
        LinePos(head.line, head.ch + offset),
        -1,
        null,
        mapOf("bracketRegex" to bracketRegexp)
    )
    val endBracket = cm.scanForBracket(
        LinePos(head.line, head.ch + offset),
        1,
        null,
        mapOf("bracketRegex" to bracketRegexp)
    )

    if (startBracket == null || endBracket == null) return null

    var start = startBracket.pos
    var end = endBracket.pos

    if ((start.line == end.line && start.ch > end.ch) || start.line > end.line) {
        val tmp = start
        start = end
        end = tmp
    }

    return if (inclusive) {
        WordBounds(start = start, end = LinePos(end.line, end.ch + 1))
    } else {
        WordBounds(start = LinePos(start.line, start.ch + 1), end = end)
    }
}

// ---------------------------------------------------------------------------
// findBeginningAndEnd
// ---------------------------------------------------------------------------

internal fun findBeginningAndEnd(
    cm: VimEditor,
    head: LinePos,
    symb: String,
    inclusive: Boolean
): WordBounds {
    var curCh = head.ch
    val line = cm.getLine(head.line)
    val chars = line.toList().map { it.toString() }
    var start: Int? = null
    var end: Int? = null
    val firstIndex = chars.indexOf(symb)

    if (curCh < firstIndex) {
        curCh = firstIndex
    } else if (firstIndex < curCh && curCh < chars.size && chars[curCh] == symb) {
        val stringAfter = cm.getTokenTypeAt(offsetCursor(head, 0, 1)).contains("string")
        val stringBefore = cm.getTokenTypeAt(head).contains("string")
        val isStringStart = stringAfter && !stringBefore
        if (!isStringStart) {
            end = curCh
            curCh--
        }
    }

    if (curCh >= 0 && curCh < chars.size && chars[curCh] == symb && end == null) {
        start = curCh + 1
    } else {
        var i = curCh
        while (i > -1 && start == null) {
            if (chars[i] == symb) {
                start = i + 1
            }
            i--
        }
    }

    if (start != null && end == null) {
        var i = start
        while (i < chars.size && end == null) {
            if (chars[i] == symb) {
                end = i
            }
            i++
        }
    }

    if (start == null || end == null) {
        return WordBounds(start = LinePos(head.line, curCh), end = LinePos(head.line, curCh))
    }

    if (inclusive) {
        start--
        end++
    }

    return WordBounds(
        start = LinePos(head.line, start),
        end = LinePos(head.line, end)
    )
}

// ---------------------------------------------------------------------------
// Search functions
// ---------------------------------------------------------------------------

internal fun getSearchState(cm: VimEditor): SearchState {
    val vim = cm.vim!!
    return vim.searchState_ ?: SearchState().also { vim.searchState_ = it }
}

internal fun splitBySlash(argString: String): List<String>? = splitBySeparator(argString, "/")

internal fun findUnescapedSlashes(argString: String): List<Int> =
    findUnescapedSeparators(argString, "/")

internal fun splitBySeparator(argString: String, separator: String): List<String>? {
    val slashes = findUnescapedSeparators(argString, separator)
    if (slashes.isEmpty()) return emptyList()
    val tokens = mutableListOf<String>()
    if (slashes[0] != 0) return null
    for (i in slashes.indices) {
        val start = slashes[i] + 1
        val end = slashes.getOrNull(i + 1) ?: argString.length
        tokens.add(argString.substring(start, end))
    }
    return tokens
}

internal fun findUnescapedSeparators(str: String, separator: String = "/"): List<Int> {
    var escapeNextChar = false
    val slashes = mutableListOf<Int>()
    for (i in str.indices) {
        val c = str[i].toString()
        if (!escapeNextChar && c == separator) {
            slashes.add(i)
        }
        escapeNextChar = !escapeNextChar && c == "\\"
    }
    return slashes
}

/**
 * Translates a search string from ex (vim) syntax into regex form.
 */
internal fun translateRegex(str: String): String {
    val modes = mapOf(
        'V' to "|(){+?*.[\$^",
        'M' to "|(){+?*.[",
        'm' to "|(){+?",
        'v' to "<>"
    )
    val escapes = mapOf(
        '>' to "(?<=[\\w])(?=[^\\w]|\$)",
        '<' to "(?<=[^\\w]|^)(?=[\\w])"
    )
    var specials = modes['m']!!

    var regex = Regex("\\\\.|[\\[|(){+*?.\$^<>]").replace(str) { matchResult ->
        val match = matchResult.value
        if (match[0] == '\\') {
            val nextChar = match[1]
            if (nextChar == '}' || specials.contains(nextChar)) {
                return@replace nextChar.toString()
            }
            if (nextChar in modes) {
                specials = modes[nextChar]!!
                return@replace ""
            }
            if (nextChar in escapes) {
                return@replace escapes[nextChar]!!
            }
            match
        } else {
            if (specials.contains(match[0])) {
                escapes[match[0]] ?: "\\$match"
            } else {
                match
            }
        }
    }

    val zsIdx = regex.indexOf("\\zs")
    if (zsIdx != -1) {
        regex = "(?<=" + regex.substring(0, zsIdx) + ")" + regex.substring(zsIdx + 3)
    }
    val zeIdx = regex.indexOf("\\ze")
    if (zeIdx != -1) {
        regex = regex.substring(0, zeIdx) + "(?=" + regex.substring(zeIdx + 3) + ")"
    }

    return regex
}

internal val charUnescapes = mapOf(
    "\\n" to "\n",
    "\\r" to "\r",
    "\\t" to "\t"
)

internal fun translateRegexReplace(str: String): String {
    var escapeNextChar = false
    val out = StringBuilder()
    var i = -1
    while (i < str.length) {
        val c = if (i >= 0 && i < str.length) str[i].toString() else ""
        val n = if (i + 1 < str.length) str[i + 1].toString() else ""
        val pair = c + n
        if (charUnescapes.containsKey(pair)) {
            out.append(charUnescapes[pair])
            i += 2
            continue
        }
        if (escapeNextChar) {
            out.append(c)
            escapeNextChar = false
        } else {
            if (c == "\\") {
                escapeNextChar = true
                if (isNumber(n) || n == "\$") {
                    out.append("\$")
                } else if (n != "/" && n != "\\") {
                    out.append("\\")
                }
            } else {
                if (c == "\$") {
                    out.append("\$")
                }
                out.append(c)
                if (n == "/") {
                    out.append("\\")
                }
            }
        }
        i++
    }
    return out.toString()
}

internal val unescapes = mapOf(
    "\\/" to "/",
    "\\\\" to "\\",
    "\\n" to "\n",
    "\\r" to "\r",
    "\\t" to "\t",
    "\\&" to "&"
)

internal fun unescapeRegexReplace(str: String): String {
    val output = StringBuilder()
    var i = 0
    while (i < str.length) {
        if (str[i] == '\\' && i + 1 < str.length) {
            val pair = str.substring(i, i + 2)
            if (unescapes.containsKey(pair)) {
                output.append(unescapes[pair])
                i += 2
                continue
            }
        }
        output.append(str[i])
        i++
    }
    return output.toString()
}

internal fun parseQuery(
    cm: VimEditor,
    query: String,
    ignoreCase: Boolean,
    smartCase: Boolean
): Regex? {
    val lastSearchRegister = cm.vimContext.registerController.getRegister("/")
    lastSearchRegister.setText(query)

    val slashes = findUnescapedSlashes(query)
    var regexPart: String
    var forceIgnoreCase = false
    if (slashes.isEmpty()) {
        regexPart = query
    } else {
        regexPart = query.substring(0, slashes[0])
        val flagsPart = query.substring(slashes[0])
        forceIgnoreCase = flagsPart.contains('i')
    }
    if (regexPart.isEmpty()) return null
    if (getOption("pcre") != true) {
        regexPart = translateRegex(regexPart)
    }
    var effectiveIgnoreCase = ignoreCase
    if (smartCase) {
        effectiveIgnoreCase = Regex("^[^A-Z]*$").matches(regexPart)
    }
    val options = mutableSetOf(RegexOption.MULTILINE)
    if (effectiveIgnoreCase || forceIgnoreCase) {
        options.add(RegexOption.IGNORE_CASE)
    }
    return try {
        Regex(regexPart, options)
    } catch (_: Throwable) {
        null
    }
}

internal fun regexEqual(r1: Regex?, r2: Regex?): Boolean {
    if (r1 == null || r2 == null) return false
    return r1.pattern == r2.pattern && r1.options == r2.options
}

internal fun updateSearchQuery(
    cm: VimEditor,
    rawQuery: String,
    ignoreCase: Boolean = false,
    smartCase: Boolean = false
): Regex? {
    if (rawQuery.isEmpty()) return null
    val state = getSearchState(cm)
    val query = parseQuery(cm, rawQuery, ignoreCase, smartCase) ?: return null
    highlightSearchMatches(cm, query)
    if (regexEqual(query, state.getQuery())) {
        return query
    }
    state.setQuery(query)
    return query
}

internal fun highlightSearchMatches(cm: VimEditor, query: Regex) {
    val searchState = getSearchState(cm)
    val overlay = searchState.getOverlay()
    if (overlay == null || query.pattern != overlay.query.pattern) {
        if (overlay != null) {
            cm.removeOverlay(overlay)
        }
        val newOverlay = SearchOverlay(query)
        cm.addOverlay(newOverlay)
        searchState.setOverlay(newOverlay)
    }
}

internal fun findNext(cm: VimEditor, prev: Boolean, query: Regex, repeat: Int? = null): LinePos? {
    return cm.operation {
        val rpt = repeat ?: 1
        var pos = cm.getCursor()
        var cursor = cm.getSearchCursor(query, pos)
        for (i in 0 until rpt) {
            var found = cursor.find(prev)
            val atStart = cursor.from() != null &&
                cursorEqual(cursor.from()!!, pos)
            if (i == 0 && found != null && atStart) {
                val lastEndPos = if (prev) cursor.from() else cursor.to()
                found = cursor.find(prev)
                if (found != null && found[0]?.isEmpty() == true &&
                    lastEndPos != null && cursor.from() != null &&
                    cursorEqual(cursor.from()!!, lastEndPos)
                ) {
                    if (lastEndPos.ch == cm.getLine(lastEndPos.line).length) {
                        found = cursor.find(prev)
                    }
                }
            }
            if (found == null) {
                cursor = cm.getSearchCursor(
                    query,
                    if (prev) {
                        LinePos(cm.lastLine(), Int.MAX_VALUE)
                    } else {
                        LinePos(cm.firstLine(), 0)
                    }
                )
                if (cursor.find(prev) == null) {
                    return@operation null
                }
            }
        }
        cursor.from()
    }
}

internal fun findNextFromAndToInclusive(
    cm: VimEditor,
    prev: Boolean,
    query: Regex,
    repeat: Int? = null,
    vim: VimState
): Pair<LinePos, LinePos>? {
    return cm.operation {
        val rpt = repeat ?: 1
        val pos = cm.getCursor()
        val cursor = cm.getSearchCursor(query, pos)

        // Go back one result
        var found = cursor.find(!prev)

        if (!vim.visualMode && found != null &&
            cursor.from() != null && cursorEqual(cursor.from()!!, pos)
        ) {
            cursor.find(!prev)
        }

        for (i in 0 until rpt) {
            found = cursor.find(prev)
            if (found == null) {
                val wrapCursor = cm.getSearchCursor(
                    query,
                    if (prev) {
                        LinePos(cm.lastLine(), Int.MAX_VALUE)
                    } else {
                        LinePos(cm.firstLine(), 0)
                    }
                )
                if (wrapCursor.find(prev) == null) {
                    return@operation null
                }
                // Use wrapCursor results
                val f = wrapCursor.from()
                val t = wrapCursor.to()
                if (f != null && t != null) return@operation f to t
                return@operation null
            }
        }
        val from = cursor.from()
        val to = cursor.to()
        if (from != null && to != null) from to to else null
    }
}

internal fun clearSearchHighlight(cm: VimEditor) {
    val state = getSearchState(cm)
    cm.removeOverlay(state.getOverlay())
    state.setOverlay(null)
}

// ---------------------------------------------------------------------------
// isInRange
// ---------------------------------------------------------------------------

internal fun isInRange(pos: Int, start: Int, end: Int): Boolean = pos in start..end

internal fun isInRange(pos: LinePos, start: Int, end: Int): Boolean = pos.line in start..end

// ---------------------------------------------------------------------------
// getUserVisibleLines (stub - no scroll info in Compose)
// ---------------------------------------------------------------------------

internal fun getUserVisibleLines(cm: VimEditor): Pair<Int, Int> {
    // Without scroll info, return the full document range
    return cm.firstLine() to cm.lastLine()
}

// ---------------------------------------------------------------------------
// getMarkPos
// ---------------------------------------------------------------------------

internal fun getMarkPos(cm: VimEditor, vim: VimState, markName: String): LinePos? {
    if (markName == "'" || markName == "`") {
        return cm.vimContext.jumpList.find(cm, -1) ?: LinePos(0, 0)
    } else if (markName == ".") {
        return getLastEditPos(cm)
    }
    val mark = vim.marks[markName]
    return mark?.find()
}

internal fun getLastEditPos(cm: VimEditor): LinePos? {
    return cm.getLastEditEnd()
}

// ---------------------------------------------------------------------------
// showConfirm / showPrompt (adapted for non-DOM environment)
// ---------------------------------------------------------------------------

internal fun showConfirm(
    cm: VimEditor,
    template: String,
    long: Boolean = false,
    duration: Int? = null
) {
    if (long) {
        if (cm.closeVimNotification != null) {
            cm.closeVimNotification!!.invoke()
        }
        val msg = "$template\nPress ENTER or type command to continue"
        cm.closeVimNotification = cm.openNotification(
            msg,
            mapOf("bottom" to true, "duration" to 0)
        )
    } else {
        cm.openNotification(
            template,
            mapOf("bottom" to true, "duration" to (duration ?: 15000))
        )
    }
}

internal fun showPrompt(cm: VimEditor, options: PromptOptions) {
    if (cm.openDialogFn != null) {
        // In Compose/UI mode, prompts are handled through the dialog interface
        cm.openDialog(
            options.prefix,
            { value -> options.onClose?.invoke(value) },
            mapOf(
                "bottom" to true,
                "selectValueOnOpen" to false,
                "value" to (options.value ?: "")
            )
        )
    } else {
        // In test/headless mode, use the virtual prompt mechanism so that
        // subsequent handleKey calls are routed to sendKeyToPrompt.
        cm.vimContext.virtualPrompt = options
    }
}

// ---------------------------------------------------------------------------
// ExCommandDispatcher
// ---------------------------------------------------------------------------

internal class ExCommandDispatcher {
    var commandMap: MutableMap<String, ExCommandDefinition> = mutableMapOf()

    init {
        buildCommandMap()
    }

    fun processCommand(cm: VimEditor, input: String, optParams: ExParams? = null) {
        cm.operation {
            val op = cm.curOp
            if (op != null) op.isVimOp = true
            processCommandInternal(cm, input, optParams)
        }
    }

    private fun processCommandInternal(cm: VimEditor, input: String, optParams: ExParams? = null) {
        val vim = cm.vim!!
        val commandHistoryRegister = cm.vimContext.registerController.getRegister(":")
        val previousCommand = commandHistoryRegister.toString()
        commandHistoryRegister.setText(input)
        val params = optParams ?: ExParams()
        params.input = input
        try {
            parseInput(cm, input, params)
        } catch (e: Exception) {
            showConfirm(cm, e.toString())
            return
        }
        if (vim.visualMode) {
            exitVisualMode(cm)
        }
        var command: ExCommandDefinition? = null
        var commandName: String? = null
        if (params.commandName.isEmpty()) {
            if (params.hasLineRange) {
                commandName = "move"
            }
        } else {
            command = matchCommand(params.commandName)
            if (command != null) {
                commandName = command.name
                if (command.excludeFromCommandHistory == true) {
                    commandHistoryRegister.setText(previousCommand)
                }
                if (command.type == "exToKey") {
                    doKeyToKey(cm, command.toKeys ?: "")
                    return
                } else if (command.type == "exToEx") {
                    processCommand(cm, command.toInput ?: "")
                    return
                }
            }
        }
        if (commandName == null) {
            showConfirm(cm, "Not an editor command \":$input\"")
            return
        }
        try {
            val fn = exCommands[commandName]
            if (fn != null) {
                fn(cm, params)
            } else {
                showConfirm(cm, "Not an editor command \":$input\"")
            }
            if (command?.possiblyAsync != true) {
                params.callback?.invoke()
            }
        } catch (e: Exception) {
            showConfirm(cm, e.toString())
        }
    }

    internal fun parseInput(cm: VimEditor, input: String, result: ExParams) {
        var pos = 0
        // Skip leading colons
        while (pos < input.length && input[pos] == ':') pos++

        if (pos < input.length && input[pos] == '%') {
            result.line = cm.firstLine()
            result.lineEnd = cm.lastLine()
            result.hasLineRange = true
            pos++
        } else if (pos < input.length && input[pos] == '*') {
            val lastSelection = cm.vim?.lastSelection
            val anchor = lastSelection?.anchorMark?.find()?.line ?: 0
            val head = lastSelection?.headMark?.find()?.line ?: 0
            result.line = max(anchor, head)
            result.lineEnd = min(anchor, head)
            result.hasLineRange = true
            pos++
        } else {
            val lineSpec = parseLineSpec(cm, input, pos)
            result.line = lineSpec.first ?: 0
            pos = lineSpec.second
            if (lineSpec.first != null) {
                result.hasLineRange = true
            }
            if (pos < input.length && input[pos] == ',') {
                pos++
                val lineEnd = parseLineSpec(cm, input, pos)
                result.lineEnd = lineEnd.first
                result.hasLineRange = true
                pos = lineEnd.second
            }
        }

        if (!result.hasLineRange) {
            if (cm.vim?.visualMode == true) {
                result.selectionLine = getMarkPos(cm, cm.vim!!, "<")?.line ?: 0
                result.selectionLineEnd = getMarkPos(cm, cm.vim!!, ">")?.line
            } else {
                result.selectionLine = cm.getCursor().line
            }
        } else {
            result.selectionLine = result.line
            result.selectionLineEnd = result.lineEnd
        }

        // Skip whitespace
        while (pos < input.length && input[pos].isWhitespace()) pos++

        // Parse command name
        val commandMatch = Regex("^(\\w+|!!|@@|[!#&*<=>@~])").find(input.substring(pos))
        if (commandMatch != null) {
            result.commandName = commandMatch.groupValues[1]
            pos += commandMatch.value.length
        } else {
            val rest = input.substring(pos)
            result.commandName = rest
            pos = input.length
        }

        // Parse args
        if (pos < input.length) {
            result.argString = input.substring(pos)
            val command = matchCommand(result.commandName)
            val delim = command?.argDelimiter ?: "\\s+"
            val args = result.argString.trim().split(Regex(delim))
            if (args.isNotEmpty() && args[0].isNotEmpty()) {
                result.args = args.toMutableList()
            }
        }
    }

    private fun parseLineSpec(cm: VimEditor, input: String, startPos: Int): Pair<Int?, Int> {
        var pos = startPos
        if (pos >= input.length) return null to pos

        // Parse base address
        var baseLine: Int? = null

        val numberMatch = Regex("^([\\d]+)").find(input.substring(pos))
        if (numberMatch != null) {
            pos += numberMatch.value.length
            baseLine = numberMatch.groupValues[1].toInt() - 1
        } else {
            when {
                pos < input.length && input[pos] == '.' -> {
                    pos++
                    baseLine = cm.getCursor().line
                }
                pos < input.length && input[pos] == '$' -> {
                    pos++
                    baseLine = cm.lastLine()
                }
                pos < input.length && input[pos] == '\'' -> {
                    pos++
                    val markName = if (pos < input.length) input[pos].toString() else ""
                    pos++
                    val markPos = getMarkPos(cm, cm.vim!!, markName)
                        ?: throw Error("Mark not set")
                    baseLine = markPos.line
                }
                pos < input.length && (input[pos] == '+' || input[pos] == '-') -> {
                    // Offset relative to current line (no explicit base)
                    baseLine = cm.getCursor().line
                }
                else -> return null to pos
            }
        }

        // Parse optional offset: +N, -N, or bare N (implicit +)
        while (pos < input.length && (
                input[pos] == '+' || input[pos] == '-' ||
                    input[pos].isDigit()
                )
        ) {
            val sign = when {
                input[pos] == '+' -> {
                    pos++
                    1
                }
                input[pos] == '-' -> {
                    pos++
                    -1
                }
                else -> 1 // bare number = implicit +
            }
            val offsetMatch = Regex("^([\\d]+)").find(input.substring(pos))
            val offset = if (offsetMatch != null) {
                pos += offsetMatch.value.length
                offsetMatch.groupValues[1].toInt()
            } else {
                1 // bare + or - without number means +1 or -1
            }
            baseLine = (baseLine ?: 0) + sign * offset
        }

        return baseLine to pos
    }

    fun matchCommand(commandName: String): ExCommandDefinition? {
        for (i in commandName.length downTo 1) {
            val prefix = commandName.substring(0, i)
            val cmd = commandMap[prefix]
            if (cmd != null && cmd.name.startsWith(commandName)) {
                return cmd
            }
        }
        return null
    }

    fun buildCommandMap() {
        commandMap = mutableMapOf()
        for (command in defaultExCommandMap) {
            val key = command.shortName ?: command.name
            commandMap[key] = command
        }
    }

    fun map(lhs: String, rhs: String, ctx: String? = null, noremap: Boolean = false) {
        if (lhs != ":" && lhs.startsWith(":")) {
            if (ctx != null) throw Error("Mode not supported for ex mappings")
            val commandName = lhs.substring(1)
            if (rhs != ":" && rhs.startsWith(":")) {
                commandMap[commandName] = ExCommandDefinition(
                    name = commandName,
                    type = "exToEx",
                    toInput = rhs.substring(1),
                    user = true
                )
            } else {
                commandMap[commandName] = ExCommandDefinition(
                    name = commandName,
                    type = "exToKey",
                    toKeys = rhs,
                    user = true
                )
            }
        } else {
            val mapping = KeyToKeyCommand(
                keys = lhs,
                toKeys = rhs,
                context = ctx,
                noremap = noremap
            )
            mapCommand(mapping)
        }
    }

    fun unmap(lhs: String, ctx: String? = null): Boolean {
        if (lhs != ":" && lhs.startsWith(":")) {
            if (ctx != null) throw Error("Mode not supported for ex mappings")
            val commandName = lhs.substring(1)
            if (commandMap[commandName]?.user == true) {
                commandMap.remove(commandName)
                return true
            }
        } else {
            for (i in defaultKeymap.indices.reversed()) {
                if (lhs == defaultKeymap[i].keys && defaultKeymap[i].context == ctx) {
                    defaultKeymap.removeAt(i)
                    return true
                }
            }
        }
        return false
    }
}

internal fun mapCommand(command: VimKeyCommand) {
    defaultKeymap.add(0, command)
}

// Global ex command dispatcher
internal val exCommandDispatcher = ExCommandDispatcher()

// ---------------------------------------------------------------------------
// Macro support
// ---------------------------------------------------------------------------

internal fun executeMacroRegister(
    cm: VimEditor,
    vim: VimState,
    macroModeState: MacroModeState,
    registerName: String
) {
    val register = cm.vimContext.registerController.getRegister(registerName)
    if (registerName == ":") {
        if (register.keyBuffer.isNotEmpty()) {
            exCommandDispatcher.processCommand(cm, register.keyBuffer[0])
        }
        macroModeState.isPlaying = false
        return
    }
    val keyBuffer = register.keyBuffer
    var imc = 0
    macroModeState.isPlaying = true
    macroModeState.replaySearchQueries = register.searchQueries.toMutableList()
    for (i in keyBuffer.indices) {
        val text = keyBuffer[i]
        val keyRe = Regex("<(?:[CSMA]-)*\\w+>|.", RegexOption.IGNORE_CASE)
        for (match in keyRe.findAll(text)) {
            val key = match.value
            vimApi.handleKey(cm, key, "macro")
            if (vim.insertMode) {
                val changes = register.insertModeChanges.getOrNull(imc)?.changes ?: mutableListOf()
                imc++
                macroModeState.lastInsertModeChanges.changes.clear()
                macroModeState.lastInsertModeChanges.changes.addAll(changes)
                repeatInsertModeChanges(cm, changes, 1)
                exitInsertMode(cm)
            }
        }
    }
    macroModeState.isPlaying = false
}

internal fun logKey(cm: VimEditor, macroModeState: MacroModeState, key: String) {
    if (macroModeState.isPlaying) return
    val registerName = macroModeState.latestRegister ?: return
    val register = cm.vimContext.registerController.getRegister(registerName)
    register.pushText(key)
}

internal fun logInsertModeChange(cm: VimEditor, macroModeState: MacroModeState) {
    if (macroModeState.isPlaying) return
    val registerName = macroModeState.latestRegister ?: return
    val register = cm.vimContext.registerController.getRegister(registerName)
    register.pushInsertModeChanges(macroModeState.lastInsertModeChanges)
}

internal fun logSearchQuery(cm: VimEditor, macroModeState: MacroModeState, query: String) {
    if (macroModeState.isPlaying) return
    val registerName = macroModeState.latestRegister ?: return
    val register = cm.vimContext.registerController.getRegister(registerName)
    register.pushSearchQuery(query)
}

// ---------------------------------------------------------------------------
// Insert mode tracking
// ---------------------------------------------------------------------------

internal fun onChange(cm: VimEditor, changeObj: Change?) {
    // Only capture document changes that happen while editing in insert mode.
    // Normal-mode edits (e.g. p, x, dd) are replayed by re-running the command
    // on dot-repeat, not by replaying inserted text, so recording them here
    // would double-apply on `.`.
    if (cm.vim?.insertMode != true) return
    val macroModeState = cm.vimContext.macroModeState
    val lastChange = macroModeState.lastInsertModeChanges
    if (!macroModeState.isPlaying) {
        val vim = cm.vim
        var current = changeObj
        while (current != null) {
            lastChange.expectCursorActivityForChange = true
            if ((lastChange.ignoreCount ?: 0) > 1) {
                lastChange.ignoreCount = (lastChange.ignoreCount ?: 0) - 1
            } else {
                val selectionCount = cm.listSelections().size
                if (selectionCount > 1) {
                    lastChange.ignoreCount = selectionCount
                }
                val text = current.text.joinToString("\n")
                if (lastChange.maybeReset == true) {
                    lastChange.changes.clear()
                    lastChange.maybeReset = false
                }
                if (text.isNotEmpty()) {
                    if (cm.vim!!.overwrite && !text.contains('\n')) {
                        lastChange.changes.add(listOf(text))
                    } else {
                        if (text.length > 1) {
                            val insertEnd = vim?.insertEnd?.find()
                            val cursor = cm.getCursor()
                            if (insertEnd != null && insertEnd.line == cursor.line) {
                                val offset = insertEnd.ch - cursor.ch
                                if (offset > 0 && offset < text.length) {
                                    lastChange.changes.add(listOf(text, offset.toString()))
                                    current = current.next
                                    continue
                                }
                            }
                        }
                        lastChange.changes.add(text)
                    }
                }
            }
            current = current.next
        }
    }
}

internal fun onCursorActivity(cm: VimEditor) {
    val vim = cm.vim ?: return
    if (vim.insertMode) {
        val macroModeState = cm.vimContext.macroModeState
        if (macroModeState.isPlaying) return
        val lastChange = macroModeState.lastInsertModeChanges
        if (lastChange.expectCursorActivityForChange) {
            lastChange.expectCursorActivityForChange = false
        } else {
            lastChange.maybeReset = true
            if (vim.insertEnd != null) vim.insertEnd!!.clear()
            vim.insertEnd = cm.setBookmark(
                cm.getCursor(),
                BookmarkOptions(insertLeft = true)
            )
        }
    } else if (cm.curOp?.isVimOp != true) {
        handleExternalSelection(cm, vim)
    }
}

internal fun handleExternalSelection(cm: VimEditor, vim: VimState) {
    val anchor = cm.getCursor("anchor")
    val head = cm.getCursor("head")
    if (vim.visualMode && !cm.somethingSelected()) {
        exitVisualMode(cm, false)
    } else if (!vim.visualMode && !vim.insertMode && cm.somethingSelected()) {
        vim.visualMode = true
        vim.visualLine = false
        cm.signal("vim-mode-change", mapOf("mode" to "visual"))
    }
    if (vim.visualMode) {
        val headOffset = if (!cursorIsBefore(head, anchor)) -1 else 0
        val anchorOffset = if (cursorIsBefore(head, anchor)) -1 else 0
        val adjustedHead = offsetCursor(head, 0, headOffset)
        val adjustedAnchor = offsetCursor(anchor, 0, anchorOffset)
        vim.sel = LinePosRange(adjustedAnchor, adjustedHead)
        updateMark(cm, vim, "<", cursorMin(adjustedHead, adjustedAnchor))
        updateMark(cm, vim, ">", cursorMax(adjustedHead, adjustedAnchor))
    } else if (!vim.insertMode) {
        vim.lastHPos = cm.getCursor().ch
    }
}

// ---------------------------------------------------------------------------
// exitInsertMode
// ---------------------------------------------------------------------------

internal fun exitInsertMode(cm: VimEditor, keepCursor: Boolean = false) {
    val vim = cm.vim ?: return
    val macroModeState = cm.vimContext.macroModeState
    val insertModeChangeRegister = cm.vimContext.registerController.getRegister(".")
    val isPlaying = macroModeState.isPlaying
    val lastChange = macroModeState.lastInsertModeChanges
    if (!isPlaying) {
        if (vim.insertEnd != null) vim.insertEnd!!.clear()
        vim.insertEnd = null
    }
    if (!isPlaying && vim.insertModeRepeat != null && (vim.insertModeRepeat ?: 0) > 1) {
        repeatLastEdit(cm, vim, (vim.insertModeRepeat ?: 1) - 1, true)
        vim.lastEditInputState?.repeatOverride = vim.insertModeRepeat
    }
    vim.insertModeRepeat = null
    vim.insertMode = false
    if (!keepCursor) {
        cm.setCursor(cm.getCursor().line, cm.getCursor().ch - 1)
    }
    cm.setOption("keyMap", "vim")
    cm.toggleOverwrite(false)
    // Update the "." register
    insertModeChangeRegister.setText(lastChange.changes.joinToString(""))
    cm.signal("vim-mode-change", mapOf("mode" to "normal"))
    if (macroModeState.isRecording) {
        logInsertModeChange(cm, macroModeState)
    }
}

// ---------------------------------------------------------------------------
// repeatLastEdit
// ---------------------------------------------------------------------------

internal fun repeatLastEdit(cm: VimEditor, vim: VimState, repeat: Int, repeatForInsert: Boolean) {
    val macroModeState = cm.vimContext.macroModeState
    macroModeState.isPlaying = true
    val lastAction = vim.lastEditActionCommand
    val cachedInputState = vim.inputState

    fun repeatCommand() {
        if (lastAction != null) {
            commandDispatcher.processAction(cm, vim, lastAction)
        } else {
            commandDispatcher.evalInput(cm, vim)
        }
    }

    fun repeatInsert(rpt: Int) {
        if (macroModeState.lastInsertModeChanges.changes.isNotEmpty()) {
            val effectiveRepeat = if (vim.lastEditActionCommand == null) 1 else rpt
            val changeObject = macroModeState.lastInsertModeChanges
            repeatInsertModeChanges(cm, changeObject.changes, effectiveRepeat)
        }
    }

    vim.inputState = vim.lastEditInputState ?: InputState()
    if (lastAction != null && lastAction.interlaceInsertRepeat == true) {
        for (i in 0 until repeat) {
            repeatCommand()
            repeatInsert(1)
        }
    } else {
        if (!repeatForInsert) {
            repeatCommand()
        }
        repeatInsert(repeat)
    }
    vim.inputState = cachedInputState
    if (vim.insertMode && !repeatForInsert) {
        exitInsertMode(cm)
    }
    macroModeState.isPlaying = false
}

internal fun sendCmKey(cm: VimEditor, key: String) {
    when (key) {
        "Backspace" -> {
            val cur = cm.getCursor()
            if (cur.ch > 0) {
                cm.replaceRange("", LinePos(cur.line, cur.ch - 1), cur)
            } else if (cur.line > 0) {
                val prevLine = cur.line - 1
                val prevLineLen = cm.getLine(prevLine).length
                cm.replaceRange("", LinePos(prevLine, prevLineLen), cur)
            }
        }
        "Delete" -> {
            val cur = cm.getCursor()
            val line = cm.getLine(cur.line)
            if (cur.ch < line.length) {
                cm.replaceRange("", cur, LinePos(cur.line, cur.ch + 1))
            } else if (cur.line < cm.lastLine()) {
                cm.replaceRange("", cur, LinePos(cur.line + 1, 0))
            }
        }
    }
}

internal fun repeatInsertModeChanges(cm: VimEditor, changes: MutableList<Any>, repeat: Int) {
    val head = cm.getCursor("head")
    val visualBlock = cm.vimContext.macroModeState.lastInsertModeChanges.visualBlock
    val hasVisualBlock = visualBlock != null && visualBlock != 0
    var rpt = repeat
    if (hasVisualBlock) {
        selectForInsert(cm, head, visualBlock!! + 1)
        rpt = cm.listSelections().size
        cm.setCursor(head)
    }
    for (i in 0 until rpt) {
        if (hasVisualBlock) {
            cm.setCursor(offsetCursor(head, i, 0))
        }
        for (change in changes) {
            when (change) {
                is InsertModeKey -> sendCmKey(cm, change.keyName)
                is String -> cm.replaceSelection(change)
                is List<*> -> {
                    val text = change[0] as? String ?: continue
                    val offset = (change.getOrNull(1) as? String)?.toIntOrNull()
                    val start = cm.getCursor()
                    val end = offsetCursor(start, 0, text.length - (offset ?: 0))
                    cm.replaceRange(text, start, if (offset != null) start else end)
                    cm.setCursor(end)
                }
            }
        }
    }
    if (hasVisualBlock) {
        cm.setCursor(offsetCursor(head, 0, 1))
    }
}

// ---------------------------------------------------------------------------
// clearInputState
// ---------------------------------------------------------------------------

internal fun clearInputState(cm: VimEditor, reason: String? = null) {
    val vim = cm.vim ?: return
    // Create a new InputState rather than resetting the existing one.
    // The old inputState may still be referenced by vim.lastEditInputState.
    vim.inputState = InputState()
    vim.expectLiteralNext = false
    vim.status = ""
    cm.signal("vim-command-done", reason)
}

// ---------------------------------------------------------------------------
// doReplace (substitute helper)
// ---------------------------------------------------------------------------

internal fun doReplace(
    cm: VimEditor,
    confirm: Boolean,
    global: Boolean,
    lineStart: Int,
    lineEnd: Int,
    searchCursor: VimSearchCursor,
    query: Regex,
    replaceWith: String,
    callback: (() -> Unit)?
) {
    cm.vim!!.exMode = true
    var done = false
    var matches = 0
    var lastPos: LinePos? = null
    var modifiedLineNumber = -1
    var joined = false
    var adjustedLineEnd = lineEnd

    fun replace() {
        var newText: String
        val match = searchCursor.match
        if (match != null) {
            newText = Regex("\\$(?:\\d{1,3}|[\$&])").replace(replaceWith) { mr ->
                val x = mr.value.substring(1)
                if (x == "\$") {
                    "\$"
                } else if (x == "&") {
                    match[0] ?: ""
                } else {
                    var x1 = x
                    while (x1.isNotEmpty() &&
                        (x1.toIntOrNull() ?: 0) >= match.size
                    ) {
                        x1 = x1.dropLast(1)
                    }
                    if (x1.isNotEmpty()) {
                        (match.getOrNull(x1.toInt()) ?: "") + x.substring(x1.length)
                    } else {
                        mr.value
                    }
                }
            }
        } else {
            val text = cm.getRange(searchCursor.from()!!, searchCursor.to()!!)
            newText = query.replace(text, replaceWith)
        }
        val unmodifiedLineNumber = searchCursor.to()!!.line
        searchCursor.replace(newText)
        modifiedLineNumber = searchCursor.to()!!.line
        if (adjustedLineEnd != Int.MAX_VALUE) {
            adjustedLineEnd += modifiedLineNumber - unmodifiedLineNumber
        }
        joined = modifiedLineNumber < unmodifiedLineNumber
    }

    fun findNextValidMatch(): List<String?>? {
        val lastMatchTo = if (lastPos != null) searchCursor.to() else null
        val match = searchCursor.findNext()
        if (match != null && match.isNotEmpty() && match[0]?.isEmpty() == true &&
            lastMatchTo != null && searchCursor.from() != null &&
            cursorEqual(searchCursor.from()!!, lastMatchTo)
        ) {
            return searchCursor.findNext()
        }
        if (match != null) matches++
        return match
    }

    fun next() {
        while (true) {
            val found = findNextValidMatch() ?: break
            val from = searchCursor.from() ?: break
            if (!isInRange(from, lineStart, adjustedLineEnd)) break
            if (!global && from.line == modifiedLineNumber && !joined) continue
            lastPos = from
            done = false
            return
        }
        done = true
    }

    fun stop() {
        if (cm.vimContext.virtualPrompt != null) {
            cm.vimContext.virtualPrompt = null
        }
        cm.focus()
        val pos = lastPos
        if (pos != null) {
            cm.setCursor(pos)
            val vim = cm.vim!!
            vim.exMode = false
            vim.lastHPos = pos.ch
            vim.lastHSPos = pos.ch
        }
        if (callback != null) {
            callback()
        } else if (done) {
            val msg = if (matches > 0) "Found $matches matches" else "No matches found"
            showConfirm(
                cm,
                "$msg for pattern: ${query.pattern}" +
                    if (getOption("pcre") == true) " (set nopcre to use Vim regexps)" else ""
            )
        }
    }

    fun replaceAll() {
        cm.operation {
            while (!done) {
                replace()
                next()
            }
            stop()
        }
    }

    next()
    if (done) {
        showConfirm(
            cm,
            "No matches for ${query.pattern}" +
                if (getOption("pcre") == true) " (set nopcre to use vim regexps)" else ""
        )
        return
    }
    if (!confirm) {
        replaceAll()
        callback?.invoke()
        return
    }
    // Confirm mode: use prompt with key handler for y/n/a/q/l
    var localCallback = callback
    fun onPromptKeyDown(event: VimKeyEvent): Boolean {
        val keyName = event.key
        when (keyName) {
            "y" -> {
                replace()
                next()
            }
            "n" -> {
                next()
            }
            "a" -> {
                // replaceAll contains a call to stop of its own. We don't want
                // it to fire too early or multiple times.
                val savedCallback = localCallback
                localCallback = null
                cm.operation { replaceAll() }
                localCallback = savedCallback
            }
            "l" -> {
                replace()
                stop()
            }
            "q", "Escape" -> {
                stop()
            }
            else -> return true // swallow all other keys
        }
        if (done) {
            stop()
        }
        return true
    }
    showPrompt(
        cm,
        PromptOptions(
            prefix = "replace with $replaceWith (y/n/a/q/l)",
            onClose = { stop() },
            onKeyDown = ::onPromptKeyDown
        )
    )
}

// ---------------------------------------------------------------------------
// Langmap support
// ---------------------------------------------------------------------------

internal data class Langmap(
    val keymap: Map<String, String>,
    val string: String,
    var remapCtrl: Boolean? = null
)

internal var langmap: Langmap = parseLangmap("")

internal fun parseLangmap(langmapString: String): Langmap {
    val keymap = mutableMapOf<String, String>()
    if (langmapString.isEmpty()) return Langmap(keymap = keymap, string = "")

    fun getEscaped(list: String): List<String> =
        Regex("\\\\?(.)").findAll(list).mapNotNull { it.groupValues.getOrNull(1) }
            .filter { it.isNotEmpty() }.toList()

    Regex("((?:[^\\\\,]|\\\\.)+),?").findAll(langmapString).forEach { partMatch ->
        val part = partMatch.groupValues[1]
        if (part.isEmpty()) return@forEach
        val semicolonParts = Regex("((?:[^\\\\;]|\\\\.)+);").findAll(part).toList()
        if (semicolonParts.size == 1) {
            val fromStr = semicolonParts[0].groupValues[1]
            val remainder = part.substring(semicolonParts[0].range.last + 1)
            val from = getEscaped(fromStr)
            val to = getEscaped(remainder)
            if (from.size == to.size) {
                for (i in from.indices) {
                    keymap[from[i]] = to[i]
                }
            }
        } else {
            val pairs = getEscaped(part)
            if (pairs.size % 2 == 0) {
                var i = 0
                while (i < pairs.size) {
                    keymap[pairs[i]] = pairs[i + 1]
                    i += 2
                }
            }
        }
    }
    return Langmap(keymap = keymap, string = langmapString)
}

internal fun updateLangmap(langmapString: String, remapCtrl: Boolean? = null) {
    if (langmap.string != langmapString) {
        langmap = parseLangmap(langmapString)
    }
    langmap.remapCtrl = remapCtrl
}

// ---------------------------------------------------------------------------
// HistoryController (for search and ex command history)
// ---------------------------------------------------------------------------

internal class HistoryController {
    val historyBuffer: MutableList<String> = mutableListOf()
    var iterator: Int = 0
    var initialPrefix: String? = null

    fun nextMatch(input: String, up: Boolean): String? {
        val dir = if (up) -1 else 1
        if (initialPrefix == null) initialPrefix = input
        val prefix = initialPrefix!!
        var i = iterator + dir
        while (if (up) i >= 0 else i < historyBuffer.size) {
            val element = historyBuffer[i]
            for (j in 0..element.length) {
                if (prefix == element.substring(0, j)) {
                    iterator = i
                    return element
                }
            }
            i += dir
        }
        if (i >= historyBuffer.size) {
            iterator = historyBuffer.size
            return initialPrefix
        }
        if (i < 0) return input
        return null
    }

    fun pushInput(input: String) {
        val index = historyBuffer.indexOf(input)
        if (index > -1) historyBuffer.removeAt(index)
        if (input.isNotEmpty()) historyBuffer.add(input)
    }

    fun reset() {
        initialPrefix = null
        iterator = historyBuffer.size
    }
}

// ---------------------------------------------------------------------------
// defineRegister
// ---------------------------------------------------------------------------

internal fun defineRegister(name: String, register: Register) {
    val registers = vimGlobalState.registerController.registers
    if (name.length != 1) {
        error("Register name must be 1 character")
    }
    if (registers.containsKey(name)) {
        error("Register already defined $name")
    }
    registers[name] = register
    validRegisters.add(name)
}

// ---------------------------------------------------------------------------
// vimKeyFromEvent (keyboard event to vim key string)
// ---------------------------------------------------------------------------

private val ignoredKeys = setOf(
    "Shift",
    "Alt",
    "Command",
    "Control",
    "CapsLock",
    "AltGraph",
    "Dead",
    "Unidentified"
)

internal fun vimKeyFromEvent(
    key: String,
    ctrlKey: Boolean = false,
    altKey: Boolean = false,
    metaKey: Boolean = false,
    shiftKey: Boolean = false,
    code: String? = null,
    vim: VimState? = null
): String? {
    var effectiveKey = key
    if (ignoredKeys.contains(effectiveKey)) return null
    if (effectiveKey.length > 1 && effectiveKey.startsWith("n")) {
        effectiveKey = effectiveKey.replace("Numpad", "")
    }
    effectiveKey = specialKeyMap[effectiveKey] ?: effectiveKey

    var name = ""
    if (ctrlKey) name += "C-"
    if (altKey) name += "A-"
    if (metaKey) name += "M-"
    if ((name.isNotEmpty() || effectiveKey.length > 1) && shiftKey) name += "S-"

    if (vim != null && !vim.expectLiteralNext && effectiveKey.length == 1) {
        val lm = langmap
        if (lm.keymap.isNotEmpty() && effectiveKey in lm.keymap) {
            if (lm.remapCtrl != false || name.isEmpty()) {
                effectiveKey = lm.keymap[effectiveKey] ?: effectiveKey
            }
        }
    }

    name += effectiveKey
    if (name.length > 1) name = "<$name>"
    return name
}

private val specialKeyMap = mapOf(
    "Return" to "CR",
    "Backspace" to "BS",
    "Delete" to "Del",
    "Escape" to "Esc",
    "Insert" to "Ins",
    "ArrowLeft" to "Left",
    "ArrowRight" to "Right",
    "ArrowUp" to "Up",
    "ArrowDown" to "Down",
    "Enter" to "CR",
    " " to "Space"
)

// ---------------------------------------------------------------------------
// Global state
// ---------------------------------------------------------------------------

internal class VimGlobalState {
    val jumpList = CircularJumpList()
    var registerController = RegisterController()
    var macroModeState = MacroModeState()
    var lastCharacterSearch = LastCharacterSearch()
    var lastSubstituteReplacePart: String? = null
    var query: Regex? = null
    var isReversed: Boolean = false
    val searchHistoryController = HistoryController()
    val exCommandHistoryController = HistoryController()
}

internal class CircularJumpList {
    private val size = 100
    private var pointer = -1
    private var head = 0
    private var tail = 0
    private val buffer = arrayOfNulls<Marker>(size)
    var cachedCursor: LinePos? = null

    fun add(cm: VimEditor, oldCur: LinePos, newCur: LinePos) {
        val current = ((pointer % size) + size) % size
        val curMark = buffer[current]

        fun useNextSlot(cursor: LinePos) {
            pointer++
            val next = ((pointer % size) + size) % size
            buffer[next]?.clear()
            buffer[next] = cm.setBookmark(cursor)
        }

        if (curMark != null) {
            val markPos = curMark.find()
            if (markPos != null && !cursorEqual(markPos, oldCur)) {
                useNextSlot(oldCur)
            }
        } else {
            useNextSlot(oldCur)
        }
        useNextSlot(newCur)
        head = pointer
        tail = pointer - size + 1
        if (tail < 0) tail = 0
    }

    fun clear() {
        pointer = -1
        head = 0
        tail = 0
        buffer.fill(null)
        cachedCursor = null
    }

    fun move(cm: VimEditor, offset: Int): Marker? {
        pointer += offset
        if (pointer > head) {
            pointer = head
        } else if (pointer < tail) pointer = tail
        var mark = buffer[((size + pointer) % size + size) % size]
        if (mark != null && mark.find() == null) {
            val inc = if (offset > 0) 1 else -1
            val oldCur = cm.getCursor()
            do {
                pointer += inc
                mark = buffer[((size + pointer) % size + size) % size]
                val newCur = mark?.find()
                if (mark != null && newCur != null && !cursorEqual(oldCur, newCur)) break
            } while (pointer < head && pointer > tail)
        }
        return mark
    }

    fun find(cm: VimEditor, offset: Int): LinePos? {
        val oldPointer = pointer
        val mark = move(cm, offset)
        pointer = oldPointer
        return mark?.find()
    }
}

internal class RegisterController {
    val registers: MutableMap<String, Register> = mutableMapOf()
    val unnamedRegister: Register

    init {
        unnamedRegister = Register()
        registers["\""] = unnamedRegister
        registers["."] = Register()
        registers[":"] = Register()
        registers["/"] = Register()
        registers["+"] = Register()
    }

    fun isValidRegister(name: String?): Boolean {
        if (name == null) return false
        return validRegisters.contains(name) || latinCharRegex.matches(name)
    }

    fun getRegister(name: String?): Register {
        if (!isValidRegister(name)) return unnamedRegister
        val effectiveName = name!!.lowercase()
        return registers.getOrPut(effectiveName) { Register() }
    }

    fun pushText(
        registerName: String?,
        operator: String,
        text: String,
        linewise: Boolean,
        blockwise: Boolean = false
    ) {
        // The black hole register, "_, means delete/yank to nowhere.
        if (registerName == "_") return
        var effectiveText = text
        if (linewise && effectiveText.isNotEmpty() && effectiveText.last() != '\n') {
            effectiveText += "\n"
        }
        // Lowercase and uppercase registers refer to the same register.
        // Uppercase just means append.
        val register = if (isValidRegister(registerName)) {
            getRegister(registerName)
        } else {
            null
        }
        if (register == null || registerName == null) {
            when (operator) {
                "yank" -> {
                    registers["0"] = Register(effectiveText, linewise, blockwise)
                }
                "delete", "change" -> {
                    if (!effectiveText.contains('\n')) {
                        registers["-"] = Register(effectiveText, linewise)
                    } else {
                        shiftNumericRegisters()
                        registers["1"] = Register(effectiveText, linewise)
                    }
                }
            }
            unnamedRegister.setText(effectiveText, linewise, blockwise)
            return
        }
        val append = isUpperCase(registerName)
        if (append) {
            register.pushText(effectiveText, linewise)
        } else {
            register.setText(effectiveText, linewise, blockwise)
        }
        unnamedRegister.setText(register.toString(), linewise)
    }

    private fun shiftNumericRegisters() {
        for (i in 9 downTo 2) {
            registers[i.toString()] = getRegister((i - 1).toString())
        }
    }
}

internal class Register(
    text: String? = null,
    linewise: Boolean = false,
    blockwise: Boolean = false
) {
    var linewise: Boolean = linewise
    var blockwise: Boolean = blockwise
    val keyBuffer: MutableList<String> = mutableListOf(text ?: "")
    val insertModeChanges: MutableList<InsertModeChanges> = mutableListOf()
    val searchQueries: MutableList<String> = mutableListOf()

    fun setText(text: String?, lw: Boolean = false, bw: Boolean = false) {
        keyBuffer.clear()
        keyBuffer.add(text ?: "")
        linewise = lw
        blockwise = bw
    }

    fun pushText(text: String, lw: Boolean = false) {
        if (lw) {
            if (!linewise) {
                keyBuffer.add("\n")
            }
            linewise = true
        }
        keyBuffer.add(text)
    }

    fun pushInsertModeChanges(changes: InsertModeChanges) {
        val copy = InsertModeChanges()
        copy.changes.addAll(changes.changes)
        copy.expectCursorActivityForChange = changes.expectCursorActivityForChange
        insertModeChanges.add(copy)
    }

    fun pushSearchQuery(query: String) {
        searchQueries.add(query)
    }

    fun clear() {
        keyBuffer.clear()
        insertModeChanges.clear()
        searchQueries.clear()
        linewise = false
    }

    override fun toString(): String = keyBuffer.joinToString("")
}

internal class MacroModeState {
    var isPlaying: Boolean = false
    var isRecording: Boolean = false
    var latestRegister: String? = null
    var lastInsertModeChanges = InsertModeChanges()
    var replaySearchQueries: MutableList<String> = mutableListOf()

    fun enterMacroRecordMode(cm: VimEditor, registerName: String?) {
        if (isRecording) return
        val register = cm.vimContext.registerController.getRegister(registerName)
        register.clear()
        latestRegister = registerName
        isRecording = true
    }

    fun exitMacroRecordMode() {
        if (!isRecording) return
        isRecording = false
    }
}

internal data class LastCharacterSearch(
    var increment: Int = 0,
    var forward: Boolean = true,
    var selectedCharacter: String = ""
)

/**
 * Legacy global [VimGlobalState] kept for the [defineRegister] public API which
 * has no editor context. New code should use [VimContext] via [vimContextField].
 */
internal val vimGlobalState = VimGlobalState()

internal fun resetVimGlobalState() {
    vimGlobalState.macroModeState = MacroModeState()
    vimGlobalState.registerController = RegisterController()
    vimGlobalState.lastCharacterSearch = LastCharacterSearch()
    vimGlobalState.lastSubstituteReplacePart = null
    vimGlobalState.query = null
    vimGlobalState.isReversed = false
    for ((_, option) in vimOptions) {
        option.value = option.defaultValue
    }
    vimGlobalState.jumpList.clear()
    vimGlobalState.searchHistoryController.historyBuffer.clear()
    vimGlobalState.exCommandHistoryController.historyBuffer.clear()
    vimApi.mapclear()
    exCommandDispatcher.buildCommandMap()
}

/**
 * Reset the per-editor [VimContext] state.
 */
internal fun resetVimContext(cm: VimEditor) {
    val ctx = cm.vimContext
    ctx.macroModeState = MacroModeState()
    ctx.registerController = RegisterController()
    ctx.lastCharacterSearch = LastCharacterSearch()
    ctx.lastSubstituteReplacePart = null
    ctx.query = null
    ctx.isReversed = false
    ctx.jumpList.clear()
    ctx.searchHistoryController.historyBuffer.clear()
    ctx.exCommandHistoryController.historyBuffer.clear()
    ctx.virtualPrompt = null
    ctx.noremap = false
    ctx.keyToKeyStack.clear()
}

// ---------------------------------------------------------------------------
// Command dispatcher forward reference
// ---------------------------------------------------------------------------

internal interface CommandDispatcherInterface {
    fun processAction(cm: VimEditor, vim: VimState, command: ActionCommand)
    fun evalInput(cm: VimEditor, vim: VimState)
    fun processCommand(cm: VimEditor, vim: VimState, command: VimKeyCommand)
}

internal lateinit var commandDispatcher: CommandDispatcherInterface

// ---------------------------------------------------------------------------
// VimApi forward reference
// ---------------------------------------------------------------------------

internal interface VimApiInterface {
    fun handleKey(cm: VimEditor, key: String, origin: String): Boolean
    fun mapclear(ctx: String? = null)
}

internal lateinit var vimApi: VimApiInterface
