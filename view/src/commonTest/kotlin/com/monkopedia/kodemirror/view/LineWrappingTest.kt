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

import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the wrap-mode decision the renderer reads from [lineWrappingFacet].
 * Matches CodeMirror 6: wrapping is OFF by default and only enabled when the
 * [lineWrapping] extension is added.
 */
class LineWrappingTest {

    private fun stateWith(vararg extensions: com.monkopedia.kodemirror.state.Extension) =
        EditorState.create(
            EditorStateConfig(
                doc = "hello world".asDoc(),
                extensions = if (extensions.isEmpty()) {
                    null
                } else {
                    com.monkopedia.kodemirror.state.ExtensionList(extensions.toList())
                }
            )
        )

    @Test
    fun wrappingDisabledByDefault() {
        assertFalse(stateWith().facet(lineWrappingFacet))
    }

    @Test
    fun lineWrappingExtensionEnablesWrapping() {
        assertTrue(stateWith(lineWrapping).facet(lineWrappingFacet))
    }
}
