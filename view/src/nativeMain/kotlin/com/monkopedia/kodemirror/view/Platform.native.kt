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
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.utf16CodePoint

internal actual fun platformOsName(): String = "Mac"

internal actual fun keyEventCharacter(event: KeyEvent): Char? {
    val codePoint = event.utf16CodePoint
    if (codePoint == 0) return null
    val char = codePoint.toChar()
    if (char.isISOControl()) return null
    return char
}

actual fun keyEventLayoutKey(event: KeyEvent): String? {
    val codePoint = event.utf16CodePoint
    if (codePoint == 0) return null
    if (codePoint in 1..26 && event.isCtrlPressed) {
        return ('a' + (codePoint - 1)).toString()
    }
    val char = codePoint.toChar()
    if (char.isISOControl()) return null
    return char.toString()
}

actual class PlatformKeyHandlerToken

internal actual fun platformRegisterKeyHandler(
    handler: (key: String, ctrl: Boolean, alt: Boolean, meta: Boolean, shift: Boolean) -> Boolean
): PlatformKeyHandlerToken {
    // No-op on native — Compose handles all keys natively
    return PlatformKeyHandlerToken()
}

internal actual fun platformUnregisterKeyHandler(token: PlatformKeyHandlerToken) {
    // No-op
}

internal actual fun platformFocusInput() {
    // No-op on native — Compose manages focus natively
}

internal actual fun platformWriteClipboard(text: String): Boolean {
    // No-op on native — Compose's ClipboardManager writes the system
    // clipboard synchronously, so the command layer handles it directly.
    return false
}

internal actual fun platformReadClipboard(onResult: (String) -> Unit): Boolean {
    // No-op on native — the command layer reads the Compose ClipboardManager
    // synchronously, so no async priming is needed.
    return false
}
