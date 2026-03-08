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
import kotlin.math.min

// A range's flags field is used like this:
// - 3 bits for bidi level (7 means unset)
// - 2 bits to indicate the side the cursor is associated with
// - 1 bit for whether the range is inverted (head before anchor)
// - Any further bits hold the goal column
private object RangeFlag {
    const val BIDI_LEVEL_MASK = 7
    const val ASSOC_BEFORE = 8
    const val ASSOC_AFTER = 16
    const val INVERTED = 32
    const val GOAL_COLUMN_OFFSET = 6
    const val NO_GOAL_COLUMN = 0xffffff
}

/**
 * A single selection range. When
 * [allowMultipleSelections][EditorState.allowMultipleSelections]
 * is enabled, a [selection][EditorSelection] may hold multiple
 * ranges. By default, selections hold exactly one range.
 */
class SelectionRange private constructor(
    /** The lower boundary of the range. */
    val from: Int,
    /** The upper boundary of the range. */
    val to: Int,
    private val flags: Int
) {
    /** The anchor of the range—the side that doesn't move. */
    val anchor: Int
        get() =
            if (flags and RangeFlag.INVERTED != 0) to else from

    /** The head of the range, which is moved when extended. */
    val head: Int
        get() =
            if (flags and RangeFlag.INVERTED != 0) from else to

    /** True when `anchor` and `head` are at the same position. */
    val empty: Boolean get() = from == to

    /**
     * If this is a cursor that is explicitly associated with the
     * character on one of its sides, this returns the side. -1
     * means the character before its position, 1 the character
     * after, and 0 means no association.
     */
    val assoc: Int
        get() = when {
            flags and RangeFlag.ASSOC_BEFORE != 0 -> -1
            flags and RangeFlag.ASSOC_AFTER != 0 -> 1
            else -> 0
        }

    /**
     * The bidirectional text level associated with this cursor, if
     * any.
     */
    val bidiLevel: Int?
        get() {
            val level = flags and RangeFlag.BIDI_LEVEL_MASK
            return if (level == 7) null else level
        }

    /**
     * The goal column associated with a cursor.
     */
    val goalColumn: Int?
        get() {
            val value = flags ushr RangeFlag.GOAL_COLUMN_OFFSET
            return if (value == RangeFlag.NO_GOAL_COLUMN) {
                null
            } else {
                value
            }
        }

    /**
     * Map this range through a change, producing a valid range in
     * the updated document.
     */
    fun map(change: ChangeDesc, assoc: Int = -1): SelectionRange {
        val newFrom: Int
        val newTo: Int
        if (empty) {
            val mapped = change.mapPos(from, assoc)
            newFrom = mapped
            newTo = mapped
        } else {
            newFrom = change.mapPos(from, 1)
            newTo = change.mapPos(to, -1)
        }
        return if (newFrom == from && newTo == to) {
            this
        } else {
            SelectionRange(newFrom, newTo, flags)
        }
    }

    /**
     * Extend this range to cover at least [from] to [to].
     */
    fun extend(from: Int, to: Int = from): SelectionRange {
        if (from <= anchor && to >= anchor) {
            return EditorSelection.range(from, to)
        }
        val head = if (abs(from - anchor) > abs(to - anchor)) {
            from
        } else {
            to
        }
        return EditorSelection.range(anchor, head)
    }

    /**
     * Compare this range to another range, optionally including
     * cursor association.
     */
    fun eq(other: SelectionRange, includeAssoc: Boolean): Boolean {
        return anchor == other.anchor &&
            head == other.head &&
            goalColumn == other.goalColumn &&
            (!includeAssoc || empty || assoc == other.assoc)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectionRange) return false
        return eq(other, includeAssoc = false)
    }

    override fun hashCode(): Int {
        var result = anchor
        result = 31 * result + head
        result = 31 * result + (goalColumn ?: 0)
        return result
    }

    /**
     * Return a JSON-serializable object representing the range.
     */
    fun toJSON(): Map<String, Int> = mapOf("anchor" to anchor, "head" to head)

    companion object {
        /**
         * Convert a JSON representation of a range to an instance.
         */
        fun fromJSON(json: Map<String, Any?>): SelectionRange {
            val anchor = json["anchor"] as? Number
                ?: throw IllegalArgumentException(
                    "Invalid JSON representation " +
                        "for SelectionRange"
                )
            val head = json["head"] as? Number
                ?: throw IllegalArgumentException(
                    "Invalid JSON representation " +
                        "for SelectionRange"
                )
            return EditorSelection.range(
                anchor.toInt(),
                head.toInt()
            )
        }

        internal fun create(from: Int, to: Int, flags: Int): SelectionRange =
            SelectionRange(from, to, flags)
    }
}

