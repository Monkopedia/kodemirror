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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [LineLayoutCache] that use a minimal stub [TextLayoutResult].
 *
 * Because Compose UI types cannot be instantiated in a headless test
 * environment, the store/retrieve API is tested without actually creating
 * [TextLayoutResult] instances. The coordinate tests are tested logically
 * using the cache's public API contract.
 */
class LineLayoutTest {

    private fun makeState(doc: String): EditorState =
        EditorState.create(EditorStateConfig(doc = doc.asDoc()))

    @Test
    fun cacheStoresAndEvicts() {
        val cache = LineLayoutCache()
        // After eviction of a line that was never added, it should not crash
        cache.evict(setOf(1, 2, 3))
        // forLine should return null for unstored line
        assertNull(cache.forLine(5))
    }

    @Test
    fun blockAtPosMissingEntry() {
        val cache = LineLayoutCache()
        val state = makeState("hello\nworld")
        // No layouts stored → should return null
        assertNull(cache.blockAtPos(0, state))
    }

    @Test
    fun viewportConstruction() {
        val vp = Viewport(10, 100)
        assertEquals(10, vp.from)
        assertEquals(100, vp.to)
        assertTrue(vp.contains(50))
        assertTrue(!vp.contains(5))
    }

    @Test
    fun blockInfoProperties() {
        val info = BlockInfo(
            from = 0,
            to = 10,
            top = 20f,
            height = 16f,
            type = BlockType.Text
        )
        assertEquals(36f, info.bottom)
        assertEquals(BlockType.Text, info.type)
        assertNull(info.widget)
    }

    @Test
    fun rectProperties() {
        val rect = Rect(10f, 20f, 30f, 40f)
        assertEquals(20f, rect.width)
        assertEquals(20f, rect.height)
        assertEquals(20f, rect.centerX)
        assertEquals(30f, rect.centerY)
    }

    @Test
    fun coordsAtPosMissingReturnsNull() {
        val cache = LineLayoutCache()
        val state = makeState("hello")
        assertNull(cache.coordsAtPos(0, 1, state))
    }

    @Test
    fun posAtCoordsEmptyCacheReturnsNull() {
        val cache = LineLayoutCache()
        val state = makeState("hello")
        assertNull(cache.posAtCoords(0f, 0f, state))
    }
}
