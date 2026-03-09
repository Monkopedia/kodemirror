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

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.assertEquals

/**
 * Assert that the editor's document content equals [expected].
 */
fun EditorSession.assertDoc(expected: String) {
    assertEquals(expected, state.doc.toString(), "Document content mismatch")
}

/**
 * Assert that the cursor is at [pos].
 *
 * Checks the head of the main selection range.
 */
fun EditorSession.assertCursorAt(pos: DocPos) {
    assertEquals(
        pos,
        state.selection.main.head,
        "Cursor position mismatch"
    )
}

/**
 * Assert that the main selection has the given [anchor] and [head].
 */
fun EditorSession.assertSelection(anchor: DocPos, head: DocPos) {
    val main = state.selection.main
    assertEquals(anchor, main.anchor, "Selection anchor mismatch")
    assertEquals(head, main.head, "Selection head mismatch")
}

/**
 * Assert that the main selection covers the range from [from] to [to].
 *
 * Unlike [assertSelection], this checks the normalized from/to positions
 * regardless of selection direction.
 */
fun EditorSession.assertSelectionRange(from: DocPos, to: DocPos) {
    val main = state.selection.main
    assertEquals(from, main.from, "Selection from mismatch")
    assertEquals(to, main.to, "Selection to mismatch")
}
