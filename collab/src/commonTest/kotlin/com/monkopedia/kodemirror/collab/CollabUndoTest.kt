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
package com.monkopedia.kodemirror.collab

import com.monkopedia.kodemirror.commands.history
import com.monkopedia.kodemirror.commands.isolateHistory
import com.monkopedia.kodemirror.commands.redo
import com.monkopedia.kodemirror.commands.undo
import com.monkopedia.kodemirror.state.ChangeDesc
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * DummyServer variant that uses EditorSession + history() for undo/redo tests.
 * All state mutations go through the views to keep history consistent.
 */
private class UndoServer(
    doc: String = "",
    n: Int = 2,
    extensions: List<Extension> = emptyList(),
    collabConf: CollabConfig = CollabConfig()
) {
    val views: MutableList<EditorSession> = mutableListOf()
    val updates: MutableList<Update> = mutableListOf()
    val delayed: MutableList<Int> = mutableListOf()

    val states: List<EditorState> get() = views.map { it.state }

    init {
        for (i in 0 until n) {
            val state = EditorState.create(
                EditorStateConfig(
                    doc = doc.asDoc(),
                    extensions = ExtensionList(
                        buildList {
                            add(history())
                            add(collab(collabConf))
                            addAll(extensions)
                        }
                    )
                )
            )
            views.add(EditorSession(state))
        }
    }

    fun sync(client: Int) {
        val state = views[client].state
        val version = getSyncedVersion(state)
        if (version != updates.size) {
            val spec = receiveUpdates(state, updates.subList(version, updates.size))
            views[client].dispatch(spec)
        }
    }

    fun send(client: Int) {
        val state = views[client].state
        val sendable = sendableUpdates(state)
        if (sendable.isNotEmpty()) {
            updates.addAll(
                sendable.map { Update(it.changes, it.clientID, it.effects) }
            )
        }
    }

    fun broadcast(client: Int) {
        if (client in delayed) return
        sync(client)
        send(client)
        for (i in views.indices) {
            if (i != client) sync(i)
        }
    }

    fun update(client: Int, f: (EditorState) -> Transaction) {
        val tr = f(views[client].state)
        views[client].dispatchTransaction(tr)
        broadcast(client)
    }

    fun type(client: Int, text: String, pos: DocPos = views[client].state.selection.main.head) {
        update(client) { s ->
            s.update(
                TransactionSpec(
                    changes = ChangeSpec.Single(
                        from = pos,
                        insert = InsertContent.StringContent(text)
                    ),
                    selection = SelectionSpec.CursorSpec(pos + text.length)
                )
            )
        }
    }

    fun conv(doc: String) {
        for (view in views) {
            assertEquals(doc, view.state.doc.toString())
        }
    }

    fun delay(client: Int, f: () -> Unit) {
        delayed.add(client)
        f()
        delayed.removeAt(delayed.size - 1)
        broadcast(client)
    }

    fun doUndo(client: Int) {
        undo(views[client])
        broadcast(client)
    }

    fun doRedo(client: Int) {
        redo(views[client])
        broadcast(client)
    }
}

/**
 * Tests that mimic the exact sync() pattern used in CollabDemo.kt:
 * collect from A, collect from B, then apply to both using drop().
 */
class CollabDemoPatternTest {

