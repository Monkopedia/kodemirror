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
@file:Suppress("ktlint:standard:max-line-length")

package com.monkopedia.kodemirror.lezer.python

import kotlin.test.Test
import kotlin.test.assertEquals

class PythonParserTest {
    private fun parse(input: String): String = treeToString(parser.parse(input))

    @Test
    fun testOperatorPrecedence() = assertEquals(
        "Script(ExpressionStatement(BinaryExpression(BinaryExpression(VariableName,ArithOp," +
            "BinaryExpression(VariableName,ArithOp,VariableName)),ArithOp," +
            "BinaryExpression(VariableName,ArithOp,Number)))," +
            "ExpressionStatement(BinaryExpression(BinaryExpression(VariableName,or," +
            "BinaryExpression(BinaryExpression(VariableName,CompareOp,Number),and,VariableName))," +
            "or,BinaryExpression(VariableName,CompareOp,None)))," +
            "ExpressionStatement(BinaryExpression(BinaryExpression(VariableName,ArithOp,VariableName),BitOp," +
            "BinaryExpression(VariableName,BitOp,VariableName))))",
        parse("a + b * c - d**3\na or b > 2 and c or d == None\na + b | c & d")
    )

    @Test
    fun testStrings() = assertEquals(
        "Script(ExpressionStatement(ContinuedString(String,String))," +
            "ExpressionStatement(String)," +
            "ExpressionStatement(String)," +
            "ExpressionStatement(String))",
        parse(
            "'foo' \"bar\"\nb'baz'\n'''long string\non two lines'''\n\n\"\"\"also with double\n\nquotes\"\"\""
        )
    )

    @Test
    fun testRawString() = assertEquals(
        "Script(ExpressionStatement(BinaryExpression(String,ArithOp,String)))",
        parse("r\"foo\\\"\" + r'\\\\'")
    )

    // TODO: Parser produces a minor error node in format replacement with nested quotes
    @Test
    fun testNestedQuoteTypes() = assertEquals(
        "Script(ExpressionStatement(FormatString(FormatReplacement(String),\u26A0)))",
        parse("f\"a{'b'}c\"")
    )

    @Test
    fun testLambda() = assertEquals(
        "Script(ExpressionStatement(CallExpression(MemberExpression(VariableName,PropertyName),ArgList(" +
            "LambdaExpression(lambda,ParamList(VariableName),BinaryExpression(VariableName,ArithOp,Number)))))," +
            "AssignStatement(VariableName,AssignOp,LambdaExpression(lambda,ParamList(VariableName,VariableName,AssignOp,Number)," +
            "BinaryExpression(VariableName,BitOp,VariableName))))",
        parse("something.map(lambda x: x + 1)\nfoo = lambda a, b = 0: a ^ b")
    )

    @Test
    fun testMemberExpressions() = assertEquals(
        "Script(ExpressionStatement(MemberExpression(VariableName,Number))," +
            "ExpressionStatement(MemberExpression(VariableName,PropertyName))," +
            "ExpressionStatement(MemberExpression(MemberExpression(VariableName,PropertyName),PropertyName)))",
        parse("x[1]\nx.foo\nx.if.True")
    )

    @Test
    fun testCallExpressions() = assertEquals(
        "Script(ExpressionStatement(BinaryExpression(" +
            "CallExpression(VariableName,ArgList(VariableName,VariableName,VariableName))," +
            "ArithOp," +
            "CallExpression(VariableName,ArgList(VariableName,AssignOp,Number)))))",
        parse("foo(x, y, **z) + bar(blah=20)")
    )

    @Test
    fun testCollectionExpressions() = assertEquals(
        "Script(ExpressionStatement(ArrayExpression(Boolean,Boolean,None))," +
            "ExpressionStatement(DictionaryExpression(VariableName,Number,VariableName,Boolean,VariableName))," +
            "ExpressionStatement(SetExpression(Number,Number,Number))," +
            "ExpressionStatement(ParenthesizedExpression(Number))," +
            "ExpressionStatement(TupleExpression(Number))," +
            "ExpressionStatement(TupleExpression(Number,Number)))",
        parse("[True, False, None]\n{foo: 22, bar: False, **other}\n{1, 2, 3}\n(3)\n(3,)\n(3, 4)")
    )

    @Test
    fun testUnaryExpressions() = assertEquals(
        "Script(ExpressionStatement(ArrayExpression(" +
            "UnaryExpression(ArithOp,Number)," +
            "BinaryExpression(UnaryExpression(ArithOp,Number),ArithOp,Number)," +
            "UnaryExpression(BitOp,BinaryExpression(Number,ArithOp,Number)))))",
        parse("[-1, +2 * 3, ~2**2]")
    )

    @Test
    fun testAwait() = assertEquals(
        "Script(ExpressionStatement(AwaitExpression(await,CallExpression(VariableName,ArgList))))",
        parse("await something()")
    )

    @Test
    fun testFunctionDefinition() = assertEquals(
        "Script(FunctionDefinition(def,VariableName,ParamList,Body(PassStatement(pass)))," +
            "FunctionDefinition(def,VariableName," +
            "ParamList(VariableName,TypeDef(VariableName),VariableName,AssignOp,Number,VariableName)," +
            "TypeDef(VariableName),Body(PassStatement(pass))))",
        parse("def foo():\n  pass\ndef bar(a: str, b = 22, **c) -> num:\n  pass")
    )

    @Test
    fun testSingleLineFunctionDefinition() = assertEquals(
        "Script(FunctionDefinition(def,VariableName,ParamList(VariableName,VariableName)," +
            "Body(ReturnStatement(return,BinaryExpression(VariableName,ArithOp,VariableName)))))",
        parse("def foo(a, b): return a + b")
    )

