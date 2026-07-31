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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.EditorSessionImpl
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.lineNumbers
import kotlin.test.assertTrue

/**
 * Holder for the editor session created inside the Compose test, so test
 * code can access it for assertions.
 */
class SessionHolder {
    lateinit var session: EditorSession
}

/**
 * Run an integration test with a KodeMirror editor.
 *
 * Sets up a [KodeMirror] composable with the given document content and
 * extensions, waits for layout, and then runs [block] with access to
 * the [ComposeUiTest] scope and a [SessionHolder] for assertions.
 *
 * The multiplatform [runComposeUiTest] has no `width`/`height` parameters —
 * unlike the desktop-only harness it replaces, which sized the test surface in
 * raw pixels. The viewport is instead established by a [requiredSize] frame
 * around the editor, with [LocalDensity] pinned to `Density(1f, 1f)`.
 *
 * Pinning the density is what makes this portable: `requiredSize` takes dp, and
 * the ambient density differs per platform (desktop ~1.0, Android/iOS
 * device-dependent, wasm follows `devicePixelRatio`). Without the pin,
 * `800.dp` would be a different pixel count on every target and each
 * `Offset(...)` in the tests below would silently mean something different.
 * At `Density(1f, 1f)` dp == px, so the raw-pixel offsets carry over unchanged.
 * `fontScale = 1f` keeps accessibility text scaling from perturbing line heights.
 */
@OptIn(ExperimentalTestApi::class)
fun runEditorTest(
    doc: String = "",
    extensions: Extension? = null,
    withGutters: Boolean = false,
    width: Int = 800,
    height: Int = 600,
    block: ComposeUiTest.(SessionHolder) -> Unit
) = SessionHolder().let { holder ->
    runComposeUiTest {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1f)
            ) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    val allExtensions = buildList {
                        if (withGutters) add(lineNumbers)
                        extensions?.let { add(it) }
                    }
                    val ext = if (allExtensions.isEmpty()) null else ExtensionList(allExtensions)
                    val state = remember {
                        EditorState.create(
                            EditorStateConfig(
                                doc = doc.asDoc(),
                                extensions = ext
                            )
                        )
                    }
                    val session = remember(state) { EditorSession(state) }
                    holder.session = session
                    KodeMirror(session = session)
                }
            }
        }
        waitForIdle()
        block(holder)
    }
}

/**
 * Assert that the cursor (head of main selection) is on the expected
 * 1-based line number.
 */
fun SessionHolder.assertCursorOnLine(expectedLine: Int) {
    val state = session.state
    val cursorPos = state.selection.main.head
    val line = state.doc.lineAt(cursorPos)
    assertTrue(
        line.number.value == expectedLine,
        "Expected cursor on line $expectedLine but was on line ${line.number.value} " +
            "(head=${cursorPos.value})"
    )
}

/**
 * Assert that the main selection is non-empty (some text is selected).
 */
fun SessionHolder.assertSelectionNotEmpty() {
    val sel = session.state.selection.main
    assertTrue(!sel.empty, "Expected non-empty selection but got cursor at ${sel.head.value}")
}

/**
 * Assert the document content equals [expected].
 */
fun SessionHolder.assertDoc(expected: String) {
    val actual = session.state.doc.toString()
    assertTrue(actual == expected, "Expected doc:\n$expected\nActual doc:\n$actual")
}

/**
 * Assert the cursor is at the given document offset.
 */
fun SessionHolder.assertCursorAt(pos: Int) {
    val head = session.state.selection.main.head
    assertTrue(head == DocPos(pos), "Expected cursor at $pos but was at ${head.value}")
}

/** Index of the first item currently laid out in the editor's line list. */
fun SessionHolder.firstVisibleIndex(): Int = (session as EditorSessionImpl).lastFirstVisibleItem

/** Number of items currently laid out in the editor's line list. */
fun SessionHolder.visibleItemCount(): Int = (session as EditorSessionImpl).lastVisibleItemCount

/** Current horizontal scroll offset of the content area, in pixels. */
fun SessionHolder.horizontalScrollPx(): Int = (session as EditorSessionImpl).lastHorizontalScrollPx

/**
 * Laid-out width, in pixels, of the most recently positioned line whose content
 * box carries a line-decoration background highlight (e.g. the active line). In
 * no-wrap mode this should reach the viewport width even for short content (#85).
 */
fun SessionHolder.activeLineContentWidthPx(): Int =
    (session as EditorSessionImpl).lastActiveLineContentWidthPx

/** Whether the given column-item index is within the currently laid-out range. */
fun SessionHolder.isIndexVisible(index: Int): Boolean {
    val impl = session as EditorSessionImpl
    val first = impl.lastFirstVisibleItem
    val count = impl.lastVisibleItemCount
    return index in first until (first + count)
}

/** Wheel delta, in scroll units, sent by one step of [wheelScrollRightToEnd]. */
private const val WHEEL_SCROLL_STEP = 600f

/** Upper bound on the steps [wheelScrollRightToEnd] will take before giving up. */
private const val MAX_WHEEL_SCROLL_STEPS = 60

/**
 * Scroll the content horizontally with the mouse wheel, from [at], until it stops moving.
 *
 * One wheel event is not a portable way to reach a given scroll offset: the scroll-unit to
 * pixel factor is platform specific. The same 600-unit horizontal scroll moves roughly 3200px
 * on desktop JVM but only about 550px in a headless browser, so a delta chosen to travel "far
 * enough" on one target falls short on another and the difference shows up as a failed
 * assertion about a *click offset*, which reads like an editor bug and is not one. Repeating
 * until [horizontalScrollPx] stops changing saturates the scrollable range everywhere, which
 * is what the scroll regressions this exercises (#67) are actually about.
 *
 * Returns having made at least one scroll attempt, so a platform that ignores horizontal wheel
 * events altogether still fails the caller's assertion rather than being papered over here.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.wheelScrollRightToEnd(holder: SessionHolder, at: Offset) {
    var previous = Int.MIN_VALUE
    repeat(MAX_WHEEL_SCROLL_STEPS) {
        val current = holder.horizontalScrollPx()
        if (current == previous) return
        previous = current
        onNodeWithTag("KodeMirror").performMouseInput {
            moveTo(at)
            scroll(WHEEL_SCROLL_STEP, ScrollWheel.Horizontal)
        }
        waitForIdle()
    }
}
