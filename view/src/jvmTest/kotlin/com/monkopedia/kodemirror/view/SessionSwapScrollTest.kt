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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SessionSwapScrollTest {

    /**
     * Regression test for #166: the vertical/horizontal scroll states must be
     * keyed on `session` like every other per-session remembered value. If they
     * are not, swapping the [EditorSession] passed to [KodeMirror] carries the
     * previous document's scroll position/range into the new document and wedges
     * scrolling.
     *
     * Setup: render a long document (session A) under a bounded height so it
     * must scroll, move the caret near the end (with scrollIntoView) to scroll
     * down, then swap to a second long document (session B). With the fix B
     * starts at the top; without it B inherits A's scroll position.
     */
    @Test
    fun swappingSession_resetsScrollPosition() {
        val docA = (1..200).joinToString("\n") { "A line $it" }.asDoc()
        val docB = (1..200).joinToString("\n") { "B line $it" }.asDoc()

        runDesktopComposeUiTest {
            lateinit var sessionA: EditorSession
            lateinit var sessionB: EditorSession
            var showB by mutableStateOf(false)

            setContent {
                sessionA = remember {
                    EditorSession(EditorState.create(EditorStateConfig(doc = docA)))
                }
                sessionB = remember {
                    EditorSession(EditorState.create(EditorStateConfig(doc = docB)))
                }
                Box(Modifier.height(200.dp)) {
                    KodeMirror(session = if (showB) sessionB else sessionA)
                }
            }
            waitForIdle()

            // Scroll session A down via the caret-reveal path: move the cursor
            // to the end of the document and request scrollIntoView.
            sessionA.dispatch(
                TransactionSpec(
                    selection = SelectionSpec.CursorSpec(
                        DocPos(sessionA.state.doc.length)
                    ),
                    scrollIntoView = true
                )
            )
            waitForIdle()

            // Confirm the setup actually scrolled A away from the top.
            val implA = sessionA as EditorSessionImpl
            assertTrue(
                implA.lastFirstVisibleItem > 0,
                "expected session A to have scrolled down, but firstVisibleItem " +
                    "was ${implA.lastFirstVisibleItem}"
            )

            // Swap to session B (a different long document).
            showB = true
            waitForIdle()

            // B must render from the top with a fresh scroll state.
            val implB = sessionB as EditorSessionImpl
            assertEquals(
                0,
                implB.lastFirstVisibleItem,
                "session B inherited session A's scroll position; scroll state " +
                    "was not keyed on the session (#166)"
            )
        }
    }
}
