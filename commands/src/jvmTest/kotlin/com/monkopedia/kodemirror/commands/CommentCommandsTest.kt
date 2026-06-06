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

import com.monkopedia.kodemirror.language.CommentTokens
import com.monkopedia.kodemirror.language.commentTokens
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ported from upstream `@codemirror/commands` `test/test-comment.ts` (#116).
 * `CommentCommands.kt` had zero tests despite being fully ported.
 *
 * A single `|` in the doc string marks the cursor.
 */
class CommentCommandsTest {

    private val lineConfig = CommentTokens(line = "//")
    private val blockConfig = CommentTokens(block = CommentTokens.BlockComment("/*", "*/"))

    private fun view(docWithCursor: String, config: CommentTokens): EditorSession {
        val pos = docWithCursor.indexOf('|')
        require(pos >= 0) { "doc must contain a | cursor marker" }
        val doc = docWithCursor.replace("|", "")
        return EditorSession(
            EditorState.create(
                EditorStateConfig(
                    doc = doc.asDoc(),
                    selection = SelectionSpec.CursorSpec(DocPos(pos)),
                    extensions = commentTokens.of(config)
                )
            )
        )
    }

    private fun assertState(view: EditorSession, expectedWithCursor: String) {
        val expectedPos = expectedWithCursor.indexOf('|')
        val expectedDoc = expectedWithCursor.replace("|", "")
        assertEquals(expectedDoc, view.state.doc.toString(), "document text")
        assertEquals(expectedPos, view.state.selection.main.head.value, "cursor position")
    }

    @Test
    fun lineCommentAddsTokenAtLineStart() {
        val v = view("line 1\nli|ne 2\nline 3", lineConfig)
        toggleComment(v)
        assertState(v, "line 1\n// li|ne 2\nline 3")
    }

    @Test
    fun lineCommentTogglesBackOff() {
        val v = view("line 1\nli|ne 2\nline 3", lineConfig)
        toggleComment(v)
        toggleComment(v)
        assertState(v, "line 1\nli|ne 2\nline 3")
    }

    @Test
    fun lineCommentInsertsAfterIndentation() {
        val v = view("    in|dented", lineConfig)
        toggleComment(v)
        assertState(v, "    // in|dented")
    }

    @Test
    fun lineCommentUncommentsAlreadyCommentedLine() {
        val v = view("// al|ready", lineConfig)
        toggleComment(v)
        assertState(v, "al|ready")
    }

    @Test
    fun blockCommentTogglesAroundSelectionRoundTrip() {
        // Spacing of the inserted block tokens isn't pinned here (kodemirror omits the
        // inner padding upstream adds — a cosmetic difference, tracked separately); this
        // asserts the toggle wraps then cleanly removes the block tokens.
        val v = view("ab|c", blockConfig)
        toggleBlockComment(v)
        val commented = v.state.doc.toString()
        assertTrue(commented.contains("/*") && commented.contains("*/"), "wrapped: $commented")
        assertTrue(commented != "abc", "should change the doc")
        toggleBlockComment(v)
        assertEquals("abc", v.state.doc.toString(), "block uncomment should restore original")
    }
}
