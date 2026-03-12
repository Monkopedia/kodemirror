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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.EditorTheme
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.editorTheme
import com.monkopedia.kodemirror.view.rememberEditorSession

private val customTheme = EditorTheme(
    background = Color(0xFF1A1B26),
    foreground = Color(0xFFA9B1D6),
    cursor = Color(0xFFC0CAF5),
    selection = Color(0xFF364A82),
    activeLineBackground = Color(0xFF1E2030),
    gutterBackground = Color(0xFF1A1B26),
    gutterForeground = Color(0xFF3B4261)
)

@Composable
fun StylingDemo() {
    DemoScaffold(
        title = "Custom Styling",
        description = "Create a custom EditorTheme with Tokyo Night-inspired colors."
    ) {
        val session = rememberEditorSession(
            doc = SampleDocs.javascript,
            extensions = basicSetup + javascript().extension +
                editorTheme.of(customTheme)
        )
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
