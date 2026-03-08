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

package com.monkopedia.kodemirror.lang.rust

import kotlin.test.Test
import kotlin.test.assertEquals

class RustParserTest {

    private fun parse(input: String): String = treeToString(rustParser.parse(input))

    // === async.txt ===

    @Test
    fun parsesAsyncFunction() = assertEquals(
        "SourceFile(" +
            "FunctionItem(async,fn,BoundIdentifier,ParamList,Block)," +
            "FunctionItem(async,fn,BoundIdentifier,ParamList," +
            "Block(LetDeclaration(let,BoundIdentifier," +
            "TryExpression(AwaitExpression(Identifier,await))))))",
        parse("async fn abc() {}\n\nasync fn main() {\n    let x = futures.await?;\n}")
    )

    @Test
    fun parsesAwaitExpression() = assertEquals(
        "SourceFile(" +
            "ExpressionStatement(AwaitExpression(Identifier,await))," +
            "ExpressionStatement(TryExpression(AwaitExpression(Identifier,await))))",
        parse("futures.await;\nfutures.await?;")
    )

    @Test
    fun parsesAsyncBlock() = assertEquals(
        "SourceFile(" +
            "ExpressionStatement(AsyncBlock(async,Block))," +
            "ExpressionStatement(AsyncBlock(async,Block(LetDeclaration(let,BoundIdentifier,Integer))))," +
            "ExpressionStatement(AsyncBlock(async,move,Block)))",
        parse("async {}\nasync { let x = 10; }\nasync move {}")
    )

    // === comments.txt ===

    @Test
    fun parsesBlockComments() = assertEquals(
        "SourceFile(BlockComment,BlockComment)",
        parse("/*\n * Block comments\n */\n\n/* Comment with asterisks **/")
    )

    @Test
    fun parsesNestedBlockComments() = assertEquals(
        "SourceFile(BlockComment(BlockComment),LineComment," +
            "BlockComment(BlockComment(BlockComment)),LineComment," +
            "BlockComment(BlockComment),LineComment)",
        parse(
            "/* /* double nested */ */\n\n// ---\n\n" +
                "/*/*/* triple nested */*/*/\n\n// ---\n\n" +
                "/****\n  /****\n    nested with extra stars\n  ****/\n****/\n\n// ---"
        )
    )

    @Test
    fun parsesLineComments() = assertEquals(
        "SourceFile(LineComment)",
        parse("// Comment")
    )

    // === declarations.txt ===

    @Test
    fun parsesModules() = assertEquals(
        "SourceFile(" +
            "ModItem(mod,BoundIdentifier)," +
            "ModItem(mod,BoundIdentifier,DeclarationList)," +
            "ModItem(mod,BoundIdentifier,DeclarationList(" +
            "ModItem(mod,BoundIdentifier,DeclarationList)," +
            "ModItem(mod,BoundIdentifier,DeclarationList)))," +
            "ModItem(Vis(pub),mod,BoundIdentifier))",
        parse(
            "mod english;\n\nmod english {}\n\nmod english {\n" +
                "    mod greetings {}\n    mod farewells {}\n}\n\npub mod english;"
        )
    )

    @Test
    fun parsesFunctionDeclarations() = assertEquals(
        "SourceFile(FunctionItem(fn,BoundIdentifier,ParamList,Block))",
        parse("fn main() {}")
    )

    @Test
    fun parsesStructs() = assertEquals(
        "SourceFile(" +
            "StructItem(struct,TypeIdentifier)," +
            "StructItem(struct,TypeIdentifier,FieldDeclarationList))",
        parse("struct Proton;\nstruct Electron {}")
    )

    @Test
    fun parsesEnums() = assertEquals(
        "SourceFile(EnumItem(Vis(pub),enum,TypeIdentifier," +
            "TypeParamList(TypeIdentifier),EnumVariantList(" +
            "EnumVariant(Identifier)," +
            "EnumVariant(Identifier,OrderedFieldDeclarationList(TypeIdentifier)))))",
        parse("pub enum Option<T> {\n    None,\n    Some(T),\n}")
    )

    @Test
    fun parsesVariableBindings() = assertEquals(
        "SourceFile(" +
            "LetDeclaration(let,BoundIdentifier)," +
            "LetDeclaration(let,BoundIdentifier,Integer)," +
            "LetDeclaration(let,BoundIdentifier,TypeIdentifier))",
        parse("let x;\nlet x = 42;\nlet x: i32;")
    )

