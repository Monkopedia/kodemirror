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
package com.monkopedia.kodemirror.lang.html

import com.monkopedia.kodemirror.lezer.highlight.Tags as t
import com.monkopedia.kodemirror.lezer.highlight.styleTags

val htmlHighlighting = styleTags(
    mapOf(
        "Text RawText IncompleteTag IncompleteCloseTag" to t.content,
        "StartTag StartCloseTag SelfClosingEndTag EndTag" to t.angleBracket,
        "TagName" to t.tagName,
        "MismatchedCloseTag/TagName" to listOf(t.tagName, t.invalid),
        "AttributeName" to t.attributeName,
        "AttributeValue UnquotedAttributeValue" to t.attributeValue,
        "Is" to t.definitionOperator,
        "EntityReference CharacterReference" to t.character,
        "Comment" to t.blockComment,
        "ProcessingInst" to t.processingInstruction,
        "DoctypeDecl" to t.documentMeta
    )
)
