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

/**
 * Cross-source completion behaviour (#137).
 *
 * Two concerns are covered here:
 *  - The `completionConfig` facet MERGES override-source *lists* across
 *    independently-registered `autocompletion()` configs (the #109 fix:
 *    basicSetup's bundled `autocompletion()` + languageServerSupport's source),
 *    rather than the last config shadowing the others.
 *  - `triggerCompletion` queries ALL sync override sources and MERGES their
 *    *results* into one ordered, deduped list — the upstream
 *    `@codemirror/autocomplete` `sortOptions` behaviour ("can merge multiple
 *    sources", "removes duplicate options", "supports unfiltered completions").
 *    This replaces the previous first-non-empty-source-wins behaviour (#137,
 *    surfaced by the #117 test port).
 */
class MultiSourceCompletionTest {

    private val emptySource: CompletionSource = { null }

    private fun listSource(
        vararg labels: String,
        from: DocPos = DocPos.ZERO,
        filter: Boolean = true
    ): CompletionSource = { ctx ->
        CompletionResult(
            from = from,
            to = ctx.pos,
            options = labels.map { Completion(label = it) },
            validFor = Regex("[\\w]*"),
            filter = filter
        )
    }

    private fun viewWith(vararg configs: CompletionConfig): EditorSession {
        val state = EditorState.create(
            EditorStateConfig(
                doc = "".asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(0)),
                extensions = ExtensionList(
                    configs.map { completionConfig.of(it) } + completionStateField
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
    fun facetMergesOverrideSourcesAcrossConfigs() {
        val state = EditorState.create(
            EditorStateConfig(
                doc = "".asDoc(),
                extensions = ExtensionList(
                    listOf(
                        completionConfig.of(CompletionConfig(override = listOf(emptySource))),
                        completionConfig.of(CompletionConfig(override = listOf(emptySource)))
                    )
                )
            )
        )
        assertEquals(2, state.facet(completionConfig).override?.size)
    }

    @Test
    fun realSourceConsultedWhenRegisteredBeforeAnEmptyOne() {
        // Regression for #109: the OLD `values.lastOrNull()` combine kept only the
        // LAST config — so a real source in an EARLIER config (here, registered first)
        // was dropped, leaving zero sources. The merge must consult it.
        val v = viewWith(
            CompletionConfig(override = listOf(listSource("real"))),
            CompletionConfig(override = listOf(emptySource))
        )
        startCompletion(v)
        val c = currentCompletions(v.state)
        assertEquals(1, c.size)
        assertEquals("real", c[0].label)
    }

    @Test
    fun realSourceConsultedWhenRegisteredAfterAnEmptyOne() {
        val v = viewWith(
            CompletionConfig(override = listOf(emptySource)),
            CompletionConfig(override = listOf(listSource("real")))
        )
        startCompletion(v)
        val c = currentCompletions(v.state)
        assertEquals(1, c.size)
        assertEquals("real", c[0].label)
    }

    @Test
    fun canMergeMultipleSources() {
        // Upstream "can merge multiple sources": TWO non-empty sources now both
        // contribute (previously the first non-empty source won and the second's
        // options never appeared). All options are sorted by one global order
        // (here: equal score, so label-ascending), which for a,b,c,d matches input.
        val v = viewWith(
            CompletionConfig(override = listOf(listSource("a", "b"), listSource("c", "d")))
        )
        startCompletion(v)
        val labels = currentCompletions(v.state).map { it.label }
        assertEquals(listOf("a", "b", "c", "d"), labels)
    }

    @Test
    fun removesDuplicateOptions() {
        // Upstream "removes duplicate options": identical labels from different
        // sources collapse to a single entry. Empty query -> every option scores 0
        // with no sortText, so the single global sort orders them label-ascending
        // (bar, baz, foo) and the duplicate "foo" is deduped.
        val v = viewWith(
            CompletionConfig(
                override = listOf(
                    listSource("foo", "bar"),
                    listSource("foo", "baz")
                )
            )
        )
        startCompletion(v)
        val labels = currentCompletions(v.state).map { it.label }
        assertEquals(listOf("bar", "baz", "foo"), labels)
    }

    @Test
    fun supportsUnfilteredCompletionsAlongsideFilteredOnes() {
        // Upstream "supports unfiltered completions": a filter = false source keeps
        // ALL of its options even as the user types a prefix that the filtered
        // source narrows on. Here "ap" narrows the filtered source to "apple" but
        // the unfiltered source's "zzz" stays.
        val v = viewWith(
            CompletionConfig(
                override = listOf(
                    listSource("apple", "other"),
                    listSource("zzz", filter = false)
                )
            )
        )
        startCompletion(v)
        assertEquals(3, currentCompletions(v.state).size)
        typeChar(v, "a")
        typeChar(v, "p")
        val labels = currentCompletions(v.state).map { it.label }.toSet()
        assertEquals(setOf("apple", "zzz"), labels)
    }
}
