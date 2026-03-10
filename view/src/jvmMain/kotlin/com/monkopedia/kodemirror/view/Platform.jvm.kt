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
import androidx.compose.ui.input.key.utf16CodePoint
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

internal actual fun platformOsName(): String = System.getProperty("os.name") ?: "Linux"

internal actual fun keyEventCharacter(event: KeyEvent): Char? {
    val codePoint = event.utf16CodePoint
    if (codePoint == 0) return null
    val char = codePoint.toChar()
    if (char.isISOControl()) return null
    return char
}

internal actual fun platformClipboardGet(): String? = try {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.getData(DataFlavor.stringFlavor) as? String
} catch (_: Exception) {
    null
}

internal actual fun platformClipboardSet(text: String) {
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    } catch (_: Exception) {
        // Clipboard not available
    }
}
