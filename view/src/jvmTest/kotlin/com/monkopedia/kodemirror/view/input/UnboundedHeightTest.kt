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
 *
 * Originally based on CodeMirror 6 by Marijn Haverbeke, licensed under MIT.
 * See NOTICE file for details.
 */
package com.monkopedia.kodemirror.view.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KodeMirror
import org.junit.Test

/**
 * Regression test for #33: placing [KodeMirror] under an UNBOUNDED vertical
 * constraint (a `Modifier.verticalScroll(...)` parent) must NOT collapse the
 * editor to zero height, and must NOT crash the inner `LazyColumn` (which
 * disallows an infinite `maxHeight`). Instead the editor grows to its content
 * height so the surrounding scroll container can scroll it.
 */
@OptIn(ExperimentalTestApi::class)
class UnboundedHeightTest {

    private val hundredLineDoc = (1..100).joinToString("\n") { "Line $it content here" }

    @Test
    fun unboundedParent_editorGrowsToContent_doesNotCollapseOrCrash() =
        runDesktopComposeUiTest(width = 800, height = 300) {
            val editorHeight = mutableStateOf(-1)
            setContent {
                val state = remember {
                    EditorState.create(
                        EditorStateConfig(doc = hundredLineDoc.asDoc())
                    )
                }
                val session = remember(state) { EditorSession(state) }
                // verticalScroll imposes an infinite maxHeight on the child.
                Box(Modifier.verticalScroll(rememberScrollState())) {
                    KodeMirror(
                        session = session,
                        modifier = Modifier.fillMaxWidth()
                            .onSizeChanged { editorHeight.value = it.height }
                    )
                }
            }
            waitForIdle()

            // Before the fix this was 0 (Column weight under infinite height) or
            // the LazyColumn threw on the infinite maxHeight. The editor must now
            // grow to its (finite) content height: well beyond the 300px surface
            // for a 100-line document.
            val height = editorHeight.value
            assert(height > 300) {
                "Expected editor to grow to its content height under an " +
                    "unbounded parent, but measured height was $height"
            }
        }
}
