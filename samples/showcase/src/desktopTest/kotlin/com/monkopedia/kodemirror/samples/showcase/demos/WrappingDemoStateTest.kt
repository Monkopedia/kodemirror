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
 */
package com.monkopedia.kodemirror.samples.showcase.demos

import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.lang.markdown.markdown
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.lineWrapping
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Regression test for the Line Wrapping demo crash.
 *
 * [basicSetup] installs a runtime guard (a StateField) that throws
 * `IllegalStateException("No language extension configured...")` from inside
 * [EditorState.create] when no language extension is present. The Line Wrapping
 * demo originally configured `basicSetup + lineWrapping` with no language and
 * crashed on load. This compiles fine, so a compile check alone never caught it.
 *
 * This test realizes the exact extension list the demo now uses and asserts that
 * creating the state does NOT throw.
 */
class WrappingDemoStateTest {

    @Test
    fun wrappingDemoExtensionsCreateStateWithoutThrowing() {
        // Mirror WrappingDemo: basicSetup + markdown language + lineWrapping (wrap on).
        val extensions = basicSetup + markdown().extension + lineWrapping
        val state = EditorState.create(
            EditorStateConfig(
                doc = SampleDocs.wrapping.asDoc(),
                extensions = extensions
            )
        )
        assertNotNull(state)
    }

    @Test
    fun wrappingDemoExtensionsCreateStateNoWrapWithoutThrowing() {
        // Mirror the "No wrap" branch: language present, wrap compartment empty.
        val extensions = basicSetup + markdown().extension
        val state = EditorState.create(
            EditorStateConfig(
                doc = SampleDocs.wrapping.asDoc(),
                extensions = extensions
            )
        )
        assertNotNull(state)
    }
}
