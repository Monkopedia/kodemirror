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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private fun mk(spec: String): ChangeDesc {
    val sections = mutableListOf<Int>()
    var s = spec
    while (s.isNotEmpty()) {
        val next = Regex("^(\\d+)(?::(\\d+))?\\s*").find(s)!!
        s = s.substring(next.value.length)
        sections.add(next.groupValues[1].toInt())
        sections.add(if (next.groupValues[2].isEmpty()) -1 else next.groupValues[2].toInt())
    }
    return ChangeDesc(sections.toIntArray())
}

private fun r(n: Int): Int = Random.nextInt(n)

private fun rStr(l: Int): String {
    val sb = StringBuilder()
    for (i in 0 until l) sb.append(('a' + r(26)))
    return sb.toString()
}

private fun rChange(len: Int): ChangeSpec.Single {
    if (len == 0 || r(3) == 0) {
        return ChangeSpec.Single(
            from = r(len.coerceAtLeast(1)),
            insert = InsertContent.StringContent(rStr(r(5) + 1))
        )
    }
    val from = r(len - 1)
    val to = minOf(from + r(5) + 1, len)
    val insert = if (r(2) == 0) InsertContent.StringContent(rStr(r(2) + 1)) else null
    return ChangeSpec.Single(from = from, to = to, insert = insert)
}

private fun rChanges(len: Int, count: Int): List<ChangeSpec.Single> {
    val result = mutableListOf<ChangeSpec.Single>()
    for (i in 0 until count) result.add(rChange(len))
    return result
}

private fun changeSpecOf(changes: List<ChangeSpec.Single>): ChangeSpec.Multi =
    ChangeSpec.Multi(changes)

class ChangeDescCompositionTest {

    private fun comp(vararg specs: String) {
        val result = specs.last()
        val sets = specs.dropLast(1).map { mk(it) }
        assertEquals(result, sets.reduce { a, b -> a.composeDesc(b) }.toString())
    }

    @Test
    fun canComposeUnrelatedChanges() =
        comp("5 0:2", "1 2:0 4", "1 2:0 2 0:2")

    @Test
    fun cancelsInsertionsWithDeletions() =
        comp("2 0:2 2", "2 2:0 2", "4")

    @Test
    fun joinsAdjacentInsertions() =
        comp("2 0:2 2", "4 0:3 2", "2 0:5 2")

    @Test
    fun joinsAdjacentDeletions() =
        comp("2 5:0", "1 1:0", "1 6:0")

    @Test
    fun allowsADeleteToShadowMultipleOperations() =
        comp("2 2:0 0:3", "5:0", "4:0")

    @Test
    fun canHandleEmptySets() =
        comp("", "0:8", "8:0", "", "")

    @Test
    fun canJoinMultipleReplaces() {
        comp("2 2:2 2:2 2", "1 2:2 2:2 2:2 1", "1 6:6 1")
        comp("1 2:2 2:2 2:2 1", "2 2:2 2:2 2", "1 6:6 1")
        comp("1 2:3 3:2 1", "2 3:1 2", "1 5:3 1")
    }

    @Test
    fun throwsForInconsistentLengths() {
        assertFailsWith<Exception> { mk("2 0:2").composeDesc(mk("1 0:1")) }
        assertFailsWith<Exception> { mk("2 0:2").composeDesc(mk("30 0:1")) }
        assertFailsWith<Exception> { mk("2 2:0 0:3").composeDesc(mk("7:0")) }
    }
}

class ChangeDescMappingTest {

    private fun over(a: String, b: String, result: String) {
        assertEquals(result, mk(a).mapDesc(mk(b)).toString())
    }

    private fun under(a: String, b: String, result: String) {
        assertEquals(result, mk(a).mapDesc(mk(b), true).toString())
    }

    @Test
    fun canMapOverAnInsertion() =
        over("4 0:1", "0:3 4", "7 0:1")

    @Test
    fun canMapOverADeletion() =
        over("4 0:1", "2:0 2", "2 0:1")

    @Test
    fun ordersInsertions() {
        over("2 0:1 2", "2 0:1 2", "3 0:1 2")
        under("2 0:1 2", "2 0:1 2", "2 0:1 3")
    }

