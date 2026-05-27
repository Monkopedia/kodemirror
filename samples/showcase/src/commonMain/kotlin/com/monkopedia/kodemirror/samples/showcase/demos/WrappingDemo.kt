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
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.Compartment
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.lineWrapping
import com.monkopedia.kodemirror.view.rememberEditorSession

@Composable
fun WrappingDemo() {
    var wrap by remember { mutableStateOf(true) }

    val wrapCompartment = remember { Compartment() }

    val session = rememberEditorSession(
        doc = SampleDocs.wrapping,
        extensions = basicSetup +
            wrapCompartment.of(if (wrap) lineWrapping else ExtensionList(emptyList()))
    )

    DemoScaffold(
        title = "Line Wrapping",
        description = "Toggle the lineWrapping extension. Default no-wrap mode scrolls long " +
            "lines horizontally (with a scrollbar and caret-follow); lineWrapping soft-wraps " +
            "them to fit the width instead.",
        controls = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = wrap,
                    onClick = {
                        wrap = true
                        session.dispatch(
                            TransactionSpec(
                                effects = listOf(
                                    wrapCompartment.reconfigure(lineWrapping)
                                )
                            )
                        )
                    },
                    label = { Text("Wrap") }
                )
                FilterChip(
                    selected = !wrap,
                    onClick = {
                        wrap = false
                        session.dispatch(
                            TransactionSpec(
                                effects = listOf(
                                    wrapCompartment.reconfigure(ExtensionList(emptyList()))
                                )
                            )
                        )
                    },
                    label = { Text("No wrap") }
                )
            }
        }
    ) {
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
