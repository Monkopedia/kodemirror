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

private val protobufKeywords = Regex(
    "^((" +
        listOf(
            "package", "message", "import", "syntax",
            "required", "optional", "repeated", "reserved",
            "default", "extensions", "packed",
            "bool", "bytes", "double", "enum", "float", "string",
            "int32", "int64", "uint32", "uint64", "sint32", "sint64",
            "fixed32", "fixed64", "sfixed32", "sfixed64",
            "option", "service", "rpc", "returns"
        ).joinToString(")|(") +
        "))\\b",
    RegexOption.IGNORE_CASE
)

private val protobufIdentifiers = Regex("^[_A-Za-z\\u00a1-\\uffff][_A-Za-z0-9\\u00a1-\\uffff]*")

private fun protobufTokenBase(stream: StringStream): String? {
    if (stream.eatSpace()) return null

    if (stream.match(Regex("^//")) != null) {
        stream.skipToEnd()
        return "comment"
    }

    if (stream.match(Regex("^[0-9.+-]"), false) != null) {
        if (stream.match(Regex("^[+-]?0x[0-9a-fA-F]+")) != null) return "number"
        if (stream.match(Regex("^[+-]?\\d*\\.\\d+([EeDd][+-]?\\d+)?")) != null) return "number"
        if (stream.match(Regex("^[+-]?\\d+([EeDd][+-]?\\d+)?")) != null) return "number"
    }

    if (stream.match(Regex("^\"([^\"]|(\"\"))*\"")) != null) return "string"
    if (stream.match(Regex("^'([^']|(''))*'")) != null) return "string"

    if (stream.match(protobufKeywords) != null) return "keyword"
    if (stream.match(protobufIdentifiers) != null) return "variable"

    stream.next()
    return null
}

/** Stream parser for Protocol Buffers. */
val protobuf: StreamParser<Unit> = object : StreamParser<Unit> {
    override val name: String get() = "protobuf"
    override fun startState(indentUnit: Int) = Unit
    override fun copyState(state: Unit) = Unit

    override fun token(stream: StringStream, state: Unit): String? {
        return protobufTokenBase(stream)
    }
}
