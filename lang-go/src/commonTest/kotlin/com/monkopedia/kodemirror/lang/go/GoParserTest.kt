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

package com.monkopedia.kodemirror.lang.go

import kotlin.test.Test
import kotlin.test.assertEquals

class GoParserTest {

    private fun parse(input: String): String = treeToString(parser.parse(input))

    // ===== source_files.txt =====

    @Test
    fun parsesPackageClause() = assertEquals(
        "SourceFile(PackageClause(package,DefName))",
        parse("package main")
    )

    @Test
    fun parsesSingleImportDeclarations() = assertEquals(
        "SourceFile(" +
            "ImportDecl(import,ImportSpec(String))," +
            "ImportDecl(import,ImportSpec(String))," +
            "ImportDecl(import,ImportSpec(DefName,String))," +
            "ImportDecl(import,ImportSpec(DefName,String)))",
        parse(
            "import \"net/http\"\n" +
                "import . \"some/dsl\"\n" +
                "import _ \"os\"\n" +
                "import alias \"some/package\""
        )
    )

    @Test
    fun parsesGroupedImportDeclarations() = assertEquals(
        "SourceFile(" +
            "ImportDecl(import,SpecList)," +
            "ImportDecl(import,SpecList(ImportSpec(String)))," +
            "ImportDecl(import,SpecList(" +
            "ImportSpec(String)," +
            "ImportSpec(String)," +
            "ImportSpec(DefName,String)," +
            "ImportSpec(DefName,String))))",
        parse(
            "import()\nimport (\"fmt\")\nimport (\n" +
                "  \"net/http\"\n  . \"some/dsl\"\n  _ \"os\"\n  alias \"some/package\"\n)"
        )
    )

    @Test
    fun parsesBlockComments() = assertEquals(
        "SourceFile(BlockComment)",
        parse("/*\n * This is a great package\n */")
    )

    @Test
    fun parsesCommentsWithAsterisks() = assertEquals(
        "SourceFile(" +
            "BlockComment,ConstDecl(const,ConstSpec(DefName))," +
            "BlockComment,ConstDecl(const,ConstSpec(DefName))," +
            "BlockComment,ConstDecl(const,ConstSpec(DefName))," +
            "BlockComment,ConstDecl(const,ConstSpec(DefName)))",
        parse(
            "/* a */\nconst a\n\n/* b **/\nconst b\n\n/* c ***/\nconst c\n\n/* d\n\n***/\nconst d"
        )
    )

    // ===== declarations.txt =====

    @Test
    fun parsesSingleConstDeclarationsWithoutTypes() = assertEquals(
        "SourceFile(" +
            "ConstDecl(const,ConstSpec(DefName,Number))," +
            "ConstDecl(const,ConstSpec(DefName,DefName,Number,Number))," +
            "ConstDecl(const,ConstSpec(DefName,DefName,DefName,Number,Number,Number)))",
        parse("const zero = 0\nconst one, two = 1, 2\nconst three, four, five = 3, 4, 5")
    )

    @Test
    fun parsesSingleConstDeclarationsWithTypes() = assertEquals(
        "SourceFile(" +
            "ConstDecl(const,ConstSpec(DefName,TypeName,Number))," +
            "ConstDecl(const,ConstSpec(DefName,DefName,TypeName,Number,Number)))",
        parse("const zero int = 0\nconst one, two uint64 = 1, 2")
    )

    @Test
    fun parsesGroupedConstDeclarations() = assertEquals(
        "SourceFile(ConstDecl(const,SpecList(ConstSpec(DefName,Number),ConstSpec(DefName,Number))))",
        parse("const (\n  zero = 0\n  one = 1\n)")
    )

    @Test
    fun parsesFunctionDeclarations() = assertEquals(
        "SourceFile(" +
            "FunctionDecl(func,DefName,Parameters,Block)," +
            "FunctionDecl(func,DefName,Parameters(" +
            "Parameter(DefName,TypeName)," +
            "Parameter(DefName,DefName,DefName,TypeName)" +
            "),TypeName,Block))",
        parse("func f1() {}\nfunc f2(a File, b, c, d Thing) int {}")
    )

    @Test
    fun parsesVariadicFunctionDeclarations() = assertEquals(
        "SourceFile(" +
            "FunctionDecl(func,DefName,Parameters(" +
            "Parameter(DefName,PointerType(TypeName))" +
            "),Block)," +
            "FunctionDecl(func,DefName,Parameters(" +
            "Parameter(DefName,TypeName)," +
            "Parameter(DefName,TypeName)" +
            "),Block))",
        parse("func f1(a ...*int) {}\nfunc f2(a int, b ...int) {}")
    )