    @Test
    fun testConditional() = assertEquals(
        "Script(IfStatement(if,VariableName,Body(ExpressionStatement(CallExpression(VariableName,ArgList))))," +
            "IfStatement(if,BinaryExpression(Number,ArithOp,Number),Body(PassStatement(pass))," +
            "elif,BinaryExpression(Number,CompareOp,Number),Body(PassStatement(pass))," +
            "else,Body(PassStatement(pass))))",
        parse("if a: b()\n\nif 1 + 3:\n  pass\nelif 55 < 2:\n  pass\nelse:\n  pass")
    )

    @Test
    fun testAssignment() = assertEquals(
        "Script(AssignStatement(VariableName,AssignOp,Number)," +
            "AssignStatement(VariableName,TypeDef(VariableName),AssignOp,String)," +
            "AssignStatement(VariableName,VariableName,VariableName,AssignOp,None)," +
            "AssignStatement(VariableName,AssignOp,VariableName,AssignOp,Boolean)," +
            "UpdateStatement(VariableName,UpdateOp,Number))",
        parse("a = 4\nb: str = \"hi\"\nc, d, e = None\nf = g = False\nh += 1")
    )

    @Test
    fun testForLoops() = assertEquals(
        "Script(ForStatement(for,VariableName,VariableName,in,CallExpression(VariableName,ArgList)," +
            "Body(ExpressionStatement(CallExpression(VariableName,ArgList(VariableName,VariableName))))))",
        parse("for a, b in woop():\n  doStuff(b, a)")
    )

    @Test
    fun testWithStatements() = assertEquals(
        "Script(WithStatement(with,CallExpression(VariableName,ArgList(String)),as,VariableName," +
            "Body(PassStatement(pass)))," +
            "WithStatement(async,with,VariableName,as,VariableName,Body(PassStatement(pass))))",
        parse("with open(\"x\") as file:\n  pass\nasync with foo as bar:\n  pass")
    )

    @Test
    fun testClassDefinition() = assertEquals(
        "Script(ClassDefinition(class,VariableName,Body(" +
            "AssignStatement(VariableName,AssignOp,Number)," +
            "FunctionDefinition(def,VariableName,ParamList(VariableName),Body(PassStatement(pass)))," +
            "FunctionDefinition(def,VariableName,ParamList(VariableName),Body(" +
            "UpdateStatement(MemberExpression(VariableName,PropertyName),UpdateOp,Number)))))," +
            "ClassDefinition(class,VariableName,ArgList(VariableName),Body(PassStatement(pass))))",
        parse(
            "class Foo:\n  prop = 0\n  def __init__(self):\n    pass\n  def plus(self):\n" +
                "    self.prop += 1\n\nclass Bar(Foo): pass"
        )
    )

    @Test
    fun testScopeStatements() = assertEquals(
        "Script(ScopeStatement(global,VariableName)," +
            "ScopeStatement(nonlocal,VariableName,VariableName))",
        parse("global a\nnonlocal b, c")
    )

    @Test
    fun testImportStatements() = assertEquals(
        "Script(ImportStatement(import,VariableName)," +
            "ImportStatement(from,VariableName,VariableName,import,VariableName,VariableName))",
        parse("import datetime\nfrom something.other import one, two")
    )

    @Test
    fun testSmallStatements() = assertEquals(
        "Script(FunctionDefinition(def,VariableName,ParamList,Body(ReturnStatement(return,Number)))," +
            "RaiseStatement(raise,CallExpression(VariableName,ArgList(String)))," +
            "WhileStatement(while,Boolean,Body(BreakStatement(break),ContinueStatement(continue)))," +
            "AssertStatement(assert,BinaryExpression(Number,CompareOp,Number))," +
            "DeleteStatement(del,MemberExpression(VariableName,Number)))",
        parse(
            "def x(): return 5\nraise Exception(\"woop\")\nwhile False:\n  break\n  continue\n" +
                "assert 1 == 2\ndel x[2]"
        )
    )

    @Test
    fun testOneLineSmallStatements() = assertEquals(
        "Script(StatementGroup(ExpressionStatement(VariableName)," +
            "ExpressionStatement(CallExpression(VariableName,ArgList))," +
            "AssignStatement(VariableName,AssignOp,Number))," +
            "RaiseStatement(raise,String))",
        parse("x; y(); z = 2\nraise \"oh\"")
    )

    @Test
    fun testScriptEndingInComment() = assertEquals(
        "Script(AssignStatement(VariableName,AssignOp,Number),Comment)",
        parse("x = 1\n\n# End")
    )

    @Test
    fun testSelfNotReserved() = assertEquals(
        "Script(AssignStatement(VariableName,AssignOp,Boolean))",
        parse("self = True")
    )

    @Test
    fun testDecorators() = assertEquals(
        "Script(DecoratedStatement(Decorator(At,VariableName,VariableName)," +
            "FunctionDefinition(def,VariableName,ParamList,Body(PassStatement(pass))))," +
            "DecoratedStatement(Decorator(At,VariableName,ArgList(VariableName,VariableName))," +
            "ClassDefinition(class,VariableName,Body(PassStatement(pass)))))",
        parse("@Something.X\ndef f(): pass\n\n@Other(arg1, arg2)\nclass C: pass")
    )

    @Test
    fun testPrintStatement() = assertEquals(
        "Script(PrintStatement(print,String)," +
            "ExpressionStatement(CallExpression(VariableName,ArgList(MemberExpression(VariableName,PropertyName)))))",
        parse("print \"hi\"\nprint(print.something)")
    )
}
