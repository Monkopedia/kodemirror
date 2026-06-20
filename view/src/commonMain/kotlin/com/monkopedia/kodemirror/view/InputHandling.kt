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
package com.monkopedia.kodemirror.view

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.SelectionRange
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asInsert

/**
 * Build a string key identifier from a Compose [KeyEvent].
 *
 * Produces something like `"Ctrl-Alt-Enter"` in the same format used by
 * [KeyBinding.key].
 */
internal fun keyEventToName(event: KeyEvent): String {
    val parts = mutableListOf<String>()
    if (event.isAltPressed) parts.add("Alt")
    if (event.isCtrlPressed) parts.add("Ctrl")
    if (event.isMetaPressed) parts.add("Meta")
    if (event.isShiftPressed) parts.add("Shift")
    // Special keys (Enter, Tab, arrows, etc.) always use physical key names.
    // Letter/digit/symbol keys prefer the layout-aware name so shortcuts
    // respect the user's keyboard layout (e.g. Dvorak).
    val keyName = if (isSpecialKey(event.key)) {
        keyName(event.key)
    } else {
        val layoutKey = keyEventLayoutKey(event)?.lowercase()
        // Normalize space character to "Space" for consistent key binding
        // matching (browser reports " " but bindings use "Space").
        if (layoutKey == " ") "Space" else layoutKey ?: keyName(event.key)
    }
    parts.add(keyName)
    return parts.joinToString("-")
}

private fun keyName(key: Key): String = when (key) {
    Key.Enter -> "Enter"
    Key.Escape -> "Escape"
    Key.Tab -> "Tab"
    Key.Spacebar -> "Space"
    Key.Backspace -> "Backspace"
    Key.Delete -> "Delete"
    Key.DirectionLeft -> "ArrowLeft"
    Key.DirectionRight -> "ArrowRight"
    Key.DirectionUp -> "ArrowUp"
    Key.DirectionDown -> "ArrowDown"
    Key.Home -> "Home"
    Key.MoveEnd -> "End"
    Key.PageUp -> "PageUp"
    Key.PageDown -> "PageDown"
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
    else -> key.toString()
}

/**
 * Whether this [Key] represents a special (non-character) key whose name
 * should always come from the physical key code, not the keyboard layout.
 */
private fun isSpecialKey(key: Key): Boolean = when (key) {
    Key.Enter, Key.Escape, Key.Tab, Key.Spacebar, Key.Backspace, Key.Delete,
    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
    Key.Home, Key.MoveEnd, Key.PageUp, Key.PageDown,
    Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6,
    Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12,
    Key.ShiftLeft, Key.ShiftRight,
    Key.CtrlLeft, Key.CtrlRight,
    Key.AltLeft, Key.AltRight,
    Key.MetaLeft, Key.MetaRight -> true
    else -> false
}

/**
 * Resolve the effective key name for a binding on the current platform.
 *
 * Prefers platform-specific overrides (mac/linux/win) when available,
 * falling back to the generic [KeyBinding.key]. The `Mod-` prefix is
 * resolved to `Meta-` on macOS and `Ctrl-` on other platforms, matching
 * the CodeMirror convention.
 */
private fun resolveBindingKey(binding: KeyBinding): String? {
    val isMac = currentOs.contains("mac", ignoreCase = true) ||
        currentOs.contains("darwin", ignoreCase = true)
    val platformKey = when {
        isMac -> binding.mac
        currentOs.contains("win", ignoreCase = true) -> binding.win
        currentOs.contains("linux", ignoreCase = true) ||
            currentOs.contains("nux", ignoreCase = true) -> binding.linux
        else -> null
    }
    val key = platformKey ?: binding.key ?: return null
    val resolved = if ("Mod" in key) {
        key.replace("Mod", if (isMac) "Meta" else "Ctrl")
    } else {
        key
    }
    return normalizeKeyName(resolved)
}

/**
 * Normalize modifier order in a key name to match [keyEventToName] output.
 *
 * Ensures modifiers always appear in the order Alt-Ctrl-Meta-Shift,
 * regardless of the order they appear in the binding string.
 */
