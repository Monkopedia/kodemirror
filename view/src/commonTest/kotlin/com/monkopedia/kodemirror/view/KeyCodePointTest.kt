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
 * Guards for [keyCharFromCodePoint], the check that decides whether a key
 * event's code point may be inserted into the document.
 *
 * The motivating regression is #220: AWT reports `CHAR_UNDEFINED` (U+FFFF) for
 * a key press that produces no character — a bare Shift, Ctrl, Alt, Meta or
 * Caps Lock press — and U+FFFF passed the old `isISOControl()` guard, so every
 * modifier tap inserted a stray glyph at the caret.
 */
class KeyCodePointTest {

    @Test
    fun charUndefined_isRejected() {
        // java.awt.event.KeyEvent.CHAR_UNDEFINED — what AWT reports for a key
        // press with no character, including a bare modifier press (#220).
        assertNull(keyCharFromCodePoint(0xFFFF))
    }

    @Test
    fun noncharacters_areRejected() {
        assertNull(keyCharFromCodePoint(0xFFFE))
        for (codePoint in 0xFDD0..0xFDEF) {
            assertNull(
                keyCharFromCodePoint(codePoint),
                "U+${codePoint.toString(16).uppercase()} is a noncharacter"
            )
        }
    }

    @Test
    fun surrogates_areRejected() {
        // A lone surrogate is half of a pair; inserting it would leave the
        // document holding an unpaired code unit.
        assertNull(keyCharFromCodePoint(0xD800))
        assertNull(keyCharFromCodePoint(0xDBFF))
        assertNull(keyCharFromCodePoint(0xDC00))
        assertNull(keyCharFromCodePoint(0xDFFF))
    }

    @Test
    fun outOfRangeCodePoints_areRejected() {
        // Cannot be represented as a single Char, so the only alternative to
        // rejecting them is inserting a truncated one.
        assertNull(keyCharFromCodePoint(-1))
        assertNull(keyCharFromCodePoint(0x1F600))
        // Android sets KeyCharacterMap.COMBINING_ACCENT (0x80000000) on dead keys.
        assertNull(keyCharFromCodePoint(0x80000000.toInt() or 'e'.code))
    }

    @Test
    fun controlCharacters_areRejected() {
        assertNull(keyCharFromCodePoint(0))
        assertNull(keyCharFromCodePoint('\t'.code))
        assertNull(keyCharFromCodePoint('\n'.code))
        assertNull(keyCharFromCodePoint(0x7F))
    }

    @Test
    fun printableCharacters_areAccepted() {
        assertEquals('a', keyCharFromCodePoint('a'.code))
        assertEquals(' ', keyCharFromCodePoint(' '.code))
        assertEquals('$', keyCharFromCodePoint('$'.code))
        assertEquals('é', keyCharFromCodePoint(0x00E9))
        assertEquals('中', keyCharFromCodePoint(0x4E2D))
        // Directly adjacent to the rejected ranges.
        assertEquals('﷏', keyCharFromCodePoint(0xFDCF))
        assertEquals('ﷰ', keyCharFromCodePoint(0xFDF0))
        assertEquals('�', keyCharFromCodePoint(0xFFFD))
    }
}
