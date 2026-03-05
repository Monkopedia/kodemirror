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

val factor = simpleMode(
    SimpleModeConfig(
        name = "factor",
        states = mapOf(
            "start" to listOf(
                SimpleModeRule(
                    regex = Regex("^#?!.*"),
                    token = "comment"
                ),
                SimpleModeRule(
                    regex = Regex("^\"\"\""),
                    token = "string",
                    next = "string3"
                ),
                SimpleModeRule(
                    regex = Regex("^(STRING:)(\\s)"),
                    token = listOf("keyword", null),
                    next = "string2"
                ),
                SimpleModeRule(
                    regex = Regex("^\\S*?\""),
                    token = "string",
                    next = "string"
                ),
                SimpleModeRule(
                    regex = Regex(
                        "^(?:0x[\\d,a-f]+)|(?:0o[0-7]+)|(?:0b[0,1]+)" +
                            "|(?:-?\\d+.?\\d*)(?=\\s)"
                    ),
                    token = "number"
                ),
                SimpleModeRule(
                    regex = Regex(
                        "^((?:GENERIC)|:?:)(\\s+)(\\S+)(\\s+)(\\()"
                    ),
                    token = listOf("keyword", null, "def", null, "bracket"),
                    next = "stack"
                ),
                SimpleModeRule(
                    regex = Regex("^(M:)(\\s+)(\\S+)(\\s+)(\\S+)"),
                    token = listOf("keyword", null, "def", null, "tag")
                ),
                SimpleModeRule(
                    regex = Regex("^USING:"),
                    token = "keyword",
                    next = "vocabulary"
                ),
                SimpleModeRule(
                    regex = Regex("^(USE:|IN:)(\\s+)(\\S+)(?=\\s|$)"),
                    token = listOf("keyword", null, "tag")
                ),
                SimpleModeRule(
                    regex = Regex("^(\\S+:)(\\s+)(\\S+)(?=\\s|$)"),
                    token = listOf("keyword", null, "def")
                ),
                SimpleModeRule(
                    regex = Regex(
                        "^(?:;|\\\\|t|f|if|loop|while|until|do|" +
                            "PRIVATE>|<PRIVATE|\\.|\\S*\\[|\\]|\\S*\\{|\\})(?=\\s|$)"
                    ),
                    token = "keyword"
                ),
                SimpleModeRule(
                    regex = Regex("^\\S+[)>.\\*?]+(?=\\s|$)"),
                    token = "builtin"
                ),
                SimpleModeRule(
                    regex = Regex("^[)><]+\\S+(?=\\s|$)"),
                    token = "builtin"
                ),
                SimpleModeRule(
                    regex = Regex("^(?:[+\\-=/\\*<>])(?=\\s|$)"),
                    token = "keyword"
                ),
                SimpleModeRule(
                    regex = Regex("^\\S+"),
                    token = "variable"
                ),
                SimpleModeRule(
                    regex = Regex("^\\s+|."),
                    token = null
                )
            ),
            "vocabulary" to listOf(
                SimpleModeRule(
                    regex = Regex("^;"),
                    token = "keyword",
                    next = "start"
                ),
                SimpleModeRule(
                    regex = Regex("^\\S+"),
                    token = "tag"
                ),
                SimpleModeRule(
                    regex = Regex("^\\s+|."),
                    token = null
                )
            ),
            "string" to listOf(
                SimpleModeRule(
                    regex = Regex("^(?:[^\\\\]|\\\\.)*?\""),
                    token = "string",
                    next = "start"
                ),
                SimpleModeRule(
                    regex = Regex("^.*"),
                    token = "string"
                )
            ),
            "string2" to listOf(
                SimpleModeRule(
                    regex = Regex("^;"),
                    token = "keyword",
                    next = "start",
                    sol = true
                ),
                SimpleModeRule(
                    regex = Regex("^.*"),
                    token = "string"
                )
            ),
            "string3" to listOf(
                SimpleModeRule(
                    regex = Regex("^(?:[^\\\\]|\\\\.)*?\"\"\""),
                    token = "string",
                    next = "start"
                ),
                SimpleModeRule(
                    regex = Regex("^.*"),
                    token = "string"
                )
            ),
            "stack" to listOf(
                SimpleModeRule(
                    regex = Regex("^\\)"),
                    token = "bracket",
                    next = "start"
                ),
                SimpleModeRule(
                    regex = Regex("^--"),
                    token = "bracket"
                ),
                SimpleModeRule(
                    regex = Regex("^\\S+"),
                    token = "meta"
                ),
                SimpleModeRule(
                    regex = Regex("^\\s+|."),
                    token = null
                )
            )
        ),
        languageData = mapOf(
            "commentTokens" to mapOf("line" to "!")
        )
    )
)
