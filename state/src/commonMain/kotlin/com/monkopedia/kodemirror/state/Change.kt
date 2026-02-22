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

import kotlin.math.min

val DefaultSplit = Regex("\r\n?|\n")

/**
 * Distinguishes different ways in which positions can be mapped.
 */
enum class MapMode {
    /**
     * Map a position to a valid new position, even when its context
     * was deleted.
     */
    Simple,

    /**
     * Return null if deletion happens across the position.
     */
    TrackDel,

    /**
     * Return null if the character _before_ the position is deleted.
     */
    TrackBefore,

    /**
     * Return null if the character _after_ the position is deleted.
     */
    TrackAfter
}

/**
 * A change description is a variant of [ChangeSet] that doesn't
 * store the inserted text. As such, it can't be applied, but is
 * cheaper to store and manipulate.
 */
open class ChangeDesc internal constructor(
    internal val sections: IntArray
) {
    /**
     * The length of the document before the change.
     */
    val length: Int
        get() {
            var result = 0
            var i = 0
            while (i < sections.size) {
                result += sections[i]
                i += 2
            }
            return result
        }

    /**
     * The length of the document after the change.
     */
    val newLength: Int
        get() {
            var result = 0
            var i = 0
            while (i < sections.size) {
                val ins = sections[i + 1]
                result += if (ins < 0) sections[i] else ins
                i += 2
            }
            return result
        }

    /**
     * False when there are actual changes in this set.
     */
    val empty: Boolean
        get() = sections.isEmpty() ||
            (sections.size == 2 && sections[1] < 0)

    /**
     * Iterate over the unchanged parts left by these changes.
     * [posA] provides the position of the range in the old document,
     * [posB] the new position in the changed document.
     */
    fun iterGaps(f: (posA: Int, posB: Int, length: Int) -> Unit) {
        var i = 0
        var posA = 0
        var posB = 0
        while (i < sections.size) {
            val len = sections[i++]
            val ins = sections[i++]
            if (ins < 0) {
                f(posA, posB, len)
                posB += len
            } else {
                posB += ins
            }
            posA += len
        }
    }

    /**
     * Iterate over the ranges changed by these changes.
     *
     * When [individual] is true, adjacent changes are reported
     * separately.
     */
    fun iterChangedRanges(
        f: (fromA: Int, toA: Int, fromB: Int, toB: Int) -> Unit,
        individual: Boolean = false
    ) {
        iterChanges(this, f, individual)
    }

    /**
     * Get a description of the inverted form of these changes.
     */
    val invertedDesc: ChangeDesc
        get() {
            val result = IntArray(sections.size)
            var i = 0
            while (i < sections.size) {
                result[i] = sections[i + 1].let { if (it < 0) sections[i] else it }
                result[i + 1] = sections[i].let { len ->
                    if (sections[i + 1] < 0) sections[i + 1] else len
                }
                i += 2
            }
            return ChangeDesc(result)
        }

    /**
     * Compute the combined effect of applying another set of changes
     * after this one. The length of the document after this set should
     * match the length before [other].
     */
    fun composeDesc(other: ChangeDesc): ChangeDesc = if (empty) {
        other
    } else if (other.empty) {
        this
    } else {
        composeSets(this, other)
    }

    /**
     * Map this description, which should start with the same document
     * as [other], over another set of changes, so that it can be
     * applied after it. When [before] is true, map as if the changes
     * in `this` happened before the ones in [other].
     */
    open fun mapDesc(other: ChangeDesc, before: Boolean = false): ChangeDesc =
        if (other.empty) this else mapSet(this, other, before)

    /**
     * Map a given position through these changes, to produce a
     * position pointing into the new document.
     */
    fun mapPos(pos: Int, assoc: Int = -1): Int = mapPosInner(pos, assoc, MapMode.Simple)!!

    /**
     * Map a given position through these changes. Returns null if
     * the position was deleted according to the given [mode].
     */
    fun mapPos(pos: Int, assoc: Int, mode: MapMode): Int? = mapPosInner(pos, assoc, mode)

    private fun mapPosInner(pos: Int, assoc: Int, mode: MapMode): Int? {
        var posA = 0
        var posB = 0
        var i = 0
        while (i < sections.size) {
            val len = sections[i++]
            val ins = sections[i++]
            val endA = posA + len
            if (ins < 0) {
                if (endA > pos) return posB + (pos - posA)
                posB += len
            } else {
                if (mode != MapMode.Simple && endA >= pos &&
                    (
                        mode == MapMode.TrackDel &&
                            posA < pos && endA > pos ||
                            mode == MapMode.TrackBefore &&
                            posA < pos ||
                            mode == MapMode.TrackAfter &&
                            endA > pos
                        )
                ) {
                    return null
                }
                if (endA > pos || endA == pos && assoc < 0 && len == 0) {
                    return if (pos == posA || assoc < 0) {
                        posB
                    } else {
                        posB + ins
                    }
                }
                posB += ins
            }
            posA = endA
        }
        if (pos > posA) {
            throw IllegalArgumentException(
                "Position $pos is out of range for changeset " +
                    "of length $posA"
            )
        }
        return posB
    }

    /**
     * Check whether these changes touch a given range. When one of
     * the changes entirely covers the range, the string "cover" is
     * returned.
     */
    fun touchesRange(from: Int, to: Int = from): TouchesResult {
        var i = 0
        var pos = 0
        while (i < sections.size && pos <= to) {
            val len = sections[i++]
            val ins = sections[i++]
            val end = pos + len
            if (ins >= 0 && pos <= to && end >= from) {
                return if (pos < from && end > to) {
                    TouchesResult.Cover
                } else {
                    TouchesResult.Yes
                }
            }
            pos = end
        }
        return TouchesResult.No
    }

    override fun toString(): String {
        val result = StringBuilder()
        var i = 0
        while (i < sections.size) {
            val len = sections[i++]
            val ins = sections[i++]
            if (result.isNotEmpty()) result.append(' ')
            result.append(len)
            if (ins >= 0) result.append(':').append(ins)
        }
        return result.toString()
    }

    /**
     * Serialize this change desc to a JSON-representable value.
     */
    fun toJSON(): List<Int> = sections.toList()

    companion object {
        /**
         * Create a change desc from its JSON representation.
         */
        fun fromJSON(json: List<Int>): ChangeDesc {
            if (json.size % 2 != 0) {
                throw IllegalArgumentException(
                    "Invalid JSON representation of ChangeDesc"
                )
            }
            return ChangeDesc(json.toIntArray())
        }

        internal fun create(sections: IntArray): ChangeDesc = ChangeDesc(sections)
    }
}

