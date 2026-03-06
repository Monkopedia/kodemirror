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
import com.monkopedia.kodemirror.lezer.highlight.styleTags

val yamlHighlighting = styleTags(
    mapOf(
        "DirectiveName" to t.keyword,
        "DirectiveContent" to t.attributeValue,
        "DirectiveEnd DocEnd" to t.meta,
        "QuotedLiteral" to t.string,
        "BlockLiteralHeader" to t.special(t.string),
        "BlockLiteralContent" to t.content,
        "Literal" to t.content,
        "Key/Literal Key/QuotedLiteral" to listOf(t.definition(t.propertyName)),
        "Anchor Alias" to t.labelName,
        "Tag" to t.typeName,
        "Comment" to t.lineComment,
        ": , -" to t.separator,
        "?" to t.punctuation,
        "[ ]" to t.squareBracket,
        "{ }" to t.brace
    )
)
