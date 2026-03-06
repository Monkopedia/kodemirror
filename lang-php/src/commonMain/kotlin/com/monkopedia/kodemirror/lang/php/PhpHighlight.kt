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

package com.monkopedia.kodemirror.lang.php

import com.monkopedia.kodemirror.lezer.highlight.Tags
import com.monkopedia.kodemirror.lezer.highlight.styleTags

internal val phpHighlighting = styleTags(
    mapOf(
        "Visibility abstract final static" to Tags.modifier,
        "for foreach while do if else elseif switch try catch finally return throw break continue default case" to Tags.controlKeyword,
        "endif endfor endforeach endswitch endwhile declare enddeclare goto match" to Tags.controlKeyword,
        "and or xor yield unset clone instanceof insteadof" to Tags.operatorKeyword,
        "function fn class trait implements extends const enum global interface use var" to Tags.definitionKeyword,
        "include include_once require require_once namespace" to Tags.moduleKeyword,
        "new from echo print array list as" to Tags.keyword,
        "null" to Tags.`null`,
        "Boolean" to Tags.bool,
        "VariableName" to Tags.variableName,
        "NamespaceName/..." to Tags.namespace,
        "NamedType/..." to Tags.typeName,
        "Name" to Tags.name,
        "CallExpression/Name" to Tags.function(Tags.variableName),
        "LabelStatement/Name" to Tags.labelName,
        "MemberExpression/Name" to Tags.propertyName,
        "MemberExpression/VariableName" to Tags.special(Tags.propertyName),
        "ScopedExpression/ClassMemberName/Name" to Tags.propertyName,
        "ScopedExpression/ClassMemberName/VariableName" to Tags.special(Tags.propertyName),
        "CallExpression/MemberExpression/Name" to Tags.function(Tags.propertyName),
        "CallExpression/ScopedExpression/ClassMemberName/Name" to Tags.function(Tags.propertyName),
        "MethodDeclaration/Name" to Tags.function(Tags.definition(Tags.variableName)),
        "FunctionDefinition/Name" to Tags.function(Tags.definition(Tags.variableName)),
        "ClassDeclaration/Name" to Tags.definition(Tags.className),
        "UpdateOp" to Tags.updateOperator,
        "ArithOp" to Tags.arithmeticOperator,
        "LogicOp IntersectionType/&" to Tags.logicOperator,
        "BitOp" to Tags.bitwiseOperator,
        "CompareOp" to Tags.compareOperator,
        "ControlOp" to Tags.controlOperator,
        "AssignOp" to Tags.definitionOperator,
        "\$ ConcatOp" to Tags.operator,
        "LineComment" to Tags.lineComment,
        "BlockComment" to Tags.blockComment,
        "Integer" to Tags.integer,
        "Float" to Tags.float,
        "String" to Tags.string,
        "ShellExpression" to Tags.special(Tags.string),
        "=> ->" to Tags.punctuation,
        "( )" to Tags.paren,
        "#[ [ ]" to Tags.squareBracket,
        "\${ { }" to Tags.brace,
        "-> ?->" to Tags.derefOperator,
        ", ; :: : \\" to Tags.separator,
        "PhpOpen PhpClose" to Tags.processingInstruction
    )
)
