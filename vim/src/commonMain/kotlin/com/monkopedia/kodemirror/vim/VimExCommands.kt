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

import com.monkopedia.kodemirror.view.save

/**
 * All ex command functions. Each takes a VimEditor and ExParams.
 */
internal val exCommands: MutableMap<String, ExFn> = mutableMapOf(

    // -----------------------------------------------------------------------
    // colorscheme
    // -----------------------------------------------------------------------
    "colorscheme" to { cm, params ->
        if (params.args == null || params.args!!.isEmpty()) {
            showConfirm(cm, cm.getOption("theme")?.toString() ?: "default")
        } else {
            cm.setOption("theme", params.args!![0])
        }
    },

    // -----------------------------------------------------------------------
    // map
    // -----------------------------------------------------------------------
    "map" to { cm, params ->
        exMapCommand(cm, params, null, false)
    },

    // -----------------------------------------------------------------------
    // imap
    // -----------------------------------------------------------------------
    "imap" to { cm, params ->
        exMapCommand(cm, params, "insert", false)
    },

    // -----------------------------------------------------------------------
    // nmap
    // -----------------------------------------------------------------------
    "nmap" to { cm, params ->
        exMapCommand(cm, params, "normal", false)
    },

    // -----------------------------------------------------------------------
    // vmap
    // -----------------------------------------------------------------------
    "vmap" to { cm, params ->
        exMapCommand(cm, params, "visual", false)
    },

    // -----------------------------------------------------------------------
    // omap
    // -----------------------------------------------------------------------
    "omap" to { cm, params ->
        exMapCommand(cm, params, "operatorPending", false)
    },

    // -----------------------------------------------------------------------
    // noremap
    // -----------------------------------------------------------------------
    "noremap" to { cm, params ->
        exMapCommand(cm, params, null, true)
    },

    // -----------------------------------------------------------------------
    // inoremap
    // -----------------------------------------------------------------------
    "inoremap" to { cm, params ->
        exMapCommand(cm, params, "insert", true)
    },

    // -----------------------------------------------------------------------
    // nnoremap
    // -----------------------------------------------------------------------
    "nnoremap" to { cm, params ->
        exMapCommand(cm, params, "normal", true)
    },

    // -----------------------------------------------------------------------
    // vnoremap
    // -----------------------------------------------------------------------
    "vnoremap" to { cm, params ->
        exMapCommand(cm, params, "visual", true)
    },

    // -----------------------------------------------------------------------
    // onoremap
    // -----------------------------------------------------------------------
    "onoremap" to { cm, params ->
        exMapCommand(cm, params, "operatorPending", true)
    },

    // -----------------------------------------------------------------------
    // unmap
    // -----------------------------------------------------------------------
    "unmap" to { cm, params ->
        exUnmapCommand(cm, params, null)
    },

    // -----------------------------------------------------------------------
    // mapclear
    // -----------------------------------------------------------------------
    "mapclear" to { _, _ ->
        vimApi.mapclear()
    },

    // -----------------------------------------------------------------------
    // imapclear
    // -----------------------------------------------------------------------
    "imapclear" to { _, _ ->
        vimApi.mapclear("insert")
    },

    // -----------------------------------------------------------------------
    // nmapclear
    // -----------------------------------------------------------------------
    "nmapclear" to { _, _ ->
        vimApi.mapclear("normal")
    },

    // -----------------------------------------------------------------------
    // vmapclear
    // -----------------------------------------------------------------------
    "vmapclear" to { _, _ ->
        vimApi.mapclear("visual")
    },

    // -----------------------------------------------------------------------
    // omapclear
    // -----------------------------------------------------------------------
    "omapclear" to { _, _ ->
        vimApi.mapclear("operatorPending")
    },

    // -----------------------------------------------------------------------
    // move
    // -----------------------------------------------------------------------
    "move" to { cm, params ->
        commandDispatcher.processCommand(
            cm,
            cm.vim!!,
            MotionCommand(
                keys = "",
                motion = "moveToLineOrEdgeOfDocument",
                motionArgs = MotionArgs(forward = false, explicitRepeat = true, linewise = true),
                repeatOverride = params.line + 1
            )
        )
    },

    // -----------------------------------------------------------------------
    // set
    // -----------------------------------------------------------------------
    "set" to { cm, params ->
        val setArgs = params.args
        val setCfg = params.setCfg
        if (setArgs == null || setArgs.isEmpty()) {
            showConfirm(cm, "Invalid mapping: ${params.input}")
            return@to
        }
        val expr = setArgs[0].split("=", limit = 2)
        var optionName = expr[0]
        var value: Any? = if (expr.size > 1) expr[1] else null
        var forceGet = false
        var forceToggle = false

        if (optionName.endsWith("?")) {
            if (value != null) throw Error("Trailing characters: ${params.argString}")
            optionName = optionName.dropLast(1)
            forceGet = true
        } else if (optionName.endsWith("!")) {
            optionName = optionName.dropLast(1)
            forceToggle = true
        }
        if (value == null && optionName.startsWith("no")) {
            optionName = optionName.substring(2)
            value = false
        }

        val optionIsBoolean = vimOptions[optionName]?.type == "boolean"
        if (optionIsBoolean) {
            if (forceToggle) {
                value = getOption(optionName, cm, setCfg) != true
            } else if (value == null) {
                value = true
            }
        }
        if ((!optionIsBoolean && value == null) || forceGet) {
            val oldValue = getOption(optionName, cm, setCfg)
            if (oldValue is Error) {
                showConfirm(cm, oldValue.message ?: "")
            } else if (oldValue == true || oldValue == false) {
                showConfirm(cm, " ${if (oldValue == true) "" else "no"}$optionName")
            } else {
                showConfirm(cm, "  $optionName=$oldValue")
            }
        } else {
            val setOptionReturn = setOption(optionName, value, cm, setCfg)
            if (setOptionReturn is Error) {
                showConfirm(cm, setOptionReturn.message ?: "")
            }
        }
    },

    // -----------------------------------------------------------------------
    // setlocal
    // -----------------------------------------------------------------------
    "setlocal" to { cm, params ->
        params.setCfg = mutableMapOf("scope" to "local")
        exCommands["set"]?.invoke(cm, params)
    },

    // -----------------------------------------------------------------------
    // setglobal
    // -----------------------------------------------------------------------
    "setglobal" to { cm, params ->
        params.setCfg = mutableMapOf("scope" to "global")
        exCommands["set"]?.invoke(cm, params)
    },

    // -----------------------------------------------------------------------
    // registers
    // -----------------------------------------------------------------------
    "registers" to { cm, params ->
        val regArgs = params.args
        val registers = cm.vimContext.registerController.registers
        val regInfo = StringBuilder("----------Registers----------\n\n")
        if (regArgs == null) {
            for ((registerName, register) in registers) {
                val text = register.toString()
                if (text.isNotEmpty()) {
                    regInfo.append("\"$registerName    $text\n")
                }
            }
        } else {
            val registerNames = regArgs.joinToString("")
            for (ch in registerNames) {
                val registerName = ch.toString()
                if (!cm.vimContext.registerController.isValidRegister(registerName)) continue
                val register = registers[registerName] ?: Register()
                regInfo.append("\"$registerName    ${register}\n")
            }
        }
        showConfirm(cm, regInfo.toString(), true)
    },

    // -----------------------------------------------------------------------
    // marks
    // -----------------------------------------------------------------------
    "marks" to { cm, params ->
        val filterArgs = params.args
        val marks = cm.vim!!.marks
        val regInfo = StringBuilder("-----------Marks-----------\nmark\tline\tcol\n\n")
        if (filterArgs == null) {
            for ((name, marker) in marks) {
                val pos = marker.find()
                if (pos != null) {
                    regInfo.append("$name\t${pos.line}\t${pos.ch}\n")
                }
            }
        } else {
            val registerNames = filterArgs.joinToString("")
            for (ch in registerNames) {
                val name = ch.toString()
                val pos = marks[name]?.find()
                if (pos != null) {
                    regInfo.append("$name\t${pos.line}\t${pos.ch}\n")
                }
            }
        }
        showConfirm(cm, regInfo.toString(), true)
    },

    // -----------------------------------------------------------------------
    // sort
    // -----------------------------------------------------------------------
    "sort" to { cm, params ->
        var reverse = false
        var ignoreCase = false
        var unique = false
        var number: String? = null
        var pattern: Regex? = null
        var includeMatch = false

        // Parse sort arguments
        var err: String? = null
        if (params.argString.isNotEmpty()) {
            var argStr = params.argString
            if (argStr.startsWith("!")) {
                reverse = true
                argStr = argStr.substring(1)
            }
            if (argStr.isNotEmpty()) {
                if (!argStr.startsWith(" ") && !argStr.startsWith("\t")) {
                    err = "Invalid arguments"
                } else {
                    argStr = argStr.trimStart()
                    val opts = Regex("([dinuoxr]+)?\\s*(/.+/)?\\s*").matchEntire(argStr)
                    if (opts == null) {
                        err = "Invalid arguments"
                    } else {
                        val flags = opts.groupValues[1]
                        if (flags.isNotEmpty()) {
                            includeMatch = 'r' in flags
                            ignoreCase = 'i' in flags
                            unique = 'u' in flags
                            val decimal = 'd' in flags || 'n' in flags
                            val hex = 'x' in flags
                            val octal = 'o' in flags
                            if (listOf(decimal, hex, octal).count { it } > 1) {
                                err = "Invalid arguments"
                            } else {
                                number = when {
                                    decimal -> "decimal"
                                    hex -> "hex"
                                    octal -> "octal"
                                    else -> null
                                }
                            }
                        }
                        if (err == null && opts.groupValues[2].isNotEmpty()) {
                            val patStr = opts.groupValues[2]
                            val options = if (ignoreCase) {
                                setOf(RegexOption.IGNORE_CASE)
                            } else {
                                emptySet()
                            }
                            pattern = Regex(patStr.substring(1, patStr.length - 1), options)
                        }
                    }
                }
            }
        }
        if (err != null) {
            showConfirm(cm, "$err: ${params.argString}")
            return@to
        }

        val lineStart = params.line.takeIf { it > 0 } ?: cm.firstLine()
        val lineEnd = params.lineEnd ?: params.line.takeIf { it > 0 } ?: cm.lastLine()
        if (lineStart == lineEnd) return@to
        val curStart = LinePos(lineStart, 0)
        val curEnd = LinePos(lineEnd, lineLength(cm, lineEnd))
        var text = cm.getRange(curStart, curEnd).split('\n').toMutableList()
        val numberRegex = when (number) {
            "decimal" -> Regex("(-?)([\\d]+)")
            "hex" -> Regex("(-?)(?:0x)?([0-9a-f]+)", RegexOption.IGNORE_CASE)
            "octal" -> Regex("([0-7]+)")
            else -> null
        }
        val radix = when (number) {
            "decimal" -> 10
            "hex" -> 16
            "octal" -> 8
            else -> null
        }
        // Each entry in numPart is (sortKey, originalLine)
        val numPart = mutableListOf<Pair<String, String>>()
        val textPart = mutableListOf<String>()
        if (number != null || pattern != null) {
            for (i in text.indices) {
                val matchPart = if (pattern != null) pattern.find(text[i]) else null
                if (matchPart != null && matchPart.value.isNotEmpty()) {
                    val sortKey = if (includeMatch) {
                        // r flag: sort by the matched text itself
                        matchPart.value
                    } else {
                        // No r flag: sort by text after the match
                        text[i].substring(
                            matchPart.range.first + matchPart.value.length
                        )
                    }
                    if (!includeMatch && sortKey.isEmpty()) {
                        // No text after match means no sort key — treat as
                        // unsortable
                        textPart.add(text[i])
                    } else {
                        numPart.add(sortKey to text[i])
                    }
                } else if (
                    numberRegex != null && numberRegex.containsMatchIn(text[i])
                ) {
                    numPart.add(text[i] to text[i])
                } else {
                    textPart.add(text[i])
                }
            }
        } else {
            textPart.addAll(text)
        }

        val compareFn = Comparator<String> { a, b ->
            var sa = a
            var sb = b
            if (reverse) {
                val tmp = sa
                sa = sb
                sb = tmp
            }
            if (ignoreCase) {
                sa = sa.lowercase()
                sb = sb.lowercase()
            }
            if (numberRegex != null && radix != null) {
                val amatch = numberRegex.find(sa)
                val bmatch = numberRegex.find(sb)
                if (amatch == null || bmatch == null) {
                    return@Comparator sa.compareTo(sb)
                }
                val aStr = amatch.groupValues.drop(1).joinToString("")
                val bStr = bmatch.groupValues.drop(1).joinToString("")
                val anum = aStr.lowercase().toIntOrNull(radix) ?: 0
                val bnum = bStr.lowercase().toIntOrNull(radix) ?: 0
                return@Comparator anum - bnum
            }
            if (sa < sb) {
                -1
            } else if (sa > sb) {
                1
            } else {
                0
            }
        }

        val comparePatternFn = Comparator<Pair<String, String>> { a, b ->
            var sa = a.first
            var sb = b.first
            if (reverse) {
                val tmp = sa
                sa = sb
                sb = tmp
            }
            if (ignoreCase) {
                sa = sa.lowercase()
                sb = sb.lowercase()
            }
            // Must return 0 for equal keys. Answering "greater than" for equal
            // elements breaks the comparator contract, and the order of tied
            // keys then depends on the platform's sort algorithm: JVM TimSort
            // keeps ties in place, while Kotlin/Native's merge sort takes from
            // the right run whenever compare(left, right) > 0 and so reverses
            // them. That made `:sort r/in/` — where every key is the literal
            // match "in" — produce different text on macOS/iOS than on JVM.
            // With a consistent comparator, sortWith is stable on every target
            // and ties keep their original order, which is what vim does.
            sa.compareTo(sb)
        }

        if (pattern != null) {
            numPart.sortWith(comparePatternFn)
        } else if (number == null) {
            textPart.sortWith(compareFn)
        } else {
            numPart.sortWith(
                Comparator { a, b ->
                    compareFn.compare(a.first, b.first)
                }
            )
        }

        // Reconstruct numPart as original line strings
        val numPartStrings = numPart.map { it.second }

        text = if (!reverse) {
            (textPart + numPartStrings).toMutableList()
        } else {
            (numPartStrings + textPart).toMutableList()
        }

        if (unique) {
            val deduped = mutableListOf<String>()
            var lastLine: String? = null
            for (line in text) {
                if (line != lastLine) deduped.add(line)
                lastLine = line
            }
            text = deduped
        }
        cm.replaceRange(text.joinToString("\n"), curStart, curEnd)
    },

    // -----------------------------------------------------------------------
    // vglobal
    // -----------------------------------------------------------------------
    "vglobal" to { cm, params ->
        exCommands["global"]?.invoke(cm, params)
    },

    // -----------------------------------------------------------------------
    // normal
    // -----------------------------------------------------------------------
    "normal" to { cm, params ->
        var argString = params.argString
        var noremap = false
        if (argString.isNotEmpty() && argString[0] == '!') {
            argString = argString.substring(1)
            noremap = true
        }
        argString = argString.trimStart()
        if (argString.isEmpty()) {
            showConfirm(cm, "Argument is required.")
            return@to
        }
        val line = params.line
        if (line > 0) {
            val lineEnd = params.lineEnd ?: line
            for (i in line..lineEnd) {
                cm.setCursor(i, 0)
                doKeyToKey(cm, argString)
                if (cm.vim?.insertMode == true) {
                    exitInsertMode(cm, true)
                }
            }
        } else {
            doKeyToKey(cm, argString)
            if (cm.vim?.insertMode == true) {
                exitInsertMode(cm, true)
            }
        }
    },

    // -----------------------------------------------------------------------
    // global
    // -----------------------------------------------------------------------
    "global" to { cm, params ->
        var argString = params.argString
        if (argString.isEmpty()) {
            showConfirm(cm, "Regular Expression missing from global")
            return@to
        }
        var inverted = params.commandName.startsWith("v")
        if (argString.startsWith("!") && params.commandName.startsWith("g")) {
            inverted = true
            argString = argString.substring(1)
        }
        val lineStart = if (params.line > 0) params.line else cm.firstLine()
        val lineEnd = params.lineEnd ?: params.line.takeIf { it > 0 } ?: cm.lastLine()
        val tokens = splitBySlash(argString)
        var regexPart = argString
        var cmd = ""
        if (tokens != null && tokens.isNotEmpty()) {
            regexPart = tokens[0]
            cmd = tokens.drop(1).joinToString("/")
        }
        if (regexPart.isNotEmpty()) {
            try {
                updateSearchQuery(cm, regexPart, true, true)
            } catch (_: Exception) {
                showConfirm(cm, "Invalid regex: $regexPart")
                return@to
            }
        }
        val query = getSearchState(cm).getQuery()
        if (query == null) {
            showConfirm(cm, "No previous search pattern")
            return@to
        }
        if (cmd.isEmpty()) {
            // Display matching lines
            val matchedLines = mutableListOf<String>()
            for (i in lineStart..lineEnd) {
                val line = cm.getLine(i)
                val matched = query.containsMatchIn(line)
                if (matched != inverted) {
                    matchedLines.add(line)
                }
            }
            showConfirm(cm, matchedLines.joinToString("\n"))
        } else {
            // Execute command on matching lines
            val matchedHandles = mutableListOf<LineHandleImpl>()
            for (i in lineStart..lineEnd) {
                val line = cm.getLine(i)
                val matched = query.containsMatchIn(line)
                if (matched != inverted) {
                    matchedHandles.add(cm.getLineHandle(i))
                }
            }
            var index = 0
            fun nextCommand() {
                if (index < matchedHandles.size) {
                    val lineHandle = matchedHandles[index++]
                    val lineNum = cm.getLineNumber(lineHandle)
                    if (lineNum == null) {
                        nextCommand()
                        return
                    }
                    val command = "${lineNum + 1}$cmd"
                    exCommandDispatcher.processCommand(
                        cm,
                        command,
                        ExParams(
                            callback = { nextCommand() }
                        )
                    )
                } else {
                    cm.releaseLineHandles()
                }
            }
            nextCommand()
        }
    },

    // -----------------------------------------------------------------------
    // substitute
    // -----------------------------------------------------------------------
    "substitute" to { cm, params ->
        val argString = params.argString
        val tokens = if (argString.isNotEmpty()) {
            splitBySeparator(argString, argString[0].toString())
        } else {
            emptyList()
        }
        var regexPart = ""
        var replacePart: String? = null
        var trailing: List<String>? = null
        var flagsPart: String? = null
        var count: Int? = null
        var confirm = false
        var global = false
        if (tokens != null && tokens.isNotEmpty()) {
            regexPart = tokens[0]
            if (getOption("pcre") == true && regexPart.isNotEmpty()) {
                regexPart = try {
                    Regex(regexPart).pattern
                } catch (_: Throwable) {
                    regexPart
                }
            }
            if (tokens.size > 1) {
                val rawReplace = tokens[1]
                replacePart = if (getOption("pcre") == true) {
                    unescapeRegexReplace(
                        rawReplace.replace(Regex("(^|[^\\\\])&")) { mr ->
                            mr.groupValues[1] + "\$&"
                        }
                    )
                } else {
                    translateRegexReplace(rawReplace)
                }
                cm.vimContext.lastSubstituteReplacePart = replacePart
            }
            trailing = if (tokens.size > 2) tokens[2].split(" ") else null
        } else {
            if (argString.isNotEmpty()) {
                showConfirm(cm, "Substitutions should be of the form :s/pattern/replace/")
                return@to
            }
        }
        if (trailing != null) {
            flagsPart = trailing[0]
            count = trailing.getOrNull(1)?.toIntOrNull()
            if (flagsPart.isNotEmpty()) {
                if ('c' in flagsPart) confirm = true
                if ('g' in flagsPart) global = true
                regexPart = if (getOption("pcre") == true) {
                    "$regexPart/$flagsPart"
                } else {
                    regexPart.replace("/", "\\/") + "/$flagsPart"
                }
            }
        }
        if (regexPart.isNotEmpty()) {
            try {
                updateSearchQuery(cm, regexPart, true, true)
            } catch (_: Exception) {
                showConfirm(cm, "Invalid regex: $regexPart")
                return@to
            }
        }
        val effectiveReplacePart = replacePart ?: cm.vimContext.lastSubstituteReplacePart
        if (effectiveReplacePart == null) {
            showConfirm(cm, "No previous substitute regular expression")
            return@to
        }
        val state = getSearchState(cm)
        val query = state.getQuery()
        if (query == null) {
            showConfirm(cm, "No previous search pattern")
            return@to
        }
        var lineStart = if (params.hasLineRange) params.line else cm.getCursor().line
        var lineEnd = params.lineEnd ?: lineStart
        if (lineStart == cm.firstLine() && lineEnd == cm.lastLine()) {
            lineEnd = Int.MAX_VALUE
        }
        if (count != null) {
            lineStart = lineEnd
            lineEnd = lineStart + count - 1
        }
        val startPos = clipCursorToContent(cm, LinePos(lineStart, 0))
        val cursor = cm.getSearchCursor(query, startPos)
        doReplace(
            cm, confirm, global, lineStart, lineEnd, cursor, query,
            effectiveReplacePart, params.callback
        )
    },

    // -----------------------------------------------------------------------
    // startinsert
    // -----------------------------------------------------------------------
    "startinsert" to { cm, params ->
        doKeyToKey(cm, if (params.argString == "!") "A" else "i")
    },

    // -----------------------------------------------------------------------
    // redo
    // -----------------------------------------------------------------------
    "redo" to { cm, _ ->
        cm.execCommand("redo")
    },

    // -----------------------------------------------------------------------
    // undo
    // -----------------------------------------------------------------------
    "undo" to { cm, _ ->
        cm.execCommand("undo")
    },

    // -----------------------------------------------------------------------
    // write
    // -----------------------------------------------------------------------
    "write" to { cm, _ ->
        save(cm.session)
    },

    // -----------------------------------------------------------------------
    // nohlsearch
    // -----------------------------------------------------------------------
    "nohlsearch" to { cm, _ ->
        clearSearchHighlight(cm)
    },

    // -----------------------------------------------------------------------
    // yank
    // -----------------------------------------------------------------------
    "yank" to { cm, params ->
        var line = params.selectionLine
        var lineEnd = params.selectionLineEnd ?: line
        if (lineEnd < line) {
            val tmp = lineEnd
            lineEnd = line
            line = tmp
        }
        val text = cm.getRange(LinePos(line, 0), LinePos(lineEnd + 1, 0))
        val registerName = params.args?.getOrNull(0) ?: "0"
        cm.vimContext.registerController.pushText(
            registerName,
            "yank",
            text,
            true,
            false
        )
        val count = lineEnd + 1 - line
        val regMsg = if (registerName.isNotEmpty()) {
            " into \"$registerName"
        } else {
            ""
        }
        val msg = "$count lines yanked$regMsg"
        showConfirm(cm, msg, false, 1500)
    },

    // -----------------------------------------------------------------------
    // put
    // -----------------------------------------------------------------------
    "put" to { cm, params ->
        exPutCommand(cm, params, false)
    },

    // -----------------------------------------------------------------------
    // iput
    // -----------------------------------------------------------------------
    "iput" to { cm, params ->
        exPutCommand(cm, params, true)
    },

    // -----------------------------------------------------------------------
    // delete
    // -----------------------------------------------------------------------
    "delete" to { cm, params ->
        val line = params.selectionLine
        val lineEnd = params.selectionLineEnd ?: line
        // Direct range deletion
        val startPos = LinePos(line, 0)
        val endPos = LinePos(lineEnd + 1, 0)
        val text = cm.getRange(startPos, endPos)
        cm.vimContext.registerController.unnamedRegister.setText(text, true, false)
        cm.replaceRange("", startPos, endPos)
    },

    // -----------------------------------------------------------------------
    // join
    // -----------------------------------------------------------------------
    "join" to { cm, params ->
        val line = params.selectionLine
        val lineEnd = params.selectionLineEnd ?: line
        cm.setCursor(LinePos(line, 0))
        actions["joinLines"]?.invoke(cm, ActionArgs(repeat = lineEnd - line), cm.vim!!)
    },

    // -----------------------------------------------------------------------
    // delmarks
    // -----------------------------------------------------------------------
    "delmarks" to { cm, params ->
        if (params.argString.isEmpty() || params.argString.isBlank()) {
            showConfirm(cm, "Argument required")
            return@to
        }
        val state = cm.vim!!
        val argStr = params.argString.trim()
        var i = 0
        while (i < argStr.length) {
            while (i < argStr.length && argStr[i].isWhitespace()) i++
            if (i >= argStr.length) break

            if (!argStr[i].isLetter()) {
                showConfirm(cm, "Invalid argument: ${argStr.substring(i)}")
                return@to
            }

            val sym = argStr[i].toString()
            i++

            if (i < argStr.length && argStr[i] == '-') {
                // Range
                i++
                if (i >= argStr.length || !argStr[i].isLetter()) {
                    showConfirm(cm, "Invalid argument: ${argStr.substring(i - 2)}")
                    return@to
                }
                val finishMark = argStr[i].toString()
                i++
                if (isLowerCase(sym) == isLowerCase(finishMark)) {
                    val start = sym[0].code
                    val finish = finishMark[0].code
                    if (start >= finish) {
                        showConfirm(cm, "Invalid argument: ${argStr.substring(i - 3)}")
                        return@to
                    }
                    for (j in 0..(finish - start)) {
                        val mark = (start + j).toChar().toString()
                        state.marks.remove(mark)
                    }
                } else {
                    showConfirm(cm, "Invalid argument: $sym-")
                    return@to
                }
            } else {
                state.marks.remove(sym)
            }
        }
    }
)