    // === expressions.txt ===

    @Test
    fun parsesIdentifiers() = assertEquals(
        "SourceFile(FunctionItem(fn,BoundIdentifier,ParamList," +
            "Block(ExpressionStatement(Identifier))))",
        parse("fn main() {\n  abc;\n}")
    )

    @Test
    fun parsesUnaryOperatorExpressions() = assertEquals(
        "SourceFile(" +
            "ExpressionStatement(UnaryExpression(ArithOp,Identifier))," +
            "ExpressionStatement(UnaryExpression(LogicOp,Identifier))," +
            "ExpressionStatement(UnaryExpression(DerefOp,Identifier)))",
        parse("-num;\n!bits;\n*boxed_thing;")
    )

    @Test
    fun parsesBinaryOperatorExpressions() = assertEquals(
        "SourceFile(" +
            "ExpressionStatement(BinaryExpression(Identifier,ArithOp,Identifier))," +
            "ExpressionStatement(BinaryExpression(Identifier,ArithOp,Identifier))," +
            "ExpressionStatement(BinaryExpression(Identifier,CompareOp,Identifier)))",
        parse("a * b;\na + b;\na == b;")
    )

    @Test
    fun parsesIfExpressions() = assertEquals(
        "SourceFile(LetDeclaration(let,BoundIdentifier," +
            "IfExpression(if,BinaryExpression(Identifier,CompareOp,Integer)," +
            "Block(ExpressionStatement(Integer)),else,Block(ExpressionStatement(Integer)))))",
        parse("let y = if x == 5 { 10 } else { 15 };")
    )

    @Test
    fun parsesMatchExpressions() = assertEquals(
        "SourceFile(ExpressionStatement(MatchExpression(match,Identifier," +
            "MatchBlock(MatchArm(LiteralPattern(Integer),String)," +
            "MatchArm(_,String)))))",
        parse("match x {\n    1 => \"one\",\n    _ => \"other\",\n}")
    )

    // === literals.txt ===

    @Test
    fun parsesIntegerLiterals() = assertEquals(
        "SourceFile(" +
            "ExpressionStatement(Integer)," +
            "ExpressionStatement(Integer)," +
            "ExpressionStatement(Integer))",
        parse("0;\n123;\n0usize;")
    )

    @Test
    fun parsesFloatingPointLiterals() = assertEquals(
        "SourceFile(" +
            "ExpressionStatement(Float)," +
            "ExpressionStatement(Float)," +
            "ExpressionStatement(Float))",
        parse("123.123;\n2.;\n123.0f64;")
    )

    @Test
    fun parsesStringLiterals() = assertEquals(
        "SourceFile(" +
            "ExpressionStatement(String)," +
            "ExpressionStatement(String))",
        parse("\"\";\n\"abc\";")
    )

    @Test
    fun parsesRawStringLiterals() = assertEquals(
        "SourceFile(" +
            "ExpressionStatement(RawString)," +
            "ExpressionStatement(RawString))",
        parse("r#\"abc\"#; r##\"ok\"##;")
    )

    @Test
    fun parsesBooleanLiterals() = assertEquals(
        "SourceFile(ExpressionStatement(Boolean),ExpressionStatement(Boolean))",
        parse("true;\nfalse;")
    )

    // === macros.txt ===

    @Test
    fun parsesMacroInvocationNoArguments() = assertEquals(
        "SourceFile(" +
            "MacroInvocation(Identifier,ParenthesizedTokens)," +
            "MacroInvocation(Identifier,BracketedTokens)," +
            "MacroInvocation(Identifier,BracedTokens)," +
            "MacroInvocation(ScopedIdentifier(ScopeIdentifier,Identifier),ParenthesizedTokens)," +
            "MacroInvocation(ScopedIdentifier(ScopeIdentifier,ScopeIdentifier,Identifier),BracedTokens))",
        parse("a!();\nb![];\nc!{}\nd::e!();\nf::g::h!{}")
    )

    @Test
    fun parsesMacroInvocationArbitraryTokens() = assertEquals(
        "SourceFile(" +
            "MacroInvocation(Identifier,ParenthesizedTokens(ArithOp,Identifier,ArithOp))," +
            "MacroInvocation(Identifier,ParenthesizedTokens(BitOp,Identifier,BitOp)))",
        parse("a!(* a *);\na!(& a &);")
    )

