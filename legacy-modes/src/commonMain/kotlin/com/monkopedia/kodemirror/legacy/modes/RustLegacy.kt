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

val rustLegacy: StreamParser<SimpleModeState> = simpleMode(
    SimpleModeConfig(
        name = "rust",
        states = mapOf(
            "start" to listOf(
                // string and byte string
                SimpleModeRule(Regex("""b?""""), token = "string", next = "string"),
                // raw string and raw byte string
                SimpleModeRule(Regex("""b?r""""), token = "string", next = "string_raw"),
                SimpleModeRule(Regex("""b?r#+"."""), token = "string", next = "string_raw_hash"),
                // character
                SimpleModeRule(
                    Regex("""'(?:[^'\\]|\\(?:[nrt0'"]|x[\da-fA-F]{2}|u\{[\da-fA-F]{6}\}))'"""),
                    token = "string.special"
                ),
                // byte
                SimpleModeRule(
                    Regex("""b'(?:[^']|\\(?:['\\nrt0]|x[\da-fA-F]{2}))'"""),
                    token = "string.special"
                ),
                SimpleModeRule(
                    Regex(
                        """(?:(?:[0-9][0-9_]*)(?:(?:[Ee][+-]?[0-9_]+)|\.[0-9_]+""" +
                            """(?:[Ee][+-]?[0-9_]+)?)(?:f32|f64)?)|(?:0(?:b[01_]+|""" +
                            """(?:o[0-7_]+)|(?:x[0-9a-fA-F_]+))|(?:[0-9][0-9_]*))""" +
                            """(?:u8|u16|u32|u64|i8|i16|i32|i64|isize|usize)?"""
                    ),
                    token = "number"
                ),
                SimpleModeRule(
                    Regex(
                        """(let(?:\s+mut)?|fn|enum|mod|struct|type|union)""" +
                            """(\s+)([a-zA-Z_][a-zA-Z0-9_]*)"""
                    ),
                    token = listOf("keyword", null, "def")
                ),
                SimpleModeRule(
                    Regex(
                        """(?:abstract|alignof|as|async|await|box|break|continue|const|""" +
                            """crate|do|dyn|else|enum|extern|fn|for|final|if|impl|in|loop|""" +
                            """macro|match|mod|move|offsetof|override|priv|proc|pub|pure|""" +
                            """ref|return|self|sizeof|static|struct|super|trait|type|typeof|""" +
                            """union|unsafe|unsized|use|virtual|where|while|yield)\b"""
                    ),
                    token = "keyword"
                ),
                SimpleModeRule(
                    Regex(
                        """\b(?:Self|isize|usize|char|bool|u8|u16|u32|u64|f16|f32|f64|""" +
                            """i8|i16|i32|i64|str|Option)\b"""
                    ),
                    token = "atom"
                ),
                SimpleModeRule(
                    Regex("""\b(?:true|false|Some|None|Ok|Err)\b"""),
                    token = "builtin"
                ),
                SimpleModeRule(
                    Regex("""\b(fn)(\s+)([a-zA-Z_][a-zA-Z0-9_]*)"""),
                    token = listOf("keyword", null, "def")
                ),
                SimpleModeRule(Regex("""#!?\[.*\]"""), token = "meta"),
                SimpleModeRule(Regex("""//.*"""), token = "comment"),
                SimpleModeRule(Regex("""/\*"""), token = "comment", next = "comment"),
                SimpleModeRule(Regex("""[-+/*=<>!]+"""), token = "operator"),
                SimpleModeRule(Regex("""[a-zA-Z_]\w*!"""), token = "macroName"),
                SimpleModeRule(Regex("""[a-zA-Z_]\w*"""), token = "variable"),
                SimpleModeRule(Regex("""[\{\[\(]"""), token = null, indent = true),
                SimpleModeRule(Regex("""[\}\]\)]"""), token = null, dedent = true)
            ),
            "string" to listOf(
                SimpleModeRule(Regex("\""), token = "string", next = "start"),
                SimpleModeRule(Regex("""(?:[^\\"]|\\(?:.|${'$'}))*"""), token = "string")
            ),
            "string_raw" to listOf(
                SimpleModeRule(Regex("\""), token = "string", next = "start"),
                SimpleModeRule(Regex("""[^"]*"""), token = "string")
            ),
            "string_raw_hash" to listOf(
                SimpleModeRule(Regex(""""+#+"""), token = "string", next = "start"),
                SimpleModeRule(Regex("""(?:[^"]|"(?!#))*"""), token = "string")
            ),
            "comment" to listOf(
                SimpleModeRule(Regex(""".*?\*/"""), token = "comment", next = "start"),
                SimpleModeRule(Regex(""".*"""), token = "comment")
            )
        ),
        languageData = mapOf(
            "dontIndentStates" to listOf("comment"),
            "indentOnInput" to Regex("^\\s*}$"),
            "commentTokens" to mapOf(
                "line" to "//",
                "block" to mapOf("open" to "/*", "close" to "*/")
            )
        )
    )
)
