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

import com.monkopedia.kodemirror.language.indentOnInput
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.LanguageDataKey
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.asInsert
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The language's published language data in use (#449): `indentOnInput` triggers and
 * `wordChars`. `|` marks the cursor; expected values come from the upstream package run on the
 * same input.
 */
class HtmlLanguageDataTest {
    private fun state(src: String): EditorState {
        val pos = src.indexOf('|')
        return EditorState.create(
            EditorStateConfig(
                doc = src.removeRange(pos, pos + 1).asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(pos)),
                extensions = ExtensionList(listOf(html().extension, indentOnInput))
            )
        )
    }

    private fun type(src: String, text: String): String {
        val start = state(src)
        val pos = start.selection.main.head
        val next = start.update(
            TransactionSpec(
                changes = ChangeSpec.Single(pos, pos, text.asInsert()),
                selection = SelectionSpec.CursorSpec(pos + text.length),
                userEvent = "input.type"
            )
        ).state
        val doc = next.doc.toString()
        val head = next.selection.main.head.value
        return doc.substring(0, head) + "|" + doc.substring(head)
    }

    private fun word(src: String): String? {
        val st = state(src)
        return st.wordAt(st.selection.main.head)?.let { st.doc.sliceString(it.from, it.to) }
    }

    @Test
    fun typingATriggerReindentsTheLine() {
        assertEquals("<div>\n  <p>x</p>\n</div>|", type("<div>\n  <p>x</p>\n  </div|", ">"))
    }

    @Test
    fun wordAtUsesTheLanguageWordChars() {
        assertEquals("my-element", word("<my-elem|ent>"))
        // Inside a nested language, that language's data applies.
        assertEquals("\$foo", word("<script>let \$fo|o = 1</script>"))
    }

    @Test
    fun scriptContentUsesTheScriptsData() {
        // Inside the script body, on the side facing into it. npm also gives "$" at the outer
        // sides of both edges (8/-1 and 13/+1) by entering the mount with
        // IterMode.EnterBracketed; that needs html's script mount marked bracketed (#474), so
        // those two positions are left until it lands.
        val st = EditorState.create(
            EditorStateConfig(doc = "<script>let a</script>".asDoc(), extensions = html().extension)
        )
        val wordChars = listOf(8 to 1, 10 to -1, 13 to -1).map { (pos, side) ->
            st.languageDataAt(LanguageDataKey.WORD_CHARS, DocPos(pos), side)
        }
        assertEquals(List(3) { listOf("\$") }, wordChars)
    }

    @Test
    fun wordCharsReachPastTheTreeBuffers() {
        // Large enough that the tag sits in a subtree, not a flat buffer, below the top node.
        val filler = "<p>filler text here</p>\n".repeat(200)
        assertEquals("my-element", word(filler + "<my-ele|ment>" + filler))
    }
}
