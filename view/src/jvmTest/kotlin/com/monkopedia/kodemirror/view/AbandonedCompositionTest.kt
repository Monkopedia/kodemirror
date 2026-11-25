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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * A composition can be abandoned: it fails before it is applied, so no effect it registered
 * ever runs, and in particular no `DisposableEffect`'s `onDispose`. The view plugins
 * `KodeMirror` builds while composing must still be destroyed then (#288), or their parse
 * workers, linter scopes and open LSP files outlive an editor that never appeared.
 *
 * In `jvmTest` because the test needs `runComposeUiTest` to rethrow the composition's failure
 * synchronously; on wasmJs it returns a promise instead. What it exercises is shared
 * `commonMain` code.
 */
@OptIn(ExperimentalTestApi::class)
class AbandonedCompositionTest {

    private class Abandon : RuntimeException("abandon this composition")

    @Test
    fun abandonedComposition_destroysTheViewPlugins() {
        var created = 0
        var destroyed = 0
        val plugin = ViewPlugin.define(
            PluginSpec<PluginValue>(
                create = {
                    created++
                    object : PluginValue {
                        override fun destroy() {
                            destroyed++
                        }
                    }
                }
            )
        )
        val session = EditorSession(
            EditorState.create(
                EditorStateConfig(doc = "hello".asDoc(), extensions = plugin.asExtension())
            )
        )
        assertFailsWith<Abandon> {
            runComposeUiTest {
                setContent {
                    KodeMirror(session = session)
                    throw Abandon()
                }
                waitForIdle()
            }
        }
        assertEquals(1, created, "the composition never built the plugin")
        assertEquals(1, destroyed, "the abandoned composition's plugin was never destroyed")
        assertNull(
            (session as EditorSessionImpl).pluginHost,
            "the session still points at the abandoned composition's plugin host"
        )
    }
}