    @Test
    fun canMapADeletionOverAnOverlappingReplace() {
        over("2 2:0", "2 1:2 1", "4 1:0")
        under("2 2:0", "2 1:2 1", "4 1:0")
    }

    @Test
    fun canHandleChangesAfter() =
        over("0:1 2:0 8", "6 1:0 0:5 3", "0:1 2:0 12")

    @Test
    fun joinsDeletions() =
        over("5:0 2 3:0 2", "4 4:0 4", "6:0 2")

    @Test
    fun keepsInsertionsInDeletions() {
        under("2 0:1 2", "4:0", "0:1")
        over("4 0:1 4", "2 4:0 2", "2 0:1 2")
    }

    @Test
    fun keepsReplacements() {
        over("2 2:2 2", "0:2 6", "4 2:2 2")
        over("2 2:2 2", "3:0 3", "1:2 2")
        over("1 4:4 1", "3 0:2 3", "1 2:4 2 2:0 1")
        over("1 4:4 1", "2 2:0 2", "1 2:4 1")
        over("2 2:2 2", "3 2:0 1", "2 1:2 1")
    }

    @Test
    fun doesntJoinReplacements() {
        over("2:2 2 2:2", "2 2:0 2", "2:2 2:2")
    }

    @Test
    fun dropsDuplicateDeletion() {
        under("2 2:0 2", "2 2:0 2", "4")
        over("2 2:0 2", "2 2:0 2", "4")
    }

    @Test
    fun handlesOverlappingReplaces() {
        over("1 1:2 1", "1 1:1 1", "2 0:2 1")
        under("1 1:2 1", "1 1:1 1", "1 0:2 2")
        over("1 1:2 2", "1 2:1 1", "1 0:2 2")
        over("2 1:2 1", "1 2:1 1", "2 0:2 1")
        over("2:1 1", "1 2:2", "1:1 2")
        over("1 2:1", "2:2 1", "2 1:1")
    }
}

class ChangeDescMapPosTest {

    private fun map(spec: String, vararg cases: Triple<Int, Int?, Any?>) {
        val set = mk(spec)
        for ((from, to, opt) in cases) {
            val assoc = if (opt is Int) opt else -1
            val mode = when (opt) {
                "D" -> MapMode.TrackDel
                "A" -> MapMode.TrackAfter
                "B" -> MapMode.TrackBefore
                else -> MapMode.Simple
            }
            if (mode == MapMode.Simple) {
                assertEquals(to, set.mapPos(from, assoc))
            } else {
                val result = set.mapPos(from, assoc, mode)
                if (to == null) {
                    assertNull(result)
                } else {
                    assertEquals(to, result)
                }
            }
        }
    }

    @Test
    fun mapsThroughAnInsertion() =
        map(
            "4 0:2 4",
            Triple(0, 0, null), Triple(4, 4, null), Triple(4, 6, 1),
            Triple(5, 7, null), Triple(8, 10, null)
        )

    @Test
    fun mapsThroughDeletion() =
        map(
            "4 4:0 4",
            Triple(0, 0, null),
            Triple(4, 4, null), Triple(4, 4, "D"), Triple(4, 4, "B"), Triple(4, null, "A"),
            Triple(5, 4, null), Triple(5, null, "D"), Triple(5, null, "B"), Triple(5, null, "A"),
            Triple(7, 4, null),
            Triple(8, 4, null), Triple(8, 4, "D"), Triple(8, null, "B"), Triple(8, 4, "A"),
            Triple(9, 5, null), Triple(12, 8, null)
        )

    @Test
    fun mapsThroughMultipleInsertions() =
        map(
            "0:2 2 0:2 2 0:2",
            Triple(0, 0, null), Triple(0, 2, 1), Triple(1, 3, null), Triple(2, 4, null),
            Triple(2, 6, 1), Triple(3, 7, null), Triple(4, 8, null), Triple(4, 10, 1)
        )

    @Test
    fun mapsThroughMultipleDeletions() =
        map(
            "2:0 2 2:0 2 2:0",
            Triple(0, 0, null), Triple(1, 0, null), Triple(2, 0, null), Triple(3, 1, null),
            Triple(4, 2, null), Triple(5, 2, null), Triple(6, 2, null), Triple(7, 3, null),
            Triple(8, 4, null), Triple(9, 4, null), Triple(10, 4, null)
        )

