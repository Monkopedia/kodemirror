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
package com.monkopedia.kodemirror.lezer.css

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CssParserTest {

    private fun parse(input: String): String = treeToString(parser.parse(input))

    @Test
    fun parsesSimpleRule() {
        val result = parse("h1 { color: red }")
        assertTrue(result.contains("RuleSet"))
        assertTrue(result.contains("TagName"))
        assertTrue(result.contains("Block"))
        assertTrue(result.contains("Declaration"))
        assertTrue(result.contains("PropertyName"))
    }

    @Test
    fun parsesClassSelector() {
        val result = parse(".foo { margin: 0 }")
        assertTrue(result.contains("ClassSelector"))
        assertTrue(result.contains("ClassName"))
    }

    @Test
    fun parsesIdSelector() {
        val result = parse("#bar { padding: 10px }")
        assertTrue(result.contains("IdSelector"))
        assertTrue(result.contains("IdName"))
        assertTrue(result.contains("NumberLiteral"))
        assertTrue(result.contains("Unit"))
    }

    @Test
    fun parsesImport() {
        val result = parse("@import url(\"foo.css\");")
        assertTrue(result.contains("ImportStatement"))
    }

    @Test
    fun parsesMediaQuery() {
        val result = parse(
            "@media screen and (min-width: 768px) { body { margin: 0 } }"
        )
        assertTrue(result.contains("MediaStatement"))
    }

    @Test
    fun parsesPseudoClass() {
        val result = parse("a:hover { color: blue }")
        assertTrue(result.contains("PseudoClassSelector"))
    }

    @Test
    fun parsesCssVariable() {
        val result = parse(":root { --my-var: red }")
        assertTrue(result.contains("VariableName"))
    }

    @Test
    fun parsesComment() {
        val result = parse("/* comment */ h1 { }")
        assertTrue(result.contains("RuleSet"))
    }

    @Test
    fun parsesDescendantSelector() {
        val result = parse("div p { color: red }")
        assertTrue(result.contains("DescendantSelector"))
    }

    @Test
    fun parsesMultipleDeclarations() {
        val result = parse("h1 { color: red; font-size: 16px }")
        assertTrue(result.contains("Declaration"))
        assertTrue(result.contains("PropertyName"))
    }

    @Test
    fun parsesUniversalSelector() {
        val result = parse("* { margin: 0 }")
        assertTrue(result.contains("UniversalSelector"))
    }

    @Test
    fun parsesChildSelector() {
        val result = parse("ul > li { list-style: none }")
        assertTrue(result.contains("ChildSelector"))
    }

    @Test
    fun parsesKeyframes() {
        val result = parse("@keyframes slide { from { left: 0 } to { left: 100px } }")
        assertTrue(result.contains("KeyframesStatement"))
        assertTrue(result.contains("KeyframeName"))
    }

    @Test
    fun parsesColorLiteral() {
        val result = parse("h1 { color: #ff0000 }")
        assertTrue(result.contains("ColorLiteral"))
    }

    @Test
    fun parsesStringLiteral() {
        val result = parse("h1 { content: \"hello\" }")
        assertTrue(result.contains("StringLiteral"))
    }

    @Test
    fun parsesComplexStylesheet() {
        val input = """
            .container { display: flex; }
            #main > .content { padding: 10px; }
            @media (max-width: 600px) {
                .container { flex-direction: column; }
            }
        """.trimIndent()
        val result = parse(input)
        assertEquals("StyleSheet", result.substringBefore("("))
    }
}
