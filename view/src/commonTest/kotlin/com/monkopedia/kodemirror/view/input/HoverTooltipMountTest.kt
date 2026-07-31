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

import androidx.compose.ui.test.ExperimentalTestApi
import com.monkopedia.kodemirror.view.hoverTooltip
import kotlin.test.Test

/**
 * Regression test for #92: mounting an editor that uses a hover tooltip (e.g.
 * via the `linter(...)` extension, which installs a [hoverTooltip] under the
 * hood) must not crash during composition.
 *
 * [HoverTooltipPlugin][com.monkopedia.kodemirror.view] reads
 * `session.coroutineScope` eagerly in its initializer. The plugin host is built
 * (and thus the plugin constructed) before the session's coroutine scope was
 * attached, so the eager read threw
 * `IllegalStateException: EditorSession is not attached to a KodeMirror
 * composable`. The fix attaches the scope before plugin construction.
 *
 * If composition throws, [runEditorTest] never reaches [block] and the test
 * fails. Reaching the assertion at all confirms the editor mounted cleanly.
 */
class HoverTooltipMountTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun editorWithHoverTooltipMountsWithoutCrashing() {
        runEditorTest(
            doc = "hello world",
            // Minimal hover source that never produces a tooltip; the trigger
            // for #92 is merely having the plugin installed, not showing it.
            extensions = hoverTooltip { _, _ -> null }
        ) { holder ->
            // Reaching this block means composition did not throw. Confirm the
            // editor actually mounted with the expected document.
            holder.assertDoc("hello world")
        }
    }
}
