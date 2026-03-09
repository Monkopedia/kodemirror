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
package com.monkopedia.kodemirror.view.screenshots

import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.monkopedia.kodemirror.language.foldGutter
import com.monkopedia.kodemirror.search.highlightSelectionMatches
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.highlightActiveLine
import com.monkopedia.kodemirror.view.highlightActiveLineGutter
import com.monkopedia.kodemirror.view.lineNumbers
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ContrastSelectionScreenshotTest {

    @Test
    fun capture() = runDesktopComposeUiTest(width = 800, height = 600) {
        setContent {
            val state = remember {
                val doc = TestScenarios.SAMPLE_CODE
                val anchor = doc.indexOf("fibonacci")
                val head = anchor + "fibonacci".length
                EditorState.create(
                    EditorStateConfig(
                        doc = doc.asDoc(),
                        selection = SelectionSpec.EditorSelectionSpec(
                            EditorSelection.create(
                                listOf(EditorSelection.range(DocPos(anchor), DocPos(head)))
                            )
                        ),
                        extensions = ExtensionList(
                            listOf(
                                lineNumbers,
                                foldGutter(),
                                highlightActiveLine,
                                highlightActiveLineGutter,
                                highlightSelectionMatches(),
                                TestScenarios.jsExtensionsContrast()
                            )
                        )
                    )
                )
            }
            val session = remember(state) { EditorSession(state) }
            KodeMirror(session = session)
        }
        onRoot().captureRoboImage("screenshots/compose/contrast-selection.png")
    }
}
