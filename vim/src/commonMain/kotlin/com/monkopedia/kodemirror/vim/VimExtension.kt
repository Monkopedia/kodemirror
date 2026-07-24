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

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.monkopedia.kodemirror.commands.cursorCharLeft
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.define
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.blockCursorProvider
import com.monkopedia.kodemirror.view.inputSuppressor
import com.monkopedia.kodemirror.view.keyEventLayoutKey
import com.monkopedia.kodemirror.view.keymap
import com.monkopedia.kodemirror.view.showPanel

/**
 * Create a vim mode extension for the editor.
 *
 * @param status If true, always show a status panel displaying the current
 *   vim mode. If false (default), only show a panel when a dialog is active
 *   (e.g., ex command line, search prompt).
 */
fun vim(status: Boolean = false): Extension = extensionListOf(
    vimContextField,
    vimKeymap,
    vimPlugin.asExtension(),
    vimInputSuppressor,
    vimBlockCursorProvider,
    vimModeField,
    vimStatusField,
    virtualPromptField,
    if (status) showPanel.of(createStatusPanel()) else vimPanelField
)

/**
 * Provide block cursor positions to the selection overlay for rendering.
 * Looks up the vim plugin for this specific editor from the per-editor
 * [vimPlugins] map.
 */
private val vimBlockCursorProvider: Extension = blockCursorProvider.of {
    val plugin = activeVimPlugin ?: return@of emptyList()
    computeBlockCursorPositions(plugin.session.state, plugin.cm)
}

/**
 * Suppress text input when vim is in normal or visual mode.
 * Characters should execute vim commands, not insert text.
 * Looks up the vim plugin for this specific editor from the per-editor
 * [vimPlugins] map.
 */
private val vimInputSuppressor: Extension = inputSuppressor.of(::shouldSuppressVimInput)

/**
 * Returns true when vim is active and NOT in insert mode, meaning
 * character keys should execute commands, not insert text.
 */
private fun shouldSuppressVimInput(): Boolean {
    val plugin = activeVimPlugin ?: return false
    val vim = plugin.cm.vim ?: return false
    return !vim.insertMode
}

/**
 * Per-editor map of [EditorSession] to [VimPluginValue]. Each editor
 * registers its plugin on init and removes it on destroy. The facet
 * callbacks ([vimBlockCursorProvider], [vimInputSuppressor]) look up
 * the correct plugin via [activeVimPlugin].
 *
 * Because Compose is single-threaded, the most recently composed editor's
 * plugin is the one whose facets are currently being evaluated. We track
 * the "active" plugin via [activeVimPlugin], set during [VimPluginValue]
 * update and init.
 */
internal val vimPlugins = mutableMapOf<EditorSession, VimPluginValue>()

/**
 * The vim plugin for the editor currently being composed/updated.
 * Set during [VimPluginValue.init] and [VimPluginValue.update].
 */
internal var activeVimPlugin: VimPluginValue? = null

/**
 * Get the [VimEditor] associated with an [EditorSession], or null
 * if vim mode is not active.
 */
fun getVimEditor(view: EditorSession): VimEditor? = view.plugin(vimPlugin)?.cm

/** @see getVimEditor */
@Deprecated("Use getVimEditor()", ReplaceWith("getVimEditor(view)"))
fun getCM(view: EditorSession): VimEditor? = getVimEditor(view)

// ---------------------------------------------------------------------------
// State effect for toggling the vim panel
// ---------------------------------------------------------------------------

internal val vimModeEffect: StateEffectType<String> = StateEffect.define()

/**
 * A [StateField] that tracks the current vim mode as a string.
 *
 * Possible values: `"normal"`, `"insert"`, `"visual"`, `"visual line"`,
 * `"visual block"`.
 */
val vimModeField: StateField<String> = StateField.define {
    create { _: EditorState -> "normal" }
    update { value: String, tr: Transaction ->
        var result = value
        for (effect in tr.effects) {
            val mode = effect.asType(vimModeEffect)
            if (mode != null) result = mode.value
        }
        result
    }
}

internal val vimStatusEffect: StateEffectType<String> = StateEffect.define()

/**
 * A [StateField] that tracks the current vim status string (pending key
 * sequence or command output shown in the status bar).
 */
