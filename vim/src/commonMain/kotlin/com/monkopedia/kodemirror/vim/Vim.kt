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
// Vim key-to-CM key mapping
// ---------------------------------------------------------------------------

private val specialKey = mapOf(
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

private val vimToCmKeyMap: Map<String, String> = buildMap {
    val directKeys = listOf("Left", "Right", "Up", "Down", "End", "Home")
    for (key in directKeys) {
        put(key.lowercase(), key)
    }
    for ((cmKey, vimKey) in specialKey) {
        put(vimKey.lowercase(), cmKey)
        put(cmKey.lowercase(), cmKey)
    }
}

// noremap, keyToKeyStack, and virtualPrompt have been moved to per-editor
// VimContext (accessed via cm.vimContext). See VimContext.kt.

// ---------------------------------------------------------------------------
// Default keymap length tracking (for mapclear)
// ---------------------------------------------------------------------------

internal val defaultKeymapLength = defaultKeymap.size

// ---------------------------------------------------------------------------
// The Vim API object
// ---------------------------------------------------------------------------

object Vim : VimApiInterface {

    init {
        vimApi = this
        commandDispatcher = VimCommandDispatcher
        initOptions()
    }

    // -- Public API ----------------------------------------------------------

    internal fun getRegisterController(cm: VimEditor): RegisterController =
        cm.vimContext.registerController

    @Suppress("ktlint:standard:function-naming")
    fun resetVimGlobalState_() {
        resetVimGlobalState()
    }

    @Suppress("ktlint:standard:function-naming")
    internal fun maybeInitVimState_(cm: VimEditor): VimState {
        return maybeInitVimState(cm)
    }

    override fun handleKey(cm: VimEditor, key: String, origin: String): Boolean {
        // When a virtual prompt is active (test/headless mode), route key input there.
        if (cm.vimContext.virtualPrompt != null) {
            sendKeyToPrompt(cm, key)
            return true
        }
        val command = findKey(cm, key, origin)
        if (command != null) {
            return command()
        }
        return false
    }

    fun handleEx(cm: VimEditor, input: String) {
        exCommandDispatcher.processCommand(cm, input)
    }

    fun multiSelectHandleKey(cm: VimEditor, key: String, origin: String): Boolean {
        // TODO: Visual-block multi-cursor handling not yet implemented
        return handleKey(cm, key, origin)
    }

    fun setOption(
        name: String,
        value: Any?,
        cm: VimEditor? = null,
        cfg: Map<String, String>? = null
    ) {
        com.monkopedia.kodemirror.vim.setOption(name, value, cm, cfg)
    }

    fun getOption(name: String, cm: VimEditor? = null, cfg: Map<String, String>? = null): Any? {
        return com.monkopedia.kodemirror.vim.getOption(name, cm, cfg)
    }

    fun defineOption(
        name: String,
        defaultValue: Any?,
        type: String? = null,
        aliases: List<String>? = null,
        callback: ((Any?, VimEditor?) -> Any?)? = null
    ) {
        com.monkopedia.kodemirror.vim.defineOption(name, defaultValue, type, aliases, callback)
    }

    internal fun defineRegister(name: String, register: Register) {
        com.monkopedia.kodemirror.vim.defineRegister(name, register)
    }

    fun langmap(langmapString: String, remapCtrl: Boolean? = null) {
        updateLangmap(langmapString, remapCtrl)
    }

    internal fun vimKeyFromEvent(
        key: String,
        ctrlKey: Boolean = false,
        altKey: Boolean = false,
        metaKey: Boolean = false,
        shiftKey: Boolean = false,
        code: String? = null,
        vim: VimState? = null
    ): String? {
        return com.monkopedia.kodemirror.vim.vimKeyFromEvent(
            key,
            ctrlKey,
            altKey,
            metaKey,
            shiftKey,
            code,
            vim
        )
    }

    internal fun defineMotion(name: String, fn: MotionFn) {
        motions[name] = fn
    }

    internal fun defineAction(name: String, fn: ActionFn) {
        actions[name] = fn
    }

    internal fun defineOperator(name: String, fn: OperatorFn) {
        operators[name] = fn
    }

    /**
     * Registers a custom ex command (invoked with `:name` in the editor).
     *
     * The [prefix] is the shortest unambiguous abbreviation that will also trigger the command.
     * It must be a prefix of [name]. If `null` or empty, [name] itself is used as the prefix.
     *
     * Example:
     * ```kotlin
     * Vim.defineEx("write", "w") { cm, params ->
     *     // handle :write or :w
     * }
     * ```
     *
     * @param name The full command name (e.g. `"write"`).
     * @param prefix The short alias prefix (e.g. `"w"`), or `null` to use [name].
     * @param func The handler called when the command is invoked.
     */
    fun defineEx(name: String, prefix: String?, func: ExFn) {
        val effectivePrefix = if (prefix.isNullOrEmpty()) name else prefix
        if (!name.startsWith(effectivePrefix)) {
            error("\"$effectivePrefix\" is not a prefix of \"$name\", command not registered")
        }
        exCommands[name] = func
        exCommandDispatcher.commandMap[effectivePrefix] = ExCommandDefinition(
            name = name,
            shortName = effectivePrefix,
            type = "api"
        )
    }

    fun map(lhs: String, rhs: String, ctx: String? = null) {
        exCommandDispatcher.map(lhs, rhs, ctx)
    }

    fun unmap(lhs: String, ctx: String? = null): Boolean {
        return exCommandDispatcher.unmap(lhs, ctx)
    }

    fun noremap(lhs: String, rhs: String, ctx: String? = null) {
        exCommandDispatcher.map(lhs, rhs, ctx, noremap = true)
    }

    override fun mapclear(ctx: String?) {
        val actualLength = defaultKeymap.size
        val origLength = defaultKeymapLength
        val userKeymap = if (actualLength > origLength) {
            defaultKeymap.subList(0, actualLength - origLength).toList()
        } else {
            emptyList()
        }
        // Remove user-defined mappings
        while (defaultKeymap.size > origLength) {
            defaultKeymap.removeAt(0)
        }
        if (ctx != null) {
            for (i in userKeymap.indices.reversed()) {
                val mapping = userKeymap[i]
                if (ctx != mapping.context) {
                    if (mapping.context != null) {
                        mapCommand(mapping)
                    } else {
                        val contexts = listOf("normal", "insert", "visual")
                        for (c in contexts) {
                            if (c != ctx) {
                                mapCommand(mapping, contextOverride = c)
                            }
                        }
                    }
                }
            }
        }
    }

    internal val cursorActivityHandlers = mutableMapOf<VimEditor, (Array<out Any?>) -> Unit>()
    internal val changeHandlers = mutableMapOf<VimEditor, (Array<out Any?>) -> Unit>()

    fun enterVimMode(cm: VimEditor) {
        cm.signal("vim-mode-change", mapOf("mode" to "normal"))
        val handler: (Array<out Any?>) -> Unit = { onCursorActivity(cm) }
        cursorActivityHandlers[cm] = handler
        cm.events.on("cursorActivity", handler)
        registerChangeHandler(cm)
        maybeInitVimState(cm)
    }

    fun leaveVimMode(cm: VimEditor) {
        cursorActivityHandlers.remove(cm)?.let { handler ->
            cm.events.off("cursorActivity", handler)
        }
        changeHandlers.remove(cm)?.let { handler ->
            cm.events.off("change", handler)
        }
        cm.vim = null
    }

    // Records insert-mode input into lastInsertModeChanges so that dot-repeat
    // (`.`) and macros can replay the text typed during a change/insert command.
    // The recorder itself only acts while in insert mode (see onChange), so the
    // handler can stay registered for the lifetime of vim mode.
    internal fun registerChangeHandler(cm: VimEditor) {
        if (changeHandlers.containsKey(cm)) return
        val handler: (Array<out Any?>) -> Unit = { args ->
            onChange(cm, args.getOrNull(1) as? Change)
        }
        changeHandlers[cm] = handler
        cm.events.on("change", handler)
    }

    fun exitVisualMode(cm: VimEditor, moveHead: Boolean = true) {
        com.monkopedia.kodemirror.vim.exitVisualMode(cm, moveHead)
    }

    fun exitInsertMode(cm: VimEditor, keepCursor: Boolean = false) {
        com.monkopedia.kodemirror.vim.exitInsertMode(cm, keepCursor)
    }

    // -- Key finding / dispatch -----------------------------------------------

    fun findKey(cm: VimEditor, key: String, origin: String? = null): (() -> Boolean)? {
        val vim = maybeInitVimState(cm)

        fun handleMacroRecording(): Boolean {
            val macroModeState = cm.vimContext.macroModeState
            if (macroModeState.isRecording) {
                if (key == "q") {
                    macroModeState.exitMacroRecordMode()
                    clearInputState(cm)
                    return true
                }
                if (origin != "mapping") {
                    logKey(cm, macroModeState, key)
                }
            }
            return false
        }

        fun handleEsc(): Boolean {
            if (key == "<Esc>" || key == "Esc" || key == "Escape") {
                if (vim.visualMode) {
                    exitVisualMode(cm)
                } else if (vim.insertMode) {
                    exitInsertMode(cm)
                } else {
                    return false
                }
                clearInputState(cm)
                return true
            }
            return false
        }

        fun handleKeyInsertMode(): Any? {
            if (handleEsc()) return true
            vim.inputState.keyBuffer.add(key)
            val keys = vim.inputState.keyBuffer.joinToString("")
            val keysAreChars = key.length == 1
            val match = VimCommandDispatcher.matchCommand(
                keys,
                defaultKeymap,
                vim.inputState,
                "insert",
                cm
            )
            val changeQueue = vim.inputState.changeQueue

            return when (match.type) {
                "none" -> {
                    clearInputState(cm)
                    false
                }
                "partial" -> {
                    if (match.expectLiteralNext) vim.expectLiteralNext = true
                    if (keysAreChars) {
                        val selections = cm.listSelections()
                        val cq = if (
                            changeQueue == null ||
                            changeQueue.removed.size != selections.size
                        ) {
                            val newCq = ChangeQueue()
                            vim.inputState.changeQueue = newCq
                            newCq
                        } else {
                            changeQueue
                        }
                        cq.inserted += key
                        for (i in selections.indices) {
                            val from = cursorMin(selections[i].anchor, selections[i].head)
                            val to = cursorMax(selections[i].anchor, selections[i].head)
                            val text = cm.getRange(from, to)
                            while (cq.removed.size <= i) cq.removed.add("")
                            cq.removed[i] = (cq.removed[i]) + text
                        }
                    }
                    !keysAreChars
                }
                "full" -> {
                    vim.inputState.keyBuffer.clear()
                    vim.expectLiteralNext = false
                    if (match.command != null && changeQueue != null) {
                        val selections = cm.listSelections()
                        for (i in selections.indices) {
                            val here = selections[i].head
                            val removeLen = changeQueue.inserted.length
                            cm.replaceRange(
                                changeQueue.removed.getOrElse(i) { "" },
                                offsetCursor(here, 0, -removeLen),
                                here
                            )
                        }
                        cm.vimContext.macroModeState
                            .lastInsertModeChanges.changes.removeLastOrNull()
                    }
                    if (match.command == null) clearInputState(cm)
                    match.command
                }
                else -> {
                    clearInputState(cm)
                    false
                }
            }
        }

        fun handleKeyNonInsertMode(): Any? {
            if (handleMacroRecording() || handleEsc()) return true

            vim.inputState.keyBuffer.add(key)
            val keys = vim.inputState.keyBuffer.joinToString("")
            if (Regex("^[1-9]\\d*$").matches(keys)) return true

            val keysMatcher = Regex("^(\\d*)(.*)$").find(keys)
            if (keysMatcher == null) {
                clearInputState(cm)
                return false
            }
            val context = if (vim.visualMode) "visual" else "normal"
            var mainKey = keysMatcher.groupValues[2].ifEmpty { keysMatcher.groupValues[1] }
            val opShortcut = vim.inputState.operatorShortcut
            if (opShortcut != null && opShortcut.isNotEmpty() &&
                opShortcut.last().toString() == mainKey
            ) {
                mainKey = opShortcut
            }
            val match = VimCommandDispatcher.matchCommand(
                mainKey,
                defaultKeymap,
                vim.inputState,
                context,
                cm
            )
            return when (match.type) {
                "none" -> {
                    clearInputState(cm)
                    false
                }
                "partial" -> {
                    if (match.expectLiteralNext) vim.expectLiteralNext = true
                    true
                }
                "clear" -> {
                    clearInputState(cm)
                    true
                }
                else -> {
                    vim.expectLiteralNext = false
                    vim.inputState.keyBuffer.clear()
                    val keysMatcher2 = Regex("^(\\d*)(.*)$").find(keys)
                    if (keysMatcher2 != null) {
                        val digits = keysMatcher2.groupValues[1]
                        if (digits.isNotEmpty() && digits != "0") {
                            vim.inputState.pushRepeatDigit(digits)
                        }
                    }
                    match.command
                }
            }
        }

        val command: Any? = if (vim.insertMode) {
            handleKeyInsertMode()
        } else {
            handleKeyNonInsertMode()
        }

        if (command == false || command == null) {
            return if (!vim.insertMode && (key.length == 1)) {
                { true }
            } else {
                null
            }
        } else if (command == true) {
            return { true }
        } else if (command is VimKeyCommand) {
            return {
                cm.operation {
                    cm.curOp?.let { it.isVimOp = true }
                    try {
                        if (command is KeyToKeyCommand) {
                            doKeyToKey(cm, command.toKeys, command)
                        } else {
                            VimCommandDispatcher.processCommand(cm, vim, command)
                        }
                    } catch (e: Exception) {
                        cm.vim = null
                        maybeInitVimState(cm)
                        throw e
                    }
                }
                true
            }
        }
        return null
    }

    // -- Internal helpers -----------------------------------------------------

    private fun mapCommand(command: VimKeyCommand, contextOverride: String? = null) {
        val cmd = if (contextOverride != null) {
            when (command) {
                is KeyToKeyCommand -> command.copy(context = contextOverride)
                is ActionCommand -> command.copy(context = contextOverride)
                is MotionCommand -> command.copy(context = contextOverride)
                is OperatorCommand -> command.copy(context = contextOverride)
                is SearchCommand -> command.copy(context = contextOverride)
                is OperatorMotionCommand -> command.copy(context = contextOverride)
                is IdleCommand -> command.copy(context = contextOverride)
                is ExCommandMapping -> command.copy(context = contextOverride)
                is KeyToExCommand -> command.copy(context = contextOverride)
            }
        } else {
            command
        }
        defaultKeymap.add(0, cmd)
    }

    private fun initOptions() {
        defineOption("filetype", null, "string", listOf("ft")) { value, cm ->
            if (cm == null) {
                // Option is local. Do nothing for global.
                return@defineOption null
            }
            if (value == null) {
                cm.getOption("mode") ?: ""
            } else {
                val mode = if (value == "") "null" else value
                cm.setOption("mode", mode)
                null
            }
        }
        defineOption("textwidth", 80, "number", listOf("tw")) { value, cm ->
            if (cm == null) return@defineOption null
            if (value == null) {
                cm.getOption("textwidth")
            } else {
                val column = (value as? Number)?.toInt()
                    ?: (value as? String)?.toIntOrNull()
                if (column != null && column > 1) {
                    cm.setOption("textwidth", column)
                }
                null
            }
        }
        defineOption("pcre", true, "boolean")
        defineOption("langmap", null, "string", listOf("lmap")) { value, _ ->
            if (value == null) {
                langmap.string
            } else {
                updateLangmap(value as String)
                null
            }
        }
    }
}

// ---------------------------------------------------------------------------
// State init
// ---------------------------------------------------------------------------

internal fun maybeInitVimState(cm: VimEditor): VimState {
    return cm.vim ?: VimState().also {
        cm.vim = it
        // Register the cursorActivity handler for this editor so that
        // handleExternalSelection can synchronise vim visual-mode state
        // when the selection is changed outside of vim operations.
        if (!Vim.cursorActivityHandlers.containsKey(cm)) {
            val handler: (Array<out Any?>) -> Unit = { onCursorActivity(cm) }
            Vim.cursorActivityHandlers[cm] = handler
            cm.events.on("cursorActivity", handler)
        }
        Vim.registerChangeHandler(cm)
    }
}

internal fun getVimState(cm: VimEditor): VimState? {
    return cm.vim
}

// ---------------------------------------------------------------------------
// Command dispatcher
// ---------------------------------------------------------------------------

internal data class MatchResult(
    val type: String,
    val command: VimKeyCommand? = null,
    val expectLiteralNext: Boolean = false
)

internal object VimCommandDispatcher : CommandDispatcherInterface {

    fun matchCommand(
        keys: String,
        keyMap: List<VimKeyCommand>,
        inputState: InputState,
        context: String,
        cm: VimEditor? = null
    ): MatchResult {
        val matches =
            commandMatches(keys, keyMap, context, inputState, cm?.vimContext?.noremap ?: false)
        val bestMatch = matches.full.firstOrNull()
        if (bestMatch == null) {
            if (matches.partial.isNotEmpty()) {
                return MatchResult(
                    type = "partial",
                    expectLiteralNext = matches.partial.size == 1 &&
                        matches.partial[0].keys.endsWith("<character>")
                )
            }
            return MatchResult(type = "none")
        }
        if (bestMatch.keys.endsWith("<character>") || bestMatch.keys.endsWith("<register>")) {
            val character = lastChar(keys)
            if (character.isEmpty() || character.length > 1) return MatchResult(type = "clear")
            inputState.selectedCharacter = character
        }
        return MatchResult(type = "full", command = bestMatch)
    }

    override fun processCommand(cm: VimEditor, vim: VimState, command: VimKeyCommand) {
        vim.inputState.repeatOverride = command.repeatOverride
        when (command) {
            is MotionCommand -> processMotion(cm, vim, command)
            is OperatorCommand -> processOperator(cm, vim, command)
            is OperatorMotionCommand -> processOperatorMotion(cm, vim, command)
            is ActionCommand -> processAction(cm, vim, command)
            is SearchCommand -> processSearch(cm, vim, command)
            is ExCommandMapping, is KeyToExCommand -> processEx(cm, vim, command)
            is KeyToKeyCommand -> doKeyToKey(cm, command.toKeys, command)
            is IdleCommand -> { /* no-op */ }
        }
    }

    fun processMotion(cm: VimEditor, vim: VimState, command: VimKeyCommand) {
        val motionName = when (command) {
            is MotionCommand -> command.motion
            is OperatorMotionCommand -> command.motion
            else -> return
        }
        val motionArgsSrc = when (command) {
            is MotionCommand -> command.motionArgs
            is OperatorMotionCommand -> command.motionArgs
            else -> null
        }
        vim.inputState.motion = motionName
        vim.inputState.motionArgs = motionArgsSrc?.copy() ?: MotionArgs()
        evalInput(cm, vim)
    }

    fun processOperator(cm: VimEditor, vim: VimState, command: VimKeyCommand) {
        val inputState = vim.inputState
        val operatorName = when (command) {
            is OperatorCommand -> command.operator
            is OperatorMotionCommand -> command.operator
            else -> return
        }
        val operatorArgsSrc = when (command) {
            is OperatorCommand -> command.operatorArgs
            is OperatorMotionCommand -> command.operatorArgs
            else -> null
        }

        if (inputState.operator != null) {
            if (inputState.operator == operatorName) {
                inputState.motion = "expandToLine"
                inputState.motionArgs = MotionArgs(linewise = true, repeat = 1)
                evalInput(cm, vim)
                return
            } else {
                clearInputState(cm)
            }
        }
        inputState.operator = operatorName
        inputState.operatorArgs = operatorArgsSrc?.copy()
        if (command.keys.length > 1) {
            inputState.operatorShortcut = command.keys
        }
        if (command.exitVisualBlock == true) {
            vim.visualBlock = false
            updateCmSelection(cm)
        }
        if (vim.visualMode) {
            evalInput(cm, vim)
        }
    }

    fun processOperatorMotion(cm: VimEditor, vim: VimState, command: OperatorMotionCommand) {
        val visualMode = vim.visualMode
        val operatorMotionArgs = command.operatorMotionArgs
        if (operatorMotionArgs != null && visualMode && operatorMotionArgs.visualLine == true) {
            vim.visualLine = true
        }
        processOperator(cm, vim, command)
        if (!visualMode) {
            processMotion(cm, vim, command)
        }
    }

    override fun processAction(cm: VimEditor, vim: VimState, command: ActionCommand) {
        val inputState = vim.inputState
        val repeat = inputState.getRepeat()
        val repeatIsExplicit = repeat != 0
        val actionArgs = command.actionArgs?.copy() ?: ActionArgs()
        if (inputState.selectedCharacter != null) {
            actionArgs.selectedCharacter = inputState.selectedCharacter
        }
        if (command.operator != null) {
            processOperator(
                cm,
                vim,
                OperatorCommand(
                    keys = command.keys,
                    operator = command.operator,
                    context = command.context
                )
            )
        }
        if (command.motion != null) {
            processMotion(
                cm,
                vim,
                MotionCommand(
                    keys = command.keys,
                    motion = command.motion,
                    context = command.context
                )
            )
        }
        if (command.motion != null || command.operator != null) {
            evalInput(cm, vim)
        }
        actionArgs.repeat = if (repeat != 0) repeat else 1
        actionArgs.repeatIsExplicit = repeatIsExplicit
        actionArgs.registerName = inputState.registerName
        clearInputState(cm)
        vim.lastMotion = null
        if (command.isEdit == true) {
            recordLastEdit(cm, vim, inputState, command)
        }
        val actionFn = actions[command.action]
        if (actionFn != null) {
            actionFn(cm, actionArgs, vim)
        }
    }

    fun processSearch(cm: VimEditor, vim: VimState, command: SearchCommand) {
        val forward = command.searchArgs.forward == true
        val wholeWordOnly = command.searchArgs.wholeWordOnly == true
        getSearchState(cm).setReversed(!forward)

        when (command.searchArgs.querySrc) {
            "prompt" -> {
                val macroModeState = cm.vimContext.macroModeState
                if (macroModeState.isPlaying) {
                    val query = macroModeState.replaySearchQueries.removeFirstOrNull() ?: ""
                    handleSearchQuery(cm, vim, command, query, ignoreCase = true, smartCase = false)
                } else {
                    val promptPrefix = if (forward) "/" else "?"
                    showPrompt(
                        cm,
                        PromptOptions(
                            onClose = { query ->
                                handleSearchQuery(
                                    cm,
                                    vim,
                                    command,
                                    query.orEmpty(),
                                    ignoreCase = true,
                                    smartCase = true
                                )
                                if (macroModeState.isRecording) {
                                    logSearchQuery(cm, macroModeState, query.orEmpty())
                                }
                            },
                            prefix = promptPrefix
                        )
                    )
                }
            }
            "wordUnderCursor" -> {
                var word = expandWordUnderCursor(cm, noSymbol = true)
                var isKeyword = true
                if (word == null) {
                    word = expandWordUnderCursor(cm, noSymbol = false)
                    isKeyword = false
                }
                if (word == null) {
                    showConfirm(cm, "No word under cursor")
                    clearInputState(cm)
                    return
                }
                var query = cm.getLine(word.start.line)
                    .substring(word.start.ch, word.end.ch)
                query = if (isKeyword && wholeWordOnly) {
                    "\\b$query\\b"
                } else {
                    escapeRegex(query)
                }
                cm.vimContext.jumpList.cachedCursor = cm.getCursor()
                cm.setCursor(word.start)
                handleSearchQuery(cm, vim, command, query, ignoreCase = true, smartCase = false)
            }
        }
    }

    private fun handleSearchQuery(
        cm: VimEditor,
        vim: VimState,
        command: SearchCommand,
        query: String,
        ignoreCase: Boolean,
        smartCase: Boolean
    ) {
        try {
            updateSearchQuery(cm, query, ignoreCase, smartCase)
        } catch (_: Exception) {
            showConfirm(cm, "Invalid regex: $query")
            clearInputState(cm)
            return
        }
        processMotion(
            cm,
            vim,
            MotionCommand(
                keys = "",
                motion = "findNext",
                motionArgs = MotionArgs(
                    forward = true,
                    toJumplist = command.searchArgs.toJumplist
                )
            )
        )
    }

    fun processEx(cm: VimEditor, vim: VimState, command: VimKeyCommand) {
        if (command is KeyToExCommand) {
            exCommandDispatcher.processCommand(cm, command.exArgs.input)
        } else {
            val initialValue = if (vim.visualMode) {
                "'<,'>"
            } else {
                val repeat = vim.inputState.getRepeat()
                if (repeat > 1) ".,.+${repeat - 1}" else null
            }
            val promptOptions = PromptOptions(
                onClose = { input ->
                    exCommandDispatcher.processCommand(cm, input.orEmpty())
                    getVimState(cm)?.let { clearInputState(cm) }
                    clearSearchHighlight(cm)
                },
                prefix = ":",
                value = initialValue
            )
            showPrompt(cm, promptOptions)
        }
    }

    override fun evalInput(cm: VimEditor, vim: VimState) {
        val inputState = vim.inputState
        val motion = inputState.motion
        val motionArgs = inputState.motionArgs ?: MotionArgs()
        val operator = inputState.operator
        val operatorArgs = inputState.operatorArgs ?: OperatorArgs().also {
            inputState.operatorArgs = it
        }
        val registerName = inputState.registerName
        val sel = vim.sel

        val origHead = copyCursor(
            if (vim.visualMode) clipCursorToContent(cm, sel.head) else cm.getCursor("head")
        )
        val origAnchor = copyCursor(
            if (vim.visualMode) clipCursorToContent(cm, sel.anchor) else cm.getCursor("anchor")
        )
        val oldHead = copyCursor(origHead)
        val oldAnchor = copyCursor(origAnchor)
        var newHead: LinePos? = null
        var newAnchor: LinePos? = null

        if (operator != null) {
            recordLastEdit(cm, vim, inputState)
        }

        var repeat: Int
        if (inputState.repeatOverride != null) {
            repeat = inputState.repeatOverride!!
        } else {
            repeat = inputState.getRepeat()
        }
        if (repeat > 0 && motionArgs.explicitRepeat == true) {
            motionArgs.repeatIsExplicit = true
        } else if (motionArgs.noRepeat == true ||
            (motionArgs.explicitRepeat != true && repeat == 0)
        ) {
            repeat = 1
            motionArgs.repeatIsExplicit = false
        }

        if (inputState.selectedCharacter != null) {
            motionArgs.selectedCharacter = inputState.selectedCharacter
            operatorArgs.selectedCharacter = inputState.selectedCharacter
        }
        motionArgs.repeat = repeat
        clearInputState(cm)

        if (motion != null) {
            val motionFn = motions[motion]
            val motionResult = motionFn?.invoke(cm, origHead, motionArgs, vim, inputState)
            vim.lastMotion = motionFn
            if (motionResult == null) return

            if (motionArgs.toJumplist == true) {
                val jumpList = cm.vimContext.jumpList
                val cachedCursor = jumpList.cachedCursor
                val resultPos = motionResult.toPos() ?: origHead
                if (cachedCursor != null) {
                    recordJumpPosition(cm, cachedCursor, resultPos)
                    jumpList.cachedCursor = null
                } else {
                    recordJumpPosition(cm, origHead, resultPos)
                }
            }

            when (motionResult) {
                is MotionResult.Range -> {
                    newAnchor = motionResult.anchor
                    newHead = motionResult.head
                }
                is MotionResult.SinglePos -> {
                    newHead = motionResult.pos
                }
            }
            if (newHead == null) {
                newHead = copyCursor(origHead)
            }
            if (vim.visualMode) {
                if (!(vim.visualBlock && newHead.ch == Int.MAX_VALUE)) {
                    newHead = clipCursorToContent(cm, newHead, oldHead)
                }
                if (newAnchor != null) {
                    newAnchor = clipCursorToContent(cm, newAnchor)
                }
                newAnchor = newAnchor ?: oldAnchor
                sel.anchor = newAnchor
                sel.head = newHead
                updateCmSelection(cm)
                updateMark(
                    cm,
                    vim,
                    "<",
                    if (cursorIsBefore(newAnchor, newHead)) newAnchor else newHead
                )
                updateMark(
                    cm,
                    vim,
                    ">",
                    if (cursorIsBefore(newAnchor, newHead)) newHead else newAnchor
                )
            } else if (operator == null) {
                newHead = clipCursorToContent(cm, newHead, oldHead)
                cm.setCursor(newHead.line, newHead.ch)
            }
        }

        if (operator != null) {
            if (operatorArgs.lastSel != null) {
                newAnchor = oldAnchor
                val lastSel = operatorArgs.lastSel!!
                val lineOffset = abs(lastSel.head.line - lastSel.anchor.line)
                val chOffset = abs(lastSel.head.ch - lastSel.anchor.ch)
                newHead = when {
                    lastSel.visualLine -> LinePos(oldAnchor.line + lineOffset, oldAnchor.ch)
                    lastSel.visualBlock -> LinePos(
                        oldAnchor.line + lineOffset,
                        oldAnchor.ch + chOffset
                    )
                    lastSel.head.line == lastSel.anchor.line -> LinePos(
                        oldAnchor.line,
                        oldAnchor.ch + chOffset
                    )
                    else -> LinePos(oldAnchor.line + lineOffset, oldAnchor.ch)
                }
                vim.visualMode = true
                vim.visualLine = lastSel.visualLine
                vim.visualBlock = lastSel.visualBlock
                vim.sel = LinePosRange(newAnchor, newHead)
                updateCmSelection(cm)
            } else if (vim.visualMode) {
                operatorArgs.lastSel = LastSelection(
                    head = copyCursor(sel.head),
                    anchor = copyCursor(sel.anchor),
                    visualBlock = vim.visualBlock,
                    visualLine = vim.visualLine
                )
            }

            val curStart: LinePos
            val curEnd: LinePos
            val linewise: Boolean
            val mode: String
            val cmSel: CmSelectionResult

            if (vim.visualMode) {
                curStart = cursorMin(vim.sel.head, vim.sel.anchor)
                curEnd = cursorMax(vim.sel.head, vim.sel.anchor)
                linewise = vim.visualLine || operatorArgs.linewise == true
                mode = when {
                    vim.visualBlock -> "block"
                    linewise -> "line"
                    else -> "char"
                }
                val newPositions = updateSelectionForSurrogateCharacters(cm, curStart, curEnd)
                cmSel = makeCmSelection(
                    cm,
                    LinePosRange(newPositions.start, newPositions.end),
                    mode
                )
                if (linewise) {
                    val ranges = cmSel.ranges
                    if (mode == "block") {
                        for (i in ranges.indices) {
                            ranges[i] = LinePosRange(
                                ranges[i].anchor,
                                LinePos(ranges[i].head.line, lineLength(cm, ranges[i].head.line))
                            )
                        }
                    } else if (mode == "line") {
                        ranges[0] = LinePosRange(
                            ranges[0].anchor,
                            LinePos(ranges[0].head.line + 1, 0)
                        )
                    }
                }
            } else {
                var cs = copyCursor(newAnchor ?: oldAnchor)
                var ce = copyCursor(newHead ?: oldHead)
                if (cursorIsBefore(ce, cs)) {
                    val tmp = cs
                    cs = ce
                    ce = tmp
                }
                linewise = motionArgs.linewise == true || operatorArgs.linewise == true
                if (linewise) {
                    val (expandedStart, expandedEnd) = expandSelectionToLine(cm, cs, ce)
                    cs = expandedStart
                    ce = expandedEnd
                } else if (motionArgs.forward == true) {
                    ce = clipToLine(cm, cs, ce)
                }
                mode = "char"
                val exclusive = motionArgs.inclusive != true || linewise
                val newPositions = updateSelectionForSurrogateCharacters(cm, cs, ce)
                cmSel = makeCmSelection(
                    cm,
                    LinePosRange(newPositions.start, newPositions.end),
                    mode,
                    exclusive
                )
            }

            cm.setSelections(cmSel.ranges, cmSel.primary)
            vim.lastMotion = null
            operatorArgs.repeat = repeat
            operatorArgs.registerName = registerName
            operatorArgs.linewise = linewise
            val operatorFn = operators[operator]
            val operatorMoveTo = operatorFn?.invoke(
                cm,
                operatorArgs,
                cmSel.ranges,
                oldAnchor,
                newHead
            )
            if (vim.visualMode) {
                exitVisualMode(cm, operatorMoveTo != null)
            }
            if (operatorMoveTo != null) {
                cm.setCursor(operatorMoveTo)
            }
        }
    }

    fun recordLastEdit(
        cm: VimEditor,
        vim: VimState,
        inputState: InputState,
        actionCommand: ActionCommand? = null
    ) {
        val macroModeState = cm.vimContext.macroModeState
        if (macroModeState.isPlaying) return
        vim.lastEditInputState = inputState
        vim.lastEditActionCommand = actionCommand
        macroModeState.lastInsertModeChanges.changes.clear()
        macroModeState.lastInsertModeChanges.expectCursorActivityForChange = false
        macroModeState.lastInsertModeChanges.visualBlock =
            if (vim.visualBlock) vim.sel.head.line - vim.sel.anchor.line else 0
    }
}

// ---------------------------------------------------------------------------
// CmSelectionResult helper type
// ---------------------------------------------------------------------------

internal data class CmSelectionResult(
    val ranges: MutableList<LinePosRange>,
    val primary: Int = 0
)

// ---------------------------------------------------------------------------
// Virtual prompt for key-to-key mappings
// ---------------------------------------------------------------------------

internal fun sendKeyToPrompt(cm: VimEditor, key: String) {
    val ctx = cm.vimContext
    val prompt = ctx.virtualPrompt ?: error("No prompt to send key to")
    var effectiveKey = key
    // Normalize bare key names (e.g. "Enter", "Return") to their canonical form
    when (effectiveKey) {
        "Enter", "Return" -> effectiveKey = "\n"
        "Escape", "Esc" -> {
            // Cancel the prompt without invoking onClose
            ctx.virtualPrompt = null
            return
        }
        "Backspace" -> {
            val current = prompt.value ?: ""
            if (current.isNotEmpty()) {
                ctx.virtualPrompt = prompt.copy(value = current.dropLast(1))
            }
            return
        }
    }
    if (effectiveKey.startsWith("<")) {
        val lowerKey = effectiveKey.lowercase().drop(1).dropLast(1)
        val parts = lowerKey.split("-")
        val lastPart = parts.last()
        when (lastPart) {
            "lt" -> effectiveKey = "<"
            "space" -> effectiveKey = " "
            "cr" -> effectiveKey = "\n"
            "bs" -> {
                val current = prompt.value ?: ""
                if (current.isNotEmpty()) {
                    ctx.virtualPrompt = prompt.copy(value = current.dropLast(1))
                }
                return
            }
            "esc" -> {
                ctx.virtualPrompt = null
                return
            }
            else -> {
                val cmKey = vimToCmKeyMap[lastPart]
                if (cmKey != null) {
                    val event = VimKeyEvent(key = cmKey)
                    prompt.onKeyDown?.invoke(event)
                    ctx.virtualPrompt?.onKeyUp?.invoke(event)
                    return
                }
            }
        }
    }
    // If an onKeyDown handler is set, dispatch the key to it first.
    // If it returns true, the key was handled and should not be appended.
    if (prompt.onKeyDown != null) {
        val event = VimKeyEvent(key = effectiveKey)
        if (prompt.onKeyDown!!.invoke(event)) {
            return
        }
    }
    if (effectiveKey == "\n") {
        ctx.virtualPrompt = null
        prompt.onClose?.invoke(prompt.value)
    } else {
        ctx.virtualPrompt = prompt.copy(value = (prompt.value ?: "") + effectiveKey)
    }
}

internal fun doKeyToKey(cm: VimEditor, keys: String, fromKey: VimKeyCommand? = null) {
    val ctx = cm.vimContext
    val noremapBefore = ctx.noremap
    if (fromKey != null) {
        if (ctx.keyToKeyStack.contains(fromKey)) return
        ctx.keyToKeyStack.add(fromKey)
        ctx.noremap = fromKey.noremap != false
    }

    try {
        val vim = maybeInitVimState(cm)
        val keyRe = Regex("<(?:[CSMA]-)*\\w+>|.", RegexOption.IGNORE_CASE)
        for (match in keyRe.findAll(keys)) {
            val matchedKey = match.value
            val wasInsert = vim.insertMode
            if (ctx.virtualPrompt != null) {
                sendKeyToPrompt(cm, matchedKey)
                continue
            }
            val result = Vim.handleKey(cm, matchedKey, "mapping")
            if (!result && wasInsert && vim.insertMode) {
                var resolvedKey = matchedKey
                if (resolvedKey.startsWith("<")) {
                    val lowerKey = resolvedKey.lowercase().drop(1).dropLast(1)
                    val parts = lowerKey.split("-")
                    val lastPart = parts.last()
                    when (lastPart) {
                        "lt" -> resolvedKey = "<"
                        "space" -> resolvedKey = " "
                        "cr" -> resolvedKey = "\n"
                        else -> {
                            val cmKey = vimToCmKeyMap[lastPart]
                            if (cmKey != null) {
                                continue
                            }
                            resolvedKey = resolvedKey.first().toString()
                        }
                    }
                }
                cm.replaceSelection(resolvedKey)
            }
        }
    } finally {
        ctx.keyToKeyStack.removeLastOrNull()
        ctx.noremap = if (ctx.keyToKeyStack.isNotEmpty()) noremapBefore else false
        if (ctx.keyToKeyStack.isEmpty() && ctx.virtualPrompt != null) {
            val promptOptions = ctx.virtualPrompt!!
            ctx.virtualPrompt = null
            showPrompt(cm, promptOptions)
        }
    }
}