    @Test
    fun mapsThroughMixedEdits() =
        map(
            "2 0:2 2:0 0:2 2 2:0 0:2",
            Triple(0, 0, null), Triple(2, 2, null), Triple(2, 4, 1), Triple(3, 4, null),
            Triple(4, 4, null), Triple(4, 6, 1), Triple(5, 7, null), Triple(6, 8, null),
            Triple(7, 8, null), Triple(8, 8, null), Triple(8, 10, 1)
        )

    @Test
    fun staysOnItsOwnSideOfReplacements() =
        map(
            "2 2:2 2",
            Triple(2, 2, 1), Triple(2, 2, -1), Triple(2, 2, "D"), Triple(2, 2, "B"),
            Triple(2, null, "A"),
            Triple(3, 2, -1), Triple(3, 4, 1), Triple(3, null, "D"), Triple(3, null, "B"),
            Triple(3, null, "A"),
            Triple(4, 4, 1), Triple(4, 4, -1), Triple(4, 4, "D"), Triple(4, null, "B"),
            Triple(4, 4, "A")
        )

    @Test
    fun mapsThroughInsertionsAroundReplacements() =
        map(
            "0:1 2:2 0:1",
            Triple(0, 0, -1), Triple(0, 1, 1),
            Triple(1, 1, -1), Triple(1, 3, 1),
            Triple(2, 3, -1), Triple(2, 4, 1)
        )

    @Test
    fun staysInBetweenReplacements() =
        map("2:2 2:2", Triple(2, 2, -1), Triple(2, 2, 1))
}

class ChangeSetTest {

    @Test
    fun canCreateChangeSets() {
        assertEquals(
            "5 0:2 5",
            ChangeSet.of(
                ChangeSpec.Multi(
                    listOf(ChangeSpec.Single(from = 5, insert = InsertContent.StringContent("hi")))
                ),
                10
            ).desc.toString()
        )
        assertEquals(
            "5 2:0 3",
            ChangeSet.of(
                ChangeSpec.Multi(
                    listOf(ChangeSpec.Single(from = 5, to = 7))
                ),
                10
            ).desc.toString()
        )
        assertEquals(
            "3:0 1 1:0 0:4 1:0 2 0:3 2",
            ChangeSet.of(
                ChangeSpec.Multi(
                    listOf(
                        ChangeSpec.Single(from = 5, insert = InsertContent.StringContent("hi")),
                        ChangeSpec.Single(from = 5, insert = InsertContent.StringContent("ok")),
                        ChangeSpec.Single(from = 0, to = 3),
                        ChangeSpec.Single(from = 4, to = 6),
                        ChangeSpec.Single(from = 8, insert = InsertContent.StringContent("boo"))
                    )
                ),
                10
            ).desc.toString()
        )
    }

    private val doc10 = Text.of(listOf("0123456789"))

    @Test
    fun canApplyChangeSets() {
        assertEquals(
            "01ok23456789",
            ChangeSet.of(
                ChangeSpec.Multi(
                    listOf(ChangeSpec.Single(from = 2, insert = InsertContent.StringContent("ok")))
                ),
                10
            ).apply(doc10).toString()
        )
        assertEquals(
            "09",
            ChangeSet.of(
                ChangeSpec.Multi(listOf(ChangeSpec.Single(from = 1, to = 9))),
                10
            ).apply(doc10).toString()
        )
        assertEquals(
            "0hi189",
            ChangeSet.of(
                ChangeSpec.Multi(
                    listOf(
                        ChangeSpec.Single(from = 2, to = 8),
                        ChangeSpec.Single(from = 1, insert = InsertContent.StringContent("hi"))
                    )
                ),
                10
            ).apply(doc10).toString()
        )
    }

