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

import kotlin.math.abs
import kotlin.math.max
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

// import {ChangeDesc} from "./change"

// A range's flags field is used like this:
// - 2 bits for bidi level (3 means unset) (only meaningful for
//   cursors)
// - 2 bits to indicate the side the cursor is associated with (only
//   for cursors)
// - 1 bit to indicate whether the range is inverted (head before
//   anchor) (only meaningful for non-empty ranges)
// - Any further bits hold the goal column (only for ranges
//   produced by vertical motion)
internal enum class RangeFlag(val mask: Int) {
    BidiLevelMask(7),
    AssocBefore(8),
    AssocAfter(16),
    Inverted(32),
    GoalColumnOffset(6),
    NoGoalColumn(0xffffff)
}

// / A single selection range. When
// / [`allowMultipleSelections`](#state.EditorState^allowMultipleSelections)
// / is enabled, a [selection](#state.EditorSelection) may hold
// / multiple ranges. By default, selections hold exactly one range.
@Serializable
class SelectionRange private constructor(
    // / The lower boundary of the range.
    val from: Int,
    // / The upper boundary of the range.
    val to: Int,
    private val flags: Int
) {

    // / The anchor of the range—the side that doesn't move when you
    // / extend it.
    val anchor: Int
        get() {
            return if (this.flags and RangeFlag.Inverted.mask != 0) this.to else this.from
        }

    // / The head of the range, which is moved when the range is
    // / [extended](#state.SelectionRange.extend).
    val head: Int
        get() {
            return if (this.flags and RangeFlag.Inverted.mask != 0) this.from else this.to
        }

    // / True when `anchor` and `head` are at the same position.
    val empty: Boolean
        get() {
            return this.from == this.to
        }

    // / If this is a cursor that is explicitly associated with the
    // / character on one of its sides, this returns the side. -1 means
    // / the character before its position, 1 the character after, and 0
    // / means no association.
    val assoc: Side
        get() {
            return when {
                this.flags and RangeFlag.AssocBefore.mask != 0 -> Side.NEG
                this.flags and RangeFlag.AssocAfter.mask != 0 -> Side.POS
                else -> Side.ZERO
            }
        }

    // / The bidirectional text level associated with this cursor, if
    // / any.
    val bidiLevel: Int?
        get() {
            val level = this.flags and RangeFlag.BidiLevelMask.mask
            return if (level == 7) null else level
        }

    // / The goal column (stored vertical offset) associated with a
    // / cursor. This is used to preserve the vertical position when
    // / [moving](#view.EditorView.moveVertically) across
    // / lines of different length.
    val goalColumn: Int?
        get() {
            val value = this.flags shr RangeFlag.GoalColumnOffset.mask
            return if (value == RangeFlag.NoGoalColumn.mask) null else value
        }

    // / Map this range through a change, producing a valid range in the
    // / updated document.
    fun map(change: ChangeDesc, assoc: Side = Side.NEG): SelectionRange {
        val from: Int?
        val to: Int?
        if (this.empty) {
            to = change.mapPos(this.from, assoc)
            from = to
        } else {
            from = change.mapPos(this.from, Side.POS)
            to = change.mapPos(this.to, Side.NEG)
        }
        return if (from == this.from && to == this.to) {
            this
        } else {
            SelectionRange(from!!, to!!, this.flags)
        }
    }

    // / Extend this range to cover at least `from` to `to`.
    fun extend(from: Int, to: Int = from): SelectionRange {
        if (this.anchor in from..to) return EditorSelection.range(from, to)
        val head = if (abs(from - this.anchor) > abs(to - this.anchor)) from else to
        return EditorSelection.range(this.anchor, head)
    }

    // / Compare this range to another range.
    fun eq(other: SelectionRange, includeAssoc: Boolean = false): Boolean =
        this.anchor == other.anchor &&
            this.head == other.head &&
            (!includeAssoc || !this.empty || this.assoc == other.assoc)

    // / Return a JSON-serializable object representing the range.
//    fun toJSON(): any { return {anchor: this.anchor, head: this.head} }

    companion object {

        // / Convert a JSON representation of a range to a `SelectionRange`
        // / instance.
//        fun fromJSON(json: any): SelectionRange {
//            if (!json || typeof json . anchor != "Int" || typeof json . head != "Int")
//            throw new RangeError ("Invalid JSON representation for SelectionRange")
//            return EditorSelection.range(json.anchor, json.head)
//        }

        // / @internal
        fun create(from: Int, to: Int, flags: Int): SelectionRange = SelectionRange(from, to, flags)
    }
}

sealed interface Selection {

    data class Data(val anchor: Int, val head: Int? = null) : Selection
}

