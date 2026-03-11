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
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.lang.python.python
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.Compartment
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.themedracula.dracula
import com.monkopedia.kodemirror.themegithublight.gitHubLight
import com.monkopedia.kodemirror.themonedark.oneDark
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.rememberEditorSession

private enum class LangChoice(val label: String, val ext: Extension, val doc: String) {
    JS("JavaScript", javascript().extension, SampleDocs.javascript),
    PY("Python", python().extension, SampleDocs.python)
}

private enum class ThemeChoice(val label: String, val ext: Extension) {
    NONE("Default", ExtensionList(emptyList())),
    ONE_DARK("One Dark", oneDark),
    GITHUB("GitHub Light", gitHubLight),
    DRACULA("Dracula", dracula)
}

@Composable
fun ConfigDemo() {
    var lang by remember { mutableStateOf(LangChoice.JS) }
    var theme by remember { mutableStateOf(ThemeChoice.NONE) }

    val langCompartment = remember { Compartment() }
    val themeCompartment = remember { Compartment() }

    val session = rememberEditorSession(
        doc = lang.doc,
        extensions = showcaseSetup +
            langCompartment.of(lang.ext) +
            themeCompartment.of(theme.ext)
    )

    DemoScaffold(
        title = "Configuration",
        description = "Use Compartments to dynamically switch language and theme.",
        controls = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LangChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = lang == choice,
                        onClick = {
                            lang = choice
                            session.dispatch(
                                TransactionSpec(
                                    effects = listOf(
                                        langCompartment.reconfigure(choice.ext)
                                    )
                                )
                            )
                        },
                        label = { Text(choice.label) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = theme == choice,
                        onClick = {
                            theme = choice
                            session.dispatch(
                                TransactionSpec(
                                    effects = listOf(
                                        themeCompartment.reconfigure(choice.ext)
                                    )
                                )
                            )
                        },
                        label = { Text(choice.label) }
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
