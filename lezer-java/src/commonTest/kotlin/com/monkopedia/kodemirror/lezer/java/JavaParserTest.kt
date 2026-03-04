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

package com.monkopedia.kodemirror.lezer.java

import kotlin.test.Test
import kotlin.test.assertEquals

class JavaParserTest {

    private fun parse(input: String): String = treeToString(parser.parse(input))

    // === Comments (comments.txt) ===

    @Test
    fun parsesComments() = assertEquals(
        "Program(LineComment,BlockComment,BlockComment)",
        parse(
            "// This is a comment\n/* This is also a comment */\n/* this comment /* // /** ends here: */"
        )
    )

    @Test
    fun parsesCommentsAndLiterals() = assertEquals(
        "Program(ExpressionStatement(IntegerLiteral),LineComment)",
        parse("123;\n// comment")
    )

    // === Literals (literals.txt) ===

    @Test
    fun parsesDecimalIntegerLiterals() = assertEquals(
        "Program(ExpressionStatement(IntegerLiteral),ExpressionStatement(IntegerLiteral),ExpressionStatement(IntegerLiteral))",
        parse("123;\n4l;\n50L;")
    )

    @Test
    fun parsesHexIntegerLiterals() = assertEquals(
        "Program(ExpressionStatement(IntegerLiteral),ExpressionStatement(IntegerLiteral),ExpressionStatement(IntegerLiteral))",
        parse("0xa_bcd_ef0;\n0Xa_bcd_ef0;\n0X8000L;")
    )

    @Test
    fun parsesBooleanLiterals() = assertEquals(
        "Program(ExpressionStatement(BooleanLiteral),ExpressionStatement(BooleanLiteral))",
        parse("true;\nfalse;")
    )

    @Test
    fun parsesStringLiterals() = assertEquals(
        "Program(ExpressionStatement(StringLiteral),ExpressionStatement(StringLiteral),ExpressionStatement(StringLiteral),ExpressionStatement(StringLiteral))",
        parse("\"\";\n\"\\\"\";\n\"This is a string\";\n\"'\";")
    )

    @Test
    fun parsesNullLiteral() = assertEquals(
        "Program(ExpressionStatement(null))",
        parse("null;")
    )

    @Test
    fun parsesFloatingPointLiterals() {
        val result = parse("4.23e9;\n1.234;\n.12345;\n1e4;")
        assertEquals(
            "Program(ExpressionStatement(FloatingPointLiteral),ExpressionStatement(FloatingPointLiteral),ExpressionStatement(FloatingPointLiteral),ExpressionStatement(FloatingPointLiteral))",
            result
        )
    }

    @Test
    fun parsesCharacterLiterals() {
        val result = parse("'a';\n'%';\n'\\t';\n'\\\\';")
        assertEquals(
            "Program(ExpressionStatement(CharacterLiteral),ExpressionStatement(CharacterLiteral),ExpressionStatement(CharacterLiteral),ExpressionStatement(CharacterLiteral))",
            result
        )
    }

    // === Declarations (declarations.txt) ===

    @Test
    fun parsesLocalVariable() = assertEquals(
        "Program(ClassDeclaration(class,Definition,ClassBody(MethodDeclaration(Modifiers(public),PrimitiveType,Definition,FormalParameters,Block(LocalVariableDeclaration(PrimitiveType,VariableDeclarator(Definition,AssignOp,IntegerLiteral)))))))",
        parse("class A {\n  public int b() {\n    int c = 5;\n  }\n}")
    )

    @Test
    fun parsesPackageDeclaration() = assertEquals(
        "Program(PackageDeclaration(package,Identifier))",
        parse("package myVector;")
    )

    @Test
    fun parsesSingleTypeImport() = assertEquals(
        "Program(ImportDeclaration(import,ScopedIdentifier(ScopedIdentifier(Identifier,Identifier),Identifier)))",
        parse("import java.util.Vector;")
    )

    @Test
    fun parsesClassDeclaration() = assertEquals(
        "Program(ClassDeclaration(class,Definition,ClassBody))",
        parse("class Point {\n}")
    )

    @Test
    fun parsesClassDeclarationWithModifiers() = assertEquals(
        "Program(ClassDeclaration(Modifiers(public),class,Definition,ClassBody),ClassDeclaration(Modifiers(private),class,Definition,ClassBody),ClassDeclaration(Modifiers(abstract),class,Definition,Superclass(extends,TypeName),ClassBody))",
        parse(
            "public class Point {\n}\n\nprivate class Point {\n}\n\nabstract class ColoredPoint extends Point {\n}"
        )
    )

