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
package com.monkopedia.kodemirror.lang.liquid

import com.monkopedia.kodemirror.lezer.highlight.styleTags
import com.monkopedia.kodemirror.lezer.highlight.tags as t

internal val liquidHighlighting = styleTags(
    mapOf(
        "cycle comment endcomment raw endraw echo increment decrement liquid in with as" to
            t.keyword,
        "empty forloop tablerowloop" to t.atom,
        "if elsif else endif unless endunless case endcase for endfor tablerow" +
            " endtablerow break continue" to t.controlKeyword,
        "assign capture endcapture" to t.definitionKeyword,
        "contains" to t.operatorKeyword,
        "render include" to t.moduleKeyword,
        "VariableName" to t.variableName,
        "TagName" to t.tagName,
        "FilterName" to t.function(t.variableName),
        "PropertyName" to t.propertyName,
        "CompareOp" to t.compareOperator,
        "AssignOp" to t.definitionOperator,
        "LogicOp" to t.logicOperator,
        "NumberLiteral" to t.number,
        "StringLiteral" to t.string,
        "BooleanLiteral" to t.bool,
        "InlineComment" to t.lineComment,
        "CommentText" to t.blockComment,
        "{% %} {{ }}" to t.brace,
        "[ ]" to t.bracket,
        "( )" to t.paren,
        "." to t.derefOperator,
        ", .. : |" to t.punctuation
    )
)
