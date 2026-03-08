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
package com.monkopedia.kodemirror.autocomplete

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionFilterResult
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.transactionFilter
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.keymap

/**
 * Configuration for close-brackets behavior.
 *
 * @param brackets The bracket/quote pairs to auto-close.
 * @param before Characters before which a closing bracket may be inserted.
 */
data class CloseBracketsConfig(
    val brackets: String = "()[]{}''\"\"",
    val before: String = ")]}:;>"
)

private val defaultPairs = mapOf(
    '(' to ')',
    '[' to ']',
    '{' to '}',
    '\'' to '\'',
    '"' to '"'
)

/**
 * Extension that auto-closes brackets and quotes.
 *
 * When the user types an opening bracket, the corresponding closing
 * bracket is inserted after the cursor. When the user types a closing
 * bracket that's already present after the cursor, it skips over it.
 */
fun closeBrackets(config: CloseBracketsConfig = CloseBracketsConfig()): Extension {
    val pairs = parsePairs(config.brackets)
    val beforeChars = config.before.toSet()

    val filter = transactionFilter.of { tr ->
        if (!tr.docChanged || tr.isUserEvent("input.complete")) {
            return@of TransactionFilterResult.Filtered(tr)
        }

        // Check if this is a single character input
        if (!tr.isUserEvent("input.type") && !tr.isUserEvent("input")) {
            return@of TransactionFilterResult.Filtered(tr)
        }

        val state = tr.startState
        val changes = tr.changes
        val doc = state.doc

        // Simple case: single cursor, single insertion
        val sel = state.selection.main
        if (!sel.empty || state.selection.ranges.size > 1) {
            return@of TransactionFilterResult.Filtered(tr)
        }

        val pos = sel.head
        val insertedText = getInsertedText(changes, doc.length)
        if (insertedText == null || insertedText.length != 1) {
            return@of TransactionFilterResult.Filtered(tr)
        }

        val ch = insertedText[0]
        val closeBracket = pairs[ch]

        if (closeBracket != null) {
            // Opening bracket: check if we should auto-close
            val afterChar = if (pos < doc.length) {
                doc.sliceString(pos, pos + 1).firstOrNull()
            } else {
                null
            }
            val shouldClose = afterChar == null ||
                beforeChars.contains(afterChar) ||
                afterChar.isWhitespace()
            if (shouldClose) {
                val insertText = "$ch$closeBracket"
                return@of TransactionFilterResult.Specs(
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
        }

        // Skip over closing bracket if it's already there
        if (pairs.values.contains(ch)) {
            val afterChar = if (pos < doc.length) {
                doc.sliceString(pos, pos + 1).firstOrNull()
            } else {
                null
            }
            if (afterChar == ch) {
                return@of TransactionFilterResult.Specs(
                    listOf(
                        TransactionSpec(
                            selection = SelectionSpec.CursorSpec(pos + 1),
                            userEvent = "input.type"
                        )
                    )
                )
            }
        }

        TransactionFilterResult.Filtered(tr)
    }

    return ExtensionList(listOf(filter, keymap.of(closeBracketsKeymap)))
}

/** Delete bracket pair command: when cursor is between a pair, delete both. */
val deleteBracketPair: (EditorSession) -> Boolean = { view ->
    val state = view.state
    val pos = state.selection.main.head
    val result = if (pos > 0 && pos < state.doc.length) {
        val before = state.doc.sliceString(pos - 1, pos)
        val after = state.doc.sliceString(pos, pos + 1)
        val isPair = before.length == 1 && after.length == 1 &&
            defaultPairs[before[0]] == after[0]
        if (isPair) {
            view.dispatch(
                TransactionSpec(
                    changes = ChangeSpec.Single(pos - 1, pos + 1),
                    selection = SelectionSpec.CursorSpec(pos - 1),
                    userEvent = "delete.backward"
                )
            )
            true
        } else {
            false
        }
    } else {
        false
    }
    result
}

/** Keymap for close-brackets: Backspace deletes bracket pair. */
val closeBracketsKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Backspace", run = deleteBracketPair)
)

/** Programmatic bracket insertion. */
fun insertBracket(state: EditorState, bracket: String): TransactionSpec {
    val pos = state.selection.main.head
    val close = defaultPairs[bracket.firstOrNull()] ?: ""
    val text = bracket + close
    return TransactionSpec(
        changes = ChangeSpec.Single(pos, pos, InsertContent.StringContent(text)),
        selection = SelectionSpec.CursorSpec(pos + bracket.length),
        userEvent = "input.type"
    )
}

private fun parsePairs(brackets: String): Map<Char, Char> {
    val result = mutableMapOf<Char, Char>()
    var i = 0
    while (i + 1 < brackets.length) {
        result[brackets[i]] = brackets[i + 1]
        i += 2
    }
    return result
}

private fun getInsertedText(
    changes: com.monkopedia.kodemirror.state.ChangeSet,
    docLength: Int
): String? {
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