/**
 * An editor selection holds one or more selection ranges.
 */
class EditorSelection private constructor(
    /**
     * The ranges in the selection, sorted by position. Ranges
     * cannot overlap (but they may touch, if they aren't empty).
     */
    val ranges: List<SelectionRange>,
    /**
     * The index of the _main_ range in the selection (which is
     * usually the range that was added last).
     */
    val mainIndex: Int
) {
    /**
     * Map a selection through a change. Used to adjust the
     * selection position for changes.
     */
    fun map(change: ChangeDesc, assoc: Int = -1): EditorSelection {
        if (change.empty) return this
        return create(
            ranges.map { r -> r.map(change, assoc) },
            mainIndex
        )
    }

    /**
     * Compare this selection to another selection, optionally including
     * cursor association.
     */
    fun eq(other: EditorSelection, includeAssoc: Boolean): Boolean {
        if (ranges.size != other.ranges.size ||
            mainIndex != other.mainIndex
        ) {
            return false
        }
        for (i in ranges.indices) {
            if (!ranges[i].eq(other.ranges[i], includeAssoc)) {
                return false
            }
        }
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EditorSelection) return false
        return eq(other, includeAssoc = false)
    }

    override fun hashCode(): Int {
        var result = ranges.hashCode()
        result = 31 * result + mainIndex
        return result
    }

    /** Get the primary selection range. */
    val main: SelectionRange get() = ranges[mainIndex]

    /** Make sure the selection only has one range. */
    fun asSingle(): EditorSelection = if (ranges.size == 1) {
        this
    } else {
        EditorSelection(listOf(main), 0)
    }

    /** Extend this selection with an extra range. */
    fun addRange(range: SelectionRange, main: Boolean = true): EditorSelection = create(
        listOf(range) + ranges,
        if (main) 0 else mainIndex + 1
    )

    /**
     * Replace a given range with another range, and then normalize
     * the selection to merge and sort ranges if necessary.
     */
    fun replaceRange(range: SelectionRange, which: Int = mainIndex): EditorSelection {
        val newRanges = ranges.toMutableList()
        newRanges[which] = range
        return create(newRanges, mainIndex)
    }

    /** Convert this selection to a JSON object. */
    fun toJSON(): Map<String, Any> = mapOf(
        "ranges" to ranges.map { it.toJSON() },
        "main" to mainIndex
    )

    companion object {
        /**
         * Create a selection from a JSON representation.
         */
        @Suppress("UNCHECKED_CAST")
        fun fromJSON(json: Map<String, Any?>): EditorSelection {
            val rangesJson = json["ranges"] as? List<*>
                ?: throw IllegalArgumentException(
                    "Invalid JSON representation " +
                        "for EditorSelection"
                )
            val mainIndex = (json["main"] as? Number)?.toInt()
                ?: throw IllegalArgumentException(
                    "Invalid JSON representation " +
                        "for EditorSelection"
                )
            if (mainIndex >= rangesJson.size) {
                throw IllegalArgumentException(
                    "Invalid JSON representation " +
                        "for EditorSelection"
                )
            }
            return EditorSelection(
                rangesJson.map {
                    SelectionRange.fromJSON(
                        it as Map<String, Any?>
                    )
                },
                mainIndex
            )
        }

        /** Create a selection holding a single range. */
        fun single(anchor: Int, head: Int = anchor): EditorSelection =
            EditorSelection(listOf(range(anchor, head)), 0)

        /**
         * Sort and merge the given set of ranges, creating a valid
         * selection.
         */
        fun create(ranges: List<SelectionRange>, mainIndex: Int = 0): EditorSelection {
            if (ranges.isEmpty()) {
                throw IllegalArgumentException(
                    "A selection needs at least one range"
                )
            }
            var pos = 0
            for (i in ranges.indices) {
                val range = ranges[i]
                if (if (range.empty) {
                        range.from <= pos
                    } else {
                        range.from < pos
                    }
                ) {
                    return normalized(
                        ranges.toMutableList(),
                        mainIndex
                    )
                }
                pos = range.to
            }
            return EditorSelection(ranges, mainIndex)
        }

        /** Create a cursor selection range at the given position. */
        fun cursor(
            pos: Int,
            assoc: Int = 0,
            bidiLevel: Int? = null,
            goalColumn: Int? = null
        ): SelectionRange = SelectionRange.create(
            pos,
            pos,
            (
                if (assoc == 0) {
                    0
                } else if (assoc < 0) {
                    RangeFlag.ASSOC_BEFORE
                } else {
                    RangeFlag.ASSOC_AFTER
                }
                ) or
                (
                    if (bidiLevel == null) {
                        7
                    } else {
                        min(6, bidiLevel)
                    }
                    ) or
                (
                    (goalColumn ?: RangeFlag.NO_GOAL_COLUMN) shl
                        RangeFlag.GOAL_COLUMN_OFFSET
                    )
        )

        /** Create a selection range. */
        fun range(
            anchor: Int,
            head: Int,
            goalColumn: Int? = null,
            bidiLevel: Int? = null
        ): SelectionRange {
            val flags = (
                (goalColumn ?: RangeFlag.NO_GOAL_COLUMN) shl
                    RangeFlag.GOAL_COLUMN_OFFSET
                ) or (
                if (bidiLevel == null) {
                    7
                } else {
                    min(6, bidiLevel)
                }
                )
            return if (head < anchor) {
                SelectionRange.create(
                    head,
                    anchor,
                    RangeFlag.INVERTED or
                        RangeFlag.ASSOC_AFTER or flags
                )
            } else {
                SelectionRange.create(
                    anchor,
                    head,
                    (
                        if (head > anchor) {
                            RangeFlag.ASSOC_BEFORE
                        } else {
                            0
                        }
                        ) or flags
                )
            }
        }

        internal fun normalized(
            ranges: MutableList<SelectionRange>,
            mainIndex: Int = 0
        ): EditorSelection {
            val main = ranges[mainIndex]
            ranges.sortWith(compareBy { it.from })
            var newMainIndex = ranges.indexOf(main)
            var i = 1
            while (i < ranges.size) {
                val range = ranges[i]
                val prev = ranges[i - 1]
                if (if (range.empty) {
                        range.from <= prev.to
                    } else {
                        range.from < prev.to
                    }
                ) {
                    val from = prev.from
                    val to = max(range.to, prev.to)
                    if (i <= newMainIndex) newMainIndex--
                    ranges.removeAt(i)
                    ranges.removeAt(i - 1)
                    ranges.add(
                        i - 1,
                        if (range.anchor > range.head) {
                            range(to, from)
                        } else {
                            range(from, to)
                        }
                    )
                } else {
                    i++
                }
            }
            return EditorSelection(ranges, newMainIndex)
        }
    }
}

fun checkSelection(selection: EditorSelection, docLength: Int) {
    for (range in selection.ranges) {
        if (range.to > docLength) {
            throw IllegalArgumentException(
                "Selection points outside of document"
            )
        }
    }
}