// ---------------------------------------------------------------------------
// Helper functions for ex commands
// ---------------------------------------------------------------------------

private fun exMapCommand(cm: VimEditor, params: ExParams, ctx: String?, defaultOnly: Boolean) {
    val mapArgs = params.args
    if (mapArgs == null || mapArgs.size < 2) {
        showConfirm(cm, "Invalid mapping: ${params.input}")
        return
    }
    exCommandDispatcher.map(mapArgs[0], mapArgs[1], ctx, defaultOnly)
}

private fun exUnmapCommand(cm: VimEditor, params: ExParams, ctx: String?) {
    val mapArgs = params.args
    if (mapArgs == null || mapArgs.isEmpty() || !exCommandDispatcher.unmap(mapArgs[0], ctx)) {
        showConfirm(cm, "No such mapping: ${params.input}")
    }
}

private fun exPutCommand(cm: VimEditor, params: ExParams, matchIndent: Boolean) {
    val actionArgs = ActionArgs(
        after = true,
        isEdit = true,
        matchIndent = matchIndent,
        repeat = 1,
        linewise = true,
        registerName = ""
    )
    val args = params.args?.toMutableList() ?: mutableListOf()
    if (args.isNotEmpty() && args[0] == "!") {
        actionArgs.after = false
        args.removeFirst()
    }
    if (args.isNotEmpty()) {
        actionArgs.registerName = args[0]
    }
    val line = params.selectionLine
    cm.setCursor(LinePos(line, 0))
    actions["continuePaste"]?.invoke(cm, actionArgs, cm.vim!!)
}
