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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * All vim action functions. Each action takes a VimEditor, ActionArgs,
 * and VimState, and performs a side-effecting operation.
 */
internal val actions: MutableMap<String, ActionFn> = mutableMapOf(

    // -----------------------------------------------------------------------
    // jumpListWalk
    // -----------------------------------------------------------------------
    "jumpListWalk" to { cm, actionArgs, vim ->
        if (!vim.visualMode) {
            val repeat = actionArgs.repeat
            val forward = actionArgs.forward
            val jumpList = cm.vimContext.jumpList
            val mark = jumpList.move(cm, if (forward == true) repeat else -repeat)
            val markPos = mark?.find() ?: cm.getCursor()
            cm.setCursor(markPos)
        }
    },

    // -----------------------------------------------------------------------
    // scroll
    // -----------------------------------------------------------------------
    "scroll" to { cm, actionArgs, vim ->
        if (!vim.visualMode) {
            val repeat = actionArgs.repeat
            val lineHeight = cm.defaultTextHeight()
            // Simplified scroll: just move cursor by repeat lines
            val cursor = copyCursor(cm.getCursor())
            val direction = if (actionArgs.forward == true) 1 else -1
            val newLine = cursor.line + (repeat * direction)
            val clampedLine = min(max(cm.firstLine(), newLine), cm.lastLine())
            cm.setCursor(LinePos(clampedLine, cursor.ch))
        }
    },

    // -----------------------------------------------------------------------
    // scrollToCursor
    // -----------------------------------------------------------------------
    "scrollToCursor" to { cm, actionArgs, _ ->
        // In Compose, scroll is handled differently. Just ensure cursor is visible.
        cm.scrollIntoView()
    },

    // -----------------------------------------------------------------------
    // replayMacro
    // -----------------------------------------------------------------------
    "replayMacro" to { cm, actionArgs, vim ->
        var registerName = actionArgs.selectedCharacter ?: ""
        var repeat = actionArgs.repeat
        val macroModeState = cm.vimContext.macroModeState
        if (registerName == "@") {
            registerName = macroModeState.latestRegister ?: ""
        } else {
            macroModeState.latestRegister = registerName
        }
        while (repeat > 0) {
            repeat--
            executeMacroRegister(cm, vim, macroModeState, registerName)
        }
    },

    // -----------------------------------------------------------------------
    // enterMacroRecordMode
    // -----------------------------------------------------------------------
    "enterMacroRecordMode" to { cm, actionArgs, _ ->
        val macroModeState = cm.vimContext.macroModeState
        val registerName = actionArgs.selectedCharacter
        if (cm.vimContext.registerController.isValidRegister(registerName)) {
            macroModeState.enterMacroRecordMode(cm, registerName)
        }
    },

    // -----------------------------------------------------------------------
    // toggleOverwrite
    // -----------------------------------------------------------------------
    "toggleOverwrite" to { cm, _, _ ->
        if (!cm.vim!!.overwrite) {
            cm.toggleOverwrite(true)
            cm.setOption("keyMap", "vim-replace")
            cm.signal("vim-mode-change", mapOf("mode" to "replace"))
        } else {
            cm.toggleOverwrite(false)
            cm.setOption("keyMap", "vim-insert")
            cm.signal("vim-mode-change", mapOf("mode" to "insert"))
        }
    },

    // -----------------------------------------------------------------------
    // enterInsertMode
    // -----------------------------------------------------------------------
    "enterInsertMode" to { cm, actionArgs, vim ->
        if (cm.getOption("readOnly") != true) {
            vim.insertMode = true
            vim.insertModeRepeat = actionArgs.repeat
            val insertAt = actionArgs.insertAt
            val sel = vim.sel
            var head = actionArgs.head ?: cm.getCursor("head")
            var height = cm.listSelections().size
            when (insertAt) {
                "eol" -> {
                    head = LinePos(head.line, lineLength(cm, head.line))
                }
                "bol" -> {
                    head = LinePos(head.line, 0)
                }
                "charAfter" -> {
                    val newPosition = updateSelectionForSurrogateCharacters(
                        cm,
                        head,
                        offsetCursor(head, 0, 1)
                    )
                    head = newPosition.end
                }
                "firstNonBlank" -> {
                    val fns = findFirstNonWhiteSpaceCharacter(cm.getLine(head.line))
                    val newPosition = updateSelectionForSurrogateCharacters(
                        cm,
                        head,
                        LinePos(head.line, fns)
                    )
                    head = newPosition.end
                }
                "startOfSelectedArea" -> {
                    if (!vim.visualMode) return@to
                    if (!vim.visualBlock) {
                        head = if (sel.head.line < sel.anchor.line) {
                            sel.head
                        } else {
                            LinePos(sel.anchor.line, 0)
                        }
                    } else {
                        head = LinePos(
                            min(sel.head.line, sel.anchor.line),
                            min(sel.head.ch, sel.anchor.ch)
                        )
                        height = abs(sel.head.line - sel.anchor.line) + 1
                    }
                }
                "endOfSelectedArea" -> {
                    if (!vim.visualMode) return@to
                    if (!vim.visualBlock) {
                        head = if (sel.head.line >= sel.anchor.line) {
                            offsetCursor(sel.head, 0, 1)
                        } else {
                            LinePos(sel.anchor.line, 0)
                        }
                    } else {
                        head = LinePos(
                            min(sel.head.line, sel.anchor.line),
                            max(sel.head.ch, sel.anchor.ch) + 1
                        )
                        height = abs(sel.head.line - sel.anchor.line) + 1
                    }
                }
                "inplace" -> {
                    if (vim.visualMode) return@to
                }
                "lastEdit" -> {
                    head = getLastEditPos(cm) ?: head
                }
            }
            if (actionArgs.replace == true) {
                cm.toggleOverwrite(true)
                cm.setOption("keyMap", "vim-replace")
                cm.signal("vim-mode-change", mapOf("mode" to "replace"))
            } else {
                cm.toggleOverwrite(false)
                cm.setOption("keyMap", "vim-insert")
                cm.signal("vim-mode-change", mapOf("mode" to "insert"))
            }
            if (!cm.vimContext.macroModeState.isPlaying) {
                if (vim.insertEnd != null) vim.insertEnd!!.clear()
                vim.insertEnd = cm.setBookmark(
                    head,
                    BookmarkOptions(insertLeft = true)
                )
            }
            if (vim.visualMode) {
                exitVisualMode(cm)
            }
            selectForInsert(cm, head, height)
        }
    },

    // -----------------------------------------------------------------------
    // toggleVisualMode
    // -----------------------------------------------------------------------
    "toggleVisualMode" to { cm, actionArgs, vim ->
        val repeat = actionArgs.repeat
        val anchor = cm.getCursor()
        if (!vim.visualMode) {
            // Entering visual mode
            vim.visualMode = true
            vim.visualLine = actionArgs.linewise == true
            vim.visualBlock = actionArgs.blockwise == true
            val head = clipCursorToContent(cm, LinePos(anchor.line, anchor.ch + repeat - 1))
            val newPosition = updateSelectionForSurrogateCharacters(cm, anchor, head)
            vim.sel = LinePosRange(
                anchor = newPosition.start,
                head = newPosition.end
            )
            val subMode = when {
                vim.visualLine -> "linewise"
                vim.visualBlock -> "blockwise"
                else -> ""
            }
            cm.signal(
                "vim-mode-change",
                mapOf("mode" to "visual", "subMode" to subMode)
            )
            updateCmSelection(cm)
            updateMark(cm, vim, "<", cursorMin(anchor, head))
            updateMark(cm, vim, ">", cursorMax(anchor, head))
        } else if (vim.visualLine != (actionArgs.linewise == true) ||
            vim.visualBlock != (actionArgs.blockwise == true)
        ) {
            // Toggling between modes
            vim.visualLine = actionArgs.linewise == true
            vim.visualBlock = actionArgs.blockwise == true
            val subMode = when {
                vim.visualLine -> "linewise"
                vim.visualBlock -> "blockwise"
                else -> ""
            }
            cm.signal(
                "vim-mode-change",
                mapOf("mode" to "visual", "subMode" to subMode)
            )
            updateCmSelection(cm)
        } else {
            exitVisualMode(cm)
        }
    },

    // -----------------------------------------------------------------------
    // reselectLastSelection
    // -----------------------------------------------------------------------
    "reselectLastSelection" to { cm, _, vim ->
        val lastSelection = vim.lastSelection
        if (vim.visualMode) {
            updateLastSelection(cm, vim)
        }
        if (lastSelection != null) {
            val anchor = lastSelection.anchorMark.find()
            val head = lastSelection.headMark.find()
            if (anchor == null || head == null) {
                return@to
            }
            vim.sel = LinePosRange(anchor = anchor, head = head)
            vim.visualMode = true
            vim.visualLine = lastSelection.visualLine
            vim.visualBlock = lastSelection.visualBlock
            updateCmSelection(cm)
            updateMark(cm, vim, "<", cursorMin(anchor, head))
            updateMark(cm, vim, ">", cursorMax(anchor, head))
            val subMode = when {
                vim.visualLine -> "linewise"
                vim.visualBlock -> "blockwise"
                else -> ""
            }
            cm.signal(
                "vim-mode-change",
                mapOf("mode" to "visual", "subMode" to subMode)
            )
        }
    },

    // -----------------------------------------------------------------------
    // joinLines
    // -----------------------------------------------------------------------
    "joinLines" to { cm, actionArgs, vim ->
        var curStart: LinePos
        var curEnd: LinePos
        if (vim.visualMode) {
            curStart = cm.getCursor("anchor")
            curEnd = cm.getCursor("head")
            if (cursorIsBefore(curEnd, curStart)) {
                val tmp = curEnd
                curEnd = curStart
                curStart = tmp
            }
            curEnd = LinePos(curEnd.line, lineLength(cm, curEnd.line) - 1)
        } else {
            val repeat = max(actionArgs.repeat, 2)
            curStart = cm.getCursor()
            curEnd = clipCursorToContent(cm, LinePos(curStart.line + repeat - 1, Int.MAX_VALUE))
        }
        var finalCh = 0
        // Build the joined text as a single replacement so undo treats
        // the entire visual-mode join as one step.
        val sb = StringBuilder(cm.getLine(curStart.line))
        for (lineIdx in curStart.line + 1..curEnd.line) {
            finalCh = sb.length
            val nextLine = cm.getLine(lineIdx)
            if (actionArgs.keepSpaces != true) {
                val nextStartCh = nextLine.indexOfFirst { !it.isWhitespace() }
                if (nextStartCh == -1) {
                    // Line is all whitespace -- skip adding its content
                } else {
                    sb.append(' ')
                    sb.append(nextLine.substring(nextStartCh))
                }
            } else {
                sb.append(nextLine)
            }
        }
        cm.replaceRange(
            sb.toString(),
            LinePos(curStart.line, 0),
            LinePos(curEnd.line, lineLength(cm, curEnd.line))
        )
        val curFinalPos = clipCursorToContent(cm, LinePos(curStart.line, finalCh))
        if (vim.visualMode) {
            exitVisualMode(cm, false)
        }
        cm.setCursor(curFinalPos)
    },

    // -----------------------------------------------------------------------
    // newLineAndEnterInsertMode
    // -----------------------------------------------------------------------
    "newLineAndEnterInsertMode" to { cm, actionArgs, vim ->
        vim.insertMode = true
        val insertAt = copyCursor(cm.getCursor())
        if (insertAt.line == cm.firstLine() && actionArgs.after != true) {
            // Special case for inserting newline before start of document.
            cm.replaceRange("\n", LinePos(cm.firstLine(), 0))
            cm.setCursor(cm.firstLine(), 0)
        } else {
            val targetLine = if (actionArgs.after == true) insertAt.line else insertAt.line - 1
            val ch = lineLength(cm, targetLine)
            cm.setCursor(LinePos(targetLine, ch))
            cm.execCommand("newlineAndIndent")
        }
        actions["enterInsertMode"]?.invoke(cm, ActionArgs(repeat = actionArgs.repeat), vim)
    },

    // -----------------------------------------------------------------------
    // paste
    // -----------------------------------------------------------------------
    "paste" to { cm, actionArgs, vim ->
        val register = cm.vimContext.registerController.getRegister(actionArgs.registerName)
        val text = register.toString()
        actions["continuePaste"]?.invoke(cm, actionArgs, vim)
    },

    // -----------------------------------------------------------------------
    // continuePaste (internal helper used by paste)
    // -----------------------------------------------------------------------
    "continuePaste" to { cm, actionArgs, vim ->
        val register = cm.vimContext.registerController.getRegister(actionArgs.registerName)
        var cur = copyCursor(cm.getCursor())
        var text = register.toString()
        if (text.isEmpty()) return@to

        if (actionArgs.matchIndent == true) {
            val tabSize = (cm.getOption("tabSize") as? Int) ?: 4
            val whitespaceLength = { str: String ->
                val tabs = str.count { it == '\t' }
                val spaces = str.count { it == ' ' }
                tabs * tabSize + spaces
            }
            val currentLine = cm.getLine(cm.getCursor().line)
            val indent = whitespaceLength(
                Regex("^\\s*").find(currentLine)?.value ?: ""
            )
            val chompedText = text.removeSuffix("\n")
            val wasChomped = text != chompedText
            val firstIndent = whitespaceLength(
                Regex("^\\s*").find(text)?.value ?: ""
            )
            text = Regex("^\\s*", RegexOption.MULTILINE).replace(chompedText) { match ->
                val wspace = match.value
                val newIndent = indent + (whitespaceLength(wspace) - firstIndent)
                if (newIndent < 0) {
                    ""
                } else {
                    " ".repeat(newIndent)
                }
            }
            if (wasChomped) text += "\n"
        }
        if (actionArgs.repeat > 1) {
            text = text.repeat(actionArgs.repeat)
        }
        val linewise = if (actionArgs.linewise == null) {
            register.linewise
        } else {
            actionArgs.linewise == true
        }
        val blockwise = register.blockwise
        val textLines = if (blockwise) text.split('\n').toMutableList() else null
        if (textLines != null) {
            if (linewise) textLines.removeLastOrNull()
            for (i in textLines.indices) {
                if (textLines[i].isEmpty()) textLines[i] = " "
            }
            cur = LinePos(cur.line, cur.ch + if (actionArgs.after == true) 1 else 0)
            cur = LinePos(cur.line, min(lineLength(cm, cur.line), cur.ch))
        } else if (linewise) {
            if (vim.visualMode) {
                text = if (vim.visualLine) {
                    text.dropLast(1)
                } else {
                    "\n" + text.substring(0, text.length - 1) + "\n"
                }
            } else if (actionArgs.after == true) {
                text = "\n" + text.substring(0, text.length - 1)
                cur = LinePos(cur.line, lineLength(cm, cur.line))
            } else {
                cur = LinePos(cur.line, 0)
            }
        } else {
            cur = LinePos(cur.line, cur.ch + if (actionArgs.after == true) 1 else 0)
        }
        var curPosFinal: LinePos
        if (vim.visualMode) {
            vim.lastPastedText = text
            var lastSelectionCurEnd: LinePos? = null
            val (selectionStart, selectionEnd) = getSelectedAreaRange(cm, vim)
            val selectedText = cm.getSelection()
            val selections = cm.listSelections()
            val emptyStrings = List(selections.size) { "" }
            if (vim.lastSelection != null) {
                lastSelectionCurEnd = vim.lastSelection!!.headMark.find()
            }
            cm.vimContext.registerController.unnamedRegister.setText(selectedText)
            if (blockwise) {
                cm.replaceSelections(emptyStrings)
                val se = LinePos(selectionStart.line + text.split('\n').size - 1, selectionStart.ch)
                cm.setCursor(selectionStart)
                selectBlock(cm, se)
                cm.replaceSelections(text.split('\n'))
                curPosFinal = selectionStart
            } else if (vim.visualBlock) {
                cm.replaceSelections(emptyStrings)
                cm.setCursor(selectionStart)
                cm.replaceRange(text, selectionStart, selectionStart)
                curPosFinal = selectionStart
            } else {
                cm.replaceRange(text, selectionStart, selectionEnd)
                val startIdx = cm.indexFromPos(selectionStart)
                curPosFinal = cm.posFromIndex(
                    DocPos(startIdx.value + text.length - 1)
                )
            }
            if (lastSelectionCurEnd != null && vim.lastSelection != null) {
                vim.lastSelection!!.headMark = cm.setBookmark(lastSelectionCurEnd)
            }
            if (linewise) {
                curPosFinal = LinePos(curPosFinal.line, 0)
            }
        } else {
            if (blockwise && textLines != null) {
                cm.setCursor(cur)
                for (i in textLines.indices) {
                    val line = cur.line + i
                    if (line > cm.lastLine()) {
                        cm.replaceRange("\n", LinePos(line, 0))
                    }
                    val lastCh = lineLength(cm, line)
                    if (lastCh < cur.ch) {
                        extendLineToColumn(cm, line, cur.ch)
                    }
                }
                cm.setCursor(cur)
                selectBlock(cm, LinePos(cur.line + textLines.size - 1, cur.ch))
                cm.replaceSelections(textLines)
                curPosFinal = cur
            } else {
                cm.replaceRange(text, cur)
                if (linewise) {
                    val line = if (actionArgs.after == true) cur.line + 1 else cur.line
                    curPosFinal = LinePos(
                        line,
                        findFirstNonWhiteSpaceCharacter(cm.getLine(line))
                    )
                } else {
                    curPosFinal = copyCursor(cur)
                    if (!text.contains('\n')) {
                        curPosFinal = LinePos(
                            curPosFinal.line,
                            curPosFinal.ch + text.length - if (actionArgs.after == true) 1 else 0
                        )
                    }
                }
            }
        }
        if (vim.visualMode) {
            exitVisualMode(cm, false)
        }
        cm.setCursor(curPosFinal)
    },

    // -----------------------------------------------------------------------
    // undo
    // -----------------------------------------------------------------------
    "undo" to { cm, actionArgs, _ ->
        cm.operation {
            repeatFn(cm, { it.execCommand("undo") }, actionArgs.repeat).invoke()
            cm.setCursor(clipCursorToContent(cm, cm.getCursor("start")))
        }
    },

    // -----------------------------------------------------------------------
    // redo
    // -----------------------------------------------------------------------
    "redo" to { cm, actionArgs, _ ->
        cm.operation {
            repeatFn(cm, { it.execCommand("redo") }, actionArgs.repeat).invoke()
            // After redo, position cursor at the earliest changed position
            // (matching vim's behavior of placing cursor at the start of
            // the redone change).
            val sels = cm.listSelections()
            var earliest = cm.getCursor("start")
            for (sel in sels) {
                val s = cursorMin(sel.anchor, sel.head)
                if (cursorIsBefore(s, earliest)) earliest = s
            }
            cm.setCursor(clipCursorToContent(cm, earliest))
        }
    },

    // -----------------------------------------------------------------------
    // setRegister
    // -----------------------------------------------------------------------
    "setRegister" to { _, actionArgs, vim ->
        vim.inputState.registerName = actionArgs.selectedCharacter
    },

    // -----------------------------------------------------------------------
    // insertRegister
    // -----------------------------------------------------------------------
    "insertRegister" to { cm, actionArgs, _ ->
        val registerName = actionArgs.selectedCharacter
        val register = cm.vimContext.registerController.getRegister(registerName)
        val text = register.toString()
        if (text.isNotEmpty()) {
            cm.replaceSelection(text)
        }
    },

    // -----------------------------------------------------------------------
    // oneNormalCommand
    // -----------------------------------------------------------------------
    "oneNormalCommand" to { cm, _, vim ->
        exitInsertMode(cm, true)
        vim.insertModeReturn = true
        lateinit var handler: (Array<out Any?>) -> Unit
        handler = { _ ->
            if (!vim.visualMode) {
                if (vim.insertModeReturn) {
                    vim.insertModeReturn = false
                    if (!vim.insertMode) {
                        actions["enterInsertMode"]?.invoke(
                            cm,
                            ActionArgs(),
                            vim
                        )
                    }
                }
                cm.off("vim-command-done", handler)
            }
        }
        cm.on("vim-command-done", handler)
    },

    // -----------------------------------------------------------------------
    // setMark
    // -----------------------------------------------------------------------
    "setMark" to { cm, actionArgs, vim ->
        val markName = actionArgs.selectedCharacter
        if (markName != null) updateMark(cm, vim, markName, cm.getCursor())
    },

    // -----------------------------------------------------------------------
    // replace
    // -----------------------------------------------------------------------
    "replace" to { cm, actionArgs, vim ->
        val replaceWith = actionArgs.selectedCharacter ?: ""
        var curStart = cm.getCursor()
        var curEnd: LinePos
        val selections = cm.listSelections()
        if (vim.visualMode) {
            curStart = cm.getCursor("start")
            curEnd = cm.getCursor("end")
        } else {
            val line = cm.getLine(curStart.line)
            var replaceTo = curStart.ch + actionArgs.repeat
            if (replaceTo > line.length) {
                replaceTo = line.length
            }
            curEnd = LinePos(curStart.line, replaceTo)
        }
        val newPositions = updateSelectionForSurrogateCharacters(cm, curStart, curEnd)
        curStart = newPositions.start
        curEnd = newPositions.end
        if (replaceWith == "\n") {
            if (!vim.visualMode) cm.replaceRange("", curStart, curEnd)
            cm.execCommand("newlineAndIndent")
        } else {
            var replaceWithStr = cm.getRange(curStart, curEnd)
            // Replace all surrogate characters with selected character
            replaceWithStr = Regex("[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]")
                .replace(replaceWithStr, replaceWith)
            // Replace all characters in range by selected, but keep linebreaks
            replaceWithStr = Regex("[^\\n]").replace(replaceWithStr, replaceWith)
            if (vim.visualBlock) {
                val spaces = " ".repeat((cm.getOption("tabSize") as? Int ?: 4) + 1)
                replaceWithStr = cm.getSelection()
                replaceWithStr = Regex("[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]")
                    .replace(replaceWithStr, replaceWith)
                val replaceWithStrings = replaceWithStr.replace("\t", spaces)
                    .replace(Regex("[^\\n]"), replaceWith)
                    .split('\n')
                cm.replaceSelections(replaceWithStrings)
            } else {
                cm.replaceRange(replaceWithStr, curStart, curEnd)
            }
            if (vim.visualMode) {
                curStart = if (cursorIsBefore(selections[0].anchor, selections[0].head)) {
                    selections[0].anchor
                } else {
                    selections[0].head
                }
                cm.setCursor(curStart)
                exitVisualMode(cm, false)
            } else {
                cm.setCursor(offsetCursor(curEnd, 0, -1))
            }
        }
    },

    // -----------------------------------------------------------------------
    // incrementNumberToken
    // -----------------------------------------------------------------------
    "incrementNumberToken" to { cm, actionArgs, _ ->
        val cur = cm.getCursor()
        val lineStr = cm.getLine(cur.line)
        val re = Regex("(-?)(?:(0x)([\\da-f]+)|(0b|0|)(\\d+))", RegexOption.IGNORE_CASE)
        var match: kotlin.text.MatchResult? = null
        var start = 0
        var end = 0
        for (m in re.findAll(lineStr)) {
            start = m.range.first
            end = m.range.last + 1
            if (cur.ch < end) {
                match = m
                break
            }
        }
        if (actionArgs.backtrack != true && end <= cur.ch) return@to
        if (match != null) {
            val baseStr = match.groupValues[2].ifEmpty { match.groupValues[4] }
            val digits = match.groupValues[3].ifEmpty { match.groupValues[5] }
            val increment = if (actionArgs.increase == true) 1 else -1
            val base = when (baseStr.lowercase()) {
                "0b" -> 2
                "0" -> 8
                "0x" -> 16
                else -> 10
            }
            val rawNum = (match.groupValues[1] + digits).toInt(base)
            val number = rawNum + (increment * actionArgs.repeat)
            var numberStr = number.toString(base)
            val zeroPadding = if (baseStr.isNotEmpty()) {
                "0".repeat(
                    max(0, digits.length - numberStr.length + match.groupValues[1].length)
                )
            } else {
                ""
            }
            numberStr = if (numberStr.startsWith("-")) {
                "-" + baseStr + zeroPadding + numberStr.substring(1)
            } else {
                baseStr + zeroPadding + numberStr
            }
            val from = LinePos(cur.line, start)
            val to = LinePos(cur.line, end)
            cm.replaceRange(numberStr, from, to)
            cm.setCursor(LinePos(cur.line, start + numberStr.length - 1))
        }
    },

    // -----------------------------------------------------------------------
    // repeatLastEdit
    // -----------------------------------------------------------------------
    "repeatLastEdit" to { cm, actionArgs, vim ->
        val lastEditInputState = vim.lastEditInputState
        if (lastEditInputState != null) {
            var repeat = actionArgs.repeat
            if (repeat > 0 && actionArgs.repeatIsExplicit == true) {
                lastEditInputState.repeatOverride = repeat
            } else {
                repeat = lastEditInputState.repeatOverride ?: repeat
            }
            repeatLastEdit(cm, vim, repeat, false)
        }
    },

    // -----------------------------------------------------------------------
    // indent
    // -----------------------------------------------------------------------
    "indent" to { cm, actionArgs, _ ->
        cm.indentLine(cm.getCursor().line, actionArgs.indentRight == true)
    },

    // -----------------------------------------------------------------------
    // exitInsertMode
    // -----------------------------------------------------------------------
    "exitInsertMode" to { cm, _, _ ->
        exitInsertMode(cm)
    }
)

/**
 * Register a new action function by name.
 */
internal fun defineAction(name: String, fn: ActionFn) {
    actions[name] = fn
}
