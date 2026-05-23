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

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Unit tests for the clipboard commands' platform-independent logic.
 *
 * The wasmJs browser-clipboard path (`navigator.clipboard`) can only be
 * verified in a real browser. These tests cover the JVM/Compose path and the
 * internal-buffer fallback that the wasmJs path also relies on for paste.
 */
class ClipboardCommandsTest {

    /** Minimal in-memory [ClipboardManager] standing in for the system clipboard. */
    private class FakeClipboardManager : ClipboardManager {
        var stored: AnnotatedString? = null
        override fun setText(annotatedString: AnnotatedString) {
            stored = annotatedString
        }
        override fun getText(): AnnotatedString? = stored
    }

    private fun session(doc: String): EditorSessionImpl {
        val state = EditorState.create(EditorStateConfig(doc = doc.asDoc()))
        return EditorSession(state) as EditorSessionImpl
    }

    private fun EditorSessionImpl.select(from: Int, to: Int) {
        dispatch(TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos(from), DocPos(to))))
    }

    private fun EditorSessionImpl.cursor(at: Int) {
        dispatch(TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos(at))))
    }

    @Test
    fun copy_writesSelectionToClipboardAndInternalBuffer() {
        val s = session("Hello world")
        val cm = FakeClipboardManager()
        s.clipboardManager = cm
        s.select(0, 5)

        assertTrue(clipboardCopy(s))

        // On JVM platformWriteClipboard is a no-op, so the Compose
        // ClipboardManager receives the text, and the internal buffer mirrors it.
        assertEquals("Hello", cm.stored?.text)
        assertEquals("Hello", s.internalClipboard)
        // Copy must not mutate the document.
        assertEquals("Hello world", s.state.doc.toString())
    }

    @Test
    fun copy_emptySelection_doesNothing() {
        val s = session("Hello world")
        val cm = FakeClipboardManager()
        s.clipboardManager = cm
        s.cursor(3)

        assertTrue(clipboardCopy(s))

        assertNull(cm.stored)
        assertNull(s.internalClipboard)
    }

    @Test
    fun cut_writesToClipboardAndRemovesSelection() {
        val s = session("Hello world")
        val cm = FakeClipboardManager()
        s.clipboardManager = cm
        s.select(0, 6) // "Hello "

        assertTrue(clipboardCut(s))

        assertEquals("Hello ", cm.stored?.text)
        assertEquals("Hello ", s.internalClipboard)
        assertEquals("world", s.state.doc.toString())
    }

    @Test
    fun paste_insertsClipboardTextAtCursor() {
        val s = session("Hello world")
        val cm = FakeClipboardManager()
        cm.stored = AnnotatedString("ABC")
        s.clipboardManager = cm
        s.cursor(0)

        assertTrue(clipboardPaste(s))

        assertEquals("ABCHello world", s.state.doc.toString())
        // Cursor advanced past the inserted text.
        assertEquals(3, s.state.selection.main.head.value)
    }

    @Test
    fun paste_replacesSelection() {
        val s = session("Hello world")
        val cm = FakeClipboardManager()
        cm.stored = AnnotatedString("XYZ")
        s.clipboardManager = cm
        s.select(0, 5) // replace "Hello"

        assertTrue(clipboardPaste(s))

        assertEquals("XYZ world", s.state.doc.toString())
    }

    @Test
    fun paste_fallsBackToInternalBufferWhenClipboardEmpty() {
        // Simulates wasmJs: the Compose ClipboardManager.getText() returns null
        // even after a copy, so paste relies on the internal buffer.
        val s = session("Hello world")
        val cm = FakeClipboardManager()
        s.clipboardManager = cm
        s.internalClipboard = "FROM_BUFFER"
        s.cursor(0)

        assertTrue(clipboardPaste(s))

        assertEquals("FROM_BUFFERHello world", s.state.doc.toString())
    }

    @Test
    fun paste_noClipboardOrBuffer_returnsFalse() {
        val s = session("Hello world")
        s.clipboardManager = FakeClipboardManager()
        s.cursor(0)

        // No text anywhere — should not consume the event so the browser's
        // native paste path can take over.
        assertEquals(false, clipboardPaste(s))
        assertEquals("Hello world", s.state.doc.toString())
    }

    @Test
    fun copyThenPaste_roundTripsThroughInternalBuffer() {
        val s = session("abcdef")
        val cm = FakeClipboardManager()
        s.clipboardManager = cm
        s.select(0, 3) // "abc"
        clipboardCopy(s)
        s.cursor(6) // end
        clipboardPaste(s)

        assertEquals("abcdefabc", s.state.doc.toString())
    }
}
