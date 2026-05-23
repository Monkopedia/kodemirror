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
 * Regression tests for the "consume only when handled" decision in
 * [handleRawKeyEvent]. On wasmJs the document-level keydown listener only
 * calls `preventDefault()` when this function returns true, so a key with no
 * editor binding must return false to fall through to the browser (#49).
 */
class RawKeyEventHandlingTest {

    private fun sessionWith(vararg bindings: KeyBinding): EditorSession {
        val state = EditorState.create(
            EditorStateConfig(
                doc = "hello".asDoc(),
                extensions = keymapOf(*bindings)
            )
        )
        return EditorSession(state)
    }

    @Test
    fun boundModifiedKeyIsConsumed() {
        var ran = false
        val session = sessionWith(
            KeyBinding(key = "Ctrl-s", run = {
                ran = true
                true
            })
        )
        val handled = handleRawKeyEvent(
            session,
            key = "s",
            ctrl = true,
            alt = false,
            meta = false,
            shift = false
        )
        assertTrue(handled, "Ctrl-s has a binding, so it must be consumed")
        assertTrue(ran, "the bound command should have executed")
    }

    @Test
    fun unboundCtrlTabFallsThroughToBrowser() {
        // Ctrl+Tab is a browser-reserved shortcut with no editor binding.
        val session = sessionWith(
            KeyBinding(key = "Ctrl-s", run = { true })
        )
        val handled = handleRawKeyEvent(
            session,
            key = "Tab",
            ctrl = true,
            alt = false,
            meta = false,
            shift = false
        )
        assertFalse(handled, "Ctrl+Tab has no binding and must fall through to the browser")
    }

    @Test
    fun unboundCtrlWFallsThroughToBrowser() {
        val session = sessionWith(
            KeyBinding(key = "Ctrl-s", run = { true })
        )
        val handled = handleRawKeyEvent(
            session,
            key = "w",
            ctrl = true,
            alt = false,
            meta = false,
            shift = false
        )
        assertFalse(handled, "Ctrl+W (close tab) has no binding and must fall through")
    }

    @Test
    fun unboundCtrlDigitFallsThroughToBrowser() {
        val session = sessionWith(
            KeyBinding(key = "Ctrl-s", run = { true })
        )
        val handled = handleRawKeyEvent(
            session,
            key = "1",
            ctrl = true,
            alt = false,
            meta = false,
            shift = false
        )
        assertFalse(handled, "Ctrl+1 (switch tab) has no binding and must fall through")
    }

    @Test
    fun unboundSpecialKeyFallsThroughToBrowser() {
        // A special key (PageUp) with a modifier and no binding must not be consumed.
        val session = sessionWith(
            KeyBinding(key = "Ctrl-s", run = { true })
        )
        val handled = handleRawKeyEvent(
            session,
            key = "PageUp",
            ctrl = true,
            alt = false,
            meta = false,
            shift = false
        )
        assertFalse(handled, "Ctrl+PageUp has no binding and must fall through")
    }

    @Test
    fun modifierOnlyKeyIsNotConsumed() {
        val session = sessionWith()
        assertFalse(
            handleRawKeyEvent(
                session,
                key = "Control",
                ctrl = true,
                alt = false,
                meta = false,
                shift = false
            )
        )
    }
}