    @Test
    fun parsesMacroDefinition() = assertEquals(
        "SourceFile(MacroDefinition(macro_rules,Identifier," +
            "MacroRule(ParenthesizedTokens," +
            "ParenthesizedTokens(Identifier,ParenthesizedTokens(String)))))",
        parse("macro_rules! say_hello {\n    () => (\n        println!(\"Hello!\");\n    )\n}")
    )

    // === patterns.txt ===

    @Test
    fun parsesTupleStructPatterns() = assertEquals(
        "SourceFile(ExpressionStatement(MatchExpression(match,Identifier,MatchBlock(" +
            "MatchArm(TuplePattern(TypeIdentifier,BoundIdentifier),String)," +
            "MatchArm(TuplePattern(ScopedTypeIdentifier(ScopeIdentifier,TypeIdentifier)),String)))))",
        parse("match x {\n  Some(x) => \"some\",\n  std::None() => \"none\"\n}")
    )

    @Test
    fun parsesReferencePatterns() = assertEquals(
        "SourceFile(ExpressionStatement(MatchExpression(match,Identifier,MatchBlock(" +
            "MatchArm(TuplePattern(TypeIdentifier,RefPattern(ref,BoundIdentifier))," +
            "FieldExpression(Identifier,Integer))," +
            "MatchArm(RefPattern(ref,MutPattern(mut,BoundIdentifier)),Identifier)," +
            "MatchArm(ReferencePattern(mut,BoundIdentifier),Identifier)))))",
        parse("match x {\n  A(ref x) => x.0,\n  ref mut y => y,\n  & mut z => z,\n}")
    )

    @Test
    fun parsesStructPatterns() = assertEquals(
        "SourceFile(ExpressionStatement(MatchExpression(match,Identifier,MatchBlock(" +
            "MatchArm(StructPattern(TypeIdentifier," +
            "FieldPatternList(FieldPattern(BoundIdentifier),FieldPattern(BoundIdentifier)))," +
            "Guard(if,BinaryExpression(Identifier,CompareOp,Integer))," +
            "TupleExpression(String,Identifier))," +
            "MatchArm(StructPattern(TypeIdentifier," +
            "FieldPatternList(FieldPattern(FieldIdentifier,BoundIdentifier)," +
            "FieldPattern(FieldIdentifier,_)))," +
            "TupleExpression(String,Identifier))))))",
        parse(
            "match x {\n" +
                "  Person{name, age} if age < 5 => (\"toddler\", name),\n" +
                "  Person{name: adult_name, age: _} => (\"adult\", adult_name),\n}"
        )
    )

    // === types.txt ===

    @Test
    fun parsesUnitType() = assertEquals(
        "SourceFile(TypeItem(type,TypeIdentifier,UnitType))",
        parse("type A = ();")
    )

    @Test
    fun parsesTupleTypes() = assertEquals(
        "SourceFile(TypeItem(type,TypeIdentifier,TupleType(TypeIdentifier,TypeIdentifier)))",
        parse("type A = (i32, String);")
    )

    @Test
    fun parsesReferenceTypes() = assertEquals(
        "SourceFile(" +
            "TypeItem(type,TypeIdentifier,ReferenceType(TypeIdentifier))," +
            "TypeItem(type,TypeIdentifier,ReferenceType(Lifetime,TypeIdentifier))," +
            "TypeItem(type,TypeIdentifier,ReferenceType(Lifetime,mut,TypeIdentifier)))",
        parse("type A = &B;\ntype C = &'a str;\ntype D = &'a mut str;")
    )

    @Test
    fun parsesGenericTypes() = assertEquals(
        "SourceFile(" +
            "TypeItem(type,TypeIdentifier," +
            "GenericType(TypeIdentifier,TypeArgList(TypeIdentifier)))," +
            "TypeItem(type,TypeIdentifier," +
            "GenericType(TypeIdentifier,TypeArgList(TypeIdentifier,TypeIdentifier))))",
        parse("type A = B<C>;\ntype D = E<F, str>;")
    )

    @Test
    fun parsesArrayTypes() = assertEquals(
        "SourceFile(" +
            "TypeItem(type,TypeIdentifier,ArrayType(TypeIdentifier,Integer))," +
            "TypeItem(type,TypeIdentifier,ReferenceType(ArrayType(TypeIdentifier))))",
        parse("type A = [B; 4];\ntype C = &[D];")
    )
}
