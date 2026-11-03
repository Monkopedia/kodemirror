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
package com.monkopedia.kodemirror.lang.javascript

import com.monkopedia.kodemirror.autocomplete.CompletionApplyContext
import com.monkopedia.kodemirror.autocomplete.snippetState
import com.monkopedia.kodemirror.autocomplete.snippets as snippetSupport
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JsSnippetsTest {

    @Test
    fun forLoopSnippetAppliesOverTypedPrefix() {
        // #383: accepting this over "fo" threw "Position 9 is out of range for changeset of
        // length 2". Expected text/selection from @codemirror/lang-javascript's snippet.
        val session = EditorSession(
            EditorState.create(
                EditorStateConfig(
                    doc = "fo".asDoc(),
                    selection = SelectionSpec.CursorSpec(DocPos(2)),
                    extensions = ExtensionList(listOf(javascript().extension, snippetSupport()))
                )
            )
        )
        val forLoop = snippets.first { it.label == "for" && it.detail == "loop" }
        val apply = assertNotNull(forLoop.applyFn)
        apply(CompletionApplyContext(session, forLoop, DocPos(0), DocPos(2)))
        assertEquals(
            "for (let index = 0; index < bound; index++) {\n  \n}",
            session.state.doc.toString()
        )
        val main = session.state.selection.main
        assertEquals(DocPos(9) to DocPos(14), main.anchor to main.head)
        assertNotNull(session.state.field(snippetState, require = false))
    }
}
