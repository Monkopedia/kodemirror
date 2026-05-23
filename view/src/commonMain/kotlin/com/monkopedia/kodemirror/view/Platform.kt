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
expect fun keyEventLayoutKey(event: KeyEvent): String?

/**
 * Token returned by [platformRegisterKeyHandler] that allows unregistering
 * a specific handler without affecting other editors' handlers.
 */
expect class PlatformKeyHandlerToken

/**
 * Register a platform-level key handler that receives raw keyboard events.
 *
 * On wasmJs, Skiko doesn't generate Compose KeyEvents for all keys (e.g.,
 * symbol keys like `/`, `?` on the canvas). This handler receives ALL
 * keydown events from the document-level listener and can process keys
 * that Skiko misses. The handler should return true if the key was handled.
 *
 * Multiple handlers can be registered (one per editor instance). When a key
 * event arrives, handlers are called in registration order; the first handler
 * that returns true wins and subsequent handlers are skipped.
 *
 * On JVM, this is a no-op since Compose handles all keys natively.
 *
 * @return A token that can be passed to [platformUnregisterKeyHandler] to
 *   remove this specific handler.
 */
internal expect fun platformRegisterKeyHandler(
    handler: (key: String, ctrl: Boolean, alt: Boolean, meta: Boolean, shift: Boolean) -> Boolean
): PlatformKeyHandlerToken

/**
 * Unregister the key handler identified by [token].
 */
internal expect fun platformUnregisterKeyHandler(token: PlatformKeyHandlerToken)

/**
 * Ensure the editor's backing text input element has platform focus.
 *
 * On wasmJs, Compose's FocusRequester.requestFocus() gives Compose-internal
 * focus but doesn't always move DOM focus to the textarea that Skiko creates.
 * This function explicitly focuses the textarea in the shadow DOM.
 *
 * On JVM/Desktop, this is a no-op since Compose manages focus natively.
 */
internal expect fun platformFocusInput()

/**
 * Write [text] to the underlying system clipboard.
 *
 * On wasmJs the canvas-rendered editor has no DOM `contenteditable`, so the
 * browser's native copy/cut machinery has nothing to read. This bridges the
 * copy/cut commands to `navigator.clipboard.writeText`, which is asynchronous
 * but must be invoked synchronously inside the originating user gesture (the
 * keydown handler) to satisfy the browser's transient-activation requirement.
 * The write itself completes asynchronously; the in-editor buffer is kept as
 * an immediate fallback.
 *
 * On JVM/Desktop this is a no-op — Compose's `ClipboardManager` writes to the
 * system clipboard synchronously, so the command layer handles it directly.
 *
 * @return true if the platform attempted a system-clipboard write, false if it
 *   delegated entirely to the Compose `ClipboardManager` (e.g. JVM).
 */
internal expect fun platformWriteClipboard(text: String): Boolean

/**
 * Asynchronously read the system clipboard and deliver the contents to
 * [onResult]. Because the browser clipboard API is async, the value cannot be
 * returned synchronously inside a key-event handler; callers use this to prime
 * the internal buffer so the *next* paste reflects external clipboard contents.
 *
 * On JVM/Desktop this is a no-op — the command layer reads the Compose
 * `ClipboardManager` synchronously.
 *
 * @return true if an asynchronous read was started, false otherwise (e.g. JVM).
 */
internal expect fun platformReadClipboard(onResult: (String) -> Unit): Boolean
