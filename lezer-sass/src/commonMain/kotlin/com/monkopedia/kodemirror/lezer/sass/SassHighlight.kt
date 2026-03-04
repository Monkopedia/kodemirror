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
package com.monkopedia.kodemirror.lezer.sass

import com.monkopedia.kodemirror.lezer.highlight.styleTags
import com.monkopedia.kodemirror.lezer.highlight.tags as t

val sassHighlighting = styleTags(
    mapOf(
        "AtKeyword import charset namespace keyframes media supports " +
            "include mixin use forward extend at-root" to t.definitionKeyword,
        "Keyword selector" to t.keyword,
        "ControlKeyword" to t.controlKeyword,
        "NamespaceName" to t.namespace,
        "KeyframeName" to t.labelName,
        "KeyframeRangeName" to t.operatorKeyword,
        "TagName" to t.tagName,
        "ClassName Suffix" to t.className,
        "PseudoClassName" to t.constant(t.className),
        "IdName" to t.labelName,
        "FeatureName PropertyName" to t.propertyName,
        "AttributeName" to t.attributeName,
        "NumberLiteral" to t.number,
        "KeywordQuery" to t.keyword,
        "UnaryQueryOp" to t.operatorKeyword,
        "CallTag ValueName" to t.atom,
        "VariableName" to t.variableName,
        "SassVariableName" to t.special(t.variableName),
        "Callee" to t.operatorKeyword,
        "Unit" to t.unit,
        "UniversalSelector NestingSelector IndentedMixin IndentedInclude" to
            t.definitionOperator,
        "MatchOp" to t.compareOperator,
        "ChildOp SiblingOp, LogicOp" to t.logicOperator,
        "BinOp" to t.arithmeticOperator,
        "Important Global Default" to t.modifier,
        "Comment" to t.blockComment,
        "LineComment" to t.lineComment,
        "ColorLiteral" to t.color,
        "ParenthesizedContent StringLiteral" to t.string,
        "InterpolationStart InterpolationContinue InterpolationEnd" to t.meta,
        ": \"...\"" to t.punctuation,
        "PseudoOp #" to t.derefOperator,
        "; ," to t.separator,
        "( )" to t.paren,
        "[ ]" to t.squareBracket,
        "{ }" to t.brace
    )
)
