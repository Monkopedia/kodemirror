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
package com.monkopedia.kodemirror.lang.html

import com.monkopedia.kodemirror.lezer.common.ChangedRange
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Incremental reparses of HTML with nested `<script>`/`<style>` content go
 * through `parseMixed`'s fragment cursor, whose `StructureCursor.moveTo` used to
 * spin forever when it could not enter the node covering the target position
 * (its children all in buffers). Upstream (`@lezer/common` 1.5.1) falls through
 * to `next()` in that case.
 *
 * Each parse runs on a daemon thread with a deadline, so a regression fails the
 * test instead of hanging the build. This is JVM-only for that reason. Every
 * expectation is the tree `@codemirror/lang-html` 6.4.11 builds incrementally
 * for the same edit, which equals its fresh parse here.
 */
class MixedIncrementalReparseTest {

    private fun reparseInserting(doc: String, pos: Int): String {
        val parser = htmlLanguage.parser
        val fragments = TreeFragment.applyChanges(
            TreeFragment.addTree(parser.parse(doc)),
            listOf(ChangedRange(pos, pos, pos, pos + 1))
        )
        val edited = doc.substring(0, pos) + " " + doc.substring(pos)
        val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r).also { it.isDaemon = true }
        }
        try {
            val result = executor.submit<Tree> { parser.parse(edited, fragments) }
            return treeToString(result.get(DEADLINE_SECONDS, TimeUnit.SECONDS))
        } catch (e: TimeoutException) {
            fail(
                "Incremental reparse of ${doc.length} chars did not finish within ${DEADLINE_SECONDS}s"
            )
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun editInsideTheScriptOpenTag() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,Attribute(AttributeName),EndTag),Text," +
            "MismatchedCloseTag(StartCloseTag,TagName,EndTag),Text))",
        reparseInserting("<script>x</script> ", 2)
    )

    @Test
    fun editInsideTheStyleCloseTag() = assertEquals(
        "Document(Text,Element(OpenTag(StartTag,TagName,EndTag)," +
            "StyleSheet(RuleSet(TagSelector(TagName),Block)),CloseTag(StartCloseTag,TagName,EndTag)),Text)",
        reparseInserting("a<style>b{}</style>c", 10)
    )

    @Test
    fun editAfterANestedScript() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "Element(OpenTag(StartTag,TagName,EndTag),Script(ExpressionStatement(VariableName))," +
            "CloseTag(StartCloseTag,TagName,EndTag)),MismatchedCloseTag(StartCloseTag,TagName,⚠),Text))",
        reparseInserting("<div><script>x</script></div>", 26)
    )

    private companion object {
        const val DEADLINE_SECONDS = 20L
    }
}
