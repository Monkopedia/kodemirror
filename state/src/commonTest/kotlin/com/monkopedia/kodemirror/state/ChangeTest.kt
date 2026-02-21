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

import com.monkopedia.kodemirror.state.Side.Companion.asSide
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

object ChangeTest {

    fun mk(spec: String): ChangeDesc {
        return ChangeDesc(
            spec.split(" ").filter { it.isNotBlank() }.map {
                if (it.contains(":")) {
                    val (a, b) = it.split(":")
                    ChangeSection(a.toInt(), b.toInt())
                } else {
                    ChangeSection(it.toInt())
                }
            }
        )
    }

    // ('r' for random)
    fun r(n: Int): Int {
        if (n <= 1) return 1
        return Random.nextInt(n - 1) + 1
    }

    fun rStr(l: Int): Text {
        return Text.of(listOf(CharArray(l) { (97 + r(26)).toChar() }.concatToString()))
    }

    fun rChange(len: Int): ChangeSpecData {
        if (len == 0 || r(3) == 0) return ChangeSpecData(from = r(len), insert = rStr(r(5) + 1))
        val from = r(len - 1)
        return ChangeSpecData(
            from,
            to = min(from + r(5) + 1, len),
            insert = if (r(2) == 0) rStr(r(2) + 1) else null
        )
    }

    fun rChanges(len: Int, count: Int): List<ChangeSpecData> {
        return List(count) { rChange(len) }
    }

    object ChangeDescTest {
        class composition {
            fun comp(vararg specs: String) {
                val result = specs.first()
                val combined = specs.drop(1).map(::mk).reduce { a, b -> a.composeDesc(b) }
                assertEquals(result, combined.toString())
            }

            @Test
            fun can_parse_identity() = comp("0:2", "0:2")

            @Test
            fun can_parse_identity_removal() = comp("2", "2")

            @Test
            fun can_parse_multiple() = comp("2:1 4:1 6", "2:1 4:1 6")

            @Test
            fun can_parse_multiple_removal() = comp("2 4 6", "2 4 6")

            @Test
            fun can_compose_unrelated_changes() = comp("1 2:0 2 0:2", "5 0:2", "1 2:0 4")

            @Test
            fun cancels_insertions_with_deletions() = comp("4", "2 0:2 2", "2 2:0 2")

            @Test
            fun joins_adjacent_insertions() = comp("2 0:5 2", "2 0:2 2", "4 0:3 2")

            @Test
            fun joins_adjacent_deletions() = comp("1 6:0", "2 5:0", "1 1:0")

            @Test
            fun allows_a_delete_to_shadow_multiple_operations() = comp("4:0", "2 2:0 0:3", "5:0")

            @Test
            fun can_handle_empty_sets() = comp("", "", "0:8", "8:0", "")

            @Test
            fun can_join_multiple_replaces() {
                comp("1 6:6 1", "2 2:2 2:2 2", "1 2:2 2:2 2:2 1")
                comp("1 6:6 1", "1 2:2 2:2 2:2 1", "2 2:2 2:2 2")
                comp("1 5:3 1", "1 2:3 3:2 1", "2 3:1 2")
            }

            @Test
            fun throws_for_inconsistent_lengths() {
                assertFails { mk("2 0:2").composeDesc(mk("1 0:1")) }
                assertFails { mk("2 0:2").composeDesc(mk("30 0:1")) }
                assertFails { mk("2 2:0 0:3").composeDesc(mk("7:0")) }
            }
        }

        class mapping {
            fun over(a: String, b: String, result: String) {
                assertEquals(result, mk(a).mapDesc(mk(b)).toString())
            }

            fun under(a: String, b: String, result: String) {
                assertEquals(result, mk(a).mapDesc(mk(b), true).toString())
            }

            @Test
            fun can_map_over_an_insertion() = over("4 0:1", "0:3 4", "7 0:1")

            @Test
            fun can_map_over_a_deletion() = over("4 0:1", "2:0 2", "2 0:1")

            @Test
            fun orders_insertions() {
                over("2 0:1 2", "2 0:1 2", "3 0:1 2")
                under("2 0:1 2", "2 0:1 2", "2 0:1 3")
            }

