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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.monkopedia.kodemirror.autocomplete.Completion
import com.monkopedia.kodemirror.autocomplete.CompletionConfig
import com.monkopedia.kodemirror.autocomplete.CompletionResult
import com.monkopedia.kodemirror.autocomplete.CompletionSource
import com.monkopedia.kodemirror.autocomplete.autocompletion
import com.monkopedia.kodemirror.autocomplete.startCompletion
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.lineNumbers
import com.monkopedia.kodemirror.view.screenshots.TestScenarios.captureScreenshot
import org.junit.Test

/**
 * Screenshot baseline for the completion popup, locking the restored
 * matched-prefix **bold** highlighting in the option labels (#111).
 *
 * The popup label was switched from an [androidx.compose.ui.text.AnnotatedString]
 * to a plain [String] while fixing #109 (it appeared not to paint on wasmJs),
 * which dropped the bold prefix styling. The render is restored here; this
 * baseline captures the popup open over the typed prefix `co`, so the matched
 * `co` of each label (`console`, `console.log`, `const`, …) renders bold while
 * the rest of the label is regular weight. A regression that drops the styling
 * (or blanks the label) changes these pixels and trips `verifyRoborazziJvm`.
 *
 * Uses [TestScenarios.pinnedFont] so the popup renders in the bundled JetBrains
 * Mono (the popup inherits the editor content font), keeping the baseline
 * reproducible across machines.
 */
@OptIn(ExperimentalTestApi::class)
class CompletionPopupScreenshotTest {

    private val keywords = listOf(
        Completion(label = "console", type = "variable", detail = "Console API"),
        Completion(label = "console.log", type = "function", detail = "Log to console"),
        Completion(label = "const", type = "keyword", detail = "Constant binding"),
        Completion(label = "constructor", type = "method"),
        Completion(label = "continue", type = "keyword")
    )

    private val source: CompletionSource = { ctx ->
        val word = ctx.matchBefore(Regex("[\\w.]+"))
        if (word != null || ctx.explicit) {
            CompletionResult(
                from = word?.from ?: ctx.pos,
                options = keywords,
                validFor = Regex("[\\w.]*")
            )
        } else {
            null
        }
    }

    @Test
    fun capture() = runDesktopComposeUiTest(width = 420, height = 320) {
        lateinit var session: EditorSession
        setContent {
            val state = remember {
                EditorState.create(
                    EditorStateConfig(
                        doc = "co".asDoc(),
                        // Cursor after the typed prefix so the source matches "co"
                        // and the matched prefix is highlighted in every label.
                        selection = SelectionSpec.CursorSpec(DocPos(2)),
                        extensions = ExtensionList(
                            listOf(
                                lineNumbers,
                                TestScenarios.pinnedFont,
                                autocompletion(
                                    CompletionConfig(override = listOf(source))
                                )
                            )
                        )
                    )
                )
            }
            session = remember(state) { EditorSession(state) }
            KodeMirror(session = session)
        }
        waitForIdle()
        // Explicitly open the popup (Ctrl-Space equivalent) and let it lay out.
        startCompletion(session)
        waitForIdle()
        // The popup is a separate Compose root (a Popup), so capture it directly
        // by its testTag rather than onRoot() (which is ambiguous with >1 root).
        onNodeWithTag("completionPopup").captureScreenshot("screenshots/compose/completion-popup.png")
    }
}