    @Test
    fun parsesMethodDeclarations() = assertEquals(
        "SourceFile(" +
            "MethodDecl(func,Parameters(Parameter(DefName,TypeName)),FieldName," +
            "Parameters(Parameter(DefName,TypeName)),TypeName,Block))",
        parse("func (self Person) Equals(other Person) bool {}")
    )

    @Test
    fun parsesTypeDeclarations() = assertEquals(
        "SourceFile(" +
            "TypeDecl(type,TypeSpec(DefName,TypeName))," +
            "TypeDecl(type,SpecList(TypeSpec(DefName,TypeName),TypeSpec(DefName,TypeName))))",
        parse("type a b\ntype (\n  a b\n  c d\n)")
    )

    @Test
    fun parsesVarDeclarations() = assertEquals(
        "SourceFile(" +
            "VarDecl(var,VarSpec(DefName,Number))," +
            "VarDecl(var,VarSpec(DefName,DefName,Number,Number)))",
        parse("var zero = 0\nvar one, two = 1, 2")
    )

    // ===== expressions.txt =====

    @Test
    fun parsesCallExpressions() = assertEquals(
        "SourceFile(FunctionDecl(func,DefName,Parameters,Block(" +
            "ExprStatement(CallExpr(VariableName,Arguments(VariableName,VariableName))))))",
        parse("func main() {\n  a(b, c...)\n}")
    )

    @Test
    fun parsesNestedCallExpressions() = assertEquals(
        "SourceFile(ExprStatement(CallExpr(VariableName,Arguments(" +
            "CallExpr(VariableName,Arguments(CallExpr(VariableName,Arguments(VariableName))))))))",
        parse("a(b(c(d)))")
    )

    @Test
    fun parsesSelectorExpressions() = assertEquals(
        "SourceFile(ExprStatement(CallExpr(SelectorExpr(SelectorExpr(VariableName,FieldName),FieldName),Arguments)))",
        parse("a.b.c()")
    )

    @Test
    fun parsesIndexingExpressions() = assertEquals(
        "SourceFile(" +
            "Assignment(VariableName,IndexExpr(VariableName,Number))," +
            "Assignment(VariableName,SliceExpr(VariableName))," +
            "Assignment(VariableName,SliceExpr(VariableName,Number)))",
        parse("_ = a[1]\n_ = b[:]\n_ = c[1:]")
    )

    @Test
    fun parsesUnaryExpressions() = assertEquals(
        "SourceFile(FunctionDecl(func,DefName,Parameters,Block(" +
            "Assignment(VariableName,UnaryExp(LogicOp,UnaryExp(VariableName)))," +
            "Assignment(VariableName,CallExpr(UnaryExp(DerefOp,VariableName),Arguments)))))",
        parse("func main() {\n  _ = !<-a\n  _ = *foo()\n}")
    )

    // ===== statements.txt =====

    @Test
    fun parsesDeclarationStatements() = assertEquals(
        "SourceFile(" +
            "PackageClause(package,DefName)," +
            "FunctionDecl(func,DefName,Parameters,Block(" +
            "VarDecl(var,VarSpec(DefName,VariableName))," +
            "ConstDecl(const,ConstSpec(DefName,Number)))))",
        parse("package main\n\nfunc main() {\n  var x = y\n  const x = 5\n}")
    )

    @Test
    fun parsesExpressionStatements() = assertEquals(
        "SourceFile(FunctionDecl(func,DefName,Parameters,Block(" +
            "ExprStatement(CallExpr(VariableName,Arguments(Number))))))",
        parse("func main() {\n  foo(5)\n}")
    )

    @Test
    fun parsesSendStatements() = assertEquals(
        "SourceFile(FunctionDecl(func,DefName,Parameters,Block(" +
            "SendStatement(VariableName,Number))))",
        parse("func main() {\n  foo <- 5\n}")
    )

    @Test
    fun parsesIncDecStatements() = assertEquals(
        "SourceFile(FunctionDecl(func,DefName,Parameters,Block(" +
            "IncDecStatement(VariableName,IncDecOp)," +
            "IncDecStatement(VariableName,IncDecOp))))",
        parse("func main() {\n  i++\n  j--\n}")
    )

    @Test
    fun parsesAssignmentStatements() = assertEquals(
        "SourceFile(FunctionDecl(func,DefName,Parameters,Block(" +
            "Assignment(VariableName,Number)," +
            "Assignment(VariableName,VariableName,UpdateOp,Number,Number)," +
            "Assignment(VariableName,UpdateOp,Number))))",
        parse("func main() {\n  a = 1\n  b, c += 2, 3\n  d *= 3\n}")
    )

    @Test
    fun parsesShortVarDeclarations() = assertEquals(
        "SourceFile(FunctionDecl(func,DefName,Parameters,Block(" +
            "VarDecl(DefName,DefName,Number,Number))))",
        parse("func main() {\n  a, b := 1, 2\n}")
    )

