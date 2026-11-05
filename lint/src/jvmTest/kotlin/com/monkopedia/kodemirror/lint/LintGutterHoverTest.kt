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
package com.monkopedia.kodemirror.lint

import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.hasHoverTooltips
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [LintGutterConfig.hoverTime] delays the lint gutter's tooltip (#382). Upstream's
 * `gutterMarkerMouseOver` shows it from `setTimeout(hovered, hoverTime)`; that path needs real
 * layout, so the expectation here comes from upstream's source rather than a node run.
 */
@OptIn(ExperimentalTestApi::class)
class LintGutterHoverTest {

    @Test
    fun gutterTooltipWaitsForHoverTime() = runDesktopComposeUiTest {
        lateinit var session: EditorSession
        setContent {
            session = remember {
                EditorSession(
                    EditorState.create(
                        EditorStateConfig(
                            doc = "hello world".asDoc(),
                            extensions = ExtensionList(
                                listOf(
                                    lintDisplay(),
                                    lintGutter(LintGutterConfig(hoverTime = 1000))
                                )
                            )
                        )
                    )
                )
            }
            KodeMirror(session = session)
        }
        waitForIdle()
        runOnIdle {
            setDiagnostics(session, listOf(Diagnostic(DocPos(0), DocPos(2), Severity.ERROR, "bad")))
        }
        waitForIdle()
        mainClock.autoAdvance = false
        // Past the end of the first line: outside the diagnostic, so only the gutter's
        // line-wide tooltip can answer.
        onRoot().performMouseInput { moveTo(Offset(width - 5f, 5f)) }
        mainClock.advanceTimeBy(500)
        assertFalse(hasHoverTooltips(session), "tooltip shown before hoverTime elapsed")
        mainClock.advanceTimeBy(600)
        assertTrue(hasHoverTooltips(session), "tooltip not shown after hoverTime elapsed")
    }
}
