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
package com.monkopedia.kodemirror.lang.javascript

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for a reduce that has to sink past two or more skipped
 * (comment) nodes in [com.monkopedia.kodemirror.lezer.lr.Stack.storeNode].
 *
 * Expected trees are the output of `@lezer/javascript` on top of
 * `@lezer/lr@1.4.10`, rendered through the same filter as [treeToString].
 */
class StoreNodeSinkTest {

    private fun parse(input: String): String = treeToString(jsParser.parse(input))

    @Test
    fun trailingLineCommentsDoNotProduceGarbageNodes() {
        assertEquals(
            "Script(VariableDeclaration(let,VariableDefinition,Equals,Number)," +
                "LineComment,LineComment)",
            parse("let x = 1\n// a\n// b\n")
        )
    }

    @Test
    fun threeTrailingLineCommentsDoNotProduceGarbageNodes() {
        assertEquals(
            "Script(VariableDeclaration(let,VariableDefinition,Equals,Number)," +
                "LineComment,LineComment,LineComment)",
            parse("let x = 1\n// a\n// b\n// c\n")
        )
    }

    @Test
    fun trailingBlockCommentsDoNotProduceGarbageNodes() {
        assertEquals(
            "Script(VariableDeclaration(let,VariableDefinition,Equals,Number)," +
                "BlockComment,BlockComment)",
            parse("let x = 1\n/*a*/ /*b*/\n")
        )
    }

    // Previously threw ArrayIndexOutOfBoundsException out of Tree.build.
    @Test
    fun assignmentFollowedByTwoBlockCommentsParses() {
        assertEquals(
            "Script(ExpressionStatement(AssignmentExpression(VariableName,Equals,Number))," +
                "BlockComment,BlockComment)",
            parse("x = 1 /*a*/ /*b*/")
        )
    }

    @Test
    fun commentsBetweenStatementsKeepCorrectNesting() {
        assertEquals(
            "Script(IfStatement(if,ParenthesizedExpression(VariableName)," +
                "ExpressionStatement(VariableName)),LineComment,LineComment," +
                "ExpressionStatement(VariableName))",
            parse("if (x) y\n// a\n// b\nz")
        )
    }

    @Test
    fun commentsInsideObjectLiteralKeepCorrectNesting() {
        assertEquals(
            "Script(ExpressionStatement(ParenthesizedExpression(ObjectExpression(" +
                "Property(PropertyDefinition,Number),BlockComment,BlockComment))))",
            parse("({a: 1 /*x*/ /*y*/})")
        )
    }

    // A single skipped node still only needs the size decrement, not a shift;
    // guards the non-sinking path against regressions from the fix.
    @Test
    fun singleTrailingLineCommentParses() {
        assertEquals(
            "Script(VariableDeclaration(let,VariableDefinition,Equals,Number),LineComment)",
            parse("let x = 1\n// a\n")
        )
    }
}
