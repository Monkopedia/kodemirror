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
package com.monkopedia.kodemirror.test

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession

/**
 * Create an [EditorSession] for testing purposes.
 *
 * This is the recommended way to create editor sessions in tests.
 * It wraps the common boilerplate of creating an [EditorState] and
 * [EditorSession].
 *
 * @param doc The initial document content.
 * @param cursor The initial cursor position.
 * @param extensions Extensions to configure the editor with.
 */
fun testEditorSession(
    doc: String = "",
    cursor: DocPos = DocPos.ZERO,
    extensions: Extension? = null
): EditorSession {
    val state = EditorState.create(
        EditorStateConfig(
            doc = doc.asDoc(),
            selection = SelectionSpec.CursorSpec(cursor),
            extensions = extensions
        )
    )
    return EditorSession(state)
}
