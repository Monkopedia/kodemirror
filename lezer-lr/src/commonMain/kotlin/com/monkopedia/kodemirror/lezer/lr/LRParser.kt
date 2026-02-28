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

import com.monkopedia.kodemirror.lezer.common.DefaultBufferLength
import com.monkopedia.kodemirror.lezer.common.Input
import com.monkopedia.kodemirror.lezer.common.NodeProp
import com.monkopedia.kodemirror.lezer.common.NodePropSource
import com.monkopedia.kodemirror.lezer.common.NodeSet
import com.monkopedia.kodemirror.lezer.common.NodeType
import com.monkopedia.kodemirror.lezer.common.NodeTypeSpec
import com.monkopedia.kodemirror.lezer.common.Parser
import com.monkopedia.kodemirror.lezer.common.PartialParse
import com.monkopedia.kodemirror.lezer.common.TextRange
import com.monkopedia.kodemirror.lezer.common.TreeFragment

/** Specializer spec for token specialization. */
data class SpecializerSpec(
    val term: Int,
    val get: ((value: String, stack: Stack) -> Int)? = null,
    val external: ((value: String, stack: Stack) -> Int)? = null,
    val extend: Boolean = false
)

/** Serialized parser specification. */
data class ParserSpec(
    val version: Int,
    val states: String,
    val stateData: String,
    val goto: String,
    val nodeNames: String,
    val maxTerm: Int,
    val repeatNodeCount: Int,
    val nodeProps: List<List<Any>>? = null,
    val propSources: List<NodePropSource<*>>? = null,
    val skippedNodes: List<Int>? = null,
    val tokenData: String,
    // Int or Tokenizer
    val tokenizers: List<Any>,
    val topRules: Map<String, List<Int>>,
    val context: ContextTracker<Any?>? = null,
    val dialects: Map<String, Int>? = null,
    val dynamicPrecedences: Map<Int, Int>? = null,
    val specialized: List<SpecializerSpec>? = null,
    val tokenPrec: Int,
    val termNames: Map<Int, String>? = null
)

/** Configuration for reconfiguring a parser. */
data class ParserConfig(
    val props: List<NodePropSource<*>>? = null,
    val top: String? = null,
    val dialect: String? = null,
    val tokenizers: List<TokenizerReplacement>? = null,
    val specializers: List<SpecializerReplacement>? = null,
    val contextTracker: ContextTracker<Any?>? = null,
    val strict: Boolean? = null,
    val wrap: ParseWrapper? = null,
    val bufferLength: Int? = null
)

data class TokenizerReplacement(val from: ExternalTokenizer, val to: ExternalTokenizer)
data class SpecializerReplacement(
    val from: (String, Stack) -> Int,
    val to: (String, Stack) -> Int
)

typealias ParseWrapper = (
    parse: PartialParse,
    input: Input,
    fragments: List<TreeFragment>,
    ranges: List<TextRange>
) -> PartialParse

/**
 * The main LR parser class. Instances are typically created via
 * [LRParser.deserialize] from a serialized [ParserSpec] produced by the
 * lezer generator.
 */