private fun normalizeKeyName(name: String): String {
    val parts = name.split("-")
    if (parts.size <= 1) return name
    val modifiers = mutableListOf<String>()
    val keyParts = mutableListOf<String>()
    for (part in parts) {
        when (part) {
            "Alt", "Ctrl", "Meta", "Shift" -> modifiers.add(part)
            else -> keyParts.add(part)
        }
    }
    if (modifiers.isEmpty()) return name
    // Sort modifiers in canonical order: Alt, Ctrl, Meta, Shift
    val order = listOf("Alt", "Ctrl", "Meta", "Shift")
    modifiers.sortBy { order.indexOf(it) }
    return (modifiers + keyParts).joinToString("-")
}

/**
 * The current operating system name, used to resolve platform-specific
 * key bindings. Set this to override automatic detection.
 *
 * Recognized values: `"Mac"`, `"Linux"`, `"Windows"`.
 */
internal var currentOs: String = platformOsName()

/**
 * Dispatch a key event to the view's key bindings.
 *
 * Returns true if the event was handled (the composable should then call
 * `onKeyEvent { true }` to consume the event).
 *
 * Checks platform-specific key overrides (mac/linux/win), the shift
 * variant, and the any handler.
 */
internal fun handleKeyEvent(view: EditorSession, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val name = keyEventToName(event)
    val isShift = event.isShiftPressed
    // Reverse so later extensions (higher precedence) are checked first,
    // matching CM6's behavior where later keymap extensions override earlier ones.
    val bindings = view.state.facet(keymap).asReversed()

    // Build the name without Shift for shift-variant matching
    val nameWithoutShift = if (isShift) {
        keyEventToNameWithoutShift(event)
    } else {
        null
    }

    // CM6-style fallback: when Shift + modifier (Ctrl/Alt/Meta) is pressed
    // and the layout key is a shifted symbol (e.g., "|" from Shift+\), also
    // try matching with the unshifted base character and explicit Shift
    // modifier. This makes bindings like "Ctrl-Shift-\" work even though
    // the layout key is "|".
    val physicalKeyName: String? = if (
        isShift &&
        (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed)
    ) {
        val layoutKey = keyEventLayoutKey(event)
        if (layoutKey != null && layoutKey.length == 1) {
            val base = UNSHIFTED_SYMBOLS[layoutKey[0]]
            if (base != null && base.toString() != layoutKey) {
                // Build name with Shift + unshifted base
                buildString {
                    if (event.isAltPressed) append("Alt-")
                    if (event.isCtrlPressed) append("Ctrl-")
                    if (event.isMetaPressed) append("Meta-")
                    append("Shift-")
                    append(base)
                }
            } else {
                null
            }
        } else {
            null
        }
    } else {
        null
    }

    for (binding in bindings) {
        val bindingKey = resolveBindingKey(binding)

        // Catch-all: when `any` is set but no key is specified, call for every event
        if (bindingKey == null) {
            if (binding.any != null) {
                val result = binding.any.invoke(view, event)
                if (result) return true
            }
            continue
        }

        // Direct match: full name matches binding key
        if (bindingKey == name) {
            val result = binding.run?.invoke(view) ?: false
            if (result) return true
        }

        // Physical key fallback: try the unshifted base key with Shift
        if (physicalKeyName != null && bindingKey == physicalKeyName) {
            val result = binding.run?.invoke(view) ?: false
            if (result) return true
        }

        // Shift variant: event has Shift, and the base key (without Shift)
        // matches. Call binding.shift if available.
        if (isShift && nameWithoutShift != null &&
            binding.shift != null && bindingKey == nameWithoutShift
        ) {
            val result = binding.shift.invoke(view)
            if (result) return true
        }

        // Any handler: called for every key event matching the base key
        if (binding.any != null && bindingKey == name) {
            val result = binding.any.invoke(view, event)
            if (result) return true
        }
    }
    return false
}

/**
 * Build a key name from a [KeyEvent] with the Shift modifier stripped.
 */
private fun keyEventToNameWithoutShift(event: KeyEvent): String {
    val parts = mutableListOf<String>()
    if (event.isAltPressed) parts.add("Alt")
    if (event.isCtrlPressed) parts.add("Ctrl")
    if (event.isMetaPressed) parts.add("Meta")
    // Shift intentionally omitted
    val name = if (isSpecialKey(event.key)) {
        keyName(event.key)
    } else {
        val layoutKey = keyEventLayoutKey(event)
        if (layoutKey == " ") "Space" else layoutKey ?: keyName(event.key)
    }
    parts.add(name)
    return parts.joinToString("-")
}

