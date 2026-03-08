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
package com.monkopedia.kodemirror.samples.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.lang.markdown.markdown
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.materialtheme.rememberMaterialEditorTheme
import com.monkopedia.kodemirror.themonedark.oneDark
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.onChange
import com.monkopedia.kodemirror.view.rememberEditorSession

private val SAMPLE_JS = """
    // Fibonacci sequence
    function fibonacci(n) {
        if (n <= 1) return n;
        return fibonacci(n - 1) + fibonacci(n - 2);
    }

    // Print first 10 numbers
    for (let i = 0; i < 10; i++) {
        console.log(`fib(${'$'}{i}) = ${'$'}{fibonacci(i)}`);
    }
""".trimIndent()

private val SAMPLE_MARKDOWN = """
    # KodeMirror Sample

    This is a **Kotlin Multiplatform** code editor built on
    [Compose](https://www.jetbrains.com/compose-multiplatform/).

    ## Features

    - Syntax highlighting
    - Line numbers
    - Code folding
    - Autocompletion
    - Search & replace

    ```kotlin
    fun main() {
        println("Hello from KodeMirror!")
    }
    ```
""".trimIndent()

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KodeMirror Sample Editor"
    ) {
        var selectedTab by remember { mutableStateOf(Tab.JAVASCRIPT) }
        val isDark = selectedTab == Tab.DARK

        MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TabBar(
                        selected = selectedTab,
                        onSelect = { selectedTab = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    when (selectedTab) {
                        Tab.JAVASCRIPT -> EditorPane(
                            doc = SAMPLE_JS,
                            extensions = basicSetup + javascript().extension
                        )
                        Tab.MARKDOWN -> EditorPane(
                            doc = SAMPLE_MARKDOWN,
                            extensions = basicSetup + markdown().extension
                        )
                        Tab.DARK -> EditorPane(
                            doc = SAMPLE_JS,
                            extensions = basicSetup + javascript().extension + oneDark
                        )
                        Tab.MATERIAL -> {
                            val materialTheme = rememberMaterialEditorTheme()
                            EditorPane(
                                doc = SAMPLE_JS,
                                extensions = basicSetup + javascript().extension +
                                    materialTheme
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class Tab(val label: String) {
    JAVASCRIPT("JavaScript"),
    MARKDOWN("Markdown"),
    DARK("Dark Theme"),
    MATERIAL("Material Theme")
}

@Composable
private fun TabBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.padding(8.dp)) {
        for (tab in Tab.entries) {
            FilterChip(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                label = { Text(tab.label) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun EditorPane(
    doc: String,
    extensions: Extension,
    modifier: Modifier = Modifier
) {
    val session = rememberEditorSession(
        doc = doc,
        extensions = extensions + onChange { _ ->
            // Handle text changes here
        }
    )
    KodeMirror(
        session = session,
        modifier = modifier.fillMaxSize()
    )
}
