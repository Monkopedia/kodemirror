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
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.lang.html.html
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.rememberEditorSession

@Composable
fun MixedLanguageDemo() {
    DemoScaffold(
        title = "Mixed Languages",
        description = "HTML with embedded JavaScript and CSS, using nested language parsing."
    ) {
        val session = rememberEditorSession(
            doc = SampleDocs.html,
            extensions = basicSetup + html().extension
        )
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
