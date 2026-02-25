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

import com.monkopedia.kodemirror.state.ChangeSet
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Transaction

/**
 * Describes an update to the editor.
 *
 * Created for every [Transaction] dispatched to [EditorView]. Plugins receive
 * this via [PluginValue.update].
 */
class ViewUpdate(
    /** The view that was updated. */
    val view: EditorView,
    /** The new editor state (after the transactions). */
    val state: EditorState,
    /** The transactions applied in this update (may be empty for view-only changes). */
    val transactions: List<Transaction>
) {
    /** The state before the transactions. */
    val startState: EditorState = view.state

    /** Whether any transaction changed the document. */
    val docChanged: Boolean
        get() = transactions.any { it.docChanged }

    /** Whether any transaction changed the selection. */
    val selectionSet: Boolean
        get() = transactions.any { it.selection != null }

    /**
     * The combined set of document changes from all transactions in this
     * update.
     */
    val changes: ChangeSet
        get() {
            if (transactions.isEmpty()) return ChangeSet.empty(startState.doc.length)
            var result = transactions[0].changes
            for (i in 1 until transactions.size) {
                result = result.compose(transactions[i].changes)
            }
            return result
        }

    /** Whether the viewport or visible ranges changed. */
    val viewportChanged: Boolean get() = false // computed by composable

    /** Whether the editor geometry (height/width) changed. */
    val heightChanged: Boolean get() = false // computed by composable

    /** Whether the editor became focused or unfocused. */
    val focusChanged: Boolean get() = false

    /**
     * The state configuration changed (extensions were reconfigured).
     *
     * Detected by checking whether any transaction carries a reconfigure effect.
     */
    val reconfigured: Boolean
        get() = transactions.any { it.reconfigured }
}
