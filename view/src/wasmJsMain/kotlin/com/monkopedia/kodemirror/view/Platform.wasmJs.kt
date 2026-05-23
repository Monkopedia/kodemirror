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
import androidx.compose.ui.input.key.isShiftPressed
import kotlin.JsFun
import kotlin.js.JsString

@JsFun(
    """() => {
    var ua = navigator.userAgent.toLowerCase();
    if (ua.indexOf('mac') !== -1) return 'Mac';
    if (ua.indexOf('win') !== -1) return 'Windows';
    return 'Linux';
}"""
)
private external fun detectOsFromBrowser(): String

// Capture the browser's layout-aware event.key on every keydown.
// Installed eagerly at module load so it's ready before the first key event.
// Uses capture phase on document so it fires before Skiko/Compose processes
// the event on the canvas.
//
// Routes ALL keys through __kodeKeyCallback because Playwright (and some
// headless environments) dispatch key events to BODY rather than the canvas
// in the shadow DOM, so Skiko never generates Compose KeyEvents for them.
// The callback receives (key, ctrlKey, altKey, metaKey, shiftKey) and returns
// true if the editor's keymap actually handled the key.
//
// preventDefault() is called ONLY when the callback reports the key was
// handled. This is critical: previously every modified/special key was
// prevented unconditionally, which swallowed browser-reserved shortcuts the
// editor has no binding for (Ctrl+Tab, Ctrl+W, Ctrl+1-9, etc.) so the browser
// never saw them (#49). Unhandled keys must fall through to the browser.
//
// For real users (events targeting the canvas), both this callback AND
// onPreviewKeyEvent may fire; a Kotlin-side flag prevents double-handling.
@JsFun(
    """() => {
    globalThis.__kodeKey = '';
    globalThis.__kodeKeyCallback = null;
    document.addEventListener('keydown', function(e) {
        globalThis.__kodeKey = e.key;
        // Route all keys through the Kotlin callback. This is necessary
        // because Playwright (and some headless environments) dispatch key
        // events to BODY rather than the canvas in the shadow DOM, so Skiko
        // never converts them to Compose KeyEvents. The Kotlin callback
        // sets a flag so onPreviewKeyEvent skips if Skiko also processes
        // the same event (preventing double-handling).
        //
        // Only consume the event (preventDefault) when the editor's keymap
        // actually handled it. Keys with no editor binding propagate to the
        // browser so reserved shortcuts (Ctrl+Tab, Ctrl+W, ...) still work.
        if (globalThis.__kodeKeyCallback) {
            var handled = globalThis.__kodeKeyCallback(
                e.key, e.ctrlKey, e.altKey, e.metaKey, e.shiftKey
            );
            if (handled) {
                e.preventDefault();
            }
        }
    }, true);
}"""
)
private external fun installKeyCapture()

/**
 * Token identifying a registered platform key handler so it can be
 * individually unregistered without affecting other editors.
 */
actual class PlatformKeyHandlerToken(
    internal val handler: (
        key: String,
        ctrl: Boolean,
        alt: Boolean,
        meta: Boolean,
        shift: Boolean
    ) -> Boolean
)

/** All currently registered key handlers, in registration order. */
private val keyHandlers = mutableListOf<PlatformKeyHandlerToken>()

/** Whether the JS-side dispatcher has been installed. */
private var jsDispatcherInstalled = false

@JsFun(
    """(cb) => {
    globalThis.__kodeKeyCallback = function(key, ctrl, alt, meta, shift) {
        return cb(key, ctrl, alt, meta, shift);
    };
}"""
)
private external fun jsSetKeyCallback(
    callback: (JsString, Boolean, Boolean, Boolean, Boolean) -> Boolean
)

@JsFun("() => { globalThis.__kodeKeyCallback = null; }")
private external fun jsClearKeyCallback()

/**
 * Install the JS-side dispatcher that fans out to all registered Kotlin
 * handlers. Installed lazily on the first [platformRegisterKeyHandler] call.
 */
private fun ensureJsDispatcher() {
    if (jsDispatcherInstalled) return
    jsDispatcherInstalled = true
    jsSetKeyCallback { key, ctrl, alt, meta, shift ->
        val keyStr = key.toString()
        for (token in keyHandlers) {
            if (token.handler(keyStr, ctrl, alt, meta, shift)) return@jsSetKeyCallback true
        }
        false
    }
}

@JsFun("() => globalThis.__kodeKey || ''")
private external fun readCapturedKey(): String

