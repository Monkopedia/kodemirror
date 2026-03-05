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
import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Smoke tests: define each mode, parse sample code, verify non-empty tree.
 */
class LegacyModesSmokeTest {

    private fun <S> smokeTest(parser: StreamParser<S>, code: String) {
        val lang = StreamLanguage.define(parser)
        assertNotNull(lang)
        val state = EditorState.create(
            EditorStateConfig(
                doc = code.asDoc(),
                extensions = lang.extension
            )
        )
        val tree = syntaxTree(state)
        assertNotNull(tree)
        assertEquals(code.length, tree.length)
        assertEquals("Document", tree.type.name)
    }

    @Test
    fun diffSmoke() = smokeTest(
        diff,
        """
--- a/file.txt
+++ b/file.txt
@@ -1,3 +1,3 @@
 unchanged
-removed
+added
        """.trimIndent()
    )

    @Test
    fun brainfuckSmoke() = smokeTest(
        brainfuck,
        "++++++++[>++++[>++>+++>+++>+<<<<-]>+>+>->>+[<]<-]>>."
    )

    @Test
    fun propertiesSmoke() = smokeTest(
        properties,
        """
# Comment
key=value
section.key = other value
        """.trimIndent()
    )

    @Test
    fun ntriplesSmoke() = smokeTest(
        ntriples,
        """<http://example.org/s> <http://example.org/p> "hello" ."""
    )

    @Test
    fun tomlSmoke() = smokeTest(
        toml,
        """
[package]
name = "test"
version = "1.0"
enabled = true
count = 42
        """.trimIndent()
    )

    @Test
    fun dockerfileSmoke() = smokeTest(
        dockerFile,
        """
FROM ubuntu:latest
RUN apt-get update
EXPOSE 8080
CMD ["node", "app.js"]
        """.trimIndent()
    )

    @Test
    fun diffHasTokens() {
        val lang = StreamLanguage.define(diff)
        val state = EditorState.create(
            EditorStateConfig(
                doc = "+added\n-removed\n@@ context".asDoc(),
                extensions = lang.extension
            )
        )
        val tree = syntaxTree(state)
        val tokenNames = mutableSetOf<String>()
        val cursor = tree.cursor()
        while (cursor.next()) {
            tokenNames.add(cursor.type.name)
        }
        // Should have at least Document + some non-empty token types
        assertTrue(
            tokenNames.any { it.isNotEmpty() && it != "Document" },
            "Expected token types besides Document, got: $tokenNames"
        )
    }
}
