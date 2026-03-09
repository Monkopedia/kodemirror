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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.onSelection
import com.monkopedia.kodemirror.view.rememberEditorSession
import com.monkopedia.kodemirror.view.selectAll

@Composable
fun SelectionDemo() {
    var selectionInfo by remember { mutableStateOf("No selection") }

    val session = rememberEditorSession(
        doc = SampleDocs.javascript,
        extensions = basicSetup + javascript().extension +
            onSelection { sel ->
                val main = sel.main
                selectionInfo = if (main.empty) {
                    "Cursor at ${main.head}"
                } else {
                    "Selection: ${main.anchor}..${main.head} " +
                        "(${(main.to - main.from)} chars)"
                }
            }
    )

    DemoScaffold(
        title = "Selections",
        description = "Programmatic cursor positioning, range selection, and multi-cursor.",
        controls = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = {
                    session.dispatch(
                        TransactionSpec(
                            selection = SelectionSpec.CursorSpec(DocPos.ZERO)
                        )
                    )
                }) { Text("Go to start") }
                Button(onClick = {
                    val line3 = session.state.doc.line(LineNumber(3))
                    session.dispatch(
                        TransactionSpec(
                            selection = SelectionSpec.CursorSpec(
                                anchor = line3.from,
                                head = line3.to
                            )
                        )
                    )
                }) { Text("Select line 3") }
                Button(onClick = {
                    session.selectAll()
                }) { Text("Select all") }
                Button(onClick = {
                    val doc = session.state.doc
                    val ranges = (1..minOf(3, doc.lines)).map { i ->
                        val line = doc.line(LineNumber(i))
                        EditorSelection.cursor(line.from)
                    }
                    session.dispatch(
                        TransactionSpec(
                            selection = SelectionSpec.EditorSelectionSpec(
                                EditorSelection.create(ranges)
                            )
                        )
                    )
                }) { Text("Multi-cursor (3 lines)") }
            }
            Text(
                text = selectionInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    ) {
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
