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
package com.monkopedia.kodemirror.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fields and facets that read other slots are computed on demand while a state is being
 * initialized, independent of extension order (#451). Expected values come from running the
 * same cases against @codemirror/state 6.5.4.
 */
class FieldDependencyTest {

    private fun mk(vararg extensions: Extension): EditorState =
        EditorState.create(EditorStateConfig(extensions = ExtensionList(extensions.toList())))

    private fun later(): StateField<Int> =
        StateField.define(StateFieldSpec(create = { 42 }, update = { v, _ -> v + 1 }))

    private fun reading(
        other: StateField<Int>,
        update: (Int, Transaction) -> Int = { v, _ -> v }
    ): StateField<Int> = StateField.define(
        StateFieldSpec(create = {
            it.field(other)
        }, update = update)
    )

    @Test
    fun fieldCreateCanReadALaterField() {
        val later = later()
        val early = reading(later)
        val st = mk(early, later)
        assertEquals(42, st.field(early))
        assertEquals(42, st.field(later))
    }

    @Test
    fun fieldCreateCanReadAnEarlierField() {
        val later = later()
        val early = reading(later)
        val st = mk(later, early)
        assertEquals(42, st.field(early))
        assertEquals(42, st.field(later))
    }

    @Test
    fun fieldUpdateSeesTheUpdatedValueOfALaterField() {
        for (order in listOf(true, false)) {
            val later = later()
            val early = reading(later) { _, tr -> tr.state.field(later) * 10 }
            val st = (if (order) mk(early, later) else mk(later, early))
                .update(TransactionSpec()).state
            assertEquals(430, st.field(early))
            assertEquals(43, st.field(later))
        }
    }

    @Test
    fun fieldCreateCanReadAFacetComputedFromALaterField() {
        val later = later()
        val fac: Facet<Int, List<Int>> = Facet.define()
        val early = StateField.define(
            StateFieldSpec(create = { it.facet(fac) }, update = { v, _ -> v })
        )
        val st =
            mk(early, later, fac.compute(listOf(Slot.FieldSlot(later))) { it.field(later) + 1 })
        assertEquals(listOf(43), st.field(early))
    }

    @Test
    fun fieldsAppendedByReconfigureResolveOnDemand() {
        val later = later()
        val early = reading(later)
        val st = mk().update(
            TransactionSpec(
                effects = listOf(StateEffect.appendConfig.of(ExtensionList(listOf(early, later))))
            )
        ).state
        assertEquals(42, st.field(early))
        assertEquals(43, st.field(later))
    }

    @Test
    fun cyclicFieldDependenciesAreReported() {
        lateinit var b: StateField<Int>
        val a = StateField.define(
            StateFieldSpec(create = {
                it.field(b) + 1
            }, update = { v, _ -> v })
        )
        b = reading(a)
        val err = assertFailsWith<IllegalStateException> { mk(a, b) }
        assertTrue(err.message!!.startsWith("Cyclic dependency"), err.message)
    }

    @Test
    fun aFieldReadingItselfIsACycle() {
        lateinit var self: StateField<Int>
        self =
            StateField.define(StateFieldSpec(create = { it.field(self) }, update = { v, _ -> v }))
        val err = assertFailsWith<IllegalStateException> { mk(self) }
        assertTrue(err.message!!.startsWith("Cyclic dependency"), err.message)
    }

    @Test
    fun initScopeIsClearedOnceInitializationFinishes() {
        val later = later()
        val early = reading(later)
        val created = mk(early, later)
        assertNull(created.initScope)
        assertNull(created.update(TransactionSpec()).state.initScope)
        val reconfigured = mk().update(
            TransactionSpec(
                effects = listOf(StateEffect.appendConfig.of(ExtensionList(listOf(early, later))))
            )
        ).state
        assertNull(reconfigured.initScope)
    }
}
