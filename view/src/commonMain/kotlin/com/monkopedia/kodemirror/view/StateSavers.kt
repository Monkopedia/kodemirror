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
package com.monkopedia.kodemirror.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorSelectionData
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.EditorStateData
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.SelectionRange
import com.monkopedia.kodemirror.state.SelectionRangeData
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateJson
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.fromData
import com.monkopedia.kodemirror.state.toData
import com.monkopedia.kodemirror.state.toEditorSelection
import com.monkopedia.kodemirror.state.toSelectionRange
import kotlinx.serialization.encodeToString

/**
 * A [Saver] for [SelectionRange] that serializes to JSON.
 */
val SelectionRangeSaver: Saver<SelectionRange, String> = Saver(
    save = { range ->
        StateJson.encodeToString(range.toData())
    },
    restore = { json ->
        StateJson.decodeFromString<SelectionRangeData>(json)
            .toSelectionRange()
    }
)

/**
 * A [Saver] for [EditorSelection] that serializes to JSON.
 */
val EditorSelectionSaver: Saver<EditorSelection, String> = Saver(
    save = { selection ->
        StateJson.encodeToString(selection.toData())
    },
    restore = { json ->
        StateJson.decodeFromString<EditorSelectionData>(json)
            .toEditorSelection()
    }
)

/**
 * A [Saver] for [Text] that serializes to its string representation.
 */
val TextSaver: Saver<Text, String> = Saver(
    save = { text -> text.toString() },
    restore = { str -> Text.of(str.split("\n")) }
)

/**
 * Create a [Saver] for [EditorState] that preserves the document and selection.
 *
 * @param extensions Extensions to apply when restoring (must match the
 *   original state's extensions for correct behavior).
 * @param fields Map of name → [StateField] for custom fields to
 *   save/restore. Only fields with serialization configured will be included.
 */
@Suppress("FunctionName")
fun EditorStateSaver(
    extensions: Extension? = null,
    fields: Map<String, StateField<*>>? = null
): Saver<EditorState, String> = Saver(
    save = { state ->
        StateJson.encodeToString(state.toData(fields))
    },
    restore = { json ->
        EditorState.fromData(
            data = StateJson.decodeFromString<EditorStateData>(json),
            config = EditorStateConfig(extensions = extensions),
            fields = fields
        )
    }
)

/**
 * Create a [Saver] for [EditorSession] that preserves the document and
 * selection across configuration changes and process death.
 *
 * @param extensions Extensions to apply when restoring.
 * @param fields Map of name → [StateField] for custom fields to save/restore.
 * @param onUpdate Optional callback invoked after each transaction.
 */
@Suppress("FunctionName")
fun EditorSessionSaver(
    extensions: Extension? = null,
    fields: Map<String, StateField<*>>? = null,
    onUpdate: (com.monkopedia.kodemirror.state.Transaction) -> Unit = {}
): Saver<EditorSession, String> = Saver(
    save = { session ->
        StateJson.encodeToString(session.state.toData(fields))
    },
    restore = { json ->
        val state = EditorState.fromData(
            data = StateJson.decodeFromString<EditorStateData>(json),
            config = EditorStateConfig(extensions = extensions),
            fields = fields
        )
        EditorSession(state, onUpdate)
    }
)

/**
 * Create and remember an [EditorSession] that survives configuration
 * changes and process death via [rememberSaveable].
 *
 * On first composition, creates a new session with [doc] and [extensions].
 * On restoration, the document text and selection are preserved.
 *
 * @param doc Initial document text (only used on first creation).
 * @param extensions Extensions for the editor.
 * @param fields Map of name → [StateField] for custom fields to persist.
 * @param onUpdate Optional callback invoked after each transaction.
 */
@Composable
fun rememberSaveableEditorSession(
    doc: String = "",
    extensions: Extension? = null,
    fields: Map<String, StateField<*>>? = null,
    onUpdate: (com.monkopedia.kodemirror.state.Transaction) -> Unit = {}
): EditorSession = rememberSaveable(
    saver = EditorSessionSaver(extensions, fields, onUpdate)
) {
    EditorSession(
        EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                extensions = extensions
            )
        ),
        onUpdate
    )
}
