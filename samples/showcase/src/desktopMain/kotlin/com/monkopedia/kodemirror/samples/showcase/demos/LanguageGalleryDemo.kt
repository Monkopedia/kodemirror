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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.lang.css.css
import com.monkopedia.kodemirror.lang.go.go
import com.monkopedia.kodemirror.lang.html.html
import com.monkopedia.kodemirror.lang.java.java
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.lang.json.json
import com.monkopedia.kodemirror.lang.markdown.markdown
import com.monkopedia.kodemirror.lang.python.python
import com.monkopedia.kodemirror.lang.rust.rust
import com.monkopedia.kodemirror.lang.yaml.yaml
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.rememberEditorSession

private data class LangEntry(
    val name: String,
    val extension: Extension,
    val sample: String
)

private val languages = listOf(
    LangEntry("JavaScript", javascript().extension, SampleDocs.javascript),
    LangEntry("Python", python().extension, SampleDocs.python),
    LangEntry("Rust", rust().extension, SampleDocs.rust),
    LangEntry("HTML", html().extension, SampleDocs.html),
    LangEntry("CSS", css().extension, SampleDocs.css),
    LangEntry("JSON", json().extension, SampleDocs.json),
    LangEntry("YAML", yaml().extension, SampleDocs.yaml),
    LangEntry("Markdown", markdown().extension, SampleDocs.markdown),
    LangEntry("Go", go().extension, SampleDocs.go),
    LangEntry("Java", java().extension, SampleDocs.java)
)

@Composable
fun LanguageGalleryDemo() {
    DemoScaffold(
        title = "Language Gallery",
        description = "Syntax highlighting for 10 built-in languages."
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(languages, key = { it.name }) { entry ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val session = rememberEditorSession(
                        doc = entry.sample,
                        extensions = basicSetup + entry.extension
                    )
                    KodeMirror(
                        session = session,
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                }
            }
        }
    }
}
