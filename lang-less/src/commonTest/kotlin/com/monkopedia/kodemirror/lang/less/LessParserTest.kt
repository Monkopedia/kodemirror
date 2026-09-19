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

package com.monkopedia.kodemirror.lang.less

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tree-shape tests for the Less parser.
 *
 * Every expectation here was taken from upstream `@codemirror/lang-less` 6.0.2 parsing the
 * same input, rendered through the same [treeToString]. Asserting the shape rather than
 * "did not throw" is deliberate: `@import` used to throw, but a parser with a damaged table
 * can just as easily return a plausible-looking tree.
 */
class LessParserTest {
    private fun parse(input: String): String = treeToString(lessParser.parse(input))

    private fun errors(input: String): Int = errorCount(lessParser.parse(input))

    /**
     * The regression from #368: twelve characters (`1G0d1G0dOOQO`) were missing from the
     * `states` table, and `@import` — one of the most common statements in any Less file —
     * crashed the reducer.
     */
    @Test
    fun parsesImport() {
        val src = "@import \"library\";\n@import (reference) \"foo.less\";"
        assertEquals(
            "StyleSheet(ImportStatement(import,StringLiteral)," +
                "ImportStatement(import,ValueName,StringLiteral))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesPlainCss() {
        val src = "body {\n  color: red;\n  margin: 0 auto;\n}\n"
        assertEquals(
            "StyleSheet(RuleSet(TagSelector(TagName),Block(Declaration(PropertyName,ValueName)," +
                "Declaration(PropertyName,NumberLiteral,ValueName))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesVariableDeclarationAndUse() {
        val src = "@link-color: #428bca;\na { color: @link-color; }\n"
        assertEquals(
            "StyleSheet(Declaration(AtKeyword,ColorLiteral)," +
                "RuleSet(TagSelector(TagName),Block(Declaration(PropertyName,AtKeyword))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesMixinDefinitionAndInclusion() {
        val src =
            ".bordered(@width: 2px) {\n  border: @width solid black;\n}\n#menu a { .bordered(4px); }\n"
        assertEquals(
            "StyleSheet(RuleSet(ClassSelector(ClassName),AtKeyword,NumberLiteral(Unit)," +
                "Block(Declaration(PropertyName,AtKeyword,ValueName,ValueName)))," +
                "RuleSet(DescendantSelector(IdSelector(IdName),TagSelector(TagName))," +
                "Block(Inclusion(ClassSelector(ClassName),NumberLiteral(Unit)))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesNesting() {
        val src = "#header {\n  .navigation { font-size: 12px; }\n  &:after { content: \"\"; }\n}\n"
        assertEquals(
            "StyleSheet(RuleSet(IdSelector(IdName),Block(RuleSet(ClassSelector(ClassName)," +
                "Block(Declaration(PropertyName,NumberLiteral(Unit))))," +
                "RuleSet(PseudoClassSelector(NestingSelector,PseudoClassName)," +
                "Block(Declaration(PropertyName,StringLiteral))))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesMixinGuard() {
        val src = ".mixin(@a) when (lightness(@a) >= 50%) {\n  background-color: black;\n}\n"
        assertEquals(
            "StyleSheet(RuleSet(ClassSelector(ClassName),AtKeyword,when," +
                "ParenthesizedValue(BinaryExpression(CallExpression(ValueName,ArgList(AtKeyword))," +
                "BinOp,NumberLiteral(Unit))),Block(Declaration(PropertyName,ValueName))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesDetachedRuleSet() {
        val src = "@detached: {\n  background: red;\n};\n.top { @detached(); }\n"
        assertEquals(
            "StyleSheet(DetachedRuleSet(AtKeyword,Block(Declaration(PropertyName,ValueName)))," +
                "RuleSet(ClassSelector(ClassName),Block(CallExpression(AtKeyword))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesNamespaceInclusion() {
        val src = "#outer() {\n  .inner { color: red; }\n}\n.c { #outer.inner(); }\n"
        assertEquals(
            "StyleSheet(RuleSet(IdSelector(IdName),Block(RuleSet(ClassSelector(ClassName)," +
                "Block(Declaration(PropertyName,ValueName)))))," +
                "RuleSet(ClassSelector(ClassName)," +
                "Block(Inclusion(ClassSelector(IdSelector(IdName),ClassName)))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesEscapeAndArithmetic() {
        val src = "@min768: ~\"(min-width: 768px)\";\n@media @min768 { .a { width: (1 + 1) * 2px; } }\n"
        assertEquals(
            "StyleSheet(Declaration(AtKeyword,Escape),MediaStatement(media,AtKeyword," +
                "Block(RuleSet(ClassSelector(ClassName),Block(Declaration(PropertyName," +
                "BinaryExpression(ParenthesizedValue(BinaryExpression(NumberLiteral,BinOp," +
                "NumberLiteral)),BinOp,NumberLiteral(Unit))))))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesCommentsAndMediaQuery() {
        val src = "// line comment\n/* block comment */\n@media screen and (max-width: 100px) {\n  .x { color: blue; }\n}\n"
        assertEquals(
            "StyleSheet(LineComment,Comment,MediaStatement(media," +
                "BinaryQuery(KeywordQuery,LogicOp,FeatureQuery(FeatureName,NumberLiteral(Unit)))," +
                "Block(RuleSet(ClassSelector(ClassName),Block(Declaration(PropertyName,ValueName))))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }
}
