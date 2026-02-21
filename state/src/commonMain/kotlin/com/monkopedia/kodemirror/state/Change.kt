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

import kotlin.jvm.JvmName
import kotlin.math.min

val DefaultSplit = Regex("\r\n?|\n")

// / Distinguishes different ways in which positions can be mapped.
enum class MapMode {
    // / Map a position to a valid new position, even when its context
    // / was deleted.
    Simple,

    // / Return null if deletion happens across the position.
    TrackDel,

    // / Return null if the character _before_ the position is deleted.
    TrackBefore,

    // / Return null if the character _after_ the position is deleted.
    TrackAfter
}

data class ChangeSection(val len: Int, val ins: Int? = null) {
    override fun toString(): String = if (ins != null) "$len:$ins" else len.toString()

    fun plus(len: Int = 0, ins: Int? = null): ChangeSection = ChangeSection(
        this.len + len,
        ins?.let {
            it + (this.ins ?: -1)
        } ?: this.ins
    )
}

// / A change description is a variant of [change set](#state.ChangeSet)
// / that doesn't store the inserted text. As such, it can't be
// / applied, but is cheaper to store and manipulate.
open class ChangeDesc
// Sections are encoded as pairs of integers. The first is the
// length in the current document, and the second is -1 for
// unaffected sections, and the length of the replacement content
// otherwise. So an insertion would be (0, n>0), a deletion (n>0,
// 0), and a replacement two positive Ints.
// / @internal
internal constructor(
    // / @internal
    val sections: List<ChangeSection>
) {

    // / The length of the document before the change.
    val length: Int
        get() = sections.sumOf { it.len }

    // / The length of the document after the change.
    val newLength: Int
        get() {
            return sections.sumOf { it.ins ?: it.len }
        }

    // / False when there are actual changes in this set.
    val isEmpty: Boolean
        get() {
            return this.sections.isEmpty() ||
                this.sections.size == 1 &&
                this.sections[0].ins == null
        }

    // / Iterate over the unchanged parts left by these changes. `posA`
    // / provides the position of the range in the old document, `posB`
    // / the new position in the changed document.
    fun iterGaps(f: (posA: Int, posB: Int, length: Int) -> Unit) {
        var posA = 0
        var posB = 0
        sections.forEach { (len, ins) ->
            if (ins == null) {
                f(posA, posB, len)
                posB += len
            } else {
                posB += ins
            }
            posA += len
        }
    }

    // / Iterate over the ranges changed by these changes. (See
    // / [`ChangeSet.iterChanges`](#state.ChangeSet.iterChanges) for a
    // / variant that also provides you with the inserted text.)
    // / `fromA`/`toA` provides the extent of the change in the starting
    // / document, `fromB`/`toB` the extent of the replacement in the
    // / changed document.
    // /
    // / When `individual` is true, adjacent changes (which are kept
    // / separate for [position mapping](#state.ChangeDesc.mapPos)) are
    // / reported separately.
    fun iterChangedRanges(
        f: (fromA: Int, toA: Int, fromB: Int, toB: Int) -> Unit,
        individual: Boolean = false
    ) {
        iterChanges(this, { a, b, c, d, _ -> f(a, b, c, d) }, individual)
    }

    // / Get a description of the inverted form of these changes.
    val invertedDesc: ChangeDesc
        get() {
            val sections = sections.map { (len, ins) ->
                if (ins == null) {
                    ChangeSection(len, ins)
                } else {
                    ChangeSection(ins, len)
                }
            }
            return ChangeDesc(sections)
        }

    // / Compute the combined effect of applying another set of changes
    // / after this one. The length of the document after this set should
    // / match the length before `other`.
    fun composeDesc(other: ChangeDesc): ChangeDesc = if (this.isEmpty) {
        other
    } else if (other.isEmpty) {
        this
    } else {
        composeSets(this, other)
    }

    // / Map this description, which should start with the same document
    // / as `other`, over another set of changes, so that it can be
    // / applied after it. When `before` is true, map as if the changes
    // / in `other` happened before the ones in `this`.
    open fun mapDesc(other: ChangeDesc, before: Boolean = false): ChangeDesc =
        if (other.isEmpty) this else mapSet(this, other, before)

    // / Map a given position through these changes, to produce a
    // / position pointing into the new document.
    // /
    // / `assoc` indicates which side the position should be associated
    // / with. When it is negative or zero, the mapping will try to keep
    // / the position close to the character before it (if any), and will
    // / move it before insertions at that point or replacements across
    // / that point. When it is positive, the position is associated with
    // / the character after it, and will be moved forward for insertions
    // / at or replacements across the position. Defaults to -1.
    // /
    // / `mode` determines whether deletions should be
    // / [reported](#state.MapMode). It defaults to
    // / [`MapMode.Simple`](#state.MapMode.Simple) (don't report
    // / deletions).
    fun mapPos(pos: Int, assoc: Side = Side.NEG, mode: MapMode = MapMode.Simple): Int? {
        var posA = 0
        var posB = 0
        sections.forEach { (len, ins) ->
            val endA = posA + len
            if (ins == null) {
                if (endA > pos) return posB + (pos - posA)
                posB += len
            } else {
                if (mode != MapMode.Simple &&
                    endA >= pos &&
                    (
                        mode == MapMode.TrackDel &&
                            posA < pos &&
                            endA > pos ||
                            mode == MapMode.TrackBefore &&
                            posA < pos ||
                            mode == MapMode.TrackAfter &&
                            endA > pos
                        )
                ) {
                    return null
                }
                if (endA > pos || endA == pos && assoc == Side.NEG && len == 0) {
                    return if (pos == posA || assoc == Side.NEG) posB else posB + ins
                }
                posB += ins
            }
            posA = endA
        }
        if (pos >
            posA
        ) {
            throw IllegalArgumentException(
                "Position $pos is out of range for changeset of length $posA"
            )
        }
        return posB
    }

    // / Check whether these changes touch a given range. When one of the
    // / changes entirely covers the range, the string `"cover"` is
    // / returned.
    fun touchesRange(from: Int, to: Int = from): Boolean? {
        var pos = 0
        sections.forEach { (len, ins) ->
            val end = pos + len
            if (ins != null && pos <= to && end >= from) {
                return if (pos < from && end > to) null else true
            }
            pos = end
        }
        return false
    }

    // / @internal
    override fun toString(): String = sections.joinToString(" ")

    // / Serialize this change desc to a JSON-representable value.
    fun toJSON(): String {
        return toString() // this.sections
    }

    companion object {
        // / Create a change desc from its JSON representation (as produced
        // / by [`toJSON`](#state.ChangeDesc.toJSON).
//        fun fromJSON(json: any) {
//            if (!Array.isArray(json) || json.length % 2 || json.some(a => typeof a != "Int"))
//            throw new IllegalArgumentException ("Invalid JSON representation of ChangeDesc")
//            return new ChangeDesc (json as Int[])
//        }

        // / @internal
        @JvmName("createChangeSection")
        internal fun create(sections: List<ChangeSection>) = ChangeDesc(sections)

        // / @internal
        internal fun create(sections: List<Pair<Int, Int>>) =
            ChangeDesc(sections.map { ChangeSection(it.first, it.second.takeIf { it >= 0 }) })
    }
}

