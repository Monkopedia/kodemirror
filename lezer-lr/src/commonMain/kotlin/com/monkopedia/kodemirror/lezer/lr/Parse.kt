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
import com.monkopedia.kodemirror.lezer.common.PartialParse
import com.monkopedia.kodemirror.lezer.common.TextRange
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.lezer.common.TreeBuildSpec
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import kotlin.math.max

/**
 * Cache for tokenizer results. Maintains one [CachedToken] per tokenizer
 * in the parser and provides the [getActions] method which determines the
 * set of parse actions available at the current position.
 */
class TokenCache(parser: LRParser, val stream: InputStream) {
    var tokens: List<CachedToken> = parser.tokenizers.map { CachedToken() }
    var mainToken: CachedToken? = null
    val actions: MutableList<Int> = mutableListOf()

    fun getActions(stack: Stack): List<Int> {
        var actionIndex = 0
        var main: CachedToken? = null
        val parser = stack.p.parser
        val tokenizers = parser.tokenizers
        val mask = parser.stateSlot(stack.state, ParseState.TokenizerMask)
        val context =
            if (stack.curContext != null) stack.curContext!!.hash else 0
        var lookAhead = 0
        for (i in tokenizers.indices) {
            if (((1 shl i) and mask) == 0) continue
            val tokenizer = tokenizers[i]
            val token = tokens[i]
            if (main != null && !tokenizer.fallback) continue
            if (tokenizer.contextual ||
                token.start != stack.pos ||
                token.mask != mask ||
                token.context != context
            ) {
                updateCachedToken(token, tokenizer, stack)
                token.mask = mask
                token.context = context
            }
            if (token.lookAhead > token.end + Lookahead.Margin) {
                lookAhead = max(token.lookAhead, lookAhead)
            }
            if (token.value != Term.Err) {
                val startIndex = actionIndex
                if (token.extended > -1) {
                    actionIndex = addActions(
                        stack, token.extended, token.end, actionIndex
                    )
                }
                actionIndex = addActions(
                    stack, token.value, token.end, actionIndex
                )
                if (!tokenizer.extend) {
                    main = token
                    if (actionIndex > startIndex) break
                }
            }
        }
        while (actions.size > actionIndex) actions.removeAt(actions.size - 1)
        if (lookAhead > 0) stack.setLookAhead(lookAhead)
        if (main == null && stack.pos == stream.end) {
            main = CachedToken()
            main.value = stack.p.parser.eofTerm
            main.start = stack.pos
            main.end = stack.pos
            actionIndex = addActions(
                stack, main.value, main.end, actionIndex
            )
            while (actions.size > actionIndex) {
                actions.removeAt(actions.size - 1)
            }
        }
        mainToken = main
        return actions
    }

    fun getMainToken(stack: Stack): CachedToken {
        if (mainToken != null) return mainToken!!
        val token = CachedToken()
        token.start = stack.pos
        token.end = stack.pos
        token.value = if (stack.pos == stream.end) {
            stack.p.parser.eofTerm
        } else {
            Term.Err
        }
        mainToken = token
        return token
    }

    fun updateCachedToken(token: CachedToken, tokenizer: Tokenizer, stack: Stack) {
        val parser = stack.p.parser
        tokenizer.token(stream.reset(stack.pos, token), stack)
        if (token.value > -1) {
            val spec = parser.specialized
            for (i in spec.indices) {
                if (spec[i] == token.value) {
                    val result = parser.specializers[i](
                        stream.read(token.start, token.end),
                        stack
                    )
                    if ((result and 1) == Specialize.Specialize) {
                        token.value = result shr 1
                        break
                    } else if ((result and 1) == Specialize.Extend) {
                        token.extended = result shr 1
                        break
                    }
                }
            }
        } else {
            token.value = Term.Err
            token.end = stack.pos + 1
        }
    }

    fun putAction(action: Int, token: Int, end: Int, index: Int): Int {
        var i = 0
        while (i < index) {
            if (actions[i] == action) return index
            i += 3
        }
        while (actions.size <= index + 2) actions.add(0)
        actions[index] = action
        actions[index + 1] = token
        actions[index + 2] = end
        return index + 3
    }