    @Test
    fun canApplyComposedSets() {
        assertEquals(
            "01234567D89",
            ChangeSet.of(
                ChangeSpec.Multi(
                    listOf(
                        ChangeSpec.Single(
                            from = 8,
                            insert = InsertContent.StringContent("ABCD")
                        )
                    )
                ),
                10
            ).compose(
                ChangeSet.of(
                    ChangeSpec.Multi(listOf(ChangeSpec.Single(from = 8, to = 11))),
                    14
                )
            ).apply(doc10).toString()
        )
        assertEquals(
            "01hi!2367ok?89",
            ChangeSet.of(
                ChangeSpec.Multi(
                    listOf(
                        ChangeSpec.Single(from = 2, insert = InsertContent.StringContent("hi")),
                        ChangeSpec.Single(from = 8, insert = InsertContent.StringContent("ok"))
                    )
                ),
                10
            ).compose(
                ChangeSet.of(
                    ChangeSpec.Multi(
                        listOf(
                            ChangeSpec.Single(
                                from = 4,
                                insert = InsertContent.StringContent("!")
                            ),
                            ChangeSpec.Single(from = 6, to = 8),
                            ChangeSpec.Single(
                                from = 12,
                                insert = InsertContent.StringContent("?")
                            )
                        )
                    ),
                    14
                )
            ).apply(doc10).toString()
        )
    }

    @Test
    fun canClipInsertedStringsOnCompose() {
        assertEquals(
            "01abef456789",
            ChangeSet.of(
                ChangeSpec.Multi(
                    listOf(
                        ChangeSpec.Single(
                            from = 2,
                            insert = InsertContent.StringContent("abc")
                        ),
                        ChangeSpec.Single(
                            from = 4,
                            insert = InsertContent.StringContent("def")
                        )
                    )
                ),
                10
            ).compose(
                ChangeSet.of(
                    ChangeSpec.Multi(listOf(ChangeSpec.Single(from = 4, to = 8))),
                    16
                )
            ).apply(doc10).toString()
        )
    }

    @Test
    fun canApplyMappedSets() {
        val set0 = ChangeSet.of(
            ChangeSpec.Multi(
                listOf(
                    ChangeSpec.Single(from = 5, insert = InsertContent.StringContent("hi")),
                    ChangeSpec.Single(from = 8, to = 10)
                )
            ),
            10
        )
        val set1 = ChangeSet.of(
            ChangeSpec.Multi(
                listOf(
                    ChangeSpec.Single(from = 10, insert = InsertContent.StringContent("ok")),
                    ChangeSpec.Single(from = 6, to = 7)
                )
            ),
            10
        )
        assertEquals(
            "01234hi57ok",
            set0.compose(set1.map(set0)).apply(doc10).toString()
        )
    }

    @Test
    fun canApplyInvertedSets() {
        val set0 = ChangeSet.of(
            ChangeSpec.Multi(
                listOf(
                    ChangeSpec.Single(from = 5, insert = InsertContent.StringContent("hi")),
                    ChangeSpec.Single(from = 8, to = 10)
                )
            ),
            10
        )
        assertEquals(
            doc10.toString(),
            set0.invert(doc10).apply(set0.apply(doc10)).toString()
        )
    }

    @Test
    fun canBeIterated() {
        val set = ChangeSet.of(
            ChangeSpec.Multi(
                listOf(
                    ChangeSpec.Single(from = 4, insert = InsertContent.StringContent("ok")),
                    ChangeSpec.Single(from = 6, to = 8)
                )
            ),
            10
        )
        val result = mutableListOf<List<Any>>()
        set.iterChanges(
            { fromA, toA, fromB, toB, inserted ->
                result.add(listOf(fromA, toA, fromB, toB, inserted.toString()))
            }
        )
        assertEquals(
            listOf(listOf<Any>(4, 4, 4, 6, "ok"), listOf<Any>(6, 8, 8, 8, "")),
            result
        )
        val gapResult = mutableListOf<List<Int>>()
        set.iterGaps { fromA, toA, len -> gapResult.add(listOf(fromA, toA, len)) }
        assertEquals(
            listOf(listOf(0, 0, 4), listOf(4, 6, 2), listOf(8, 8, 2)),
            gapResult
        )
    }

    @Test
    fun mappingBeforeProducesTheSameResultAsMappingTheOtherAfter() {
        val total = 100
        for (i in 0 until total) {
            val size = r(20)
            val count = i / (total / 10) + 1
            val a = rChanges(size, count)
            val b = rChanges(size, count)
            try {
                val setA = ChangeSet.of(changeSpecOf(a), size)
                val setB = ChangeSet.of(changeSpecOf(b), size)
                val setA1 = setA.map(setB, true)
                val setB1 = setB.map(setA, false)
                val doc = Text.of(listOf(rStr(size)))
                val setAB = setA.compose(setB1)
                val setBA = setB.compose(setA1)
                assertEquals(setAB.apply(doc).toString(), setBA.apply(doc).toString())
            } catch (e: Exception) {
                println("size = $size\na = $a\nb = $b")
                throw e
            }
        }
    }

