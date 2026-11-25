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

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The same key reaches the same binding through both key entry points (#234): [handleKeyEvent],
 * for Compose key events, and [handleRawKeyEvent], for the raw keys wasmJs's document-level
 * listener sees. Both now resolve through [runKeyBindings]; this pins that they keep agreeing on
 * the names they hand it, which is where they could still drift.
 *
 * In `jvmTest` because it builds Compose key events directly, which only the desktop API allows.
 */
@OptIn(InternalComposeUiApi::class)
class KeyResolutionParityTest {

    private class Press(
        val description: String,
        val key: Key,
        val codePoint: Int,
        val raw: String,
        val ctrl: Boolean = false,
        val alt: Boolean = false,
        val shift: Boolean = false
    )

    private val presses = listOf(
        Press("Ctrl-s", Key.S, 's'.code, "s", ctrl = true),
        Press("Shift-Tab", Key.Tab, '\t'.code, "Tab", shift = true),
        Press("Alt-ArrowLeft", Key.DirectionLeft, 0, "ArrowLeft", alt = true),
        Press(
            "Ctrl-Shift-| (physical Ctrl-Shift-\\)",
            Key.Backslash,
            '|'.code,
            "|",
            true,
            false,
            true
        ),
        Press("Ctrl-/", Key.Slash, '/'.code, "/", ctrl = true),
        Press("Escape", Key.Escape, 27, "Escape"),
        Press("Ctrl-Enter", Key.Enter, '\n'.code, "Enter", ctrl = true),
        Press("Shift-a", Key.A, 'A'.code, "a", shift = true),
        Press("Ctrl-Shift-k", Key.K, 'K'.code, "k", ctrl = true, shift = true)
    )

    /** Which recorded binding a press reached, or "none". */
    private fun resolve(press: (EditorSession) -> Boolean): String {
        val hits = mutableListOf<String>()
        fun bind(key: String) = KeyBinding(key = key, run = {
            hits += key
            true
        })
        val session = EditorSession(
            EditorState.create(
                EditorStateConfig(
                    doc = "hello".asDoc(),
                    extensions = keymapOf(
                        bind("Ctrl-s"),
                        KeyBinding(key = "Tab", shift = {
                            hits += "shift of Tab"
                            true
                        }),
                        bind("Alt-ArrowLeft"),
                        bind("Ctrl-Shift-\\"),
                        bind("Ctrl-/"),
                        bind("Escape"),
                        bind("Ctrl-Enter"),
                        bind("Shift-a"),
                        bind("Ctrl-Shift-k")
                    )
                )
            )
        )
        press(session)
        return hits.singleOrNull() ?: "none ($hits)"
    }

    @Test
    fun bothEntryPointsReachTheSameBinding() {
        for (p in presses) {
            val viaEvent = resolve { session ->
                handleKeyEvent(
                    session,
                    KeyEvent(
                        key = p.key,
                        type = KeyEventType.KeyDown,
                        codePoint = p.codePoint,
                        isCtrlPressed = p.ctrl,
                        isAltPressed = p.alt,
                        isShiftPressed = p.shift
                    )
                )
            }
            val viaRaw = resolve { session ->
                handleRawKeyEvent(session, p.raw, p.ctrl, p.alt, meta = false, shift = p.shift)
            }
            assertEquals(viaEvent, viaRaw, "${p.description}: event path vs raw path")
            assertEquals(false, viaEvent.startsWith("none"), "${p.description} reached nothing")
        }
    }
}
