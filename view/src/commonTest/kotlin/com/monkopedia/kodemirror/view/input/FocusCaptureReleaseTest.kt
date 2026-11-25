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

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.EditorSessionImpl
import com.monkopedia.kodemirror.view.KodeMirror
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A point inside line 1 of the editor. */
internal val capturePressPoint = Offset(8f, 15f)

/** An editor above a plain focusable box, and the state the capture tests read. */
internal class FocusCaptureFixture {
    val holder = SessionHolder()
    val elsewhere = FocusRequester()
    var elsewhereFocused = false
    var showEditor by mutableStateOf(true)

    val editorFocused: Boolean
        get() = (holder.session as EditorSessionImpl).hasFocus
}

/** An editor above a plain focusable box; the editor holds focus when [block] starts. */
@OptIn(ExperimentalTestApi::class)
internal fun runFocusCaptureTest(block: ComposeUiTest.(FocusCaptureFixture) -> Unit) =
    FocusCaptureFixture().let { f ->
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1f, fontScale = 1f)
                ) {
                    Column {
                        if (f.showEditor) {
                            Box(Modifier.requiredSize(800.dp, 300.dp)) {
                                val session = remember {
                                    EditorSession(
                                        EditorState.create(
                                            EditorStateConfig(doc = "Hello world".asDoc())
                                        )
                                    )
                                }
                                f.holder.session = session
                                KodeMirror(session = session)
                            }
                        }
                        Box(
                            Modifier.requiredSize(80.dp, 40.dp)
                                .testTag("elsewhere")
                                .onFocusChanged { f.elsewhereFocused = it.isFocused }
                                .focusRequester(f.elsewhere)
                                .focusable()
                        )
                    }
                }
            }
            waitForIdle()
            assertTrue(f.editorFocused, "the editor focuses itself at composition")
            block(f)
        }
    }

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.requestFocusElsewhere(f: FocusCaptureFixture) {
    runOnIdle { f.elsewhere.requestFocus() }
    waitForIdle()
}

/** Another focusable asks for focus and must be refused: the press-long capture holds. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.assertCaptureHeld(f: FocusCaptureFixture, moment: String) {
    requestFocusElsewhere(f)
    assertTrue(
        f.editorFocused && !f.elsewhereFocused,
        "$moment, another focusable took focus from the editor (editor=${f.editorFocused}, " +
            "elsewhere=${f.elsewhereFocused}). The editor captures focus for the length of " +
            "a press, and a captured target refuses a non-forced request (#259/#359)."
    )
}

/** Another focusable asks for focus and must get it: the capture has been let go. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.assertCaptureReleased(f: FocusCaptureFixture, moment: String) {
    requestFocusElsewhere(f)
    assertTrue(
        f.elsewhereFocused,
        "$moment, another focusable could not take focus (editor=${f.editorFocused}). " +
            "The editor's press-long focus capture leaked, and nothing else on screen can " +
            "take focus while it is held (#359)."
    )
}

/**
 * The editor captures its focus for the length of a press (#259) and must let it go however
 * the press ends (#359).
 *
 * [UnfocusedEditorClickTest] pins the pass the capture is taken on and, through focus moving
 * between two editors, the release on an ordinary lift. These cover the rest of the capture
 * loop in `KodeMirror.kt`:
 *
 *  * the capture actually **holds** while the press is down, which is the whole point of it:
 *    a capture freed as soon as it is taken still lets focus move between editors afterwards;
 *  * a press that ends in a **cancel** rather than a lift releases it (Android only, in
 *    `FocusCaptureCancelTest`: the skiko test harness cannot inject a touch cancel at all);
 *  * with **several pointers** down it is held until the last one lifts, not the first;
 *  * an editor **removed mid-press** leaves nothing captured.
 *
 * Each is checked the same way: a plain focusable outside the editor asks for focus. A captured
 * focus target refuses that request, so the editor keeping focus means the capture is held and
 * the outside box getting it means the capture is gone. A leaked capture is not cosmetic:
 * nothing else on screen could take focus until the editor was disposed.
 *
 * Every test body is a single expression whose value is the `runComposeUiTest` call. On wasmJs
 * that value is what the framework waits on, and a body that returns `Unit` passes without
 * evaluating an assertion (see [UnfocusedEditorClickTest]).
 */
@OptIn(ExperimentalTestApi::class)
class FocusCaptureReleaseTest {

    @Test
    fun whilePressed_captureBlocksOtherFocus() = runFocusCaptureTest { f ->
        onNodeWithTag("KodeMirror").performMouseInput {
            moveTo(capturePressPoint)
            press()
        }
        waitForIdle()
        assertCaptureHeld(f, "While a mouse press on the editor was held")
        onNodeWithTag("KodeMirror").performMouseInput { release() }
        waitForIdle()
        assertCaptureReleased(f, "After the press was released")
    }

    @Test
    fun multiTouch_capturesUntilTheLastPointerLifts() = runFocusCaptureTest { f ->
        onNodeWithTag("KodeMirror").performTouchInput {
            down(0, capturePressPoint)
            down(1, capturePressPoint + Offset(200f, 0f))
        }
        waitForIdle()
        onNodeWithTag("KodeMirror").performTouchInput { up(1) }
        waitForIdle()
        assertCaptureHeld(f, "With one of two fingers lifted and the other still down")
        onNodeWithTag("KodeMirror").performTouchInput { up(0) }
        waitForIdle()
        assertCaptureReleased(f, "After the last finger lifted")
    }

    @Test
    fun editorRemovedMidPress_leavesNothingCaptured() = runFocusCaptureTest { f ->
        onNodeWithTag("KodeMirror").performTouchInput { down(capturePressPoint) }
        waitForIdle()
        assertCaptureHeld(f, "While a touch on the editor was held")
        f.showEditor = false
        waitForIdle()
        onRoot().performTouchInput { up() }
        waitForIdle()
        assertCaptureReleased(f, "After the editor left the composition mid-press")
        assertFalse(f.editorFocused)
    }
}
