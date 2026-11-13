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

import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.monkopedia.kodemirror.autocomplete.Completion
import com.monkopedia.kodemirror.autocomplete.CompletionConfig
import com.monkopedia.kodemirror.autocomplete.CompletionResult
import com.monkopedia.kodemirror.autocomplete.CompletionSource
import com.monkopedia.kodemirror.autocomplete.SuspendCompletionSource
import com.monkopedia.kodemirror.autocomplete.autocompletion
import com.monkopedia.kodemirror.autocomplete.completionStatus
import com.monkopedia.kodemirror.autocomplete.currentCompletions
import com.monkopedia.kodemirror.autocomplete.startCompletion
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred

/**
 * When an open completion's result stops being valid for the edited text, upstream
 * `@codemirror/autocomplete` 6.20.1 re-queries its source (the result goes
 * `Pending`, keeping the session's explicit flag) rather than closing, on
 * backspace as well as typing (#390). It also takes a result's backspace limit
 * from the cursor the query ran at. These run the real completion plugin.
 */
@OptIn(ExperimentalTestApi::class)
class CompletionRequeryTest {

    /** Completes the word before the cursor, with no `validFor` (so every edit re-queries). */
    private val wordSource: CompletionSource = { ctx ->
        val word = ctx.matchBefore(Regex("\\w+"))
        if (word == null && !ctx.explicit) {
            null
        } else {
            CompletionResult(
                from = word?.from ?: ctx.pos,
                options = listOf(Completion("bar"), Completion("baz"))
            )
        }
    }

    private fun ComposeUiTest.editor(doc: String, source: CompletionSource): EditorSession =
        editor(doc, CompletionConfig(override = listOf(source)))

    private fun ComposeUiTest.editor(doc: String, config: CompletionConfig): EditorSession {
        lateinit var session: EditorSession
        setContent {
            val state = remember {
                EditorState.create(
                    EditorStateConfig(
                        doc = doc.asDoc(),
                        selection = SelectionSpec.CursorSpec(DocPos(doc.length)),
                        extensions = ExtensionList(
                            listOf(autocompletion(config))
                        )
                    )
                )
            }
            session = remember(state) { EditorSession(state) }
            KodeMirror(session = session)
        }
        waitForIdle()
        return session
    }

    private fun ComposeUiTest.edit(session: EditorSession, insert: String?, userEvent: String) {
        val pos = session.state.selection.main.head
        val from = if (insert == null) pos - 1 else pos
        val text = insert ?: ""
        runOnIdle {
            session.dispatch(
                TransactionSpec(
                    changes = ChangeSpec.Single(from, pos, InsertContent.StringContent(text)),
                    selection = SelectionSpec.CursorSpec(from + text.length),
                    userEvent = userEvent
                )
            )
        }
        waitForIdle()
    }

    private fun shown(session: EditorSession) =
        completionStatus(session.state) to currentCompletions(session.state).map { it.label }

    @Test
    fun backspaceReQueriesAResultWithoutValidFor() = runDesktopComposeUiTest {
        val session = editor("foo ", wordSource)
        edit(session, "b", "input.type")
        edit(session, "a", "input.type")
        assertEquals("active" to listOf("bar", "baz"), shown(session))
        edit(session, null, "delete.backward")
        assertEquals("active" to listOf("bar", "baz"), shown(session))
    }

    @Test
    fun aReQueryKeepsTheSessionExplicit() = runDesktopComposeUiTest {
        // Only answers explicit queries, so it shows whether the re-query was explicit.
        val explicitOnly: CompletionSource = { ctx -> if (ctx.explicit) wordSource(ctx) else null }
        val session = editor("foo ba", explicitOnly)
        runOnIdle { startCompletion(session) }
        waitForIdle()
        assertEquals("active" to listOf("bar", "baz"), shown(session))
        edit(session, "r", "input.type")
        assertEquals("active" to listOf("bar"), shown(session))
    }

    @Test
    fun aValidResultIsFilteredWithoutReQuerying() = runDesktopComposeUiTest {
        // Control: a source with validFor is re-filtered in place on both edits.
        var queries = 0
        val counted: CompletionSource = { ctx ->
            queries++
            wordSource(ctx)?.copy(validFor = Regex("\\w*"))
        }
        val session = editor("foo ", counted)
        edit(session, "b", "input.type")
        edit(session, "a", "input.type")
        edit(session, null, "delete.backward")
        assertEquals("active" to listOf("bar", "baz"), shown(session))
        assertEquals(1, queries)
    }

    @Test
    fun aTypedQueryTakesItsLimitFromTheCursor() = runDesktopComposeUiTest {
        // Queried at 6 after typing "a" into "foo b": the limit is min(6, 4 + 1) == 5,
        // so the second backspace (to 4) closes.
        val session = editor("foo b", wordSource.withValidFor())
        edit(session, "a", "input.type")
        edit(session, null, "delete.backward")
        assertEquals("active" to listOf("bar", "baz"), shown(session))
        edit(session, null, "delete.backward")
        assertEquals(null to emptyList<String>(), shown(session))
    }

    @Test
    fun aMemberQueryMayBeBackspacedToItsStart() = runDesktopComposeUiTest {
        val member: CompletionSource = { ctx ->
            CompletionResult(
                from = ctx.pos,
                options = listOf(Completion("bar"), Completion("baz")),
                validFor = Regex("\\w*")
            ).takeIf { ctx.state.doc.sliceString(ctx.pos - 1, ctx.pos) == "." }
        }
        val session = editor("x", member)
        edit(session, ".", "input.type")
        edit(session, "b", "input.type")
        edit(session, null, "delete.backward")
        assertEquals("active" to listOf("bar", "baz"), shown(session))
    }

    @Test
    fun aLateAsyncResultTakesItsLimitFromTheQueryCursor() = runDesktopComposeUiTest {
        // The query runs at 2, right after "."; its result lands after "ba" is typed.
        // Upstream's limit is min(2, 2 + 1) == 2, so two backspaces keep it open.
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val source: SuspendCompletionSource = { ctx ->
            if (calls++ == 0) {
                gate.await()
                CompletionResult(
                    from = ctx.pos,
                    options = listOf(Completion("bar"), Completion("baz")),
                    validFor = Regex("\\w*")
                )
            } else {
                null
            }
        }
        val session = editor("x", CompletionConfig(asyncOverride = listOf(source)))
        edit(session, ".", "input.type")
        edit(session, "b", "input.type")
        edit(session, "a", "input.type")
        runOnIdle { gate.complete(Unit) }
        waitForIdle()
        assertEquals("active" to listOf("bar", "baz"), shown(session))
        edit(session, null, "delete.backward")
        edit(session, null, "delete.backward")
        assertEquals("active" to listOf("bar", "baz"), shown(session))
    }

    private fun CompletionSource.withValidFor(): CompletionSource =
        { ctx -> this(ctx)?.copy(validFor = Regex("\\w*")) }
}
