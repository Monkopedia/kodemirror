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
package com.monkopedia.kodemirror.commands

import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.keymapOf

/**
 * Essential key bindings for basic editing: arrow keys, deletion, selection,
 * Enter, and select-all.
 */
val standardKeymap: List<KeyBinding> = listOf(
    // Cursor movement
    KeyBinding(key = "ArrowLeft", run = cursorCharLeft, shift = selectCharLeft),
    KeyBinding(key = "ArrowRight", run = cursorCharRight, shift = selectCharRight),
    KeyBinding(key = "ArrowUp", run = cursorLineUp, shift = selectLineUp),
    KeyBinding(key = "ArrowDown", run = cursorLineDown, shift = selectLineDown),
    KeyBinding(
        key = "Ctrl-ArrowLeft",
        mac = "Alt-ArrowLeft",
        run = cursorGroupLeft,
        shift = selectGroupLeft
    ),
    KeyBinding(
        key = "Ctrl-ArrowRight",
        mac = "Alt-ArrowRight",
        run = cursorGroupRight,
        shift = selectGroupRight
    ),
    KeyBinding(key = "Home", run = cursorLineStart, shift = selectLineStart),
    KeyBinding(key = "End", run = cursorLineEnd, shift = selectLineEnd),
    KeyBinding(
        key = "Ctrl-Home",
        mac = "Meta-Home",
        run = cursorDocStart,
        shift = selectDocStart
    ),
    KeyBinding(
        key = "Ctrl-End",
        mac = "Meta-End",
        run = cursorDocEnd,
        shift = selectDocEnd
    ),
    KeyBinding(key = "PageUp", run = cursorPageUp),
    KeyBinding(key = "PageDown", run = cursorPageDown),

    // Deletion
    KeyBinding(key = "Backspace", run = deleteCharBackward),
    KeyBinding(key = "Delete", run = deleteCharForward),
    KeyBinding(
        key = "Ctrl-Backspace",
        mac = "Alt-Backspace",
        run = deleteGroupBackward
    ),
    KeyBinding(
        key = "Ctrl-Delete",
        mac = "Alt-Delete",
        run = deleteGroupForward
    ),

    // Text insertion
    KeyBinding(key = "Enter", run = insertNewlineAndIndent),

    // Selection
    KeyBinding(key = "Ctrl-a", mac = "Meta-a", run = selectAll)
)

/**
 * Extended keymap that adds line operations, indent commands, and more
 * on top of [standardKeymap].
 */
val defaultKeymap: List<KeyBinding> = standardKeymap + commentKeymap + listOf(
    // Line operations
    KeyBinding(key = "Alt-ArrowUp", run = moveLineUp),
    KeyBinding(key = "Alt-ArrowDown", run = moveLineDown),
    KeyBinding(key = "Shift-Alt-ArrowUp", run = copyLineUp),
    KeyBinding(key = "Shift-Alt-ArrowDown", run = copyLineDown),

    // Indent
    KeyBinding(key = "Ctrl-]", run = indentMore),
    KeyBinding(key = "Ctrl-[", run = indentLess),

    // Delete line
    KeyBinding(key = "Ctrl-Shift-k", run = deleteLine),

    // Transpose
    KeyBinding(key = "Ctrl-t", run = transposeChars),

    // Bracket matching
    KeyBinding(
        key = "Ctrl-Shift-\\",
        mac = "Meta-Shift-\\",
        run = cursorMatchingBracket,
        shift = selectMatchingBracket
    ),

    // Select next occurrence
    KeyBinding(key = "Ctrl-d", mac = "Meta-d", run = selectNextOccurrence)
)

/**
 * Emacs-style key bindings.
 *
 * - Ctrl-b/f → char left/right
 * - Ctrl-p/n → line up/down
 * - Ctrl-a/e → line start/end
 * - Ctrl-d   → delete forward
 * - Ctrl-h   → delete backward
 * - Ctrl-k   → delete to line end
 * - Ctrl-t   → transpose
 */
val emacsStyleKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Ctrl-b", run = cursorCharLeft, shift = selectCharLeft),
    KeyBinding(key = "Ctrl-f", run = cursorCharRight, shift = selectCharRight),
    KeyBinding(key = "Ctrl-p", run = cursorLineUp, shift = selectLineUp),
    KeyBinding(key = "Ctrl-n", run = cursorLineDown, shift = selectLineDown),
    KeyBinding(key = "Ctrl-a", run = cursorLineStart, shift = selectLineStart),
    KeyBinding(key = "Ctrl-e", run = cursorLineEnd, shift = selectLineEnd),
    KeyBinding(key = "Ctrl-d", run = deleteCharForward),
    KeyBinding(key = "Ctrl-h", run = deleteCharBackward),
    KeyBinding(key = "Ctrl-k", run = deleteToLineEnd),
    KeyBinding(key = "Ctrl-t", run = transposeChars)
)

/**
 * Key bindings that use Tab/Shift-Tab for indentation.
 *
 * Usage: add `keymapOf(*indentWithTab.toTypedArray())` to the editor
 * extensions.
 */
val indentWithTab: List<KeyBinding> = listOf(
    KeyBinding(key = "Tab", run = indentMore),
    KeyBinding(key = "Shift-Tab", run = indentLess)
)

/**
 * Convenience function to create an extension from the [standardKeymap].
 */
fun standardKeymapExtension(): Extension = keymapOf(*standardKeymap.toTypedArray())

/**
 * Convenience function to create an extension from the [defaultKeymap].
 */
fun defaultKeymapExtension(): Extension = keymapOf(*defaultKeymap.toTypedArray())
