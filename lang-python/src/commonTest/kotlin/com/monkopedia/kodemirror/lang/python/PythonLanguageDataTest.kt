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

import com.monkopedia.kodemirror.language.indentOnInput
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.LanguageDataKey
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The language's published language data in use (#449). Typing an `indentOnInput`
 * trigger is not tested here: the reindent reads a syntax tree that does not yet contain the typed
 * text (the tree only advances in the view's parse worker), which this language's indent rules
 * need. `|` marks the cursor; expected values come from the upstream package run on the
 * same input.
 */
class PythonLanguageDataTest {
    private fun state(src: String): EditorState {
        val pos = src.indexOf('|')
        return EditorState.create(
            EditorStateConfig(
                doc = src.removeRange(pos, pos + 1).asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(pos)),
                extensions = ExtensionList(listOf(python().extension, indentOnInput))
            )
        )
    }

    @Test
    fun indentOnInputRuleIsPublished() {
        val st = state("x|")
        assertEquals(
            1,
            st.languageDataAt(LanguageDataKey.INDENT_ON_INPUT, st.selection.main.head).size
        )
    }
}
