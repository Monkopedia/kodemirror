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

private typealias HttpTokenFn = (StringStream, HttpState) -> String?

data class HttpState(
    var cur: HttpTokenFn = ::httpStart
)

private fun httpFailFirstLine(stream: StringStream, state: HttpState): String {
    stream.skipToEnd()
    state.cur = ::httpHeader
    return "error"
}

private fun httpStart(stream: StringStream, state: HttpState): String? {
    if (stream.match(Regex("^HTTP/\\d\\.\\d")) != null) {
        state.cur = ::httpResponseStatusCode
        return "keyword"
    } else if (
        stream.match(Regex("^[A-Z]+")) != null &&
        stream.peek()?.let { Regex("[ \\t]").containsMatchIn(it) } == true
    ) {
        state.cur = ::httpRequestPath
        return "keyword"
    } else {
        return httpFailFirstLine(stream, state)
    }
}

private fun httpResponseStatusCode(stream: StringStream, state: HttpState): String {
    val code = stream.match(Regex("^\\d+"))
    if (code == null) return httpFailFirstLine(stream, state)

    state.cur = ::httpResponseStatusText
    val status = code.value.toInt()
    return if (status in 100 until 400) "atom" else "error"
}

private fun httpResponseStatusText(stream: StringStream, state: HttpState): String? {
    stream.skipToEnd()
    state.cur = ::httpHeader
    return null
}

private fun httpRequestPath(stream: StringStream, state: HttpState): String {
    stream.eatWhile(Regex("\\S"))
    state.cur = ::httpRequestProtocol
    return "string special"
}

private fun httpRequestProtocol(stream: StringStream, state: HttpState): String {
    if (stream.match(Regex("^HTTP/\\d\\.\\d$")) != null) {
        state.cur = ::httpHeader
        return "keyword"
    } else {
        return httpFailFirstLine(stream, state)
    }
}

private fun httpHeader(
    stream: StringStream,
    @Suppress(
        "UNUSED_PARAMETER"
    ) state: HttpState
): String? {
    if (stream.sol() && stream.eat(Regex("[ \\t]")) == null) {
        if (stream.match(Regex("^.*?:")) != null) {
            return "atom"
        } else {
            stream.skipToEnd()
            return "error"
        }
    } else {
        stream.skipToEnd()
        return "string"
    }
}

private fun httpBody(
    stream: StringStream,
    @Suppress(
        "UNUSED_PARAMETER"
    ) state: HttpState
): String? {
    stream.skipToEnd()
    return null
}

/** Stream parser for HTTP request/response. */
val http: StreamParser<HttpState> = object : StreamParser<HttpState> {
    override val name: String get() = "http"

    override fun startState(indentUnit: Int) = HttpState()
    override fun copyState(state: HttpState) = state.copy()

    override fun token(stream: StringStream, state: HttpState): String? {
        val cur = state.cur
        if (cur !== ::httpHeader && cur !== ::httpBody && stream.eatSpace()) return null
        return cur(stream, state)
    }

    override fun blankLine(state: HttpState, indentUnit: Int) {
        state.cur = ::httpBody
    }
}