/**
 * Result type for [ChangeDesc.touchesRange].
 */
enum class TouchesResult {
    No,
    Yes,
    Cover;

    fun toBoolean(): Boolean = this != No
}

/**
 * This type is used as argument to [EditorState.changes] and in
 * the `changes` field of transaction specs to succinctly describe
 * document changes.
 */
sealed interface ChangeSpec {
    data class Single(
        val from: Int,
        val to: Int? = null,
        val insert: InsertContent? = null
    ) : ChangeSpec

    data class Multi(val specs: List<ChangeSpec>) : ChangeSpec

    class Set(val changeSet: ChangeSet) : ChangeSpec
}

sealed interface InsertContent {
    data class StringContent(val value: String) : InsertContent
    data class TextContent(val value: Text) : InsertContent
}

/**
 * A change set represents a group of modifications to a document.
 * It stores the document length, and can only be applied to
 * documents with exactly that length.
 */
class ChangeSet private constructor(
    sections: IntArray,
    internal val inserted: List<Text>
) : ChangeDesc(sections) {

    /**
     * Apply the changes to a document, returning the modified
     * document.
     */
    fun apply(doc: Text): Text {
        if (length != doc.length) {
            throw IllegalArgumentException(
                "Applying change set to a document with the " +
                    "wrong length"
            )
        }
        var result = doc
        iterChanges(
            this,
            { fromA, toA, fromB, _, text ->
                result = result.replace(
                    fromB, fromB + (toA - fromA), text
                )
            },
            false
        )
        return result
    }

    override fun mapDesc(other: ChangeDesc, before: Boolean): ChangeDesc = if (other.empty) {
        this
    } else {
        mapSet(this, other, before, mkSet = true)
    }

    /**
     * Given the document as it existed _before_ the changes, return
     * a change set that represents the inverse of this set, which
     * could be used to go from the document created by the changes
     * back to the document as it existed before the changes.
     */
    fun invert(doc: Text): ChangeSet {
        val newSections = sections.copyOf()
        val newInserted = mutableListOf<Text>()
        var pos = 0
        var i = 0
        while (i < newSections.size) {
            val len = newSections[i]
            val ins = newSections[i + 1]
            if (ins >= 0) {
                newSections[i] = ins
                newSections[i + 1] = len
                val index = i shr 1
                while (newInserted.size < index) {
                    newInserted.add(Text.empty)
                }
                newInserted.add(
                    if (len > 0) {
                        doc.slice(pos, pos + len)
                    } else {
                        Text.empty
                    }
                )
            }
            pos += len
            i += 2
        }
        return ChangeSet(newSections, newInserted)
    }

    /**
     * Combine two subsequent change sets into a single set. [other]
     * must start in the document produced by `this`.
     */
    fun compose(other: ChangeSet): ChangeSet = if (empty) {
        other
    } else if (other.empty) {
        this
    } else {
        composeSets(this, other, mkSet = true) as ChangeSet
    }

    /**
     * Given another change set starting in the same document, maps
     * this change set over the other, producing a new change set
     * that can be applied to the document produced by applying
     * [other].
     */
    fun map(other: ChangeDesc, before: Boolean = false): ChangeSet = if (other.empty) {
        this
    } else {
        mapSet(this, other, before, mkSet = true) as ChangeSet
    }

    /**
     * Iterate over the changed ranges in the document.
     */
    fun iterChanges(
        f: (
            fromA: Int,
            toA: Int,
            fromB: Int,
            toB: Int,
            inserted: Text
        ) -> Unit,
        individual: Boolean = false
    ) {
        iterChanges(this, f, individual)
    }

    /**
     * Get a [change description][ChangeDesc] for this change set.
     */
    val desc: ChangeDesc get() = ChangeDesc.create(sections)

    internal fun filter(ranges: IntArray): FilterResult {
        val resultSections = mutableListOf<Int>()
        val resultInserted = mutableListOf<Text>()
        val filteredSections = mutableListOf<Int>()
        val iter = SectionIter(this)
        var i = 0
        var pos = 0
        outer@ while (true) {
            val next = if (i == ranges.size) {
                1_000_000_000
            } else {
                ranges[i++]
            }
            while (pos < next || pos == next && iter.len == 0) {
                if (iter.done) break@outer
                val len = min(iter.len, next - pos)
                addSection(filteredSections, len, -1)
                val ins = if (iter.ins == -1) {
                    -1
                } else if (iter.off == 0) iter.ins else 0
                addSection(resultSections, len, ins)
                if (ins > 0) {
                    addInsert(
                        resultInserted,
                        resultSections,
                        iter.text
                    )
                }
                iter.forward(len)
                pos += len
            }
            val end = ranges[i++]
            while (pos < end) {
                if (iter.done) break@outer
                val len = min(iter.len, end - pos)
                addSection(resultSections, len, -1)
                addSection(
                    filteredSections,
                    len,
                    if (iter.ins == -1) {
                        -1
                    } else if (iter.off == 0) iter.ins else 0
                )
                iter.forward(len)
                pos += len
            }
        }
        return FilterResult(
            changes = createSet(
                resultSections.toIntArray(),
                resultInserted
            ),
            filtered = ChangeDesc.create(
                filteredSections.toIntArray()
            )
        )
    }

    /**
     * Serialize this change set to a JSON-representable value.
     */
    fun toChangeSetJSON(): List<Any> {
        val parts = mutableListOf<Any>()
        var i = 0
        while (i < sections.size) {
            val len = sections[i]
            val ins = sections[i + 1]
            if (ins < 0) {
                parts.add(len)
            } else if (ins == 0) {
                parts.add(listOf(len))
            } else {
                val list = mutableListOf<Any>(len)
                list.addAll(inserted[i shr 1].toJSON())
                parts.add(list)
            }
            i += 2
        }
        return parts
    }

    companion object {
        /**
         * Create a change set for the given changes, for a document
         * of the given length, using [lineSep] as line separator.
         */
        fun of(changes: ChangeSpec, length: Int, lineSep: String? = null): ChangeSet {
            val lineSepRegex =
                if (lineSep != null) {
                    Regex(Regex.escape(lineSep))
                } else {
                    DefaultSplit
                }
            var sections = mutableListOf<Int>()
            var inserted = mutableListOf<Text>()
            var pos = 0
            var total: ChangeSet? = null

            fun flush(force: Boolean = false) {
                if (!force && sections.isEmpty()) return
                if (pos < length) addSection(sections, length - pos, -1)
                val set = ChangeSet(
                    sections.toIntArray(),
                    inserted
                )
                total = total?.compose(set.map(total!!)) ?: set
                sections = mutableListOf()
                inserted = mutableListOf()
                pos = 0
            }

            fun process(spec: ChangeSpec) {
                when (spec) {
                    is ChangeSpec.Multi -> {
                        for (sub in spec.specs) process(sub)
                    }
                    is ChangeSpec.Set -> {
                        if (spec.changeSet.length != length) {
                            throw IllegalArgumentException(
                                "Mismatched change set length " +
                                    "(got ${spec.changeSet.length}" +
                                    ", expected $length)"
                            )
                        }
                        flush()
                        total = total?.compose(
                            spec.changeSet.map(total!!)
                        ) ?: spec.changeSet
                    }
                    is ChangeSpec.Single -> {
                        val from = spec.from
                        val to = spec.to ?: from
                        if (from > to || from < 0 || to > length) {
                            throw IllegalArgumentException(
                                "Invalid change range $from to " +
                                    "$to (in doc of length $length)"
                            )
                        }
                        val insText = when (val ins = spec.insert) {
                            null -> Text.empty
                            is InsertContent.StringContent ->
                                Text.of(
                                    ins.value.split(lineSepRegex)
                                )
                            is InsertContent.TextContent -> ins.value
                        }
                        val insLen = insText.length
                        if (from == to && insLen == 0) return
                        if (from < pos) flush()
                        if (from > pos) {
                            addSection(sections, from - pos, -1)
                        }
                        addSection(sections, to - from, insLen)
                        addInsert(inserted, sections, insText)
                        pos = to
                    }
                }
            }

            process(changes)
            flush(total == null)
            return total!!
        }

        /**
         * Create an empty changeset of the given length.
         */
        fun empty(length: Int): ChangeSet = ChangeSet(
            if (length > 0) {
                intArrayOf(length, -1)
            } else {
                intArrayOf()
            },
            emptyList()
        )

        /**
         * Create a changeset from its JSON representation.
         */
        @Suppress("UNCHECKED_CAST")
        fun changeSetFromJSON(json: List<Any>): ChangeSet {
            val sections = mutableListOf<Int>()
            val inserted = mutableListOf<Text>()
            for (i in json.indices) {
                val part = json[i]
                when {
                    part is Number -> {
                        sections.add(part.toInt())
                        sections.add(-1)
                    }
                    part is List<*> -> {
                        val list = part as List<Any>
                        if (list.isEmpty() ||
                            list[0] !is Number
                        ) {
                            throw IllegalArgumentException(
                                "Invalid JSON representation " +
                                    "of ChangeSet"
                            )
                        }
                        if (list.size == 1) {
                            sections.add(
                                (list[0] as Number).toInt()
                            )
                            sections.add(0)
                        } else {
                            while (inserted.size < i) {
                                inserted.add(Text.empty)
                            }
                            inserted.add(
                                i,
                                Text.of(
                                    list.drop(1)
                                        .map { it.toString() }
                                )
                            )
                            sections.add(
                                (list[0] as Number).toInt()
                            )
                            sections.add(inserted[i].length)
                        }
                    }
                    else -> throw IllegalArgumentException(
                        "Invalid JSON representation of ChangeSet"
                    )
                }
            }
            return ChangeSet(
                sections.toIntArray(),
                inserted
            )
        }

        internal fun createSet(sections: IntArray, inserted: List<Text>): ChangeSet =
            ChangeSet(sections, inserted)
    }
}

