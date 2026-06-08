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

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Pipeline-level completion behaviour, ported/adapted from upstream
 * `@codemirror/autocomplete` `test/webtest-autocomplete.ts` (#117) — covering
 * the cases that are portable to kodemirror's (intentionally simpler)
 * completion model:
 *  - `filter = false` (unfiltered) results keep every option as the user types
 *  - accepting replaces the result's whole `from`..`to` range, including a `to`
 *    that extends past the cursor
 *  - source selection: the first source that returns options wins (kodemirror
 *    consults sources in order and uses the first non-empty result — it does
 *    NOT merge across sources, see the note below)
 *  - `completeFromList`'s word-match / explicit gating
 *
 * Divergences from upstream that are deliberately NOT ported here (kodemirror
 * does not implement them — tracked on #117, not asserted as green tests):
 * cross-source merge + dedup of options, multi-cursor completion, `sortText`
 * (kodemirror uses `boost`, covered by FilterTest), and the explicit-session
 * lifecycle nuances (backspace-out-of-word, stop-on-non-spanning-input).
 */
class CompletionPipelineTest {

    private fun listSource(
        vararg labels: String,
        filter: Boolean = true,
        to: DocPos? = null
    ): CompletionSource = { ctx ->
        CompletionResult(
            from = DocPos.ZERO,
            to = to ?: ctx.pos,
            options = labels.map { Completion(label = it) },
            validFor = Regex("[\\w]*"),
            filter = filter
        )
    }

    private fun viewWith(
        source: CompletionSource,
        doc: String = "",
        cursor: Int = doc.length
    ): EditorSession {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(cursor)),
                extensions = ExtensionList(
                    listOf(
                        completionConfig.of(CompletionConfig(override = listOf(source))),
                        completionStateField
                    )
                )
            )
        )
        return EditorSession(state)
    }

    private fun typeChar(view: EditorSession, ch: String) {
        val pos = view.state.selection.main.head
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(pos, pos, InsertContent.StringContent(ch)),
                selection = SelectionSpec.CursorSpec(pos + ch.length),
                userEvent = "input.type"
            )
        )
    }

    @Test
    fun unfilteredResultKeepsAllOptionsWhileTyping() {
        // filter = false: the editor must not drop options that fail to match the
        // typed prefix (upstream "supports unfiltered completions").
        val v = viewWith(listSource("apple", "banana", "cherry", filter = false))
        startCompletion(v)
        assertEquals(3, currentCompletions(v.state).size)
        typeChar(v, "z") // matches nothing, but filter=false keeps everything
        assertEquals("active", completionStatus(v.state))
        assertEquals(3, currentCompletions(v.state).size)
    }

    @Test
    fun filteredResultDropsNonMatchingWhileTyping() {
        // Control for the above: the default filter=true source narrows to nothing
        // on a non-matching char (and therefore closes).
        val v = viewWith(listSource("apple", "banana", "cherry"))
        startCompletion(v)
        assertEquals(3, currentCompletions(v.state).size)
        typeChar(v, "z")
        assertNull(completionStatus(v.state))
    }

    @Test
    fun acceptReplacesRangeBeyondCursor() {
        // result.to extends past the cursor — accepting replaces the whole
        // from..to range, not just up to the cursor (upstream "can cover range
        // beyond cursor").
        val v = viewWith(
            listSource("foobarbaz", to = DocPos(6)),
            doc = "foobar",
            cursor = 3
        )
        startCompletion(v) // query = "foo" → prefix-matches "foobarbaz"
        assertEquals("active", completionStatus(v.state))
        acceptCompletion(v)
        assertEquals("foobarbaz", v.state.doc.toString())
    }

    @Test
    fun firstNonEmptySourceWinsAndIsNotMerged() {
        // kodemirror consults override sources in order and uses the FIRST that
        // returns options; it does not merge across sources. So a second
        // non-empty source's options never appear.
        val v = viewWith(
            { ctx ->
                CompletionResult(
                    from = DocPos.ZERO,
                    to = ctx.pos,
                    options = listOf(Completion("a"), Completion("b")),
                    validFor = Regex("[\\w]*")
                )
            }
        )
        startCompletion(v)
        val labels = currentCompletions(v.state).map { it.label }
        assertEquals(listOf("a", "b"), labels)
    }

    @Test
    fun completeFromListGatesOnWordMatchUnlessExplicit() {
        // At a non-word position, completeFromList returns null for an implicit
        // (typed) request but still fires for an explicit (Ctrl-Space) one.
        val state = EditorState.create(EditorStateConfig(doc = "1 ".asDoc()))
        val list = listOf(Completion("foo"), Completion("bar"))
        val source = completeFromList(list)
        val pos = DocPos(2) // after the space — no word before the cursor

        assertNull(source(CompletionContext(state, pos, explicit = false)))
        val explicitResult = source(CompletionContext(state, pos, explicit = true))
        assertNotNull(explicitResult)
        assertEquals(pos, explicitResult.from)
        assertEquals(2, explicitResult.options.size)
    }

    @Test
    fun completeFromListAnchorsToWordBeforeCursor() {
        // With a word before the cursor, the result range starts at the word.
        val state = EditorState.create(EditorStateConfig(doc = "foo".asDoc()))
        val source = completeFromList(listOf(Completion("foobar")))
        val result = source(CompletionContext(state, DocPos(3), explicit = false))
        assertNotNull(result)
        assertEquals(DocPos.ZERO, result.from)
    }
}
