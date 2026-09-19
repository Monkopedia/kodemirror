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
package com.monkopedia.kodemirror.lang.liquid

import com.monkopedia.kodemirror.lezer.lr.LRParser
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tree-shape tests for the serialized Liquid LR tables.
 *
 * Every expectation here is the tree `@codemirror/lang-liquid@6.3.2` produces for the same
 * input, so a divergence in the transcribed `states`/`stateData`/`goto`/`tokenData`/`nodeNames`
 * literals shows up as a failing assertion rather than as a silently different parse.
 *
 * The assertions run against [liquidTagLanguage] — the standalone Liquid parser with no HTML
 * mixing — so a failure localises to the tables and cannot be blamed on `parseMixed`.
 */
class LiquidParserTest {

    private val tagParser get() = liquidTagLanguage.parser as LRParser

    private fun parse(input: String): String {
        val tree = tagParser.parse(input)
        return "errors=${errorCount(tree)} ${treeToString(tree)}"
    }

    private fun parseWithPositions(input: String): String {
        val tree = tagParser.parse(input)
        return "errors=${errorCount(tree)} ${treeToString(tree, withPositions = true)}"
    }

    @Test
    fun parsesPlainHtmlWithNoLiquidAsText() {
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
    fun parsesFilter() {
        assertEquals(
            "errors=0 Template(Interpolation(StringLiteral,Filter(FilterName)))",
            parse("{{ \"x\" | upcase }}")
        )
    }

    @Test
    fun parsesIfDirective() {
        assertEquals(
            "errors=0 Template(IfDirective(Tag(if,VariableName),Text,EndTag(endif)))",
            parse("{% if a %}b{% endif %}")
        )
    }

    @Test
    fun parsesForDirective() {
        assertEquals(
            "errors=0 Template(ForDirective(" +
                "Tag(for,VariableName,in,VariableName)," +
                "Interpolation(VariableName)," +
                "EndTag(endfor)))",
            parse("{% for x in y %}{{ x }}{% endfor %}")
        )
    }

    @Test
    fun parsesUnlessDirective() {
        assertEquals(
            "errors=0 Template(UnlessDirective(Tag(unless,VariableName),Text,EndTag(endunless)))",
            parse("{% unless a %}b{% endunless %}")
        )
    }

    @Test
    fun parsesCaseDirective() {
        assertEquals(
            "errors=0 Template(CaseDirective(" +
                "Tag(case,VariableName),Tag(when,NumberLiteral),Text,EndTag(endcase)))",
            parse("{% case x %}{% when 1 %}a{% endcase %}")
        )
    }

    @Test
    fun parsesAssignTag() {
        assertEquals(
            "errors=0 Template(Tag(assign," +
                "AssignmentExpression(VariableName,AssignOp,NumberLiteral)))",
            parse("{% assign n = 1 %}")
        )
    }

    @Test
    fun parsesCommentDirective() {
        assertEquals(
            "errors=0 Template(Comment(Tag(comment),CommentText,EndTag(endcomment)))",
            parse("{% comment %}hi{% endcomment %}")
        )
    }

    @Test
    fun parsesRawDirectiveWithoutInterpretingItsBody() {
        assertEquals(
            "errors=0 Template(RawDirective(Tag(raw),RawText,EndTag(endraw)))",
            parse("{% raw %}{{ x }}{% endraw %}")
        )
    }

    @Test
    fun parsesDirectivesInterleavedWithMarkup() {
        assertEquals(
            "errors=0 Template(Text,ForDirective(" +
                "Tag(for,VariableName,in,VariableName),Text,Interpolation(VariableName),Text," +
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
            "errors=0 Template[0,35](ForDirective[0,35](" +
                "Tag[0,16](for[3,6],VariableName[7,8],in[9,11],VariableName[12,13])," +
                "Interpolation[16,23](VariableName[19,20])," +
                "EndTag[23,35](endfor[26,32])))",
            parseWithPositions("{% for x in y %}{{ x }}{% endfor %}")
        )
    }

    @Test
    fun assignsUpstreamTokenPositionsForMemberExpressions() {
        assertEquals(
            "errors=0 Template[0,15](Interpolation[0,15](" +
                "MemberExpression[3,12](VariableName[3,7],PropertyName[8,12])))",
            parseWithPositions("{{ user.name }}")
        )
    }

    /**
     * `RenderParameter` is reduced on a `when` lookahead only inside a `{% liquid %}` tag's
     * `case` body. That is the one place the two corrupted `stateData` characters are
     * observable, and the corrupt table produces a wrong tree with **zero error nodes** — so
     * nothing but a tree-shape assertion can catch it.
     */
    @Test
    fun parsesRenderParameterBeforeWhenInsideLiquidTag() {
        assertEquals(
            "errors=0 Template(Tag(liquid,CaseDirective(" +
                "Tag(case,VariableName),Tag(when,NumberLiteral)," +
                "Tag(render,StringLiteral,RenderParameter(VariableName,NumberLiteral))," +
                "Tag(when,NumberLiteral),EndTag(endcase))))",
            parse("{% liquid case x\nwhen 1\nrender \"p\", a: 1\nwhen 2\nendcase %}")
        )
    }

    @Test
    fun parsesWithRenderParameterBeforeWhenInsideLiquidTag() {
        assertEquals(
            "errors=0 Template(Tag(liquid,CaseDirective(" +
                "Tag(case,VariableName),Tag(when,NumberLiteral)," +
                "Tag(render,StringLiteral,RenderParameter(with,VariableName))," +
                "Tag(when,NumberLiteral),EndTag(endcase))))",
            parse("{% liquid case x\nwhen 1\nrender \"p\" with y\nwhen 2\nendcase %}")
        )
    }

    @Test
    fun htmlMixedLanguageParsesTheSameLiquidStructure() {
        val tree = (liquidLanguage.parser as LRParser).parse(
            "<ul>{% for i in items %}<li>{{ i }}</li>{% endfor %}</ul>"
        )
        assertEquals(0, errorCount(tree))
        assertEquals(
            "Template(Text,ForDirective(" +
                "Tag(for,VariableName,in,VariableName),Text,Interpolation(VariableName),Text," +
                "EndTag(endfor)),Text)",
            treeToString(tree)
        )
    }
}
