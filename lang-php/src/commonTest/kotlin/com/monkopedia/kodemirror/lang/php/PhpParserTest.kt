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

package com.monkopedia.kodemirror.lang.php

import kotlin.test.Test
import kotlin.test.assertEquals

class PhpParserTest {

    private fun parse(input: String): String = treeToString(phpParser.parse(input))

    // === Literals ===

    @Test
    fun booleans() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ExpressionStatement(Boolean),ExpressionStatement(Boolean),ExpressionStatement(Boolean),ExpressionStatement(Boolean),ExpressionStatement(Boolean),ExpressionStatement(Boolean),TextInterpolation(PhpClose))",
        parse("<?php\nTrue;\ntrue;\nTRUE;\nfalse;\nFalse;\nFALSE;\n?>")
    )

    @Test
    fun floats() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ExpressionStatement(Float),ExpressionStatement(Float),ExpressionStatement(Float),ExpressionStatement(Float),ExpressionStatement(Float),ExpressionStatement(Float),ExpressionStatement(Float))",
        parse(
            "<?php\n\n1.0;\n1E432;\n1.0E-3432;\n1423.0E3432;\n.5;\n6.674_083e11;\n107_925_284.88;\n"
        )
    )

    @Test
    fun integers() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ExpressionStatement(Integer),ExpressionStatement(Integer),ExpressionStatement(Integer),ExpressionStatement(Integer),ExpressionStatement(Integer),ExpressionStatement(Integer),ExpressionStatement(Integer),ExpressionStatement(Integer),ExpressionStatement(Integer))",
        parse(
            "<?php\n\n1234;\n1_234_456;\n0123;\n0123_456;\n0x1A;\n0x1A_2B_3C;\n0b111111111;\n0b1111_1111_1111;\n0o123;\n"
        )
    )

    @Test
    fun shellCommand() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ExpressionStatement(ShellExpression),ExpressionStatement(ShellExpression))",
        parse("<?php\n`ls -la`;\n`ls`;\n")
    )

    // === Interpolation ===

    @Test
    fun noInterpolatedText() = assertEquals(
        "Template(TextInterpolation(PhpOpen),EchoStatement(echo,String))",
        parse("<?php\necho \"hi\";\n")
    )

    @Test
    fun interpolatedTextAtBeginning() = assertEquals(
        "Template(TextInterpolation(Text,PhpOpen),EchoStatement(echo,String))",
        parse("<div>\n<?php\necho \"hi\";")
    )

    @Test
    fun interpolatedTextAtEnd() = assertEquals(
        "Template(TextInterpolation(PhpOpen),EchoStatement(echo,String),TextInterpolation(PhpClose,Text))",
        parse("<?php\necho \"hi\";\n?>\n\n<div>\n")
    )

    @Test
    fun interpolatedTextInMiddle() = assertEquals(
        "Template(TextInterpolation(PhpOpen),EchoStatement(echo,String),TextInterpolation(PhpClose,Text,PhpOpen),EchoStatement(echo,String),TextInterpolation(PhpClose))",
        parse("<?php\necho \"hi\";\n?>\n\n<div>\n\n<?php\necho \"bye\";\n?>")
    )

    // === Statements ===

    @Test
    fun ifStatements() = assertEquals(
        "Template(TextInterpolation(PhpOpen),IfStatement(if,ParenthesizedExpression(BinaryExpression(VariableName,CompareOp,Integer)),Block(EchoStatement(echo,String))),IfStatement(if,ParenthesizedExpression(BinaryExpression(VariableName,CompareOp,Integer)),Block(EchoStatement(echo,String)),else,Block(EchoStatement(echo,String))),IfStatement(if,ParenthesizedExpression(BinaryExpression(VariableName,CompareOp,Integer)),Block(EchoStatement(echo,String)),elseif,ParenthesizedExpression(BinaryExpression(VariableName,CompareOp,Integer)),Block(EchoStatement(echo,String)),else,Block(EchoStatement(echo,String))))",
        parse(
            "<?php\n\nif (\$a > 0) {\n  echo \"Yes\";\n}\n\nif (\$a==0) {\n  echo \"bad\";\n} else {\n  echo \"good\";\n}\n\nif (\$a==0) {\n  echo \"bad\";\n} elseif (\$a==3) {\n  echo \"bad\";\n} else {\n  echo \"good\";\n}\n"
        )
    )

    @Test
    fun whileStatements() = assertEquals(
        "Template(TextInterpolation(PhpOpen),WhileStatement(while,ParenthesizedExpression(BinaryExpression(VariableName,CompareOp,Integer)),Block(EchoStatement(echo,VariableName),ExpressionStatement(UpdateExpression(VariableName,ArithOp)))))",
        parse("<?php\nwhile (\$a < 10) {\n  echo \$a;\n  \$a++;\n}\n")
    )

    @Test
    fun forStatements() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ForStatement(for,ForSpec(AssignmentExpression(VariableName,AssignOp,Integer),BinaryExpression(VariableName,CompareOp,Integer),UpdateExpression(VariableName,ArithOp)),EchoStatement(echo,VariableName)),ForStatement(for,ForSpec(AssignmentExpression(VariableName,AssignOp,Integer),BinaryExpression(VariableName,CompareOp,Integer),UpdateExpression(VariableName,ArithOp)),ColonBlock(EchoStatement(echo,VariableName)),endfor))",
        parse(
            "<?php\n\nfor(\$a=0;\$a<5;\$a++) echo \$a;\nfor(\$a=0;\$a<5;\$a++):\n  echo \$a;\nendfor;\n"
        )
    )

    @Test
    fun includeStatement() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ExpressionStatement(IncludeExpression(include,String)))",
        parse("<?php\ninclude \"015.inc\";\n")
    )

    @Test
    fun doWhileStatements() = assertEquals(
        "Template(TextInterpolation(PhpOpen),DoStatement(do,Block(EchoStatement(echo,VariableName),ExpressionStatement(UpdateExpression(VariableName,ArithOp))),while,ParenthesizedExpression(BinaryExpression(VariableName,CompareOp,Integer))))",
        parse("<?php\ndo {\n  echo \$i;\n  \$i--;\n} while(\$i>0);\n")
    )

    // === Expressions ===

    @Test
    fun dynamicVariableNames() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ExpressionStatement(AssignmentExpression(DynamicVariable(VariableName),AssignOp,VariableName)))",
        parse("<?php\n\$\$k = \$v;\n")
    )

    @Test
    fun reservedIdentifiersAsNames() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ExpressionStatement(AssignmentExpression(VariableName,AssignOp,NewExpression(new,Name,ArgList))))",
        parse("<?php\n\$foo = new self();\n")
    )

    @Test
    fun concatenationPrecedence() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ExpressionStatement(BinaryExpression(String,ConcatOp,BinaryExpression(String,ArithOp,Integer))))",
        parse("<?php\n\n\"3\" . \"5\" + 7;\n")
    )

    @Test
    fun arrays() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ExpressionStatement(CallExpression(Name,ArgList(ArrayExpression(Integer,Integer,Integer)))),ExpressionStatement(CallExpression(Name,ArgList(ArrayExpression(Pair(String,String),Pair(String,String),Pair(String,String))))),ExpressionStatement(AssignmentExpression(VariableName,AssignOp,ArrayExpression(VariadicUnpacking(VariableName)))),TextInterpolation(PhpClose))",
        parse(
            "<?php\nprint_r([1, 2, 3]);\nprint_r([\"foo\" => \"orange\", \"bar\" => \"apple\", \"baz\" => \"lemon\"]);\n\$a = [...\$values];\n?>"
        )
    )

    @Test
    fun nullsafeOperator() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ExpressionStatement(MemberExpression(VariableName,Name)),ExpressionStatement(CallExpression(MemberExpression(VariableName,Name),ArgList(VariableName))),ExpressionStatement(NewExpression(new,MemberExpression(VariableName,Name))),ExpressionStatement(AssignmentExpression(VariableName,AssignOp,MemberExpression(CallExpression(MemberExpression(MemberExpression(VariableName,Name),Name),ArgList),Name))))",
        parse(
            "<?php\n\n\$a?->b;\n\$a?->b(\$c);\nnew \$a?->b;\n\$country = \$session?->user?->getAddress()?->country;\n"
        )
    )

    // === Declarations ===

    @Test
    fun classDeclarationsWithBaseClasses() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ClassDeclaration(class,Name,BaseClause(extends,Name),DeclarationList))",
        parse("<?php\nclass A extends B {\n\n}\n")
    )

    @Test
    fun functionParameters() = assertEquals(
        "Template(TextInterpolation(PhpOpen),FunctionDefinition(function,Name,ParamList(Parameter(NamedType(Name),VariableName),VariadicParameter(NamedType(Name),VariableName)),Block))",
        parse("<?php\nfunction test(int \$a, string ...\$b)\n{\n}\n")
    )

    @Test
    fun definingConstants() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ExpressionStatement(CallExpression(Name,ArgList(String,String))),ConstDeclaration(const,VariableDeclarator(Name,AssignOp,String)),ConstDeclaration(const,VariableDeclarator(Name,AssignOp,BinaryExpression(Name,ConcatOp,String))),ConstDeclaration(const,VariableDeclarator(Name,AssignOp,ArrayExpression(array,ValueList(String,String,String)))),ExpressionStatement(CallExpression(Name,ArgList(String,ArrayExpression(array,ValueList(String,String,String))))))",
        parse(
            "<?php\n\ndefine(\"CONSTANT\", \"Hello world.\");\nconst CONSTANT = 'Hello World';\nconst ANOTHER_CONST = CONSTANT.'; Goodbye World';\nconst ANIMALS = array('dog', 'cat', 'bird');\ndefine('ANIMALS', array(\n    'dog',\n    'cat',\n    'bird'\n));\n"
        )
    )

    // === String interpolation ===

    @Test
    fun complexVariableAccess() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ExpressionStatement(String(Interpolation(VariableName))))",
        parse("<?php\n\n\"{\$test}\";\n")
    )

    @Test
    fun simpleVariableAccess() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ExpressionStatement(String(VariableName)),ExpressionStatement(String(Interpolation(Name))))",
        parse("<?php\n\n\"Hello \$people, you're awesome!\";\n\"hello \${a} world\";\n")
    )

    @Test
    fun singleQuoted() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ExpressionStatement(String),ExpressionStatement(String),ExpressionStatement(String),ExpressionStatement(String),ExpressionStatement(String),ExpressionStatement(String),ExpressionStatement(String))",
        parse(
            "<?php\n\n'this is a simple string';\n'You can also have embedded newlines in\nstrings this way as it is\nokay to do';\n'Arnold once said: \"I\\'ll be back\"';\n'You deleted C:\\\\*.*?';\n'You deleted C:\\*.*?';\n'This will not expand: \\n a newline';\n'Variables do not \$expand \$either';\n"
        )
    )

    // === Types ===

    @Test
    fun typeNames() = assertEquals(
        "Template(TextInterpolation(PhpOpen),FunctionDefinition(function,Name,ParamList,NamedType(Name),Block),FunctionDefinition(function,Name,ParamList,NamedType(QualifiedName(NamespaceName,Name)),Block))",
        parse("<?php\nfunction a(): A {}\nfunction b(): A\\B {}\n")
    )

    @Test
    fun optionalTypes() = assertEquals(
        "Template(TextInterpolation(PhpOpen),FunctionDefinition(function,Name,ParamList,OptionalType(LogicOp,NamedType(array)),Block),FunctionDefinition(function,Name,ParamList,OptionalType(LogicOp,NamedType(Name)),Block))",
        parse("<?php\n\nfunction a(): ?array {}\nfunction b(): ?Something {}\n")
    )

    // === Classes ===

    @Test
    fun abstractClass() = assertEquals(
        "Template(TextInterpolation(PhpOpen),ClassDeclaration(abstract,class,Name,DeclarationList(MethodDeclaration(Visibility,function,Name,ParamList,Block),MethodDeclaration(abstract,Visibility,function,Name,ParamList))))",
        parse(
            "<?php\n\nabstract class A {\n    public function a() {}\n    abstract public function b();\n}\n"
        )
    )

    @Test
    fun textEndsInLessThan() = assertEquals(
        "Template(Text)",
        parse("foo<")
    )

    @Test
    fun closingTagsBeforeFirstPhpTag() = assertEquals(
        "Template(TextInterpolation(Text,PhpOpen),ExpressionStatement(Name))",
        parse("a ?> b <?php c;")
    )
}
