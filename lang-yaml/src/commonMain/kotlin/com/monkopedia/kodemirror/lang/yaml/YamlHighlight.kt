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
package com.monkopedia.kodemirror.lang.yaml

import com.monkopedia.kodemirror.lezer.highlight.Tags as t
import com.monkopedia.kodemirror.lezer.highlight.styleTagsList

val yamlHighlighting = styleTagsList(
    mapOf(
        "DirectiveName" to listOf(t.keyword),
        "DirectiveContent" to listOf(t.attributeValue),
        "DirectiveEnd DocEnd" to listOf(t.meta),
        "QuotedLiteral" to listOf(t.string),
        "BlockLiteralHeader" to listOf(t.special(t.string)),
        "BlockLiteralContent" to listOf(t.content),
        "Literal" to listOf(t.content),
        "Key/Literal Key/QuotedLiteral" to listOf(t.definition(t.propertyName)),
        "Anchor Alias" to listOf(t.labelName),
        "Tag" to listOf(t.typeName),
        "Comment" to listOf(t.lineComment),
        ": , -" to listOf(t.separator),
        "?" to listOf(t.punctuation),
        "[ ]" to listOf(t.squareBracket),
        "{ }" to listOf(t.brace)
    )
)
