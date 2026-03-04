/*
 * Copyright 2025 Jason Monk
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
package com.monkopedia.kodemirror.lezer.grammar

import com.monkopedia.kodemirror.lezer.highlight.styleTags
import com.monkopedia.kodemirror.lezer.highlight.tags as t

val lezerHighlighting = styleTags(
    mapOf(
        "LineComment" to t.lineComment,
        "BlockComment" to t.blockComment,
        "AnyChar" to t.character,
        "Literal" to t.string,
        "tokens from grammar as empty prop extend specialize AtName" to
            t.keyword,
        "@top @left @right @cut @external" to t.modifier,
        "@precedence @tokens @context @dialects @skip @detectDelim @conflict" to
            t.definitionKeyword,
        "@extend @specialize" to t.operatorKeyword,
        "CharSet InvertedCharSet" to t.regexp,
        "CharClass" to t.atom,
        "RuleName" to t.variableName,
        "RuleDeclaration/RuleName InlineRule/RuleName TokensBody/RuleName" to
            t.definition(t.variableName),
        "PrecedenceName" to t.labelName,
        "Name" to t.name,
        "( )" to t.paren,
        "[ ]" to t.squareBracket,
        "{ }" to t.brace,
        "\"!\" ~ * + ? |" to t.operator
    )
)
