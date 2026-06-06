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
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Multiple independently-registered `autocompletion()` configs must coexist —
 * their completion sources are merged by the `completionConfig` facet rather than
 * the last config shadowing the others. This is the #109 facet-merge fix
 * (basicSetup's bundled autocompletion() + languageServerSupport's source); part
 * of the #117 `:autocomplete` test port.
 */
class MultiSourceCompletionTest {

    private val emptySource: CompletionSource = { null }

    private fun listSource(vararg labels: String): CompletionSource = { ctx ->
        CompletionResult(
            from = DocPos.ZERO,
            to = ctx.pos,
            options = labels.map { Completion(label = it) },
            validFor = Regex("[\\w]*")
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
}
