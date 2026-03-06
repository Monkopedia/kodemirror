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
package com.monkopedia.kodemirror.legacy.modes

import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.StringStream

private object Location {
    const val PRE_SUBJECT = 0
    const val WRITING_SUB_URI = 1
    const val WRITING_BNODE_URI = 2
    const val PRE_PRED = 3
    const val WRITING_PRED_URI = 4
    const val PRE_OBJ = 5
    const val WRITING_OBJ_URI = 6
    const val WRITING_OBJ_BNODE = 7
    const val WRITING_OBJ_LITERAL = 8
    const val WRITING_LIT_LANG = 9
    const val WRITING_LIT_TYPE = 10
    const val POST_OBJ = 11
    const val ERROR = 12
}

data class NtriplesState(
    var location: Int = Location.PRE_SUBJECT,
    val uris: MutableList<String> = mutableListOf(),
    val anchors: MutableList<String> = mutableListOf(),
    val bnodes: MutableList<String> = mutableListOf(),
    val langs: MutableList<String> = mutableListOf(),
    val types: MutableList<String> = mutableListOf()
)

private fun transitState(state: NtriplesState, c: Char) {
    val loc = state.location
    state.location = when {
        loc == Location.PRE_SUBJECT && c == '<' -> Location.WRITING_SUB_URI
        loc == Location.PRE_SUBJECT && c == '_' -> Location.WRITING_BNODE_URI
        loc == Location.PRE_PRED && c == '<' -> Location.WRITING_PRED_URI
        loc == Location.PRE_OBJ && c == '<' -> Location.WRITING_OBJ_URI
        loc == Location.PRE_OBJ && c == '_' -> Location.WRITING_OBJ_BNODE
        loc == Location.PRE_OBJ && c == '"' -> Location.WRITING_OBJ_LITERAL
        loc == Location.WRITING_SUB_URI && c == '>' -> Location.PRE_PRED
        loc == Location.WRITING_BNODE_URI && c == ' ' -> Location.PRE_PRED
        loc == Location.WRITING_PRED_URI && c == '>' -> Location.PRE_OBJ
        loc == Location.WRITING_OBJ_URI && c == '>' -> Location.POST_OBJ
        loc == Location.WRITING_OBJ_BNODE && c == ' ' -> Location.POST_OBJ
        loc == Location.WRITING_OBJ_LITERAL && c == '"' -> Location.POST_OBJ
        loc == Location.WRITING_LIT_LANG && c == ' ' -> Location.POST_OBJ
        loc == Location.WRITING_LIT_TYPE && c == '>' -> Location.POST_OBJ
        loc == Location.WRITING_OBJ_LITERAL && c == '@' -> Location.WRITING_LIT_LANG
        loc == Location.WRITING_OBJ_LITERAL && c == '^' -> Location.WRITING_LIT_TYPE
        c == ' ' && loc in listOf(
            Location.PRE_SUBJECT, Location.PRE_PRED,
            Location.PRE_OBJ, Location.POST_OBJ
        ) -> loc
        loc == Location.POST_OBJ && c == '.' -> Location.PRE_SUBJECT
        else -> Location.ERROR
    }
}

/** Stream parser for N-Triples (RDF). */
val ntriples: StreamParser<NtriplesState> = object : StreamParser<NtriplesState> {
    override val name: String get() = "ntriples"
    override fun startState(indentUnit: Int) = NtriplesState()

    override fun copyState(state: NtriplesState) = NtriplesState(
        location = state.location,
        uris = state.uris.toMutableList(),
        anchors = state.anchors.toMutableList(),
        bnodes = state.bnodes.toMutableList(),
        langs = state.langs.toMutableList(),
        types = state.types.toMutableList()
    )

    override fun token(stream: StringStream, state: NtriplesState): String? {
        val ch = stream.next() ?: return null
        val c = ch[0]
        if (c == '<') {
            transitState(state, c)
            val sb = StringBuilder()
            stream.eatWhile { cc ->
                if (cc[0] != '#' && cc[0] != '>') {
                    sb.append(cc)
                    true
                } else {
                    false
                }
            }
            state.uris.add(sb.toString())
            if (stream.match("#", consume = false)) return "variableName"
            stream.next()
            transitState(state, '>')
            return "variableName"
        }
        if (c == '#') {
            val sb = StringBuilder()
            stream.eatWhile { cc ->
                if (cc[0] != '>' && cc[0] != ' ') {
                    sb.append(cc)
                    true
                } else {
                    false
                }
            }
            state.anchors.add(sb.toString())
            return "url"
        }
        if (c == '>') {
            transitState(state, '>')
            return "variableName"
        }
        if (c == '_') {
            transitState(state, c)
            val sb = StringBuilder()
            stream.eatWhile { cc ->
                if (cc[0] != ' ') {
                    sb.append(cc)
                    true
                } else {
                    false
                }
            }
            state.bnodes.add(sb.toString())
            stream.next()
            transitState(state, ' ')
            return "variableName.standard"
        }
        if (c == '"') {
            transitState(state, c)
            stream.eatWhile { it != "\"" }
            stream.next()
            val pk = stream.peek()
            if (pk != "@" && pk != "^") {
                transitState(state, '"')
            }
            return "string"
        }
        if (c == '@') {
            transitState(state, '@')
            val sb = StringBuilder()
            stream.eatWhile { cc ->
                if (cc[0] != ' ') {
                    sb.append(cc)
                    true
                } else {
                    false
                }
            }
            state.langs.add(sb.toString())
            stream.next()
            transitState(state, ' ')
            return "string.special"
        }
        if (c == '^') {
            stream.next()
            transitState(state, '^')
            val sb = StringBuilder()
            stream.eatWhile { cc ->
                if (cc[0] != '>') {
                    sb.append(cc)
                    true
                } else {
                    false
                }
            }
            state.types.add(sb.toString())
            stream.next()
            transitState(state, '>')
            return "variableName"
        }
        if (c == ' ') transitState(state, c)
        if (c == '.') transitState(state, c)
        return null
    }
}
