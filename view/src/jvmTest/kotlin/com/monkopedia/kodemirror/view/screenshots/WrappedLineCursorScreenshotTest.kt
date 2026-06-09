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
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.editorTheme
import com.monkopedia.kodemirror.view.highlightActiveLine
import com.monkopedia.kodemirror.view.lightEditorTheme
import com.monkopedia.kodemirror.view.lineNumbers
import com.monkopedia.kodemirror.view.lineWrapping
import com.monkopedia.kodemirror.view.screenshots.TestScenarios.captureScreenshot
import org.junit.Test

/**
 * Captures a wrapped line (narrow viewport + long single line + [lineWrapping])
 * with the caret placed near the end of the line, i.e. on a later visual row,
 * and gutters enabled.
 *
 * Regression coverage for #75: the caret must span only its actual visual row
 * (not the full multi-row line height) and the gutter line number must sit
 * beside the first visual row (top-aligned), not centered across all rows.
 */
@OptIn(ExperimentalTestApi::class)
class WrappedLineCursorScreenshotTest {

    @Test
    fun capture() = runDesktopComposeUiTest(width = 360, height = 320) {
        setContent {
            val state = remember {
                // A long single line that wraps into several visual rows in a
                // narrow viewport, surrounded by short lines so the gutter shows
                // multiple line numbers next to the tall wrapped line.
                val doc = buildString {
                    append("const before = 1;\n")
                    append(
                        "const sentence = \"the quick brown fox jumps over the " +
                            "lazy dog and then keeps running across the wide field\";\n"
                    )
                    append("const after = 2;\n")
                }
                // Place the caret near the end of the long wrapped line so it
                // lands on a later visual row.
                val caret = doc.indexOf("wide field")
                EditorState.create(
                    EditorStateConfig(
                        doc = doc.asDoc(),
                        selection = SelectionSpec.EditorSelectionSpec(
                            EditorSelection.create(
                                listOf(EditorSelection.cursor(DocPos(caret)))
                            )
                        ),
                        extensions = ExtensionList(
                            listOf(
                                lineWrapping,
                                lineNumbers,
                                foldGutter(),
                                highlightActiveLine,
                                editorTheme.of(lightEditorTheme),
                                TestScenarios.jsLanguageExtensions(light = true)
                            )
                        )
                    )
                )
            }
            val session = remember(state) { EditorSession(state) }
            KodeMirror(session = session)
        }
        onRoot().captureScreenshot("screenshots/compose/wrapped-line-cursor.png")
    }
}
