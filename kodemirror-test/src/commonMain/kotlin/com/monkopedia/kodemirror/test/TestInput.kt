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
package com.monkopedia.kodemirror.test

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorSession

/**
 * Simulate typing [text] at the current cursor position.
 *
 * Inserts the text at the cursor (or replaces the current selection)
 * and moves the cursor to the end of the inserted text.
 */
fun EditorSession.typeText(text: String) {
    val sel = state.selection.main
    val newPos = sel.from + text.length
    dispatch(
        TransactionSpec(
            changes = ChangeSpec.Single(
                from = sel.from,
                to = if (sel.from != sel.to) sel.to else null,
                insert = InsertContent.StringContent(text)
            ),
            selection = SelectionSpec.CursorSpec(newPos)
        )
    )
}

/**
 * Simulate pressing a key by name.
 *
 * Supports common key names:
 * - `"Backspace"` — deletes one character before the cursor
 * - `"Delete"` — deletes one character after the cursor
 * - `"Enter"` — inserts a newline
 * - `"Tab"` — inserts a tab character
 *
 * @throws IllegalArgumentException for unrecognized key names.
 */
fun EditorSession.pressKey(key: String) {
    when (key) {
        "Backspace" -> {
            val sel = state.selection.main
            if (sel.from != sel.to) {
                dispatch(
                    TransactionSpec(
                        changes = ChangeSpec.Single(from = sel.from, to = sel.to),
                        selection = SelectionSpec.CursorSpec(sel.from)
                    )
                )
            } else if (sel.from > DocPos.ZERO) {
                dispatch(
                    TransactionSpec(
                        changes = ChangeSpec.Single(
                            from = sel.from - 1,
                            to = sel.from
                        ),
                        selection = SelectionSpec.CursorSpec(sel.from - 1)
                    )
                )
            }
        }
        "Delete" -> {
            val sel = state.selection.main
            if (sel.from != sel.to) {
                dispatch(
                    TransactionSpec(
                        changes = ChangeSpec.Single(from = sel.from, to = sel.to),
                        selection = SelectionSpec.CursorSpec(sel.from)
                    )
                )
            } else if (sel.to.value < state.doc.length) {
                dispatch(
                    TransactionSpec(
                        changes = ChangeSpec.Single(
                            from = sel.to,
                            to = sel.to + 1
                        )
                    )
                )
            }
        }
        "Enter" -> typeText("\n")
        "Tab" -> typeText("\t")
        else -> error("Unrecognized key: $key")
    }
}