data class FilterResult(
    val changes: ChangeSet,
    val filtered: ChangeDesc
)

private fun addSection(sections: MutableList<Int>, len: Int, ins: Int, forceJoin: Boolean = false) {
    if (len == 0 && ins <= 0) return
    val last = sections.size - 2
    if (last >= 0 && ins <= 0 && ins == sections[last + 1]) {
        sections[last] = sections[last] + len
    } else if (last >= 0 && len == 0 && sections[last] == 0) {
        sections[last + 1] = sections[last + 1] + ins
    } else if (forceJoin) {
        sections[last] = sections[last] + len
        sections[last + 1] = sections[last + 1] + ins
    } else {
        sections.add(len)
        sections.add(ins)
    }
}

private fun addInsert(values: MutableList<Text>, sections: List<Int>, value: Text) {
    if (value.length == 0) return
    val index = (sections.size - 2) shr 1
    if (index < values.size) {
        values[values.size - 1] =
            values[values.size - 1].append(value)
    } else {
        while (values.size < index) values.add(Text.empty)
        values.add(value)
    }
}

private fun iterChanges(
    desc: ChangeDesc,
    f: (
        fromA: Int,
        toA: Int,
        fromB: Int,
        toB: Int,
        text: Text
    ) -> Unit,
    individual: Boolean
) {
    val inserted = (desc as? ChangeSet)?.inserted
    var posA = 0
    var posB = 0
    var i = 0
    while (i < desc.sections.size) {
        val len = desc.sections[i++]
        val ins = desc.sections[i++]
        if (ins < 0) {
            posA += len
            posB += len
        } else {
            var endA = posA
            var endB = posB
            var text = Text.empty
            var curLen = len
            var curIns = ins
            while (true) {
                endA += curLen
                endB += curIns
                if (curIns > 0 && inserted != null) {
                    text = text.append(inserted[(i - 2) shr 1])
                }
                if (individual ||
                    i == desc.sections.size ||
                    desc.sections[i + 1] < 0
                ) {
                    break
                }
                curLen = desc.sections[i++]
                curIns = desc.sections[i++]
            }
            f(posA, endA, posB, endB, text)
            posA = endA
            posB = endB
        }
    }
}

