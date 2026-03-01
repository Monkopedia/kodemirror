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
package com.monkopedia.kodemirror.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.MarkDecorationSpec
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate

/** Configuration for selection match highlighting. */
data class HighlightSelectionMatchConfig(
    val minSelectionLength: Int = 1,
    val maxMatches: Int = 100,
    val wholeWords: Boolean = false
)

private val selectionMatchStyle = SpanStyle(
    background = Color(0x30A0A0FF)
)

private val selectionMatchDeco = Decoration.mark(
    MarkDecorationSpec(
        style = selectionMatchStyle,
        cssClass = "cm-selectionMatch"
    )
)

/**
 * Extension that highlights all occurrences of the currently selected text.
 *
 * When the user selects a non-empty text range, this plugin finds and
 * decorates all matching occurrences throughout the document.
 */
fun highlightSelectionMatches(
    config: HighlightSelectionMatchConfig = HighlightSelectionMatchConfig()
): Extension {
    return ViewPlugin.define(
        create = { view ->
            SelectionMatchPlugin(view.state, config)
        },
        configure = {
            copy(
                decorations = { plugin ->
                    (plugin as? SelectionMatchPlugin)?.decos ?: RangeSet.empty()
                }
            )
        }
    ).asExtension()
}

private class SelectionMatchPlugin(
    state: EditorState,
    private val config: HighlightSelectionMatchConfig
) : PluginValue {
    var decos: DecorationSet = buildDecos(state)

    override fun update(update: ViewUpdate) {
        if (update.docChanged || update.selectionSet) {
            decos = buildDecos(update.state)
        }
    }

    private fun buildDecos(state: EditorState): DecorationSet {
        val sel = state.selection.main
        if (sel.empty) return RangeSet.empty()

        val selectedText = state.doc.sliceString(sel.from, sel.to)
        if (selectedText.length < config.minSelectionLength) return RangeSet.empty()
        if (selectedText.contains('\n')) return RangeSet.empty()

        val cursor = SearchCursor(
            state.doc,
            selectedText,
            normalize = String::lowercase
        )

        val builder = RangeSetBuilder<Decoration>()
        var count = 0
        for (match in cursor) {
            if (count >= config.maxMatches) break
            // Skip the current selection itself
            if (match.from == sel.from && match.to == sel.to) continue
            builder.add(match.from, match.to, selectionMatchDeco)
            count++
        }

        return builder.finish()
    }
}
