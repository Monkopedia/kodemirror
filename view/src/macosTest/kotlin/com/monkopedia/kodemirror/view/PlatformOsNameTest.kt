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
import kotlin.test.assertTrue

/**
 * What this target must call itself, as a literal.
 *
 * The name is written out here rather than derived from anything, because every
 * other test that cares about the platform reads [currentOs] and would agree
 * with whatever [platformOsName] happened to say — the shape that let a
 * hardcoded `"Mac"` on *all* Kotlin/Native targets go unnoticed (#217). Only a
 * test that already knows the answer for the target it is compiled into can
 * catch a wrong one, so this file exists once per Apple family and states it.
 */
class PlatformOsNameTest {

    @Test
    fun macosReportsMac() {
        assertEquals("Mac", platformOsName(), "macOS must report itself as Mac")
    }

    @Test
    fun macosIsMacFamily() {
        assertTrue(isMacFamilyOs(platformOsName()), "macOS must resolve the mac overrides")
    }
}
