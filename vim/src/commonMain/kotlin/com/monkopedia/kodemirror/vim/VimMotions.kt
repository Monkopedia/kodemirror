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

import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.view.moveVertically
import kotlin.math.floor

// ---------------------------------------------------------------------------
// The motions map
// ---------------------------------------------------------------------------

internal val motions: MutableMap<String, MotionFn> = mutableMapOf(
    "moveToTopLine" to { cm, _head, motionArgs, _vim, _inputState ->
        val visible = getUserVisibleLines(cm)
        val line = visible.first + motionArgs.repeat - 1
        MotionResult.from(
            LinePos(line, findFirstNonWhiteSpaceCharacter(cm.getLine(line)))
        )
    },

    "moveToMiddleLine" to { cm, _head, _motionArgs, _vim, _inputState ->
        val visible = getUserVisibleLines(cm)
        val line = floor((visible.first + visible.second) * 0.5).toInt()
        MotionResult.from(
            LinePos(line, findFirstNonWhiteSpaceCharacter(cm.getLine(line)))
        )
    },

    "moveToBottomLine" to { cm, _head, motionArgs, _vim, _inputState ->
        val visible = getUserVisibleLines(cm)
        val line = visible.second - motionArgs.repeat + 1
        MotionResult.from(
            LinePos(line, findFirstNonWhiteSpaceCharacter(cm.getLine(line)))
        )
    },

    "expandToLine" to { _cm, head, motionArgs, _vim, _inputState ->
        // Expands forward to end of line, and then to next line if repeat is >1.
        // Does not handle backward motion!
        val cur = head
        MotionResult.from(LinePos(cur.line + motionArgs.repeat - 1, Int.MAX_VALUE))
    },

    "findNext" to { cm, _head, motionArgs, _vim, _inputState ->
        val state = getSearchState(cm)
        val query = state.getQuery()
        if (query == null) {
            null
        } else {
            var prev = motionArgs.forward != true
            // If search is initiated with ? instead of /, negate direction.
            prev = if (state.isReversed()) !prev else prev
            highlightSearchMatches(cm, query)
            val result = findNext(cm, prev, query, motionArgs.repeat)
            if (result == null) {
                showConfirm(
                    cm,
                    "No match found $query" +
                        if (getOption("pcre") == true) {
                            " (set nopcre to use Vim regexps)"
                        } else {
                            ""
                        }
                )
            }
            result?.let { MotionResult.from(it) }
        }
    },

    "findAndSelectNextInclusive" to { cm, _head, motionArgs, vim, inputState ->
        val state = getSearchState(cm)
        val query = state.getQuery()

        if (query == null) {
            null
        } else {
            var prev = motionArgs.forward != true
            prev = if (state.isReversed()) !prev else prev

            // next: Pair<LinePos, LinePos>? (from, to)
            val next = findNextFromAndToInclusive(cm, prev, query, motionArgs.repeat, vim)

            if (next == null) {
                null
            } else {
                // If there's an operator that will be executed, return the selection.
                if (inputState.operator != null) {
                    MotionResult.from(next.first, next.second)
                } else {
                    val from = next.first
                    // Shrink selection by 1 char so that only the match is selected.
                    val to = LinePos(next.second.line, next.second.ch - 1)

                    if (vim.visualMode) {
                        // If we were in visualLine or visualBlock mode, get out of it.
                        if (vim.visualLine || vim.visualBlock) {
                            vim.visualLine = false
                            vim.visualBlock = false
                            cm.signal(
                                "vim-mode-change",
                                mapOf("mode" to "visual", "subMode" to "")
                            )
                        }

                        val anchor = vim.sel.anchor
                        if (state.isReversed()) {
                            if (motionArgs.forward == true) {
                                MotionResult.from(anchor, from)
                            } else {
                                MotionResult.from(anchor, to)
                            }
                        } else {
                            if (motionArgs.forward == true) {
                                MotionResult.from(anchor, to)
                            } else {
                                MotionResult.from(anchor, from)
                            }
                        }
                    } else {
                        // Turn visual mode on.
                        vim.visualMode = true
                        vim.visualLine = false
                        vim.visualBlock = false
                        cm.signal(
                            "vim-mode-change",
                            mapOf("mode" to "visual", "subMode" to "")
                        )

                        if (prev) {
                            MotionResult.from(to, from)
                        } else {
                            MotionResult.from(from, to)
                        }
                    }
                }
            }
        }
    },

    "goToMark" to { cm, _head, motionArgs, vim, _inputState ->
        val pos = getMarkPos(cm, vim, motionArgs.selectedCharacter ?: "")
        if (pos != null) {
            if (motionArgs.linewise == true) {
                MotionResult.from(
                    LinePos(
                        pos.line,
                        findFirstNonWhiteSpaceCharacter(cm.getLine(pos.line))
                    )
                )
            } else {
                MotionResult.from(pos)
            }
        } else {
            null
        }
    },

    "moveToOtherHighlightedEnd" to { cm, _head, motionArgs, vim, _inputState ->
        val sel = vim.sel
        if (vim.visualBlock && motionArgs.sameLine == true) {
            MotionResult.from(
                clipCursorToContent(cm, LinePos(sel.anchor.line, sel.head.ch)),
                clipCursorToContent(cm, LinePos(sel.head.line, sel.anchor.ch))
            )
        } else {
            MotionResult.from(sel.head, sel.anchor)
        }
    },

    "jumpToMark" to { cm, head, motionArgs, vim, _inputState ->
        var best = head
        for (i in 0 until motionArgs.repeat) {
            val cursor = best
            for ((key, markMarker) in vim.marks) {
                if (!isLowerCase(key)) {
                    continue
                }
                val mark = markMarker.find() ?: continue
                val isWrongDirection = if (motionArgs.forward == true) {
                    cursorIsBefore(mark, cursor)
                } else {
                    cursorIsBefore(cursor, mark)
                }

                if (isWrongDirection) {
                    continue
                }
                if (motionArgs.linewise == true && mark.line == cursor.line) {
                    continue
                }

                val equal = cursorEqual(cursor, best)
                val between = if (motionArgs.forward == true) {
                    cursorIsBetween(cursor, mark, best)
                } else {
                    cursorIsBetween(best, mark, cursor)
                }

                if (equal || between) {
                    best = mark
                }
            }
        }

        if (motionArgs.linewise == true) {
            best = LinePos(
                best.line,
                findFirstNonWhiteSpaceCharacter(cm.getLine(best.line))
            )
        }
        MotionResult.from(best)
    },

    "moveByCharacters" to { _cm, head, motionArgs, _vim, _inputState ->
        val cur = head
        val repeat = motionArgs.repeat
        val ch = if (motionArgs.forward == true) cur.ch + repeat else cur.ch - repeat
        MotionResult.from(LinePos(cur.line, ch))
    },

    "moveByLines" to { cm, head, motionArgs, vim, _inputState ->
        val cur = head
        var endCh = cur.ch
        // Depending what our last motion was, we may want to do different things.
        when (vim.lastMotion) {
            motions["moveByLines"],
            motions["moveByDisplayLines"],
            motions["moveByScroll"],
            motions["moveToColumn"],
            motions["moveToEol"]
            -> {
                endCh = vim.lastHPos
            }

            else -> {
                vim.lastHPos = endCh
            }
        }
        val repeat = motionArgs.repeat + (motionArgs.repeatOffset ?: 0)
        val line = if (motionArgs.forward == true) {
            cur.line + repeat
        } else {
            cur.line - repeat
        }
        val first = cm.firstLine()
        val last = cm.lastLine()
        // Vim go to line begin or line end when cursor at first/last line
        if (line < first && cur.line == first) {
            MotionResult.from(LinePos(head.line, 0))
        } else if (line > last && cur.line == last) {
            MotionResult.from(moveToEol(cm, head, motionArgs, vim, true))
        } else {
            if (motionArgs.toFirstChar == true) {
                endCh = findFirstNonWhiteSpaceCharacter(cm.getLine(line))
                vim.lastHPos = endCh
            }
            vim.lastHSPos = 0 // charCoords not available, use placeholder
            MotionResult.from(LinePos(line, endCh))
        }
    },

    "moveByDisplayLines" to { cm, head, motionArgs, vim, _inputState ->
        // gj/gk move by VISUAL (wrapped) row, not by logical line. Drive the
        // view's wrap-aware vertical motion (the same one cursorLineUp/Down use)
        // so a count moves that many visual rows and the goal column is tracked
        // by moveVertically itself. We deliberately do NOT apply vim's lastHPos
        // sticky-column logic here (that's for the linewise moveByLines).
        //
        // Discoverability: plain j/k remain LINEWISE (moveByLines) — they jump a
        // whole logical line at a time even when it wraps across multiple visual
        // rows. Users who prefer j/k to follow visual rows can remap them with:
        //     Vim.map("j", "gj")
        //     Vim.map("k", "gk")
        when (vim.lastMotion) {
            motions["moveByDisplayLines"],
            motions["moveByScroll"],
            motions["moveByLines"],
            motions["moveToColumn"],
            motions["moveToEol"]
            -> {
                // Keep lastHSPos
            }

            else -> {
                vim.lastHSPos = 0 // charCoords not available
            }
        }
        val forward = motionArgs.forward == true
        val repeat = motionArgs.repeat.coerceAtLeast(1)
        val session = cm.session

        // Start from the current head as a document offset and let the
        // wrap-aware vertical motion walk visual rows.
        var sel = EditorSelection.cursor(cm.indexFromPos(head))
        var movedVisually = false
        var i = 0
        while (i < repeat) {
            val next = moveVertically(session, sel, forward = forward)
            if (next.head == sel.head) {
                // No progress: either we hit the document edge or live layout
                // geometry isn't available. Stop the visual walk; any remaining
                // repeats fall back to logical-line movement below.
                break
            }
            sel = next
            movedVisually = true
            i++
        }

        var current = cm.posFromIndex(sel.head)

        // Graceful fallback: when geometry is unavailable, moveVertically can't
        // move at all (returns the same position). Preserve the previous
        // logical-line behavior for any iterations the visual walk couldn't do.
        if (i < repeat) {
            var fallback = current
            for (j in i until repeat) {
                val newLine = if (forward) fallback.line + 1 else fallback.line - 1
                if (newLine < cm.firstLine() || newLine > cm.lastLine()) break
                fallback = LinePos(newLine, fallback.ch)
            }
            current = fallback
        }

        if (movedVisually && !cursorEqual(current, head)) {
            // Keep lastHPos roughly in sync for any subsequent linewise motion.
            vim.lastHPos = current.ch
        }
        MotionResult.from(current)
    },

    "moveByPage" to { cm, head, motionArgs, _vim, _inputState ->
        val curStart = head
        val repeat = motionArgs.repeat
        // Approximate page as 30 lines
        val pageSize = 30
        val delta = if (motionArgs.forward == true) {
            repeat * pageSize
        } else {
            -repeat * pageSize
        }
        val newLine = (curStart.line + delta).coerceIn(cm.firstLine(), cm.lastLine())
        MotionResult.from(LinePos(newLine, curStart.ch))
    },

    "moveByParagraph" to { cm, head, motionArgs, _vim, _inputState ->
        val dir = if (motionArgs.forward == true) 1 else -1
        MotionResult.from(findParagraph(cm, head, motionArgs.repeat, dir, false).start)
    },

    "moveBySentence" to { cm, head, motionArgs, _vim, _inputState ->
        val dir = if (motionArgs.forward == true) 1 else -1
        MotionResult.from(findSentence(cm, head, motionArgs.repeat, dir))
    },

    "moveByScroll" to { cm, head, motionArgs, vim, _inputState ->
        var repeat = motionArgs.repeat
        if (repeat == 0) {
            // Default to half page
            repeat = 15 // Approximate half page
        }
        val adjustedArgs = motionArgs.copy(repeat = repeat)
        motions["moveByDisplayLines"]?.invoke(cm, head, adjustedArgs, vim, InputState())
    },

    "moveByWords" to { cm, head, motionArgs, _vim, _inputState ->
        val result = moveToWord(
            cm,
            head,
            motionArgs.repeat,
            motionArgs.forward == true,
            motionArgs.wordEnd == true,
            motionArgs.bigWord == true
        )
        result?.let { MotionResult.from(it) }
    },

    "moveTillCharacter" to { cm, head, motionArgs, _vim, _inputState ->
        val repeat = motionArgs.repeat
        val curEnd = moveToCharacter(
            cm,
            repeat,
            motionArgs.forward,
            motionArgs.selectedCharacter,
            head
        )
        val increment = if (motionArgs.forward == true) -1 else 1
        recordLastCharacterSearch(cm, increment, motionArgs)
        if (curEnd == null) {
            null
        } else {
            MotionResult.from(LinePos(curEnd.line, curEnd.ch + increment))
        }
    },

    "moveToCharacter" to { cm, head, motionArgs, _vim, _inputState ->
        val repeat = motionArgs.repeat
        recordLastCharacterSearch(cm, 0, motionArgs)
        val result = moveToCharacter(
            cm,
            repeat,
            motionArgs.forward,
            motionArgs.selectedCharacter,
            head
        ) ?: head
        MotionResult.from(result)
    },

    "moveToSymbol" to { cm, head, motionArgs, _vim, _inputState ->
        val repeat = motionArgs.repeat
        val result = if (motionArgs.selectedCharacter != null) {
            findSymbol(cm, repeat, motionArgs.forward, motionArgs.selectedCharacter!!)
        } else {
            head
        }
        MotionResult.from(result)
    },

    "moveToColumn" to { cm, head, motionArgs, vim, _inputState ->
        val repeat = motionArgs.repeat
        // repeat is equivalent to which column we want to move to!
        vim.lastHPos = repeat - 1
        vim.lastHSPos = 0 // charCoords not available
        MotionResult.from(moveToColumn(cm, repeat))
    },

    "moveToEol" to { cm, head, motionArgs, vim, _inputState ->
        MotionResult.from(moveToEol(cm, head, motionArgs, vim, false))
    },

    "moveToFirstNonWhiteSpaceCharacter" to { cm, head, _motionArgs, _vim, _inputState ->
        // Go to the start of the line where the text begins, or the end for
        // whitespace-only lines
        val cursor = head
        MotionResult.from(
            LinePos(cursor.line, findFirstNonWhiteSpaceCharacter(cm.getLine(cursor.line)))
        )
    },

    "moveToMatchedSymbol" to { cm, head, _motionArgs, _vim, _inputState ->
        val cursor = head
        val line = cursor.line
        var ch = cursor.ch
        val lineText = cm.getLine(line)
        var symbol: String? = null
        while (ch < lineText.length) {
            symbol = lineText[ch].toString()
            if (isMatchableSymbol(symbol)) {
                val style = cm.getTokenTypeAt(LinePos(line, ch + 1))
                if (style != "string" && style != "comment") {
                    break
                }
            }
            ch++
        }
        if (ch < lineText.length) {
            val matched = cm.findMatchingBracket(LinePos(line, ch))
            if (matched.to != null) {
                MotionResult.from(matched.to)
            } else {
                MotionResult.from(cursor)
            }
        } else {
            MotionResult.from(cursor)
        }
    },

    "moveToStartOfLine" to { _cm, head, _motionArgs, _vim, _inputState ->
        MotionResult.from(LinePos(head.line, 0))
    },

    "moveToLineOrEdgeOfDocument" to { cm, _head, motionArgs, _vim, _inputState ->
        var lineNum = if (motionArgs.forward == true) cm.lastLine() else cm.firstLine()
        if (motionArgs.repeatIsExplicit == true) {
            lineNum = motionArgs.repeat - (cm.getOption("firstLineNumber") as? Int ?: 1)
        }
        MotionResult.from(
            LinePos(lineNum, findFirstNonWhiteSpaceCharacter(cm.getLine(lineNum)))
        )
    },

    "moveToStartOfDisplayLine" to { cm, _head, _motionArgs, _vim, _inputState ->
        cm.execCommand("goLineLeft")
        MotionResult.from(cm.getCursor())
    },

    "moveToEndOfDisplayLine" to { cm, _head, _motionArgs, _vim, _inputState ->
        cm.execCommand("goLineRight")
        val resultHead = cm.getCursor()
        // In the JS version this checks head.sticky == "before" and decrements ch.
        // We don't have sticky in the Kotlin LinePos, so we return as-is.
        MotionResult.from(resultHead)
    },

    "textObjectManipulation" to { cm, head, motionArgs, vim, _inputState ->
        val mirroredPairs = mapOf(
            "(" to ")", ")" to "(",
            "{" to "}", "}" to "{",
            "[" to "]", "]" to "[",
            "<" to ">", ">" to "<"
        )
        val selfPaired = setOf("'", "\"", "`")

        var character = motionArgs.selectedCharacter ?: ""
        // 'b' refers to '()' block.
        // 'B' refers to '{}' block.
        if (character == "b") {
            character = "("
        } else if (character == "B") {
            character = "{"
        }

        // Inclusive is the difference between a and i
        val inclusive = motionArgs.textObjectInner != true

        var tmp: WordBounds? = null
        var move = false

        if (mirroredPairs.containsKey(character)) {
            move = true
            tmp = selectCompanionObject(cm, head, character, inclusive)
            if (tmp == null) {
                val sc = cm.getSearchCursor(Regex(Regex.escape(character)), head)
                if (sc.findNext() != null) {
                    tmp = selectCompanionObject(cm, sc.from()!!, character, inclusive)
                }
            }
        } else if (selfPaired.contains(character)) {
            move = true
            tmp = findBeginningAndEnd(cm, head, character, inclusive)
        } else if (character == "W" || character == "w") {
            var remaining = motionArgs.repeat.coerceAtLeast(1)
            var result: WordBounds? = null
            while (remaining-- > 0) {
                val repeated = expandWordUnderCursor(
                    cm,
                    inclusive = inclusive,
                    innerWord = !inclusive,
                    bigWord = character == "W",
                    noSymbol = character == "W",
                    multiline = true,
                    cursor = result?.end
                )
                if (repeated != null) {
                    if (result == null) {
                        result = repeated
                    } else {
                        result = WordBounds(start = result.start, end = repeated.end)
                    }
                }
            }
            tmp = result
        } else if (character == "p") {
            val paraResult = findParagraph(cm, head, motionArgs.repeat, 0, inclusive)
            tmp = WordBounds(start = paraResult.start, end = paraResult.end)
            motionArgs.linewise = true
            if (vim.visualMode) {
                if (!vim.visualLine) {
                    vim.visualLine = true
                }
            } else {
                val operatorArgs = vim.inputState.operatorArgs
                if (operatorArgs != null) {
                    operatorArgs.linewise = true
                }
                tmp = WordBounds(
                    start = tmp.start,
                    end = LinePos(tmp.end.line - 1, tmp.end.ch)
                )
            }
        } else if (character == "t") {
            tmp = expandTagUnderCursor(cm, head, inclusive)
        } else if (character == "s") {
            // Account for cursor on end of sentence symbol
            var adjustedHead = head
            val content = cm.getLine(head.line)
            if (head.ch > 0 && head.ch < content.length &&
                isEndOfSentenceSymbol(content[head.ch].toString())
            ) {
                adjustedHead = LinePos(head.line, head.ch - 1)
            }
            val end = getSentence(cm, adjustedHead, motionArgs.repeat, 1, inclusive)
            val start = getSentence(cm, adjustedHead, motionArgs.repeat, -1, inclusive)
            // Closer vim behaviour, 'a' only takes the space after the sentence
            // if there is one before and after
            val startLine = cm.getLine(start.line)
            val endLine = cm.getLine(end.line)
            val adjustedStart = if (
                start.ch < startLine.length &&
                isWhiteSpaceString(startLine[start.ch].toString()) &&
                end.ch > 0 && end.ch - 1 < endLine.length &&
                isWhiteSpaceString(endLine[end.ch - 1].toString())
            ) {
                LinePos(start.line, start.ch + 1)
            } else {
                start
            }
            tmp = WordBounds(start = adjustedStart, end = end)
        }

        if (tmp == null) {
            // No valid text object, don't move.
            null
        } else {
            if (!cm.vim!!.visualMode) {
                MotionResult.from(tmp.start, tmp.end)
            } else {
                val expanded = expandSelection(cm, tmp.start, tmp.end, move)
                MotionResult.from(expanded.first, expanded.second)
            }
        }
    },

    "repeatLastCharacterSearch" to { cm, head, motionArgs, _vim, _inputState ->
        val lastSearch = cm.vimContext.lastCharacterSearch
        val repeat = motionArgs.repeat
        val forward = motionArgs.forward == lastSearch.forward
        val increment =
            (if (lastSearch.increment != 0) 1 else 0) * (if (forward) -1 else 1)
        motionArgs.inclusive = forward
        val curEnd = moveToCharacter(
            cm,
            repeat,
            forward,
            lastSearch.selectedCharacter,
            offsetCursor(head, 0, -increment)
        )
        if (curEnd == null) {
            MotionResult.from(head)
        } else {
            MotionResult.from(LinePos(curEnd.line, curEnd.ch + increment))
        }
    }
)

/**
 * Register a new motion function.
 */
internal fun defineMotion(name: String, fn: MotionFn) {
    motions[name] = fn
}