    @Test
    fun parsesIfStatements() = assertEquals(
        "SourceFile(FunctionDecl(func,DefName,Parameters,Block(" +
            "IfStatement(if,VariableName,Block(" +
            "ExprStatement(CallExpr(VariableName,Arguments)))))))",
        parse("func main() {\n  if a {\n    b()\n  }\n}")
    )

    @Test
    fun parsesForStatements() = assertEquals(
        "SourceFile(FunctionDecl(func,DefName,Parameters,Block(" +
            "ForStatement(for,Block(ExprStatement(CallExpr(VariableName,Arguments))," +
            "GotoStatement(goto,LabelName))))))",
        parse("func main() {\n  for {\n    a()\n    goto loop\n  }\n}")
    )

    @Test
    fun parsesSwitchStatements() = assertEquals(
        "SourceFile(FunctionDecl(func,DefName,Parameters,Block(" +
            "SwitchStatement(switch,VariableName,SwitchBlock(" +
            "Case(case,Number,Number)," +
            "ExprStatement(CallExpr(VariableName,Arguments))," +
            "FallthroughStatement(fallthrough)," +
            "Case(case,Number)," +
            "ExprStatement(CallExpr(VariableName,Arguments))," +
            "Case(default)," +
            "ExprStatement(CallExpr(VariableName,Arguments))," +
            "GotoStatement(break))))))",
        parse(
            "func main() {\n  switch e {\n    case 1, 2:\n      a()\n      fallthrough\n" +
                "    case 3:\n      d()\n    default:\n      c()\n      break\n  }\n}"
        )
    )

    @Test
    fun parsesGoAndDeferStatements() = assertEquals(
        "SourceFile(FunctionDecl(func,DefName,Parameters,Block(" +
            "DeferStatement(defer,CallExpr(SelectorExpr(VariableName,FieldName),Arguments))," +
            "GoStatement(go,CallExpr(SelectorExpr(VariableName,FieldName),Arguments)))))",
        parse("func main() {\n  defer x.y()\n  go x.y()\n}")
    )

    @Test
    fun parsesReturnStatements() = assertEquals(
        "SourceFile(FunctionDecl(func,DefName,Parameters,Block(" +
            "SwitchStatement(switch,SwitchBlock(Case(case,Bool),ReturnStatement(return))))))",
        parse("func main() {\n  switch {\n    case true:\n      return\n  }\n}")
    )

    @Test
    fun parsesSelectStatements() = assertEquals(
        "SourceFile(FunctionDecl(func,DefName,Parameters,Block(" +
            "SelectStatement(select,SelectBlock(" +
            "Case(case,ReceiveStatement(DefName,UnaryExp(VariableName)))," +
            "ExprStatement(CallExpr(VariableName,Arguments(VariableName)))," +
            "Case(case,SendStatement(VariableName,VariableName))," +
            "ExprStatement(CallExpr(VariableName,Arguments(Number)))," +
            "Case(default)," +
            "ReturnStatement(return))))))",
        parse(
            "func main() {\n  select {\n    case x := <-c:\n      println(x)\n" +
                "    case y <- c:\n      println(5)\n" +
                "    default:\n      return\n  }\n}"
        )
    )

    @Test
    fun parsesTopLevelStatements() = assertEquals(
        "SourceFile(" +
            "ExprStatement(CallExpr(VariableName,Arguments(Number)))," +
            "VarDecl(DefName,TypedLiteral(TypeName,LiteralValue(Element(Key(VariableName),VariableName)))))",
        parse("foo(5)\nx := T { a: b }")
    )

    // ===== types.txt =====

    @Test
    fun parsesQualifiedTypeNames() = assertEquals(
        "SourceFile(TypeDecl(type,TypeSpec(DefName,QualifiedType(VariableName,TypeName))))",
        parse("type a b.c")
    )

    @Test
    fun parsesArrayTypes() = assertEquals(
        "SourceFile(TypeDecl(type,TypeSpec(DefName,ArrayType(BinaryExp(Number,ArithOp,Number),TypeName))))",
        parse("type a [2+2]c")
    )

    @Test
    fun parsesSliceTypes() = assertEquals(
        "SourceFile(" +
            "TypeDecl(type,TypeSpec(DefName,SliceType(TypeName)))," +
            "TypeDecl(type,TypeSpec(DefName,SliceType(SliceType(TypeName)))))",
        parse("type a []c\ntype b [][]d")
    )

    @Test
    fun parsesStructTypes() = assertEquals(
        "SourceFile(" +
            "TypeDecl(type,TypeSpec(DefName,StructType(struct,StructBody)))," +
            "TypeDecl(type,TypeSpec(DefName,StructType(struct,StructBody(FieldDecl(TypeName))))))",
        parse("type s1 struct {}\n\ntype s2 struct { Person }")
    )