            @Test
            fun can_map_a_deletion_over_an_overlapping_replace() {
                over("2 2:0", "2 1:2 1", "4 1:0")
                under("2 2:0", "2 1:2 1", "4 1:0")
            }

            @Test
            fun can_handle_changes_after() {
                over("0:1 2:0 8", "6 1:0 0:5 3", "0:1 2:0 12")
            }

            @Test
            fun joins_deletions() {
                over("5:0 2 3:0 2", "4 4:0 4", "6:0 2")
            }

            @Test
            fun keeps_insertions_in_deletions() {
                under("2 0:1 2", "4:0", "0:1")
                over("4 0:1 4", "2 4:0 2", "2 0:1 2")
            }

            @Test
            fun keeps_replacements() {
                over("2 2:2 2", "0:2 6", "4 2:2 2")
                over("2 2:2 2", "3:0 3", "1:2 2")
                over("1 4:4 1", "3 0:2 3", "1 2:4 2 2:0 1")
                over("1 4:4 1", "2 2:0 2", "1 2:4 1")
                over("2 2:2 2", "3 2:0 1", "2 1:2 1")
            }

            @Test
            fun doesnt_join_replacements() {
                over("2:2 2 2:2", "2 2:0 2", "2:2 2:2")
            }

            @Test
            fun drops_duplicate_deletion() {
                under("2 2:0 2", "2 2:0 2", "4")
                over("2 2:0 2", "2 2:0 2", "4")
            }

            @Test
            fun handles_overlapping_replaces() {
                over("1 1:2 1", "1 1:1 1", "2 0:2 1")
                under("1 1:2 1", "1 1:1 1", "1 0:2 2")
                over("1 1:2 2", "1 2:1 1", "1 0:2 2")
                over("2 1:2 1", "1 2:1 1", "2 0:2 1")
                over("2:1 1", "1 2:2", "1:1 2")
                over("1 2:1", "2:2 1", "2 1:1")
            }
        }

        class mapPos {
            fun map(spec: String, vararg cases: List<Any?>) {
                val set = mk(spec)
                for (r in cases) {
                    val from = r[0] as Int
                    val to = r[1] as Int?
                    val opt = r.getOrNull(2)
                    val assoc = (if (opt is Int) opt else -1).asSide
                    val mode = when (opt) {
                        "D" -> MapMode.TrackDel
                        "A" -> MapMode.TrackAfter
                        "B" -> MapMode.TrackBefore
                        else -> null
                    }
                    assertEquals(to, set.mapPos(from, assoc, mode ?: MapMode.Simple))
                }
            }

            @Test
            fun maps_through_an_insertion() {
                map(
                    "4 0:2 4",
                    listOf(0, 0),
                    listOf(4, 4),
                    listOf(4, 6, 1),
                    listOf(5, 7),
                    listOf(8, 10)
                )
            }

            @Test
            fun maps_through_deletion() {
                map(
                    "4 4:0 4",
                    listOf(0, 0),
                    listOf(4, 4),
                    listOf(4, 4, "D"),
                    listOf(4, 4, "B"),
                    listOf(4, null, "A"),
                    listOf(5, 4),
                    listOf(5, null, "D"),
                    listOf(5, null, "B"),
                    listOf(5, null, "A"),
                    listOf(7, 4),
                    listOf(8, 4),
                    listOf(8, 4, "D"),
                    listOf(8, null, "B"),
                    listOf(8, 4, "A"),
                    listOf(9, 5),
                    listOf(12, 8)
                )
            }

            @Test
            fun maps_through_multiple_insertions() {
                map(
                    "0:2 2 0:2 2 0:2",
                    listOf(0, 0),
                    listOf(0, 2, 1),
                    listOf(1, 3),
                    listOf(2, 4),
                    listOf(2, 6, 1),
                    listOf(3, 7),
                    listOf(4, 8),
                    listOf(4, 10, 1)
                )
            }

