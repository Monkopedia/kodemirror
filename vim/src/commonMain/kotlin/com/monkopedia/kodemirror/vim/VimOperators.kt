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

import kotlin.math.abs

// ---------------------------------------------------------------------------
// Utility: fillArray
// ---------------------------------------------------------------------------

private fun fillArray(value: String, times: Int): List<String> {
    val arr = mutableListOf<String>()
    for (i in 0 until times) {
        arr.add(value)
    }
    return arr
}

// ---------------------------------------------------------------------------
// The operators map
// ---------------------------------------------------------------------------

internal val operators: MutableMap<String, OperatorFn> = mutableMapOf(
    "change" to { cm, args, ranges, _oldAnchor, _newHead ->
        var finalHead: LinePos
        var text: String
        val vim = cm.vim!!
        var anchor = ranges[0].anchor
        var head = ranges[0].head
        if (!vim.visualMode) {
            text = cm.getRange(anchor, head)
            val lastState = vim.lastEditInputState
            if (lastState?.motion == "moveByWords" && !isWhiteSpaceString(text)) {
                // Exclude trailing whitespace if the range is not all whitespace.
                val match = Regex("\\s+$").find(text)
                if (match != null && lastState.motionArgs?.forward == true) {
                    head = offsetCursor(head, 0, -match.value.length)
                    text = text.substring(0, text.length - match.value.length)
                }
            }
            if (args.linewise == true) {
                anchor = LinePos(
                    anchor.line,
                    findFirstNonWhiteSpaceCharacter(cm.getLine(anchor.line))
                )
                if (head.line > anchor.line) {
                    head = LinePos(head.line - 1, Int.MAX_VALUE)
                }
            }
            cm.replaceRange("", anchor, head)
            finalHead = anchor
        } else if (args.fullLine == true) {
            head = LinePos(head.line - 1, Int.MAX_VALUE)
            cm.setSelection(anchor, head)
            text = cm.getSelection()
            cm.replaceSelection("")
            finalHead = anchor
        } else {
            text = cm.getSelection()
            val replacement = fillArray("", ranges.size)
            cm.replaceSelections(replacement)
            finalHead = cursorMin(ranges[0].head, ranges[0].anchor)
        }
        cm.vimContext.registerController.pushText(
            args.registerName,
            "change",
            text,
            args.linewise == true,
            ranges.size > 1
        )
        actions["enterInsertMode"]?.invoke(
            cm,
            ActionArgs(head = finalHead),
            vim
        )
        // Return null: enterInsertMode already positions the cursor (including
        // multi-cursor via selectForInsert for visual-block).  Returning a
        // position here would cause evalInput to call setCursor, collapsing
        // the multi-cursor state.
        null
    },

    "delete" to { cm, args, ranges, _oldAnchor, _newHead ->
        var finalHead: LinePos
        var text: String
        val vim = cm.vim!!
        if (!vim.visualBlock) {
            var anchor = ranges[0].anchor
            var head = ranges[0].head
            if (args.linewise == true &&
                head.line != cm.firstLine() &&
                anchor.line == cm.lastLine() &&
                anchor.line == head.line - 1
            ) {
                // Special case for dd on last line (and first line).
                if (anchor.line == cm.firstLine()) {
                    anchor = LinePos(anchor.line, 0)
                } else {
                    anchor = LinePos(anchor.line - 1, lineLength(cm, anchor.line - 1))
                }
            }
            text = cm.getRange(anchor, head)
            cm.replaceRange("", anchor, head)
            finalHead = anchor
            if (args.linewise == true) {
                finalHead = motions["moveToFirstNonWhiteSpaceCharacter"]?.invoke(
                    cm,
                    anchor,
                    MotionArgs(),
                    vim,
                    InputState()
                )?.toPos() ?: anchor
            }
        } else {
            text = cm.getSelection()
            val replacement = fillArray("", ranges.size)
            cm.replaceSelections(replacement)
            finalHead = cursorMin(ranges[0].head, ranges[0].anchor)
        }
        cm.vimContext.registerController.pushText(
            args.registerName,
            "delete",
            text,
            args.linewise == true,
            vim.visualBlock
        )
        clipCursorToContent(cm, finalHead)
    },

    "indent" to { cm, args, ranges, _oldAnchor, _newHead ->
        val vim = cm.vim!!
        // In visual mode, n> shifts the selection right n times, instead of
        // shifting n lines right once.
        val repeat = if (vim.visualMode) args.repeat.coerceAtLeast(1) else 1
        if (vim.visualBlock) {
            val tabSize = cm.getOption("tabSize") as? Int ?: 4
            val indent = if (cm.getOption("indentWithTabs") == true) {
                "\t"
            } else {
                " ".repeat(tabSize)
            }
            var cursor: LinePos? = null
            for (i in ranges.size - 1 downTo 0) {
                cursor = cursorMin(ranges[i].anchor, ranges[i].head)
                if (args.indentRight == true) {
                    cm.replaceRange(indent.repeat(repeat), cursor, cursor)
                } else {
                    val text = cm.getLine(cursor.line)
                    var end = 0
                    for (j in 0 until repeat) {
                        val ch =
                            text.getOrNull(cursor.ch + end)?.toString() ?: break
                        if (ch == "\t") {
                            end++
                        } else if (ch == " ") {
                            end++
                            for (k in 1 until indent.length) {
                                val ch2 =
                                    text.getOrNull(cursor.ch + end)?.toString()
                                        ?: break
                                if (ch2 != " ") break
                                end++
                            }
                        } else {
                            break
                        }
                    }
                    cm.replaceRange("", cursor, offsetCursor(cursor, 0, end))
                }
            }
            cursor
        } else {
            for (j in 0 until repeat) {
                if (args.indentRight == true) cm.indentMore() else cm.indentLess()
            }
            motions["moveToFirstNonWhiteSpaceCharacter"]?.invoke(
                cm,
                ranges[0].anchor,
                MotionArgs(),
                vim,
                InputState()
            )?.toPos()
        }
    },

    "indentAuto" to { cm, _args, ranges, _oldAnchor, _newHead ->
        cm.execCommand("indentAuto")
        motions["moveToFirstNonWhiteSpaceCharacter"]?.invoke(
            cm,
            ranges[0].anchor,
            MotionArgs(),
            cm.vim!!,
            InputState()
        )?.toPos()
    },

    "hardWrap" to { cm, operatorArgs, ranges, oldAnchor, _newHead ->
        val from = ranges[0].anchor.line
        var to = ranges[0].head.line
        if (operatorArgs.linewise == true) to--
        var endRow = cm.hardWrap(
            HardWrapOptions(from = from, to = to)
        )
        if (endRow > from && operatorArgs.linewise == true) endRow--
        if (operatorArgs.keepCursor == true) oldAnchor else LinePos(endRow, 0)
    },

    "toggleComment" to { cm, _args, _ranges, _oldAnchor, newHead ->
        cm.execCommand("toggleLineComment")
        newHead
    },

    "changeCase" to { cm, args, ranges, oldAnchor, newHead ->
        val selections = cm.getSelections()
        val swapped = mutableListOf<String>()
        val toLower = args.toLower
        for (j in selections.indices) {
            val toSwap = selections[j]
            val text: String
            if (toLower == true) {
                text = toSwap.lowercase()
            } else if (toLower == false) {
                text = toSwap.uppercase()
            } else {
                // Toggle case
                val sb = StringBuilder()
                for (i in toSwap.indices) {
                    val character = toSwap[i].toString()
                    sb.append(
                        if (isUpperCase(character)) {
                            character.lowercase()
                        } else {
                            character.uppercase()
                        }
                    )
                }
                text = sb.toString()
            }
            swapped.add(text)
        }
        cm.replaceSelections(swapped)
        if (args.shouldMoveCursor == true) {
            newHead
        } else if (!cm.vim!!.visualMode &&
            args.linewise == true &&
            ranges[0].anchor.line + 1 == ranges[0].head.line
        ) {
            motions["moveToFirstNonWhiteSpaceCharacter"]?.invoke(
                cm,
                oldAnchor,
                MotionArgs(),
                cm.vim!!,
                InputState()
            )?.toPos()
        } else if (args.linewise == true) {
            oldAnchor
        } else {
            cursorMin(ranges[0].anchor, ranges[0].head)
        }
    },

    "yank" to { cm, args, ranges, oldAnchor, _newHead ->
        val vim = cm.vim!!
        val text = cm.getSelection()
        val endPos = if (vim.visualMode) {
            // cursorMin of 4 positions
            var result = cursorMin(vim.sel.anchor, vim.sel.head)
            result = cursorMin(result, ranges[0].head)
            result = cursorMin(result, ranges[0].anchor)
            result
        } else {
            oldAnchor
        }
        cm.vimContext.registerController.pushText(
            args.registerName,
            "yank",
            text,
            args.linewise == true,
            vim.visualBlock
        )

        val lineCount = abs(cm.getCursor("end").line - cm.getCursor("start").line)
            .coerceAtLeast(1)
        val registerInfo = if (args.registerName != null) {
            " into \"${args.registerName}"
        } else {
            ""
        }
        showConfirm(cm, "$lineCount lines yanked$registerInfo", duration = 1500)
        endPos
    },

    "rot13" to { cm, args, ranges, oldAnchor, newHead ->
        val selections = cm.getSelections()
        val swapped = mutableListOf<String>()
        for (j in selections.indices) {
            val replacement = selections[j].map { x ->
                val code = x.code
                when {
                    code in 65..90 -> { // Uppercase
                        (65 + ((code - 65 + 13) % 26)).toChar()
                    }

                    code in 97..122 -> { // Lowercase
                        (97 + ((code - 97 + 13) % 26)).toChar()
                    }

                    else -> x // Not a letter
                }
            }.joinToString("")
            swapped.add(replacement)
        }
        cm.replaceSelections(swapped)
        if (args.shouldMoveCursor == true) {
            newHead
        } else if (!cm.vim!!.visualMode &&
            args.linewise == true &&
            ranges[0].anchor.line + 1 == ranges[0].head.line
        ) {
            motions["moveToFirstNonWhiteSpaceCharacter"]?.invoke(
                cm,
                oldAnchor,
                MotionArgs(),
                cm.vim!!,
                InputState()
            )?.toPos()
        } else if (args.linewise == true) {
            oldAnchor
        } else {
            cursorMin(ranges[0].anchor, ranges[0].head)
        }
    }
)

/**
 * Register a new operator function.
 */
internal fun defineOperator(name: String, fn: OperatorFn) {
    operators[name] = fn
}