    @Test
    fun parsesClassWithBody() = assertEquals(
        "Program(ClassDeclaration(class,Definition,ClassBody(FieldDeclaration(PrimitiveType,VariableDeclarator(Definition)),MethodDeclaration(void,Definition,FormalParameters,Block(ExpressionStatement(AssignmentExpression(Identifier,AssignOp,IntegerLiteral)))))))",
        parse("class Point {\n  int x;\n\n  void bar() {\n    x = 2;\n  }\n}")
    )

    @Test
    fun parsesInterfaceDeclaration() = assertEquals(
        "Program(InterfaceDeclaration(interface,Definition,InterfaceBody))",
        parse("interface Top {\n}")
    )

    @Test
    fun parsesEnumDeclaration() = assertEquals(
        "Program(EnumDeclaration(enum,Definition,EnumBody(EnumConstant(Definition),EnumConstant(Definition),EnumConstant(Definition))))",
        parse("enum HandSign {\n   SCISSOR, PAPER, STONE\n}")
    )

    // === Expressions (expressions.txt) ===

    @Test
    fun parsesAssignmentExpression() = assertEquals(
        "Program(ExpressionStatement(AssignmentExpression(Identifier,AssignOp,IntegerLiteral)))",
        parse("x = 3;")
    )

    @Test
    fun parsesBinaryExpressions() = assertEquals(
        "Program(ExpressionStatement(BinaryExpression(Identifier,CompareOp,Identifier)),ExpressionStatement(BinaryExpression(Identifier,CompareOp,Identifier)),ExpressionStatement(BinaryExpression(Identifier,LogicOp,Identifier)),ExpressionStatement(BinaryExpression(Identifier,BitOp,Identifier)),ExpressionStatement(BinaryExpression(IntegerLiteral,ArithOp,IntegerLiteral)),ExpressionStatement(BinaryExpression(IntegerLiteral,ArithOp,IntegerLiteral)))",
        parse("a > b;\na == b;\na && b;\na & b;\n3 + 2;\n3 * 2;")
    )

    @Test
    fun parsesInstanceofExpression() = assertEquals(
        "Program(ExpressionStatement(InstanceofExpression(Identifier,instanceof,ScopedTypeName(TypeName,TypeName))),ExpressionStatement(InstanceofExpression(Identifier,instanceof,GenericType(TypeName,TypeArguments(TypeName)))),ExpressionStatement(InstanceofExpression(Identifier,instanceof,ArrayType(TypeName,Dimension))))",
        parse("a instanceof C.D;\na instanceof List<B>;\nc instanceof C[];")
    )

    @Test
    fun parsesIfStatement() = assertEquals(
        "Program(IfStatement(if,ParenthesizedExpression(Identifier),ExpressionStatement(Identifier)))",
        parse("if (x)\n  y;")
    )

    @Test
    fun parsesIfThenElseStatement() = assertEquals(
        "Program(IfStatement(if,ParenthesizedExpression(AssignmentExpression(Identifier,AssignOp,IntegerLiteral)),Block(ExpressionStatement(AssignmentExpression(Identifier,AssignOp,IntegerLiteral))),else,Block(ExpressionStatement(AssignmentExpression(Identifier,AssignOp,IntegerLiteral)))))",
        parse("if (x = 3) {\n  y = 9;\n} else {\n  y = 0;\n}")
    )

    @Test
    fun parsesReturnStatement() = assertEquals(
        "Program(ReturnStatement(return,Identifier),ReturnStatement(return,BinaryExpression(Identifier,ArithOp,Identifier)),ReturnStatement(return,BinaryExpression(Identifier,ArithOp,IntegerLiteral)),ReturnStatement(return,MethodInvocation(MethodName(Identifier),ArgumentList(Identifier))))",
        parse("return x;\nreturn x * y;\nreturn x + 2;\nreturn fire(x);")
    )

    @Test
    fun parsesTextBlock() = assertEquals(
        "Program(ExpressionStatement(AssignmentExpression(Identifier,AssignOp,TextBlock)))",
        parse("x = \"\"\"\n    hello\n    multi-\"\"-line\n    foo\"\"\";")
    )

    @Test
    fun parsesAnnotation() = assertEquals(
        "Program(ClassDeclaration(Modifiers(Annotation(Identifier,AnnotationArgumentList(ElementValuePair(Identifier,AssignOp,StringLiteral))),Annotation(Identifier,AnnotationArgumentList(ElementValuePair(Identifier,AssignOp,BooleanLiteral)))),class,Definition,ClassBody))",
        parse(
            "@SuppressWarnings(value = \"unchecked\")\n@GwtCompatible(emulated = true)\nclass Duck {\n\n}"
        )
    )

