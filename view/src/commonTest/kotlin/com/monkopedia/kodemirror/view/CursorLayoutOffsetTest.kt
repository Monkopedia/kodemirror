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
import kotlin.test.assertNull

/**
 * [cursorLayoutOffset] decides which offset the line caret queries the text
 * layout for. Regression test for #146: the line's `TextLayoutResult` lags the
 * document by one frame, so right after a keystroke `offsetInLine == textLen + 1`
 * transiently. The old `offsetInLine in 0..textLen` guard bailed to the
 * line-start (column 0) in that case, flashing the caret to column 0 on every
 * keypress. It must instead CLAMP to the current layout (caret sits at line-end
 * for the one frame), reserving the null/fallback only for a genuinely absent
 * layout.
 */
class CursorLayoutOffsetTest {

    @Test
    fun transientPastEndClampsToLineEndNotColumnZero() {
        // The #146 case: doc applied, layout still has the old (shorter) text.
        assertEquals(5, cursorLayoutOffset(offsetInLine = 6, textLen = 5))
    }

    @Test
    fun inRangeOffsetIsUnchanged() {
        assertEquals(3, cursorLayoutOffset(offsetInLine = 3, textLen = 5))
    }

    @Test
    fun endOfLineOffsetIsKept() {
        assertEquals(5, cursorLayoutOffset(offsetInLine = 5, textLen = 5))
    }

    @Test
    fun negativeOffsetClampsToZero() {
        assertEquals(0, cursorLayoutOffset(offsetInLine = -1, textLen = 5))
    }

    @Test
    fun nullLayoutLengthUsesFallback() {
        // Only a genuinely absent layout falls back to the line-start draw.
        assertNull(cursorLayoutOffset(offsetInLine = 0, textLen = null))
    }
}
