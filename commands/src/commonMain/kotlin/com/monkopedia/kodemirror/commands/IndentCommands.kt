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

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorView

/**
 * Facet that configures the indent unit (number of spaces per indent level).
 * Defaults to 4 spaces.
 */
val indentUnit: Facet<Int, Int> = Facet.define(
    combine = { values -> values.firstOrNull() ?: 4 }
)

/**
 * Get the indent unit string for the given state (spaces based on the
 * [indentUnit] facet).
 */
fun getIndentUnit(state: EditorState): String {
    val size = state.facet(indentUnit)
    return " ".repeat(size)
}

/**
 * Add one level of indentation to each line in the selection.
 */
val indentMore: (EditorView) -> Boolean = { view ->
    changeIndent(view, add = true)
}

/**
 * Remove one level of indentation from each line in the selection.
 */
val indentLess: (EditorView) -> Boolean = { view ->
    changeIndent(view, add = false)
}

private fun changeIndent(view: EditorView, add: Boolean): Boolean {
    val state = view.state
    if (state.readOnly) return false

    val indent = getIndentUnit(state)
    val sel = state.selection.main
    val startLine = state.doc.lineAt(sel.from)
    val endLine = state.doc.lineAt(sel.to)

    val changes = mutableListOf<ChangeSpec>()

    for (lineNum in startLine.number..endLine.number) {
        val line = state.doc.line(lineNum)
        if (add) {
            changes.add(
                ChangeSpec.Single(
                    line.from,
                    line.from,
                    InsertContent.StringContent(indent)
                )
            )
        } else {
            val text = line.text
            val removeLen = when {
                text.startsWith(indent) -> indent.length
                text.startsWith("\t") -> 1
                else -> {
                    // Remove as many leading spaces as possible (up to indent size)
                    val spaces = text.takeWhile { it == ' ' }.length
                    spaces.coerceAtMost(indent.length)
                }
            }
            if (removeLen > 0) {
                changes.add(ChangeSpec.Single(line.from, line.from + removeLen))
            }
        }
    }

    if (changes.isEmpty()) return false

    view.dispatch(
        TransactionSpec(
            changes = ChangeSpec.Multi(changes),
            scrollIntoView = true,
            userEvent = if (add) "indent.more" else "indent.less",
            annotations = listOf(Transaction.addToHistory.of(true))
        )
    )
    return true
}
