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

import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionFilterResult
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.transactionFilter

/**
 * Extension that auto-closes JSX tags when typing `>` or completes
 * closing tags when typing `/`.
 *
 * When the user types `>` at the end of a JSX open tag like `<div`,
 * this inserts `</div>` and places the cursor between the tags.
 *
 * When the user types `/` inside a `<` that starts a close tag, this
 * completes the closing tag name based on the nearest open tag.
 */
fun autoCloseTags(): Extension = transactionFilter.of { tr ->
    if (!tr.docChanged || !tr.isUserEvent("input.type")) {
        return@of TransactionFilterResult.Filtered(tr)
    }

    val state = tr.startState
    val doc = state.doc
    val sel = state.selection.main
    if (!sel.empty || state.selection.ranges.size > 1) {
        return@of TransactionFilterResult.Filtered(tr)
    }

    val insertedText = getInsertedText(tr.changes, doc.length)
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
    pos: DocPos,
    state: com.monkopedia.kodemirror.state.EditorState,
    doc: Text
): TransactionFilterResult {
    val tree = syntaxTree(state)
    val node = tree.resolveInner(pos.value, -1)

    // Check if we're inside a JSXOpenTag or JSXFragmentTag
    val openTag = findAncestor(node, "JSXOpenTag")
        ?: findAncestor(node, "JSXFragmentTag")
        ?: return TransactionFilterResult.Filtered(tr)

    val name = elementName(openTag, doc)
    if (name == null) {
        // Fragment: insert ></>
        val insertText = "></>"
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
    pos: DocPos,
    state: com.monkopedia.kodemirror.state.EditorState,
    doc: Text
): TransactionFilterResult {
    val tree = syntaxTree(state)
    val node = tree.resolveInner(pos.value, -1)

    // Check if we're inside a JSXStartCloseTag (the `</` in a closing tag)
    val closeTag = findAncestor(node, "JSXStartCloseTag")
    if (closeTag != null) {
        // Find the matching open tag
        val jsxElement = findAncestor(closeTag, "JSXElement")
        if (jsxElement != null) {
            val openChild = jsxElement.firstChild
            if (openChild != null &&
                (openChild.name == "JSXOpenTag" || openChild.name == "JSXFragmentTag")
            ) {
                val name = elementName(openChild, doc)
                if (name != null) {
                    val insertText = "/$name>"
                    return TransactionFilterResult.Specs(
                        listOf(
                            TransactionSpec(
                                changes = ChangeSpec.Single(
                                    pos,
                                    pos,
                                    InsertContent.StringContent(insertText)
                                ),
                                selection = SelectionSpec.CursorSpec(
                                    pos + insertText.length
                                ),
                                userEvent = "input.type"
                            )
                        )
                    )
                }
            }
        }
    }

    return TransactionFilterResult.Filtered(tr)
}

/**
 * Extract the element name from a JSX open or close tag node by
 * looking for JSXIdentifier, JSXBuiltin, JSXNamespacedName, or
 * JSXMemberExpression children.
 */
private fun elementName(node: SyntaxNode, doc: Text): String? {
    var child = node.firstChild
    while (child != null) {
        when (child.name) {
            "JSXIdentifier", "JSXBuiltin", "JSXNamespacedName",
            "JSXMemberExpression" -> {
                return doc.sliceString(DocPos(child.from), DocPos(child.to))
            }
        }
        child = child.nextSibling
    }
    return null
}

private fun findAncestor(node: SyntaxNode, name: String): SyntaxNode? {
    var current: SyntaxNode? = node
    while (current != null) {
        if (current.name == name) return current
        current = current.parent
    }
    return null
}

private fun getInsertedText(
    changes: com.monkopedia.kodemirror.state.ChangeSet,
    docLength: Int
): String? {
    var result: String? = null
    changes.iterChanges(
        f = { _, _, _, _, inserted ->
            val text = inserted.sliceString(DocPos.ZERO)
            if (text.isNotEmpty()) {
                result = text
            }
        }
    )
    return result
}
