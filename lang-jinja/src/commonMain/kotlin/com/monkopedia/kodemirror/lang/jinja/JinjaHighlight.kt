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
package com.monkopedia.kodemirror.lang.jinja

import com.monkopedia.kodemirror.lezer.highlight.styleTags
import com.monkopedia.kodemirror.lezer.highlight.tags as t

internal val jinjaHighlighting = styleTags(
    mapOf(
        "TagName raw endraw filter endfilter as trans pluralize endtrans with endwith" +
            " autoescape endautoescape" to t.keyword,
        "required scoped recursive with without context ignore missing" to t.modifier,
        "self" to t.self,
        "loop super" to t.standard(t.variableName),
        "if elif else endif for endfor call endcall" to t.controlKeyword,
        "block endblock set endset macro endmacro import from include" to t.definitionKeyword,
        "Comment/..." to t.blockComment,
        "VariableName" to t.variableName,
        "Definition" to t.definition(t.variableName),
        "PropertyName" to t.propertyName,
        "FilterName" to t.special(t.variableName),
        "ArithOp" to t.arithmeticOperator,
        "AssignOp" to t.definitionOperator,
        "not and or" to t.logicOperator,
        "CompareOp" to t.compareOperator,
        "in is" to t.operatorKeyword,
        "FilterOp ConcatOp" to t.operator,
        "StringLiteral" to t.string,
        "NumberLiteral" to t.number,
        "BooleanLiteral" to t.bool,
        "{% %} {# #} {{ }} { }" to t.brace,
        "( )" to t.paren,
        "." to t.derefOperator,
        ": , ." to t.punctuation
    )
)
