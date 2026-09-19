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
package com.monkopedia.kodemirror.lang.python

import com.monkopedia.kodemirror.lezer.common.IterateSpec
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A document with nothing executable in it — empty, whitespace-only, or comments only — is a
 * valid Python program and must not produce a syntax-error node.
 *
 * `states[0]` is state 0's flag word, and `2` is `StateFlag.Accepting`. The port's `states`
 * table was transcribed from `@lezer/python` 1.1.18, which cleared that flag; upstream 1.1.19
 * ("Don't tag empty programs as syntax errors") sets it, and that single character is the whole
 * difference between the two releases' tables. Until it was synced, an empty editor rendered
 * `Script(⚠)` — a visible diagnostic, and fold and indent behaviour keys off it too (#369).
 */
class PythonEmptyDocumentTest {
    private fun parse(input: String): String = treeToString(pythonParser.parse(input))

    private fun errorNodes(input: String): List<String> {
        val found = mutableListOf<String>()
        pythonParser.parse(input).iterate(
            IterateSpec(
                enter = { nodeRef ->
                    if (nodeRef.type.isError) found.add("\u26A0@${nodeRef.from}-${nodeRef.to}")
                    null
                }
            )
        )
        return found
    }

    @Test
    fun emptyDocumentHasNoErrorNode() {
        assertEquals("Script", parse(""))
        assertEquals(emptyList(), errorNodes(""))
    }

    @Test
    fun whitespaceOnlyDocumentHasNoErrorNode() {
        val src = "\n\n   \n\n"
        assertEquals("Script", parse(src))
        assertEquals(emptyList(), errorNodes(src))
    }

    @Test
    fun commentOnlyDocumentHasNoErrorNode() {
        val src = "# just a comment\n"
        assertEquals("Script(Comment)", parse(src))
        assertEquals(emptyList(), errorNodes(src))
    }

    @Test
    fun shebangAndEncodingLinesHaveNoErrorNode() {
        val src = "#!/usr/bin/env python3\n# -*- coding: utf-8 -*-\n"
        assertEquals("Script(Comment,Comment)", parse(src))
        assertEquals(emptyList(), errorNodes(src))
    }

    @Test
    fun blankLineThenCommentHasNoErrorNode() {
        val src = "\n# leading blank then comment\n\n"
        assertEquals("Script(Comment)", parse(src))
        assertEquals(emptyList(), errorNodes(src))
    }

    /**
     * A control: a document that really is malformed must still report an error, so the
     * assertions above cannot be satisfied by a parser that simply stopped emitting `⚠`.
     */
    @Test
    fun genuinelyMalformedDocumentStillReportsAnError() {
        assertEquals(listOf("\u26A0@4-6"), errorNodes("def :\n    pass\n"))
    }
}
