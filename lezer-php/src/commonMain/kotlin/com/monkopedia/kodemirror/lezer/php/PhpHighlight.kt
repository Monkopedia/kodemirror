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

package com.monkopedia.kodemirror.lezer.php

import com.monkopedia.kodemirror.lezer.highlight.styleTags
import com.monkopedia.kodemirror.lezer.highlight.tags

internal val phpHighlighting = styleTags(
    mapOf(
        "Visibility abstract final static" to tags.modifier,
        "for foreach while do if else elseif switch try catch finally return throw break continue default case" to tags.controlKeyword,
        "endif endfor endforeach endswitch endwhile declare enddeclare goto match" to tags.controlKeyword,
        "and or xor yield unset clone instanceof insteadof" to tags.operatorKeyword,
        "function fn class trait implements extends const enum global interface use var" to tags.definitionKeyword,
        "include include_once require require_once namespace" to tags.moduleKeyword,
        "new from echo print array list as" to tags.keyword,
        "null" to tags.`null`,
        "Boolean" to tags.bool,
        "VariableName" to tags.variableName,
        "NamespaceName/..." to tags.namespace,
        "NamedType/..." to tags.typeName,
        "Name" to tags.name,
        "CallExpression/Name" to tags.function(tags.variableName),
        "LabelStatement/Name" to tags.labelName,
        "MemberExpression/Name" to tags.propertyName,
        "MemberExpression/VariableName" to tags.special(tags.propertyName),
        "ScopedExpression/ClassMemberName/Name" to tags.propertyName,
        "ScopedExpression/ClassMemberName/VariableName" to tags.special(tags.propertyName),
        "CallExpression/MemberExpression/Name" to tags.function(tags.propertyName),
        "CallExpression/ScopedExpression/ClassMemberName/Name" to tags.function(tags.propertyName),
        "MethodDeclaration/Name" to tags.function(tags.definition(tags.variableName)),
        "FunctionDefinition/Name" to tags.function(tags.definition(tags.variableName)),
        "ClassDeclaration/Name" to tags.definition(tags.className),
        "UpdateOp" to tags.updateOperator,
        "ArithOp" to tags.arithmeticOperator,
        "LogicOp IntersectionType/&" to tags.logicOperator,
        "BitOp" to tags.bitwiseOperator,
        "CompareOp" to tags.compareOperator,
        "ControlOp" to tags.controlOperator,
        "AssignOp" to tags.definitionOperator,
        "\$ ConcatOp" to tags.operator,
        "LineComment" to tags.lineComment,
        "BlockComment" to tags.blockComment,
        "Integer" to tags.integer,
        "Float" to tags.float,
        "String" to tags.string,
        "ShellExpression" to tags.special(tags.string),
        "=> ->" to tags.punctuation,
        "( )" to tags.paren,
        "#[ [ ]" to tags.squareBracket,
        "\${ { }" to tags.brace,
        "-> ?->" to tags.derefOperator,
        ", ; :: : \\" to tags.separator,
        "PhpOpen PhpClose" to tags.processingInstruction
    )
)
