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
package com.monkopedia.kodemirror.samples.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.lang.markdown.markdown
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.rememberEditorSession

private val SAMPLE_DOC = """
    # KodeMirror on Android

    This is a **Kotlin Multiplatform** code editor built on
    [Compose](https://www.jetbrains.com/compose-multiplatform/),
    running natively on Android.

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleApp()
        }
    }
}

@Composable
private fun SampleApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // basicSetup must be combined with a language extension, otherwise the
            // editor crashes at runtime. Here we use the Markdown language.
            val session = rememberEditorSession(
                doc = SAMPLE_DOC,
                extensions = basicSetup + markdown().extension
            )
            KodeMirror(
                session = session,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
