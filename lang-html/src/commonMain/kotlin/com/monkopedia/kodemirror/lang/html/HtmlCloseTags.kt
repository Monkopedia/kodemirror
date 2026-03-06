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
package com.monkopedia.kodemirror.lang.html

import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionFilterResult
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.transactionFilter

private val selfClosingTags = setOf(
    "area", "base", "br", "col", "command",
    "embed", "frame", "hr", "img", "input",
    "keygen", "link", "meta", "param", "source",
    "track", "wbr", "menuitem"
)

/**
 * Extension that automatically inserts close tags when `>` or `/` is
 * typed in an HTML document.
 *
 * When the user types `>` at the end of an open tag like `<div`,
 * this inserts `</div>` after the cursor. Self-closing tags (like
 * `<br>`, `<img>`, etc.) are not auto-closed.
 *
 * When the user types `/` after `<` to start a closing tag, this
 * completes the tag name from the nearest unclosed open tag.
 */
val autoCloseTags: Extension = transactionFilter.of { tr ->
    if (!tr.docChanged || !tr.isUserEvent("input.type")) {
        return@of TransactionFilterResult.Filtered(tr)
    }

    val state = tr.startState
    val doc = state.doc
    val sel = state.selection.main
    if (!sel.empty || state.selection.ranges.size > 1) {
        return@of TransactionFilterResult.Filtered(tr)
    }

    val insertedText = getInsertedText(tr.changes)
    if (insertedText == null || insertedText.length != 1) {
        return@of TransactionFilterResult.Filtered(tr)
    }

    val pos = sel.head
    val ch = insertedText[0]

    when (ch) {
        '>' -> handleCloseAngle(tr, pos, state, doc)
        '/' -> handleSlash(tr, pos, state, doc)
        else -> TransactionFilterResult.Filtered(tr)
    }
}

private fun handleCloseAngle(
    tr: Transaction,
    pos: Int,
    state: com.monkopedia.kodemirror.state.EditorState,
    doc: Text
): TransactionFilterResult {
    val tree = syntaxTree(state)
    val after = tree.resolveInner(pos, -1)

    // Look for EndTag (the `>` at the end of an open tag)
    if (after.name != "EndTag") return TransactionFilterResult.Filtered(tr)

    val tag = after.parent ?: return TransactionFilterResult.Filtered(tr)
    val element = tag.parent ?: return TransactionFilterResult.Filtered(tr)

    // Check that this element doesn't already have a CloseTag
    if (element.lastChild?.name == "CloseTag") {
        return TransactionFilterResult.Filtered(tr)
    }

    val name = elementName(doc, element, pos)
    if (name.isEmpty() || selfClosingTags.contains(name)) {
        return TransactionFilterResult.Filtered(tr)
    }

    val insertText = "></$name>"
    return TransactionFilterResult.Specs(
        listOf(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    pos,
                    pos,
                    InsertContent.StringContent(insertText)
                ),
                selection = SelectionSpec.CursorSpec(pos + 1),
                userEvent = "input.type"
            )
        )
    )
}

private fun handleSlash(
    tr: Transaction,
    pos: Int,
    state: com.monkopedia.kodemirror.state.EditorState,
    doc: Text
): TransactionFilterResult {
    val tree = syntaxTree(state)
    val after = tree.resolveInner(pos, -1)

    // Look for IncompleteCloseTag (the `</` that starts a close tag)
    if (after.name != "IncompleteCloseTag") {
        return TransactionFilterResult.Filtered(tr)
    }

    val tag = after.parent ?: return TransactionFilterResult.Filtered(tr)

    // Make sure we're at the right position (just typed `/` after `<`)
    if (after.from != pos - 1) return TransactionFilterResult.Filtered(tr)

    // Check that this element doesn't already have a CloseTag
    if (tag.lastChild?.name == "CloseTag") {
        return TransactionFilterResult.Filtered(tr)
    }

    val name = elementName(doc, tag, pos)
    if (name.isEmpty() || selfClosingTags.contains(name)) {
        return TransactionFilterResult.Filtered(tr)
    }

    val insertText = "/$name>"
    return TransactionFilterResult.Specs(
        listOf(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    pos,
                    pos,
                    InsertContent.StringContent(insertText)
                ),
                selection = SelectionSpec.CursorSpec(pos + insertText.length),
                userEvent = "input.type"
            )
        )
    )
}

/**
 * Extract the element name from an Element node by finding the first
 * child's TagName.
 */
private fun elementName(
    doc: Text,
    tree: com.monkopedia.kodemirror.lezer.common.SyntaxNode?,
    max: Int = doc.length
): String {
    if (tree == null) return ""
    val tag = tree.firstChild ?: return ""
    val name = tag.getChild("TagName") ?: return ""
    return doc.sliceString(name.from, minOf(name.to, max))
}

private fun getInsertedText(changes: com.monkopedia.kodemirror.state.ChangeSet): String? {
    var result: String? = null
    changes.iterChanges(
        f = { _, _, _, _, inserted ->
            val text = inserted.sliceString(0)
            if (text.isNotEmpty()) {
                result = text
            }
        }
    )
    return result
}