    @Test
    fun parsesMarkerAnnotation() = assertEquals(
        "Program(ClassDeclaration(Modifiers(MarkerAnnotation(Identifier)),class,Definition,ClassBody(MethodDeclaration(Modifiers(MarkerAnnotation(Identifier),public),void,Definition,FormalParameters,Block))))",
        parse("@Override\nclass Quack {\n  @bar\n  public void foo() {\n\n  }\n}")
    )

    // === Types (types.txt) ===

    @Test
    fun parsesIntegralTypes() = assertEquals(
        "Program(ClassDeclaration(class,Definition,ClassBody(MethodDeclaration(PrimitiveType,Definition,FormalParameters,Block(LocalVariableDeclaration(PrimitiveType,VariableDeclarator(Definition)),LocalVariableDeclaration(PrimitiveType,VariableDeclarator(Definition)),LocalVariableDeclaration(PrimitiveType,VariableDeclarator(Definition)),LocalVariableDeclaration(PrimitiveType,VariableDeclarator(Definition)),LocalVariableDeclaration(PrimitiveType,VariableDeclarator(Definition)))))))",
        parse(
            "class Beyonce {\n  int formation() {\n    int x;\n    byte x;\n    short x;\n    long x;\n    char x;\n  }\n}"
        )
    )

    @Test
    fun parsesFloatingPointTypes() = assertEquals(
        "Program(ClassDeclaration(class,Definition,ClassBody(MethodDeclaration(PrimitiveType,Definition,FormalParameters,Block(LocalVariableDeclaration(PrimitiveType,VariableDeclarator(Definition)),LocalVariableDeclaration(PrimitiveType,VariableDeclarator(Definition)))))))",
        parse("class Beyonce {\n  int formation() {\n    float x;\n    double x;\n  }\n}")
    )

    // === Generics and type arguments (expressions.txt) ===

    @Test
    fun parsesTypeArguments() = assertEquals(
        "Program(ClassDeclaration(class,Definition,TypeParameters(TypeParameter(Definition)),ClassBody(FieldDeclaration(Modifiers(private),TypeName,VariableDeclarator(Definition)),ConstructorDeclaration(Modifiers(public),Definition,FormalParameters(FormalParameter(TypeName,Definition)),ConstructorBody(ExpressionStatement(AssignmentExpression(Identifier,AssignOp,Identifier)))),LineComment)))",
        parse(
            "class Box <T> {\n  private T theObject;\n  public Box( T arg) { theObject = arg; }\n  // more code\n}"
        )
    )

    @Test
    fun parsesEmptyTypeArguments() = assertEquals(
        "Program(LocalVariableDeclaration(GenericType(TypeName,TypeArguments(TypeName)),VariableDeclarator(Definition,AssignOp,ObjectCreationExpression(new,GenericType(TypeName,TypeArguments),ArgumentList))))",
        parse("Box<Integer> integerBox = new Box<>();")
    )

    @Test
    fun parsesLambdaExpression() = assertEquals(
        "Program(ClassDeclaration(class,Definition,ClassBody(MethodDeclaration(void,Definition,FormalParameters,Block(ExpressionStatement(LambdaExpression(Definition,Identifier)),ExpressionStatement(LambdaExpression(InferredParameters(Definition,Definition),BinaryExpression(Identifier,ArithOp,Identifier))))))))",
        parse(
            "class LambdaTest {\n  void singleton() {\n    version -> create;\n    (a, b) -> a + b;\n  }\n}"
        )
    )

    @Test
    fun parsesForStatement() = assertEquals(
        "Program(ForStatement(for,ForSpec(LocalVariableDeclaration(PrimitiveType,VariableDeclarator(Definition,AssignOp,IntegerLiteral)),BinaryExpression(Identifier,CompareOp,IntegerLiteral),UpdateExpression(Identifier,UpdateOp)),Block(ExpressionStatement(MethodInvocation(FieldAccess(Identifier,Identifier),MethodName(Identifier),ArgumentList(BinaryExpression(StringLiteral,ArithOp,Identifier)))))))",
        parse("for(int i = 1; i < 11; i++) {\n  System.out.println(\"Count is: \" + i);\n}")
    )

    @Test
    fun parsesMethodReference() = assertEquals(
        "Program(ExpressionStatement(AssignmentExpression(Identifier,AssignOp,MethodReference(Identifier,Identifier))),ExpressionStatement(MethodReference(FieldAccess(Identifier,Identifier),Identifier)),ExpressionStatement(MethodReference(ArrayType(TypeName,Dimension),new)),ExpressionStatement(MethodReference(GenericType(TypeName,TypeArguments(TypeName)),Identifier)),ExpressionStatement(MethodReference(super,Identifier)))",
        parse(
            "action = bar::method;\nfoo.bar::method;\nString[]::new;\nFoo<T>::apply;\nsuper::something;"
        )
    )
}
