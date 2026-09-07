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

import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.StringStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Token styles for JavaScript, TypeScript and JSON documents (issue #276).
 *
 * The grammar decides most of these, not the tokenizer: `"def"`,
 * `"property"`, `"type"`, `"variableName.local"` and the `"keyword"`
 * re-marking of contextual words all come from a combinator setting
 * `cx.marked`, or from a name having been registered in a variable scope. A
 * document that only checks indentation leaves nearly all of that unchecked --
 * indentation reads `state.lexical` and nothing else -- so these tests are what
 * hold the rest of the ported grammar in place.
 *
 * Every expected pair was produced by running upstream
 * `@codemirror/legacy-modes` 6.5.0 `mode/javascript.js` over the same document
 * with the real `@codemirror/language` 6.12.3 `StringStream`. Whitespace
 * tokens, which carry no style, are dropped.
 */
class JavaScriptTokenStyleTest {

    /** The (text, style) pair of every non-blank token in [code]. */
    private fun styles(
        parser: StreamParser<JavaScriptState>,
        code: String
    ): List<Pair<String, String?>> {
        val state = parser.startState(UNIT)
        val out = mutableListOf<Pair<String, String?>>()
        for (line in code.split("\n")) {
            if (line.isEmpty()) {
                parser.blankLine(state, UNIT)
                continue
            }
            val stream = StringStream(line, TAB_SIZE, UNIT)
            while (!stream.eol()) {
                stream.start = stream.pos
                var style: String? = null
                var advanced = false
                for (attempt in 0 until MAX_TOKEN_ATTEMPTS) {
                    style = parser.token(stream, state)
                    if (stream.pos > stream.start) {
                        advanced = true
                        break
                    }
                }
                check(advanced) { "stream did not advance on: $line" }
                if (stream.current().isNotBlank()) out.add(stream.current() to style)
            }
        }
        return out
    }

    /** Class names register as definitions and methods as properties. */
    @Test
    fun classDeclaration() {
        assertEquals(
            listOf(
                "class" to "keyword",
                "A" to "def",
                "{" to null,
                "m" to "property",
                "(" to null,
                ")" to null,
                "{" to null,
                "return" to "keyword",
                "1" to "number",
                ";" to null,
                "}" to null,
                "}" to null
            ),
            styles(
                javaScriptLegacy,
                "class A {\n" +
                    "  m() {\n" +
                    "    return 1;\n" +
                    "  }\n" +
                    "}\n"
            )
        )
    }

    /** Fat-arrow parameters are definitions and their uses are locals. */
    @Test
    fun arrowFunctionLocals() {
        assertEquals(
            listOf(
                "const" to "keyword",
                "f" to "def",
                "=" to "operator",
                "(" to null,
                "a" to "def",
                "," to null,
                "b" to "def",
                ")" to null,
                "=>" to "operator",
                "{" to null,
                "return" to "keyword",
                "a" to "variableName.local",
                "+" to "operator",
                "b" to "variableName.local",
                ";" to null,
                "}" to null,
                ";" to null
            ),
            styles(
                javaScriptLegacy,
                "const f = (a, b) => {\n" +
                    "return a + b;\n" +
                    "};\n"
            )
        )
    }

    /** Object keys, getters and a nested function body. */
    @Test
    fun objectLiteralProperties() {
        assertEquals(
            listOf(
                "var" to "keyword",
                "o" to "def",
                "=" to "operator",
                "{" to null,
                "a" to "property",
                ":" to null,
                "1" to "number",
                "," to null,
                "b" to "property",
                ":" to null,
                "function" to "keyword",
                "(" to null,
                ")" to null,
                "{" to null,
                "return" to "keyword",
                "2" to "number",
                ";" to null,
                "}" to null,
                "," to null,
                "get" to "property",
                "c" to "property",
                "(" to null,
                ")" to null,
                "{" to null,
                "return" to "keyword",
                "3" to "number",
                "}" to null,
                "," to null,
                "}" to null,
                ";" to null
            ),
            styles(
                javaScriptLegacy,
                "var o = {\n" +
                    "  a: 1,\n" +
                    "  b: function () {\n" +
                    "    return 2;\n" +
                    "  },\n" +
                    "  get c() { return 3 },\n" +
                    "};\n"
            )
        )
    }

    /** Module syntax: `as`, `from`, `default` and `*` are keywords. */
    @Test
    fun importsAndExports() {
        assertEquals(
            listOf(
                "import" to "keyword",
                "a" to "def",
                "," to null,
                "{" to null,
                "b" to "def",
                "as" to "keyword",
                "c" to "def",
                "}" to null,
                "from" to "keyword",
                "\"m\"" to "string",
                ";" to null,
                "import" to "keyword",
                "*" to "keyword",
                "as" to "keyword",
                "d" to "def",
                "from" to "keyword",
                "\"n\"" to "string",
                ";" to null,
                "export" to "keyword",
                "default" to "keyword",
                "a" to "variable",
                ";" to null,
                "export" to "keyword",
                "{" to null,
                "c" to "variable",
                "}" to null,
                ";" to null
            ),
            styles(
                javaScriptLegacy,
                "import a, {b as c} from \"m\";\n" +
                    "import * as d from \"n\";\n" +
                    "export default a;\n" +
                    "export {c};\n"
            )
        )
    }

    /** Destructuring binds every name it introduces. */
    @Test
    fun destructuringPatterns() {
        assertEquals(
            listOf(
                "var" to "keyword",
                "{" to null,
                "a" to "def",
                "," to null,
                "b" to "property",
                ":" to null,
                "{" to null,
                "c" to "def",
                "}" to null,
                "}" to null,
                "=" to "operator",
                "o" to "variable",
                "," to null,
                "[" to null,
                "d" to "def",
                "," to null,
                "e" to "def",
                "]" to null,
                "=" to "operator",
                "arr" to "variable",
                ";" to null,
                "function" to "keyword",
                "f" to "def",
                "(" to null,
                "{" to null,
                "x" to "def",
                "=" to "operator",
                "1" to "number",
                "}" to null,
                "," to null,
                "[" to null,
                "y" to "def",
                "]" to null,
                ")" to null,
                "{" to null,
                "return" to "keyword",
                "x" to "variableName.local",
                "+" to "operator",
                "y" to "variableName.local",
                ";" to null,
                "}" to null
            ),
            styles(
                javaScriptLegacy,
                "var {a, b: {c}} = o, [d, e] = arr;\n" +
                    "function f({x = 1}, [y]) {\n" +
                    "return x + y;\n" +
                    "}\n"
            )
        )
    }

    /** A `for` head's `var` binds into the loop's block scope. */
    @Test
    fun forLoopLocals() {
        assertEquals(
            listOf(
                "for" to "keyword",
                "(" to null,
                "var" to "keyword",
                "i" to "def",
                "=" to "operator",
                "0" to "number",
                ";" to null,
                "i" to "variableName.local",
                "<" to "operator",
                "10" to "number",
                ";" to null,
                "i" to "variableName.local",
                "++" to "operator",
                ")" to null,
                "{" to null,
                "foo" to "variable",
                "(" to null,
                ")" to null,
                ";" to null,
                "}" to null
            ),
            styles(
                javaScriptLegacy,
                "for (var i = 0; i < 10; i++) {\n" +
                    "foo();\n" +
                    "}\n"
            )
        )
    }

    /** `yield` and `yield*`. */
    @Test
    fun generatorFunction() {
        assertEquals(
            listOf(
                "function" to "keyword",
                "*" to "keyword",
                "g" to "def",
                "(" to null,
                ")" to null,
                "{" to null,
                "yield" to "keyword",
                "1" to "number",
                ";" to null,
                "yield" to "keyword",
                "*" to "operator",
                "h" to "variable",
                "(" to null,
                ")" to null,
                ";" to null,
                "}" to null
            ),
            styles(
                javaScriptLegacy,
                "function* g() {\n" +
                    "yield 1;\n" +
                    "yield* h();\n" +
                    "}\n"
            )
        )
    }

    /** Type parameters, annotations and optional parameters. */
    @Test
    fun typescriptGenerics() {
        assertEquals(
            listOf(
                "function" to "keyword",
                "f" to "def",
                "<" to "operator",
                "T" to "type",
                "extends" to "keyword",
                "object" to "type",
                ">" to "operator",
                "(" to null,
                "a" to "def",
                ":" to null,
                "T" to "type",
                "," to null,
                "b" to "def",
                "?" to "operator",
                ":" to null,
                "string" to "type",
                ")" to null,
                ":" to null,
                "T" to "type",
                "{" to null,
                "return" to "keyword",
                "a" to "variableName.local",
                ";" to null,
                "}" to null
            ),
            styles(
                typescript,
                "function f<T extends object>(a: T, b?: string): T {\n" +
                    "return a;\n" +
                    "}\n"
            )
        )
    }

    /** Modifiers, `implements`, field types and static generics. */
    @Test
    fun typescriptClassMembers() {
        assertEquals(
            listOf(
                "class" to "keyword",
                "C" to "def",
                "extends" to "keyword",
                "D" to "type",
                "implements" to "keyword",
                "I" to "type",
                "{" to null,
                "private" to "keyword",
                "readonly" to "keyword",
                "x" to "property",
                ":" to null,
                "number" to "type",
                "=" to "operator",
                "1" to "number",
                ";" to null,
                "static" to "keyword",
                "m" to "property",
                "<" to "operator",
                "T" to "type",
                ">" to "operator",
                "(" to null,
                "a" to "def",
                ":" to null,
                "T" to "type",
                ")" to null,
                ":" to null,
                "void" to "type",
                "{" to null,
                "}" to null,
                "}" to null
            ),
            styles(
                typescript,
                "class C extends D implements I {\n" +
                    "private readonly x: number = 1;\n" +
                    "static m<T>(a: T): void {}\n" +
                    "}\n"
            )
        )
    }

    /** A typed arrow: the return type must not swallow the arrow. */
    @Test
    fun typescriptArrowReturnType() {
        assertEquals(
            listOf(
                "const" to "keyword",
                "f" to "def",
                "=" to "operator",
                "(" to null,
                "a" to "def",
                ":" to null,
                "number" to "type",
                ")" to null,
                ":" to null,
                "string" to "variable",
                "=>" to "operator",
                "{" to null,
                "return" to "keyword",
                "\"\"" to "string",
                "+" to "operator",
                "a" to "variableName.local",
                ";" to null,
                "}" to null,
                ";" to null
            ),
            styles(
                typescript,
                "const f = (a: number): string => {\n" +
                    "return \"\" + a;\n" +
                    "};\n"
            )
        )
    }

    /** JSON keys are properties; the mode has no statement grammar. */
    @Test
    fun jsonDocument() {
        assertEquals(
            listOf(
                "{" to null,
                "\"a\"" to "string property",
                ":" to null,
                "[" to null,
                "1" to "number",
                "," to null,
                "2" to "number",
                "]" to null,
                "}" to null
            ),
            styles(
                json,
                "{\n" +
                    "  \"a\": [\n" +
                    "    1,\n" +
                    "    2\n" +
                    "  ]\n" +
                    "}\n"
            )
        )
    }
    private companion object {
        const val UNIT = 2
        const val TAB_SIZE = 4
        const val MAX_TOKEN_ATTEMPTS = 10
    }
}
