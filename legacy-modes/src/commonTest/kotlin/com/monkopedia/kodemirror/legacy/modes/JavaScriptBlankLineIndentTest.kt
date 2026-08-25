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

import com.monkopedia.kodemirror.language.IndentContext
import com.monkopedia.kodemirror.language.StreamLanguage
import com.monkopedia.kodemirror.language.getIndentation
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for `isContinuedStatement` reading `textAfter[0]` (issue
 * #264).
 *
 * CodeMirror 5 v5.65.16 `mode/javascript/javascript.js:852-856` reads
 * `textAfter.charAt(0)`, which returns `""` for an empty line; neither
 * `isOperatorChar` nor `/[,.]/` matches `""`, so the answer for a blank line is
 * a plain `false`. The port used `textAfter[0]`, which throws
 * `StringIndexOutOfBoundsException` on `""`.
 *
 * `||` short-circuits, so the read is only reached when `state.lastType` is
 * neither `"operator"` nor `","` -- the case where the preceding token is an
 * identifier or a closing paren, not an assignment.
 *
 * The tests come in two halves because the two halves carry different weight:
 *
 * * [indentQueriesAtTheStartOfEveryLine] drives the public
 *   [getIndentation] entry point. It cannot reach the faulty read on this port
 *   (see below) and so passes before and after the fix; it is here to pin the
 *   answers the boundary actually gives for blank lines.
 * * [indentOfABlankContinuedStatementLine] and its siblings call
 *   `StreamParser.indent` -- the same method [getIndentation] ends up in, via
 *   `StreamLanguage.getIndent` -- with a [JavaScriptState] built directly.
 *   These are the tests that fail with `StringIndexOutOfBoundsException`
 *   without the fix.
 *
 * Building the state by hand is necessary rather than convenient: this port
 * does not carry CodeMirror's `cc` continuation stack, so nothing ever pushes a
 * lexical scope and `state.lexical` stays the base `"block"` scope for every
 * document. The `"stat"` branch that calls `isContinuedStatement` is therefore
 * unreachable through the parser today, which makes the crash latent rather
 * than live. [JavaScriptState] and [JSLexical] are public API, and these tests
 * hold the upstream contract for the branch so that porting the lexical stack
 * does not resurrect the crash.
 */
class JavaScriptBlankLineIndentTest {

    /** Indent column reported for the first position of each line of [code]. */
    private fun indentsPerLine(code: String): List<Int?> {
        val lang = StreamLanguage.define(javaScriptLegacy)
        val state = EditorState.create(
            EditorStateConfig(doc = code.asDoc(), extensions = lang.extension)
        )
        return (1..state.doc.lines).map { n ->
            getIndentation(state, state.doc.line(LineNumber(n)).from)
        }
    }

    /**
     * The public boundary answers an indent query on every line, blank lines
     * included, for the documents whose last token before the blank line is an
     * identifier or `)` -- the shape that reaches the faulty read once a
     * statement scope exists.
     */
    @Test
    fun indentQueriesAtTheStartOfEveryLine() {
        assertEquals(listOf(0, 0, 0), indentsPerLine("foo()\n\n"))
        assertEquals(listOf(0, 0, 0), indentsPerLine("foo\n\n"))
        assertEquals(listOf(0, 0, 0, 0, 0), indentsPerLine("function f() {\n  foo();\n\n}\n"))
        assertEquals(listOf(0, 0, 0, 0), indentsPerLine("foo();\n   \nbar()\n"))
    }

    /** A `"stat"` scope indented to column 4, with no enclosing scope. */
    private fun statState(lastType: String) = JavaScriptState(
        lastType = lastType,
        lexical = JSLexical(indented = 4, column = 0, type = "stat", align = false)
    )

    private fun indentContext(): IndentContext {
        val lang = StreamLanguage.define(javaScriptLegacy)
        return IndentContext(
            EditorState.create(
                EditorStateConfig(doc = "foo()\n\n".asDoc(), extensions = lang.extension)
            )
        )
    }

    /**
     * The crashing case: a blank line inside a statement scope whose previous
     * token is a closing paren. Upstream answers `false`, so the statement is
     * not treated as continued and the line takes the scope's own indentation
     * with no continuation unit added.
     */
    @Test
    fun indentOfABlankContinuedStatementLine() {
        assertEquals(4, javaScriptLegacy.indent(statState(")"), "", indentContext()))
    }

    /** Same for an identifier as the previous token. */
    @Test
    fun indentOfABlankLineAfterAnIdentifier() {
        assertEquals(4, javaScriptLegacy.indent(statState("variable"), "", indentContext()))
    }

    /**
     * The negative half of the oracle: when the line *is* a continuation the
     * answer must still be the continued one, so the fix cannot be "always
     * return false". `,` as the first character matches `/[,.]/` upstream.
     */
    @Test
    fun indentOfANonBlankContinuedStatementLine() {
        val cx = indentContext()
        assertEquals(4 + cx.unit, javaScriptLegacy.indent(statState(")"), ", 1)", cx))
    }

    /**
     * And the short-circuit path, which never reached the faulty read: an
     * assignment leaves `lastType == "operator"`, so the statement is continued
     * whatever follows -- including nothing at all.
     */
    @Test
    fun indentOfABlankLineAfterAnAssignmentOperator() {
        val cx = indentContext()
        assertEquals(4 + cx.unit, javaScriptLegacy.indent(statState("operator"), "", cx))
    }
}