val vimStatusField: StateField<String> = StateField.define {
    create { _: EditorState -> "" }
    update { value: String, tr: Transaction ->
        var result = value
        for (effect in tr.effects) {
            val status = effect.asType(vimStatusEffect)
            if (status != null) result = status.value
        }
        result
    }
}

internal val virtualPromptEffect: StateEffectType<PromptOptions?> = StateEffect.define()

/**
 * A [StateField] that tracks the active virtual prompt (used in headless/test
 * mode when no real dialog UI is available). Null when no prompt is open.
 */
internal val virtualPromptField: StateField<PromptOptions?> = StateField.define {
    create { _: EditorState -> null }
    update { value: PromptOptions?, tr: Transaction ->
        var result = value
        for (effect in tr.effects) {
            val prompt = effect.asType(virtualPromptEffect)
            if (prompt != null) result = prompt.value
        }
        result
    }
}

internal val showVimPanel: StateEffectType<Boolean> = StateEffect.define()

// ---------------------------------------------------------------------------
// State field that tracks vim panel visibility
// ---------------------------------------------------------------------------

internal val vimPanelField: StateField<Boolean> = StateField.define {
    create { _: EditorState -> false }
    update { value: Boolean, tr: Transaction ->
        var result = value
        for (effect in tr.effects) {
            val panelEffect = effect.asType(showVimPanel)
            if (panelEffect != null) {
                result = panelEffect.value
            }
        }
        result
    }
    provide { field: StateField<Boolean> ->
        showPanel.from(field) { on: Boolean ->
            if (on) createVimDialogPanel() else null
        }
    }
}

// ---------------------------------------------------------------------------
// Vim keymap: intercepts all key events and routes them to the vim engine
// ---------------------------------------------------------------------------

private val vimKeymap: Extension = keymap.of(
    listOf(
        KeyBinding(
            any = handler@{ view, event ->
                if (event.type != KeyEventType.KeyDown) return@handler false
                val plugin = view.plugin(vimPlugin) ?: return@handler false
                plugin.handleKey(event)
            },
            anyRaw = handler@{ view, key, ctrl, alt, meta, shift ->
                // Handle raw key events from the document-level listener
                // for keys that Skiko doesn't convert to Compose KeyEvents.
                val plugin = view.plugin(vimPlugin) ?: return@handler false
                plugin.handleRawKey(key, ctrl, alt, meta, shift)
            }
        )
    )
)

// ---------------------------------------------------------------------------
// Vim ViewPlugin: manages the vim lifecycle
// ---------------------------------------------------------------------------

internal val vimPlugin: ViewPlugin<VimPluginValue> = ViewPlugin.define(
    create = { session -> VimPluginValue(session) },
    decorations = { _ -> RangeSet.empty() }
)

internal class VimPluginValue(internal val session: EditorSession) : PluginValue {
    val cm: VimEditor = VimEditor(session)
    private val vimState: VimState

    init {
        vimPlugins[session] = this
        activeVimPlugin = this
        Vim.enterVimMode(cm)
        cm.vimPlugin = this
        vimState = cm.vim ?: Vim.maybeInitVimState_(cm)

        cm.on("vim-command-done") {
            cm.vim?.status = ""
            updateStatus()
        }
        cm.on("vim-mode-change") { args ->
            val vimSt = cm.vim ?: return@on
            val eventMap = args.getOrNull(0) as? Map<*, *>
            if (eventMap != null) {
                vimSt.mode = eventMap["mode"] as? String
                val sub = eventMap["subMode"] as? String
                if (!sub.isNullOrEmpty()) {
                    vimSt.mode = (vimSt.mode ?: "") +
                        if (sub == "linewise") " line" else " block"
                }
            }
            vimSt.status = ""
            session.dispatch(
                TransactionSpec(
                    effects = listOf(
                        vimModeEffect.of(vimSt.mode ?: "normal")
                    )
                )
            )
            updateStatus()
            syncPromptPanel()
        }

        // Dispatch the initial mode so the state field has the correct value
        session.dispatch(
            TransactionSpec(
                effects = listOf(
                    vimModeEffect.of(vimState.mode ?: "normal")
                )
            )
        )

        cm.on("dialog") {
            if (cm.statusbar != null) {
                updateStatus()
            } else {
                session.dispatch(
                    TransactionSpec(
                        effects = listOf(
                            showVimPanel.of(cm.dialog != null)
                        )
                    )
                )
            }
        }
    }