// / An editor selection holds one or more selection ranges.
@Serializable
class EditorSelection
private constructor(
    // / The ranges in the selection, sorted by position. Ranges cannot
    // / overlap (but they may touch, if they aren't empty).
    val ranges: List<SelectionRange>,
    // / The index of the _main_ range in the selection (which is
    // / usually the range that was added last).
    val mainIndex: Int
) : Selection {

    // / Map a selection through a change. Used to adjust the selection
    // / position for changes.
    fun map(change: ChangeDesc, assoc: Side = Side.NEG): EditorSelection {
        if (change.isEmpty) return this
        return EditorSelection.create(this.ranges.map { r -> r.map(change, assoc) }, this.mainIndex)
    }

    // / Compare this selection to another selection. By default, ranges
    // / are compared only by position. When `includeAssoc` is true,
    // / cursor ranges must also have the same
    // / [`assoc`](#state.SelectionRange.assoc) value.
    fun eq(other: EditorSelection, includeAssoc: Boolean = false): Boolean {
        if (this.ranges.size != other.ranges.size ||
            this.mainIndex != other.mainIndex
        ) {
            return false
        }
        for (i in this.ranges.indices) {
            if (!this.ranges[i].eq(other.ranges[i], includeAssoc)) return false
        }
        return true
    }

    // / Get the primary selection range. Usually, you should make sure
    // / your code applies to _all_ ranges, by using methods like
    // / [`changeByRange`](#state.EditorState.changeByRange).
    val main: SelectionRange
        get() {
            return this.ranges[this.mainIndex]
        }

    // / Make sure the selection only has one range. Returns a selection
    // / holding only the main range from this selection.
    fun asSingle(): EditorSelection =
        if (this.ranges.size == 1) this else EditorSelection(listOf(this.main), 0)

    // / Extend this selection with an extra range.
    fun addRange(range: SelectionRange, main: Boolean = true): EditorSelection =
        EditorSelection.create(
            listOf(range) + this.ranges,
            if (main) 0 else this.mainIndex + 1
        )

    // / Replace a given range with another range, and then normalize the
    // / selection to merge and sort ranges if necessary.
    fun replaceRange(range: SelectionRange, which: Int = this.mainIndex): EditorSelection {
        val ranges = this.ranges.toMutableList()
        ranges[which] = range
        return EditorSelection.create(ranges, this.mainIndex)
    }

    // / Convert this selection to an object that can be serialized to
    // / JSON.
    fun toJSON(): JsonElement {
        return Json.encodeToJsonElement(this)
    }

    companion object {
        // / Create a selection from a JSON representation.
        fun fromJSON(json: JsonElement): EditorSelection {
            return Json.decodeFromJsonElement(json)
        }

        // / Create a selection holding a single range.
        fun single(anchor: Int, head: Int = anchor): EditorSelection =
            EditorSelection(listOf(EditorSelection.range(anchor, head)), 0)

        // / Sort and merge the given set of ranges, creating a valid
        // / selection.
        fun create(ranges: List<SelectionRange>, mainIndex: Int = 0): EditorSelection {
            if (ranges.isEmpty()) {
                throw IllegalArgumentException(
                    "A selection needs at least one range"
                )
            }
            var pos = 0
            for (i in ranges.indices) {
                val range = ranges[i]
                if (if (range.empty) range.from <= pos else range.from < pos) {
                    return EditorSelection.normalized(ranges.toList(), mainIndex)
                }
                pos = range.to
            }
            return EditorSelection(ranges, mainIndex)
        }

        // / Create a cursor selection range at the given position. You can
        // / safely ignore the optional arguments in most situations.
        fun cursor(
            pos: Int,
            assoc: Side = Side.ZERO,
            bidiLevel: Int? = null,
            goalColumn: Int? = null
        ): SelectionRange = SelectionRange.create(
            pos,
            pos,
            (
                if (assoc ==
                    Side.ZERO
                ) {
                    0
                } else if (assoc ==
                    Side.NEG
                ) {
                    RangeFlag.AssocBefore.mask
                } else {
                    RangeFlag.AssocAfter.mask
                }
                ) or
                (bidiLevel?.coerceAtMost(6) ?: 7) or
                (
                    (
                        goalColumn
                            ?: RangeFlag.NoGoalColumn.mask
                        ) shl RangeFlag.GoalColumnOffset.mask
                    )
        )

        // / Create a selection range.
        fun range(
            anchor: Int,
            head: Int,
            goalColumn: Int? = null,
            bidiLevel: Int? = null
        ): SelectionRange {
            val flags =
                ((goalColumn ?: RangeFlag.NoGoalColumn.mask) shl RangeFlag.GoalColumnOffset.mask) or
                    (bidiLevel?.coerceAtMost(6) ?: 7)
            return if (head < anchor) {
                SelectionRange.create(
                    head,
                    anchor,
                    RangeFlag.Inverted.mask or RangeFlag.AssocAfter.mask or flags
                )
            } else {
                SelectionRange.create(
                    anchor,
                    head,
                    (if (head > anchor) RangeFlag.AssocBefore.mask else 0) or flags
                )
            }
        }

        // / @internal
        internal fun normalized(ranges: List<SelectionRange>, mainIndex: Int = 0): EditorSelection {
            val main = ranges[mainIndex]
            val ranges = ranges.sortedBy { it.from }.toMutableList()
            var mainIndex = ranges.indexOf(main)
            var i = 1
            while (i < ranges.size) {
                val range = ranges[i]
                val prev = ranges[i - 1]
                if (if (range.empty) range.from <= prev.to else range.from < prev.to) {
                    val from = prev.from
                    val to = max(range.to, prev.to)
                    if (i <= mainIndex) {
                        mainIndex--
                    }
                    ranges[i] = if (range.anchor > range.head) {
                        EditorSelection.range(
                            to,
                            from
                        )
                    } else {
                        EditorSelection.range(from, to)
                    }
                    ranges.removeAt(--i)
                }
                i++
            }
            return EditorSelection(ranges, mainIndex)
        }
    }
}

fun checkSelection(selection: EditorSelection, docLength: Int) {
    for (range in selection.ranges) {
        if (range.to > docLength) {
            println("Range ${range.to} $docLength")
            throw IllegalArgumentException("Selection points outside of document")
        }
    }
}
