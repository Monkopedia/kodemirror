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
package com.monkopedia.kodemirror.view.input

import androidx.compose.runtime.remember
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.lineNumbers

/**
 * Holder for the editor session created inside the Compose test, so test
 * code can access it for assertions.
 */
class SessionHolder {
    lateinit var session: EditorSession
}

/**
 * Run an integration test with a KodeMirror editor.
 *
 * Sets up a [KodeMirror] composable with the given document content and
 * extensions, waits for layout, and then runs [block] with access to
 * the [DesktopComposeUiTest] scope and a [SessionHolder] for assertions.
 */
@OptIn(ExperimentalTestApi::class)
fun runEditorTest(
    doc: String = "",
    extensions: Extension? = null,
    withGutters: Boolean = false,
    width: Int = 800,
    height: Int = 600,
    block: DesktopComposeUiTest.(SessionHolder) -> Unit
) {
    val holder = SessionHolder()
    runDesktopComposeUiTest(width = width, height = height) {
        setContent {
            val allExtensions = buildList {
                if (withGutters) add(lineNumbers)
                extensions?.let { add(it) }
            }
            val ext = if (allExtensions.isEmpty()) null else ExtensionList(allExtensions)
            val state = remember {
                EditorState.create(
                    EditorStateConfig(
                        doc = doc.asDoc(),
                        extensions = ext
                    )
                )
            }
            val session = remember(state) { EditorSession(state) }
            holder.session = session
            KodeMirror(session = session)
        }
        waitForIdle()
        block(holder)
    }
}

/**
 * Assert that the cursor (head of main selection) is on the expected
 * 1-based line number.
 */
fun SessionHolder.assertCursorOnLine(expectedLine: Int) {
    val state = session.state
    val cursorPos = state.selection.main.head
    val line = state.doc.lineAt(cursorPos)
    assert(line.number.value == expectedLine) {
        "Expected cursor on line $expectedLine but was on line ${line.number.value} " +
            "(head=${cursorPos.value})"
    }
}

/**
 * Assert that the main selection is non-empty (some text is selected).
 */
fun SessionHolder.assertSelectionNotEmpty() {
    val sel = session.state.selection.main
    assert(!sel.empty) {
        "Expected non-empty selection but got cursor at ${sel.head.value}"
    }
}

/**
 * Assert the document content equals [expected].
 */
fun SessionHolder.assertDoc(expected: String) {
    val actual = session.state.doc.toString()
    assert(actual == expected) {
        "Expected doc:\n$expected\nActual doc:\n$actual"
    }
}

/**
 * Assert the cursor is at the given document offset.
 */
fun SessionHolder.assertCursorAt(pos: Int) {
    val head = session.state.selection.main.head
    assert(head == DocPos(pos)) {
        "Expected cursor at $pos but was at ${head.value}"
    }
}
