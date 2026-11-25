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
package com.monkopedia.kodemirror.lang.vue

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
 * The language data this wrapper publishes, which comes from the language it wraps (it keeps
 * that language's data facet, as upstream's `LRLanguage.configure` does) plus the data its
 * support adds (#449). Expected values come from @codemirror/lang-vue run on the same input.
 */
class VueLanguageDataTest {
    private fun state(doc: String) =
        EditorState.create(EditorStateConfig(doc = doc.asDoc(), extensions = vue().extension))

    private fun data(
        doc: String,
        pos: Int
    ): Triple<List<String>, List<String>, List<Map<String, List<String>>>> {
        val st = state(doc)
        return Triple(
            st.languageDataAt(LanguageDataKey.INDENT_ON_INPUT, DocPos(pos)).map { it.pattern },
            st.languageDataAt(LanguageDataKey.WORD_CHARS, DocPos(pos)),
            st.languageDataAt(LanguageDataKey.CLOSE_BRACKETS, DocPos(pos))
        )
    }

    @Test
    fun publishesHtmlDataWithVueBrackets() {
        assertEquals(
            Triple(
                listOf("""^\s*<\/\w+\W$"""),
                listOf("-_"),
                listOf(
                    mapOf(
                        "brackets" to listOf("{", "\"")
                    )
                )
            ),
            data("<div>{{ a }}</div>", 0)
        )
    }

    @Test
    fun typingAClosingTagReindents() {
        val src = "<div>\n  <p>x</p>\n  </div"
        val start = EditorState.create(
            EditorStateConfig(
                doc = src.asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(src.length)),
                extensions = ExtensionList(listOf(vue().extension, indentOnInput))
            )
        )
        val end = DocPos(src.length)
        val next = start.update(
            TransactionSpec(
                changes = ChangeSpec.Single(end, end, ">".asInsert()),
                selection = SelectionSpec.CursorSpec(end + 1),
                userEvent = "input.type"
            )
        ).state
        assertEquals("<div>\n  <p>x</p>\n</div>", next.doc.toString())
    }
}