private fun iterChanges(
    desc: ChangeDesc,
    f: (fromA: Int, toA: Int, fromB: Int, toB: Int) -> Unit,
    individual: Boolean
) {
    iterChanges(
        desc,
        { fromA, toA, fromB, toB, _ ->
            f(fromA, toA, fromB, toB)
        },
        individual
    )
}

private fun mapSet(
    setA: ChangeDesc,
    setB: ChangeDesc,
    before: Boolean,
    mkSet: Boolean = false
): ChangeDesc {
    val sections = mutableListOf<Int>()
    val insert: MutableList<Text>? =
        if (mkSet) mutableListOf() else null
    val a = SectionIter(setA)
    val b = SectionIter(setB)
    var inserted = -1
    while (true) {
        if (a.done && b.len > 0 || b.done && a.len > 0) {
            throw IllegalStateException(
                "Mismatched change set lengths"
            )
        } else if (a.ins == -1 && b.ins == -1) {
            val len = min(a.len, b.len)
            addSection(sections, len, -1)
            a.forward(len)
            b.forward(len)
        } else if (b.ins >= 0 &&
            (
                a.ins < 0 || inserted == a.i ||
                    a.off == 0 &&
                    (
                        b.len < a.len ||
                            b.len == a.len && !before
                        )
                )
        ) {
            val len = b.len
            addSection(sections, b.ins, -1)
            var remaining = len
            while (remaining > 0) {
                val piece = min(a.len, remaining)
                if (a.ins >= 0 && inserted < a.i &&
                    a.len <= piece
                ) {
                    addSection(sections, 0, a.ins)
                    if (insert != null) {
                        addInsert(insert, sections, a.text)
                    }
                    inserted = a.i
                }
                a.forward(piece)
                remaining -= piece
            }
            b.next()
        } else if (a.ins >= 0) {
            var len = 0
            var left = a.len
            while (left > 0) {
                if (b.ins == -1) {
                    val piece = min(left, b.len)
                    len += piece
                    left -= piece
                    b.forward(piece)
                } else if (b.ins == 0 && b.len < left) {
                    left -= b.len
                    b.next()
                } else {
                    break
                }
            }
            addSection(
                sections,
                len,
                if (inserted < a.i) a.ins else 0
            )
            if (insert != null && inserted < a.i) {
                addInsert(insert, sections, a.text)
            }
            inserted = a.i
            a.forward(a.len - left)
        } else if (a.done && b.done) {
            return if (insert != null) {
                ChangeSet.createSet(
                    sections.toIntArray(),
                    insert
                )
            } else {
                ChangeDesc.create(sections.toIntArray())
            }
        } else {
            throw IllegalStateException(
                "Mismatched change set lengths"
            )
        }
    }
}