class LRParser private constructor(
    val states: IntArray,
    val data: IntArray,
    val goto: IntArray,
    val maxTerm: Int,
    val minRepeatTerm: Int,
    tokenizers: List<Tokenizer>,
    val topRules: Map<String, List<Int>>,
    context: ContextTracker<Any?>?,
    val dialects: Map<String, Int>,
    val dynamicPrecedences: Map<Int, Int>?,
    val specialized: IntArray,
    specializers: List<(String, Stack) -> Int>,
    val specializerSpecs: List<SpecializerSpec>,
    val tokenPrecTable: Int,
    val termNames: Map<Int, String>?,
    val maxNode: Int,
    nodeSet: NodeSet,
    dialect: Dialect,
    wrappers: List<ParseWrapper>,
    top: List<Int>,
    bufferLength: Int,
    strict: Boolean
) : Parser() {

    var tokenizers: List<Tokenizer> = tokenizers
        internal set
    var context: ContextTracker<Any?>? = context
        internal set
    var specializers: List<(String, Stack) -> Int> = specializers
        internal set
    var nodeSet: NodeSet = nodeSet
        internal set
    var dialect: Dialect = dialect
        internal set
    var wrappers: List<ParseWrapper> = wrappers
        internal set
    var top: List<Int> = top
        internal set
    var bufferLength: Int = bufferLength
        internal set
    var strict: Boolean = strict
        internal set

    override fun createParse(
        input: Input,
        fragments: List<TreeFragment>,
        ranges: List<TextRange>
    ): PartialParse {
        var parse: PartialParse = Parse(this, input, fragments, ranges)
        for (w in wrappers) parse = w(parse, input, fragments, ranges)
        return parse
    }

    fun getGoto(state: Int, term: Int, loose: Boolean = false): Int {
        val table = goto
        if (term >= table[0]) return -1
        var pos = table[term + 1]
        while (true) {
            val groupTag = table[pos++]
            val last = (groupTag and 1) != 0
            val target = table[pos++]
            if (last && loose) return target
            val end = pos + (groupTag shr 1)
            while (pos < end) {
                if (table[pos] == state) return target
                pos++
            }
            if (last) return -1
        }
    }

    fun hasAction(state: Int, terminal: Int): Int {
        val data = this.data
        for (set in 0 until 2) {
            var i = stateSlot(
                state,
                if (set != 0) ParseState.Skip else ParseState.Actions
            )
            while (true) {
                var next = data[i]
                if (next == Seq.End) {
                    if (data[i + 1] == Seq.Next) {
                        i = pair(data, i + 2)
                        next = data[i]
                    } else if (data[i + 1] == Seq.Other) {
                        return pair(data, i + 2)
                    } else {
                        break
                    }
                }
                if (next == terminal || next == Term.Err) return pair(data, i + 1)
                i += 3
            }
        }
        return 0
    }

    fun stateSlot(state: Int, slot: Int): Int {
        return states[(state * ParseState.Size) + slot]
    }

    fun stateFlag(state: Int, flag: Int): Boolean {
        return (stateSlot(state, ParseState.Flags) and flag) > 0
    }

    fun validAction(state: Int, action: Int): Boolean {
        return allActions(state) { a -> if (a == action) true else null } == true
    }

    fun <T> allActions(state: Int, action: (Int) -> T?): T? {
        val deflt = stateSlot(state, ParseState.DefaultReduce)
        var result: T? = if (deflt != 0) action(deflt) else null
        var i = stateSlot(state, ParseState.Actions)
        while (result == null) {
            if (data[i] == Seq.End) {
                if (data[i + 1] == Seq.Next) {
                    i = pair(data, i + 2)
                } else {
                    break
                }
            }
            result = action(pair(data, i + 1))
            i += 3
        }
        return result
    }

    fun nextStates(state: Int): List<Int> {
        val result = mutableListOf<Int>()
        var i = stateSlot(state, ParseState.Actions)
        while (true) {
            if (data[i] == Seq.End) {
                if (data[i + 1] == Seq.Next) {
                    i = pair(data, i + 2)
                } else {
                    break
                }
            }
            if ((data[i + 2] and (Action.ReduceFlag shr 16)) == 0) {
                val value = data[i + 1]
                if (!result.filterIndexed { idx, _ -> idx % 2 == 1 }.any { it == value }) {
                    result.add(data[i])
                    result.add(value)
                }
            }
            i += 3
        }
        return result
    }

    /**
     * Create a reconfigured copy of this parser with the given overrides.
     */
    fun configure(config: ParserConfig): LRParser {
        val copy = LRParser(
            states = states,
            data = data,
            goto = goto,
            maxTerm = maxTerm,
            minRepeatTerm = minRepeatTerm,
            tokenizers = tokenizers,
            topRules = topRules,
            context = config.contextTracker ?: context,
            dialects = dialects,
            dynamicPrecedences = dynamicPrecedences,
            specialized = specialized,
            specializers = specializers,
            specializerSpecs = specializerSpecs,
            tokenPrecTable = tokenPrecTable,
            termNames = termNames,
            maxNode = maxNode,
            nodeSet = nodeSet,
            dialect = dialect,
            wrappers = wrappers,
            top = top,
            bufferLength = config.bufferLength ?: bufferLength,
            strict = config.strict ?: strict
        )

        if (config.props != null) {
            copy.nodeSet = nodeSet.extend(*config.props.toTypedArray())
        }

        if (config.top != null) {
            val info = topRules[config.top]
                ?: throw IllegalArgumentException(
                    "Invalid top rule name ${config.top}"
                )
            copy.top = info
        }

        if (config.tokenizers != null) {
            copy.tokenizers = tokenizers.map { tok ->
                val replacement = config.tokenizers.find { it.from === tok }
                replacement?.to ?: tok
            }
        }

        if (config.specializers != null) {
            copy.specializers = specializers.map { spec ->
                val replacement = config.specializers.find { it.from === spec }
                replacement?.to ?: spec
            }
        }

        if (config.dialect != null) {
            copy.dialect = copy.parseDialect(config.dialect)
        }

        if (config.wrap != null) {
            copy.wrappers = copy.wrappers + config.wrap
        }

        return copy
    }

    fun hasWrappers(): Boolean = wrappers.isNotEmpty()

    fun getName(term: Int): String {
        return termNames?.get(term) ?: (
            if (term <= maxNode) {
                nodeSet.types[term].name.ifEmpty { term.toString() }
            } else {
                term.toString()
            }
            )
    }

    val eofTerm: Int get() = maxNode + 1

    val topNode: NodeType get() = nodeSet.types[top[1]]

    fun dynamicPrecedence(term: Int): Int {
        return dynamicPrecedences?.get(term) ?: 0
    }

    fun parseDialect(dialect: String? = null): Dialect {
        val values = dialects.keys.toList()
        val flags = MutableList(values.size) { false }
        if (dialect != null) {
            for (part in dialect.split(" ")) {
                val id = values.indexOf(part)
                if (id >= 0) flags[id] = true
            }
        }
        var disabled: IntArray? = null
        for (i in values.indices) {
            if (!flags[i]) {
                var j = dialects[values[i]]!!
                while (true) {
                    val id = data[j++]
                    if (id == Seq.End) break
                    if (disabled == null) disabled = IntArray(maxTerm + 1)
                    disabled[id] = 1
                }
            }
        }
        return Dialect(dialect, flags, disabled)
    }

    companion object {
        /**
         * Deserialize a parser from a [ParserSpec] produced by the lezer
         * parser generator.
         */
        fun deserialize(spec: ParserSpec): LRParser {
            if (spec.version != File.Version) {
                throw IllegalArgumentException(
                    "Parser version (${spec.version}) doesn't match " +
                        "runtime version (${File.Version})"
                )
            }

            val nodeNames = spec.nodeNames.split(" ").toMutableList()
            val minRepeatTerm = nodeNames.size
            repeat(spec.repeatNodeCount) { nodeNames.add("") }

            val topTerms = spec.topRules.values.map { it[1] }
            val nodePropsArr = Array(nodeNames.size) {
                mutableListOf<Pair<NodeProp<*>, Any?>>()
            }

            if (spec.nodeProps != null) {
                for (propSpec in spec.nodeProps) {
                    val prop: NodeProp<Any?> = when (val p = propSpec[0]) {
                        is NodeProp<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            p as NodeProp<Any?>
                        }
                        is String -> getNodePropByName(p)
                        else -> error("Invalid prop spec: $p")
                    }
                    var i = 1
                    while (i < propSpec.size) {
                        val next = propSpec[i++] as Int
                        if (next >= 0) {
                            setProp(nodePropsArr, next, prop, propSpec[i++] as String)
                        } else {
                            val value = propSpec[i + (-next)] as String
                            for (j in -next downTo 1) {
                                setProp(
                                    nodePropsArr,
                                    propSpec[i++] as Int,
                                    prop,
                                    value
                                )
                            }
                            i++
                        }
                    }
                }
            }

            var nodeSet = NodeSet(
                nodeNames.mapIndexed { i, name ->
                    NodeType.define(
                        NodeTypeSpec(
                            name = if (i >= minRepeatTerm) "" else name,
                            id = i,
                            props = nodePropsArr[i],
                            top = topTerms.contains(i),
                            error = i == 0,
                            skipped = spec.skippedNodes?.contains(i) == true
                        )
                    )
                }
            )

            if (spec.propSources != null) {
                nodeSet = nodeSet.extend(*spec.propSources.toTypedArray())
            }

            val tokenArray = decodeArray(spec.tokenData)
            val specializerSpecs = spec.specialized ?: emptyList()
            val specialized =
                IntArray(specializerSpecs.size) { specializerSpecs[it].term }
            val specializers = specializerSpecs.map { getSpecializer(it) }
            val tokenizers = spec.tokenizers.map { value ->
                if (value is Int) TokenGroup(tokenArray, value) else value as Tokenizer
            }
            val maxNode = nodeSet.types.size - 1
            val states = decodeArray(spec.states)
            val data = decodeArray(spec.stateData)
            val goto = decodeArray(spec.goto)

            val parser = LRParser(
                states = states,
                data = data,
                goto = goto,
                maxTerm = spec.maxTerm,
                minRepeatTerm = minRepeatTerm,
                tokenizers = tokenizers,
                topRules = spec.topRules,
                context = spec.context,
                dialects = spec.dialects ?: emptyMap(),
                dynamicPrecedences = spec.dynamicPrecedences,
                specialized = specialized,
                specializers = specializers,
                specializerSpecs = specializerSpecs,
                tokenPrecTable = spec.tokenPrec,
                termNames = spec.termNames,
                maxNode = maxNode,
                nodeSet = nodeSet,
                // temporary, will be replaced below
                dialect = Dialect(null, emptyList(), null),
                wrappers = emptyList(),
                top = spec.topRules.values.first(),
                bufferLength = DefaultBufferLength,
                strict = false
            )
            parser.dialect = parser.parseDialect()
            return parser
        }
    }
}

