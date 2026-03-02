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

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChunkTest {

    private val linesA: List<String> = (1..1000).map { "line $it" }
    private val linesB: List<String> = linesA.toMutableList().also {
        it[499] = "line D"
        it.subList(699, 749).clear()
        it.add(699, "line ??")
        it.add(700, "line !!")
    }
    private val docA = Text.of(linesA)
    private val docB = Text.of(linesB)

    @Test
    fun enumeratesChangedChunks() {
        val chunks = Chunk.build(docA, docB)
        assertEquals(2, chunks.size)

        val (ch1, ch2) = chunks
        assertEquals(docA.line(500).from, ch1.fromA)
        assertEquals(docA.line(501).from, ch1.toA)
        assertEquals(docB.line(500).from, ch1.fromB)
        assertEquals(docB.line(501).from, ch1.toB)

        assertEquals(docA.line(700).from, ch2.fromA)
        assertEquals(docA.line(750).from, ch2.toA)
        assertEquals(docB.line(700).from, ch2.fromB)
        assertEquals(docB.line(702).from, ch2.toB)

        assertEquals(2, ch2.changes.size)
        val (c1, c2) = ch2.changes
        assertEquals(listOf(5, 5, 7), listOf(c1.fromA, c1.fromB, c1.toB))
        assertEquals(
            listOf(docA.line(749).to - ch2.fromA, 13, 15),
            listOf(c2.toA, c2.fromB, c2.toB)
        )
    }

    @Test
    fun handlesChangesAtEndOfDocument() {
        val chunks = Chunk.build(Text.of(listOf("one", "")), Text.of(listOf("one", "", "")))
        val ch1 = chunks[0]
        assertEquals(listOf(4, 4), listOf(ch1.fromA, ch1.toA))
        assertEquals(listOf(4, 5), listOf(ch1.fromB, ch1.toB))
    }

    @Test
    fun canUpdateChunksForChanges() {
        var stateA = EditorState.create(EditorStateConfig(doc = docA.toString().asDoc()))
        val stateB = EditorState.create(EditorStateConfig(doc = docB.toString().asDoc()))
        var chunks = Chunk.build(stateA.doc, stateB.doc)

        val tr1 = stateA.update(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = 0,
                    insert = InsertContent.StringContent("line NULL\n")
                )
            )
        )
        val chunks1 = Chunk.updateA(chunks, tr1.newDoc, stateB.doc, tr1.changes)
        assertEquals(3, chunks1.size)
        val ch1 = chunks1[0]
        val ch2 = chunks1[1]
        assertEquals(listOf(0, 10, 0, 0), listOf(ch1.fromA, ch1.toA, ch1.fromB, ch1.toB))
        assertEquals(tr1.newDoc.line(501).from, ch2.fromA)
        assertEquals(stateB.doc.line(500).from, ch2.fromB)
        stateA = tr1.state

        val tr2 = stateB.update(
            TransactionSpec(
                changes = ChangeSpec.Multi(
                    listOf(
                        ChangeSpec.Single(
                            from = stateB.doc.line(600).from + 1,
                            insert = InsertContent.StringContent("---")
                        ),
                        ChangeSpec.Single(
                            from = stateB.doc.length,
                            insert = InsertContent.StringContent("\n???")
                        )
                    )
                )
            )
        )
        val chunks2 = Chunk.updateB(chunks1, stateA.doc, tr2.newDoc, tr2.changes)
        assertEquals(5, chunks2.size)
        val ch3 = chunks2[2]
        val ch5 = chunks2[4]
        assertEquals(stateA.doc.line(601).from, ch3.fromA)
        assertEquals(stateA.doc.line(602).from, ch3.toA)
        assertEquals(tr2.newDoc.line(600).from, ch3.fromB)
        assertEquals(tr2.newDoc.line(601).from, ch3.toB)
        assertEquals(stateA.doc.length - 9, ch5.fromA)
        assertEquals(stateA.doc.length + 1, ch5.toA)
        assertEquals(tr2.newDoc.length - 13, ch5.fromB)
        assertEquals(tr2.newDoc.length + 1, ch5.toB)
    }

    @Test
    fun canHandleDeletingUpdates() {
        val stateA = EditorState.create(EditorStateConfig(doc = docA.toString().asDoc()))
        val chunks = Chunk.build(stateA.doc, docB)

        val tr = stateA.update(
            TransactionSpec(changes = ChangeSpec.Single(from = 0, to = 100))
        )
        val chunks1 = Chunk.updateA(chunks, tr.newDoc, docB, tr.changes)
        assertEquals(3, chunks1.size)
        assertEquals(listOf(0, 4283, 6083), chunks1.map { it.fromA })
        assertEquals(listOf(0, 4383, 6181), chunks1.map { it.fromB })
    }

    @Test
    fun clearsChunksWhenAIsSetToEqualB() {
        val sA = EditorState.create(EditorStateConfig(doc = "".asDoc()))
        val sB = EditorState.create(EditorStateConfig(doc = "foo\n".asDoc()))
        val chs = Chunk.build(sA.doc, sB.doc)
        val tr = sA.update(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = 0,
                    insert = InsertContent.StringContent(sB.doc.toString())
                )
            )
        )
        assertEquals(0, Chunk.updateA(chs, tr.newDoc, sB.doc, tr.changes).size)
    }

    @Test
    fun dropsOldChunksWhenADocIsCleared() {
        val sA = EditorState.create(EditorStateConfig(doc = "A\nb\nC\nd\nE".asDoc()))
        val sB = EditorState.create(EditorStateConfig(doc = "a\nb\nc\nd\ne".asDoc()))
        val chs = Chunk.build(sA.doc, sB.doc)
        val tr = sA.update(
            TransactionSpec(changes = ChangeSpec.Single(from = 0, to = sA.doc.length))
        )
        val updated = Chunk.updateA(chs, tr.newDoc, sB.doc, tr.changes)
        assertEquals(1, updated.size)
        assertEquals(sB.doc.length + 1, updated[0].toB)
    }

    @Test
    fun worksForChangesInsideChangedCode() {
        val sA = EditorState.create(EditorStateConfig(doc = "A\nb\nc\nD\nE".asDoc()))
        val sB = EditorState.create(EditorStateConfig(doc = "A\nD\nE".asDoc()))
        val chs = Chunk.build(sA.doc, sB.doc)
        val tr = sA.update(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = 3,
                    to = 3,
                    insert = InsertContent.StringContent("!")
                )
            )
        )
        val updated = Chunk.updateA(chs, tr.newDoc, sB.doc, tr.changes)
        assertEquals(
            "2-7/2-2",
            updated.joinToString(" ") { "${it.fromA}-${it.toA}/${it.fromB}-${it.toB}" }
        )
    }

    @Test
    fun handlesChangesThatOverlapWithTheStartOfChunks() {
        val big = "2".repeat(1100)
        val sA = EditorState.create(
            EditorStateConfig(
                doc = listOf("1", "2", "3", big, "5", "6", "7", "8").joinToString("\n").asDoc()
            )
        )
        val sB = EditorState.create(
            EditorStateConfig(doc = listOf("1", big, "5", "8").joinToString("\n").asDoc())
        )
        val chs = Chunk.build(sA.doc, sB.doc)
        val tr = sB.update(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = 2,
                    to = 2,
                    insert = InsertContent.StringContent("2\n3\n")
                )
            )
        )
        val updated = Chunk.updateB(chs, sA.doc, tr.newDoc, tr.changes)
        assertEquals(
            "1109-1113/1109-1109",
            updated.joinToString(" ") { "${it.fromA}-${it.toA}/${it.fromB}-${it.toB}" }
        )
    }

    @Test
    fun tracksChunkPrecision() {
        val head = "---\n".repeat(500)
        val sA = EditorState.create(
            EditorStateConfig(doc = (head + "a\n".repeat(1000)).asDoc())
        )
        val sB = EditorState.create(
            EditorStateConfig(doc = ("///$head" + "b\n").asDoc())
        )
        val chs = Chunk.build(sA.doc, sB.doc)
        assertEquals(2, chs.size)
        assertTrue(chs.all { it.precise })
        val tr = sB.update(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = sB.doc.length,
                    insert = InsertContent.StringContent("b\n".repeat(999))
                )
            )
        )
        val updated = Chunk.updateB(
            chs,
            sA.doc,
            tr.newDoc,
            tr.changes,
            DiffConfig(scanLimit = 100)
        )
        assertEquals(2, updated.size)
        assertTrue(updated[0].precise)
        assertFalse(updated[1].precise)
    }

    @Test
    fun properlyCopiesOverUnaffectedChunksAtEndOfDoc() {
        val identical = "\n\n${"A".repeat(896)}\n${"B".repeat(112)}\n\n\n\n\nG"
        val sA = EditorState.create(EditorStateConfig(doc = "AB$identical".asDoc()))
        val sB = EditorState.create(
            EditorStateConfig(doc = "A${identical}\nH\nI\nJ".asDoc())
        )
        val chs = Chunk.build(sA.doc, sB.doc)
        assertEquals(2, chs.size)
        val tr = sA.update(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = 1,
                    to = 1,
                    insert = InsertContent.StringContent("!")
                )
            )
        )
        val updated = Chunk.updateA(chs, tr.newDoc, sB.doc, tr.changes)
        assertEquals(2, updated.size)
    }

    @Test
    fun correctlyHandlesChangesToEmptyDoc() {
        val sA = EditorState.create()
        val sB = EditorState.create(EditorStateConfig(doc = "a".asDoc()))
        val chs = Chunk.build(sA.doc, sB.doc)
        assertEquals(1, chs.size)
        val tr = sB.update(
            TransactionSpec(changes = ChangeSpec.Single(from = 0, to = 1))
        )
        val updated = Chunk.updateB(chs, sA.doc, tr.newDoc, tr.changes)
        assertEquals(0, updated.size)
    }
}
