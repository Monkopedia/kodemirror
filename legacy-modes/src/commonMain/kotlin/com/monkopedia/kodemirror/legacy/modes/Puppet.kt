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

private val puppetVariableRegex = Regex(
    "(\\{)?([a-z][a-z0-9_]*)?((::[a-z][a-z0-9_]*)*::)?[a-zA-Z0-9_]+(\\})?"
)

private val puppetWords: Map<String, String> = buildMap {
    fun define(style: String, str: String) {
        for (w in str.split(" ")) {
            put(w, style)
        }
    }
    define("keyword", "class define site node include import inherits")
    define("keyword", "case if else in and elsif default or")
    define(
        "atom",
        "false true running present absent file directory undef"
    )
    define(
        "builtin",
        "action augeas burst chain computer cron destination dport exec " +
            "file filebucket group host icmp iniface interface jump k5login limit " +
            "log_level log_prefix macauthorization mailalias maillist mcx mount " +
            "nagios_command nagios_contact nagios_contactgroup nagios_host " +
            "nagios_hostdependency nagios_hostescalation nagios_hostextinfo " +
            "nagios_hostgroup nagios_service nagios_servicedependency " +
            "nagios_serviceescalation nagios_serviceextinfo nagios_servicegroup " +
            "nagios_timeperiod name notify outiface package proto reject resources " +
            "router schedule scheduled_task selboolean selmodule service source " +
            "sport ssh_authorized_key sshkey stage state table tidy todest toports " +
            "tosource user vlan yumrepo zfs zone zpool"
    )
}

private fun puppetTokenString(stream: StringStream, state: PuppetState): String {
    var current: String?
    var prev: String? = null
    var foundVar = false
    while (!stream.eol()) {
        current = stream.next()
        if (current == state.pending) {
            if (prev != "\\" || state.pending != "\"") {
                break
            }
        }
        if (current == "$" && prev != "\\" && state.pending == "\"") {
            foundVar = true
            break
        }
        prev = current
    }
    if (foundVar) {
        stream.backUp(1)
    }
    if (!foundVar && stream.current().lastOrNull()?.toString() == state.pending) {
        state.continueString = false
    } else {
        state.continueString = true
    }
    return "string"
}

private fun puppetTokenize(stream: StringStream, state: PuppetState): String? {
    val word = stream.match(Regex("^[\\w]+"), false)
    val attribute = stream.match(Regex("^(\\s+)?\\w+\\s+=>.*"), false)
    val resource = stream.match(Regex("^(\\s+)?[\\w:_]+(\\s+)?\\{"), false)
    val specialResource = stream.match(
        Regex("^(\\s+)?@{1,2}[\\w:_]+(\\s+)?\\{"),
        false
    )

    val ch = stream.next() ?: return null

    // Variable?
    if (ch == "$") {
        if (stream.match(puppetVariableRegex) != null) {
            return if (state.continueString) "variableName.special" else "variable"
        }
        return "error"
    }
    // Still looking for end of string?
    if (state.continueString) {
        stream.backUp(1)
        return puppetTokenString(stream, state)
    }
    // In a definition?
    if (state.inDefinition) {
        if (stream.match(Regex("^(\\s+)?[\\w:_]+(\\s+)?")) != null) {
            return "def"
        }
        stream.match(Regex("^\\s+\\{"))
        state.inDefinition = false
    }
    // In an include?
    if (state.inInclude) {
        stream.match(Regex("^(\\s+)?\\S+(\\s+)?"))
        state.inInclude = false
        return "def"
    }
    // Function call?
    if (stream.match(Regex("^(\\s+)?\\w+\\(")) != null) {
        stream.backUp(1)
        return "def"
    }
    // Attribute?
    if (attribute != null) {
        stream.match(Regex("^(\\s+)?\\w+"))
        return "tag"
    }
    // Puppet specific word?
    val wordStr = word?.value
    if (wordStr != null && puppetWords.containsKey(wordStr)) {
        stream.backUp(1)
        stream.match(Regex("^[\\w]+"))
        if (stream.match(Regex("^\\s+\\S+\\s+\\{"), false) != null) {
            state.inDefinition = true
        }
        if (wordStr == "include") {
            state.inInclude = true
        }
        return puppetWords[wordStr]
    }
    // Reference?
    if (wordStr != null && Regex("(^|\\s+)[A-Z][\\w:_]+").containsMatchIn(wordStr)) {
        stream.backUp(1)
        stream.match(Regex("^(^|\\s+)[A-Z][\\w:_]+"))
        return "def"
    }
    // Resource?
    if (resource != null) {
        stream.match(Regex("^(\\s+)?[\\w:_]+"))
        return "def"
    }
    // Special resource?
    if (specialResource != null) {
        stream.match(Regex("^(\\s+)?@{1,2}"))
        return "atom"
    }
    // Comment
    if (ch == "#") {
        stream.skipToEnd()
        return "comment"
    }
    // String
    if (ch == "'" || ch == "\"") {
        state.pending = ch
        return puppetTokenString(stream, state)
    }
    // Brackets
    if (ch == "{" || ch == "}") {
        return "bracket"
    }
    // Regex
    if (ch == "/") {
        stream.match(Regex("^[^/]*/"))
        return "string.special"
    }
    // Number
    if (Regex("[0-9]").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[0-9]+"))
        return "number"
    }
    // Operator
    if (ch == "=") {
        if (stream.peek() == ">") {
            stream.next()
        }
        return "operator"
    }
    // Everything else
    stream.eatWhile(Regex("[\\w-]"))
    return null
}

data class PuppetState(
    var inDefinition: Boolean = false,
    var inInclude: Boolean = false,
    var continueString: Boolean = false,
    var pending: String? = null
)

val puppet: StreamParser<PuppetState> = object : StreamParser<PuppetState> {
    override val name: String get() = "puppet"

    override fun startState(indentUnit: Int) = PuppetState()
    override fun copyState(state: PuppetState) = state.copy()

    override fun token(stream: StringStream, state: PuppetState): String? {
        if (stream.eatSpace()) return null
        return puppetTokenize(stream, state)
    }
}
