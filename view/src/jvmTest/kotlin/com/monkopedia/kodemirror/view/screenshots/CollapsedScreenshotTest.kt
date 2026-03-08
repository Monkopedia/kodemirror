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
import com.monkopedia.kodemirror.language.foldState
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.ReplaceDecorationSpec
import com.monkopedia.kodemirror.view.WidgetType
import com.monkopedia.kodemirror.view.highlightActiveLine
import com.monkopedia.kodemirror.view.lineNumbers
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class CollapsedScreenshotTest {

    @Test
    fun capture() = runDesktopComposeUiTest(width = 800, height = 600) {
        setContent {
            val state = remember {
                EditorState.create(
                    EditorStateConfig(
                        doc = TestScenarios.SAMPLE_CODE.asDoc(),
                        extensions = ExtensionList(
                            listOf(
                                lineNumbers,
                                foldGutter(),
                                highlightActiveLine,
                                foldState.init { state ->
                                    // Fold the fibonacci function body.
                                    // Line 1: "function fibonacci(n) {"
                                    // Line 4: "}"
                                    val line1 = state.doc.line(1)
                                    val openBrace = line1.text.indexOf('{')
                                    val from = line1.from + openBrace + 1
                                    val line4 = state.doc.line(4)
                                    val to = line4.from
                                    val builder = RangeSetBuilder<Decoration>()
                                    builder.add(
                                        from,
                                        to,
                                        Decoration.replace(
                                            ReplaceDecorationSpec(
                                                widget = FoldPlaceholderWidget()
                                            )
                                        )
                                    )
                                    builder.finish()
                                },
                                TestScenarios.jsLanguageExtensions(light = false)
                            )
                        )
                    )
                )
            }
            val session = remember(state) { EditorSession(state) }
            KodeMirror(session = session)
        }
        onRoot().captureRoboImage("screenshots/compose/collapsed.png")
    }
}

private class FoldPlaceholderWidget : WidgetType() {
    @androidx.compose.runtime.Composable
    override fun Content() {
        androidx.compose.foundation.text.BasicText(
            text = "\u2026",
            style = com.monkopedia.kodemirror.view.LocalEditorTheme.current
                .contentTextStyle.copy(
                    color = androidx.compose.ui.graphics.Color.Gray
                )
        )
    }

    override fun equals(other: Any?): Boolean = other is FoldPlaceholderWidget

    override fun hashCode(): Int = this::class.hashCode()
}
