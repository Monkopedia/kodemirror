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
package com.monkopedia.kodemirror.lezer.lr

import com.monkopedia.kodemirror.lezer.common.Input
import com.monkopedia.kodemirror.lezer.common.TextRange
import kotlin.math.max
import kotlin.math.min

class CachedToken {
    var start: Int = -1
    var value: Int = -1
    var end: Int = -1
    var extended: Int = -1
    var lookAhead: Int = 0
    var mask: Int = 0
    var context: Int = 0
}

val nullToken = CachedToken()

interface Tokenizer {
    fun token(input: InputStream, stack: Stack)
    val contextual: Boolean
    val fallback: Boolean
    val extend: Boolean
}

/**
 * Tokenizers interact with the input through this interface. It presents the
 * input as a stream of characters, tracking lookahead and hiding the complexity
 * of ranges from tokenizer code.
 */
class InputStream(
    val input: Input,
    val ranges: List<TextRange>
) {
    var chunk: String = ""
    var chunkOff: Int = 0
    var chunkPos: Int
    private var chunk2: String = ""
    private var chunk2Pos: Int = 0

    /** The character code of the next code unit in the input, or -1 at EOF. */
    var next: Int = -1

    var token: CachedToken = nullToken

    /** The current position of the stream. */
    var pos: Int

    var end: Int

    private var rangeIndex: Int = 0
    private var range: TextRange

    init {
        pos = ranges[0].from
        chunkPos = ranges[0].from
        range = ranges[0]
        end = ranges[ranges.size - 1].to
        readNext()
    }

    fun resolveOffset(offset: Int, assoc: Int): Int? {
        var currentRange = this.range
        var index = this.rangeIndex
        var resolvedPos = this.pos + offset
        while (resolvedPos < currentRange.from) {
            if (index == 0) return null
            val next = this.ranges[--index]
            resolvedPos -= currentRange.from - next.to
            currentRange = next
        }
        while (if (assoc < 0) resolvedPos > currentRange.to else resolvedPos >= currentRange.to) {
            if (index == this.ranges.size - 1) return null
            val next = this.ranges[++index]
            resolvedPos += next.from - currentRange.to
            currentRange = next
        }
        return resolvedPos
    }

    fun clipPos(pos: Int): Int {
        if (pos >= this.range.from && pos < this.range.to) return pos
        for (range in this.ranges) {
            if (range.to > pos) return max(pos, range.from)
        }
        return this.end
    }

    /**
     * Look at a code unit near the stream position. `.peek(0)` equals
     * `.next`, `.peek(-1)` gives you the previous character, and so on.
     */
    fun peek(offset: Int): Int {
        val idx = this.chunkOff + offset
        val peekPos: Int
        val result: Int
        if (idx >= 0 && idx < this.chunk.length) {
            peekPos = this.pos + offset
            result = this.chunk[idx].code
        } else {
            val resolved = this.resolveOffset(offset, 1) ?: return -1
            peekPos = resolved
            if (peekPos >= this.chunk2Pos && peekPos < this.chunk2Pos + this.chunk2.length) {
                result = this.chunk2[peekPos - this.chunk2Pos].code
            } else {
                var i = this.rangeIndex
                var r = this.range
                while (r.to <= peekPos) r = this.ranges[++i]
                this.chunk2 = this.input.chunk(peekPos)
                this.chunk2Pos = peekPos
                if (peekPos + this.chunk2.length > r.to) {
                    this.chunk2 = this.chunk2.substring(0, r.to - peekPos)
                }
                result = this.chunk2[0].code
            }
        }
        if (peekPos >= this.token.lookAhead) this.token.lookAhead = peekPos + 1
        return result
    }

    /**
     * Accept a token. By default, the end of the token is set to the
     * current stream position, but you can pass an offset (relative to
     * the stream position) to change that.
     */
    fun acceptToken(token: Int, endOffset: Int = 0) {
        val end = if (endOffset != 0) this.resolveOffset(endOffset, -1) else this.pos
        if (end == null || end < this.token.start) {
            throw IllegalArgumentException("Token end out of bounds")
        }
        this.token.value = token
        this.token.end = end
    }

    /** Accept a token ending at a specific given position. */
    fun acceptTokenTo(token: Int, endPos: Int) {
        this.token.value = token
        this.token.end = endPos
    }

    private fun getChunk() {
        if (this.pos >= this.chunk2Pos && this.pos < this.chunk2Pos + this.chunk2.length) {
            val tmpChunk = this.chunk
            val tmpChunkPos = this.chunkPos
            this.chunk = this.chunk2
            this.chunkPos = this.chunk2Pos
            this.chunk2 = tmpChunk
            this.chunk2Pos = tmpChunkPos
            this.chunkOff = this.pos - this.chunkPos
        } else {
            this.chunk2 = this.chunk
            this.chunk2Pos = this.chunkPos
            val nextChunk = this.input.chunk(this.pos)
            val chunkEnd = this.pos + nextChunk.length
            this.chunk =
                if (chunkEnd > this.range.to) {
                    nextChunk.substring(0, this.range.to - this.pos)
                } else {
                    nextChunk
                }
            this.chunkPos = this.pos
            this.chunkOff = 0
        }
    }

    private fun readNext(): Int {
        if (this.chunkOff >= this.chunk.length) {
            this.getChunk()
            if (this.chunkOff == this.chunk.length) {
                this.next = -1
                return this.next
            }
        }
        this.next = this.chunk[this.chunkOff].code
        return this.next
    }

    /**
     * Move the stream forward N (defaults to 1) code units. Returns
     * the new value of [next].
     */
    fun advance(n: Int = 1): Int {
        this.chunkOff += n
        var remaining = n
        while (this.pos + remaining >= this.range.to) {
            if (this.rangeIndex == this.ranges.size - 1) return this.setDone()
            remaining -= this.range.to - this.pos
            this.range = this.ranges[++this.rangeIndex]
            this.pos = this.range.from
        }
        this.pos += remaining
        if (this.pos >= this.token.lookAhead) this.token.lookAhead = this.pos + 1
        return this.readNext()
    }

    private fun setDone(): Int {
        this.pos = this.end
        this.chunkPos = this.end
        this.rangeIndex = this.ranges.size - 1
        this.range = this.ranges[this.rangeIndex]
        this.chunk = ""
        this.next = -1
        return this.next
    }

    fun reset(pos: Int, token: CachedToken? = null): InputStream {
        if (token != null) {
            this.token = token
            token.start = pos
            token.lookAhead = pos + 1
            token.value = -1
            token.extended = -1
        } else {
            this.token = nullToken
        }
        if (this.pos != pos) {
            this.pos = pos
            if (pos == this.end) {
                this.setDone()
                return this
            }
            while (pos < this.range.from) this.range = this.ranges[--this.rangeIndex]
            while (pos >= this.range.to) this.range = this.ranges[++this.rangeIndex]
            if (pos >= this.chunkPos && pos < this.chunkPos + this.chunk.length) {
                this.chunkOff = pos - this.chunkPos
            } else {
                this.chunk = ""
                this.chunkOff = 0
            }
            this.readNext()
        }
        return this
    }

    fun read(from: Int, to: Int): String {
        if (from >= this.chunkPos && to <= this.chunkPos + this.chunk.length) {
            return this.chunk.substring(from - this.chunkPos, to - this.chunkPos)
        }
        if (from >= this.chunk2Pos && to <= this.chunk2Pos + this.chunk2.length) {
            return this.chunk2.substring(from - this.chunk2Pos, to - this.chunk2Pos)
        }
        if (from >= this.range.from && to <= this.range.to) {
            return this.input.read(from, to)
        }
        var result = ""
        for (r in this.ranges) {
            if (r.from >= to) break
            if (r.to > from) {
                result += this.input.read(max(r.from, from), min(r.to, to))
            }
        }
        return result
    }
}

