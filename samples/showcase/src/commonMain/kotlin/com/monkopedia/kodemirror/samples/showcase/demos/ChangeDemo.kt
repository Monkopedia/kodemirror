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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.endPos
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.state.transactionSpec
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.insertAt
import com.monkopedia.kodemirror.view.rememberEditorSession
import com.monkopedia.kodemirror.view.setDoc

@Composable
fun ChangeDemo() {
    val session = rememberEditorSession(
        doc = SampleDocs.javascript,
        extensions = basicSetup + javascript().extension
    )

    DemoScaffold(
        title = "Document Changes",
        description = "Programmatic document manipulation: insert, replace, and delete.",
        controls = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = {
                    session.insertAt(DocPos.ZERO, "// Inserted header\n")
                }) { Text("Insert at top") }
                Button(onClick = {
                    session.insertAt(session.state.doc.endPos, "\n// Appended footer")
                }) { Text("Append at end") }
                Button(onClick = {
                    val firstLineEnd = session.state.doc.line(LineNumber(1)).to
                    session.dispatch(
                        transactionSpec {
                            replace(
                                DocPos.ZERO,
                                firstLineEnd,
                                "// Replaced first line"
                            )
                        }
                    )
                }) { Text("Replace line 1") }
                Button(onClick = {
                    session.setDoc(SampleDocs.javascript)
                }) { Text("Reset") }
            }
        }
    ) {
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
