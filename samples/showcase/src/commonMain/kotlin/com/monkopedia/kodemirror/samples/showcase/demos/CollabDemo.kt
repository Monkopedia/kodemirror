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
 */
package com.monkopedia.kodemirror.samples.showcase.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.samples.showcase.showcaseSetup
import com.monkopedia.kodemirror.collab.CollabConfig
import com.monkopedia.kodemirror.collab.Update
import com.monkopedia.kodemirror.collab.collab
import com.monkopedia.kodemirror.collab.getSyncedVersion
import com.monkopedia.kodemirror.collab.receiveUpdates
import com.monkopedia.kodemirror.collab.sendableUpdates
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.rememberEditorSession

@Composable
fun CollabDemo() {
    val sessionA = rememberEditorSession(
        doc = SampleDocs.collabInitial,
        extensions = showcaseSetup + javascript().extension +
            collab(CollabConfig(clientID = "editor-a"))
    )
    val sessionB = rememberEditorSession(
        doc = SampleDocs.collabInitial,
        extensions = showcaseSetup + javascript().extension +
            collab(CollabConfig(clientID = "editor-b"))
    )

    val sharedUpdates = remember { mutableListOf<Update>() }

    fun sync() {
        // Collect updates from A
        val updatesA = sendableUpdates(sessionA.state)
        for (u in updatesA) {
            sharedUpdates.add(
                Update(
                    changes = u.changes,
                    clientID = u.clientID,
                    effects = u.effects
                )
            )
        }
        // Collect updates from B
        val updatesB = sendableUpdates(sessionB.state)
        for (u in updatesB) {
            sharedUpdates.add(
                Update(
                    changes = u.changes,
                    clientID = u.clientID,
                    effects = u.effects
                )
            )
        }
        // Apply to both
        val versionA = getSyncedVersion(sessionA.state)
        val forA = sharedUpdates.drop(versionA)
        if (forA.isNotEmpty()) {
            sessionA.dispatch(receiveUpdates(sessionA.state, forA))
        }
        val versionB = getSyncedVersion(sessionB.state)
        val forB = sharedUpdates.drop(versionB)
        if (forB.isNotEmpty()) {
            sessionB.dispatch(receiveUpdates(sessionB.state, forB))
        }
    }

    DemoScaffold(
        title = "Collaboration",
        description = "Two editors sharing a document via the collab module. " +
            "Click Sync to exchange updates.",
        controls = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { sync() }) { Text("Sync") }
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KodeMirror(
                session = sessionA,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            KodeMirror(
                session = sessionB,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}
