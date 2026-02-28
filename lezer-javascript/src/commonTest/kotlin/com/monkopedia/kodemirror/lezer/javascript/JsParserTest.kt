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
package com.monkopedia.kodemirror.lezer.javascript

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsParserTest {

    private fun parse(input: String): String = treeToString(parser.parse(input))

    @Test
    fun parsesVariableDeclaration() {
        val result = parse("let x = 1")
        assertTrue(result.contains("VariableDeclaration"))
        assertTrue(result.contains("VariableDefinition"))
        assertTrue(result.contains("Number"))
    }

    @Test
    fun parsesFunctionDeclaration() {
        val result = parse("function foo() {}")
        assertTrue(result.contains("FunctionDeclaration"))
        assertTrue(result.contains("VariableDefinition"))
    }

    @Test
    fun parsesArrowFunction() {
        val result = parse("const f = (x) => x + 1")
        assertTrue(result.contains("ArrowFunction"))
    }

    @Test
    fun parsesClassDeclaration() {
        val result = parse("class Foo extends Bar {}")
        assertTrue(result.contains("ClassDeclaration"))
        assertTrue(result.contains("VariableDefinition"))
    }

    @Test
    fun parsesIfElse() {
        val result = parse("if (x) { y } else { z }")
        assertTrue(result.contains("IfStatement"))
    }

    @Test
    fun parsesForLoop() {
        val result = parse("for (let i = 0; i < 10; i++) {}")
        assertTrue(result.contains("ForStatement"))
    }

    @Test
    fun parsesTemplateLiteral() {
        val result = parse("`hello \${name}`")
        assertTrue(result.contains("TemplateString"))
        assertTrue(result.contains("Interpolation"))
    }

    @Test
    fun parsesObjectLiteral() {
        val result = parse("let o = { a: 1, b: 2 }")
        assertTrue(result.contains("ObjectExpression"))
        assertTrue(result.contains("Property"))
    }

    @Test
    fun parsesImportDeclaration() {
        val result = parse("import { x } from \"y\"")
        assertTrue(result.contains("ImportDeclaration"))
    }

    @Test
    fun parsesAsyncAwait() {
        val result = parse("async function f() { await x }")
        assertTrue(result.contains("FunctionDeclaration"))
    }

    @Test
    fun parsesLineComment() {
        val result = parse("// line comment\nlet x = 1")
        assertTrue(result.contains("VariableDeclaration"))
    }

    @Test
    fun parsesBlockComment() {
        val result = parse("/* block */\nlet x = 1")
        assertTrue(result.contains("VariableDeclaration"))
    }

    @Test
    fun parsesAutoSemicolonInsertion() {
        val result = parse("let x = 1\nlet y = 2")
        assertTrue(result.contains("VariableDeclaration"))
    }

    @Test
    fun parsesExpressionStatement() {
        val result = parse("x + y")
        assertTrue(result.contains("BinaryExpression"))
    }

    @Test
    fun parsesReturnStatement() {
        val result = parse("function f() { return 42 }")
        assertTrue(result.contains("ReturnStatement"))
    }

    @Test
    fun parsesArrayExpression() {
        val result = parse("let a = [1, 2, 3]")
        assertTrue(result.contains("ArrayExpression"))
    }

    @Test
    fun parsesComplexProgram() {
        val input = """
            const add = (a, b) => a + b;
            function greet(name) {
                return `Hello, ${'$'}{name}!`;
            }
            class Calculator {
                constructor(value) {
                    this.value = value;
                }
            }
        """.trimIndent()
        val result = parse(input)
        assertEquals("Script", result.substringBefore("("))
    }
}
