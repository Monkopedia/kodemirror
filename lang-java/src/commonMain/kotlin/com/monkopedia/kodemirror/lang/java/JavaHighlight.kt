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

package com.monkopedia.kodemirror.lang.java

import com.monkopedia.kodemirror.lezer.highlight.Tags as t
import com.monkopedia.kodemirror.lezer.highlight.styleTags

val javaHighlighting = styleTags(
    mapOf(
        "null" to t.`null`,
        "instanceof" to t.operatorKeyword,
        "this" to t.self,
        "new super assert open to with void" to t.keyword,
        "class interface extends implements enum var" to t.definitionKeyword,
        "module package import" to t.moduleKeyword,
        "switch while for if else case default do break continue return try catch finally throw" to t.controlKeyword,
        "requires exports opens uses provides public private protected static transitive abstract final strictfp synchronized native transient volatile throws" to t.modifier,
        "IntegerLiteral" to t.integer,
        "FloatingPointLiteral" to t.float,
        "StringLiteral TextBlock" to t.string,
        "CharacterLiteral" to t.character,
        "LineComment" to t.lineComment,
        "BlockComment" to t.blockComment,
        "BooleanLiteral" to t.bool,
        "PrimitiveType" to t.standard(t.typeName),
        "TypeName" to t.typeName,
        "Identifier" to t.variableName,
        "MethodName/Identifier" to t.function(t.variableName),
        "Definition" to t.definition(t.variableName),
        "ArithOp" to t.arithmeticOperator,
        "LogicOp" to t.logicOperator,
        "BitOp" to t.bitwiseOperator,
        "CompareOp" to t.compareOperator,
        "AssignOp" to t.definitionOperator,
        "UpdateOp" to t.updateOperator,
        "Asterisk" to t.punctuation,
        "Label" to t.labelName,
        "( )" to t.paren,
        "[ ]" to t.squareBracket,
        "{ }" to t.brace,
        "." to t.derefOperator,
        ", ;" to t.separator
    )
)
