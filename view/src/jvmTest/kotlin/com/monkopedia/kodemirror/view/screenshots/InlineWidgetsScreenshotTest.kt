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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monkopedia.kodemirror.language.foldGutter
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.WidgetDecorationSpec
import com.monkopedia.kodemirror.view.WidgetType
import com.monkopedia.kodemirror.view.decorations
import com.monkopedia.kodemirror.view.highlightActiveLine
import com.monkopedia.kodemirror.view.lineNumbers
import com.monkopedia.kodemirror.view.screenshots.TestScenarios.captureScreenshot
import org.junit.Test

/**
 * Inline widgets at their document positions (#397). The CM6 side is
 * `gap-analysis/fixtures/cm6-scenarios/inline-widgets.html`: a badge at the end
 * of line 1, one before `return` on line 2 (`side = -1`) and one after the `(`
 * on line 3 (`side = 1`).
 */
@OptIn(ExperimentalTestApi::class)
class InlineWidgetsScreenshotTest {

    private class Badge(val label: String) : WidgetType() {
        @Composable
        override fun Content() {
            BasicText(
                text = label,
                style = TextStyle(color = Color.White, fontSize = 12.sp),
                modifier = Modifier.background(Color(0xFF3366CC)).padding(horizontal = 3.dp)
            )
        }
    }

    @Test
    fun capture() = runDesktopComposeUiTest(width = 800, height = 600) {
        setContent {
            val state = remember {
                val text = TestScenarios.SAMPLE_CODE
                val builder = RangeSetBuilder<Decoration>()
                fun badge(pos: Int, side: Int, label: String) = builder.add(
                    DocPos(pos),
                    DocPos(pos),
                    Decoration.widget(WidgetDecorationSpec(Badge(label), side = side))
                )
                badge(text.indexOf('\n'), 1, "end")
                badge(text.indexOf("return n"), -1, "before")
                badge(text.indexOf("fibonacci(n - 1)") + "fibonacci(".length, 1, "after")
                EditorState.create(
                    EditorStateConfig(
                        doc = text.asDoc(),
                        extensions = ExtensionList(
                            listOf(
                                lineNumbers,
                                foldGutter(),
                                highlightActiveLine,
                                TestScenarios.jsLanguageExtensions(light = false),
                                decorations.of(builder.finish())
                            )
                        )
                    )
                )
            }
            val session = remember(state) { EditorSession(state) }
            KodeMirror(session = session)
        }
        onRoot().captureScreenshot("screenshots/compose/inline-widgets.png")
    }
}