internal fun pair(data: IntArray, off: Int): Int = data[off] or (data[off + 1] shl 16)

internal fun getSpecializer(spec: SpecializerSpec): (String, Stack) -> Int {
    if (spec.external != null) {
        val mask =
            if (spec.extend) Specialize.Extend else Specialize.Specialize
        return { value, stack ->
            (spec.external.invoke(value, stack) shl 1) or mask
        }
    }
    return spec.get!!
}

@Suppress("UNCHECKED_CAST")
internal fun getNodePropByName(name: String): NodeProp<Any?> {
    return when (name) {
        "group" -> NodeProp.group as NodeProp<Any?>
        "closedBy" -> NodeProp.closedBy as NodeProp<Any?>
        "openedBy" -> NodeProp.openedBy as NodeProp<Any?>
        "top" -> NodeProp.top as NodeProp<Any?>
        "contextHash" -> NodeProp.contextHash as NodeProp<Any?>
        "lookAhead" -> NodeProp.lookAhead as NodeProp<Any?>
        "skipped" -> NodeProp.skipped as NodeProp<Any?>
        else -> throw IllegalArgumentException("Unknown node prop: $name")
    }
}

private fun setProp(
    nodeProps: Array<MutableList<Pair<NodeProp<*>, Any?>>>,
    nodeID: Int,
    prop: NodeProp<Any?>,
    value: String
) {
    val deserialized = prop.deserialize?.invoke(value) ?: value
    nodeProps[nodeID].add(prop to deserialized)
}
