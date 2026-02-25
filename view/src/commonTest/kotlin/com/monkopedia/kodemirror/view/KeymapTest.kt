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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeymapTest {

    @Test
    fun simpleKey() {
        assertEquals("Enter", normalizeKeyName("Enter"))
    }

    @Test
    fun ctrlModifier() {
        assertEquals("Ctrl-s", normalizeKeyName("Ctrl-s"))
    }

    @Test
    fun altModifier() {
        assertEquals("Alt-F", normalizeKeyName("Alt-F"))
    }

    @Test
    fun shiftModifier() {
        assertEquals("Shift-Tab", normalizeKeyName("Shift-Tab"))
    }

    @Test
    fun metaModifier() {
        assertEquals("Meta-k", normalizeKeyName("Meta-k"))
    }

    @Test
    fun cmdAlias() {
        assertEquals("Meta-k", normalizeKeyName("cmd-k"))
    }

    @Test
    fun mAlias() {
        assertEquals("Meta-k", normalizeKeyName("m-k"))
    }

    @Test
    fun altAliasA() {
        assertEquals("Alt-x", normalizeKeyName("a-x"))
    }

    @Test
    fun ctrlAliasC() {
        assertEquals("Ctrl-c", normalizeKeyName("c-c"))
    }

    @Test
    fun ctrlAliasControl() {
        assertEquals("Ctrl-a", normalizeKeyName("control-a"))
    }

    @Test
    fun shiftAliasS() {
        assertEquals("Shift-F5", normalizeKeyName("s-F5"))
    }

    @Test
    fun modOnNonMac() {
        assertEquals("Ctrl-s", normalizeKeyName("mod-s", mac = false))
    }

    @Test
    fun modOnMac() {
        assertEquals("Meta-s", normalizeKeyName("mod-s", mac = true))
    }

    @Test
    fun multipleModifiers() {
        assertEquals("Alt-Ctrl-Enter", normalizeKeyName("Ctrl-Alt-Enter"))
    }

    @Test
    fun spaceKey() {
        assertEquals(" ", normalizeKeyName("Space"))
    }

    @Test
    fun hyphenKey() {
        // "Ctrl--" splits as Ctrl and - (end of string, not split)
        assertEquals("Ctrl--", normalizeKeyName("Ctrl--"))
    }

    @Test
    fun unknownModifier() {
        assertFailsWith<IllegalArgumentException> {
            normalizeKeyName("Super-x")
        }
    }

    @Test
    fun shiftFirst() {
        // When multiple modifiers, canonical order is Alt, Ctrl, Meta, Shift
        assertEquals("Ctrl-Shift-k", normalizeKeyName("Shift-Ctrl-k"))
    }

    @Test
    fun keyBindingData() {
        val binding = KeyBinding(
            key = "Ctrl-s",
            run = { _ -> true }
        )
        assertEquals("Ctrl-s", binding.key)
    }

    @Test
    fun keymapFacetCollects() {
        // keymapOf creates an extension that contributes bindings
        val ext = keymapOf(
            KeyBinding(key = "Ctrl-a"),
            KeyBinding(key = "Ctrl-b")
        )
        // Verify it's an Extension (not null)
        val state = com.monkopedia.kodemirror.state.EditorState.create(
            com.monkopedia.kodemirror.state.EditorStateConfig(extensions = ext)
        )
        val bindings = state.facet(keymap)
        assertEquals(2, bindings.size)
        assertEquals("Ctrl-a", bindings[0].key)
        assertEquals("Ctrl-b", bindings[1].key)
    }
}
