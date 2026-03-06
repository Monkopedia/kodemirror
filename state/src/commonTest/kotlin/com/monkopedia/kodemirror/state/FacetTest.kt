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
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FacetTest {

    private val num: Facet<Int, List<Int>> = Facet.define()
    private val str: Facet<String, List<String>> = Facet.define()
    private val bool: Facet<Boolean, List<Boolean>> = Facet.define()

    private fun mk(vararg extensions: Extension): EditorState =
        EditorState.create(EditorStateConfig(extensions = ExtensionList(extensions.toList())))

    @Test
    fun allowsQueryingOfFacets() {
        val st = mk(num.of(10), num.of(20), str.of("x"), str.of("y"))
        assertEquals("10,20", st.facet(num).joinToString(","))
        assertEquals("x,y", st.facet(str).joinToString(","))
    }

    @Test
    fun includesSubExtenders() {
        val e = { s: String -> ExtensionList(listOf(num.of(s.length), num.of(s.toInt()))) }
        val st = mk(num.of(5), e("20"), num.of(40), e("100"))
        assertEquals("5,2,20,40,3,100", st.facet(num).joinToString(","))
    }

    @Test
    fun onlyIncludesDuplicatedExtensionsOnce() {
        val e = num.of(50)
        val st = mk(num.of(1), e, num.of(4), e)
        assertEquals("1,50,4", st.facet(num).joinToString(","))
    }

    @Test
    fun returnsAnEmptyArrayForAbsentFacet() {
        val st = mk()
        assertTrue(st.facet(num).isEmpty())
    }

    @Test
    fun sortsExtensionsByPriority() {
        val st = mk(
            str.of("a"),
            str.of("b"),
            Prec.high(str.of("c")),
            Prec.highest(str.of("d")),
            Prec.low(str.of("e")),
            Prec.high(str.of("f")),
            str.of("g")
        )
        assertEquals("d,c,f,a,b,g,e", st.facet(str).joinToString(","))
    }

    @Test
    fun letsSubExtensionsInheritTheirParentsPriority() {
        val e = { n: Int -> num.of(n) as Extension }
        val st = mk(num.of(1), Prec.highest(e(2)), e(4))
        assertEquals("2,1,4", st.facet(num).joinToString(","))
    }

    @Test
    fun supportsDynamicFacet() {
        val st = mk(num.of(1), num.compute(emptyList()) { 88 })
        assertEquals("1,88", st.facet(num).joinToString(","))
    }

    @Test
    fun onlyRecomputesAFacetValueWhenNecessary() {
        val st = mk(
            num.of(1),
            num.compute(listOf(str.asSlot())) { s -> s.facet(str).joinToString("").length },
            str.of("hello")
        )
        val array = st.facet(num)
        assertEquals("1,5", array.joinToString(","))
        assertSame(array, st.update(TransactionSpec()).state.facet(num))
    }

    @Test
    fun canHandleDependenciesOnFacetsThatAreNotPresentInTheState() {
        val st = mk(
            num.compute(listOf(str.asSlot())) { s -> s.facet(str).joinToString("").length },
            str.compute(listOf(bool.asSlot())) { s -> s.facet(bool).joinToString("") }
        )
        assertEquals("0", st.update(TransactionSpec()).state.facet(num).joinToString(","))
    }

    @Test
    fun canSpecifyADependencyOnTheDocument() {
        var count = 0
        var st = mk(num.compute(listOf(Slot.Doc)) { count++ })
        assertEquals("0", st.facet(num).joinToString(","))
        st = st.update(
            TransactionSpec(
                changes = ChangeSpec.Single(from = 0, insert = InsertContent.StringContent("hello"))
            )
        ).state
        assertEquals("1", st.facet(num).joinToString(","))
        st = st.update(TransactionSpec()).state
        assertEquals("1", st.facet(num).joinToString(","))
    }

    @Test
    fun canSpecifyADependencyOnTheSelection() {
        var count = 0
        var st = mk(num.compute(listOf(Slot.Selection)) { count++ })
        assertEquals("0", st.facet(num).joinToString(","))
        st = st.update(
            TransactionSpec(
                changes = ChangeSpec.Single(from = 0, insert = InsertContent.StringContent("hello"))
            )
        ).state
        assertEquals("1", st.facet(num).joinToString(","))
        st = st.update(TransactionSpec(selection = SelectionSpec.CursorSpec(anchor = 2))).state
        assertEquals("2", st.facet(num).joinToString(","))
        st = st.update(TransactionSpec()).state
        assertEquals("2", st.facet(num).joinToString(","))
    }

    @Test
    fun canProvideMultipleValuesAtOnce() {
        var st = mk(
            num.computeN(listOf(Slot.Doc)) { s ->
                if (s.doc.length % 2 != 0) listOf(100, 10) else emptyList()
            },
            num.of(1)
        )
        assertEquals("1", st.facet(num).joinToString(","))
        st = st.update(
            TransactionSpec(
                changes = ChangeSpec.Single(from = 0, insert = InsertContent.StringContent("hello"))
            )
        ).state
        assertEquals("100,10,1", st.facet(num).joinToString(","))
    }

    @Test
    fun worksWithAStaticCombinedFacet() {
        val f = Facet.define<Int, Int>(combine = { ns -> ns.fold(0) { a, b -> a + b } })
        val st = mk(f.of(1), f.of(2), f.of(3))
        assertEquals(6, st.facet(f))
    }

    @Test
    fun worksWithADynamicCombinedFacet() {
        val f = Facet.define<Int, Int>(combine = { ns -> ns.fold(0) { a, b -> a + b } })
        var st = mk(f.of(1), f.compute(listOf(Slot.Doc)) { s -> s.doc.length }, f.of(3))
        assertEquals(4, st.facet(f))
        st = st.update(
            TransactionSpec(
                changes = ChangeSpec.Single(from = 0, insert = InsertContent.StringContent("hello"))
            )
        ).state
        assertEquals(9, st.facet(f))
    }

    @Test
    fun survivesReconfiguration() {
        var st = mk(num.compute(listOf(Slot.Doc)) { s -> s.doc.length }, num.of(2), str.of("3"))
        val st2 = st.update(
            TransactionSpec(
                effects = listOf(
                    StateEffect.reconfigure.of(
                        ExtensionList(
                            listOf(num.compute(listOf(Slot.Doc)) { s -> s.doc.length }, num.of(2))
                        )
                    )
                )
            )
        ).state
        assertEquals(st.facet(num), st2.facet(num))
        assertEquals(0, st2.facet(str).size)
    }

    @Test
    fun survivesUnrelatedReconfigurationEvenWithoutDeepCompare() {
        val f = Facet.define<Int, Map<String, Int>>(
            combine = { v -> mapOf("count" to v.size) }
        )
        val st = mk(f.compute(listOf(Slot.Doc)) { s -> s.doc.length }, f.of(2))
        val st2 = st.update(
            TransactionSpec(effects = listOf(StateEffect.appendConfig.of(str.of("hi"))))
        ).state
        assertSame(st.facet(f), st2.facet(f))
    }

    @Test
    fun preservesStaticFacetsAcrossReconfiguration() {
        val st = mk(num.of(1), num.of(2), str.of("3"))
        val st2 = st.update(
            TransactionSpec(
                effects = listOf(
                    StateEffect.reconfigure.of(
                        ExtensionList(listOf(num.of(1), num.of(2)))
                    )
                )
            )
        ).state
        assertSame(st.facet(num), st2.facet(num))
    }

    @Test
    fun createsNewlyAddedFieldsWhenReconfiguring() {
        var st = mk(num.of(2))
        val events = mutableListOf<String>()
        val field = StateField.define(
            StateFieldSpec<Int>(
                create = {
                    events.add("create")
                    0
                },
                update = { value, _ ->
                    events.add("update $value")
                    value + 1
                }
            )
        )
        st = st.update(TransactionSpec(effects = listOf(StateEffect.appendConfig.of(field)))).state
        assertEquals("create, update 0", events.joinToString(", "))
        assertEquals(1, st.field(field))
    }

    @Test
    fun appliesEffectsFromReconfiguringTransactionToNewFields() {
        var st = mk()
        val effect = StateEffect.define<Int>()
        val field = StateField.define(
            StateFieldSpec<Int>(
                create = { state ->
                    state.facet(num).firstOrNull() ?: 0
                },
                update = { value, tr ->
                    tr.effects.fold(value) { v, e ->
                        if (e.asType(effect) != null) v + (e as StateEffect<Int>).value else v
                    }
                }
            )
        )
        st = st.update(
            TransactionSpec(
                effects = listOf(
                    StateEffect.appendConfig.of(ExtensionList(listOf(field, num.of(10)))),
                    effect.of(5)
                )
            )
        ).state
        assertEquals(15, st.field(field))
    }

    @Test
    fun errorsOnCyclicDependencies() {
        assertFailsWith<IllegalStateException> {
            mk(
                num.compute(listOf(str.asSlot())) { s -> s.facet(str).size },
                str.compute(listOf(num.asSlot())) { s -> s.facet(num).joinToString(",") }
            )
        }
    }

    @Test
    fun updatesFacetsComputedFromStaticValuesOnReconfigure() {
        var st =
            mk(num.compute(listOf(str.asSlot())) { state -> state.facet(str).size }, str.of("A"))
        st = st.update(
            TransactionSpec(effects = listOf(StateEffect.appendConfig.of(str.of("B"))))
        ).state
        assertEquals("2", st.facet(num).joinToString(","))
        assertSame(
            st.facet(num),
            st.update(
                TransactionSpec(effects = listOf(StateEffect.appendConfig.of(bool.of(false))))
            ).state.facet(num)
        )
    }

    @Test
    fun preservesDynamicFacetValuesWhenDependenciesStayTheSame() {
        data class Wrapper(val a: Int)
        val f: Facet<Wrapper, List<Wrapper>> = Facet.define()
        val st1 = mk(f.compute(emptyList()) { Wrapper(a = 1) }, str.of("A"))
        val st2 = st1.update(
            TransactionSpec(effects = listOf(StateEffect.appendConfig.of(bool.of(true))))
        ).state
        assertSame(st1.facet(f), st2.facet(f))
    }
}
