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

/** Read text from the system clipboard. Returns null if unavailable. */
internal expect fun platformClipboardGet(): String?

/** Write text to the system clipboard. */
internal expect fun platformClipboardSet(text: String)
