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
package com.monkopedia.kodemirror.merge

import com.monkopedia.kodemirror.state.ChangeSet
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.endPos
import com.monkopedia.kodemirror.view.DecorationSource
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.MarkDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `unifiedMergeView` end to end on an [EditorState] (#451). Expected values come from running
 * the same cases against @codemirror/merge 6.12.0.
 */
class UnifiedMergeViewTest {

    private fun mk(
        original: String,
        doc: String,
        config: (Text) -> UnifiedMergeConfig = { UnifiedMergeConfig(original = it) }
    ): EditorState = EditorState.create(
        EditorStateConfig(
            doc = doc.asDoc(),
            extensions = unifiedMergeView(config(Text.of(original.split("\n"))))
        )
    )

    private fun chunks(state: EditorState): String = getChunks(state)!!.chunks.joinToString(" ") {
        "${it.fromA.value}-${it.toA.value}/${it.fromB.value}-${it.toB.value}"
    }

    private fun change(from: Int, to: Int?, insert: String): ChangeSpec = ChangeSpec.Single(
        DocPos(from),
        to?.let { DocPos(it) },
        InsertContent.StringContent(insert)
    )

    private fun EditorState.edit(from: Int, to: Int?, insert: String): EditorState =
        update(TransactionSpec(changes = change(from, to, insert))).state

    @Test
    fun constructs() {
        assertEquals("2-4/2-4", chunks(mk("a\nb\nc", "a\nB\nc")))
        assertEquals("", chunks(mk("same", "same")))
        assertEquals("", chunks(mk("", "")))
        val collapsed = mk(
            "1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12",
            "1\n2\n3\n4\n5\n6\nX\n8\n9\n10\n11\n12"
        ) { UnifiedMergeConfig(original = it, collapseUnchanged = CollapseConfig(1, 2)) }
        assertEquals("12-14/12-14", chunks(collapsed))
    }

    @Test
    fun tracksEditsToTheDocument() {
        var st = mk("a\nb\nc\nd", "a\nB\nc\nd").edit(0, null, "X\n")
        assertEquals("X\na\nB\nc\nd", st.doc.toString())
        assertEquals("0-4/0-6", chunks(st))
        st = st.edit(0, 2, "").edit(2, 3, "b")
        assertEquals("a\nb\nc\nd", st.doc.toString())
        assertEquals("", chunks(st))
    }

    @Test
    fun tracksChangesToTheOriginal() {
        var st = mk("a\nb\nc\nd", "a\nB\nc\nd")
        st = st.update(
            TransactionSpec(
                effects = listOf(originalDocChangeEffect(st, ChangeSet.of(change(2, 3, "B"), 7)))
            )
        ).state
        assertEquals("a\nB\nc\nd", getOriginalDoc(st).toString())
        assertEquals("", chunks(st))
        st = st.update(
            TransactionSpec(
                effects = listOf(
                    originalDocChangeEffect(st, ChangeSet.of(change(7, null, "\ne"), 7))
                )
            )
        ).state
        assertEquals("a\nB\nc\nd\ne", getOriginalDoc(st).toString())
        assertEquals("6-10/6-8", chunks(st))
    }

    @Test
    fun acceptsAndRejectsChunks() {
        val rejected = EditorSession(mk("a\nb\nc\nd", "a\nB\nc\nd"))
        assertTrue(rejectChunk(rejected, DocPos(2)))
        assertEquals("a\nb\nc\nd", rejected.state.doc.toString())
        assertEquals("", chunks(rejected.state))

        val accepted = EditorSession(mk("a\nb\nc\nd", "a\nB\nc\nd"))
        assertTrue(acceptChunk(accepted, DocPos(2)))
        assertEquals("a\nB\nc\nd", getOriginalDoc(accepted.state).toString())
        assertEquals("", chunks(accepted.state))
    }

    private fun changedText(allowInlineDiffs: Boolean): List<String> {
        val st = mk("hello world\nsecond line\nthird", "hello brave world\nsecond line\nthird") {
            UnifiedMergeConfig(original = it, allowInlineDiffs = allowInlineDiffs)
        }
        val decos = (decorateChunks.spec.create(EditorSession(st)) as DecorationSource).decorations
        val marks = mutableListOf<String>()
        decos.between(DocPos.ZERO, st.doc.endPos) { from, to, value ->
            if (value is MarkDecoration && value.spec.cssClass == "cm-changedText") {
                marks.add("$from-$to:${st.doc.sliceString(DocPos(from), DocPos(to))}")
            }
            null
        }
        return marks
    }

    @Test
    fun inlineDiffsMarkInsertedText() {
        assertEquals(listOf("6-12:brave "), changedText(allowInlineDiffs = true))
        assertEquals(listOf("6-12:brave "), changedText(allowInlineDiffs = false))
    }
}