class TokenGroup(val data: IntArray, val id: Int) : Tokenizer {
    override val contextual: Boolean = false
    override val fallback: Boolean = false
    override val extend: Boolean = false

    override fun token(input: InputStream, stack: Stack) {
        val parser = stack.p.parser
        readToken(data, input, stack, id, parser.data, parser.tokenPrecTable)
    }
}

class LocalTokenGroup(
    data: Any,
    val precTable: Int,
    val elseToken: Int? = null
) : Tokenizer {
    val data: IntArray = if (data is String) decodeArray(data) else data as IntArray
    override val contextual: Boolean = false
    override val fallback: Boolean = false
    override val extend: Boolean = false

    override fun token(input: InputStream, stack: Stack) {
        val start = input.pos
        var skipped = 0
        while (true) {
            val atEof = input.next < 0
            val nextPos = input.resolveOffset(1, 1)
            readToken(this.data, input, stack, 0, this.data, this.precTable)
            if (input.token.value > -1) break
            if (this.elseToken == null) return
            if (!atEof) skipped++
            if (nextPos == null) break
            input.reset(nextPos, input.token)
        }
        if (skipped > 0) {
            input.reset(start, input.token)
            input.acceptToken(this.elseToken!!, skipped)
        }
    }
}

/**
 * `@external tokens` declarations in the grammar should resolve to
 * an instance of this class.
 */
