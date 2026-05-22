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
package com.monkopedia.kodemirror.lsp

import com.monkopedia.kodemirror.state.ChangeSet
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.TextDocumentContentChangeEventRange
import com.monkopedia.lsp.TextDocumentContentChangeEventVariant
import com.monkopedia.lsp.TextDocumentSyncKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class DocumentSyncTest {
    private fun doc(vararg lines: String): Text = Text.of(lines.toList())

    private fun insertion(from: Int, to: Int, text: String, docLength: Int): ChangeSet =
        ChangeSet.of(
            ChangeSpec.Single(
                from = DocPos(from),
                to = DocPos(to),
                insert = InsertContent.StringContent(text)
            ),
            docLength
        )

    @Test
    fun toPositionMapsLineAndCharacter() {
        val d = doc("hello", "world")
        assertEquals(Position(0u, 0u), toPosition(0, d))
        assertEquals(Position(0u, 3u), toPosition(3, d))
        // offset 5 is end of line 0 (before the newline).
        assertEquals(Position(0u, 5u), toPosition(5, d))
        // offset 6 is start of line 1 (newline is at offset 5).
        assertEquals(Position(1u, 0u), toPosition(6, d))
        assertEquals(Position(1u, 2u), toPosition(8, d))
    }

    @Test
    fun fromPositionInvertsToPosition() {
        val d = doc("hello", "world")
        for (offset in 0..d.length) {
            assertEquals(offset, fromPosition(toPosition(offset, d), d))
        }
    }

    @Test
    fun positionRoundTripsAcrossUtf16SurrogatePair() {
        // An emoji is two UTF-16 code units; LSP character offsets are code units.
        val d = doc("a😀b")
        assertEquals(Position(0u, 1u), toPosition(1, d))
        // After the surrogate pair the character offset is 3.
        assertEquals(Position(0u, 3u), toPosition(3, d))
        assertEquals(3, fromPosition(Position(0u, 3u), d))
    }

    @Test
    fun fromPositionClampsOutOfRange() {
        val d = doc("hi")
        // Character past the line clamps to line length.
        assertEquals(2, fromPosition(Position(0u, 99u), d))
        // Line past the document clamps to the last line; character 0 -> its start.
        assertEquals(0, fromPosition(Position(99u, 0u), d))
    }

    @Test
    fun syncModeFromNumberCapability() {
        val caps = ServerCapabilities(textDocumentSync = JsonPrimitive(2))
        val mode = DocumentSyncMode.forCapabilities(caps)
        assertEquals(TextDocumentSyncKind.INCREMENTAL, mode.change)
        assertTrue(mode.openClose)
    }

    @Test
    fun syncModeFromOptionsCapability() {
        val caps = ServerCapabilities(
            textDocumentSync = buildJsonObject {
                put("openClose", JsonPrimitive(true))
                put("change", JsonPrimitive(1))
            }
        )
        val mode = DocumentSyncMode.forCapabilities(caps)
        assertEquals(TextDocumentSyncKind.FULL, mode.change)
        assertTrue(mode.openClose)
    }

    @Test
    fun syncModeDefaultsToNone() {
        val mode = DocumentSyncMode.forCapabilities(ServerCapabilities())
        assertEquals(TextDocumentSyncKind.NONE, mode.change)
    }

    @Test
    fun fullSyncSendsWholeDocument() {
        val prev = doc("abc")
        val change = insertion(1, 1, "X", prev.length)
        val next = change.apply(prev)
        val events = buildContentChanges(
            change,
            prev,
            next,
            DocumentSyncMode(openClose = true, change = TextDocumentSyncKind.FULL)
        )
        assertEquals(1, events.size)
        assertEquals("aXbc", (events[0] as TextDocumentContentChangeEventVariant).text)
    }

    @Test
    fun incrementalSyncSendsRangedChange() {
        val prev = doc("abc")
        val change = insertion(1, 2, "XY", prev.length)
        val next = change.apply(prev)
        assertEquals("aXYc", next.toString())
        val events = buildContentChanges(
            change,
            prev,
            next,
            DocumentSyncMode(openClose = true, change = TextDocumentSyncKind.INCREMENTAL)
        )
        assertEquals(1, events.size)
        val ranged = events[0] as TextDocumentContentChangeEventRange
        assertEquals("XY", ranged.text)
        assertEquals(Position(0u, 1u), ranged.range.start)
        assertEquals(Position(0u, 2u), ranged.range.end)
    }

    @Test
    fun incrementalMultiChangeEmittedHighestOffsetFirst() {
        val prev = doc("0123456789")
        // Two non-overlapping edits in old-doc coordinates.
        val change = ChangeSet.of(
            ChangeSpec.Multi(
                listOf(
                    ChangeSpec.Single(DocPos(1), DocPos(2), InsertContent.StringContent("A")),
                    ChangeSpec.Single(DocPos(5), DocPos(6), InsertContent.StringContent("B"))
                )
            ),
            prev.length
        )
        val next = change.apply(prev)
        val events = buildContentChanges(
            change,
            prev,
            next,
            DocumentSyncMode(openClose = true, change = TextDocumentSyncKind.INCREMENTAL)
        ).map { it as TextDocumentContentChangeEventRange }
        assertEquals(2, events.size)
        // Highest offset first so earlier ranges stay valid when applied in order.
        assertEquals(5u, events[0].range.start.character)
        assertEquals(1u, events[1].range.start.character)
        // Applying the events sequentially to prev reproduces next.
        var result = prev.toString()
        for (e in events) {
            val from = fromPosition(e.range.start, Text.of(listOf(result)))
            val to = fromPosition(e.range.end, Text.of(listOf(result)))
            result = result.substring(0, from) + e.text + result.substring(to)
        }
        assertEquals(next.toString(), result)
    }

    @Test
    fun workspaceMappingMapsThroughInFlightEdits() {
        val mapping = WorkspaceMapping("file:///x")
        val prev = doc("hello world")
        // A request returned position at offset 6 ("world"). Meanwhile, "AB" was
        // inserted at offset 0, shifting everything right by 2.
        val edit = insertion(0, 0, "AB", prev.length)
        mapping.addChanges(edit.desc)
        assertEquals(8, mapping.mapPos(6))
        // Mapping an LSP position interpreted against the request-time doc.
        assertEquals(8, mapping.mapPosition(Position(0u, 6u), prev))
    }

    @Test
    fun workspaceMappingIsIdentityWithoutEdits() {
        val mapping = WorkspaceMapping("file:///x")
        assertEquals(4, mapping.mapPos(4))
    }
}
