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
package com.monkopedia.kodemirror.lang.sass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SassParserTest {

    private fun parse(input: String): String = treeToString(sassParser.parse(input))

    // --- selector.txt tests ---

    @Test
    fun parsesUniversalSelector() = assertEquals(
        "StyleSheet(RuleSet(UniversalSelector,Block))",
        parse("* {}")
    )

    @Test
    fun parsesTypeSelectors() {
        val result = parse("div, span {}\nh1, h2, h3, h4 {}")
        assertTrue(result.contains("TagName"))
        assertTrue(result.contains("RuleSet"))
    }

    @Test
    fun parsesClassSelectors() {
        val result = parse(".class-a {}")
        assertTrue(result.contains("ClassSelector"))
        assertTrue(result.contains("ClassName"))
    }

    @Test
    fun parsesIdSelectors() {
        val result = parse("#some-id, a#another-id {}")
        assertTrue(result.contains("IdSelector"))
        assertTrue(result.contains("IdName"))
    }

    @Test
    fun parsesNestingSelectors() {
        val result = parse("a {\n  &.b {}\n  & c {}\n  & > d {}\n}")
        assertTrue(result.contains("NestingSelector"))
        assertTrue(result.contains("ChildSelector"))
    }

    // --- declarations.txt tests ---

    @Test
    fun parsesFunctionCalls() = assertEquals(
        "StyleSheet(RuleSet(TagSelector(TagName),Block(" +
            "Declaration(PropertyName," +
            "CallExpression(Callee,ArgList(" +
            "NumberLiteral,NumberLiteral,NumberLiteral,NumberLiteral))))))",
        parse("a {\n  color: rgba(0, 255, 0, 0.5);\n}")
    )

    @Test
    fun parsesColorLiterals() {
        val result = parse("a {\n  b: #fafd04;\n  c: #fafd0401;\n}")
        assertEquals(
            "StyleSheet(RuleSet(TagSelector(TagName),Block(" +
                "Declaration(PropertyName,ColorLiteral)," +
                "Declaration(PropertyName,ColorLiteral))))",
            result
        )
    }

    @Test
    fun parsesNumbers() {
        val result = parse("a {\n  b: 0.5%;\n  c: 5em;\n}")
        assertTrue(result.contains("NumberLiteral"))
        assertTrue(result.contains("Unit"))
    }

    @Test
    fun parsesImportantDeclarations() = assertEquals(
        "StyleSheet(RuleSet(TagSelector(TagName),Block(" +
            "Declaration(PropertyName,ValueName,Important))))",
        parse("a {\n  b: c !important;\n}")
    )

    @Test
    fun parsesVariableNames() {
        val result = parse("foo {\n  --my-variable: white;\n  color: var(--my-variable);\n}")
        assertTrue(result.contains("VariableName"))
        assertTrue(result.contains("CallExpression"))
    }

    // --- statements.txt tests ---

    @Test
    fun parsesEmptyStylesheet() = assertEquals(
        "StyleSheet(Comment)",
        parse("/* Just a comment */")
    )

    @Test
    fun parsesImportStatements() {
        val result = parse("@import url(\"fineprint.css\") print;")
        assertTrue(result.contains("ImportStatement"))
        assertTrue(result.contains("import"))
    }

    @Test
    fun parsesKeyframes() {
        val result = parse(
            "@keyframes important1 {\n" +
                "  from { margin-top: 50px; }\n" +
                "  50%  { margin-top: 150px !important; }\n" +
                "  to   { margin-top: 100px; }\n" +
                "}"
        )
        assertTrue(result.contains("KeyframesStatement"))
        assertTrue(result.contains("KeyframeName"))
        assertTrue(result.contains("KeyframeRangeName"))
    }

    @Test
    fun parsesMediaStatements() {
        val result = parse(
            "@media screen and (min-width: 30em) and " +
                "(orientation: landscape) {}"
        )
        assertTrue(result.contains("MediaStatement"))
        assertTrue(result.contains("FeatureQuery"))
    }

    @Test
    fun parsesCharsetStatement() = assertEquals(
        "StyleSheet(CharsetStatement(charset,StringLiteral))",
        parse("@charset \"utf-8\";")
    )

    // --- sass.txt tests ---

    @Test
    fun parsesInclude() = assertEquals(
        "StyleSheet(RuleSet(" +
            "DescendantSelector(TagSelector(TagName),TagSelector(TagName)),Block(" +
            "IncludeStatement(include,ValueName))))",
        parse("nav ul {\n  @include horizontal-list;\n}")
    )

    @Test
    fun parsesLineComment() {
        val result = parse("foo { // blah\n  // Something\n  color: green;\n}")
        assertTrue(result.contains("LineComment"))
        assertTrue(result.contains("Declaration"))
    }

    @Test
    fun parsesTopLevelProperties() = assertEquals(
        "StyleSheet(Declaration(SassVariableName,ValueName))",
        parse("\$color: red;")
    )

    @Test
    fun parsesInterpolation() {
        val result = parse(
            "#{\$prefix} {\n  border-#{\$stuff}-#{\$side}: #{\$value};\n}"
        )
        assertTrue(result.contains("Interpolation"))
        assertTrue(result.contains("InterpolationStart"))
        assertTrue(result.contains("SassVariableName"))
        assertTrue(result.contains("InterpolationEnd"))
    }

    @Test
    fun parsesMixin() {
        val result = parse("@mixin reset-list {\n  padding: 0;\n}")
        assertTrue(result.contains("MixinStatement"))
        assertTrue(result.contains("mixin"))
    }

    @Test
    fun parsesUseStatement() {
        val result = parse("@use 'foundation/code';")
        assertTrue(result.contains("UseStatement"))
        assertTrue(result.contains("use"))
        assertTrue(result.contains("StringLiteral"))
    }

    @Test
    fun parsesForwardStatement() {
        val result = parse(
            "@forward \"src/list\" hide list-reset, " +
                "\$horizontal-list-gap;"
        )
        assertTrue(result.contains("ForwardStatement"))
        assertTrue(result.contains("forward"))
    }

    @Test
    fun parsesControlFlow() {
        val result = parse(
            "@function pow(\$base, \$exponent) {\n" +
                "  \$result: 1;\n" +
                "  @for \$_ from 1 through \$exponent {\n" +
                "    \$result: \$result * \$base;\n" +
                "  }\n" +
                "  @return \$result;\n" +
                "}"
        )
        assertTrue(result.contains("MixinStatement"))
        assertTrue(result.contains("ForStatement"))
        assertTrue(result.contains("ControlKeyword"))
        assertTrue(result.contains("OutputStatement"))
    }

    @Test
    fun parsesPlaceholderSelector() {
        val result = parse("%theme-button {}")
        assertTrue(result.contains("PlaceholderSelector"))
        assertTrue(result.contains("ClassName"))
    }

    @Test
    fun parsesSuffixedSelector() {
        val result = parse(".foo {\n  &__bar {\n  }\n}")
        assertTrue(result.contains("SuffixedSelector"))
        assertTrue(result.contains("NestingSelector"))
        assertTrue(result.contains("Suffix"))
    }
}
