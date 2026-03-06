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

package com.monkopedia.kodemirror.lang.cpp

import com.monkopedia.kodemirror.lezer.highlight.Tags as t
import com.monkopedia.kodemirror.lezer.highlight.styleTags

val cppHighlighting = styleTags(
    mapOf(
        "typedef struct union enum class typename decltype auto template operator friend noexcept namespace using requires concept import export module __attribute__ __declspec __based" to
            t.definitionKeyword,
        "extern MsCallModifier MsPointerModifier extern static register thread_local inline const volatile restrict _Atomic mutable constexpr constinit consteval virtual explicit VirtualSpecifier Access" to
            t.modifier,
        "if else switch for while do case default return break continue goto throw try catch" to
            t.controlKeyword,
        "co_return co_yield co_await" to t.controlKeyword,
        "new sizeof delete static_assert" to t.operatorKeyword,
        "NULL nullptr" to t.`null`,
        "this" to t.self,
        "True False" to t.bool,
        "TypeSize PrimitiveType" to t.standard(t.typeName),
        "TypeIdentifier" to t.typeName,
        "FieldIdentifier" to t.propertyName,
        "CallExpression/FieldExpression/FieldIdentifier" to t.function(t.propertyName),
        "ModuleName/Identifier" to t.namespace,
        "PartitionName" to t.labelName,
        "StatementIdentifier" to t.labelName,
        "Identifier DestructorName" to t.variableName,
        "CallExpression/Identifier" to t.function(t.variableName),
        "CallExpression/ScopedIdentifier/Identifier" to t.function(t.variableName),
        "FunctionDeclarator/Identifier FunctionDeclarator/DestructorName" to
            t.function(t.definition(t.variableName)),
        "NamespaceIdentifier" to t.namespace,
        "OperatorName" to t.operator,
        "ArithOp" to t.arithmeticOperator,
        "LogicOp" to t.logicOperator,
        "BitOp" to t.bitwiseOperator,
        "CompareOp" to t.compareOperator,
        "AssignOp" to t.definitionOperator,
        "UpdateOp" to t.updateOperator,
        "LineComment" to t.lineComment,
        "BlockComment" to t.blockComment,
        "Number" to t.number,
        "String" to t.string,
        "RawString SystemLibString" to t.special(t.string),
        "CharLiteral" to t.character,
        "EscapeSequence" to t.escape,
        "UserDefinedLiteral/Identifier" to t.literal,
        "PreProcArg" to t.meta,
        "PreprocDirectiveName #include #ifdef #ifndef #if #define #else #endif #elif" to
            t.processingInstruction,
        "MacroName" to t.special(t.name),
        "( )" to t.paren,
        "[ ]" to t.squareBracket,
        "{ }" to t.brace,
        "< >" to t.angleBracket,
        ". ->" to t.derefOperator,
        ", ;" to t.separator
    )
)
