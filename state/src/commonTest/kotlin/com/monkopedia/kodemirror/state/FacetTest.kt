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

import com.monkopedia.kodemirror.state.SingleOrList.Companion.list
import com.monkopedia.kodemirror.state.SingleOrList.Companion.single
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

fun mk(vararg extensions: Extension): EditorState =
    EditorState.create(extensions = extensions.toList().extension)

val num = Facet.define<Int>()
val str = Facet.define<String>()
val bool = Facet.define<Boolean>()

class FacetTest {

    @Test
    fun allows_querying_of_facets() {
        val st = mk(num.of(10), num.of(20), str.of("x"), str.of("y"))
        assertEquals("10,20", st.facet(num).joinToString(","))
        assertEquals("x,y", st.facet(str).joinToString(","))
    }

    @Test
    fun includes_sub_extenders() {
        // + operator for converting string to int
        val e = { s: String -> listOf(num.of(s.length), num.of(s.toInt())).extension }
        val st = mk(num.of(5), e("20"), num.of(40), e("100"))
        assertEquals("5,2,20,40,3,100", st.facet(num).joinToString(","))
    }

    @Test
    fun only_includes_duplicated_extensions_once() {
        val e = num.of(50)
        val st = mk(num.of(1), e, num.of(4), e)
        assertEquals("1,50,4", st.facet(num).joinToString(","))
    }

    @Test
    fun returns_an_empty_array_for_absent_facet() {
        val st = mk()
        assertEquals("[]", st.facet(num).toString())
    }

