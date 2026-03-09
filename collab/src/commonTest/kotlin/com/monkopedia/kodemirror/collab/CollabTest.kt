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

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals

open class DummyServer(
    doc: String = "",
    n: Int = 2,
    extensions: List<Extension> = emptyList(),
    collabConf: CollabConfig = CollabConfig()
) {
    val states: MutableList<EditorState> = mutableListOf()
    val updates: MutableList<Update> = mutableListOf()
    val delayed: MutableList<Int> = mutableListOf()

    init {
        for (i in 0 until n) {
            states.add(
                EditorState.create(
                    EditorStateConfig(
                        doc = doc.asDoc(),
                        extensions = ExtensionList(
                            buildList {
                                addAll(extraExtensions())
                                add(collab(collabConf))
                                addAll(extensions)
                            }
                        )
                    )
                )
            )
        }
    }

    protected open fun extraExtensions(): List<Extension> = emptyList()

    open fun sync(client: Int) {
        val state = states[client]
        val version = getSyncedVersion(state)
        if (version != updates.size) {
            states[client] = state.update(
                receiveUpdates(state, updates.subList(version, updates.size))
            ).state
        }
    }

    fun send(client: Int) {
        val state = states[client]
        val sendable = sendableUpdates(state)
        if (sendable.isNotEmpty()) {
            updates.addAll(
                sendable.map {
                    Update(it.changes, it.clientID, it.effects)
                }
            )
        }
    }

    fun broadcast(client: Int) {
        if (client in delayed) return
        sync(client)
        send(client)
        for (i in states.indices) {
            if (i != client) sync(i)
        }
    }

    open fun update(client: Int, f: (EditorState) -> Transaction) {
        states[client] = f(states[client]).state
        broadcast(client)
    }

    fun type(client: Int, text: String, pos: DocPos = states[client].selection.main.head) {
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
        for (state in states) {
            assertEquals(doc, state.doc.toString())
        }
    }

    fun delay(client: Int, f: () -> Unit) {
        delayed.add(client)
        f()
        delayed.removeAt(delayed.size - 1)
        broadcast(client)
    }
}

class CollabTest {

    @Test
    fun convergesForSimpleChanges() {
        val s = DummyServer()
        s.type(0, "hi")
        s.type(1, "ok", DocPos(2))
        s.type(0, "!", DocPos(4))
        s.type(1, "...", DocPos.ZERO)
        s.conv("...hiok!")
    }

    @Test
    fun convergesForMultipleLocalChanges() {
        val s = DummyServer()
        s.type(0, "hi")
        s.delay(0) {
            s.type(0, "A")
            s.type(1, "X", DocPos(2))
            s.type(0, "B")
            s.type(1, "Y")
        }
        s.conv("hiXYAB")
    }

    @Test
    fun convergesWithThreePeers() {
        val s = DummyServer(n = 3)
        s.type(0, "A")
        s.type(1, "U")
        s.type(2, "X")
        s.type(0, "B")
        s.type(1, "V")
        s.type(2, "Y")
        s.conv("XYUVAB")
    }

    @Test
    fun convergesWithThreePeersWithMultipleSteps() {
        val s = DummyServer(n = 3)
        s.type(0, "A")
        s.delay(1) {
            s.type(1, "U")
            s.type(2, "X")
            s.type(0, "B")
            s.type(1, "V")
            s.type(2, "Y")
        }
        s.conv("XYUVAB")
    }

    @Test
    fun allowsYouToSetTheInitialVersion() {
        val state = EditorState.create(
            EditorStateConfig(
                extensions = collab(CollabConfig(startVersion = 20))
            )
        )
        assertEquals(20, getSyncedVersion(state))
    }

    @Test
    fun associatesTransactionInfoWithLocalChanges() {
        val state = EditorState.create(
            EditorStateConfig(extensions = collab())
        )
        val tr = state.update(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = DocPos.ZERO,
                    insert = InsertContent.StringContent("hi")
                )
            )
        )
        assertEquals(tr, sendableUpdates(tr.state)[0].origin)
    }

    @Test
    fun clientIdsSurviveReconfiguration() {
        val ext = collab()
        val state = EditorState.create(
            EditorStateConfig(extensions = ext)
        )
        val state2 = state.update(
            TransactionSpec(
                effects = listOf(StateEffect.reconfigure.of(ext))
            )
        ).state
        assertEquals(getClientID(state), getClientID(state2))
    }
}