            @Test
            fun maps_through_multiple_deletions() {
                map(
                    "2:0 2 2:0 2 2:0",
                    listOf(0, 0),
                    listOf(1, 0),
                    listOf(2, 0),
                    listOf(3, 1),
                    listOf(4, 2),
                    listOf(5, 2),
                    listOf(6, 2),
                    listOf(7, 3),
                    listOf(8, 4),
                    listOf(9, 4),
                    listOf(10, 4)
                )
            }

            @Test
            fun maps_through_mixed_edits() {
                map(
                    "2 0:2 2:0 0:2 2 2:0 0:2",
                    listOf(0, 0),
                    listOf(2, 2),
                    listOf(2, 4, 1),
                    listOf(3, 4),
                    listOf(4, 4),
                    listOf(4, 6, 1),
                    listOf(5, 7),
                    listOf(6, 8),
                    listOf(7, 8),
                    listOf(8, 8),
                    listOf(8, 10, 1)
                )
            }

            @Test
            fun stays_on_its_own_side_of_replacements() {
                map(
                    "2 2:2 2",
                    listOf(2, 2, 1),
                    listOf(2, 2, -1),
                    listOf(2, 2, "D"),
                    listOf(2, 2, "B"),
                    listOf(2, null, "A"),
                    listOf(3, 2, -1),
                    listOf(3, 4, 1),
                    listOf(3, null, "D"),
                    listOf(3, null, "B"),
                    listOf(3, null, "A"),
                    listOf(4, 4, 1),
                    listOf(4, 4, -1),
                    listOf(4, 4, "D"),
                    listOf(4, null, "B"),
                    listOf(4, 4, "A")
                )
            }

            @Test
            fun maps_through_insertions_around_replacements() {
                map(
                    "0:1 2:2 0:1",
                    listOf(0, 0, -1),
                    listOf(0, 1, 1),
                    listOf(1, 1, -1),
                    listOf(1, 3, 1),
                    listOf(2, 3, -1),
                    listOf(2, 4, 1)
                )
            }

            @Test
            fun stays_in_between_replacements() {
                map(
                    "2:2 2:2",
                    listOf(2, 2, -1),
                    listOf(2, 2, 1)
                )
            }
        }

        class change_set {
            @Test
            fun can_create_change_sets() {
                assertEquals(
                    "5 0:2 5",
                    ChangeSet.of(
                        listOf(ChangeSpecData(from = 5, insert = "hi".asText)).asSpec,
                        10
                    ).desc.toString()
                )
                assertEquals(
                    "5 2:0 3",
                    ChangeSet.of(
                        listOf(ChangeSpecData(from = 5, to = 7)).asSpec,
                        10
                    ).desc.toString()
                )
                assertEquals(
                    "3:0 1 1:0 0:4 1:0 2 0:3 2",
                    ChangeSet.of(
                        listOf(
                            ChangeSpecData(from = 5, insert = "hi".asText),
                            ChangeSpecData(from = 5, insert = "ok".asText),
                            ChangeSpecData(from = 0, to = 3),
                            ChangeSpecData(from = 4, to = 6),
                            ChangeSpecData(from = 8, insert = "boo".asText)
                        ).asSpec,
                        10
                    ).desc.toString()
                )
            }

            val doc10 = Text.of(listOf("0123456789"))

            @Test
            fun can_apply_change_sets() {
                assertEquals(
                    "01ok23456789",
                    ChangeSet.of(listOf(ChangeSpecData(from = 2, insert = "ok".asText)).asSpec, 10)
                        .apply(doc10)
                        .toString()
                )
                assertEquals(
                    "09",
                    ChangeSet.of(listOf(ChangeSpecData(from = 1, to = 9)).asSpec, 10).apply(doc10)
                        .toString()
                )
                assertEquals(
                    "0hi189",
                    ChangeSet.of(
                        listOf(
                            ChangeSpecData(from = 2, to = 8),
                            ChangeSpecData(from = 1, insert = "hi".asText)
                        ).asSpec,
                        10
                    )
                        .apply(doc10)
                        .toString()
                )
            }

