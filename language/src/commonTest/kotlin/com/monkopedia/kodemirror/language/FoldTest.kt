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
package com.monkopedia.kodemirror.language

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FoldTest {

    private fun createState(
        doc: String,
        vararg extensions: com.monkopedia.kodemirror.state.Extension
    ): EditorState {
        return EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                extensions = if (extensions.size == 1) {
                    extensions[0]
                } else {
                    ExtensionList(extensions.toList())
                }
            )
        )
    }

    @Test
    fun foldedRangesEmptyOnFreshState() {
        val state = createState("line one\nline two\nline three", codeFolding())
        val ranges = foldedRanges(state)
        var count = 0
        ranges.between(DocPos.ZERO, DocPos(state.doc.length)) { _, _, _ ->
            count++
            true
        }
        assertEquals(0, count)
    }

    @Test
    fun foldEffectAddsFoldedRange() {
        val state = createState("line one\nline two\nline three", codeFolding())
        val fold = FoldRange(from = DocPos(4), to = DocPos(15))
        val tr = state.update(
            TransactionSpec(effects = listOf(foldEffect.of(fold)))
        )
        val newState = tr.state
        val ranges = foldedRanges(newState)
        var count = 0
        ranges.between(DocPos.ZERO, DocPos(newState.doc.length)) { from, to, _ ->
            assertEquals(4, from)
            assertEquals(15, to)
            count++
            true
        }
        assertEquals(1, count)
    }

    @Test
    fun unfoldEffectRemovesFoldedRange() {
        val state = createState("line one\nline two\nline three", codeFolding())
        val fold = FoldRange(from = DocPos(4), to = DocPos(15))
        // Add a fold
        val folded = state.update(
            TransactionSpec(effects = listOf(foldEffect.of(fold)))
        ).state
        // Remove the fold
        val unfolded = folded.update(
            TransactionSpec(effects = listOf(unfoldEffect.of(fold)))
        ).state
        val ranges = foldedRanges(unfolded)
        var count = 0
        ranges.between(DocPos.ZERO, DocPos(unfolded.doc.length)) { _, _, _ ->
            count++
            true
        }
        assertEquals(0, count)
    }

    @Test
    fun foldableWithFoldServiceReturnsFoldRange() {
        val service: (EditorState, DocPos) -> FoldRange? = { _, lineStart ->
            if (lineStart == DocPos.ZERO) FoldRange(DocPos(5), DocPos(18)) else null
        }
        val state = createState(
            "line one\nline two\nline three",
            ExtensionList(listOf(codeFolding(), foldService.of(service)))
        )
        val result = foldable(state, DocPos.ZERO)
        assertNotNull(result)
        assertEquals(DocPos(5), result.from)
        assertEquals(DocPos(18), result.to)
    }

    @Test
    fun foldableReturnsNullWithoutServiceOrTree() {
        val state = createState("line one\nline two", codeFolding())
        val result = foldable(state, DocPos.ZERO)
        assertNull(result)
    }

    @Test
    fun multipleFoldsTrackedIndependently() {
        val state = createState(
            "aaa\nbbb\nccc\nddd",
            codeFolding()
        )
        val fold1 = FoldRange(from = DocPos(1), to = DocPos(3))
        val fold2 = FoldRange(from = DocPos(8), to = DocPos(11))
        val folded = state.update(
            TransactionSpec(
                effects = listOf(foldEffect.of(fold1), foldEffect.of(fold2))
            )
        ).state
        val ranges = foldedRanges(folded)
        var count = 0
        ranges.between(DocPos.ZERO, DocPos(folded.doc.length)) { _, _, _ ->
            count++
            true
        }
        assertEquals(2, count)
    }

    @Test
    fun foldedRangesEmptyWithoutCodeFolding() {
        val state = EditorState.create(
            EditorStateConfig(doc = "hello".asDoc())
        )
        val ranges = foldedRanges(state)
        var count = 0
        ranges.between(DocPos.ZERO, DocPos(state.doc.length)) { _, _, _ ->
            count++
            true
        }
        assertEquals(0, count)
    }
}
