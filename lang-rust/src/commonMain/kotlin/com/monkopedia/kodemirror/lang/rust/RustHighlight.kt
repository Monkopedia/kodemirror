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
package com.monkopedia.kodemirror.lang.rust

import com.monkopedia.kodemirror.lezer.highlight.styleTags
import com.monkopedia.kodemirror.lezer.highlight.tags as t

val rustHighlighting = styleTags(
    mapOf(
        "const macro_rules struct union enum type fn impl trait let static" to
            t.definitionKeyword,
        "mod use crate" to t.moduleKeyword,
        "pub unsafe async mut extern default move" to t.modifier,
        "for if else loop while match continue break return await" to t.controlKeyword,
        "as in ref" to t.operatorKeyword,
        "where _ crate super dyn" to t.keyword,
        "self" to t.self,
        "String" to t.string,
        "Char" to t.character,
        "RawString" to t.special(t.string),
        "Boolean" to t.bool,
        "Identifier" to t.variableName,
        "CallExpression/Identifier" to t.function(t.variableName),
        "BoundIdentifier" to t.definition(t.variableName),
        "FunctionItem/BoundIdentifier" to
            t.function(t.definition(t.variableName)),
        "LoopLabel" to t.labelName,
        "FieldIdentifier" to t.propertyName,
        "CallExpression/FieldExpression/FieldIdentifier" to
            t.function(t.propertyName),
        "Lifetime" to t.special(t.variableName),
        "ScopeIdentifier" to t.namespace,
        "TypeIdentifier" to t.typeName,
        "MacroInvocation/Identifier MacroInvocation/ScopedIdentifier/Identifier" to
            t.macroName,
        "MacroInvocation/TypeIdentifier MacroInvocation/ScopedIdentifier/TypeIdentifier" to
            t.macroName,
        "\"!\"" to t.macroName,
        "UpdateOp" to t.updateOperator,
        "LineComment" to t.lineComment,
        "BlockComment" to t.blockComment,
        "Integer" to t.integer,
        "Float" to t.float,
        "ArithOp" to t.arithmeticOperator,
        "LogicOp" to t.logicOperator,
        "BitOp" to t.bitwiseOperator,
        "CompareOp" to t.compareOperator,
        "=" to t.definitionOperator,
        ".. ... => ->" to t.punctuation,
        "( )" to t.paren,
        "[ ]" to t.squareBracket,
        "{ }" to t.brace,
        ". DerefOp" to t.derefOperator,
        "&" to t.operator,
        ", ; ::" to t.separator,
        "Attribute/..." to t.meta
    )
)
