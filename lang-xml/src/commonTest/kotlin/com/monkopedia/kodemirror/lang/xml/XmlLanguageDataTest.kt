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
package com.monkopedia.kodemirror.lang.xml

import com.monkopedia.kodemirror.language.indentOnInput
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
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
class XmlLanguageDataTest {
    private fun state(src: String): EditorState {
        val pos = src.indexOf('|')
        return EditorState.create(
            EditorStateConfig(
                doc = src.removeRange(pos, pos + 1).asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(pos)),
                extensions = ExtensionList(listOf(xml().extension, indentOnInput))
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

    @Test
    fun typingATriggerReindentsTheLine() {
        assertEquals("<a>\n  <b/>\n</|", type("<a>\n  <b/>\n  <|", "/"))
    }
}
