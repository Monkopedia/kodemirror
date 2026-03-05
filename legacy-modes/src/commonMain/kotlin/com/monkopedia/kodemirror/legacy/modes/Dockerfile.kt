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

private val shells = listOf("run", "cmd", "entrypoint", "shell")
private val others = listOf(
    "arg", "from", "maintainer", "label", "env",
    "add", "copy", "volume", "user",
    "workdir", "onbuild", "stopsignal", "healthcheck", "shell"
)
private val instructions = listOf("from", "expose") + shells + others
private val instructionPat = "(${instructions.joinToString("|")})"

private val fromRegex = Regex("^(\\s*)\\b(from)\\b", RegexOption.IGNORE_CASE)
private val shellsAsArrayRegex = Regex(
    "^(\\s*)(${shells.joinToString("|")})(\\s+\\[)",
    RegexOption.IGNORE_CASE
)
private val exposeRegex = Regex("^(\\s*)(expose)(\\s+)", RegexOption.IGNORE_CASE)
private val instructionOnlyLine = Regex(
    "^(\\s*)$instructionPat(\\s*)(#.*)?\$",
    RegexOption.IGNORE_CASE
)
private val instructionWithArguments = Regex(
    "^(\\s*)$instructionPat(\\s+)",
    RegexOption.IGNORE_CASE
)

val dockerFile: StreamParser<SimpleModeState> = simpleMode(
    SimpleModeConfig(
        name = "dockerfile",
        states = mapOf(
            "start" to listOf(
                SimpleModeRule(
                    regex = Regex("^\\s*#.*$"),
                    token = "comment",
                    sol = true
                ),
                SimpleModeRule(
                    regex = fromRegex,
                    token = listOf(null, "keyword"),
                    sol = true,
                    next = "from"
                ),
                SimpleModeRule(
                    regex = instructionOnlyLine,
                    token = listOf(null, "keyword", null, "error"),
                    sol = true
                ),
                SimpleModeRule(
                    regex = shellsAsArrayRegex,
                    token = listOf(null, "keyword", null),
                    sol = true,
                    next = "array"
                ),
                SimpleModeRule(
                    regex = exposeRegex,
                    token = listOf(null, "keyword", null),
                    sol = true,
                    next = "expose"
                ),
                SimpleModeRule(
                    regex = instructionWithArguments,
                    token = listOf(null, "keyword", null),
                    sol = true,
                    next = "arguments"
                ),
                SimpleModeRule(regex = Regex("^."), token = null)
            ),
            "from" to listOf(
                SimpleModeRule(regex = Regex("^\\s*$"), token = null, next = "start"),
                SimpleModeRule(
                    regex = Regex("^(\\s*)(#.*)$"),
                    token = listOf(null, "error"),
                    next = "start"
                ),
                SimpleModeRule(
                    regex = Regex("^(\\s*\\S+\\s+)(as)", RegexOption.IGNORE_CASE),
                    token = listOf(null, "keyword"),
                    next = "start"
                ),
                SimpleModeRule(regex = Regex("^."), token = null, next = "start")
            ),
            "single" to listOf(
                SimpleModeRule(regex = Regex("^(?:[^\\\\']|\\\\.)"), token = "string"),
                SimpleModeRule(regex = Regex("^'"), token = "string", pop = true)
            ),
            "double" to listOf(
                SimpleModeRule(regex = Regex("^(?:[^\\\\\"]|\\\\.)"), token = "string"),
                SimpleModeRule(regex = Regex("^\""), token = "string", pop = true)
            ),
            "array" to listOf(
                SimpleModeRule(regex = Regex("^]"), token = null, next = "start"),
                SimpleModeRule(
                    regex = Regex("^\"(?:[^\\\\\"]|\\\\.)*\"?"),
                    token = "string"
                )
            ),
            "expose" to listOf(
                SimpleModeRule(
                    regex = Regex("^\\d+$"),
                    token = "number",
                    next = "start"
                ),
                SimpleModeRule(
                    regex = Regex("^[^\\d]+$"),
                    token = null,
                    next = "start"
                ),
                SimpleModeRule(regex = Regex("^\\d+"), token = "number"),
                SimpleModeRule(regex = Regex("^[^\\d]+"), token = null),
                SimpleModeRule(regex = Regex("^."), token = null, next = "start")
            ),
            "arguments" to listOf(
                SimpleModeRule(
                    regex = Regex("^\\s*#.*$"),
                    sol = true,
                    token = "comment"
                ),
                SimpleModeRule(
                    regex = Regex("^\"(?:[^\\\\\"]|\\\\.)*\"?$"),
                    token = "string",
                    next = "start"
                ),
                SimpleModeRule(
                    regex = Regex("^\""),
                    token = "string",
                    push = "double"
                ),
                SimpleModeRule(
                    regex = Regex("^'(?:[^\\\\']|\\\\.)*'?$"),
                    token = "string",
                    next = "start"
                ),
                SimpleModeRule(
                    regex = Regex("^'"),
                    token = "string",
                    push = "single"
                ),
                SimpleModeRule(
                    regex = Regex("^[^#\"']+[\\\\`]$"),
                    token = null
                ),
                SimpleModeRule(
                    regex = Regex("^[^#\"']+$"),
                    token = null,
                    next = "start"
                ),
                SimpleModeRule(regex = Regex("^[^#\"']+"), token = null),
                SimpleModeRule(regex = Regex("^."), token = null, next = "start")
            )
        ),
        languageData = mapOf("commentTokens" to mapOf("line" to "#"))
    )
)