/**
 * Handle character input from a key event.
 *
 * If the key event represents a printable character that wasn't consumed by
 * key bindings, insert it into the document at the current cursor position.
 *
 * @return true if a character was inserted (the event should be consumed).
 */
internal fun handleCharacterInput(view: EditorSession, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    // Don't insert for modified keys (Ctrl+A, Alt+X, etc.)
    if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return false
    val char = keyEventCharacter(event) ?: return false
    val sel = view.state.selection.main
    view.dispatch(
        TransactionSpec(
            changes = ChangeSpec.Single(
                from = sel.from,
                to = sel.to,
                insert = char.toString().asInsert()
            )
        )
    )
    return true
}

/**
 * Handle a raw key event from the platform's document-level listener.
 *
 * This is called when Skiko doesn't generate a Compose KeyEvent for a key
 * (e.g., symbol keys like `/`, `?`, `~` on the canvas). It routes the key
 * through the keymap's `any` handlers (which includes vim) and falls back
 * to text insertion for unconsumed printable characters.
 *
 * @return true if the key was handled (caller should preventDefault).
 */
internal fun handleRawKeyEvent(
    view: EditorSession,
    key: String,
    ctrl: Boolean,
    alt: Boolean,
    meta: Boolean,
    shift: Boolean
): Boolean {
    // Ignore modifier-only keys
    if (key in setOf("Shift", "Control", "Alt", "Meta", "CapsLock")) return false

    // Build the key name for keymap matching.
    val normalizedKey = if (shift && key.length == 1 && key[0].isLetter()) {
        key.uppercase()
    } else if (shift && key.length == 1) {
        SHIFTED_SYMBOLS[key[0]]?.toString() ?: key
    } else {
        key
    }

    // Build the full key name with modifiers (e.g., "Ctrl-Shift-\")
    val fullName = buildString {
        if (alt) append("Alt-")
        if (ctrl) append("Ctrl-")
        if (meta) append("Meta-")
        if (shift) append("Shift-")
        append(normalizedKey)
    }

    // Also build a physical-key fallback name for shifted symbols with
    // modifiers. When Shift + another modifier is pressed and the key
    // is a shifted symbol (e.g., "|" from Shift+\), try the unshifted
    // base character with explicit Shift (e.g., "Ctrl-Shift-\").
    val physicalName: String? = if (
        shift && (ctrl || alt || meta) &&
        normalizedKey.length == 1
    ) {
        val base = UNSHIFTED_SYMBOLS[normalizedKey[0]]
        if (base != null && base.toString() != normalizedKey) {
            buildString {
                if (alt) append("Alt-")
                if (ctrl) append("Ctrl-")
                if (meta) append("Meta-")
                append("Shift-")
                append(base)
            }
        } else {
            null
        }
    } else {
        null
    }

    // Build name without shift for shift-variant matching
    val nameWithoutShift: String? = if (shift) {
        buildString {
            if (alt) append("Alt-")
            if (ctrl) append("Ctrl-")
            if (meta) append("Meta-")
            append(normalizedKey)
        }
    } else {
        null
    }

    val bindings = view.state.facet(keymap).asReversed()
    for (binding in bindings) {
        val bindingKey = resolveBindingKey(binding)

        // Catch-all `any` handlers (like vim's)
        if (bindingKey == null) {
            if (binding.any != null) {
                val result =
                    binding.anyRaw?.invoke(view, normalizedKey, ctrl, alt, meta, shift)
                if (result == true) return true
            }
            continue
        }

        // Direct match
        if (bindingKey == fullName) {
            val result = binding.run?.invoke(view) ?: false
            if (result) return true
        }

        // Physical key fallback
        if (physicalName != null && bindingKey == physicalName) {
            val result = binding.run?.invoke(view) ?: false
            if (result) return true
        }

        // Shift variant: key matches without Shift, call binding.shift
        if (shift && nameWithoutShift != null &&
            binding.shift != null && bindingKey == nameWithoutShift
        ) {
            val result = binding.shift.invoke(view)
            if (result) return true
        }
    }

    // Not consumed by keymap -- check if it should be inserted as text
    if (!view.state.facet(editable)) return false
    val shouldSuppress = view.state.facet(inputSuppressor).any { it.invoke() }
    if (!shouldSuppress && normalizedKey.length == 1 &&
        !normalizedKey[0].isISOControl() &&
        !ctrl && !alt && !meta
    ) {
        val sel = view.state.selection.main
        val newCursor = DocPos(sel.from.value + normalizedKey.length)
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = sel.from,
                    to = sel.to,
                    insert = normalizedKey.asInsert()
                ),
                selection = SelectionSpec.CursorSpec(newCursor),
                userEvent = "input.type"
            )
        )
        return true
    }

    return false
}

