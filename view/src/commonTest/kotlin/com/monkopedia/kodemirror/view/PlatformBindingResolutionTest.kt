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

import com.monkopedia.kodemirror.commands.standardKeymap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The intended OS-name → binding mapping, written out as literals.
 *
 * `resolveBindingKey` takes the OS name as a parameter, so every case here is
 * driven from a written-down name rather than from whatever the host reports.
 * That is the point: the tests this replaces asked the product what platform it
 * was on and then asserted the product agreed with itself, which is how a
 * hardcoded `"Mac"` for every Kotlin/Native target survived (#217). These run
 * identically on all six targets and would fail on all six if the mapping moved.
 *
 * `"iOS"` is the case that matters. It matches neither `"mac"` nor `"darwin"`,
 * so it is only in the mac family because [isMacFamilyOs] puts it there — and
 * if that widening were dropped alongside the honest platform name, iOS users
 * would move from Cmd to Ctrl and these are the assertions that would say so.
 */
class PlatformBindingResolutionTest {

    /** The OS names that must resolve the `mac` overrides. */
    private val macFamily = listOf("Mac", "Mac OS X", "iOS", "Darwin")

    /** The OS names that must not. */
    private val nonMac = listOf("Linux", "Windows", "Windows 11")

    private val cut = KeyBinding(key = "Ctrl-x", mac = "Meta-x")
    private val mod = KeyBinding(key = "Mod-s")
    private val perPlatform = KeyBinding(
        key = "Ctrl-Home",
        mac = "Meta-Home",
        win = "Alt-Home",
        linux = "Shift-Home"
    )

    @Test
    fun macFamilyNamesAreMacFamily() {
        for (os in macFamily) {
            assertTrue(isMacFamilyOs(os), "$os must be in the mac family")
        }
    }

    @Test
    fun nonMacNamesAreNotMacFamily() {
        for (os in nonMac) {
            assertFalse(isMacFamilyOs(os), "$os must not be in the mac family")
        }
    }

    @Test
    fun macFamilyResolvesTheMacOverride() {
        for (os in macFamily) {
            assertEquals("Meta-x", resolveBindingKey(cut, os), "cut on $os")
        }
    }

    @Test
    fun nonMacResolvesTheGenericKey() {
        for (os in nonMac) {
            assertEquals("Ctrl-x", resolveBindingKey(cut, os), "cut on $os")
        }
    }

    @Test
    fun modResolvesToMetaOnMacFamilyAndCtrlElsewhere() {
        for (os in macFamily) {
            assertEquals("Meta-s", resolveBindingKey(mod, os), "Mod-s on $os")
        }
        for (os in nonMac) {
            assertEquals("Ctrl-s", resolveBindingKey(mod, os), "Mod-s on $os")
        }
    }

    /**
     * The `mac` override wins over `win`/`linux` for every mac-family name, so
     * adding iOS to the family cannot have quietly routed it down another arm
     * of the `when`.
     */
    @Test
    fun macFamilyPrefersTheMacOverrideOverTheOthers() {
        for (os in macFamily) {
            assertEquals("Meta-Home", resolveBindingKey(perPlatform, os), "docStart on $os")
        }
        assertEquals("Alt-Home", resolveBindingKey(perPlatform, "Windows"), "docStart on Windows")
        assertEquals("Shift-Home", resolveBindingKey(perPlatform, "Linux"), "docStart on Linux")
    }

    /**
     * The same mapping read off the real keymap rather than a stand-in, so a
     * change to `standardKeymap`'s clipboard bindings cannot pass this file by.
     */
    @Test
    fun standardKeymapCutFollowsTheSameMapping() {
        val binding = standardKeymap.single { it.key == "Ctrl-x" }
        assertEquals("Meta-x", resolveBindingKey(binding, "Mac"), "standard cut on Mac")
        assertEquals("Meta-x", resolveBindingKey(binding, "iOS"), "standard cut on iOS")
        assertEquals("Ctrl-x", resolveBindingKey(binding, "Linux"), "standard cut on Linux")
        assertEquals("Ctrl-x", resolveBindingKey(binding, "Windows"), "standard cut on Windows")
    }
}