private fun composeSets(setA: ChangeDesc, setB: ChangeDesc, mkSet: Boolean = false): ChangeDesc {
    val sections = mutableListOf<Int>()
    val insert: MutableList<Text>? =
        if (mkSet) mutableListOf() else null
    val a = SectionIter(setA)
    val b = SectionIter(setB)
    var open = false
    while (true) {
        if (a.done && b.done) {
            return if (insert != null) {
                ChangeSet.createSet(
                    sections.toIntArray(),
                    insert
                )
            } else {
                ChangeDesc.create(sections.toIntArray())
            }
        } else if (a.ins == 0) { // Deletion in A
            addSection(sections, a.len, 0, open)
            a.next()
        } else if (b.len == 0 && !b.done) { // Insertion in B
            addSection(sections, 0, b.ins, open)
            if (insert != null) {
                addInsert(insert, sections, b.text)
            }
            b.next()
        } else if (a.done || b.done) {
            throw IllegalStateException(
                "Mismatched change set lengths"
            )
        } else {
            val len = min(a.len2, b.len)
            val sectionLen = sections.size
            if (a.ins == -1) {
                val insB = if (b.ins == -1) {
                    -1
                } else if (b.off != 0) 0 else b.ins
                addSection(sections, len, insB, open)
                if (insert != null && insB != 0) {
                    addInsert(insert, sections, b.text)
                }
            } else if (b.ins == -1) {
                addSection(
                    sections,
                    if (a.off != 0) 0 else a.len,
                    len,
                    open
                )
                if (insert != null) {
                    addInsert(insert, sections, a.textBit(len))
                }
            } else {
                addSection(
                    sections,
                    if (a.off != 0) 0 else a.len,
                    if (b.off != 0) 0 else b.ins,
                    open
                )
                if (insert != null && b.off == 0) {
                    addInsert(insert, sections, b.text)
                }
            }
            open = (
                a.ins > len ||
                    b.ins >= 0 && b.len > len
                ) &&
                (open || sections.size > sectionLen)
            a.forward2(len)
            b.forward(len)
        }
    }
}

