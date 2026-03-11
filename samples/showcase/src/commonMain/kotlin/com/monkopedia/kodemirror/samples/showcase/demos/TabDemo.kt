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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.samples.showcase.showcaseSetup
import com.monkopedia.kodemirror.commands.indentWithTab
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.Compartment
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.insertAt
import com.monkopedia.kodemirror.view.keymapOf
import com.monkopedia.kodemirror.view.rememberEditorSession

private enum class TabMode(val label: String) {
    INDENT("indentWithTab"),
    INSERT("Insert literal tab")
}

@Composable
fun TabDemo() {
    var mode by remember { mutableStateOf(TabMode.INDENT) }
    val tabCompartment = remember { Compartment() }

    fun tabExtension(m: TabMode): Extension = when (m) {
        TabMode.INDENT -> keymapOf(indentWithTab)
        TabMode.INSERT -> keymapOf(
            KeyBinding(
                key = "Tab",
                run = { session ->
                    session.insertAt(session.state.selection.main.head, "\t")
                    true
                }
            )
        )
    }

    val session = rememberEditorSession(
        doc = SampleDocs.javascript,
        extensions = showcaseSetup + javascript().extension +
            tabCompartment.of(tabExtension(mode))
    )

    DemoScaffold(
        title = "Tab Handling",
        description = "Switch between indentWithTab (indent/dedent) and literal tab insertion.",
        controls = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TabMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = {
                            mode = m
                            session.dispatch(
                                TransactionSpec(
                                    effects = listOf(
                                        tabCompartment.reconfigure(tabExtension(m))
                                    )
                                )
                            )
                        },
                        label = { Text(m.label) }
                    )
                }
            }
        }
    ) {
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