    private fun createSession(doc: String, clientID: String): EditorSession {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                extensions = collab(CollabConfig(clientID = clientID))
            )
        )
        return EditorSession(state)
    }

    private fun syncOne(session: EditorSession, sharedUpdates: MutableList<Update>) {
        val version = getSyncedVersion(session.state)
        val pending = sharedUpdates.drop(version)
        if (pending.isNotEmpty()) {
            session.dispatch(receiveUpdates(session.state, pending))
        }
    }

    private fun sendOne(session: EditorSession, sharedUpdates: MutableList<Update>) {
        val sendable = sendableUpdates(session.state)
        for (u in sendable) {
            sharedUpdates.add(Update(u.changes, u.clientID, u.effects))
        }
    }

    /**
     * Correct sync pattern: for each client, receive then send in sequence,
     * then receive on all again to converge.
     */
    private fun sync(
        sessionA: EditorSession,
        sessionB: EditorSession,
        sharedUpdates: MutableList<Update>
    ) {
        syncOne(sessionA, sharedUpdates)
        sendOne(sessionA, sharedUpdates)
        syncOne(sessionB, sharedUpdates)
        sendOne(sessionB, sharedUpdates)
        syncOne(sessionA, sharedUpdates)
        syncOne(sessionB, sharedUpdates)
    }

    @Test
    fun demoSyncConvergesForSimpleEdits() {
        val sessionA = createSession("hello", "editor-a")
        val sessionB = createSession("hello", "editor-b")
        val sharedUpdates = mutableListOf<Update>()

        // A types " world" at position 5
        sessionA.dispatchTransaction(
            sessionA.state.update(
                TransactionSpec(
                    changes = ChangeSpec.Single(
                        from = DocPos(5),
                        insert = InsertContent.StringContent(" world")
                    )
                )
            )
        )
        sync(sessionA, sessionB, sharedUpdates)
        assertEquals("hello world", sessionA.state.doc.toString())
        assertEquals("hello world", sessionB.state.doc.toString())

        // B types "!" at end
        sessionB.dispatchTransaction(
            sessionB.state.update(
                TransactionSpec(
                    changes = ChangeSpec.Single(
                        from = DocPos(11),
                        insert = InsertContent.StringContent("!")
                    )
                )
            )
        )
        sync(sessionA, sessionB, sharedUpdates)
        assertEquals("hello world!", sessionA.state.doc.toString())
        assertEquals("hello world!", sessionB.state.doc.toString())
    }

    @Test
    fun demoSyncConvergesForConcurrentEdits() {
        val sessionA = createSession("hello", "editor-a")
        val sessionB = createSession("hello", "editor-b")
        val sharedUpdates = mutableListOf<Update>()

        // Both edit before sync
        sessionA.dispatchTransaction(
            sessionA.state.update(
                TransactionSpec(
                    changes = ChangeSpec.Single(
                        from = DocPos(5),
                        insert = InsertContent.StringContent(" world")
                    )
                )
            )
        )
        sessionB.dispatchTransaction(
            sessionB.state.update(
                TransactionSpec(
                    changes = ChangeSpec.Single(
                        from = DocPos(0),
                        insert = InsertContent.StringContent("Say: ")
                    )
                )
            )
        )

        // Now sync — both should converge
        sync(sessionA, sessionB, sharedUpdates)
        assertEquals(sessionA.state.doc.toString(), sessionB.state.doc.toString())
        assertEquals("Say: hello world", sessionA.state.doc.toString())
    }

    @Test
    fun demoSyncMultipleRoundsConverge() {
        val sessionA = createSession("", "editor-a")
        val sessionB = createSession("", "editor-b")
        val sharedUpdates = mutableListOf<Update>()

        // Round 1: A types
        sessionA.dispatchTransaction(
            sessionA.state.update(
                TransactionSpec(
                    changes = ChangeSpec.Single(
                        from = DocPos(0),
                        insert = InsertContent.StringContent("A")
                    )
                )
            )
        )
        sync(sessionA, sessionB, sharedUpdates)

        // Round 2: B types
        sessionB.dispatchTransaction(
            sessionB.state.update(
                TransactionSpec(
                    changes = ChangeSpec.Single(
                        from = DocPos(1),
                        insert = InsertContent.StringContent("B")
                    )
                )
            )
        )
        sync(sessionA, sessionB, sharedUpdates)

        // Round 3: Both type concurrently
        sessionA.dispatchTransaction(
            sessionA.state.update(
                TransactionSpec(
                    changes = ChangeSpec.Single(
                        from = DocPos(2),
                        insert = InsertContent.StringContent("C")
                    )
                )
            )
        )
        sessionB.dispatchTransaction(
            sessionB.state.update(
                TransactionSpec(
                    changes = ChangeSpec.Single(
                        from = DocPos(2),
                        insert = InsertContent.StringContent("D")
                    )
                )
            )
        )
        sync(sessionA, sessionB, sharedUpdates)
        assertEquals(sessionA.state.doc.toString(), sessionB.state.doc.toString())
    }
}

class CollabUndoTest {

    @Test
    fun supportsUndo() {
        val s = UndoServer()
        s.type(0, "A")
        s.type(1, "a")
        s.type(0, "B")
        s.doUndo(1)
        s.conv("AB")
        s.type(1, "b")
        s.type(0, "C")
        s.conv("bABC")
    }

    @Test
    fun supportsRedo() {
        val s = UndoServer()
        s.type(0, "A")
        s.type(1, "a")
        s.type(0, "B")
        s.doUndo(1)
        s.doRedo(1)
        s.type(1, "b")
        s.type(0, "C")
        s.conv("abABC")
    }

