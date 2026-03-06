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

private val mboxRfc2822 = listOf(
    "From", "Sender", "Reply-To", "To", "Cc", "Bcc", "Message-ID",
    "In-Reply-To", "References", "Resent-From", "Resent-Sender", "Resent-To",
    "Resent-Cc", "Resent-Bcc", "Resent-Message-ID", "Return-Path", "Received"
)
private val mboxRfc2822NoEmail = listOf(
    "Date",
    "Subject",
    "Comments",
    "Keywords",
    "Resent-Date"
)

private val mboxSeparator = Regex("^From ")
private val mboxRfc2822Header = Regex("^(" + mboxRfc2822.joinToString("|") + "): ")
private val mboxRfc2822HeaderNoEmail = Regex("^(" + mboxRfc2822NoEmail.joinToString("|") + "): ")
private val mboxHeaderGeneric = Regex("^[^:]+:")
private val mboxEmail = Regex("^[^ ]+@[^ ]+")
private val mboxUntilEmail = Regex("^.*?(?=[^ ]+?@[^ ]+)")
private val mboxBracketedEmail = Regex("^<.*?>")
private val mboxUntilBracketedEmail = Regex("^.*?(?=<.*>)")

data class MboxState(
    var inSeparator: Boolean = false,
    var inHeader: Boolean = false,
    var emailPermitted: Boolean = false,
    var header: String? = null,
    var inHeaders: Boolean = false
)

private fun mboxStyleForHeader(header: String?): String {
    return if (header == "Subject") "header" else "string"
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
private fun mboxReadToken(stream: StringStream, state: MboxState): String? {
    if (stream.sol()) {
        state.inSeparator = false
        if (state.inHeader && stream.eat(Regex("[ \\t]")) != null) {
            return null
        } else {
            state.inHeader = false
            state.header = null
        }

        if (stream.match(mboxSeparator) != null) {
            state.inHeaders = true
            state.inSeparator = true
            return "atom"
        }

        var emailPermitted = false
        val matchNoEmail = stream.match(mboxRfc2822HeaderNoEmail)
        val matchEmail = if (matchNoEmail == null) {
            emailPermitted = true
            stream.match(mboxRfc2822Header)
        } else {
            null
        }
        val match = matchNoEmail ?: matchEmail
        if (match != null) {
            state.inHeaders = true
            state.inHeader = true
            state.emailPermitted = emailPermitted
            state.header = match.groupValues[1]
            return "atom"
        }

        if (state.inHeaders) {
            val genericMatch = stream.match(mboxHeaderGeneric)
            if (genericMatch != null) {
                state.inHeader = true
                state.emailPermitted = true
                state.header = genericMatch.value.dropLast(1)
                return "atom"
            }
        }

        state.inHeaders = false
        stream.skipToEnd()
        return null
    }

    if (state.inSeparator) {
        if (stream.match(mboxEmail) != null) return "link"
        if (stream.match(mboxUntilEmail) != null) return "atom"
        stream.skipToEnd()
        return "atom"
    }

    if (state.inHeader) {
        val style = mboxStyleForHeader(state.header)
        if (state.emailPermitted) {
            if (stream.match(mboxBracketedEmail) != null) return "$style link"
            if (stream.match(mboxUntilBracketedEmail) != null) return style
        }
        stream.skipToEnd()
        return style
    }

    stream.skipToEnd()
    return null
}

/** Stream parser for mbox (email). */
val mbox: StreamParser<MboxState> = object : StreamParser<MboxState> {
    override val name: String get() = "mbox"

    override fun startState(indentUnit: Int) = MboxState()
    override fun copyState(state: MboxState) = state.copy()

    override fun token(stream: StringStream, state: MboxState): String? {
        return mboxReadToken(stream, state)
    }

    override fun blankLine(state: MboxState, indentUnit: Int) {
        state.inHeaders = false
        state.inSeparator = false
        state.inHeader = false
    }
}
