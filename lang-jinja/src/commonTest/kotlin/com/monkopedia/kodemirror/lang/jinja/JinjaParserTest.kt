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
package com.monkopedia.kodemirror.lang.jinja

import com.monkopedia.kodemirror.lezer.lr.LRParser
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tree-shape tests for the serialized Jinja LR tables.
 *
 * Every expectation here is the tree `@codemirror/lang-jinja@6.0.1` produces for the same input,
 * so a divergence in the transcribed `states`/`stateData`/`goto`/`tokenData`/`nodeNames` literals
 * shows up as a failing assertion rather than as a silently different parse.
 *
 * The assertions run against [jinjaTagLanguage] — the standalone Jinja parser with no HTML
 * mixing — so a failure localises to the tables and cannot be blamed on `parseMixed`.
 */
class JinjaParserTest {

    private val tagParser get() = jinjaTagLanguage.parser as LRParser

    private fun parse(input: String): String {
        val tree = tagParser.parse(input)
        return "errors=${errorCount(tree)} ${treeToString(tree)}"
    }

    private fun parseWithPositions(input: String): String {
        val tree = tagParser.parse(input)
        return "errors=${errorCount(tree)} ${treeToString(tree, withPositions = true)}"
    }

    @Test
    fun parsesPlainHtmlWithNoJinjaAsText() {
        assertEquals("errors=0 Template(Text)", parse("<p>hello</p>"))
    }

    @Test
    fun parsesOutputInterpolation() {
        assertEquals(
            "errors=0 Template(Interpolation(MemberExpression(VariableName,PropertyName)))",
            parse("{{ user.name }}")
        )
    }

    @Test
    fun parsesFilterExpression() {
        assertEquals(
            "errors=0 Template(Interpolation(" +
                "FilterExpression(VariableName,FilterOp,FilterName)))",
            parse("{{ x | upper }}")
        )
    }

    @Test
    fun parsesIfElifElseChain() {
        assertEquals(
            "errors=0 Template(IfStatement(" +
                "Tag(if,VariableName),Text,Tag(elif,VariableName),Text,Tag(else),Text," +
                "EndTag(endif)))",
            parse("{% if a %}x{% elif b %}y{% else %}z{% endif %}")
        )
    }

    @Test
    fun parsesForStatement() {
        assertEquals(
            "errors=0 Template(ForStatement(" +
                "Tag(for,Definition,in,VariableName)," +
                "Interpolation(VariableName)," +
                "EndTag(endfor)))",
            parse("{% for x in y %}{{ x }}{% endfor %}")
        )
    }

    @Test
    fun parsesSetTag() {
        assertEquals(
            "errors=0 Template(Tag(set,Definition,AssignOp,NumberLiteral))",
            parse("{% set n = 1 %}")
        )
    }

    @Test
    fun parsesHashComment() {
        assertEquals("errors=0 Template(Comment)", parse("{# hi #}"))
    }

    @Test
    fun parsesRawStatementWithoutInterpretingItsBody() {
        assertEquals(
            "errors=0 Template(RawStatement(Tag(raw),RawText,EndTag(endraw)))",
            parse("{% raw %}{{ x }}{% endraw %}")
        )
    }

    @Test
    fun parsesBlockStatement() {
        assertEquals(
            "errors=0 Template(BlockStatement(Tag(block,Definition),Text,EndTag(endblock)))",
            parse("{% block body %}hi{% endblock %}")
        )
    }

    @Test
    fun parsesMacroStatement() {
        assertEquals(
            "errors=0 Template(MacroStatement(" +
                "Tag(macro,Definition,ParamList(Definition))," +
                "Interpolation(VariableName)," +
                "EndTag(endmacro)))",
            parse("{% macro m(a) %}{{ a }}{% endmacro %}")
        )
    }

    @Test
    fun parsesExtendsTag() {
        assertEquals(
            "errors=0 Template(Tag(TagName,StringLiteral))",
            parse("{% extends \"base.html\" %}")
        )
    }

    @Test
    fun parsesIncludeTag() {
        assertEquals(
            "errors=0 Template(Tag(include,StringLiteral))",
            parse("{% include \"p.html\" %}")
        )
    }

    @Test
    fun parsesWithStatement() {
        assertEquals(
            "errors=0 Template(WithStatement(" +
                "Tag(with,Definition,AssignOp,NumberLiteral)," +
                "Interpolation(VariableName)," +
                "EndTag(endwith)))",
            parse("{% with a = 1 %}{{ a }}{% endwith %}")
        )
    }

    @Test
    fun parsesFilterStatement() {
        assertEquals(
            "errors=0 Template(FilterStatement(Tag(filter,FilterName),Text,EndTag(endfilter)))",
            parse("{% filter upper %}hi{% endfilter %}")
        )
    }

    @Test
    fun parsesCallStatement() {
        assertEquals(
            "errors=0 Template(CallStatement(" +
                "Tag(call,VariableName,ArgumentList),Text,EndTag(endcall)))",
            parse("{% call m() %}hi{% endcall %}")
        )
    }

    @Test
    fun parsesIsTest() {
        assertEquals(
            "errors=0 Template(Interpolation(BinaryExpression(VariableName,is,VariableName)))",
            parse("{{ a is defined }}")
        )
    }

    @Test
    fun parsesStatementsInterleavedWithMarkup() {
        assertEquals(
            "errors=0 Template(Text,ForStatement(" +
                "Tag(for,Definition,in,VariableName),Text,Interpolation(VariableName),Text," +
                "EndTag(endfor)),Text)",
            parse("<ul>{% for i in items %}<li>{{ i }}</li>{% endfor %}</ul>")
        )
    }

    @Test
    fun parsesEmptyDocument() {
        assertEquals("errors=0 Template", parse(""))
    }

    @Test
    fun assignsUpstreamTokenPositions() {
        assertEquals(
            "errors=0 Template[0,35](ForStatement[0,35](" +
                "Tag[0,16](for[3,6],Definition[7,8],in[9,11],VariableName[12,13])," +
                "Interpolation[16,23](VariableName[19,20])," +
                "EndTag[23,35](endfor[26,32])))",
            parseWithPositions("{% for x in y %}{{ x }}{% endfor %}")
        )
    }

    @Test
    fun assignsUpstreamTokenPositionsForBlockStatements() {
        assertEquals(
            "errors=0 Template[0,32](BlockStatement[0,32](" +
                "Tag[0,16](block[3,8],Definition[9,13]),Text[16,18]," +
                "EndTag[18,32](endblock[21,29])))",
            parseWithPositions("{% block body %}hi{% endblock %}")
        )
    }

    @Test
    fun htmlMixedLanguageParsesTheSameJinjaStructure() {
        val tree = (jinjaLanguage.parser as LRParser).parse(
            "<ul>{% for i in items %}<li>{{ i }}</li>{% endfor %}</ul>"
        )
        assertEquals(0, errorCount(tree))
        assertEquals(
            "Template(Text,ForStatement(" +
                "Tag(for,Definition,in,VariableName),Text,Interpolation(VariableName),Text," +
                "EndTag(endfor)),Text)",
            treeToString(tree)
        )
    }
}
