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
import kotlin.JsFun

@JsFun(
    """() => {
    var ua = navigator.userAgent.toLowerCase();
    if (ua.indexOf('mac') !== -1) return 'Mac';
    if (ua.indexOf('win') !== -1) return 'Windows';
    return 'Linux';
}"""
)
private external fun detectOsFromBrowser(): String

@JsFun("(text) => { navigator.clipboard.writeText(text); }")
private external fun jsClipboardWrite(text: String)

internal actual fun platformOsName(): String = detectOsFromBrowser()

internal actual fun keyEventCharacter(event: KeyEvent): Char? {
    // On wasmJs, character input flows through BasicTextField's onValueChange
    return null
}

internal actual fun keyEventLayoutKey(event: KeyEvent): String? {
    // On wasmJs, Compose doesn't reliably expose the layout-aware character
    // from keydown events. Fall back to physical key names for now.
    return null
}

internal actual fun platformClipboardGet(): String? {
    // Clipboard API on web is async; not supported in synchronous context
    return null
}

internal actual fun platformClipboardSet(text: String) {
    try {
        jsClipboardWrite(text)
    } catch (_: Throwable) {
        // Clipboard API may not be available in all contexts
    }
}