    var status: String = ""
        private set

    override fun update(update: ViewUpdate) {
        activeVimPlugin = this
        if (update.docChanged) {
            cm.onChange(update)
        }
        if (update.selectionSet) {
            cm.onSelectionChange()
        }
        if (cm.curOp != null && cm.curOp?.isVimOp != true) {
            cm.onBeforeEndOperation()
        }
    }

    fun handleKey(event: KeyEvent): Boolean {
        val vim = cm.vim ?: return false
        val key = composeKeyToVimKey(event, vim) ?: return false

        // Clear search highlight on Esc in normal mode
        if (key == "<Esc>" && !vim.insertMode && !vim.visualMode) {
            val searchState = vim.searchState_
            if (searchState != null) {
                cm.removeOverlay(searchState.getOverlay())
                searchState.setOverlay(null)
            }
        }

        vim.status = (vim.status) + key
        val result = Vim.multiSelectHandleKey(cm, key, "user")
        // The vim state object can change if there is an exception in handleKey
        val currentVim = Vim.maybeInitVimState_(cm)

        // Overwrite mode handling
        if (!result && currentVim.insertMode && currentVim.overwrite) {
            val charKey = extractCharFromEvent(event)
            if (charKey != null && charKey.length == 1 && !charKey.contains('\n')) {
                cm.overWriteSelection(charKey)
                updateStatus()
                syncPromptPanel()
                return true
            } else if (event.key == Key.Backspace) {
                cursorCharLeft(cm.session)
                updateStatus()
                syncPromptPanel()
                return true
            }
        }

        if (result) {
            updateStatus()
        }
        syncPromptPanel()

        return result
    }

    /**
     * Handle a raw key event from the document-level listener.
     * Used for keys that Skiko doesn't convert to Compose KeyEvents.
     */
    fun handleRawKey(
        key: String,
        ctrl: Boolean,
        alt: Boolean,
        meta: Boolean,
        shift: Boolean
    ): Boolean {
        val vim = cm.vim ?: return false
        val vimKey = vimKeyFromEvent(
            key = key,
            ctrlKey = ctrl,
            altKey = alt,
            metaKey = meta,
            shiftKey = shift,
            vim = vim
        ) ?: return false

        vim.status = (vim.status) + vimKey
        val result = Vim.multiSelectHandleKey(cm, vimKey, "user")
        Vim.maybeInitVimState_(cm)

        if (result) {
            updateStatus()
        }
        syncPromptPanel()

        return result
    }

    /**
     * Sync the panel visibility and [virtualPromptField] with the current
     * per-editor [VimContext.virtualPrompt]. Shows the panel when a prompt
     * is active, hides it when dismissed. Dispatches [virtualPromptEffect]
     * on every call so that the panel composable recomposes with the latest
     * prompt value.
     */
    private fun syncPromptPanel() {
        val currentPrompt = cm.vimContext.virtualPrompt
        val promptActive = currentPrompt != null
        // Always dispatch the current prompt value so the StateField stays
        // in sync and the panel composable recomposes when the value changes.
        val effects = mutableListOf<StateEffect<*>>(virtualPromptEffect.of(currentPrompt))
        if (promptActive != promptPanelShown) {
            promptPanelShown = promptActive
            if (cm.statusbar == null) {
                effects.add(showVimPanel.of(promptActive))
            }
        }
        session.dispatch(TransactionSpec(effects = effects))
    }

    /** Whether the prompt panel is currently shown. */
    private var promptPanelShown: Boolean = false

    private fun updateStatus() {
        val vim = cm.vim ?: return
        status = vim.status
        session.dispatch(
            TransactionSpec(
                effects = listOf(vimStatusEffect.of(status))
            )
        )
    }

    override fun destroy() {
        vimPlugins.remove(session)
        if (activeVimPlugin === this) activeVimPlugin = null
        Vim.leaveVimMode(cm)
    }
}

// ---------------------------------------------------------------------------
// Key conversion: Compose KeyEvent → vim key string
// ---------------------------------------------------------------------------

