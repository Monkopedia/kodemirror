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

import com.monkopedia.kodemirror.lezer.common.BufferCursor
import com.monkopedia.kodemirror.lezer.common.Tree
import kotlin.math.min

class Stack(
    val p: Parse,
    val stack: MutableList<Int>,
    var state: Int,
    var reducePos: Int,
    var pos: Int,
    var score: Int,
    var buffer: MutableList<Int>,
    var bufferBase: Int,
    var curContext: StackContext?,
    var lookAhead: Int = 0,
    var parent: Stack?
) {
    override fun toString(): String {
        val states = stack.filterIndexed { i, _ -> i % 3 == 0 } + state
        val scoreStr = if (score != 0) "!$score" else ""
        return "[$states]@$pos$scoreStr"
    }

    companion object {
        fun start(p: Parse, state: Int, pos: Int = 0): Stack {
            val cx = p.parser.context
            return Stack(
                p, mutableListOf(), state, pos, pos, 0, mutableListOf(), 0,
                if (cx != null) StackContext(cx, cx.start) else null,
                0, null
            )
        }
    }

    val context: Any? get() = curContext?.context

    fun pushState(state: Int, start: Int) {
        stack.add(this.state)
        stack.add(start)
        stack.add(bufferBase + buffer.size)
        this.state = state
    }

    fun reduce(action: Int) {
        var depth = action shr Action.REDUCE_DEPTH_SHIFT
        val type = action and Action.VALUE_MASK
        val parser = p.parser
        val lookaheadRecord = reducePos < pos - Lookahead.MARGIN && setLookAhead(pos)
        val dPrec = parser.dynamicPrecedence(type)
        if (dPrec != 0) score += dPrec
        if (depth == 0) {
            pushState(parser.getGoto(state, type, true), reducePos)
            if (type < parser.minRepeatTerm) {
                storeNode(type, reducePos, reducePos, if (lookaheadRecord) 8 else 4, true)
            }
            reduceContext(type, reducePos)
            return
        }
        val base = stack.size - ((depth - 1) * 3) -
            (if ((action and Action.STAY_FLAG) != 0) 6 else 0)
        val start = if (base > 0) stack[base - 2] else p.ranges[0].from
        val size = reducePos - start
        if (size >= Recover.MIN_BIG_REDUCTION &&
            !(p.parser.nodeSet.types.getOrNull(type)?.let { it.name.isEmpty() } == true)
        ) {
            if (start == p.lastBigReductionStart) {
                p.bigReductionCount++
                p.lastBigReductionSize = size
            } else if (p.lastBigReductionSize < size) {
                p.bigReductionCount = 1
                p.lastBigReductionStart = start
                p.lastBigReductionSize = size
            }
        }
        val bufferBase = if (base > 0) stack[base - 1] else 0
        val count = this.bufferBase + buffer.size - bufferBase
        if (type < parser.minRepeatTerm || (action and Action.REPEAT_FLAG) != 0) {
            val storeEnd =
                if (parser.stateFlag(state, StateFlag.SKIPPED)) pos else reducePos
            storeNode(type, start, storeEnd, count + 4, true)
        }
        if ((action and Action.STAY_FLAG) != 0) {
            state = stack[base]
        } else {
            val baseStateID = stack[base - 3]
            state = parser.getGoto(baseStateID, type, true)
        }
        while (stack.size > base) stack.removeAt(stack.size - 1)
        reduceContext(type, start)
    }

    fun storeNode(term: Int, start: Int, end: Int, size: Int = 4, mustSink: Boolean = false) {
        if (term == Term.ERR &&
            (stack.isEmpty() || stack.last() < buffer.size + bufferBase)
        ) {
            var cur: Stack? = this
            var top = buffer.size
            if (top == 0 && cur!!.parent != null) {
                top = cur.bufferBase - cur.parent!!.bufferBase
                cur = cur.parent
            }
            if (top > 0 && cur!!.buffer[top - 4] == Term.ERR && cur.buffer[top - 1] > -1) {
                if (start == end) return
                if (cur.buffer[top - 2] >= start) {
                    cur.buffer[top - 2] = end
                    return
                }
            }
        }
        if (!mustSink || pos == end) {
            buffer.add(term)
            buffer.add(start)
            buffer.add(end)
            buffer.add(size)
        } else {
            var index = buffer.size
            if (index > 0 && (buffer[index - 4] != Term.ERR || buffer[index - 1] < 0)) {
                var mustMove = false
                var scan = index
                while (scan > 0 && buffer[scan - 2] > end) {
                    if (buffer[scan - 1] >= 0) {
                        mustMove = true
                        break
                    }
                    scan -= 4
                }
                if (mustMove) {
                    @Suppress("NAME_SHADOWING")
                    var size = size
                    while (index > 0 && buffer[index - 2] > end) {
                        // Shift elements forward by 4
                        buffer.add(0)
                        buffer.add(0)
                        buffer.add(0)
                        buffer.add(0)
                        buffer[index + 3] = buffer[index - 1]
                        buffer[index + 2] = buffer[index - 2]
                        buffer[index + 1] = buffer[index - 3]
                        buffer[index] = buffer[index - 4]
                        index -= 4
                        if (size > 4) size -= 4
                    }
                }
            }
            // Ensure we have room at index
            while (buffer.size <= index + 3) buffer.add(0)
            buffer[index] = term
            buffer[index + 1] = start
            buffer[index + 2] = end
            buffer[index + 3] = size
        }
    }

    fun shift(action: Int, type: Int, start: Int, end: Int) {
        if ((action and Action.GOTO_FLAG) != 0) {
            pushState(action and Action.VALUE_MASK, pos)
        } else if ((action and Action.STAY_FLAG) == 0) {
            val nextState = action
            val parser = p.parser
            pos = end
            val skipped = parser.stateFlag(nextState, StateFlag.SKIPPED)
            if (!skipped && (end > start || type <= parser.maxNode)) reducePos = end
            pushState(nextState, if (skipped) start else min(start, reducePos))
            shiftContext(type, start)
            if (type <= parser.maxNode) {
                buffer.add(type)
                buffer.add(start)
                buffer.add(end)
                buffer.add(4)
            }
        } else {
            pos = end
            shiftContext(type, start)
            if (type <= p.parser.maxNode) {
                buffer.add(type)
                buffer.add(start)
                buffer.add(end)
                buffer.add(4)
            }
        }
    }

    fun apply(action: Int, next: Int, nextStart: Int, nextEnd: Int) {
        if ((action and Action.REDUCE_FLAG) != 0) {
            reduce(action)
        } else {
            shift(action, next, nextStart, nextEnd)
        }
    }

    fun useNode(value: Tree, next: Int) {
        var index = p.reused.size - 1
        if (index < 0 || p.reused[index] != value) {
            p.reused.add(value)
            index = p.reused.size - 1
        }
        val start = pos
        reducePos = start + value.length
        pos = reducePos
        pushState(next, start)
        buffer.add(index)
        buffer.add(start)
        buffer.add(reducePos)
        buffer.add(-1)
        if (curContext != null) {
            @Suppress("UNCHECKED_CAST")
            val tracker = curContext!!.tracker as ContextTracker<Any?>
            updateContext(
                tracker.reuse(
                    curContext!!.context,
                    value,
                    this,
                    p.stream.reset(pos - value.length)
                )
            )
        }
    }

    fun split(): Stack {
        var parent: Stack? = this
        var off = parent!!.buffer.size
        while (off > 0 && parent!!.buffer[off - 2] > parent.reducePos) off -= 4
        val buffer = parent!!.buffer.subList(off, parent.buffer.size).toMutableList()
        val base = parent.bufferBase + off
        var p: Stack? = parent
        while (p != null && base == p.bufferBase) p = p.parent
        parent = p
        return Stack(
            this.p, stack.toMutableList(), state, reducePos, pos,
            score, buffer, base, curContext, lookAhead, parent
        )
    }

    fun recoverByDelete(next: Int, nextEnd: Int) {
        val isNode = next <= p.parser.maxNode
        if (isNode) storeNode(next, pos, nextEnd, 4)
        storeNode(Term.ERR, pos, nextEnd, if (isNode) 8 else 4)
        pos = nextEnd
        reducePos = nextEnd
        score -= Recover.DELETE
    }

    fun canShift(term: Int): Boolean {
        val sim = SimulatedStack(this)
        while (true) {
            val action = p.parser.stateSlot(sim.state, ParseState.DEFAULT_REDUCE).toInt()
                .takeIf { it != 0 }
                ?: p.parser.hasAction(sim.state, term)
            if (action == 0) return false
            if ((action and Action.REDUCE_FLAG) == 0) return true
            sim.reduce(action)
        }
    }

    fun recoverByInsert(next: Int): List<Stack> {
        if (stack.size >= Recover.MAX_INSERT_STACK_DEPTH) return emptyList()
        val nextStates = p.parser.nextStates(state).toMutableList()
        if (nextStates.size > Recover.MAX_NEXT shl 1 ||
            stack.size >= Recover.DAMPEN_INSERT_STACK_DEPTH
        ) {
            val best = mutableListOf<Int>()
            var i = 0
            while (i < nextStates.size) {
                val s = nextStates[i + 1]
                if (s != state && p.parser.hasAction(s, next) != 0) {
                    best.add(nextStates[i])
                    best.add(s)
                }
                i += 2
            }
            if (stack.size < Recover.DAMPEN_INSERT_STACK_DEPTH) {
                i = 0
                while (best.size < Recover.MAX_NEXT shl 1 && i < nextStates.size) {
                    val s = nextStates[i + 1]
                    if (!best.filterIndexed { idx, _ -> idx % 2 == 1 }.any { it == s }) {
                        best.add(nextStates[i])
                        best.add(s)
                    }
                    i += 2
                }
            }
            nextStates.clear()
            nextStates.addAll(best)
        }
        val result = mutableListOf<Stack>()
        var i = 0
        while (i < nextStates.size && result.size < Recover.MAX_NEXT) {
            val s = nextStates[i + 1]
            if (s != state) {
                val stack = split()
                stack.pushState(s, pos)
                stack.storeNode(Term.ERR, stack.pos, stack.pos, 4, true)
                stack.shiftContext(nextStates[i], pos)
                stack.reducePos = pos
                stack.score -= Recover.INSERT
                result.add(stack)
            }
            i += 2
        }
        return result
    }

    fun forceReduce(): Boolean {
        val parser = p.parser
        val reduce = parser.stateSlot(state, ParseState.FORCED_REDUCE)
        if ((reduce and Action.REDUCE_FLAG) == 0) return false
        var actualReduce = reduce
        if (!parser.validAction(state, reduce)) {
            val depth = reduce shr Action.REDUCE_DEPTH_SHIFT
            val term = reduce and Action.VALUE_MASK
            val target = stack.size - depth * 3
            if (target < 0 || parser.getGoto(stack[target], term, false) < 0) {
                val backup = findForcedReduction() ?: return false
                actualReduce = backup
            }
            storeNode(Term.ERR, pos, pos, 4, true)
            score -= Recover.REDUCE
        }
        reducePos = pos
        reduce(actualReduce)
        return true
    }

    fun findForcedReduction(): Int? {
        val parser = p.parser
        val seen = mutableListOf<Int>()
        fun explore(state: Int, depth: Int): Int? {
            if (seen.contains(state)) return null
            seen.add(state)
            return parser.allActions(state) { action ->
                if ((action and (Action.STAY_FLAG or Action.GOTO_FLAG)) != 0) {
                    null
                } else if ((action and Action.REDUCE_FLAG) != 0) {
                    val rDepth = (action shr Action.REDUCE_DEPTH_SHIFT) - depth
                    if (rDepth > 1) {
                        val term = action and Action.VALUE_MASK
                        val target = this.stack.size - rDepth * 3
                        if (target >= 0 && parser.getGoto(this.stack[target], term, false) >= 0) {
                            (rDepth shl Action.REDUCE_DEPTH_SHIFT) or Action.REDUCE_FLAG or term
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    explore(action, depth + 1)
                }
            }
        }
        return explore(state, 0)
    }

    fun forceAll(): Stack {
        while (!p.parser.stateFlag(state, StateFlag.ACCEPTING)) {
            if (!forceReduce()) {
                storeNode(Term.ERR, pos, pos, 4, true)
                break
            }
        }
        return this
    }

    val deadEnd: Boolean
        get() {
            if (stack.size != 3) return false
            val parser = p.parser
            return parser.data[parser.stateSlot(state, ParseState.ACTIONS)] == Seq.END &&
                parser.stateSlot(state, ParseState.DEFAULT_REDUCE) == 0
        }

    fun restart() {
        storeNode(Term.ERR, pos, pos, 4, true)
        state = stack[0]
        stack.clear()
    }

    fun sameState(other: Stack): Boolean {
        if (state != other.state || stack.size != other.stack.size) return false
        var i = 0
        while (i < stack.size) {
            if (stack[i] != other.stack[i]) return false
            i += 3
        }
        return true
    }

    val parser: LRParser get() = p.parser

    fun dialectEnabled(dialectID: Int): Boolean = p.parser.dialect.flags[dialectID]

    internal fun shiftContext(term: Int, start: Int) {
        if (curContext != null) {
            @Suppress("UNCHECKED_CAST")
            val tracker = curContext!!.tracker as ContextTracker<Any?>
            updateContext(
                tracker.shift(curContext!!.context, term, this, p.stream.reset(start))
            )
        }
    }

    internal fun reduceContext(term: Int, start: Int) {
        if (curContext != null) {
            @Suppress("UNCHECKED_CAST")
            val tracker = curContext!!.tracker as ContextTracker<Any?>
            updateContext(
                tracker.reduce(curContext!!.context, term, this, p.stream.reset(start))
            )
        }
    }

    private fun emitContext() {
        val last = buffer.size - 1
        if (last < 0 || buffer[last] != -3) {
            buffer.add(curContext!!.hash)
            buffer.add(pos)
            buffer.add(pos)
            buffer.add(-3)
        }
    }

    fun emitLookAhead() {
        val last = buffer.size - 1
        if (last < 0 || buffer[last] != -4) {
            buffer.add(lookAhead)
            buffer.add(pos)
            buffer.add(pos)
            buffer.add(-4)
        }
    }

    private fun updateContext(context: Any?) {
        if (context != curContext!!.context) {
            val newCx = StackContext(curContext!!.tracker, context)
            if (newCx.hash != curContext!!.hash) emitContext()
            curContext = newCx
        }
    }

    fun setLookAhead(lookAhead: Int): Boolean {
        if (lookAhead <= this.lookAhead) return false
        emitLookAhead()
        this.lookAhead = lookAhead
        return true
    }

    fun close() {
        if (curContext != null && curContext!!.tracker.strict) emitContext()
        if (lookAhead > 0) emitLookAhead()
    }
}

class StackContext(val tracker: ContextTracker<*>, val context: Any?) {
    val hash: Int = if (tracker.strict) {
        @Suppress("UNCHECKED_CAST")
        (tracker as ContextTracker<Any?>).hash(context)
    } else {
        0
    }
}

class SimulatedStack(val start: Stack) {
    var state: Int = start.state
    var stack: MutableList<Int> = start.stack // shared initially
    var base: Int = stack.size

    fun reduce(action: Int) {
        val term = action and Action.VALUE_MASK
        val depth = action shr Action.REDUCE_DEPTH_SHIFT
        if (depth == 0) {
            if (stack === start.stack) stack = stack.toMutableList()
            stack.add(state)
            stack.add(0)
            stack.add(0)
            base += 3
        } else {
            base -= (depth - 1) * 3
        }
        val goto = start.p.parser.getGoto(stack[base - 3], term, true)
        state = goto
    }
}

class StackBufferCursor(
    var stack: Stack,
    override var pos: Int,
    var index: Int
) : BufferCursor {
    var buffer: MutableList<Int> = stack.buffer

    init {
        if (index == 0) maybeNext()
    }

    companion object {
        fun create(
            stack: Stack,
            pos: Int = stack.bufferBase + stack.buffer.size
        ): StackBufferCursor {
            return StackBufferCursor(stack, pos, pos - stack.bufferBase)
        }
    }

    fun maybeNext() {
        val next = stack.parent
        if (next != null) {
            index = stack.bufferBase - next.bufferBase
            stack = next
            buffer = next.buffer
        } else {
            // Match JS behavior: when no parent, cursor is exhausted.
            // JS returns undefined for negative array indices; we set pos=0
            // so callers' `while (cursor.pos > 0)` loops terminate.
            pos = 0
        }
    }

    override val id: Int get() = buffer.getOrElse(index - 4) { 0 }
    override val start: Int get() = buffer.getOrElse(index - 3) { 0 }
    override val end: Int get() = buffer.getOrElse(index - 2) { 0 }
    override val size: Int get() = buffer.getOrElse(index - 1) { 0 }

    override fun next() {
        index -= 4
        pos -= 4
        if (index == 0) maybeNext()
    }

    override fun fork(): BufferCursor = StackBufferCursor(stack, pos, index)
}