internal class SectionIter(private val set: ChangeDesc) {
    var i = 0
        private set
    var len: Int = 0
        private set
    var off: Int = 0
        private set
    var ins: Int = 0
        private set

    init {
        next()
    }

    fun next() {
        if (i < set.sections.size) {
            len = set.sections[i++]
            ins = set.sections[i++]
        } else {
            len = 0
            ins = -2
        }
        off = 0
    }

    val done: Boolean get() = ins == -2

    val len2: Int get() = if (ins < 0) len else ins

    val text: Text
        get() {
            val changeSet = set as? ChangeSet
                ?: return Text.empty
            val index = (i - 2) shr 1
            return if (index >= changeSet.inserted.size) {
                Text.empty
            } else {
                changeSet.inserted[index]
            }
        }

    fun textBit(len: Int? = null): Text {
        val changeSet = set as? ChangeSet
            ?: return Text.empty
        val index = (i - 2) shr 1
        return if (index >= changeSet.inserted.size && len == null) {
            Text.empty
        } else {
            changeSet.inserted[index].slice(
                off,
                if (len == null) Int.MAX_VALUE else off + len
            )
        }
    }

    fun forward(len: Int) {
        if (len == this.len) {
            next()
        } else {
            this.len -= len
            off += len
        }
    }

    fun forward2(len: Int) {
        if (ins == -1) {
            forward(len)
        } else if (len == ins) {
            next()
        } else {
            ins -= len
            off += len
        }
    }
}