            @Test
            fun can_apply_composed_sets() {
                assertEquals(
                    "01234567D89",
                    ChangeSet.of(
                        listOf(ChangeSpecData(from = 8, insert = "ABCD".asText)).asSpec,
                        10
                    )
                        .compose(ChangeSet.of(listOf(ChangeSpecData(from = 8, to = 11)).asSpec, 14))
                        .apply(doc10).toString()
                )
                assertEquals(
                    "01hi!2367ok?89",
                    ChangeSet.of(
                        listOf(
                            ChangeSpecData(from = 2, insert = "hi".asText),
                            ChangeSpecData(from = 8, insert = "ok".asText)
                        ).asSpec,
                        10
                    )
                        .compose(
                            ChangeSet.of(
                                listOf(
                                    ChangeSpecData(from = 4, insert = "!".asText),
                                    ChangeSpecData(from = 6, to = 8),
                                    ChangeSpecData(from = 12, insert = "?".asText)
                                ).asSpec,
                                14
                            )
                        )
                        .apply(doc10).toString()
                )
            }

            @Test
            fun can_clip_inserted_strings_on_compose() {
                assertEquals(
                    "01abef456789",
                    ChangeSet.of(
                        listOf(
                            ChangeSpecData(from = 2, insert = "abc".asText),
                            ChangeSpecData(from = 4, insert = "def".asText)
                        ).asSpec,
                        10
                    )
                        .compose(ChangeSet.of(listOf(ChangeSpecData(from = 4, to = 8)).asSpec, 16))
                        .apply(doc10).toString()
                )
            }

            @Test
            fun can_apply_mapped_sets() {
                val set0 =
                    ChangeSet.of(
                        listOf(
                            ChangeSpecData(from = 5, insert = "hi".asText),
                            ChangeSpecData(from = 8, to = 10)
                        ).asSpec,
                        10
                    )
                val set1 =
                    ChangeSet.of(
                        listOf(
                            ChangeSpecData(from = 10, insert = "ok".asText),
                            ChangeSpecData(from = 6, to = 7)
                        ).asSpec,
                        10
                    )
                assertEquals(
                    "01234hi57ok",
                    set0.compose(set1.map(set0)).apply(doc10).toString()
                )
            }

            @Test
            fun can_apply_inverted_sets() {
                val set0 =
                    ChangeSet.of(
                        listOf(
                            ChangeSpecData(from = 5, insert = "hi".asText),
                            ChangeSpecData(from = 8, to = 10)
                        ).asSpec,
                        10
                    )
                assertEquals(
                    doc10.toString(),
                    set0.invert(doc10).apply(set0.apply(doc10)).toString()
                )
            }

            @Test
            fun can_be_iterated() {
                val set =
                    ChangeSet.of(
                        listOf(
                            ChangeSpecData(from = 4, insert = "ok".asText),
                            ChangeSpecData(from = 6, to = 8)
                        ).asSpec,
                        10
                    )
                var result = mutableListOf<List<Any>>()
                set.iterChanges({ fromA, toA, fromB, toB, inserted ->
                    result.add(listOf(fromA, toA, fromB, toB, inserted.toString()))
                })
                assertEquals(
                    listOf<List<Any>>(listOf(4, 4, 4, 6, "ok"), listOf(6, 8, 8, 8, "")),
                    result
                )
                result = mutableListOf()
                set.iterGaps({ fromA, toA, len -> result.add(listOf(fromA, toA, len)) })
                assertEquals(
                    listOf<List<Any>>(listOf(0, 0, 4), listOf(4, 6, 2), listOf(8, 8, 2)),
                    result
                )
            }

            @Test
            fun mapping_before_produces_the_same_result_as_mapping_the_other_after() {
                val total = 100
                for (i in 0 until total) {
                    val size = r(20)
                    val count = (i / (total / 10.0)).roundToInt() + 1
                    val a = rChanges(size, count).asSpec
                    val b = rChanges(size, count).asSpec
                    try {
                        val setA = ChangeSet.of(a, size)
                        val setB = ChangeSet.of(b, size)
                        val setA1 = setA.map(setB, true)
                        val setB1 = setB.map(setA, false)
                        val doc = rStr(size)
                        val setAB = setA.compose(setB1)
                        val setBA = setB.compose(setA1)
                        assertEquals(setAB.apply(doc).toString(), setBA.apply(doc).toString())
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        throw e
                    }
                }
            }

