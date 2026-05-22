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

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.lsp.Position

/**
 * Move the user to [targetUri] at the document offset corresponding to LSP
 * [position], reusing the navigation tail shared by go-to-definition and
 * find-references.
 *
 * Ports the navigation step of upstream `@codemirror/lsp-client`'s
 * `jumpToOrigin` / reference-panel `showReference`:
 * - **Same file** ([targetUri] equals [sourceUri]): resolve the position against
 *   the source file's current document and move its selection.
 * - **Cross file:** ask the [Workspace] to surface the target file
 *   ([Workspace.displayFile]); if it returns a session, move that session's
 *   selection (resolved against *that* session's document).
 *
 * In both cases the move scrolls the target into view. Resolving the LSP
 * [position] against the *current* document (rather than the version the server
 * saw) keeps the jump correct under concurrent edits, matching the position
 * mapping upstream performs.
 *
 * **Workspace limitation:** the default [Workspace] is single-file — its
 * [displayFile][Workspace.displayFile] only returns a session for a file that is
 * already open. For such a workspace a cross-file navigation degrades gracefully
 * to a no-op. To support real cross-file navigation a consumer must subclass
 * [Workspace] and override [displayFile][Workspace.displayFile].
 *
 * @param client The LSP client whose [workspace][LSPClient.workspace] resolves
 *   the target file.
 * @param sourceUri The URI of the file the navigation originates from (used to
 *   detect the same-file case).
 * @param targetUri The URI of the file to navigate to.
 * @param position The LSP position within [targetUri] to move the cursor to.
 * @param userEvent The `userEvent` annotation on the dispatched selection move.
 */
internal fun navigateToLocation(
    client: LSPClient,
    sourceUri: String,
    targetUri: String,
    position: Position,
    userEvent: String
) {
    val workspace = client.workspace
    val targetSession: EditorSession? = if (targetUri == sourceUri) {
        workspace.getFile(sourceUri)?.session
    } else {
        workspace.displayFile(targetUri)
    }
    if (targetSession == null) return
    val offset = fromPosition(position, targetSession.state.doc)
    targetSession.dispatch(
        TransactionSpec(
            selection = SelectionSpec.CursorSpec(DocPos(offset)),
            scrollIntoView = true,
            userEvent = userEvent
        )
    )
}
