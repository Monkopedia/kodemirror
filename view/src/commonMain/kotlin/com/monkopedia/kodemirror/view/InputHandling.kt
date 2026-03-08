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

/**
 * Build a string key identifier from a Compose [KeyEvent].
 *
 * Produces something like `"Ctrl-Alt-Enter"` in the same format used by
 * [KeyBinding.key].
 */
fun keyEventToName(event: KeyEvent): String {
    val parts = mutableListOf<String>()
    if (event.isAltPressed) parts.add("Alt")
    if (event.isCtrlPressed) parts.add("Ctrl")
    if (event.isMetaPressed) parts.add("Meta")
    if (event.isShiftPressed) parts.add("Shift")
    val keyName = keyName(event.key)
    parts.add(keyName)
    return parts.joinToString("-")
}

private fun keyName(key: Key): String = when (key) {
    Key.Enter -> "Enter"
    Key.Escape -> "Escape"
    Key.Tab -> "Tab"
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
 * Resolve the effective key name for a binding on the current platform.
 *
 * Prefers platform-specific overrides (mac/linux/win) when available,
 * falling back to the generic [KeyBinding.key].
 */
private fun resolveBindingKey(binding: KeyBinding): String? {
    val osName = currentOs
    val platformKey = when {
        osName.contains("mac", ignoreCase = true) ||
            osName.contains("darwin", ignoreCase = true) -> binding.mac
        osName.contains("win", ignoreCase = true) -> binding.win
        osName.contains("linux", ignoreCase = true) ||
            osName.contains("nux", ignoreCase = true) -> binding.linux
        else -> null
    }
    return platformKey ?: binding.key
}

/**
 * The current operating system name, used to resolve platform-specific
 * key bindings. Set this to override automatic detection.
 *
 * Recognized values: `"Mac"`, `"Linux"`, `"Windows"`.
 */
var currentOs: String = platformOsName()

/**
 * Dispatch a key event to the view's key bindings.
 *
 * Returns true if the event was handled (the composable should then call
 * `onKeyEvent { true }` to consume the event).
 *
 * Checks platform-specific key overrides (mac/linux/win), the shift
 * variant, and the any handler.
 */
fun handleKeyEvent(view: EditorSession, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val name = keyEventToName(event)
    val isShift = event.isShiftPressed
    val bindings = view.state.facet(keymap)

    // Build the name without Shift for shift-variant matching
    val nameWithoutShift = if (isShift) {
        keyEventToNameWithoutShift(event)
    } else {
        null
    }

    for (binding in bindings) {
        val bindingKey = resolveBindingKey(binding) ?: continue

        // Direct match: full name matches binding key
        if (bindingKey == name) {
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
    val name = keyName(event.key)
    parts.add(name)
    return parts.joinToString("-")
}

/**
 * Handle a tap/click at the given document-space [offset].
 *
 * Moves the cursor to the tapped position by dispatching a transaction that
 * sets the selection.
 */
fun handleTap(view: EditorSession, offset: Offset) {
    val pos = view.posAtCoords(offset.x, offset.y) ?: return
    view.dispatch(
        com.monkopedia.kodemirror.state.TransactionSpec(
            selection = com.monkopedia.kodemirror.state.SelectionSpec.CursorSpec(pos)
        )
    )
}

/**
 * Handle a drag gesture for click-and-drag selection.
 *
 * @param view   The editor view.
 * @param start  The document-space coordinate where the drag started.
 * @param current The current drag position.
 */
fun handleDrag(view: EditorSession, start: Offset, current: Offset) {
    val anchor = view.posAtCoords(start.x, start.y) ?: return
    val head = view.posAtCoords(current.x, current.y) ?: return
    view.dispatch(
        com.monkopedia.kodemirror.state.TransactionSpec(
            selection = com.monkopedia.kodemirror.state.SelectionSpec.EditorSelectionSpec(
                com.monkopedia.kodemirror.state.EditorSelection.single(anchor, head)
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
fun handleRectangularDrag(view: EditorSession, start: Offset, current: Offset) {
    val doc = view.state.doc
    val startPos = view.posAtCoords(start.x, start.y) ?: return
    val currentPos = view.posAtCoords(current.x, current.y) ?: return

    val startLine = doc.lineAt(startPos)
    val currentLine = doc.lineAt(currentPos)

    val startCol = startPos - startLine.from
    val currentCol = currentPos - currentLine.from

    val minLineNum = minOf(startLine.number, currentLine.number)
    val maxLineNum = maxOf(startLine.number, currentLine.number)
    val minCol = minOf(startCol, currentCol)
    val maxCol = maxOf(startCol, currentCol)

    val ranges = mutableListOf<com.monkopedia.kodemirror.state.SelectionRange>()
    for (lineNum in minLineNum..maxLineNum) {
        val line = doc.line(lineNum)
        val lineLen = line.text.length
        val from = line.from + minOf(minCol, lineLen)
        val to = line.from + minOf(maxCol, lineLen)
        ranges.add(
            com.monkopedia.kodemirror.state.EditorSelection.range(
                from,
                to
            )
        )
    }

    if (ranges.isEmpty()) return

    view.dispatch(
        com.monkopedia.kodemirror.state.TransactionSpec(
            selection = com.monkopedia.kodemirror.state.SelectionSpec
                .EditorSelectionSpec(
                    com.monkopedia.kodemirror.state.EditorSelection.create(
                        ranges
                    )
                )
        )
    )
}
