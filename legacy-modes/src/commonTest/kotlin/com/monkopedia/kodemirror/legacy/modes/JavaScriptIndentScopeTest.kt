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
package com.monkopedia.kodemirror.legacy.modes

import com.monkopedia.kodemirror.language.StreamLanguage
import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.getIndentUnit
import com.monkopedia.kodemirror.language.getIndentation
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exact indent columns for nested JavaScript/TypeScript/JSON documents,
 * measured through the public [getIndentation] boundary (issue #276).
 *
 * Every expected column in this file was produced by running the upstream
 * `@codemirror/legacy-modes` 6.5.0 `mode/javascript.js` -- the module this
 * file's subject is a port of -- over the same document with the same
 * `indentUnit` (2) and `tabSize` (4), driving it with the real
 * `@codemirror/language` 6.12.3 `StringStream` and the same line-by-line
 * loop `StreamLanguage.getIndent` uses. They are upstream's answers, not
 * this port's.
 */
class JavaScriptIndentScopeTest {

    /** Indent column reported for the first position of each line of [code]. */
    private fun indents(parser: StreamParser<JavaScriptState>, code: String): List<Int?> {
        val lang = StreamLanguage.define(parser)
        val state = EditorState.create(
            EditorStateConfig(doc = code.asDoc(), extensions = lang.extension)
        )
        assertEquals(2, getIndentUnit(state), "fixtures are calibrated for indentUnit 2")
        return (1..state.doc.lines).map { n ->
            getIndentation(state, state.doc.line(LineNumber(n)).from)
        }
    }

    private fun js(code: String) = indents(javaScriptLegacy, code)

    /**
     * The core of #276: three distinct nesting levels in one document.
     * A constant answer cannot satisfy this and neither can a
     * one-level-only implementation.
     */
    @Test
    fun nestedFunctionIfBlock() {
        assertEquals(
            listOf(0, 2, 4, 2, 0, 0),
            js("function f() {\n  if (x) {\n    g();\n  }\n}\n")
        )
    }

    /** The same nesting with the input left unindented. */
    @Test
    fun nestedBlocksWithUnindentedInput() {
        assertEquals(
            listOf(0, 2, 2, 0, 0, 0),
            js("function f() {\nif (x) {\ng();\n}\n}\n")
        )
    }

    /** Four levels: a function containing an object containing a function. */
    @Test
    fun deepNesting() {
        assertEquals(
            listOf(0, 2, 4, 6, 4, 2, 0, 0),
            js(
                "function outer() {\n  var o = {\n    a: function () {\n" +
                    "      return 1;\n    },\n  };\n}\n"
            )
        )
    }

    /** `switch` bodies get the double indent, and `case` labels one unit. */
    @Test
    fun switchDoubleIndent() {
        assertEquals(
            listOf(0, 2, 4, 4, 2, 4, 0, 0),
            js("switch (x) {\ncase 1:\nfoo();\nbreak;\ndefault:\nbar();\n}\n")
        )
    }

    /** A continued line inside an open paren aligns one past the bracket. */
    @Test
    fun alignsToOpenParen() {
        assertEquals(listOf(0, 4, 0), js("foo(a,\nb)\n"))
    }

    /** A `vardef` scope indents by the length of the keyword plus one. */
    @Test
    fun vardefContinuationIndent() {
        assertEquals(listOf(0, 4, 0), js("var x = 1,\ny = 2;\n"))
    }

    /** A statement continued by a trailing operator takes one unit. */
    @Test
    fun continuedExpression() {
        assertEquals(listOf(0, 4, 0), js("var x = 1 +\n2;\n"))
    }

    /** `if`/`else` bodies are `form` scopes. */
    @Test
    fun ifElseForms() {
        assertEquals(listOf(0, 2, 0, 2, 0), js("if (a)\nfoo();\nelse\nbar();\n"))
    }

    @Test
    fun forLoopBody() {
        assertEquals(listOf(0, 2, 0, 0), js("for (var i = 0; i < 10; i++) {\nfoo();\n}\n"))
    }

    @Test
    fun classBody() {
        assertEquals(listOf(0, 2, 4, 2, 0, 0), js("class A {\n  m() {\n    return 1;\n  }\n}\n"))
    }

    @Test
    fun arrayLiteralBody() {
        assertEquals(listOf(0, 2, 2, 0, 0), js("var a = [\n1,\n2\n];\n"))
    }

    @Test
    fun tryCatchBlocks() {
        assertEquals(listOf(0, 2, 0, 2, 0, 0), js("try {\nfoo();\n} catch (e) {\nbar();\n}\n"))
    }

    @Test
    fun typescriptInterfaceBody() {
        assertEquals(
            listOf(0, 2, 0, 0),
            indents(typescript, "interface Foo {\nbar: number;\n}\n")
        )
    }

    @Test
    fun jsonNestedStructure() {
        assertEquals(
            listOf(0, 2, 4, 4, 2, 0, 0),
            indents(json, "{\n  \"a\": [\n    1,\n    2\n  ]\n}\n")
        )
    }

    /**
     * Two `if`s on one line leave two `maybeelse` continuations stacked with a
     * `poplex` between them. Upstream's kludge walks past the `maybeelse` to
     * run that pending `poplex` before answering; without it the outer `form`
     * scope is still open and line 2 answers 2 instead of 0.
     */
    @Test
    fun nestedSingleStatementIf() {
        assertEquals(listOf(0, 0, 0), js("if (a) if (b) foo();\nbar();\n"))
    }

    /**
     * A bracket opened on a continuation line of a statement takes the
     * statement's own indentation as its base, not the indentation of the line
     * the bracket happens to sit on.
     */
    @Test
    fun bracketOpenedOnAContinuationLine() {
        assertEquals(listOf(0, 2, 2, 0, 0), js("foo\n  .bar(\n    baz\n  )\n"))
    }

    /**
     * Closing a `)` scope restores the enclosing indentation basis, so the
     * block opened after it on the same line does not inherit the closing
     * line's own indentation.
     */
    @Test
    fun blockOpenedAfterAnIndentedClosingParen() {
        assertEquals(listOf(0, 4, 2, 0, 0), js("for (;;\n  ) {\n  foo();\n}\n"))
    }

    /**
     * A document long enough that the indent query resumes from a parser state
     * cached on the syntax tree rather than replaying from the start. The
     * cached state must be copied, continuation stack included, or the first
     * query consumes the stack every later query depends on.
     */
    @Test
    fun longDocumentResumingFromACachedState() {
        val prefix = (0 until 45).joinToString("") { "var a$it = $it;\n" }
        val expected = List(46) { 0 } + listOf(2, 4, 2, 0, 0)
        assertEquals(
            expected,
            js(prefix + "function f() {\n  if (x) {\n    g();\n  }\n}\n")
        )
    }

    /**
     * The same, but with the cached chunk boundary falling *inside* an open
     * function body, so the state carried on the tree has a non-empty
     * continuation stack. Sharing that stack with the cached state instead of
     * copying it lets the first query consume it.
     */
    @Test
    fun longDocumentResumingInsideAnOpenBlock() {
        val body = (0 until 45).joinToString("") { "  var a$it = $it;\n" }
        val expected = listOf(0) + List(45) { 2 } + listOf(2, 4, 2, 0, 0)
        assertEquals(
            expected,
            js("function f() {\n" + body + "  if (x) {\n    g();\n  }\n}\n")
        )
    }

    // ---- The documents named in issue #276, which all answered 0 before ----

    @Test
    fun issueDocumentBlankLineAfterCall() {
        assertEquals(listOf(0, 0, 0), js("foo()\n\n"))
    }

    @Test
    fun issueDocumentBlankLineAfterIdentifier() {
        assertEquals(listOf(0, 0, 0), js("foo\n\n"))
    }

    @Test
    fun issueDocumentBlankLineAfterIf() {
        assertEquals(listOf(0, 2, 2), js("if (x)\n\n"))
    }

    @Test
    fun issueDocumentBlankLineAfterAssignment() {
        assertEquals(listOf(0, 4, 4), js("var x =\n\n"))
    }

    @Test
    fun issueDocumentBlankLineInFunctionBody() {
        assertEquals(listOf(0, 2, 2, 0, 0), js("function f() {\n  foo();\n\n}\n"))
    }

    @Test
    fun issueDocumentBlankLineInObjectLiteral() {
        assertEquals(listOf(0, 2, 2, 0, 0), js("var o = {\n  a: 1,\n\n}\n"))
    }

    @Test
    fun issueDocumentBlankLineInSwitchCase() {
        assertEquals(listOf(0, 2, 4, 0, 0), js("switch (x) {\n  case 1:\n\n}\n"))
    }

    @Test
    fun issueDocumentWhitespaceOnlyLine() {
        assertEquals(listOf(0, 0, 0, 0), js("foo();\n   \nbar()\n"))
    }
}