    fun addActions(stack: Stack, token: Int, end: Int, index: Int): Int {
        var idx = index
        val state = stack.state
        val parser = stack.p.parser
        val data = parser.data
        for (set in 0 until 2) {
            var i = parser.stateSlot(
                state,
                if (set != 0) ParseState.Skip else ParseState.Actions
            )
            while (true) {
                if (data[i] == Seq.End) {
                    if (data[i + 1] == Seq.Next) {
                        i = pair(data, i + 2)
                    } else {
                        if (idx == 0 && data[i + 1] == Seq.Other) {
                            idx = putAction(
                                pair(data, i + 2), token, end, idx
                            )
                        }
                        break
                    }
                }
                if (data[i] == token) {
                    idx = putAction(pair(data, i + 1), token, end, idx)
                }
                i += 3
            }
        }
        return idx
    }
}

/**
 * The core parse driver. Implements [PartialParse] and manages a set
 * of parallel [Stack]s that advance through the input.
 */
class Parse(
    val parser: LRParser,
    val input: Input,
    fragments: List<TreeFragment>,
    val ranges: List<TextRange>
) : PartialParse {

    var stacks: MutableList<Stack>
    var recovering: Int = 0
    internal var fragments: FragmentCursor?
    var nextStackID: Int = 0x2654
    var minStackPos: Int = 0
    val reused: MutableList<Tree> = mutableListOf()
    val stream: InputStream
    val tokens: TokenCache
    val topTerm: Int

    override var stoppedAt: Int? = null
        private set

    var lastBigReductionStart: Int = -1
    var lastBigReductionSize: Int = 0
    var bigReductionCount: Int = 0

    override val parsedPos: Int get() = minStackPos

    init {
        stream = InputStream(input, ranges)
        tokens = TokenCache(parser, stream)
        topTerm = parser.top[1]
        val startState = parser.top[0]
        val startPos = ranges[0].from
        stacks = mutableListOf(Stack.start(this, startState, startPos))
        minStackPos = startPos
        this.fragments =
            if (fragments.isNotEmpty() &&
                parser.nodeSet.types.isNotEmpty()
            ) {
                FragmentCursor(fragments, parser.nodeSet)
            } else {
                null
            }
    }

    override fun advance(): Tree? {
        val stacks = this.stacks
        val pos = this.minStackPos
        val newStacks: MutableList<Stack> = mutableListOf()
        this.stacks = newStacks
        var stopped: MutableList<Stack>? = null
        var stoppedTokens: MutableList<Int>? = null

        // Handle left-associative reduction explosion
        if (bigReductionCount > Rec.MaxLeftAssociativeReductionCount &&
            stacks.size == 1
        ) {
            val s = stacks[0]
            while (s.forceReduce() &&
                s.stack.isNotEmpty() &&
                s.stack[s.stack.size - 2] >= lastBigReductionStart
            ) {
                // keep reducing
            }
            bigReductionCount = 0
            lastBigReductionSize = 0
        }

        // Process each stack
        var i = 0
        outer@ while (i < stacks.size) {
            val stack = stacks[i]
            inner@ while (true) {
                tokens.mainToken = null
                if (stack.pos > pos) {
                    newStacks.add(stack)
                } else if (advanceStack(stack, newStacks, stacks)) {
                    continue@inner
                } else {
                    if (stopped == null) {
                        stopped = mutableListOf()
                        stoppedTokens = mutableListOf()
                    }
                    stopped.add(stack)
                    val tok = tokens.getMainToken(stack)
                    stoppedTokens!!.add(tok.value)
                    stoppedTokens.add(tok.end)
                }
                break@inner
            }
            i++
        }

        if (newStacks.isEmpty()) {
            val finished =
                if (stopped != null) findFinished(stopped) else null
            if (finished != null) return stackToTree(finished)
            if (parser.strict) {
                throw IllegalStateException("No parse at $pos")
            }
            if (recovering == 0) recovering = Rec.Distance
        }

        if (recovering > 0 && stopped != null) {
            val finished = if (stoppedAt != null &&
                stopped[0].pos > stoppedAt!!
            ) {
                stopped[0]
            } else {
                runRecovery(stopped, stoppedTokens!!, newStacks)
            }
            if (finished != null) {
                return stackToTree(finished.forceAll())
            }
        }

        if (recovering > 0) {
            val maxRemaining = if (recovering == 1) {
                1
            } else {
                recovering * Rec.MaxRemainingPerStep
            }
            if (newStacks.size > maxRemaining) {
                newStacks.sortByDescending { it.score }
                while (newStacks.size > maxRemaining) {
                    newStacks.removeAt(newStacks.size - 1)
                }
            }
            if (newStacks.any { it.reducePos > pos }) recovering--
        } else if (newStacks.size > 1) {
            // Prune redundant stacks
            var idx = 0
            outer2@ while (idx < newStacks.size - 1) {
                val stack = newStacks[idx]
                var j = idx + 1
                while (j < newStacks.size) {
                    val other = newStacks[j]
                    if (stack.sameState(other) ||
                        (
                            stack.buffer.size > Rec.MinBufferLengthPrune &&
                                other.buffer.size > Rec.MinBufferLengthPrune
                            )
                    ) {
                        val cmp = (stack.score - other.score).let {
                            if (it != 0) {
                                it
                            } else {
                                stack.buffer.size - other.buffer.size
                            }
                        }
                        if (cmp > 0) {
                            newStacks.removeAt(j)
                        } else {
                            newStacks.removeAt(idx)
                            continue@outer2
                        }
                    } else {
                        j++
                    }
                }
                idx++
            }
            if (newStacks.size > Rec.MaxStackCount) {
                newStacks.sortByDescending { it.score }
                while (newStacks.size > Rec.MaxStackCount) {
                    newStacks.removeAt(newStacks.size - 1)
                }
            }
        }

        this.minStackPos = newStacks[0].pos
        for (k in 1 until newStacks.size) {
            if (newStacks[k].pos < this.minStackPos) {
                this.minStackPos = newStacks[k].pos
            }
        }

        return null
    }

    override fun stopAt(pos: Int) {
        if (stoppedAt != null && stoppedAt!! < pos) {
            throw IllegalArgumentException(
                "Can't move the braked parse ahead"
            )
        }
        stoppedAt = pos
    }

    /**
     * Try to advance a single stack. Returns true if the stack was
     * successfully advanced (shifted or reduced), meaning the caller
     * should continue processing this stack. Returns false if the
     * stack is stuck (no valid action found).
     */
    private fun advanceStack(
        stack: Stack,
        newStacks: MutableList<Stack>?,
        split: MutableList<Stack>?
    ): Boolean {
        val start = stack.pos
        val actions = tokens.getActions(stack)
        val main = tokens.mainToken

        // Try to use a cached tree fragment
        if (main != null && !parser.hasWrappers()) {
            val cached = useCachedResult(stack, main)
            if (cached) return true
        }

        // Default reduce (no token needed)
        val defaultReduce =
            parser.stateSlot(stack.state, ParseState.DefaultReduce)
        if (defaultReduce > 0) {
            stack.reduce(defaultReduce)
            return true
        }

        if (actions.isEmpty()) return false

        // Use the first action; split stacks for any alternatives
        val action0 = actions[0]
        val token0 = if (actions.size >= 2) actions[1] else 0
        val end0 = if (actions.size >= 3) actions[2] else start

        if (actions.size > 3) {
            var ai = 3
            while (ai < actions.size) {
                val action = actions[ai]
                val token = actions[ai + 1]
                val end = actions[ai + 2]
                val s = stack.split()
                s.apply(action, token, start, end)
                if (split != null) {
                    split.add(s)
                } else if (newStacks != null) {
                    pushStackDedup(s, newStacks)
                }
                ai += 3
            }
        }

        stack.apply(action0, token0, start, end0)
        return true
    }

    /**
     * Try to reuse a cached tree node at the current position.
     */
    private fun useCachedResult(stack: Stack, main: CachedToken): Boolean {
        val frags = fragments ?: return false
        val cached = frags.nodeAt(stack.pos) ?: return false
        val type = cached.type
        if (type.id == 0) return false // error node

        val goto = parser.getGoto(stack.state, type.id, false)
        if (goto < 0) return false

        stack.useNode(cached, goto)
        return true
    }

    /**
     * Advance a stack through nested reductions until it progresses
     * past its current position, or until a limit is reached. Used
     * during error recovery after inserting a synthetic token.
     */
    private fun advanceFully(stack: Stack, newStacks: MutableList<Stack>): Boolean {
        val startPos = stack.pos
        var limit = 0
        while (true) {
            if (stack.pos > startPos) {
                pushStackDedup(stack, newStacks)
                return true
            }
            val defaultReduce = parser.stateSlot(
                stack.state,
                ParseState.DefaultReduce
            )
            if (defaultReduce > 0) {
                stack.reduce(defaultReduce)
            } else {
                val actions = tokens.getActions(stack)
                if (actions.isEmpty()) return false
                stack.apply(
                    actions[0],
                    if (actions.size >= 2) actions[1] else 0,
                    startPos,
                    if (actions.size >= 3) actions[2] else startPos
                )
            }
            if (++limit > Rec.ForceReduceLimit) return false
        }
    }

    /**
     * Attempt error recovery on stopped stacks by trying insertions
     * and deletions. Returns a finished stack if one is found during
     * recovery.
     */
    private fun runRecovery(
        stacks: List<Stack>,
        tokens: List<Int>,
        newStacks: MutableList<Stack>
    ): Stack? {
        var finished: Stack? = null

        for (i in stacks.indices) {
            val stack = stacks[i]
            val tokenValue = tokens[i * 2]
            val tokenEnd = tokens[i * 2 + 1]

            // Try insert recovery
            val insertStacks = stack.recoverByInsert(tokenValue)
            for (inserted in insertStacks) {
                advanceFully(inserted, newStacks)
            }

            // If we have a dead end, try restart
            if (stack.deadEnd) {
                if (insertStacks.isEmpty()) {
                    stack.restart()
                    advanceFully(stack, newStacks)
                }
                continue
            }

            // Try delete recovery
            val deletedStack = stack.split()
            deletedStack.recoverByDelete(tokenValue, tokenEnd)
            pushStackDedup(deletedStack, newStacks)
        }

        // Check if any recovered stack has finished
        if (newStacks.isNotEmpty()) {
            finished = findFinished(newStacks)
        }

        return finished
    }

    /**
     * Build a [Tree] from a finished parse [Stack].
     */
    fun stackToTree(stack: Stack): Tree {
        stack.close()
        return Tree.build(
            TreeBuildSpec(
                buffer = StackBufferCursor.create(stack),
                nodeSet = parser.nodeSet,
                topID = topTerm,
                start = ranges[0].from,
                bufferStart = 0,
                length = stack.pos - ranges[0].from,
                maxBufferLength = parser.bufferLength,
                reused = reused,
                minRepeatType = parser.minRepeatTerm
            )
        )
    }

    /** Assign a unique ID string to a stack (for debugging). */
    fun stackID(stack: Stack): String {
        val id = (nextStackID++).toString(36)
        return id
    }
}

/**
 * Push a stack onto [newStacks], deduplicating against existing stacks
 * at the same position and state. If a duplicate is found, the one with
 * the higher score is kept.
 */
fun pushStackDedup(stack: Stack, newStacks: MutableList<Stack>) {
    for (i in newStacks.indices) {
        val other = newStacks[i]
        if (other.pos == stack.pos && other.sameState(stack)) {
            if (other.score < stack.score) {
                newStacks[i] = stack
            }
            return
        }
    }
    newStacks.add(stack)
}

/**
 * Search a list of stacks for one that has reached an accepting state.
 * Returns the best such stack (by score), or null if none are accepting.
 */
fun findFinished(stacks: List<Stack>): Stack? {
    var best: Stack? = null
    for (stack in stacks) {
        if (stack.p.parser.stateFlag(stack.state, StateFlag.Accepting)) {
            if (best == null || best.score < stack.score) {
                best = stack
            }
        }
    }
    return best
}
