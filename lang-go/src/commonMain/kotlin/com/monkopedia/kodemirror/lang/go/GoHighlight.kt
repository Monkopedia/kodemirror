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
package com.monkopedia.kodemirror.lang.go

import com.monkopedia.kodemirror.lezer.highlight.Tags as t
import com.monkopedia.kodemirror.lezer.highlight.styleTags

val goHighlighting = styleTags(
    mapOf(
        "func interface struct chan map const type var" to t.definitionKeyword,
        "import package" to t.moduleKeyword,
        "switch for go select return break continue goto fallthrough case if else defer" to
            t.controlKeyword,
        "range" to t.keyword,
        "Bool" to t.bool,
        "String" to t.string,
        "Rune" to t.character,
        "Number" to t.number,
        "Nil" to t.`null`,
        "VariableName" to t.variableName,
        "DefName" to t.definition(t.variableName),
        "TypeName" to t.typeName,
        "LabelName" to t.labelName,
        "FieldName" to t.propertyName,
        "FunctionDecl/DefName" to t.function(t.definition(t.variableName)),
        "TypeSpec/DefName" to t.definition(t.typeName),
        "CallExpr/VariableName" to t.function(t.variableName),
        "LineComment" to t.lineComment,
        "BlockComment" to t.blockComment,
        "LogicOp" to t.logicOperator,
        "ArithOp" to t.arithmeticOperator,
        "BitOp" to t.bitwiseOperator,
        "DerefOp ." to t.derefOperator,
        "UpdateOp IncDecOp" to t.updateOperator,
        "CompareOp" to t.compareOperator,
        "= :=" to t.definitionOperator,
        "<-" to t.operator,
        "~ \"*\"" to t.modifier,
        "; ," to t.separator,
        "... :" to t.punctuation,
        "( )" to t.paren,
        "[ ]" to t.squareBracket,
        "{ }" to t.brace
    )
)
