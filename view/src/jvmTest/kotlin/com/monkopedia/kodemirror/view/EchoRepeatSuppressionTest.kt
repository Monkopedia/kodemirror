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
package com.monkopedia.kodemirror.view

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import com.monkopedia.kodemirror.commands.standardKeymap
import com.monkopedia.kodemirror.view.input.assertDoc
import com.monkopedia.kodemirror.view.input.runEditorTest
import kotlin.test.Test

/**
 * One keystroke can produce more than one `onValueChange` echo on wasmJs — the
 * browser input event, plus a re-fire when a completion popup recomposes — and
 * every one of them has to be dropped, or the trigger character is inserted
 * twice and the spurious document change closes the just-opened popup (#109).
 * `pendingEcho` therefore stays armed while matching echoes keep arriving,
 * rather than disarming on the first (#294).
 *
 * This lives in `jvmTest` rather than beside the other input tests in
 * `commonTest` because it cannot be written portably: on wasmJs
 * `keyEventLayoutKey` reads the browser's real keydown, which a synthetic
 * `performKeyInput` never fires, so no key press in the Compose harness enters
 * a character there and the suppression it is meant to exercise is never armed
 * at all. The same test in `commonTest` would assert `x` on JVM/Android/native
 * and `xx` on wasmJs. The behaviour under test is shared `commonMain` code, so
 * running it on the one target that can drive it is coverage of the real path,
 * not of a JVM-specific one.
 */
@OptIn(ExperimentalTestApi::class)
class EchoRepeatSuppressionTest {

    private val keymapExt = keymapOf(standardKeymap)

    @Test
    fun repeatedEchoesOfOneKeystroke_areAllDropped() = runEditorTest(
        doc = "",
        extensions = keymapExt
    ) { holder ->
        // No click: a pointer press takes focus off the hidden field on Android
        // (#259), and the editor already auto-focuses it at composition.
        onNodeWithTag("KodeMirror_input").performKeyInput {
            keyDown(Key.X)
            keyUp(Key.X)
        }
        waitForIdle()
        holder.assertDoc("x")

        repeat(2) {
            onNodeWithTag("KodeMirror_input").performTextInput("x")
            waitForIdle()
        }
        holder.assertDoc("x")
    }
}
