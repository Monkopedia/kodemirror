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

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asInsert

/**
 * Copy the current selection to the system clipboard.
 */
val clipboardCopy: (EditorSession) -> Boolean = { view ->
    val sel = view.state.selection.main
    if (!sel.empty) {
        val text = view.state.doc.sliceString(sel.from, sel.to)
        platformClipboardSet(text)
    }
    true
}

/**
 * Cut the current selection: copy to clipboard and delete.
 */
val clipboardCut: (EditorSession) -> Boolean = { view ->
    val sel = view.state.selection.main
    if (!sel.empty) {
        val text = view.state.doc.sliceString(sel.from, sel.to)
        platformClipboardSet(text)
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
    val text = platformClipboardGet()
    if (text != null && text.isNotEmpty()) {
        val sel = view.state.selection.main
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = sel.from,
                    to = sel.to,
                    insert = text.asInsert()
                )
            )
        )
    }
    true
}