    @Test
    fun parsesInterfaceTypes() = assertEquals(
        "SourceFile(" +
            "TypeDecl(type,TypeSpec(DefName,InterfaceType(interface,InterfaceBody)))," +
            "TypeDecl(type,TypeSpec(DefName,InterfaceType(interface,InterfaceBody(" +
            "QualifiedType(VariableName,TypeName))))))",
        parse("type i1 interface {}\n\ntype i1 interface { io.Reader }")
    )

    @Test
    fun parsesMapTypes() = assertEquals(
        "SourceFile(TypeDecl(type,TypeSpec(DefName,MapType(map,TypeName,TypeName))))",
        parse("type m1 map[string]error")
    )

    @Test
    fun parsesPointerTypes() = assertEquals(
        "SourceFile(TypeDecl(type,SpecList(" +
            "TypeSpec(DefName,PointerType(TypeName))," +
            "TypeSpec(DefName,PointerType(PointerType(TypeName))))))",
        parse("type (\n  p1 *string\n  p2 **p1\n)")
    )

    @Test
    fun parsesChannelTypes() = assertEquals(
        "SourceFile(TypeDecl(type,SpecList(" +
            "TypeSpec(DefName,ChannelType(chan,ChannelType(chan,TypeName)))," +
            "TypeSpec(DefName,ChannelType(chan,ChannelType(chan,StructType(struct,StructBody))))," +
            "TypeSpec(DefName,ChannelType(chan,ChannelType(chan,TypeName))))))",
        parse(
            "type (\n  c1 chan<- chan int\n  c2 chan<- chan<- struct{}\n  c3 chan<- <-chan int\n)"
        )
    )

    // ===== literals.txt =====

    @Test
    fun parsesIntLiterals() = assertEquals(
        "SourceFile(ConstDecl(const,SpecList(" +
            "ConstSpec(DefName,Number)," +
            "ConstSpec(DefName,Number)," +
            "ConstSpec(DefName,Number))))",
        parse("const (\n  i1 = 42\n  i2 = 4_2\n  i3 = 0600\n)")
    )

    @Test
    fun parsesRuneLiterals() = assertEquals(
        "SourceFile(ConstDecl(const,SpecList(" +
            "ConstSpec(DefName,Rune)," +
            "ConstSpec(DefName,Rune)," +
            "ConstSpec(DefName,Rune))))",
        parse("const (\n  a = '0'\n  b = '\\''\n  c = '\\\\'\n)")
    )

    @Test
    fun parsesStringLiterals() = assertEquals(
        "SourceFile(ConstDecl(const,SpecList(" +
            "ConstSpec(DefName,String)," +
            "ConstSpec(DefName,String)," +
            "ConstSpec(DefName,String))))",
        parse("const (\n  a = \"0\"\n  b = \"`\\\"`\"\n  c = \"\\x0c\"\n)")
    )

    @Test
    fun parsesSliceLiterals() = assertEquals(
        "SourceFile(" +
            "ConstDecl(const,ConstSpec(DefName,TypedLiteral(SliceType(TypeName),LiteralValue)))," +
            "ConstDecl(const,ConstSpec(DefName,TypedLiteral(SliceType(TypeName),LiteralValue(Element(String))))))",
        parse("const s1 = []string{}\n\nconst s2 = []string{\"hi\"}")
    )

    @Test
    fun parsesMapLiterals() = assertEquals(
        "SourceFile(ConstDecl(const,ConstSpec(DefName,TypedLiteral(" +
            "MapType(map,TypeName,TypeName)," +
            "LiteralValue(Element(Key(String),String),Element(Key(String),String))))))",
        parse(
            "const s = map[string]string{\n  \"hi\": \"hello\",\n  \"bye\": \"goodbye\",\n}"
        )
    )

    @Test
    fun parsesStructLiterals() = assertEquals(
        "SourceFile(ConstDecl(const,ConstSpec(DefName,TypedLiteral(" +
            "TypeName," +
            "LiteralValue(Element(Key(VariableName),String),Element(Key(VariableName),String))))))",
        parse("const s1 = Person{\n  name: \"Frank\",\n  Age: \"5 months\",\n}")
    )

    @Test
    fun parsesFunctionLiterals() = assertEquals(
        "SourceFile(ConstDecl(const,ConstSpec(DefName,FunctionLiteral(" +
            "func,Parameters(Parameter(DefName,TypeName)),Parameters(Parameter(TypeName),Parameter(TypeName))," +
            "Block(ReturnStatement(return,Number,Number))))))",
        parse("const s1 = func(s string) (int, int) {\n  return 1, 2\n}")
    )
}
