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

package com.monkopedia.kodemirror.lezer.cpp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CppParserTest {

    private fun parse(input: String): String = treeToString(parser.parse(input))

    // --- declarations.txt ---

    @Test
    fun parsesNamespaceDefinition() {
        val result = parse("namespace std {\n\nint x;\n\n}  // namespace std")
        assertTrue(result.contains("NamespaceDefinition"))
        assertTrue(result.contains("Identifier"))
        assertTrue(result.contains("CompoundStatement"))
        assertTrue(result.contains("Declaration"))
        assertTrue(result.contains("PrimitiveType"))
    }

    @Test
    fun parsesUsingDeclarations() {
        val result = parse("using a;\nusing ::b;\nusing c::d;")
        assertTrue(result.contains("UsingDeclaration"))
        assertTrue(result.contains("ScopedIdentifier"))
    }

    @Test
    fun parsesReferenceDeclarations() {
        val result = parse("int main() {\n  T &x = y<T &>();\n}")
        assertTrue(result.contains("FunctionDefinition"))
        assertTrue(result.contains("ReferenceDeclarator"))
        assertTrue(result.contains("TemplateFunction"))
        assertTrue(result.contains("TemplateArgumentList"))
    }

    @Test
    fun parsesInlineMethodDefinitions() {
        val result = parse(
            "struct S {\n  int f;\n\n  S() : f(0) {}\n\n private:\n  int getF() const { return f; }\n};"
        )
        assertTrue(result.contains("StructSpecifier"))
        assertTrue(result.contains("FieldDeclaration"))
        assertTrue(result.contains("FunctionDefinition"))
        assertTrue(result.contains("FieldInitializerList"))
        assertTrue(result.contains("AccessSpecifier"))
        assertTrue(result.contains("ReturnStatement"))
    }

    @Test
    fun parsesTemplateDeclarations() {
        val result = parse("template <typename T>\nvoid foo(T &t);")
        assertTrue(result.contains("TemplateDeclaration"))
        assertTrue(result.contains("TemplateParameterList"))
        assertTrue(result.contains("TypeParameterDeclaration"))
    }

    // --- expressions.txt ---

    @Test
    fun parsesScopedFunctionCalls() {
        val result = parse("int main() {\n  abc::def(\"hello\", \"world\");\n}")
        assertTrue(result.contains("CallExpression"))
        assertTrue(result.contains("ScopedIdentifier"))
        assertTrue(result.contains("NamespaceIdentifier"))
        assertTrue(result.contains("String"))
    }

    @Test
    fun parsesNewAndDeleteExpressions() {
        val result = parse("int main() {\n  auto a = new T();\n  delete a;\n}")
        assertTrue(result.contains("NewExpression"))
        assertTrue(result.contains("DeleteExpression"))
    }

    @Test
    fun parsesLambdaExpressions() {
        val result = parse("auto f = [&](int x) -> bool {\n  return true;\n};")
        assertTrue(result.contains("LambdaExpression"))
        assertTrue(result.contains("LambdaCaptureSpecifier"))
        assertTrue(result.contains("TrailingReturnType"))
    }

    @Test
    fun parsesRawStringLiterals() {
        val result = parse("const char *s1 = R\"(\n  hello\n)\";")
        assertTrue(result.contains("RawString"))
    }

    @Test
    fun parsesTemplateCalls() {
        val result = parse("int main() {\n  if (a<b && c>()) {}\n}")
        assertTrue(result.contains("TemplateFunction"))
        assertTrue(result.contains("TemplateArgumentList"))
        assertTrue(result.contains("CallExpression"))
    }

    // --- statements.txt ---

    @Test
    fun parsesForRangeLoop() {
        val result = parse(
            "T main() {\n  for (Value &value : values) {\n    cout << value;\n  }\n}"
        )
        assertTrue(result.contains("ForRangeLoop"))
        assertTrue(result.contains("ReferenceDeclarator"))
    }

    @Test
    fun parsesTryCatchStatement() {
        val result = parse(
            "void main() {\n  try {\n    f();\n  } catch (const exception &e) {\n  } catch (...) {\n  }\n}"
        )
        assertTrue(result.contains("TryStatement"))
        assertTrue(result.contains("CatchClause"))
    }

    @Test
    fun parsesThrowStatement() {
        val result = parse("void main() {\n  throw e;\n  throw \"exception\";\n}")
        assertTrue(result.contains("ThrowStatement"))
    }

    @Test
    fun parsesSwitchStatement() {
        val result = parse(
            "string xyzzy(Bar s) {\n  switch (s) {\n    case Bar::OK:\n      return \"OK\";\n    default:\n      return \"AHA\";\n  }\n}"
        )
        assertTrue(result.contains("SwitchStatement"))
        assertTrue(result.contains("CaseStatement"))
    }

    @Test
    fun parsesForLoop() {
        val result = parse("for (int x = 1; x < 5; x++, y++) {\n}")
        assertTrue(result.contains("ForStatement"))
        assertTrue(result.contains("UpdateExpression"))
        assertTrue(result.contains("CommaExpression"))
    }

    // --- definitions.txt ---

    @Test
    fun parsesScopedFunctionDefinitions() {
        val result = parse("int T::foo() { return 1; }\nint T::foo() const { return 0; }")
        assertTrue(result.contains("FunctionDefinition"))
        assertTrue(result.contains("ScopedIdentifier"))
        assertTrue(result.contains("NamespaceIdentifier"))
    }

    @Test
    fun parsesConstructorDefinitions() {
        val result = parse("T::T() {}\n\nT::T() : f1(0), f2(1, 2) {\n  puts(\"HI\");\n}")
        assertTrue(result.contains("FunctionDefinition"))
        assertTrue(result.contains("FieldInitializerList"))
        assertTrue(result.contains("FieldInitializer"))
    }

    @Test
    fun parsesDestructorDefinitions() {
        val result = parse("~T() {}\nT::~T() {}")
        assertTrue(result.contains("FunctionDefinition"))
        assertTrue(result.contains("DestructorName"))
    }

    @Test
    fun parsesDefaultAndDeletedMethods() {
        val result = parse(
            "class A : public B {\n  A() = default;\n  A(A &&) = delete;\n};"
        )
        assertTrue(result.contains("DefaultMethodClause"))
        assertTrue(result.contains("DeleteMethodClause"))
    }

    // --- types.txt ---

    @Test
    fun parsesAutoType() {
        val result = parse("void foo() {\n  auto x = 1;\n}")
        assertTrue(result.contains("auto"))
    }

    @Test
    fun parsesNamespacedTypes() {
        val result = parse("std::string my_string;")
        assertTrue(result.contains("ScopedTypeIdentifier"))
        assertTrue(result.contains("NamespaceIdentifier"))
        assertTrue(result.contains("TypeIdentifier"))
    }

    @Test
    fun parsesDecltype() {
        val result = parse("decltype(A) x;")
        assertTrue(result.contains("Decltype"))
    }

    // --- ambiguities.txt ---

    @Test
    fun parsesTemplateFunctionsVsRelational() {
        val result = parse("T1 a = b < c > d;\nT2 e = f<T3>(g);")
        assertTrue(result.contains("BinaryExpression"))
        assertTrue(result.contains("CompareOp"))
        assertTrue(result.contains("TemplateFunction"))
        assertTrue(result.contains("CallExpression"))
    }

    @Test
    fun parsesTemplateClassesVsRelational() {
        val result = parse("int main() {\n  T1<T2> v1;\n  T1<T2> v2 = v3;\n}")
        assertTrue(result.contains("TemplateType"))
        assertTrue(result.contains("TemplateArgumentList"))
    }

    // --- cpp20.txt ---

    @Test
    fun parsesConceptDefinition() {
        val result = parse(
            "template <class T, class U>\nconcept Derived = std::is_base_of<U, T>::value;"
        )
        assertTrue(result.contains("ConceptDefinition"))
        assertTrue(result.contains("TemplateDeclaration"))
    }

    @Test
    fun parsesCoroutines() {
        val result = parse("co_await fn() || co_await var;\nco_return 1;\nco_yield 1;")
        assertTrue(result.contains("CoAwaitExpression"))
        assertTrue(result.contains("CoReturnStatement"))
        assertTrue(result.contains("CoYieldStatement"))
    }

    @Test
    fun parsesModules() {
        val result = parse("export module helloworld;\nmodule a.b;")
        assertTrue(result.contains("ExportDeclaration"))
        assertTrue(result.contains("ModuleDeclaration"))
        assertTrue(result.contains("ModuleName"))
    }

    // --- microsoft.txt ---

    @Test
    fun parsesDeclspec() {
        val result = parse("struct __declspec(dllexport) s2\n{\n};")
        assertTrue(result.contains("StructSpecifier"))
        assertTrue(result.contains("MsDeclspecModifier"))
    }

    // --- General parsing ---

    @Test
    fun parsesComplexProgram() {
        val input = """
            #include <iostream>

            namespace myns {

            class MyClass {
            public:
                MyClass() = default;
                virtual ~MyClass() {}
                int getValue() const { return value_; }
            private:
                int value_ = 0;
            };

            }  // namespace myns
        """.trimIndent()
        val result = parse(input)
        assertEquals("Program", result.substringBefore("("))
    }

    @Test
    fun parsesEmptyProgram() {
        val result = parse("")
        assertEquals("Program", result)
    }
}
