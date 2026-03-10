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

import androidx.compose.ui.input.key.KeyEvent

internal expect fun platformOsName(): String

/**
 * Extract the printable character from a [KeyEvent], or null if the event
 * does not represent a printable character (e.g., arrow keys, modifiers).
 */
internal expect fun keyEventCharacter(event: KeyEvent): Char?

/**
 * Extract the layout-aware key name from a [KeyEvent] for shortcut matching.
 *
 * On a Dvorak layout, pressing the physical QWERTY 'B' key (which produces 'x'
 * on Dvorak) should return `"x"`, not `"b"`. This enables keyboard shortcuts
 * to follow the user's layout rather than hardcoded QWERTY positions.
 *
 * When Ctrl is held, the OS typically sends control characters (1-26 for a-z);
 * this function recovers the original letter.
 *
 * Returns null if the layout-aware character cannot be determined, in which case
 * callers should fall back to the physical key name.
 */
internal expect fun keyEventLayoutKey(event: KeyEvent): String?

/** Read text from the system clipboard. Returns null if unavailable. */
internal expect fun platformClipboardGet(): String?

/** Write text to the system clipboard. */
internal expect fun platformClipboardSet(text: String)
