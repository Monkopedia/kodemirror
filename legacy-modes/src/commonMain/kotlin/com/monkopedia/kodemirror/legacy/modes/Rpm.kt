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

private val rpmHeaderSeparator = Regex("^-+$")
private val rpmHeaderLine = Regex(
    "^(Mon|Tue|Wed|Thu|Fri|Sat|Sun) " +
        "(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) " +
        " ?\\d{1,2} \\d{2}:\\d{2}(:\\d{2})? [A-Z]{3,4} \\d{4} - "
)
private val rpmSimpleEmail = Regex("^[\\w+.-]+@[\\w.-]+")

/** Stream parser for RPM changelog. */
val rpmChanges: StreamParser<Unit> = object : StreamParser<Unit> {
    override val name: String get() = "rpmchanges"
    override fun startState(indentUnit: Int) = Unit
    override fun copyState(state: Unit) = Unit

    override fun token(stream: StringStream, state: Unit): String? {
        if (stream.sol()) {
            if (stream.match(rpmHeaderSeparator) != null) return "tag"
            if (stream.match(rpmHeaderLine) != null) return "tag"
        }
        if (stream.match(rpmSimpleEmail) != null) return "string"
        stream.next()
        return null
    }
}

// Quick and dirty spec file highlighting

private val rpmArch = Regex(
    "^(i386|i586|i686|x86_64|ppc64le|ppc64|ppc|ia64|" +
        "s390x|s390|sparc64|sparcv9|sparc|noarch|alphaev6|alpha|hppa|mipsel)"
)
private val rpmPreamble = Regex("^[a-zA-Z0-9()]+:")
private val rpmSection = Regex(
    "^%(debug_package|package|description|prep|build|install|files|clean|" +
        "changelog|preinstall|preun|postinstall|postun|pretrans|posttrans|" +
        "pre|post|triggerin|triggerun|verifyscript|check|" +
        "triggerpostun|triggerprein|trigger)"
)
private val rpmControlFlowComplex = Regex("^%(ifnarch|ifarch|if)")
private val rpmControlFlowSimple = Regex("^%(else|endif)")
private val rpmOperators = Regex("^(!|\\?|<=|<|>=|>|==|&&|\\|\\|)")

data class RpmSpecState(
    var controlFlow: Boolean = false,
    var macroParameters: Boolean = false,
    var section: Boolean = false
)

/** Stream parser for RPM spec. */
val rpmSpec: StreamParser<RpmSpecState> = object : StreamParser<RpmSpecState> {
    override val name: String get() = "rpmspec"

    override fun startState(indentUnit: Int) = RpmSpecState()
    override fun copyState(state: RpmSpecState) = state.copy()

    override fun token(stream: StringStream, state: RpmSpecState): String? {
        val ch = stream.peek()
        if (ch == "#") {
            stream.skipToEnd()
            return "comment"
        }

        if (stream.sol()) {
            if (stream.match(rpmPreamble) != null) return "header"
            if (stream.match(rpmSection) != null) return "atom"
        }

        if (stream.match(Regex("^\\$\\w+")) != null) return "def"
        if (stream.match(Regex("^\\$\\{\\w+\\}")) != null) return "def"

        if (stream.match(rpmControlFlowSimple) != null) return "keyword"
        if (stream.match(rpmControlFlowComplex) != null) {
            state.controlFlow = true
            return "keyword"
        }
        if (state.controlFlow) {
            if (stream.match(rpmOperators) != null) return "operator"
            if (stream.match(Regex("^(\\d+)")) != null) return "number"
            if (stream.eol()) state.controlFlow = false
        }

        if (stream.match(rpmArch) != null) {
            if (stream.eol()) state.controlFlow = false
            return "number"
        }

        // Macros like '%make_install' or '%attr(0775,root,root)'
        if (stream.match(Regex("^%[\\w]+")) != null) {
            if (stream.match(Regex("^\\(")) != null) state.macroParameters = true
            return "keyword"
        }
        if (state.macroParameters) {
            if (stream.match(Regex("^\\d+")) != null) return "number"
            if (stream.match(Regex("^\\)")) != null) {
                state.macroParameters = false
                return "keyword"
            }
        }

        // Macros like '%{defined fedora}'
        if (stream.match(Regex("^%\\{\\??[\\w \\-:!]+\\}")) != null) {
            if (stream.eol()) state.controlFlow = false
            return "def"
        }

        stream.next()
        return null
    }
}