            @Test
            fun mapping_still_converges_when_mapping_through_multiple_changes() {
                val total = 100
                for (i in 0 until total) {
                    val size = r(20)
                    val count = (i / (total / 10.0)).roundToInt() + 1
                    val a = ChangeSet.of(rChanges(size, count).asSpec, size)
                    val b = ChangeSet.of(rChanges(a.newLength, count).asSpec, a.newLength)
                    val c = ChangeSet.of(rChanges(size, count).asSpec, size)
                    val c_a = c.map(a)
                    val c_ab = c_a.map(b)
                    val a_c = a.map(c, true)
                    val b_ca = b.map(c_a, true)
                    val doc = rStr(size)
                    assertEquals(
                        a.compose(b).compose(c_ab).apply(doc).toString(),
                        c.compose(a_c).compose(b_ca).apply(doc).toString()
                    )
                }
            }

            @Test
            fun compose_produces_the_same_result_as_individual_changes() {
                for (i in 0 until 100) {
                    val size = r(20)
                    val doc = rStr(size)
                    val a = ChangeSet.of(rChanges(size, r(5) + 1).asSpec, size)
                    val b = ChangeSet.of(rChanges(a.newLength, r(6)).asSpec, a.newLength)
                    assertEquals(
                        b.apply(a.apply(doc)).toString(),
                        a.compose(b).apply(doc).toString()
                    )
                }
            }

            @Test
            fun composing_is_associative() {
                for (i in 0 until 100) {
                    val size = r(20)
                    val doc = rStr(size)
                    val a = ChangeSet.of(rChanges(size, r(5) + 1).asSpec, size)
                    val b = ChangeSet.of(rChanges(a.newLength, r(6)).asSpec, a.newLength)
                    val c = ChangeSet.of(rChanges(b.newLength, r(5) + 1).asSpec, b.newLength)
                    val left = a.compose(b).compose(c)
                    val right = a.compose(b.compose(c))
                    assertEquals(left.apply(doc).toString(), right.apply(doc).toString())
                }
            }

            @Test
            fun survives_random_sequences_of_changes() {
                for (i in 0 until 50) {
                    var doc = doc10
                    var txt = doc.toString()
                    val all = mutableListOf<ChangeSet>()
                    val inv = mutableListOf<ChangeSet>()
                    val log = mutableListOf<Any>()
                    try {
                        for (j in 0 until 50) {
                            val set: ChangeSet
                            val change = rChange(doc.length)
                            log.add("ChangeSet.of([$change], ${doc.length})")
                            val from = change.from
                            val to = change.to ?: from
                            val insert = change.insert ?: ""
                            txt = txt.substring(0, from) + insert + txt.substring(to)
                            set = ChangeSet.of(listOf(change).asSpec, doc.length)
                            all.add(set)
                            inv.add(set.invert(doc))
                            doc = set.apply(doc)
                            assertEquals(doc.toString(), txt)
                        }
                        val composed = all.fold(
                            ChangeSet.of(listOf<ChangeSpec>().asSpec, doc10.length)
                        ) { a, b ->
                            a.compose(b)
                        }

                        assertEquals(composed.apply(doc10).toString(), txt)
                        assertEquals(composed.invert(doc10).apply(doc).toString(), doc10.toString())
                        for (i in inv.indices.reversed()) doc = inv[i].apply(doc)
                        assertEquals(doc.toString(), doc10.toString())
                    } catch (e: Throwable) {
                        println("With changes: ${log.joinToString(", ")}")
                        throw e
                    }
                }
            }

//            fun can_be_serialized_to_JSON() {
//                for (i in 0 until 100) {
//                    val size = r(20) + 1
//                    val set = ChangeSet.of(rChanges(size, r(4)).asSpec, size)
//                    assertEquals(String(ChangeSet.fromJSON(set.toJSON())), String(set))
//                }
//            }
        }
    }
}
