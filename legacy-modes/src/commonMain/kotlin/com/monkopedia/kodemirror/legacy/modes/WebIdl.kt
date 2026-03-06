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

private fun wordRegexpWebIdl(words: List<String>): Regex {
    return Regex("^((" + words.joinToString(")|(") + "))\\b")
}

private val webIdlBuiltinArray = listOf(
    "Clamp", "Constructor", "EnforceRange", "Exposed", "ImplicitThis",
    "Global", "PrimaryGlobal", "LegacyArrayClass", "LegacyUnenumerableNamedProperties",
    "LenientThis", "NamedConstructor", "NewObject", "NoInterfaceObject",
    "OverrideBuiltins", "PutForwards", "Replaceable", "SameObject",
    "TreatNonObjectAsNull", "TreatNullAs", "EmptyString", "Unforgeable", "Unscopeable"
)
private val webIdlBuiltins = wordRegexpWebIdl(webIdlBuiltinArray)

private val webIdlTypeArray = listOf(
    "unsigned", "short", "long", "unrestricted", "float", "double",
    "boolean", "byte", "octet", "Promise",
    "ArrayBuffer", "DataView", "Int8Array", "Int16Array", "Int32Array",
    "Uint8Array", "Uint16Array", "Uint32Array", "Uint8ClampedArray",
    "Float32Array", "Float64Array",
    "ByteString", "DOMString", "USVString", "sequence", "object", "RegExp",
    "Error", "DOMException", "FrozenArray",
    "any", "void"
)
private val webIdlTypes = wordRegexpWebIdl(webIdlTypeArray)

private val webIdlKeywordArray = listOf(
    "attribute", "callback", "const", "deleter", "dictionary", "enum", "getter",
    "implements", "inherit", "interface", "iterable", "legacycaller", "maplike",
    "partial", "required", "serializer", "setlike", "setter", "static",
    "stringifier", "typedef", "optional", "readonly", "or"
)
private val webIdlKeywords = wordRegexpWebIdl(webIdlKeywordArray)

private val webIdlAtomArray = listOf("true", "false", "Infinity", "NaN", "null")
private val webIdlAtoms = wordRegexpWebIdl(webIdlAtomArray)

private val webIdlStartDefs =
    wordRegexpWebIdl(listOf("callback", "dictionary", "enum", "interface"))
private val webIdlEndDefs = wordRegexpWebIdl(listOf("typedef"))

private val webIdlSingleOperators = Regex("^[:<=>?]")
private val webIdlIntegers = Regex("^-?([1-9][0-9]*|0[Xx][0-9A-Fa-f]+|0[0-7]*)")
private val webIdlFloats = Regex(
    "^-?(([0-9]+\\.[0-9]*|[0-9]*\\.[0-9]+)([Ee][+-]?[0-9]+)?|[0-9]+[Ee][+-]?[0-9]+)"
)
private val webIdlIdentifiers = Regex("^_?[A-Za-z][0-9A-Z_a-z-]*")
private val webIdlIdentifiersEnd = Regex("^_?[A-Za-z][0-9A-Z_a-z-]*(?=\\s*;)")
private val webIdlStrings = Regex("""^"[^"]*"""")
private val webIdlMultilineComments = Regex("^/\\*.*?\\*/")
private val webIdlMultilineCommentsStart = Regex("^/\\*.*")
private val webIdlMultilineCommentsEnd = Regex("^.*?\\*/")

data class WebIdlState(
    var inComment: Boolean = false,
    var lastToken: String = "",
    var startDef: Boolean = false,
    var endDef: Boolean = false
)

/** Stream parser for WebIDL. */
val webIDL: StreamParser<WebIdlState> = object : StreamParser<WebIdlState> {
    override val name: String get() = "webidl"

    override fun startState(indentUnit: Int) = WebIdlState()
    override fun copyState(state: WebIdlState) = state.copy()

    private fun readToken(stream: StringStream, state: WebIdlState): String? {
        if (stream.eatSpace()) return null

        if (state.inComment) {
            if (stream.match(webIdlMultilineCommentsEnd) != null) {
                state.inComment = false
                return "comment"
            }
            stream.skipToEnd()
            return "comment"
        }
        if (stream.match("//")) {
            stream.skipToEnd()
            return "comment"
        }
        if (stream.match(webIdlMultilineComments) != null) return "comment"
        if (stream.match(webIdlMultilineCommentsStart) != null) {
            state.inComment = true
            return "comment"
        }

        if (stream.match(Regex("^-?[0-9.]"), false) != null) {
            if (stream.match(webIdlIntegers) != null || stream.match(webIdlFloats) != null) {
                return "number"
            }
        }

        if (stream.match(webIdlStrings) != null) return "string"

        if (state.startDef && stream.match(webIdlIdentifiers) != null) return "def"

        if (state.endDef && stream.match(webIdlIdentifiersEnd) != null) {
            state.endDef = false
            return "def"
        }

        if (stream.match(webIdlKeywords) != null) return "keyword"

        if (stream.match(webIdlTypes) != null) {
            val lastToken = state.lastToken
            val nextToken = stream.match(Regex("^\\s*(.+?)\\b"), false)
                ?.groupValues?.getOrNull(1)

            if (lastToken == ":" || lastToken == "implements" ||
                nextToken == "implements" || nextToken == "="
            ) {
                return "builtin"
            } else {
                return "type"
            }
        }

        if (stream.match(webIdlBuiltins) != null) return "builtin"
        if (stream.match(webIdlAtoms) != null) return "atom"
        if (stream.match(webIdlIdentifiers) != null) return "variable"

        if (stream.match(webIdlSingleOperators) != null) return "operator"

        stream.next()
        return null
    }

    override fun token(stream: StringStream, state: WebIdlState): String? {
        val style = readToken(stream, state)

        if (style != null) {
            val cur = stream.current()
            state.lastToken = cur
            if (style == "keyword") {
                state.startDef = webIdlStartDefs.containsMatchIn(cur)
                state.endDef = state.endDef || webIdlEndDefs.containsMatchIn(cur)
            } else {
                state.startDef = false
            }
        }

        return style
    }
}