/**
 * Focus the canvas in the shadow DOM so keyboard events reach Skiko's
 * event handler. Skiko processes key events from the canvas element
 * and converts them to Compose KeyEvents that flow through
 * onPreviewKeyEvent.
 */
@JsFun(
    """() => {
    var shadow = document.body.shadowRoot;
    if (shadow) {
        var canvas = shadow.querySelector('canvas');
        if (canvas) canvas.focus();
    }
}"""
)
private external fun platformFocusCanvas()

internal actual fun platformRegisterKeyHandler(
    handler: (key: String, ctrl: Boolean, alt: Boolean, meta: Boolean, shift: Boolean) -> Boolean
): PlatformKeyHandlerToken {
    ensureJsDispatcher()
    val token = PlatformKeyHandlerToken(handler)
    keyHandlers.add(token)
    return token
}

internal actual fun platformUnregisterKeyHandler(token: PlatformKeyHandlerToken) {
    keyHandlers.remove(token)
    if (keyHandlers.isEmpty()) {
        jsClearKeyCallback()
        jsDispatcherInstalled = false
    }
}

internal actual fun platformFocusInput() {
    platformFocusCanvas()
}

// Write to the system clipboard via the async Clipboard API. This must be
// called synchronously inside the user gesture (the keydown handler) so the
// browser's transient-activation check passes; the returned promise resolves
// later. Falls back to the legacy execCommand('copy') path via a hidden
// textarea when navigator.clipboard is unavailable (insecure context, older
// browser).
@JsFun(
    """(text) => {
    try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).catch(function() {});
            return true;
        }
    } catch (e) {}
    try {
        var ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.focus();
        ta.select();
        var ok = document.execCommand('copy');
        document.body.removeChild(ta);
        return ok;
    } catch (e) {
        return false;
    }
}"""
)
private external fun jsWriteClipboard(text: String): Boolean

// Read the system clipboard asynchronously and deliver the text to the Kotlin
// callback when the promise resolves. Returns true if a read was started.
@JsFun(
    """(cb) => {
    try {
        if (navigator.clipboard && navigator.clipboard.readText) {
            navigator.clipboard.readText().then(function(text) {
                cb(text == null ? '' : text);
            }).catch(function() {});
            return true;
        }
    } catch (e) {}
    return false;
}"""
)
private external fun jsReadClipboard(callback: (JsString) -> Unit): Boolean

internal actual fun platformWriteClipboard(text: String): Boolean = jsWriteClipboard(text)

internal actual fun platformReadClipboard(onResult: (String) -> Unit): Boolean =
    jsReadClipboard { text -> onResult(text.toString()) }

// Eagerly install the capture listener when this file is first loaded.
// platformOsName() is called during currentOs initialization (before any
// key events), which triggers loading of this file and runs this initializer.
@Suppress("unused")
private val keyCaptureInstalled: Boolean = run {
    installKeyCapture()
    true
}

// US-layout shift map for Playwright compatibility.
// Playwright sends the unshifted e.key for Shift+digit/symbol combinations.
// A real browser would send the shifted character (e.g., "$" for Shift+4).
private val SHIFT_MAP = mapOf(
    '1' to '!', '2' to '@', '3' to '#', '4' to '$', '5' to '%',
    '6' to '^', '7' to '&', '8' to '*', '9' to '(', '0' to ')',
    '-' to '_', '=' to '+', '[' to '{', ']' to '}', '\\' to '|',
    ';' to ':', '\'' to '"', ',' to '<', '.' to '>', '/' to '?',
    '`' to '~'
)

internal actual fun platformOsName(): String = detectOsFromBrowser()

internal actual fun keyEventCharacter(event: KeyEvent): Char? {
    // On wasmJs, character input flows through BasicTextField's onValueChange
    return null
}

actual fun keyEventLayoutKey(event: KeyEvent): String? {
    // Reference keyCaptureInstalled to prevent dead-code elimination of the
    // property initializer that installs the document keydown listener.
    keyCaptureInstalled
    val key = readCapturedKey()
    // Browser's event.key is a single character for printable keys ("x", "z")
    // and a longer string for special keys ("Enter", "Tab").
    if (key.length != 1) return null
    // Playwright's keyboard.press("Shift+j") sends e.key="j" (not "J") and
    // keyboard.press("Shift+4") sends e.key="4" (not "$"). A real browser
    // would give the shifted character. Use Compose's isShiftPressed + a
    // shift map to produce the correct character.
    if (event.isShiftPressed) {
        return SHIFT_MAP[key[0]]?.toString() ?: key.uppercase()
    }
    return key
}
