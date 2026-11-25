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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import kotlin.test.Test

/**
 * A press that ends in a cancel releases the editor's focus capture (#359), like a lift.
 *
 * Android-only because only Android's test harness can deliver a touch cancel: the skiko one
 * (desktop, wasmJs, iOS, macOS) implements `enqueueCancel` as a no-op, so there the press simply
 * stays down. The rest of the capture's release paths are in [FocusCaptureReleaseTest].
 */
@OptIn(ExperimentalTestApi::class)
class FocusCaptureCancelTest {

    @Test
    fun cancelledPress_releasesTheCapture() = runFocusCaptureTest { f ->
        onNodeWithTag("KodeMirror").performTouchInput { down(capturePressPoint) }
        waitForIdle()
        assertCaptureHeld(f, "While a touch on the editor was held")
        onNodeWithTag("KodeMirror").performTouchInput { cancel() }
        waitForIdle()
        assertCaptureReleased(f, "After the touch was cancelled")
    }
}