    @Test
    fun mappingStillConvergesWhenMappingThroughMultipleChanges() {
        val total = 100
        for (i in 0 until total) {
            val size = r(20)
            val count = i / (total / 10) + 1
            val a = ChangeSet.of(changeSpecOf(rChanges(size, count)), size)
            val b = ChangeSet.of(changeSpecOf(rChanges(a.newLength, count)), a.newLength)
            val c = ChangeSet.of(changeSpecOf(rChanges(size, count)), size)
            val cA = c.map(a)
            val cAb = cA.map(b)
            val aC = a.map(c, true)
            val bCa = b.map(cA, true)
            val doc = Text.of(listOf(rStr(size)))
            assertEquals(
                a.compose(b).compose(cAb).apply(doc).toString(),
                c.compose(aC).compose(bCa).apply(doc).toString()
            )
        }
    }

    @Test
    fun composeProducesTheSameResultAsIndividualChanges() {
        for (i in 0 until 100) {
            val size = r(20)
            val doc = Text.of(listOf(rStr(size)))
            val a = ChangeSet.of(changeSpecOf(rChanges(size, r(5) + 1)), size)
            val b = ChangeSet.of(changeSpecOf(rChanges(a.newLength, r(6))), a.newLength)
            assertEquals(
                b.apply(a.apply(doc)).toString(),
                a.compose(b).apply(doc).toString()
            )
        }
    }

    @Test
    fun composingIsAssociative() {
        for (i in 0 until 100) {
            val size = r(20)
            val doc = Text.of(listOf(rStr(size)))
            val a = ChangeSet.of(changeSpecOf(rChanges(size, r(5) + 1)), size)
            val b = ChangeSet.of(changeSpecOf(rChanges(a.newLength, r(6))), a.newLength)
            val c = ChangeSet.of(changeSpecOf(rChanges(b.newLength, r(5) + 1)), b.newLength)
            val left = a.compose(b).compose(c)
            val right = a.compose(b.compose(c))
            assertEquals(left.apply(doc).toString(), right.apply(doc).toString())
        }
    }

    @Test
    fun survivesRandomSequencesOfChanges() {
        for (i in 0 until 50) {
            var doc = doc10
            var txt = doc.toString()
            val all = mutableListOf<ChangeSet>()
            val inv = mutableListOf<ChangeSet>()
            val log = mutableListOf<String>()
            try {
                for (j in 0 until 50) {
                    val change = rChange(doc.length)
                    log.add("ChangeSet.of([$change], ${doc.length})")
                    val from = change.from
                    val to = change.to ?: from
                    val insert = (change.insert as? InsertContent.StringContent)?.value ?: ""
                    txt = txt.substring(0, from) + insert + txt.substring(to)
                    val set = ChangeSet.of(
                        ChangeSpec.Multi(listOf(change)),
                        doc.length
                    )
                    all.add(set)
                    inv.add(set.invert(doc))
                    doc = set.apply(doc)
                    assertEquals(txt, doc.toString())
                }
                val composed = all.fold(
                    ChangeSet.of(ChangeSpec.Multi(emptyList()), doc10.length)
                ) { a, b -> a.compose(b) }
                assertEquals(txt, composed.apply(doc10).toString())
                assertEquals(doc10.toString(), composed.invert(doc10).apply(doc).toString())
                for (k in inv.size - 1 downTo 0) doc = inv[k].apply(doc)
                assertEquals(doc10.toString(), doc.toString())
            } catch (e: Exception) {
                println("With changes: ${log.joinToString(", ")}")
                throw e
            }
        }
    }

    @Test
    fun canBeSerializedToJSON() {
        for (i in 0 until 100) {
            val size = r(20) + 1
            val set = ChangeSet.of(changeSpecOf(rChanges(size, r(4))), size)
            assertEquals(
                set.toString(),
                ChangeSet.changeSetFromJSON(set.toChangeSetJSON()).toString()
            )
        }
    }
}
