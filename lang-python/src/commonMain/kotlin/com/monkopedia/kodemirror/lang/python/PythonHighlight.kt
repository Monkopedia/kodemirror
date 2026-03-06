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
@file:Suppress("ktlint:standard:max-line-length")

package com.monkopedia.kodemirror.lang.python

import com.monkopedia.kodemirror.lezer.highlight.Tags as t
import com.monkopedia.kodemirror.lezer.highlight.styleTags

val pythonHighlighting = styleTags(
    mapOf(
        "async \"*\" \"**\" FormatConversion FormatSpec" to t.modifier,
        "for while if elif else try except finally return raise break continue with pass assert await yield match case" to t.controlKeyword,
        "in not and or is del" to t.operatorKeyword,
        "from def class global nonlocal lambda" to t.definitionKeyword,
        "import" to t.moduleKeyword,
        "with as print" to t.keyword,
        "Boolean" to t.bool,
        "None" to t.`null`,
        "VariableName" to t.variableName,
        "CallExpression/VariableName" to t.function(t.variableName),
        "FunctionDefinition/VariableName" to t.function(t.definition(t.variableName)),
        "ClassDefinition/VariableName" to t.definition(t.className),
        "PropertyName" to t.propertyName,
        "CallExpression/MemberExpression/PropertyName" to t.function(t.propertyName),
        "Comment" to t.lineComment,
        "Number" to t.number,
        "String" to t.string,
        "FormatString" to t.special(t.string),
        "Escape" to t.escape,
        "UpdateOp" to t.updateOperator,
        "ArithOp!" to t.arithmeticOperator,
        "BitOp" to t.bitwiseOperator,
        "CompareOp" to t.compareOperator,
        "AssignOp" to t.definitionOperator,
        "Ellipsis" to t.punctuation,
        "At" to t.meta,
        "( )" to t.paren,
        "[ ]" to t.squareBracket,
        "{ }" to t.brace,
        "." to t.derefOperator,
        ", ;" to t.separator
    )
)
