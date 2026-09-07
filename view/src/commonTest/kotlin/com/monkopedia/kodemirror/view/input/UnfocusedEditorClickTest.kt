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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.commands.standardKeymap
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.EditorSessionImpl
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.keymapOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The **first** press on an editor that does not already hold focus.
 *
 * # Why this case, and not the one [PointerFocusRetentionTest] covers
 *
 * #259 is repaired by capturing the editor's focus for the length of a press,
 * and the capture is taken on the `Final` pointer pass. That pass choice is the
 * load-bearing part of the fix and nothing else in the repository pinned it:
 * changing it to `Main` or to `Initial` left the whole Android instrumented
 * suite green (#344).
 *
 * It left the suite green because every other focus test starts from an editor
 * that is *already* focused — the editor requests focus once at composition —
 * and a capture taken too early still finds a focus to capture in that state.
 * The ordering only matters when there is nothing to capture yet:
 *
 *  * the editor's tap gesture calls `focusRequester.requestFocus()` on the
 *    **Main** pass of the down;
 *  * `AndroidComposeView.dispatchTouchEvent` applies
 *    `AutoClearFocusBehavior.CursorBased` — the platform default — *after* all
 *    of that, clearing focus because the press lands outside the 1-dp hidden
 *    input that holds it;
 *  * a captured focus target refuses that (non-forced) clear, so the capture is
 *    the veto — but only if it was taken after the request.
 *
 * `Final` runs after every Main-pass handler in the tree, so the capture
 * succeeds. On `Main` the capture modifier is inner to the gesture modifier and
 * therefore sees the down *first*, before there is any focus; on `Initial` it
 * runs earlier still. Either way `captureFocus()` returns `false`, nothing
 * vetoes the auto-clear, and the focus the gesture has just requested is blanked
 * — which is #259 again, on the first click.
 *
 * # What is asserted
 *
 * Concrete state on both sides of the press: focus is false before it and true
 * after it, and the key that follows lands the cursor at an exact offset. The
 * press at `x = 8` is two pixels into the content's 6-dp start padding, so it
 * resolves to the first character of line 1 on any font, and `End` from there
 * must reach **11**, the end of `"Hello world"`. A dropped key leaves the cursor
 * where the press put it, at 0, so only an exact 11 says it arrived.
 *
 * The auto-clear these guard is Android's, and Android is where they discriminate
 * — `view/build.gradle.kts` keeps `...view.input.*` out of every Android local
 * unit test, so on that target they run instrumented only. On the other targets
 * they are ordinary passing coverage of the same user-visible behaviour.
 *
 * # A note on the shape of the test bodies
 *
 * Every test here is a single expression whose value is the `runComposeUiTest`
 * call, and it has to stay that way. On wasmJs that value is what the framework
 * waits on; a body that runs the call as a statement and returns `Unit` finishes
 * before the composition does, and the browser suite then reports the test as
 * **passed without having evaluated a single assertion** — verified here by
 * asserting a deliberately wrong cursor offset and watching it pass.
 */
@OptIn(ExperimentalTestApi::class)
class UnfocusedEditorClickTest {

    private val doc = "Hello world\nSecond line"

    /** A point inside line 1 of the editor, ahead of its first character. */
    private val pressOnLine1 = Offset(8f, 15f)

    private val SessionHolder.hasFocus: Boolean
        get() = (session as EditorSessionImpl).hasFocus

    private val SessionHolder.head: Int
        get() = session.state.selection.main.head.value

    /** One editor, sized and with the density pinned the way [runEditorTest] does. */
    @Composable
    private fun Editor(holder: SessionHolder, height: Int) {
        Box(Modifier.requiredSize(800.dp, height.dp)) {
            val state = remember {
                EditorState.create(
                    EditorStateConfig(
                        doc = doc.asDoc(),
                        extensions = keymapOf(standardKeymap)
                    )
                )
            }
            val session = remember(state) { EditorSession(state) }
            holder.session = session
            KodeMirror(session = session)
        }
    }

    /**
     * Compose one editor next to a plain focusable box and hand focus to the
     * box, so the editor starts [block] **unfocused**.
     */
    private fun runUnfocusedEditorTest(block: ComposeUiTest.(SessionHolder) -> Unit) =
        SessionHolder().let { holder ->
            val elsewhere = FocusRequester()
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = 1f, fontScale = 1f)
                    ) {
                        Column {
                            Editor(holder, height = 400)
                            Box(
                                Modifier.requiredSize(80.dp, 40.dp)
                                    .testTag("elsewhere")
                                    .focusRequester(elsewhere)
                                    .focusable()
                            )
                        }
                    }
                }
                waitForIdle()
                // The editor focuses itself once at composition; move that focus
                // away so the click under test is the first one it ever sees.
                runOnIdle { elsewhere.requestFocus() }
                waitForIdle()
                assertFalse(
                    holder.hasFocus,
                    "The test setup is wrong, not the editor: focus was moved to another " +
                        "focusable and the editor still reports hasFocus=true, so the click " +
                        "below is not a first click on an unfocused editor and would prove " +
                        "nothing about when the focus capture is taken (#344)."
                )
                block(holder)
            }
        }

    /**
     * The first mouse press on an unfocused editor must focus it and keep that
     * focus, so the key after the press is delivered.
     */
    @Test
    fun firstMouseClickOnUnfocusedEditor_takesFocusAndDeliversTheKeyAfterIt() =
        runUnfocusedEditorTest { holder ->
            onNodeWithTag("KodeMirror").performMouseInput { click(pressOnLine1) }
            waitForIdle()
            assertTrue(
                holder.hasFocus,
                "editor lost focus on the first mouse click while unfocused: the press " +
                    "placed the cursor at ${holder.head} and then hasFocus=false. The " +
                    "gesture requests focus on the Main pass and Compose's cursor " +
                    "auto-clear runs after it, so the focus capture that vetoes the " +
                    "clear has to be taken on a later pass than the request (#344/#259)."
            )

            onNodeWithTag("KodeMirror_input").performKeyInput {
                keyDown(Key.MoveEnd)
                keyUp(Key.MoveEnd)
            }
            waitForIdle()
            assertTrue(
                holder.head == 11,
                "Expected End to reach 11, the end of \"Hello world\", after the first " +
                    "click on an unfocused editor, but the cursor is at ${holder.head}. " +
                    "At 0 the key was never delivered — the click placed the cursor " +
                    "there and the focus was taken away before the key arrived (#344)."
            )
        }

    /**
     * Two editors above one another: clicking one focuses it and unfocuses the
     * other, in both directions.
     *
     * Only one of the two can hold the composition-time focus, so one of these
     * clicks is always a first click on an unfocused editor. The pair is also
     * the only coverage of focus moving *between* editors, which is what a
     * capture that is taken and never released would break.
     */
    @Test
    fun clickEditorAThenEditorB_focusMovesBetweenThem() =
        Pair(SessionHolder(), SessionHolder()).let { (a, b) ->
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = 1f, fontScale = 1f)
                    ) {
                        Column {
                            Editor(a, height = 200)
                            Editor(b, height = 200)
                        }
                    }
                }
                waitForIdle()

                fun clickAndAssert(
                    index: Int,
                    name: String,
                    focused: SessionHolder,
                    blurred: SessionHolder
                ) {
                    onAllNodesWithTag("KodeMirror")[index].performMouseInput {
                        click(pressOnLine1)
                    }
                    waitForIdle()
                    assertTrue(
                        focused.hasFocus,
                        "editor $name did not hold focus after a mouse click on it: the " +
                            "click placed its cursor at ${focused.head} and then " +
                            "hasFocus=false. A click on an editor that was not already " +
                            "focused must leave it focused (#344/#259)."
                    )
                    assertFalse(
                        blurred.hasFocus,
                        "both editors report focus after the click on $name. Focus has to " +
                            "move to the clicked editor, so a capture taken across a press " +
                            "must be released when the press ends (#344)."
                    )
                    onAllNodesWithTag("KodeMirror_input")[index].performKeyInput {
                        keyDown(Key.MoveEnd)
                        keyUp(Key.MoveEnd)
                    }
                    waitForIdle()
                    assertTrue(
                        focused.head == 11,
                        "Expected End to reach 11 in editor $name after clicking it, but " +
                            "its cursor is at ${focused.head}. At 0 the key was never " +
                            "delivered (#344)."
                    )
                }

                clickAndAssert(index = 0, name = "A", focused = a, blurred = b)
                clickAndAssert(index = 1, name = "B", focused = b, blurred = a)
            }
        }
}