/**
 * Convert a Compose [KeyEvent] to a vim key string (e.g., "j", "<Esc>",
 * "<C-r>").
 *
 * Returns null if the key should be ignored (modifier-only keys, etc.).
 */
internal fun composeKeyToVimKey(event: KeyEvent, vim: VimState?): String? {
    val key = composeKeyName(event)
    return vimKeyFromEvent(
        key = key,
        ctrlKey = event.isCtrlPressed,
        altKey = event.isAltPressed,
        metaKey = event.isMetaPressed,
        shiftKey = event.isShiftPressed,
        vim = vim
    )
}

/**
 * Extract the DOM-style key name from a Compose [KeyEvent].
 *
 * Uses [keyEventLayoutKey] as the primary source for printable characters,
 * which provides layout-aware key names (e.g., on Dvorak, physical 'j'
 * returns 'c'). Falls back to the Compose [Key] enum (physical positions)
 * only for special keys and when layout info is unavailable.
 */
private fun composeKeyName(event: KeyEvent): String {
    // For modifier-only keys, return the modifier name
    return when (event.key) {
        Key.ShiftLeft, Key.ShiftRight -> "Shift"
        Key.CtrlLeft, Key.CtrlRight -> "Control"
        Key.AltLeft, Key.AltRight -> "Alt"
        Key.MetaLeft, Key.MetaRight -> "Meta"
        Key.CapsLock -> "CapsLock"

        // Special keys → DOM-style names (will be mapped by specialKeyMap)
        Key.Escape -> "Escape"
        Key.Enter -> "Enter"
        Key.Tab -> "Tab"
        Key.Spacebar -> " "
        Key.Backspace -> "Backspace"
        Key.Delete -> "Delete"
        Key.Insert -> "Insert"
        Key.DirectionLeft -> "ArrowLeft"
        Key.DirectionRight -> "ArrowRight"
        Key.DirectionUp -> "ArrowUp"
        Key.DirectionDown -> "ArrowDown"
        Key.MoveHome -> "Home"
        Key.MoveEnd -> "End"
        Key.PageUp -> "PageUp"
        Key.PageDown -> "PageDown"

        // Function keys
        Key.F1 -> "F1"
        Key.F2 -> "F2"
        Key.F3 -> "F3"
        Key.F4 -> "F4"
        Key.F5 -> "F5"
        Key.F6 -> "F6"
        Key.F7 -> "F7"
        Key.F8 -> "F8"
        Key.F9 -> "F9"
        Key.F10 -> "F10"
        Key.F11 -> "F11"
        Key.F12 -> "F12"

        // Printable keys: use layout-aware key name from the platform.
        // This returns the character the user's keyboard layout produces
        // (e.g., "c" on Dvorak when pressing the physical QWERTY "j" key).
        // Falls back to the hardcoded Compose Key enum mapping for platforms
        // where layout info is unavailable.
        else -> layoutAwareKey(event)
    }
}

/**
 * Get the layout-aware character for a key event.
 *
 * Tries [keyEventLayoutKey] first (which reads the browser's `e.key` on
 * wasmJs, or the JVM's `KeyEvent.keyChar`). The platform returns the
 * actual character including shift state (e.g., "R" for Shift+r, "$" for
 * Shift+4). Falls back to the hardcoded [extractCharFromEvent] for
 * platforms where layout info is unavailable.
 */
private fun layoutAwareKey(event: KeyEvent): String {
    val layoutKey = keyEventLayoutKey(event)
    if (layoutKey != null) return layoutKey
    // Fallback: use physical key mapping (works for Ctrl+letter combos
    // where the platform can't determine the layout character)
    return extractCharFromEvent(event) ?: event.key.toString()
}

/**
 * Extract a printable character from a KeyEvent, accounting for shift state
 * and keyboard layout.
 */
