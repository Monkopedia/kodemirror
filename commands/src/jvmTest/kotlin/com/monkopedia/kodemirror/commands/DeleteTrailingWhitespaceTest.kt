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
package com.monkopedia.kodemirror.commands

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ported from upstream `@codemirror/commands` `test/test-commands.ts`
 * (`deleteTrailingWhitespace`). Part of #116 — previously uncovered.
 */
class DeleteTrailingWhitespaceTest {

    private fun run(input: String): String {
        val v = EditorSession(
            EditorState.create(
                EditorStateConfig(
                    doc = input.asDoc(),
                    selection = SelectionSpec.CursorSpec(DocPos(0))
                )
            )
        )
        deleteTrailingWhitespace(v)
        return v.state.doc.toString()
    }

    @Test fun deletesTrailingWhitespace() = assertEquals("foo", run("foo   "))

    @Test fun checksMultipleLines() =
        assertEquals("one\ntwo\nthree\n", run("one\ntwo \nthree   \n   "))

    @Test fun handlesEmptyLines() = assertEquals("one\n\ntwo", run("one  \n\ntwo "))
}
