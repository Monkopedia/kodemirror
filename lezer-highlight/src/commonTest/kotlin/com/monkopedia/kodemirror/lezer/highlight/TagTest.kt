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
package com.monkopedia.kodemirror.lezer.highlight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TagTest {

    @Test
    fun defineCreatesTag() {
        val tag = Tag.define("test")
        assertEquals("test", tag.name)
        assertTrue(tag.set.contains(tag))
    }

    @Test
    fun defineWithParent() {
        val parent = Tag.define("parent")
        val child = Tag.define("child", parent)
        assertTrue(child.set.contains(child))
        assertTrue(child.set.contains(parent))
        assertEquals(2, child.set.size)
    }

    @Test
    fun tagSetIncludesAncestors() {
        val grandparent = Tag.define("gp")
        val parent = Tag.define("p", grandparent)
        val child = Tag.define("c", parent)
        assertEquals(3, child.set.size)
        assertTrue(child.set.contains(child))
        assertTrue(child.set.contains(parent))
        assertTrue(child.set.contains(grandparent))
    }

    @Test
    fun modifierProducesDerivedTag() {
        val base = Tag.define("base")
        val mod = Tag.defineModifier("mod")
        val modified = mod(base)
        assertNotEquals(base.id, modified.id)
        assertEquals(base, modified.base)
    }

    @Test
    fun sameModifierAppliedTwiceReturnsSame() {
        val base = Tag.define("base")
        val mod = Tag.defineModifier("mod")
        val first = mod(base)
        val second = mod(base)
        assertSame(first, second)
    }

    @Test
    fun modifiedTagAlreadyModifiedReturnsSame() {
        val base = Tag.define("base")
        val mod = Tag.defineModifier("mod")
        val modified = mod(base)
        val doubleModified = mod(modified)
        assertSame(modified, doubleModified)
    }

    @Test
    fun multipleModifiersOrderIndependent() {
        val base = Tag.define("base")
        val m1 = Tag.defineModifier("m1")
        val m2 = Tag.defineModifier("m2")
        val a = m1(m2(base))
        val b = m2(m1(base))
        assertSame(a, b)
    }

    @Test
    fun modifiedTagSetIncludesSubsets() {
        val base = Tag.define("base")
        val m1 = Tag.defineModifier("m1")
        val m2 = Tag.defineModifier("m2")
        val both = m1(m2(base))
        // Should include m1(m2(base)), m1(base), m2(base), base
        assertTrue(both.set.contains(both))
        assertTrue(both.set.any { it == m1(base) })
        assertTrue(both.set.any { it == m2(base) })
        assertTrue(both.set.any { it == base })
    }

    @Test
    fun standardTagsExist() {
        assertNotNull(Tags.comment)
        assertNotNull(Tags.keyword)
        assertNotNull(Tags.string)
        assertNotNull(Tags.number)
        assertNotNull(Tags.operator)
        assertNotNull(Tags.punctuation)
    }

    @Test
    fun standardTagHierarchy() {
        // lineComment is a subtag of comment
        assertTrue(Tags.lineComment.set.contains(Tags.comment))
        // keyword subtags
        assertTrue(Tags.controlKeyword.set.contains(Tags.keyword))
        // bracket is a subtag of punctuation
        assertTrue(Tags.bracket.set.contains(Tags.punctuation))
    }

    @Test
    fun standardModifiers() {
        val defVar = Tags.definition(Tags.variableName)
        assertNotNull(defVar)
        assertEquals(Tags.variableName, defVar.base)
    }
}