private fun extractCharFromEvent(event: KeyEvent): String? {
    // For keys with Ctrl/Alt/Meta modifiers, return the base letter
    if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) {
        return when (event.key) {
            Key.A -> "a"
            Key.B -> "b"
            Key.C -> "c"
            Key.D -> "d"
            Key.E -> "e"
            Key.F -> "f"
            Key.G -> "g"
            Key.H -> "h"
            Key.I -> "i"
            Key.J -> "j"
            Key.K -> "k"
            Key.L -> "l"
            Key.M -> "m"
            Key.N -> "n"
            Key.O -> "o"
            Key.P -> "p"
            Key.Q -> "q"
            Key.R -> "r"
            Key.S -> "s"
            Key.T -> "t"
            Key.U -> "u"
            Key.V -> "v"
            Key.W -> "w"
            Key.X -> "x"
            Key.Y -> "y"
            Key.Z -> "z"
            Key.Zero -> "0"
            Key.One -> "1"
            Key.Two -> "2"
            Key.Three -> "3"
            Key.Four -> "4"
            Key.Five -> "5"
            Key.Six -> "6"
            Key.Seven -> "7"
            Key.Eight -> "8"
            Key.Nine -> "9"
            else -> null
        }
    }

    // For normal key presses, try to get the actual typed character
    // When Shift is held, letters should be uppercase
    return when (event.key) {
        Key.A -> if (event.isShiftPressed) "A" else "a"
        Key.B -> if (event.isShiftPressed) "B" else "b"
        Key.C -> if (event.isShiftPressed) "C" else "c"
        Key.D -> if (event.isShiftPressed) "D" else "d"
        Key.E -> if (event.isShiftPressed) "E" else "e"
        Key.F -> if (event.isShiftPressed) "F" else "f"
        Key.G -> if (event.isShiftPressed) "G" else "g"
        Key.H -> if (event.isShiftPressed) "H" else "h"
        Key.I -> if (event.isShiftPressed) "I" else "i"
        Key.J -> if (event.isShiftPressed) "J" else "j"
        Key.K -> if (event.isShiftPressed) "K" else "k"
        Key.L -> if (event.isShiftPressed) "L" else "l"
        Key.M -> if (event.isShiftPressed) "M" else "m"
        Key.N -> if (event.isShiftPressed) "N" else "n"
        Key.O -> if (event.isShiftPressed) "O" else "o"
        Key.P -> if (event.isShiftPressed) "P" else "p"
        Key.Q -> if (event.isShiftPressed) "Q" else "q"
        Key.R -> if (event.isShiftPressed) "R" else "r"
        Key.S -> if (event.isShiftPressed) "S" else "s"
        Key.T -> if (event.isShiftPressed) "T" else "t"
        Key.U -> if (event.isShiftPressed) "U" else "u"
        Key.V -> if (event.isShiftPressed) "V" else "v"
        Key.W -> if (event.isShiftPressed) "W" else "w"
        Key.X -> if (event.isShiftPressed) "X" else "x"
        Key.Y -> if (event.isShiftPressed) "Y" else "y"
        Key.Z -> if (event.isShiftPressed) "Z" else "z"
        Key.Zero -> if (event.isShiftPressed) ")" else "0"
        Key.One -> if (event.isShiftPressed) "!" else "1"
        Key.Two -> if (event.isShiftPressed) "@" else "2"
        Key.Three -> if (event.isShiftPressed) "#" else "3"
        Key.Four -> if (event.isShiftPressed) "$" else "4"
        Key.Five -> if (event.isShiftPressed) "%" else "5"
        Key.Six -> if (event.isShiftPressed) "^" else "6"
        Key.Seven -> if (event.isShiftPressed) "&" else "7"
        Key.Eight -> if (event.isShiftPressed) "*" else "8"
        Key.Nine -> if (event.isShiftPressed) "(" else "9"
        Key.Minus -> if (event.isShiftPressed) "_" else "-"
        Key.Equals -> if (event.isShiftPressed) "+" else "="
        Key.LeftBracket -> if (event.isShiftPressed) "{" else "["
        Key.RightBracket -> if (event.isShiftPressed) "}" else "]"
        Key.Backslash -> if (event.isShiftPressed) "|" else "\\"
        Key.Semicolon -> if (event.isShiftPressed) ":" else ";"
        Key.Apostrophe -> if (event.isShiftPressed) "\"" else "'"
        Key.Comma -> if (event.isShiftPressed) "<" else ","
        Key.Period -> if (event.isShiftPressed) ">" else "."
        Key.Slash -> if (event.isShiftPressed) "?" else "/"
        Key.Grave -> if (event.isShiftPressed) "~" else "`"
        else -> null
    }
}
