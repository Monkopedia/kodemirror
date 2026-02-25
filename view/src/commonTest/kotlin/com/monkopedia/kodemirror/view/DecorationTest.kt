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

import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DecorationTest {

    @Test
    fun markDecorationCreation() {
        val spec = MarkDecorationSpec()
        val mark = Decoration.mark(spec)
        assertTrue(mark is MarkDecoration)
        assertEquals(spec, mark.spec)
    }

    @Test
    fun markDecorationInclusiveSides() {
        val inclusive = Decoration.mark(MarkDecorationSpec(inclusive = true))
        assertEquals(-1, inclusive.startSide)
        assertEquals(1, inclusive.endSide)

        val exclusive = Decoration.mark(MarkDecorationSpec())
        assertEquals(0, exclusive.startSide)
        assertEquals(0, exclusive.endSide)
    }

    @Test
    fun markDecorationInclusiveStart() {
        val deco = Decoration.mark(MarkDecorationSpec(inclusiveStart = true))
        assertEquals(-1, deco.startSide)
        assertEquals(0, deco.endSide)
    }

    @Test
    fun markDecorationEquality() {
        val spec = MarkDecorationSpec()
        val a = Decoration.mark(spec)
        val b = Decoration.mark(spec)
        assertTrue(a.eq(b))
        assertTrue(b.eq(a))

        val c = Decoration.mark(MarkDecorationSpec(inclusive = true))
        assertFalse(a.eq(c))
    }

    @Test
    fun widgetDecorationCreation() {
        val widget = SimpleWidget()
        val spec = WidgetDecorationSpec(widget = widget)
        val deco = Decoration.widget(spec)
        assertTrue(deco is WidgetDecoration)
        assertTrue(deco.point)
    }

    @Test
    fun widgetDecorationSides() {
        val widget = SimpleWidget()
        val before = Decoration.widget(WidgetDecorationSpec(widget = widget, side = -1))
        val after = Decoration.widget(WidgetDecorationSpec(widget = widget, side = 1))
        assertEquals(-1, before.startSide)
        assertEquals(1, after.startSide)
    }

    @Test
    fun lineDecorationCreation() {
        val spec = LineDecorationSpec(cssClass = "my-class")
        val deco = Decoration.line(spec)
        assertTrue(deco is LineDecoration)
        assertEquals(-1, deco.startSide)
    }

    @Test
    fun lineDecorationEquality() {
        val spec = LineDecorationSpec(cssClass = "test")
        val a = Decoration.line(spec)
        val b = Decoration.line(spec)
        assertTrue(a.eq(b))

        val c = Decoration.line(LineDecorationSpec(cssClass = "other"))
        assertFalse(a.eq(c))
    }

    @Test
    fun replaceDecorationCreation() {
        val spec = ReplaceDecorationSpec()
        val deco = Decoration.replace(spec)
        assertTrue(deco is ReplaceDecoration)
        assertTrue(deco.point)
    }

    @Test
    fun replaceDecorationSides() {
        val deco = Decoration.replace(ReplaceDecorationSpec())
        assertEquals(-100, deco.startSide)
        assertEquals(100, deco.endSide)

        val inclusive = Decoration.replace(ReplaceDecorationSpec(inclusive = true))
        assertEquals(-1, inclusive.startSide)
        assertEquals(1, inclusive.endSide)
    }

    @Test
    fun decorationInRangeSet() {
        val builder = RangeSetBuilder<Decoration>()
        builder.add(0, 5, Decoration.mark(MarkDecorationSpec()))
        builder.add(3, 8, Decoration.mark(MarkDecorationSpec(inclusive = true)))
        val set = builder.finish()

        assertEquals(2, set.size)
        assertFalse(set.isEmpty)
    }

    @Test
    fun emptyDecorationSet() {
        val empty: DecorationSet = RangeSet.empty()
        assertTrue(empty.isEmpty)
        assertEquals(0, empty.size)
    }

    @Test
    fun decorationSetBetween() {
        val builder = RangeSetBuilder<Decoration>()
        builder.add(2, 6, Decoration.mark(MarkDecorationSpec()))
        val set = builder.finish()

        var found = false
        set.between(0, 10) { _, _, _ ->
            found = true
            null
        }
        assertTrue(found)
    }

    @Test
    fun widgetTypeEquality() {
        val w1 = SimpleWidget()
        val w2 = SimpleWidget()
        // Default eq uses identity
        assertFalse(w1.eq(w2))
        assertTrue(w1.eq(w1))
    }
}

private class SimpleWidget : WidgetType() {
    @androidx.compose.runtime.Composable
    override fun Content() {}
}
