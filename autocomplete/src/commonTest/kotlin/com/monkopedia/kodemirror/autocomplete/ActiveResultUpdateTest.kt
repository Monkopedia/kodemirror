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

import com.monkopedia.kodemirror.state.ChangeByRangeResult
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.allowMultipleSelections
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How an open completion follows typing, backspacing and other deletions, against
 * upstream `@codemirror/autocomplete` 6.20.1's `ActiveResult.updateFor` on the same
 * inputs (#390), and which text it filters by: upstream's `from..to`, where `to`
 * defaults to the cursor when the result arrives and then maps through edits (#421).
 * Each expected value is upstream's output for the same doc, cursor, result and edits.
 */
class ActiveResultUpdateTest {

    private fun open(
        doc: String,
        cursors: List<Int>,
        results: List<CompletionResult>,
        explicit: Boolean = false,
        queryPos: Int = cursors.last(),
        config: CompletionConfig = CompletionConfig()
    ): EditorSession {
        val s = EditorSession(
            EditorState.create(
                EditorStateConfig(
                    doc = doc.asDoc(),
                    selection = SelectionSpec.EditorSelectionSpec(
                        EditorSelection.create(
                            cursors.map { EditorSelection.cursor(DocPos(it)) },
                            cursors.lastIndex
                        )
                    ),
                    extensions = ExtensionList(
                        listOf(
                            allowMultipleSelections.of(true),
                            completionConfig.of(config),
                            completionStateField
                        )
                    )
                )
            )
        )
        s.dispatch(
            TransactionSpec(
                effects = listOf(
                    startCompletionEffect.of(
                        StartCompletion(results, explicit, DocPos(queryPos))
                    )
                )
            )
        )
        return s
    }

    private fun open(
        doc: String,
        cursor: Int,
        result: CompletionResult,
        explicit: Boolean = false
    ) = open(doc, listOf(cursor), listOf(result), explicit)

    /** Whether the last transaction asked to re-query, and with which explicit flag. */
    private fun requery(s: EditorSession): Pair<Boolean, Boolean> {
        val cs = s.state.field(completionStateField)
        return cs.requery to cs.explicit
    }

    private fun result(from: Int, vararg labels: String = arrayOf("bar", "baz")) = CompletionResult(
        from = DocPos(from),
        options = labels.map { Completion(it) },
        validFor = Regex("\\w*")
    )

    /** Type [text] at every cursor, as the editor's input path does. */
    private fun type(s: EditorSession, text: String) = s.dispatch(
        s.state.changeByRange { r ->
            ChangeByRangeResult(
                changes = ChangeSpec.Single(r.head, r.head, InsertContent.StringContent(text)),
                range = EditorSelection.cursor(r.head + text.length)
            )
        }.copy(userEvent = "input.type")
    )

    /** Delete the character before every cursor, tagged with [userEvent]. */
    private fun backspace(s: EditorSession, userEvent: String = "delete.backward") = s.dispatch(
        s.state.changeByRange { r ->
            ChangeByRangeResult(
                changes = ChangeSpec.Single(r.head - 1, r.head, InsertContent.StringContent("")),
                range = EditorSelection.cursor(r.head - 1)
            )
        }.copy(userEvent = userEvent)
    )

    /** Status and shown labels, the shape the upstream probe prints. */
    private fun shown(s: EditorSession) =
        completionStatus(s.state) to currentCompletions(s.state).map { it.label }

    private val closed = null to emptyList<String>()

    @Test
    fun implicitResultAtTheCursorStaysOpenWhenBackspacedToItsStart() {
        // Member completion right after ".": from == cursor. Upstream's limit is
        // min(cursor, from + 1) == from, so backspacing back to from keeps it open.
        val s = open("x.", 2, result(2))
        type(s, "b")
        assertEquals("active" to listOf("bar", "baz"), shown(s))
        backspace(s)
        assertEquals("active" to listOf("bar", "baz"), shown(s))
        // Backspacing from `from` itself still closes.
        backspace(s)
        assertEquals(closed, shown(s))
    }

    @Test
    fun implicitResultAtTheCursorClosesOnBackspaceAtItsStart() {
        val s = open("x.", 2, result(2))
        backspace(s)
        assertEquals(closed, shown(s))
    }

    @Test
    fun explicitResultAtTheCursorFollowsTheSameLimit() {
        val s = open("x.", 2, result(2), explicit = true)
        type(s, "b")
        backspace(s)
        assertEquals("active" to listOf("bar", "baz"), shown(s))
        backspace(s)
        assertEquals(closed, shown(s))
    }

    @Test
    fun implicitResultBeforeTheCursorClosesOnceTheWordIsGone() {
        val s = open("foo ba", 6, result(4))
        backspace(s)
        assertEquals("active" to listOf("bar", "baz"), shown(s))
        backspace(s)
        assertEquals(closed, shown(s))
    }

    @Test
    fun explicitResultBeforeTheCursorMayBackspaceToItsStart() {
        val s = open("foo ba", 6, result(4), explicit = true)
        backspace(s)
        backspace(s)
        assertEquals("active" to listOf("bar", "baz"), shown(s))
        backspace(s)
        assertEquals(closed, shown(s))
    }

    @Test
    fun onlyBackwardDeletionKeepsTheCompletion() {
        for (event in listOf(
            "delete.forward",
            "delete.selection",
            "delete.cut",
            "input.delete",
            "delete"
        )) {
            val s = open("foo bar", 6, result(4))
            backspace(s, event)
            assertEquals(closed, shown(s), event)
        }
        // A sub-event of delete.backward counts as backspacing.
        val s = open("foo bar", 6, result(4))
        backspace(s, "delete.backward.word")
        assertEquals("active" to listOf("bar", "baz"), shown(s))
    }

    @Test
    fun backspacingPastTheStartInOneStepCloses() {
        val s = open("foo ba", 6, result(4), explicit = true)
        s.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(DocPos(3), DocPos(6), InsertContent.StringContent("")),
                selection = SelectionSpec.CursorSpec(DocPos(3)),
                userEvent = "delete.backward"
            )
        )
        assertEquals(closed, shown(s))
    }

    @Test
    fun backspaceThatChangesNothingAtTheStartCloses() {
        // Upstream checks `cur(startState) == from` even when nothing was deleted.
        val s = open("", 0, result(0), explicit = true)
        s.dispatch(TransactionSpec(userEvent = "delete.backward"))
        assertEquals(closed, shown(s))
    }

    @Test
    fun typingThatChangesNothingKeepsTheCompletion() {
        val s = open("foo ba", 6, result(4))
        s.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(DocPos(6)),
                userEvent = "input.type"
            )
        )
        assertEquals("active" to listOf("bar", "baz"), shown(s))
    }

    @Test
    fun multipleCursorsFollowTheMainOne() {
        val s = open("ba ba", listOf(2, 5), listOf(result(3)))
        type(s, "r")
        assertEquals("active" to listOf("bar"), shown(s))
        backspace(s)
        assertEquals("active" to listOf("bar", "baz"), shown(s))
        backspace(s)
        assertEquals(closed, shown(s))
    }

    @Test
    fun eachSourceChecksItsOwnStart() {
        // One result for the word "ba" (from 4) and one at the cursor (from 6).
        val s = open("foo ba", listOf(6), listOf(result(4), result(6, "rex", "rot")))
        type(s, "r")
        assertEquals(listOf("bar", "rex", "rot"), shown(s).second.sorted())
        backspace(s)
        assertEquals(listOf("bar", "baz", "rex", "rot"), shown(s).second.sorted())
        // Backspacing from the second result's start drops it but keeps the first.
        backspace(s)
        assertEquals("active" to listOf("bar", "baz"), shown(s))
    }

    @Test
    fun eachSourceKeepsItsOwnLimitWhateverTheOrder() {
        // The cursor source first: its limit (6) must not apply to the word source.
        val s = open("foo ba", listOf(6), listOf(result(6, "rex", "rot"), result(4)))
        type(s, "r")
        backspace(s)
        backspace(s)
        assertEquals("active" to listOf("bar", "baz"), shown(s))
    }

    @Test
    fun theLimitFollowsEditsAtOtherCursors() {
        // Typing at the cursor at 0 shifts the limit from 5 to 6 with the word, so
        // the backspace that leaves the main cursor at 5 closes.
        val s = open("foo b", listOf(0, 5), listOf(result(4)))
        type(s, "a")
        assertEquals("active" to listOf("bar", "baz"), shown(s))
        backspace(s)
        assertEquals(closed, shown(s))
    }

    @Test
    fun backspacingAtOneSourcesStartKeepsTheOther() {
        val s = open("foo ba", listOf(6), listOf(result(4), result(6, "rex", "rot")))
        backspace(s)
        assertEquals("active" to listOf("bar", "baz"), shown(s))
    }

    @Test
    fun theSelectedOptionStaysSelectedWhileTyping() {
        // Upstream re-finds the previously selected option in the re-filtered list.
        val s = open("foo ", 4, result(4, "a", "b", "bc"))
        assertEquals(listOf("a", "b", "bc"), shown(s).second)
        s.dispatch(TransactionSpec(effects = listOf(setSelectedCompletion.of(2))))
        type(s, "b")
        assertEquals(listOf("b", "bc"), shown(s).second)
        assertEquals(1, selectedCompletionIndex(s.state))
    }

    @Test
    fun theSelectionResetsWhenTheSelectedOptionIsFilteredOut() {
        val s = open("foo ", 4, result(4, "a", "b", "bc"))
        s.dispatch(TransactionSpec(effects = listOf(setSelectedCompletion.of(0))))
        type(s, "b")
        assertEquals(listOf("b", "bc"), shown(s).second)
        assertEquals(0, selectedCompletionIndex(s.state))
    }

    @Test
    fun aResultThatArrivesAfterTypingTakesItsLimitFromTheQueryPosition() {
        // Queried at 2 right after ".", the result lands once "b" is typed. Upstream
        // takes the limit from the query's cursor, so backspacing to 2 keeps it.
        val s = open("x.b", listOf(3), listOf(result(2)), queryPos = 2)
        backspace(s)
        assertEquals("active" to listOf("bar", "baz"), shown(s))
    }

    @Test
    fun aFailedValidForAsksForAReQueryWithTheSessionsExplicitFlag() {
        // Backspacing to an empty word that validFor rejects.
        val strict = result(4).copy(validFor = Regex("\\w+"))
        var s = open("foo ba", 6, strict, explicit = true)
        backspace(s)
        backspace(s)
        assertEquals(closed, shown(s))
        assertEquals(true to true, requery(s))
        // No validFor: every edit re-queries, on backspace too.
        s = open("foo ba", 6, result(4).copy(validFor = null))
        backspace(s)
        assertEquals(true to false, requery(s))
        s = open("foo ba", 6, result(4).copy(validFor = null), explicit = true)
        type(s, "r")
        assertEquals(true to true, requery(s))
        // A typed space leaves the word.
        s = open("foo ba", 6, result(4), explicit = true)
        type(s, " ")
        assertEquals(true to true, requery(s))
        // The request lasts one transaction.
        s.dispatch(TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos(0))))
        assertEquals(false to false, requery(s))
    }

    @Test
    fun closingForTheLimitDoesNotReQuery() {
        val s = open("foo ba", 6, result(4))
        backspace(s)
        backspace(s)
        assertEquals(false to false, requery(s))
    }

    // ── Filter text: from..to (#421) ──

    /** Each open result's stored `from..to`, as upstream's `ActiveResult` holds it. */
    private fun ranges(s: EditorSession) = s.state.field(completionStateField).active
        .map { it.result.from.value to it.result.to?.value }

    private fun CompletionResult.to(to: Int) = copy(to = DocPos(to))

    /** Insert [text] at [at] with `input.type` but leave the cursor where it was. */
    private fun typeBehindCursor(s: EditorSession, at: Int, text: String) = s.dispatch(
        TransactionSpec(
            changes = ChangeSpec.Single(DocPos(at), DocPos(at), InsertContent.StringContent(text)),
            selection = SelectionSpec.CursorSpec(s.state.selection.main.head),
            userEvent = "input.type"
        )
    )

    @Test
    fun typingThatLeavesTheCursorBehindFiltersByTheResultsTo() {
        // `to` maps with assoc 1 over text typed at it, the cursor does not move.
        var s = open("foo ba)", 6, result(4).to(6))
        typeBehindCursor(s, 6, "r")
        assertEquals("active" to listOf("bar"), shown(s))
        assertEquals(listOf(4 to 7), ranges(s))
        // An unset `to` is the cursor when the result arrives, and maps the same way.
        s = open("foo ba)", 6, result(4))
        typeBehindCursor(s, 6, "r")
        assertEquals("active" to listOf("bar"), shown(s))
        assertEquals(listOf(4 to 7), ranges(s))
        // Accepting replaces that same range.
        acceptCompletion(s)
        assertEquals("foo bar)", s.state.doc.toString())
        assertEquals(7, s.state.selection.main.head.value)
    }

    @Test
    fun aToPastTheCursorFiltersByTheWholeRange() {
        // "foo b|ar" with a result over "bar", as an LSP textEdit range gives.
        var s = open("foo bar", 5, result(4, "bar", "baz", "bat").to(7))
        assertEquals("active" to listOf("bar"), shown(s))
        type(s, "a")
        assertEquals(closed, shown(s))
        // "foo b|z", explicit: typing and backspacing inside the range.
        s = open("foo bz", 5, result(4, "bz", "bzz", "bar").to(6), explicit = true)
        assertEquals("active" to listOf("bz", "bzz"), shown(s))
        type(s, "z")
        assertEquals("active" to listOf("bzz"), shown(s))
        assertEquals(listOf(4 to 7), ranges(s))
        backspace(s)
        assertEquals("active" to listOf("bz", "bzz"), shown(s))
        assertEquals(listOf(4 to 6), ranges(s))
    }

    @Test
    fun aToBeforeTheCursorFiltersUpToIt() {
        val s = open("foo barx", 8, result(4, "bar", "baz", "bxx").to(6), explicit = true)
        assertEquals("active" to listOf("bar", "baz"), shown(s))
        // The cursor is still past `to` after a backspace, which closes it.
        backspace(s)
        assertEquals(closed, shown(s))
    }

    @Test
    fun withoutAToTheFilterTextFollowsTypingAndBackspacing() {
        val s = open("foo b", 5, result(4, "bar", "baz", "bat"))
        assertEquals("active" to listOf("bar", "bat", "baz"), shown(s))
        type(s, "a")
        type(s, "r")
        assertEquals("active" to listOf("bar"), shown(s))
        backspace(s)
        assertEquals("active" to listOf("bar", "bat", "baz"), shown(s))
    }

    @Test
    fun withMultipleCursorsTheRangeMapsThroughEveryEdit() {
        // Main cursor at 5 ("foo b|ar|"), a second cursor at the result's `to`.
        var s = open("foo bar", listOf(7, 5), listOf(result(4, "bar", "baz", "bat", "baar").to(7)))
        assertEquals("active" to listOf("bar", "baar"), shown(s))
        type(s, "a")
        assertEquals(closed, shown(s))
        // No `to`, a second cursor before the result.
        s = open("b foo b", listOf(1, 7), listOf(result(6)))
        type(s, "a")
        assertEquals("active" to listOf("bar", "baz"), shown(s))
        type(s, "r")
        assertEquals("active" to listOf("bar"), shown(s))
        backspace(s)
        assertEquals("active" to listOf("bar", "baz"), shown(s))
    }

    @Test
    fun anUnfilteredResultIgnoresItsRange() {
        val s = open("foo bar", 5, result(4, "abc", "bar", "xyz").copy(filter = false).to(7))
        assertEquals("active" to listOf("abc", "bar", "xyz"), shown(s))
        type(s, "q")
        assertEquals("active" to listOf("abc", "bar", "xyz"), shown(s))
    }

    @Test
    fun aResultThatArrivesAfterTypingFiltersByTheTypedText() {
        // Queried at 2 right after ".", landing once "ba" is typed.
        val s = open("x.ba", listOf(4), listOf(result(2, "bar", "baz", "qux")), queryPos = 2)
        assertEquals("active" to listOf("bar", "baz"), shown(s))
    }

    @Test
    fun eachSourceFiltersByItsOwnRange() {
        val s =
            open(
                "foo bar",
                listOf(5),
                listOf(result(4, "bar", "bat").to(7), result(4, "baz", "bxx"))
            )
        assertEquals("active" to listOf("bar", "baz", "bxx"), shown(s))
        assertEquals(listOf(4 to 7, 4 to 5), ranges(s))
    }

    @Test
    fun typingPastTheResultsToAsksForAReQuery() {
        // closeBrackets typing ")" over the one after the cursor in "f(b|)": the
        // defaulted `to` (3) stays behind, so upstream re-queries.
        val skipParen = TransactionSpec(
            changes = ChangeSpec.Single(DocPos(3), DocPos(4), InsertContent.StringContent(")")),
            selection = SelectionSpec.CursorSpec(DocPos(4)),
            userEvent = "input.type"
        )
        var s = open("f(b)", 3, result(2))
        s.dispatch(skipParen)
        assertEquals(closed, shown(s))
        assertEquals(true to false, requery(s))
        // Without activateOnTyping it just closes.
        s = open(
            "f(b)",
            listOf(3),
            listOf(result(2)),
            config = CompletionConfig(activateOnTyping = false)
        )
        s.dispatch(skipParen)
        assertEquals(closed, shown(s))
        assertEquals(false to false, requery(s))
        // An explicit `to` before the cursor, typed past.
        s = open("foo barx", 8, result(4, "bar", "baz", "bxx").to(6), explicit = true)
        type(s, "y")
        assertEquals(closed, shown(s))
        assertEquals(true to true, requery(s))
        // Backspacing past it just closes.
        s = open("foo barx", 8, result(4, "bar", "baz", "bxx").to(6), explicit = true)
        backspace(s)
        assertEquals(false to false, requery(s))
    }
}
