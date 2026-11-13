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
package com.monkopedia.kodemirror.autocomplete

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * When an open completion closes, and how its range follows edits, against
 * @codemirror/autocomplete 6.20.1 on the same inputs (#383).
 */
class CompletionLifecycleTest {

    private fun session(doc: String, cursor: Int, result: CompletionResult): EditorSession {
        val s = EditorSession(
            EditorState.create(
                EditorStateConfig(
                    doc = doc.asDoc(),
                    selection = SelectionSpec.CursorSpec(DocPos(cursor)),
                    extensions = ExtensionList(
                        listOf(completionConfig.of(CompletionConfig()), completionStateField)
                    )
                )
            )
        )
        s.dispatch(
            TransactionSpec(
                effects = listOf(
                    startCompletionEffect.of(
                        StartCompletion(listOf(result), false, s.state.selection.main.head)
                    )
                )
            )
        )
        assertEquals("active", completionStatus(s.state))
        return s
    }

    private fun barBaz(from: Int, to: Int? = null) = CompletionResult(
        from = DocPos(from),
        to = to?.let(::DocPos),
        options = listOf(Completion("bar"), Completion("baz")),
        validFor = Regex("\\w*")
    )

    private fun insert(s: EditorSession, at: Int, text: String, userEvent: String?, cursor: Int?) =
        s.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    DocPos(at),
                    DocPos(at),
                    InsertContent.StringContent(text)
                ),
                selection = cursor?.let { SelectionSpec.CursorSpec(DocPos(it)) },
                userEvent = userEvent
            )
        )

    @Test
    fun movingTheCursorClosesTheCompletion() {
        val s = session("foo ba", 6, barBaz(4))
        s.dispatch(TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos(0))))
        assertNull(completionStatus(s.state))
        // Enter after the move must not apply the stale completion.
        assertEquals(false, acceptCompletion(s))
        assertEquals("foo ba", s.state.doc.toString())
    }

    @Test
    fun applyFnCompletionClosesTheCompletion() {
        val apply: (CompletionApplyContext) -> Unit = { c ->
            c.session.dispatch(
                insertCompletionText(c.session.state, c.completion.label, c.from, c.to)
            )
        }
        val s = session(
            "fo",
            2,
            CompletionResult(
                DocPos(0),
                options = listOf(
                    Completion("foo", applyFn = apply),
                    Completion("foobar", applyFn = apply)
                ),
                validFor = Regex("\\w*")
            )
        )
        acceptCompletion(s)
        assertEquals("foo", s.state.doc.toString())
        assertNull(completionStatus(s.state))
    }

    @Test
    fun explicitResultEndFollowsTyping() {
        val s = session("foo ba)", 6, barBaz(4, to = 6))
        insert(s, 6, "r", "input.type", cursor = 7)
        assertEquals("active", completionStatus(s.state))
        acceptCompletion(s)
        assertEquals("foo bar)", s.state.doc.toString())
    }

    @Test
    fun typingKeepsTheCompletionOpen() {
        val s = session("foo ba", 6, barBaz(4))
        insert(s, 6, "r", "input.type", cursor = 7)
        assertEquals("active", completionStatus(s.state))
    }

    @Test
    fun anEditBeforeTheRangeMapsIt() {
        val s = session("foo ba)", 6, barBaz(4, to = 6))
        // A non-user edit away from the completion range (e.g. a remote change).
        insert(s, 0, "xx", userEvent = null, cursor = null)
        assertEquals("active", completionStatus(s.state))
        acceptCompletion(s)
        assertEquals("xxfoo bar)", s.state.doc.toString())
    }

    @Test
    fun anEditTouchingTheRangeClosesIt() {
        val s = session("foo ba", 6, barBaz(4))
        insert(s, 5, "q", userEvent = null, cursor = null)
        assertNull(completionStatus(s.state))
    }
}
