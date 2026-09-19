/*
 * Copyright 2026 Jason Monk
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

package com.monkopedia.kodemirror.lang.wast

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tree-shape tests for the WebAssembly text-format parser.
 *
 * Every expectation here was taken from upstream `@codemirror/lang-wast` 6.0.2 parsing the
 * same input, rendered through the same [treeToString]. Asserting the shape rather than
 * "did not throw" is deliberate: a parser whose tables are damaged can still return a tree.
 */
class WastParserTest {
    private fun parse(input: String): String = treeToString(wastParser.parse(input))

    private fun errors(input: String): Int = errorCount(wastParser.parse(input))

    @Test
    fun parsesEmptyModule() {
        assertEquals("Module(App(Keyword))", parse("(module)"))
        assertEquals(0, errors("(module)"))
    }

    @Test
    fun parsesFunctionDefinition() {
        val src = """
            (module
              (func ${'$'}add (param ${'$'}a i32) (param ${'$'}b i32) (result i32)
                local.get ${'$'}a
                local.get ${'$'}b
                i32.add))
        """.trimIndent()
        assertEquals(
            "Module(App(Keyword,App(Keyword,Identifier,App(Keyword,Identifier,Type)," +
                "App(Keyword,Identifier,Type),App(Keyword,Type),Keyword,Identifier,Keyword," +
                "Identifier,Keyword)))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesExport() {
        val src = """
            (module
              (func ${'$'}f (result i32) (i32.const 42))
              (export "f" (func ${'$'}f)))
        """.trimIndent()
        assertEquals(
            "Module(App(Keyword,App(Keyword,Identifier,App(Keyword,Type),App(Keyword,Number))," +
                "App(Keyword,String,App(Keyword,Identifier))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesMemoryAndData() {
        val src = """
            (module
              (memory 1 2)
              (data (i32.const 0) "hello\00world"))
        """.trimIndent()
        assertEquals(
            "Module(App(Keyword,App(Keyword,Number,Number)," +
                "App(Keyword,App(Keyword,Number),String)))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesGlobals() {
        val src = """
            (module
              (global ${'$'}g (mut i32) (i32.const 7))
              (global ${'$'}h i64 (i64.const 8)))
        """.trimIndent()
        assertEquals(
            "Module(App(Keyword,App(Keyword,Identifier,App(Keyword,Type),App(Keyword,Number))," +
                "App(Keyword,Identifier,Type,App(Keyword,Number))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesTableAndElem() {
        val src = """
            (module
              (table 2 funcref)
              (elem (i32.const 0) ${'$'}f ${'$'}g)
              (func ${'$'}f) (func ${'$'}g))
        """.trimIndent()
        assertEquals(
            "Module(App(Keyword,App(Keyword,Number,Type)," +
                "App(Keyword,App(Keyword,Number),Identifier,Identifier)," +
                "App(Keyword,Identifier),App(Keyword,Identifier)))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesComments() {
        val src = ";; a line comment\n(; a block comment ;)\n(module)\n"
        assertEquals("Module(LineComment,BlockComment,App(Keyword))", parse(src))
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesIfElse() {
        val src = """
            (module
              (func ${'$'}x (param i32) (result i32)
                local.get 0
                (if (result i32) (then (i32.const 1)) (else (i32.const 2)))))
        """.trimIndent()
        assertEquals(
            "Module(App(Keyword,App(Keyword,Identifier,App(Keyword,Type),App(Keyword,Type)," +
                "Keyword,Number,App(Keyword,App(Keyword,Type),App(Keyword,App(Keyword,Number))," +
                "App(Keyword,App(Keyword,Number))))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesBlockLoopAndBranches() {
        val src = """
            (module
              (func ${'$'}l
                (block ${'$'}b
                  (loop ${'$'}c
                    (br_if ${'$'}b (i32.const 0))
                    (br ${'$'}c)))))
        """.trimIndent()
        assertEquals(
            "Module(App(Keyword,App(Keyword,Identifier,App(Keyword,Identifier," +
                "App(Keyword,Identifier,App(Keyword,Identifier,App(Keyword,Number))," +
                "App(Keyword,Identifier))))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesHexFloatAndInfinity() {
        val src = """
            (module
              (func (result f64) (f64.const 0x1.921fb54442d18p+1))
              (func (result f32) (f32.const inf)))
        """.trimIndent()
        assertEquals(
            "Module(App(Keyword,App(Keyword,App(Keyword,Type),App(Keyword,Number))," +
                "App(Keyword,App(Keyword,Type),App(Keyword,Keyword))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }
}