    @Test
    fun sorts_extensions_by_priority() {
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
    fun lets_sub_extensions_inherit_their_parents_priority() {
        val e = { n: Int -> num.of(n) }
        val st = mk(num.of(1), Prec.highest(e(2)), e(4))
        assertEquals("2,1,4", st.facet(num).joinToString(","))
    }

    @Test
    fun supports_dynamic_facet() {
        val st = mk(num.of(1), num.compute(emptyList()) { _ -> 88 })
        assertEquals("1,88", st.facet(num).joinToString(","))
    }

    @Test
    fun only_recomputes_a_facet_value_when_necessary() {
        val st = mk(
            num.of(1),
            num.compute(listOf(str)) { s -> s.facet(str).joinToString(",").length },
            str.of("hello")
        )
        val array = st.facet(num)
        assertEquals("1,5", array.joinToString(","))
        assertEquals(array, st.update().state.facet(num))
    }

    @Test
    fun can_handle_dependencies_on_facets_that_arent_present_in_the_state() {
        val st = mk(
            num.compute(listOf(str)) { s -> s.facet(str).joinToString("").length },
            str.compute(listOf(bool)) { s -> s.facet(bool).joinToString("") }
        )
        assertEquals("0", st.update().state.facet(num).joinToString(","))
    }

    @Test
    fun can_specify_a_dependency_on_the_document() {
        var count = 0
        var st = mk(num.compute(listOf(Slot.Doc)) { _ -> count++ })
        assertEquals("0", st.facet(num).joinToString(","))
        st = st.update(
            TransactionSpec(changes = ChangeSpecData(insert = "hello".asText, from = 0))
        ).state
        assertEquals("1", st.facet(num).joinToString(","))
        st = st.update().state
        assertEquals("1", st.facet(num).joinToString(","))
    }

    @Test
    fun can_specify_a_dependency_on_the_selection() {
        var count = 0
        var st = mk(num.compute(listOf(Slot.Selection), { _ -> count++ }))
        assertEquals("0", st.facet(num).joinToString(","))
        st = st.update(
            TransactionSpec(changes = ChangeSpecData(insert = "hello".asText, from = 0))
        ).state
        assertEquals("1", st.facet(num).joinToString(","))
        st = st.update(TransactionSpec(selection = Selection.Data(anchor = 2))).state
        assertEquals("2", st.facet(num).joinToString(","))
        st = st.update().state
        assertEquals("2", st.facet(num).joinToString(","))
    }

    @Test
    fun can_provide_multiple_values_at_once() {
        var st = mk(
            num.computeN(listOf(Slot.Doc)) { s ->
                if (s.doc.length and 1 != 0) listOf(100, 10) else emptyList()
            },
            num.of(1)
        )
        assertEquals("1", st.facet(num).joinToString(","))
        st = st.update(
            TransactionSpec(changes = ChangeSpecData(insert = "hello".asText, from = 0))
        ).state
        assertEquals("100,10,1", st.facet(num).joinToString(","))
    }

    @Test
    fun works_with_a_static_combined_facet() {
        val f = Facet.define<Int, Int>(FacetConfig(combine = { ns -> ns.sum() }))
        val st = mk(f.of(1), f.of(2), f.of(3))
        assertEquals(6, st.facet(f))
    }

    @Test
    fun works_with_a_dynamic_combined_facet() {
        val f = Facet.define<Int, Int>(FacetConfig(combine = { ns -> ns.sum() }))
        var st =
            mk(f.of(1), f.compute(listOf(Slot.Doc)) { s -> s.doc.length }, f.of(3))
        assertEquals(4, st.facet(f))
        st = st.update(
            TransactionSpec(changes = ChangeSpecData(insert = "hello".asText, from = 0))
        ).state
        assertEquals(9, st.facet(f))
    }

    @Test
    fun survives_reconfiguration() {
        val st = mk(num.compute(listOf(Slot.Doc)) { s -> s.doc.length }, num.of(2), str.of("3"))
        val st2 =
            st.update(
                TransactionSpec(
                    effects = StateEffect.reconfigure.of(
                        listOf(
                            num.compute(listOf(Slot.Doc)) { s -> s.doc.length },
                            num.of(2)
                        ).extension
                    ).single
                )
            ).state
        assertEquals(st2.facet(num), st.facet(num))
        assertEquals(0, st2.facet(str).size)
    }

    data class FacetType(val count: Int)

    @Test
    fun survives_unrelated_reconfiguration_even_without_deep_compare() {
        val f = Facet.define<Int, FacetType>(
            combine = { v -> FacetType(count = v.size) }
        )
        val st = mk(f.compute(listOf(Slot.Doc), { it.doc.length }), f.of(2))
        val st2 =
            st.update(
                TransactionSpec(effects = StateEffect.appendConfig.of(str.of("hi")).single)
            ).state
        assertEquals(st2.facet(f), st.facet(f))
    }

    @Test
    fun preserves_static_facets_across_reconfiguration() {
        val st = mk(num.of(1), num.of(2), str.of("3"))
        val st2 = st.update(
            TransactionSpec(
                effects = StateEffect.reconfigure.of(listOf(num.of(1), num.of(2)).extension)
                    .single
            )
        ).state
        assertEquals(st2.facet(num), st.facet(num))
    }

    @Test
    fun creates_newly_added_fields_when_reconfiguring() {
        var st = mk(num.of(2))
        val events = mutableListOf<String>()
        val field = StateField.define(
            StateFieldSpec(
                create = {
                    events.add("create")
                    0
                },
                update = { v: Int, _ ->
                    events.add("update $v")
                    v + 1
                }
            )
        )
        st = st.update(TransactionSpec(effects = StateEffect.appendConfig.of(field).single)).state
        assertEquals("create, update 0", events.joinToString(", "))
        assertEquals(1, st.field(field))
    }

    @Test
    fun applies_effects_from_reconfiguring_transaction_to_new_fields() {
        var st = mk()
        val effect = StateEffect.define<Int>()
        val field = StateField.define<Int>(
            create = {
                (it.facet(num).getOrNull(0) ?: 0).also {
                    println("CReate $it")
                }
            },
            update = { v, tr ->
                tr.effects.list.filter { it.isOf(effect) }
                    .onEach { println("Element $v ${it.value}") }
                    .fold(v) { v, e -> v + (e.value as Int) }
            }
        )
        st = st.update(
            TransactionSpec(
                effects = listOf(
                    StateEffect.appendConfig.of(listOf(field, num.of(10)).extension),
                    effect.of(5)
                ).list
            )
        ).state
        assertEquals(15, st.field(field))
    }

    @Test
    fun errors_on_cyclic_dependencies() {
        assertFails {
            mk(
                num.compute(listOf(str)) { s -> s.facet(str).size },
                str.compute(listOf(num)) { s -> s.facet(num).joinToString() }
            )
        }
    }

    @Test
    fun updates_facets_computed_from_static_values_on_reconfigure() {
        var st = mk(
            num.compute(listOf(str)) { state -> state.facet(str).size },
            str.of("A")
        )
        assertEquals("1", st.facet(num).joinToString(","))
        st = st.update(
            TransactionSpec(effects = StateEffect.appendConfig.of(str.of("B")).single)
        ).state
        assertEquals("2", st.facet(num).joinToString(","))
        assertEquals(
            st.update(TransactionSpec(effects = StateEffect.appendConfig.of(bool.of(false)).single))
                .state.facet(num),
            st.facet(num)
        )
    }

    data class FacetValue(val a: Int)

    @Test
    fun preserves_dynamic_facet_values_when_dependencies_stay_the_same() {
        val f = Facet.define<FacetValue>()
        val st1 = mk(f.compute(emptyList()) { state -> FacetValue(a = 1) }, str.of("A"))
        val st2 = st1.update(
            TransactionSpec(effects = StateEffect.appendConfig.of(bool.of(true)).single)
        ).state
        assertEquals(st2.facet(f), st1.facet(f))
    }
}
