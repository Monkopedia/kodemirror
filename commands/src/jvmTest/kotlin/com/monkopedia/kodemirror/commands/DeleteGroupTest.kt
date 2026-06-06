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
package com.monkopedia.kodemirror.commands

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ported from upstream `@codemirror/commands` `test/test-commands.ts`
 * (deleteGroupForward / deleteGroupBackward). Part of #116 — `deleteByGroup`
 * had no direct coverage. A single `|` marks the cursor.
 */
class DeleteGroupTest {

    private fun view(docWithCursor: String): EditorSession {
        val pos = docWithCursor.indexOf('|')
        require(pos >= 0)
        val doc = docWithCursor.replace("|", "")
        return EditorSession(
            EditorState.create(
                EditorStateConfig(
                    doc = doc.asDoc(),
                    selection = SelectionSpec.CursorSpec(DocPos(pos))
                )
            )
        )
    }

    private fun assertResult(v: EditorSession, expectedWithCursor: String) {
        val expectedPos = expectedWithCursor.indexOf('|')
        val expectedDoc = expectedWithCursor.replace("|", "")
        assertEquals(expectedDoc, v.state.doc.toString(), "document text")
        assertEquals(expectedPos, v.state.selection.main.head.value, "cursor position")
    }

    private fun fwd(from: String, to: String) {
        val v = view(from)
        deleteGroupForward(v)
        assertResult(v, to)
    }

    private fun back(from: String, to: String) {
        val v = view(from)
        deleteGroupBackward(v)
        assertResult(v, to)
    }

    // ── deleteGroupForward ──

    @Test fun fwdDeletesWord() = fwd("one |two three", "one | three")

    @Test fun fwdDeletesWordWithLeadingSpace() = fwd("one| two three", "one| three")

    @Test fun fwdDeletesPunctuation() = fwd("one|...two", "one|two")

    @Test fun fwdDeletesSpaceGroup() = fwd("one|  \ttwo", "one|two")

    @Test fun fwdDeletesNewline() = fwd("one|\ntwo", "one|two")

    @Test fun fwdStopsAtNewline() = fwd("one| \n two", "one|\n two")

    @Test fun fwdStopsAfterNewline() = fwd("one|\n two", "one| two")

    @Test fun fwdDeletesToEndOfDoc() = fwd("one|two", "one|")

    @Test fun fwdDoesNothingAtEndOfDoc() = fwd("one|", "one|")

    // ── deleteGroupBackward ──

    @Test fun backDeletesWord() = back("one two| three", "one | three")

    @Test fun backDeletesWordWithTrailingSpace() = back("one two |three", "one |three")

    @Test fun backDeletesPunctuation() = back("one...|two", "one|two")

    @Test fun backDeletesSpaceGroup() = back("one \t |two", "one|two")

    @Test fun backDeletesNewline() = back("one\n|two", "one|two")

    @Test fun backStopsAtNewline() = back("one \n |two", "one \n|two")

    @Test fun backStopsAfterNewline() = back("one \n|two", "one |two")

    @Test fun backDeletesToStartOfDoc() = back("one|two", "|two")
}
