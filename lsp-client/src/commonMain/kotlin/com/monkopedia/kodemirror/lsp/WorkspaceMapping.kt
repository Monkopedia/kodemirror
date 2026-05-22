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
package com.monkopedia.kodemirror.lsp

import com.monkopedia.kodemirror.state.ChangeDesc
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.lsp.Position

/**
 * Maps positions from the document version a request was issued against to the
 * current document version.
 *
 * Ports upstream `@codemirror/lsp-client`'s `WorkspaceMapping`. An LSP request
 * is sent against a particular document version; by the time the response comes
 * back the editor may have moved on through further edits. Positions returned
 * by the server are expressed against the version the request saw, so they must
 * be mapped through the edits that happened in the meantime before they are used
 * in the (now-current) editor.
 *
 * Obtain one via [Workspace.getMapping]. The mapping is a live view: it tracks
 * edits applied to the file after it was created, so a request handler can
 * capture the mapping when it issues the request and use it once the response
 * arrives.
 *
 * @param uri The URI of the file whose positions this mapping translates.
 */
class WorkspaceMapping internal constructor(
    val uri: String
) {
    private var changes: ChangeDesc? = null

    /**
     * Record that [change] was applied to the file after this mapping was
     * created. Composed onto any previously-recorded changes.
     */
    internal fun addChanges(change: ChangeDesc) {
        if (change.empty) return
        val existing = changes
        changes = if (existing == null) change else existing.composeDesc(change)
    }

    /**
     * Map a document offset issued against the request-time document to the
     * current document.
     *
     * @param pos The offset in the request-time document.
     * @param assoc Which side of an insertion the position prefers
     *   (negative biases left, positive biases right). Defaults to `-1`.
     */
    fun mapPos(pos: Int, assoc: Int = -1): Int = changes?.mapPos(DocPos(pos), assoc)?.value ?: pos

    /**
     * Map an LSP [Position] from the request-time document to a current-document
     * offset.
     *
     * The [lspPos] is interpreted against [prevDoc] (the document as the request
     * saw it), converted to an offset, then mapped through any edits recorded
     * since this mapping was created.
     *
     * @param lspPos The LSP position from the request-time document.
     * @param prevDoc The document the request was issued against.
     * @param assoc Association bias for the mapping. Defaults to `-1`.
     */
    fun mapPosition(lspPos: Position, prevDoc: Text, assoc: Int = -1): Int =
        mapPos(fromPosition(lspPos, prevDoc), assoc)
}
