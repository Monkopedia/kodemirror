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

package com.monkopedia.kodemirror.lang.javascript

import com.monkopedia.kodemirror.lezer.highlight.Tags as t
import com.monkopedia.kodemirror.lezer.highlight.styleTags

val jsHighlight = styleTags(
    mapOf(
        "get set async static" to t.modifier,
        "for while do if else switch try catch finally return throw break continue default case defer" to
            t.controlKeyword,
        "in of await yield void typeof delete instanceof as satisfies" to
            t.operatorKeyword,
        "let var const using function class extends" to t.definitionKeyword,
        "import export from" to t.moduleKeyword,
        "with debugger new" to t.keyword,
        "TemplateString" to t.special(t.string),
        "super" to t.atom,
        "BooleanLiteral" to t.bool,
        "this" to t.self,
        "null" to t.`null`,
        "Star" to t.modifier,
        "VariableName" to t.variableName,
        "CallExpression/VariableName TaggedTemplateExpression/VariableName" to
            t.function(t.variableName),
        "VariableDefinition" to t.definition(t.variableName),
        "Label" to t.labelName,
        "PropertyName" to t.propertyName,
        "PrivatePropertyName" to t.special(t.propertyName),
        "CallExpression/MemberExpression/PropertyName" to
            t.function(t.propertyName),
        "FunctionDeclaration/VariableDefinition" to
            t.function(t.definition(t.variableName)),
        "ClassDeclaration/VariableDefinition" to
            t.definition(t.className),
        "NewExpression/VariableName" to t.className,
        "PropertyDefinition" to t.definition(t.propertyName),
        "PrivatePropertyDefinition" to
            t.definition(t.special(t.propertyName)),
        "UpdateOp" to t.updateOperator,
        "LineComment Hashbang" to t.lineComment,
        "BlockComment" to t.blockComment,
        "Number" to t.number,
        "String" to t.string,
        "Escape" to t.escape,
        "ArithOp" to t.arithmeticOperator,
        "LogicOp" to t.logicOperator,
        "BitOp" to t.bitwiseOperator,
        "CompareOp" to t.compareOperator,
        "RegExp" to t.regexp,
        "Equals" to t.definitionOperator,
        "Arrow" to t.function(t.punctuation),
        ": Spread" to t.punctuation,
        "( )" to t.paren,
        "[ ]" to t.squareBracket,
        "{ }" to t.brace,
        "InterpolationStart InterpolationEnd" to t.special(t.brace),
        "." to t.derefOperator,
        ", ;" to t.separator,
        "@" to t.meta,
        "TypeName" to t.typeName,
        "TypeDefinition" to t.definition(t.typeName),
        "type enum interface implements namespace module declare" to
            t.definitionKeyword,
        "abstract global Privacy readonly override" to t.modifier,
        "is keyof unique infer asserts" to t.operatorKeyword,
        "JSXAttributeValue" to t.attributeValue,
        "JSXText" to t.content,
        "JSXStartTag JSXStartCloseTag JSXSelfCloseEndTag JSXEndTag" to
            t.angleBracket,
        "JSXIdentifier JSXNameSpacedName" to t.tagName,
        "JSXAttribute/JSXIdentifier JSXAttribute/JSXNameSpacedName" to
            t.attributeName,
        "JSXBuiltin/JSXIdentifier" to t.standard(t.tagName)
    )
)
