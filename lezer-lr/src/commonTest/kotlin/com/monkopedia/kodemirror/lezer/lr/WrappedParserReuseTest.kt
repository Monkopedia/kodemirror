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
package com.monkopedia.kodemirror.lezer.lr

import com.monkopedia.kodemirror.lezer.common.NodeType
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import com.monkopedia.kodemirror.lezer.common.parseMixed
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Fragment reuse in `Parse.advanceStack` under a parse wrapper (#354). Every
 * expectation is what `@lezer/lr` 1.4.10 produces for the same parser table,
 * input and fragment. The recovery-time fork guard is covered by
 * `:lang-javascript`'s `RecoveryForkTest`, since neither fixture grammar here
 * reaches an ambiguous state during recovery.
 */
class WrappedParserReuseTest {

    // --- Fragment reuse under a parse wrapper ---------------------------
    //
    // Upstream gates reuse on the fragment cursor alone; a wrapper is applied
    // around the inner `Parse` (`createParse`), which still reuses. The
    // document `"12345"` reads as a `Number` fresh; a `String` fragment over
    // it shows reuse as `JsonText(String)`.

    private fun reusedShape(parser: LRParser): String {
        val doc = "12345"
        val string = parser.nodeSet.types.first { it.name == "String" }
        val cached = Tree(string, emptyList(), emptyList(), doc.length)
        val root = Tree(NodeType.none, listOf(cached), listOf(0), doc.length)
        return treeToString(parser.parse(doc, listOf(TreeFragment(0, doc.length, root, 0))))
    }

    private val unwrapped = jsonParser.configure(ParserConfig(bufferLength = 1))

    @Test
    fun unwrappedParserReusesTheFragment() =
        assertEquals("JsonText(String)", reusedShape(unwrapped))

    @Test
    fun parserWrappedInParseMixedStillReusesTheFragment() = assertEquals(
        "JsonText(String)",
        reusedShape(unwrapped.configure(ParserConfig(wrap = parseMixed { _, _ -> null })))
    )

    @Test
    fun parserWithAPassThroughWrapperStillReusesTheFragment() = assertEquals(
        "JsonText(String)",
        reusedShape(unwrapped.configure(ParserConfig(wrap = { parse, _, _, _ -> parse })))
    )
}
