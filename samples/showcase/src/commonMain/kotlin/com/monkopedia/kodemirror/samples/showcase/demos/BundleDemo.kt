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
import com.monkopedia.kodemirror.samples.showcase.showcaseSetup
import com.monkopedia.kodemirror.lang.yaml.yaml
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.editable
import com.monkopedia.kodemirror.view.rememberEditorSession

private val GRADLE_CONFIG = """
    // build.gradle.kts
    kotlin:
      sourceSets:
        commonMain:
          dependencies:
            - project(":view")
            - project(":basic-setup")
            - project(":lang-javascript")
            - project(":theme-one-dark")
            - compose.runtime
            - compose.foundation
            - compose.ui
            - compose.material3
        desktopMain:
          dependencies:
            - compose.desktop.currentOs
""".trimIndent()

@Composable
fun BundleDemo() {
    DemoScaffold(
        title = "Bundle Setup",
        description = "Gradle dependencies needed for a KodeMirror project."
    ) {
        val session = rememberEditorSession(
            doc = GRADLE_CONFIG,
            extensions = showcaseSetup + yaml().extension + editable.of(false)
        )
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
