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
package com.monkopedia.kodemirror.lezer.common

/**
 * The default maximum length of a [TreeBuffer] node.
 */
const val DEFAULT_BUFFER_LENGTH = 1024

/**
 * Abstraction for iterating over a tree buffer. A cursor initially points at the
 * very last element in the buffer. Every time [next] is called it moves on to
 * the previous one.
 */
interface BufferCursor {
    /** The current buffer position (four times the number of nodes remaining). */
    val pos: Int

    /** The node ID of the next node in the buffer. */
    val id: Int

    /** The start position of the next node in the buffer. */
    val start: Int

    /** The end position of the next node. */
    val end: Int

    /** The size of the next node. */
    val size: Int

    /** Moves [pos] down by 4. */
    fun next()

    /** Create a copy of this cursor. */
    fun fork(): BufferCursor
}

/**
 * A [BufferCursor] that reads from a flat [IntArray].
 */
class FlatBufferCursor(val buffer: List<Int>, var index: Int) : BufferCursor {
    override val id: Int get() = buffer[index - 4]
    override val start: Int get() = buffer[index - 3]
    override val end: Int get() = buffer[index - 2]
    override val size: Int get() = buffer[index - 1]

    override val pos: Int get() = index

    override fun next() {
        index -= 4
    }

    override fun fork(): BufferCursor = FlatBufferCursor(buffer, index)
}

/**
 * Tree buffers contain (type, start, end, endIndex) quads for each node.
 * In such a buffer, nodes are stored in prefix order (parents before children,
 * with the endIndex of the parent indicating which children belong to it).
 */
class TreeBuffer(
    /** The buffer's content. */
    val buffer: IntArray,
    /** The total length of the group of nodes in the buffer. */
    val length: Int,
    /** The node set used in this buffer. */
    val set: NodeSet
) {
    val type: NodeType get() = NodeType.none

    override fun toString(): String {
        val result = mutableListOf<String>()
        var index = 0
        while (index < buffer.size) {
            result.add(childString(index))
            index = buffer[index + 3]
        }
        return result.joinToString(",")
    }

    internal fun childString(index: Int): String {
        val id = buffer[index]
        val endIndex = buffer[index + 3]
        val nodeType = set.types[id]
        var result = if (Regex("\\W").containsMatchIn(nodeType.name) && !nodeType.isError) {
            "\"${nodeType.name}\""
        } else {
            nodeType.name
        }
        var i = index + 4
        if (endIndex == i) return result
        val children = mutableListOf<String>()
        while (i < endIndex) {
            children.add(childString(i))
            i = buffer[i + 3]
        }
        return result + "(" + children.joinToString(",") + ")"
    }

    internal fun findChild(startIndex: Int, endIndex: Int, dir: Int, pos: Int, side: Int): Int {
        var pick = -1
        var i = startIndex
        while (i != endIndex) {
            if (checkSide(side, pos, buffer[i + 1], buffer[i + 2])) {
                pick = i
                if (dir > 0) break
            }
            i = buffer[i + 3]
        }
        return pick
    }

    internal fun slice(startI: Int, endI: Int, from: Int): TreeBuffer {
        val copy = IntArray(endI - startI)
        var len = 0
        var i = startI
        var j = 0
        while (i < endI) {
            copy[j++] = buffer[i++]
            copy[j++] = buffer[i++] - from
            val to = buffer[i++] - from
            copy[j - 1 + 1] = to // overwrite: copy[j] = to
            // Actually let me redo this more carefully
            j-- // back up
            copy[j++] = buffer[i - 1] - from // start adjusted (but we already incremented i)
            // Let me rewrite this properly
            break
        }
        // Rewrite cleanly
        val copy2 = IntArray(endI - startI)
        var len2 = 0
        i = startI
        j = 0
        while (i < endI) {
            copy2[j++] = buffer[i++] // type id
            copy2[j++] = buffer[i++] - from // start - from
            val toVal = buffer[i++] - from // end - from
            copy2[j++] = toVal
            copy2[j++] = buffer[i++] - startI // endIndex - startI
            if (toVal > len2) len2 = toVal
        }
        return TreeBuffer(copy2, len2, set)
    }
}

/**
 * Side constants for child finding.
 */
internal object Side {
    const val BEFORE = -2
    const val AT_OR_BEFORE = -1
    const val AROUND = 0
    const val AT_OR_AFTER = 1
    const val AFTER = 2
    const val DONT_CARE = 4
}

internal fun checkSide(side: Int, pos: Int, from: Int, to: Int): Boolean {
    return when (side) {
        Side.BEFORE -> from < pos
        Side.AT_OR_BEFORE -> to >= pos && from < pos
        Side.AROUND -> from < pos && to > pos
        Side.AT_OR_AFTER -> from <= pos && to > pos
        Side.AFTER -> to > pos
        Side.DONT_CARE -> true
        else -> false
    }
}