// / This type is used as argument to
// / [`EditorState.changes`](#state.EditorState.changes) and in the
// / [`changes` field](#state.TransactionSpec.changes) of transaction
// / specs to succinctly describe document changes. It may either be a
// / plain object describing a change (a deletion, insertion, or
// / replacement, depending on which fields are present), a [change
// / set](#state.ChangeSet), or an array of change specs.

sealed interface ChangeSpec {
    companion object {
        val empty: ChangeSpec
            get() = ChangeSpecSet(emptyList())
    }
}

data class ChangeSpecData(val from: Int, val to: Int? = null, val insert: Text? = null) :
    ChangeSpec {
    constructor(from: Int, to: Int? = null, insert: String) : this(from, to, insert.asText)
}

class ChangeSpecSet(val content: List<ChangeSpec>) :
    ChangeSpec,
    List<ChangeSpec> by content {
    constructor(vararg content: ChangeSpec) : this(content.toList())
}

val List<ChangeSpec>.asSpec: ChangeSpec
    get() = ChangeSpecSet(this)

// / A change set represents a group of modifications to a document. It
// / stores the document length, and can only be applied to documents
// / with exactly that length.
class ChangeSet private constructor(

    sections: List<ChangeSection>,
// / @internal
    internal val inserted: List<Text>
) : ChangeDesc(sections),
    ChangeSpec {

    // / Apply the changes to a document, returning the modified
    // / document.
    fun apply(doc: Text): Text {
        var ret = doc
        if (this.length !=
            ret.length
        ) {
            throw IllegalArgumentException(
                "Applying change set to a document with the wrong length"
            )
        }
        iterChanges(
            this,
            { fromA, toA, fromB, _toB, text ->
                ret = ret.replace(fromB, fromB + (toA - fromA), text)
            },
            false
        )
        return ret
    }

    override fun mapDesc(other: ChangeDesc, before: Boolean): ChangeDesc = mapSet(
        this,
        other,
        before,
        true
    )

    // / Given the document as it existed _before_ the changes, return a
    // / change set that represents the inverse of this set, which could
    // / be used to go from the document created by the changes back to
    // / the document as it existed before the changes.
    fun invert(doc: Text): ChangeSet {
        val inserted = mutableListOf<Text>()
        var pos = 0
        val sections = sections.map { (len, ins) ->
            if (ins != null) {
                inserted.add(if (len > 0) doc.slice(pos, pos + len) else Text.empty)
                ChangeSection(ins, len)
            } else {
                inserted.add(Text.empty)
                ChangeSection(len, ins)
            }.also {
                pos += len
            }
        }
        return ChangeSet(sections, inserted)
    }

    // / Combine two subsequent change sets into a single set. `other`
    // / must start in the document produced by `this`. If `this` goes
    // / `docA` → `docB` and `other` represents `docB` → `docC`, the
    // / returned value will represent the change `docA` → `docC`.
    fun compose(other: ChangeSet): ChangeSet = when {
        this.isEmpty -> other
        other.isEmpty -> this
        else -> composeSets(this, other, true) as ChangeSet
    }

    // / Given another change set starting in the same document, maps this
    // / change set over the other, producing a new change set that can be
    // / applied to the document produced by applying `other`. When
    // / `before` is `true`, order changes as if `this` comes before
    // / `other`, otherwise (the default) treat `other` as coming first.
    // /
    // / Given two changes `A` and `B`, `A.compose(B.map(A))` and
    // / `B.compose(A.map(B, true))` will produce the same document. This
    // / provides a basic form of [operational
    // / transformation](https://en.wikipedia.org/wiki/Operational_transformation),
    // / and can be used for collaborative editing.
    fun map(other: ChangeDesc, before: Boolean = false): ChangeSet =
        if (other.isEmpty) this else mapSet(this, other, before, true) as ChangeSet

    // / Iterate over the changed ranges in the document, calling `f` for
    // / each, with the range in the original document (`fromA`-`toA`)
    // / and the range that replaces it in the new document
    // / (`fromB`-`toB`).
    // /
    // / When `individual` is true, adjacent changes are reported
    // / separately.
    fun iterChanges(
        f: (fromA: Int, toA: Int, fromB: Int, toB: Int, inserted: Text) -> Unit,
        individual: Boolean = false
    ) {
        iterChanges(this, f, individual)
    }

    // / Get a [change description](#state.ChangeDesc) for this change
    // / set.
    val desc: ChangeDesc
        get() {
            return ChangeDesc.create(this.sections)
        }

    // / @internal
    fun filter(ranges: List<Pair<Int, Int>>): Pair<ChangeSet, ChangeDesc> {
        val resultSections = mutableListOf<ChangeSection>()
        val resultInserted = mutableListOf<Text>()
        val filteredSections = mutableListOf<ChangeSection>()
        val iter = SectionIter(this)
        var pos = 0
        val rangeSequence: Sequence<Pair<Int, Int>> =
            ranges.asSequence() + generateSequence { Int.MAX_VALUE to 0 }
        rangeSequence.forEach { (next, end) ->
            while (pos < next || pos == next && iter.len == 0) {
                if (iter.done) {
                    return ChangeSet(resultSections, resultInserted) to create(filteredSections)
                }
                val len = min(iter.len, next - pos)
                addSection(filteredSections, len, null)
                val ins = if (iter.ins == null) {
                    null
                } else if (iter.off == 0) {
                    iter.ins
                } else {
                    0
                }
                addSection(resultSections, len, ins)
                if (ins != null && ins > 0) addInsert(resultInserted, resultSections, iter.text)
                iter.forward(len)
                pos += len
            }

            while (pos < end) {
                if (iter.done) {
                    return ChangeSet(resultSections, resultInserted) to create(filteredSections)
                }
                val len = min(iter.len, end - pos)
                addSection(resultSections, len, null)
                addSection(
                    filteredSections,
                    len,
                    if (iter.ins == null) {
                        null
                    } else if (iter.off == 0) {
                        iter.ins
                    } else {
                        0
                    }
                )
                iter.forward(len)
                pos += len
            }
        }
        error("Unreachable code")
    }

    // / Serialize this change set to a JSON-representable value.
//    fun toJSON(): any {
//        let parts :(Int |[Int, ...string[]])[] = []
//        for (let i = 0; i < this.sections.length; i += 2) {
//        let len = this.sections[i], ins = this.sections[i+1]
//        if (ins < 0) parts.push(len)
//        else if (ins == 0) parts.push([len])
//        else parts.push(([len] as [ Int, ... string []]).concat(this.inserted[i >> 1].toJSON()) as any)
//    }
//        return parts
//    }

    companion object {
        // / Create a change set for the given changes, for a document of the
        // / given length, using `lineSep` as line separator.
        fun of(changes: ChangeSpec, length: Int, lineSep: String? = null): ChangeSet {
            var sections = mutableListOf<ChangeSection>()
            var inserted = mutableListOf<Text>()
            var pos = 0
            var total: ChangeSet? = null

            fun flush(force: Boolean = false) {
                if (!force && sections.isEmpty()) return
                if (pos < length) addSection(sections, length - pos, null)
                val set = ChangeSet(sections, inserted)
                total = total?.let { it.compose(set.map(it)) } ?: set
                sections = mutableListOf()
                inserted = mutableListOf()
                pos = 0
            }

            fun process(spec: ChangeSpec) {
                when (spec) {
                    is ChangeSpecSet -> {
                        for (sub in spec.content) process(sub)
                    }

                    is ChangeSet -> {
                        if (spec.length != length) {
                            throw IllegalArgumentException(
                                "Mismatched change set length " +
                                    "(got ${spec.length}, expected $length)"
                            )
                        }
                        flush()
                        total = total?.let { it.compose(spec.map(it)) } ?: spec
                    }

                    is ChangeSpecData -> {
                        val from = spec.from
                        val to = spec.to ?: from
                        val insert = spec.insert
                        if (from > to || from < 0 || to > length) {
                            throw IllegalArgumentException(
                                "Invalid change range $from to $to (in doc of length $length)"
                            )
                        }
                        // !insert ? Text.empty : typeof insert == "string" ? Text.of(insert.split(lineSep || DefaultSplit)) : insert
                        val insText = insert ?: Text.empty
                        val insLen = insText.length
                        if (from == to && insLen == 0) return
                        if (from < pos) flush()
                        if (from > pos) addSection(sections, from - pos, null)
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

        // / Create an empty changeset of the given length.
        fun empty(length: Int): ChangeSet = ChangeSet(
            if (length != 0) listOf(ChangeSection(length)) else emptyList(),
            emptyList()
        )

        // / Create a changeset from its JSON representation (as produced by
        // / [`toJSON`](#state.ChangeSet.toJSON).
//        fun fromJSON (json: any) {
//            if (!Array.isArray(json)) throw new IllegalArgumentException ("Invalid JSON representation of ChangeSet")
//            let sections =[], inserted = []
//            for (let i = 0; i < json.length; i++) {
//            let part = json [i]
//            if (typeof part == "Int") {
//            sections.push(part, -1)
//        } else if (!Array.isArray(part) || typeof part[0] != "Int" || part.some((e, i) => i && typeof e != "string")) {
//            throw new IllegalArgumentException ("Invalid JSON representation of ChangeSet")
//        } else if (part.length == 1) {
//            sections.push(part[0], 0)
//        } else {
//            while (inserted.length < i) inserted.push(Text.empty)
//            inserted[i] = Text.of(part.slice(1))
//            sections.push(part[0], inserted[i].length)
//        }
//        }
//            return new ChangeSet (sections, inserted)
//        }

        @JvmName("createChangeSection")
        // / @internal
        internal fun createSet(sections: List<ChangeSection>, inserted: List<Text>) =
            ChangeSet(sections, inserted)

        // / @internal
        internal fun createSet(sections: List<Pair<Int, Int>>, inserted: List<Text>) = ChangeSet(
            sections.map { ChangeSection(it.first, it.second.takeIf { it >= 0 }) },
            inserted
        )
    }
}

internal fun addSection(
    sections: MutableList<ChangeSection>,
    len: Int,
    ins: Int?,
    forceJoin: Boolean = false
) {
    if (len == 0 && (ins == null || ins == 0)) return
    val last = sections.size - 1
    if (last >= 0 && (ins == null || ins == 0) && ins == sections[last].ins) {
        sections[last] = sections[last].plus(len = len)
    } else if (last >= 0 && len == 0 && sections[last].len == 0) {
        sections[last] = sections[last].plus(ins = ins ?: -1)
    } else if (forceJoin) {
        sections[last] = sections[last].plus(len = len, ins = ins)
    } else {
        sections.add(ChangeSection(len, ins))
    }
}

internal fun addInsert(
    values: MutableList<Text>,
    sections: MutableList<ChangeSection>,
    value: Text
) {
    if (value.length == 0) return
    val index = (sections.size - 1)
    if (index < values.size) {
        values[values.size - 1] = values[values.size - 1].append(value)
    } else {
        while (values.size < index) values.add(Text.empty)
        values.add(value)
    }
}

internal fun iterChanges(
    desc: ChangeDesc,
    f: (fromA: Int, toA: Int, fromB: Int, toB: Int, text: Text) -> Unit,
    individual: Boolean
) {
    val inserted = (desc as ChangeSet).inserted
    var posA = 0
    var posB = 0
    var i = 0
    while (i < desc.sections.size) {
        var (len, ins) = desc.sections[i++]
        if (ins == null) {
            posA += len
            posB += len
        } else {
            var endA = posA
            var endB = posB
            var text = Text.empty
            while (true) {
                endA += len
                endB += ins!!
                if (ins != 0 && inserted.isNotEmpty()) {
                    text = text.append(inserted[i - 1])
                }
                if (individual || i == desc.sections.size || desc.sections[i].ins == null) break
                len = desc.sections[i].len
                ins = desc.sections[i++].ins
            }
            f(posA, endA, posB, endB, text)
            posA = endA
            posB = endB
        }
    }
}

internal fun mapSet(setA: ChangeSet, setB: ChangeDesc, before: Boolean): ChangeDesc =
    mapSet(setA, setB, before, mkSet = true)

internal fun mapSet(
    setA: ChangeDesc,
    setB: ChangeDesc,
    before: Boolean,
    mkSet: Boolean = false
): ChangeDesc {
    // Produce a copy of setA that applies to the document after setB
    // has been applied (assuming both start at the same document).
    val sections = mutableListOf<ChangeSection>()
    val insert = if (mkSet) mutableListOf<Text>() else null
    val a = SectionIter(setA)
    val b = SectionIter(setB)
    // Iterate over both sets in parallel. inserted tracks, for changes
    // in A that have to be processed piece-by-piece, whether their
    // content has been inserted already, and refers to the section
    // index.
    var inserted = -1
    while (true) {
        if (a.done && b.len != 0 || b.done && a.len != 0) {
            throw Error("Mismatched change set lengths")
        } else if (a.ins == null && b.ins == null && !a.done && !b.done) {
            // Move across ranges skipped by both sets.
            val len = min(a.len, b.len)
            addSection(sections, len, null)
            a.forward(len)
            b.forward(len)
        } else if (b.ins != null &&
            (
                a.ins == null ||
                    inserted == a.i ||
                    a.off == 0 &&
                    (b.len < a.len || b.len == a.len && !before)
                )
        ) {
            // If there's a change in B that comes before the next change in
            // A (ordered by start pos, then len, then before flag), skip
            // that (and process any changes in A it covers).
            var len = b.len
            addSection(sections, b.ins!!, null)
            while (len != 0) {
                val piece = min(a.len, len)
                if (a.ins != null && inserted < a.i && a.len <= piece) {
                    addSection(sections, 0, a.ins)
                    if (insert != null) addInsert(insert, sections, a.text)
                    inserted = a.i
                }
                a.forward(piece)
                len -= piece
            }
            b.next()
        } else if (a.ins != null) {
            // Process the part of a change in A up to the start of the next
            // non-deletion change in B (if overlapping).
            var len = 0
            var left = a.len
            while (left != 0) {
                if (b.ins == null) {
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
            addSection(sections, len, if (inserted < a.i) a.ins else 0)
            if (insert != null && inserted < a.i) addInsert(insert, sections, a.text)
            inserted = a.i
            a.forward(a.len - left)
        } else if (a.done && b.done) {
            return if (insert != null) {
                ChangeSet.createSet(sections, insert)
            } else {
                ChangeDesc.create(sections)
            }
        } else {
            throw Error("Mismatched change set lengths")
        }
    }
}

internal fun composeSets(setA: ChangeSet, setB: ChangeSet): ChangeSet =
    composeSets(setA, setB, true) as ChangeSet

internal fun composeSets(setA: ChangeDesc, setB: ChangeDesc, mkSet: Boolean? = null): ChangeDesc {
    val mkSet = mkSet ?: (setA is ChangeSet && setB is ChangeSet)
    val sections = mutableListOf<ChangeSection>()
    val insert = if (mkSet) mutableListOf<Text>() else null
    val a = SectionIter(setA)
    val b = SectionIter(setB)
    var open = false
    while (true) {
        if (a.done && b.done) {
            return if (insert != null) {
                ChangeSet.createSet(sections, insert)
            } else {
                ChangeDesc.create(sections)
            }
        } else if (a.ins == 0) { // Deletion in A
            addSection(sections, a.len, 0, open)
            a.next()
        } else if (b.len == 0 && !b.done) { // Insertion in B
            addSection(sections, 0, b.ins, open)
            if (insert != null) addInsert(insert, sections, b.text)
            b.next()
        } else if (a.done || b.done) {
            throw Error("Mismatched change set lengths ($setA) ($setB) ${a.done} ${b.done}")
        } else {
            val len = min(a.len2, b.len)
            val sectionLen = sections.size
            if (a.ins == null) {
                val insB = when {
                    b.ins == null -> null
                    b.off != 0 -> 0
                    else -> b.ins
                }
                addSection(sections, len, insB, open)
                if (insert != null && insB != 0) addInsert(insert, sections, b.text)
            } else if (b.ins == null) {
                addSection(sections, if (a.off != 0) 0 else a.len, len, open)
                if (insert != null) addInsert(insert, sections, a.textBit(len))
            } else {
                addSection(
                    sections,
                    if (a.off != 0) 0 else a.len,
                    if (b.off != 0) 0 else b.ins,
                    open
                )
                if (insert != null && b.off == 0) addInsert(insert, sections, b.text)
            }
            open = ((a.ins ?: -1) > len || b.ins != null && b.len > len) &&
                (open || sections.size > sectionLen)
            a.forward2(len)
            b.forward(len)
        }
    }
}

internal class SectionIter constructor(val set: ChangeDesc) {
    var i = 0
    var len: Int = 0
    var off: Int = 0
    var ins: Int? = null
    var done = false

    init {
        this.next()
    }

    fun next() {
        val sections = this.set.sections
        if (this.i < sections.size) {
            this.len = sections[this.i].len
            this.ins = sections[this.i++].ins
        } else {
            this.len = 0
            this.ins = null
            this.done = true
        }
        this.off = 0
    }

//    val done: Boolean
//        get() {
//            return this.ins == -2
//        }

    val len2: Int
        get() {
            return ins ?: len
        }

    val text: Text
        get() {
            val inserted = (this.set as ChangeSet).inserted
            val index = this.i - 1
            return inserted.getOrNull(index) ?: Text.empty
        }

    fun textBit(len: Int?): Text {
        val inserted = (this.set as ChangeSet).inserted
        val index = (this.i - 1)
        return inserted.takeIf { len != null && len != 0 }
            ?.getOrNull(index)
            ?.slice(this.off, len?.plus(this.off))
            ?: Text.empty
    }

    fun forward(len: Int) {
        if (len == this.len) {
            this.next()
        } else {
            this.len -= len
            this.off += len
        }
    }

    fun forward2(len: Int) {
        if (this.ins == null) {
            this.forward(len)
        } else if (len == this.ins) {
            this.next()
        } else {
            this.ins = this.ins?.minus(len)
            this.off += len
        }
    }
}
