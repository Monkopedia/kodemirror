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

import androidx.compose.ui.text.AnnotatedString
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asInsert

/**
 * Write [text] to the system clipboard, bridging the platform gap.
 *
 * On wasmJs [platformWriteClipboard] kicks off `navigator.clipboard.writeText`
 * synchronously inside the originating key-event gesture (returns true). On
 * JVM/Desktop it returns false, so we fall back to Compose's `ClipboardManager`,
 * which writes the AWT system clipboard synchronously. In both cases the
 * in-editor [EditorSessionImpl.internalClipboard] buffer is updated as an
 * immediate, always-reliable fallback for paste within the same session.
 */
private fun writeClipboard(impl: EditorSessionImpl?, text: String) {
    impl?.internalClipboard = text
    val handledByPlatform = platformWriteClipboard(text)
    if (!handledByPlatform) {
        try {
            impl?.clipboardManager?.setText(AnnotatedString(text))
        } catch (_: Throwable) {
            // Best-effort system clipboard write; the internal buffer is the
            // reliable fallback.
        }
    }
}

/**
 * Copy the current selection to the system clipboard.
 */
val clipboardCopy: (EditorSession) -> Boolean = { view ->
    val impl = view as? EditorSessionImpl
    val sel = view.state.selection.main
    if (!sel.empty) {
        writeClipboard(impl, view.state.doc.sliceString(sel.from, sel.to))
    }
    true
}

/**
 * Cut the current selection: copy to clipboard and delete.
 */
val clipboardCut: (EditorSession) -> Boolean = { view ->
    val impl = view as? EditorSessionImpl
    val sel = view.state.selection.main
    if (!sel.empty) {
        writeClipboard(impl, view.state.doc.sliceString(sel.from, sel.to))
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(from = sel.from, to = sel.to)
            )
        )
    }
    true
}

/**
 * Paste text from the system clipboard at the current cursor position.
 */
val clipboardPaste: (EditorSession) -> Boolean = { view ->
    val impl = view as? EditorSessionImpl
    // On wasmJs the browser Clipboard API read is asynchronous, so it cannot
    // deliver a value within this synchronous key-event handler. Kick off an
    // async read to refresh the internal buffer for the *next* paste, then use
    // the best value currently available (Compose ClipboardManager on JVM, or
    // the internal buffer — which holds the most recent in-editor copy/cut and
    // any previously-read external clipboard contents).
    platformReadClipboard { external ->
        if (external.isNotEmpty()) impl?.internalClipboard = external
    }
    val text = try {
        impl?.clipboardManager?.getText()?.text
    } catch (_: Throwable) {
        null
    } ?: impl?.internalClipboard
    if (text != null && text.isNotEmpty()) {
        val sel = view.state.selection.main
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = sel.from,
                    to = sel.to,
                    insert = text.asInsert()
                ),
                selection = SelectionSpec.CursorSpec(
                    DocPos(sel.from.value + text.length)
                )
            )
        )
        true
    } else {
        // No clipboard text available.
        // Return false so the event propagates to the browser's native
        // paste mechanism via the hidden BasicTextField.
        false
    }
}
