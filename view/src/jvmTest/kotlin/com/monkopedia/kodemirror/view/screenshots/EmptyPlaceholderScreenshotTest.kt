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

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.view.EditorView
import com.monkopedia.kodemirror.view.placeholder
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class EmptyPlaceholderScreenshotTest {

    @Test
    fun capture() = runDesktopComposeUiTest(width = 800, height = 600) {
        setContent {
            val state = remember {
                EditorState.create(
                    EditorStateConfig(
                        extensions = placeholder {
                            BasicText(
                                text = TestScenarios.PLACEHOLDER_TEXT,
                                style = TextStyle(color = Color.Gray)
                            )
                        }
                    )
                )
            }
            EditorView(state = state, onUpdate = {})
        }
        onRoot().captureRoboImage("screenshots/compose/empty-placeholder.png")
    }
}