    @Test
    fun supportsDeepUndo() {
        val s = UndoServer(doc = "hello bye")
        s.update(0) {
            it.update(TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos(5))))
        }
        s.update(1) {
            it.update(TransactionSpec(selection = SelectionSpec.CursorSpec(DocPos(9))))
        }
        s.type(0, "!")
        s.type(1, "!")
        s.update(0) {
            it.update(
                TransactionSpec(
                    annotations = listOf(isolateHistory.of("full"))
                )
            )
        }
        s.delay(0) {
            s.type(0, " ...")
            s.type(1, " ,,,")
        }
        s.update(0) {
            it.update(
                TransactionSpec(
                    annotations = listOf(isolateHistory.of("full"))
                )
            )
        }
        s.type(0, "*")
        s.type(1, "*")
        s.doUndo(0)
        s.conv("hello! ... bye! ,,,*")
        s.doUndo(0)
        s.doUndo(0)
        s.conv("hello bye! ,,,*")
        s.doRedo(0)
        s.doRedo(0)
        s.doRedo(0)
        s.conv("hello! ...* bye! ,,,*")
        s.doUndo(0)
        s.doUndo(0)
        s.conv("hello! bye! ,,,*")
        s.doUndo(1)
        s.conv("hello! bye")
    }

    @Test
    fun supportsUndoWithClashingEvents() {
        val s = UndoServer(doc = "okay!")
        s.type(0, "A", DocPos(5))
        s.delay(0) {
            s.type(0, "B", DocPos(3))
            s.type(0, "C", DocPos(4))
            s.type(0, "D", DocPos(0))
            s.update(1) {
                it.update(TransactionSpec(changes = ChangeSpec.Single(DocPos(1), DocPos(4))))
            }
        }
        s.conv("DoBC!A")
        s.doUndo(0)
        s.doUndo(0)
        s.conv("o!A")
        assertEquals(DocPos(3), s.states[0].selection.main.head)
    }

    @Test
    fun handlesConflictingSteps() {
        val s = UndoServer(doc = "abcde")
        s.delay(0) {
            s.update(0) {
                it.update(TransactionSpec(changes = ChangeSpec.Single(DocPos(2), DocPos(3))))
            }
            s.type(0, "x")
            s.update(1) {
                it.update(TransactionSpec(changes = ChangeSpec.Single(DocPos(1), DocPos(4))))
            }
        }
        s.doUndo(0)
        s.doUndo(0)
        s.conv("ace")
    }

    @Test
    fun canUndoSimultaneousTyping() {
        val s = UndoServer(doc = "A B")
        s.delay(0) {
            s.type(0, "1", DocPos(1))
            s.type(0, "2")
            s.type(1, "x", DocPos(3))
            s.type(1, "y")
        }
        s.conv("A12 Bxy")
        s.doUndo(0)
        s.conv("A Bxy")
        s.doUndo(1)
        s.conv("A B")
    }

    @Test
    fun supportsSharedEffects() {
        data class Mark(val from: DocPos, val to: DocPos, val id: String) {
            fun map(mapping: ChangeDesc): Mark? {
                val newFrom = mapping.mapPos(from, 1)
                val newTo = mapping.mapPos(to, -1)
                return if (newFrom >= newTo) null else Mark(newFrom, newTo, id)
            }

            override fun toString(): String = "${from.value}-${to.value}=$id"
        }

        val addMark = StateEffect.define<Mark> { v, m -> v.map(m) }
        val marks = StateField.define(
            StateFieldSpec<List<Mark>>(
                create = { emptyList() },
                update = { value, tr ->
                    var result = value.mapNotNull { it.map(tr.changes) }
                    for (effect in tr.effects) {
                        effect.asType(addMark)?.let {
                            result = result + it.value
                        }
                    }
                    result.sortedWith(compareBy { it.id })
                }
            )
        )

        val s = UndoServer(
            doc = "hello",
            extensions = listOf(marks),
            collabConf = CollabConfig(
                sharedEffects = { tr ->
                    tr.effects.mapNotNull { it.asType(addMark) }
                }
            )
        )
        s.delay(0) {
            s.delay(1) {
                s.update(0) {
                    it.update(
                        TransactionSpec(
                            effects = listOf(addMark.of(Mark(DocPos(1), DocPos(3), "a")))
                        )
                    )
                }
                s.update(1) {
                    it.update(
                        TransactionSpec(
                            effects = listOf(addMark.of(Mark(DocPos(3), DocPos(5), "b")))
                        )
                    )
                }
                s.type(0, "A", DocPos(4))
                s.type(1, "B", DocPos(0))
                assertEquals(
                    "1-3=a",
                    s.states[0].field(marks).joinToString(",")
                )
                assertEquals(
                    "4-6=b",
                    s.states[1].field(marks).joinToString(",")
                )
            }
        }
        s.conv("BhellAo")
        assertEquals(
            "2-4=a,4-7=b",
            s.states[0].field(marks).joinToString(",")
        )
        assertEquals(
            "2-4=a,4-7=b",
            s.states[1].field(marks).joinToString(",")
        )
    }
}