class ExternalTokenizer(
    private val tokenFn: (input: InputStream, stack: Stack) -> Unit,
    override val contextual: Boolean = false,
    override val fallback: Boolean = false,
    override val extend: Boolean = false
) : Tokenizer {
    override fun token(input: InputStream, stack: Stack) = tokenFn(input, stack)
}

// Tokenizer data is stored in a big IntArray containing, for each state:
//
//  - A group bitmask, indicating what token groups are reachable from
//    this state, so that paths that can only lead to tokens not in
//    any of the current groups can be cut off early.
//
//  - The position of the end of the state's sequence of accepting tokens
//
//  - The number of outgoing edges for the state
//
//  - The accepting tokens, as (token id, group mask) pairs
//
//  - The outgoing edges, as (start character, end character, state
//    index) triples, with end character being exclusive
//
// This function interprets that data, running through a stream as
// long as new states with a matching group mask can be reached,
// and updating `input.token` when it matches a token.
internal fun readToken(
    data: IntArray,
    input: InputStream,
    stack: Stack,
    group: Int,
    precTable: IntArray,
    precOffset: Int
) {
    var state = 0
    val groupMask = 1 shl group
    val dialect = stack.p.parser.dialect
    scan@ while (true) {
        if ((groupMask and data[state]) == 0) break
        val accEnd = data[state + 1]
        // Check whether this state can lead to a token in the current group
        // Accept tokens in this state, possibly overwriting
        // lower-precedence / shorter tokens
        var i = state + 3
        while (i < accEnd) {
            if ((data[i + 1] and groupMask) > 0) {
                val term = data[i]
                if (dialect.allows(term) &&
                    (
                        input.token.value == -1 || input.token.value == term ||
                            overrides(term, input.token.value, precTable, precOffset)
                        )
                ) {
                    input.acceptToken(term)
                    break
                }
            }
            i += 2
        }
        val next = input.next
        var low = 0
        var high = data[state + 2]
        // Special case for EOF
        if (input.next < 0 && high > low && data[accEnd + high * 3 - 3] == Seq.End) {
            state = data[accEnd + high * 3 - 1]
            continue@scan
        }
        // Do a binary search on the state's edges
        while (low < high) {
            val mid = (low + high) shr 1
            val index = accEnd + mid + (mid shl 1)
            val from = data[index]
            val to = if (data[index + 1] != 0) data[index + 1] else 0x10000
            if (next < from) {
                high = mid
            } else if (next >= to) {
                low = mid + 1
            } else {
                state = data[index + 2]
                input.advance()
                continue@scan
            }
        }
        break
    }
}

internal fun findOffset(data: IntArray, start: Int, term: Int): Int {
    var i = start
    while (true) {
        val next = data[i]
        if (next == Seq.End) return -1
        if (next == term) return i - start
        i++
    }
}

internal fun overrides(token: Int, prev: Int, tableData: IntArray, tableOffset: Int): Boolean {
    val iPrev = findOffset(tableData, tableOffset, prev)
    return iPrev < 0 || findOffset(tableData, tableOffset, token) < iPrev
}
