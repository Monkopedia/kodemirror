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

package com.monkopedia.kodemirror.lang.grammar

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tree-shape tests for the Lezer-grammar parser.
 *
 * Every expectation here was taken from upstream `@lezer/lezer` 1.1.1 parsing the same input,
 * rendered through the same [treeToString]. Asserting the shape rather than "did not throw"
 * is deliberate: a parser whose tables are damaged can still return a tree.
 */
class LezerGrammarParserTest {
    private fun parse(input: String): String = treeToString(lezerParser.parse(input))

    private fun errors(input: String): Int = errorCount(lezerParser.parse(input))

    @Test
    fun parsesSimpleRules() {
        val src = "@top Program { statement* }\nstatement { Expr \";\" }\n"
        assertEquals(
            "Grammar(RuleDeclaration(RuleName,Body(Repeat(RuleName)))," +
                "RuleDeclaration(RuleName,Body(Sequence(RuleName,Literal))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    /**
     * The `stateData` regression from #367: a single `l` was missing at index 1177, and
     * `@skip` scopes crashed with `ArrayIndexOutOfBoundsException`.
     */
    @Test
    fun parsesSkipScope() {
        val src = "@skip { spaces | newline } {\n  Block { \"{\" statement* \"}\" }\n}\n"
        assertEquals(
            "Grammar(SkipScope(Body(Choice(RuleName,RuleName))," +
                "SkipBody(RuleDeclaration(RuleName,Body(Sequence(Literal,Repeat(RuleName),Literal))))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    /** The other input the missing `stateData` character broke: node props. */
    @Test
    fun parsesNodeProps() {
        val src = "@top Program { Body }\nBody[group=Statement, isolate] { \"x\" }\n"
        assertEquals(
            "Grammar(RuleDeclaration(RuleName,Body(RuleName))," +
                "RuleDeclaration(RuleName,Props(Prop(Name,Name),Prop(Name)),Body(Literal)))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesLocalTokens() {
        val src = "@local tokens {\n  interpolationStart[@name=Interpolation] { \"\\\${\" }\n  stringEnd { '\"' }\n  @else stringContent\n}\n"
        assertEquals(
            "Grammar(LocalTokensDeclaration(tokens,TokensBody(" +
                "RuleDeclaration(RuleName,Props(Prop(AtName,Name)),Body(Literal))," +
                "RuleDeclaration(RuleName,Body(Literal)),ElseToken(RuleName))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesPrecedenceDeclarationAndMarkers() {
        val src = "@precedence { times @left, plus @left }\nExpr { Expr !times \"*\" Expr | Expr !plus \"+\" Expr }\n"
        assertEquals(
            "Grammar(PrecedenceDeclaration(PrecedenceBody(Precedence(PrecedenceName)," +
                "Precedence(PrecedenceName)))," +
                "RuleDeclaration(RuleName,Body(Choice(" +
                "Sequence(RuleName,PrecedenceMarker(PrecedenceName),Literal,RuleName)," +
                "Sequence(RuleName,PrecedenceMarker(PrecedenceName),Literal,RuleName)))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesTokensDeclaration() {
        val src = "@tokens {\n  Identifier { \$[a-zA-Z_] \$[a-zA-Z_0-9]* }\n  String { '\"' (![\\\\\\n\"] | \"\\\\\\\\\" _)* '\"' }\n  space { \$[ \\t\\n\\r]+ }\n}\n"
        assertEquals(
            "Grammar(TokensDeclaration(TokensBody(" +
                "RuleDeclaration(RuleName,Body(Sequence(CharSet,Repeat(CharSet))))," +
                "RuleDeclaration(RuleName,Body(Sequence(Literal,Repeat(ParenExpression(" +
                "Choice(InvertedCharSet,Sequence(Literal,AnyChar)))),Literal)))," +
                "RuleDeclaration(RuleName,Body(Repeat1(CharSet))))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesExternalDeclarations() {
        val src = "@external tokens insertSemi from \"./tokens\" { insertedSemicolon }\n@external prop myProp from \"./props\"\n"
        assertEquals(
            "Grammar(ExternalTokensDeclaration(tokens,Name,from,Literal," +
                "TokensBody(Token(RuleName)))," +
                "ExternalPropDeclaration(prop,Name,from,Literal))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesSpecializeAndExtend() {
        val src = "@top Program { Kw* }\nKw { @specialize<Identifier, \"if\" | \"else\"> | @extend<Identifier, \"get\"> }\n"
        assertEquals(
            "Grammar(RuleDeclaration(RuleName,Body(Repeat(RuleName)))," +
                "RuleDeclaration(RuleName,Body(Choice(" +
                "Specialization(ArgList(RuleName,Choice(Literal,Literal)))," +
                "Specialization(ArgList(RuleName,Literal))))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesDialects() {
        val src = "@dialects { jsx, ts }\n@top Program { Thing* }\nThing { JsxTag }\n"
        assertEquals(
            "Grammar(DialectsDeclaration(DialectBody(Name,Name))," +
                "RuleDeclaration(RuleName,Body(Repeat(RuleName)))," +
                "RuleDeclaration(RuleName,Body(RuleName)))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }

    @Test
    fun parsesCharacterClasses() {
        val src = "@tokens {\n  Word { @asciiLetter+ }\n  Digits { @digit+ }\n  Any { ![\\n] }\n}\n"
        assertEquals(
            "Grammar(TokensDeclaration(TokensBody(" +
                "RuleDeclaration(RuleName,Body(Repeat1(CharClass)))," +
                "RuleDeclaration(RuleName,Body(Repeat1(CharClass)))," +
                "RuleDeclaration(RuleName,Body(InvertedCharSet)))))",
            parse(src)
        )
        assertEquals(0, errors(src))
    }
}
