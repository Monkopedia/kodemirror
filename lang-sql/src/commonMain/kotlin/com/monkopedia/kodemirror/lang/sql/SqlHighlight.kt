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
package com.monkopedia.kodemirror.lang.sql

import com.monkopedia.kodemirror.lezer.highlight.Tags as t
import com.monkopedia.kodemirror.lezer.highlight.styleTags

val sqlHighlighting = styleTags(
    mapOf(
        "Keyword" to t.keyword,
        "Type" to t.typeName,
        "Builtin" to t.standard(t.name),
        "Bits" to t.number,
        "Bytes" to t.string,
        "Bool" to t.bool,
        "Null" to t.`null`,
        "Number" to t.number,
        "String" to t.string,
        "Identifier" to t.name,
        "QuotedIdentifier" to t.special(t.string),
        "SpecialVar" to t.special(t.name),
        "LineComment" to t.lineComment,
        "BlockComment" to t.blockComment,
        "Operator" to t.operator,
        "Semi Punctuation" to t.punctuation,
        "( )" to t.paren,
        "{ }" to t.brace,
        "[ ]" to t.squareBracket
    )
)