// US-layout shift map for converting unshifted symbols to their shifted variant
private val SHIFTED_SYMBOLS = mapOf(
    '1' to '!', '2' to '@', '3' to '#', '4' to '$', '5' to '%',
    '6' to '^', '7' to '&', '8' to '*', '9' to '(', '0' to ')',
    '-' to '_', '=' to '+', '[' to '{', ']' to '}', '\\' to '|',
    ';' to ':', '\'' to '"', ',' to '<', '.' to '>', '/' to '?',
    '`' to '~'
)

// Reverse map: shifted symbol → unshifted base character.
// Used by the keymap fallback when Shift + modifier keys are pressed.
private val UNSHIFTED_SYMBOLS: Map<Char, Char> =
    SHIFTED_SYMBOLS.entries.associate { (k, v) -> v to k }

/**
 * Handle a tap/click at the given document-space [offset].
 *
 * Moves the cursor to the tapped position by dispatching a transaction that
 * sets the selection.
 */
internal fun handleTap(view: EditorSession, offset: Offset) {
    val pos = view.posAtCoords(offset.x, offset.y) ?: return
    view.dispatch(
        TransactionSpec(
            selection = SelectionSpec.CursorSpec(DocPos(pos))
        )
    )
}

/**
 * Handle a drag gesture for click-and-drag selection.
 *
 * @param view   The editor view.
 * @param start  The document-space coordinate where the drag started.
 * @param current The current drag position.
 * @param posAt  Resolves a viewport coordinate to a document offset. Callers
 *   pass the LIVE-layout resolver (`posFromVisibleItems`-backed) so drag
 *   selection tracks scrolling instead of using the stale-after-scroll cache
 *   (#165).
 */
internal fun handleDrag(
    view: EditorSession,
    start: Offset,
    current: Offset,
    posAt: (Offset) -> Int?
) {
    val anchor = posAt(start) ?: return
    val head = posAt(current) ?: return
    view.dispatch(
        TransactionSpec(
            selection = SelectionSpec.EditorSelectionSpec(
                EditorSelection.single(DocPos(anchor), DocPos(head))
            )
        )
    )
}

/**
 * Handle a rectangular (column-mode) drag selection.
 *
 * Creates one selection range per line between [start] and [current],
 * spanning the same column range on each line.
 *
 * @param view    The editor view.
 * @param start   The document-space coordinate where the drag started.
 * @param current The current drag position.
 */
internal fun handleRectangularDrag(
    view: EditorSession,
    start: Offset,
    current: Offset,
    posAt: (Offset) -> Int?
) {
    val doc = view.state.doc
    val startPos = posAt(start) ?: return
    val currentPos = posAt(current) ?: return

    val startDocPos = DocPos(startPos)
    val currentDocPos = DocPos(currentPos)

    val startLine = doc.lineAt(startDocPos)
    val currentLine = doc.lineAt(currentDocPos)

    val startCol = startDocPos - startLine.from
    val currentCol = currentDocPos - currentLine.from

    val minLineNum = minOf(startLine.number, currentLine.number)
    val maxLineNum = maxOf(startLine.number, currentLine.number)
    val minCol = minOf(startCol, currentCol)
    val maxCol = maxOf(startCol, currentCol)

    val ranges = mutableListOf<SelectionRange>()
    for (lineNum in minLineNum.value..maxLineNum.value) {
        val line = doc.line(LineNumber(lineNum))
        val lineLen = line.text.length
        val from = line.from + minOf(minCol, lineLen)
        val to = line.from + minOf(maxCol, lineLen)
        ranges.add(EditorSelection.range(from, to))
    }

    if (ranges.isEmpty()) return

    view.dispatch(
        TransactionSpec(
            selection = SelectionSpec.EditorSelectionSpec(
                EditorSelection.create(ranges)
            )
        )
    )
}
