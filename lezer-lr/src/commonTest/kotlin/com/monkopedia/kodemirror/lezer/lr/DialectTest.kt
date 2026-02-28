/*
 * Copyright 2025 Jason Monk
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
package com.monkopedia.kodemirror.lezer.lr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the [Dialect] class.
 */
class DialectTest {

    // --- allows() with null disabled ---

    @Test
    fun allowsReturnsTrueWhenDisabledIsNull() {
        val dialect = Dialect(source = null, flags = emptyList(), disabled = null)
        assertTrue(dialect.allows(0))
        assertTrue(dialect.allows(1))
        assertTrue(dialect.allows(100))
        assertTrue(dialect.allows(65535))
    }

    @Test
    fun allowsReturnsTrueForAnyTermWhenDisabledIsNull() {
        val dialect = Dialect(source = "test", flags = listOf(true, false), disabled = null)
        // Any term should be allowed when disabled is null
        for (term in 0..50) {
            assertTrue(dialect.allows(term), "Term $term should be allowed")
        }
    }

    // --- allows() with specific disabled terms ---

    @Test
    fun allowsReturnsTrueWhenTermIsEnabled() {
        // disabled[term] == 0 means the term is allowed
        val disabled = intArrayOf(0, 0, 0, 0, 0)
        val dialect = Dialect(source = null, flags = emptyList(), disabled = disabled)
        assertTrue(dialect.allows(0))
        assertTrue(dialect.allows(1))
        assertTrue(dialect.allows(4))
    }

    @Test
    fun allowsReturnsFalseWhenTermIsDisabled() {
        // disabled[term] != 0 means the term is not allowed
        val disabled = intArrayOf(0, 1, 0, 1, 0)
        val dialect = Dialect(source = null, flags = emptyList(), disabled = disabled)
        assertTrue(dialect.allows(0))
        assertFalse(dialect.allows(1))
        assertTrue(dialect.allows(2))
        assertFalse(dialect.allows(3))
        assertTrue(dialect.allows(4))
    }

    @Test
    fun allowsWithAllDisabled() {
        val disabled = intArrayOf(1, 1, 1, 1, 1)
        val dialect = Dialect(source = null, flags = emptyList(), disabled = disabled)
        for (term in 0..4) {
            assertFalse(dialect.allows(term), "Term $term should be disabled")
        }
    }

    @Test
    fun allowsWithAllEnabled() {
        val disabled = intArrayOf(0, 0, 0, 0, 0)
        val dialect = Dialect(source = null, flags = emptyList(), disabled = disabled)
        for (term in 0..4) {
            assertTrue(dialect.allows(term), "Term $term should be allowed")
        }
    }

    @Test
    fun allowsWithMixedDisabledValues() {
        // disabled can contain values other than 0 and 1
        val disabled = intArrayOf(0, 2, 0, 255, 0)
        val dialect = Dialect(source = null, flags = emptyList(), disabled = disabled)
        assertTrue(dialect.allows(0))
        assertFalse(dialect.allows(1)) // 2 != 0
        assertTrue(dialect.allows(2))
        assertFalse(dialect.allows(3)) // 255 != 0
        assertTrue(dialect.allows(4))
    }

    // --- Dialect constructor properties ---

    @Test
    fun dialectStoresSource() {
        val dialect = Dialect(source = "myDialect", flags = emptyList(), disabled = null)
        assertEquals("myDialect", dialect.source)
    }

    @Test
    fun dialectStoresNullSource() {
        val dialect = Dialect(source = null, flags = emptyList(), disabled = null)
        assertEquals(null, dialect.source)
    }

    @Test
    fun dialectStoresFlags() {
        val flags = listOf(true, false, true)
        val dialect = Dialect(source = null, flags = flags, disabled = null)
        assertEquals(flags, dialect.flags)
    }

    @Test
    fun dialectStoresEmptyFlags() {
        val dialect = Dialect(source = null, flags = emptyList(), disabled = null)
        assertEquals(emptyList(), dialect.flags)
    }

    @Test
    fun dialectStoresDisabledArray() {
        val disabled = intArrayOf(0, 1, 0)
        val dialect = Dialect(source = null, flags = emptyList(), disabled = disabled)
        assertEquals(disabled, dialect.disabled)
    }

    // --- Edge cases ---

    @Test
    fun allowsWithSingleElementDisabled() {
        val disabled = intArrayOf(0)
        val dialect = Dialect(source = null, flags = emptyList(), disabled = disabled)
        assertTrue(dialect.allows(0))
    }

    @Test
    fun allowsWithSingleElementDisabledAndBlocked() {
        val disabled = intArrayOf(1)
        val dialect = Dialect(source = null, flags = emptyList(), disabled = disabled)
        assertFalse(dialect.allows(0))
    }

    @Test
    fun allowsFirstTermDisabled() {
        val disabled = intArrayOf(1, 0, 0)
        val dialect = Dialect(source = null, flags = emptyList(), disabled = disabled)
        assertFalse(dialect.allows(0))
        assertTrue(dialect.allows(1))
        assertTrue(dialect.allows(2))
    }

    @Test
    fun allowsLastTermDisabled() {
        val disabled = intArrayOf(0, 0, 1)
        val dialect = Dialect(source = null, flags = emptyList(), disabled = disabled)
        assertTrue(dialect.allows(0))
        assertTrue(dialect.allows(1))
        assertFalse(dialect.allows(2))
    }
}
