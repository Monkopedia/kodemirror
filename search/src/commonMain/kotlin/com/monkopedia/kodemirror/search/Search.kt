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

import androidx.compose.ui.text.SpanStyle
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.Slot
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.LocalEditorSession
import com.monkopedia.kodemirror.view.MarkDecorationSpec
import com.monkopedia.kodemirror.view.Panel
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.editorTheme
import com.monkopedia.kodemirror.view.keymap
import com.monkopedia.kodemirror.view.showPanels

/**
 * Create an extension that provides search functionality.
 *
 * This bundles the search state fields, panel plugin, keymap, and
 * goto-line support.
 */
fun search(): Extension {
    val searchPanelProvider = showPanels.compute(
        listOf(
            Slot.FieldSlot(searchPanelOpenField),
            Slot.FieldSlot(gotoLinePanelOpenField)
        )
    ) { state ->
        buildList {
            if (state.field(searchPanelOpenField, require = false) == true) {
                add(
                    Panel(top = false) {
                        val view = LocalEditorSession.current
                        SearchPanel(view)
                    }
                )
            }
            if (state.field(gotoLinePanelOpenField, require = false) == true) {
                add(
                    Panel(top = true) {
                        val view = LocalEditorSession.current
                        GoToLinePanel(view)
                    }
                )
            }
        }
    }

    val highlightPlugin = ViewPlugin.define(
        create = { view ->
            SearchHighlightPlugin(view.state)
        },
        configure = {
            copy(
                decorations = { plugin ->
                    (plugin as? SearchHighlightPlugin)?.decos
                        ?: RangeSet.empty()
                }
            )
        }
    ).asExtension()

    return ExtensionList(
        listOf(
            searchQueryField,
            searchPanelOpenField,
            gotoLinePanelOpenField,
            searchPanelProvider,
            highlightPlugin,
            keymap.of(searchKeymap),
            keymap.of(listOf(KeyBinding(key = "Mod-l", run = gotoLine)))
        )
    )
}

private class SearchHighlightPlugin(
    state: EditorState
) : PluginValue {
    var decos: DecorationSet = buildDecos(state)

    override fun update(update: ViewUpdate) {
        if (update.docChanged || update.selectionSet ||
            update.transactions.any { tr ->
                tr.effects.any {
                    it.asType(toggleSearchPanel) != null || it.asType(setSearchQuery) != null
                }
            }
        ) {
            decos = buildDecos(update.state)
        }
    }

    private fun buildDecos(state: EditorState): DecorationSet {
        val panelOpen = state.field(
            searchPanelOpenField,
            require = false
        ) ?: false
        if (!panelOpen) return RangeSet.empty()

        val query = state.field(
            searchQueryField,
            require = false
        ) ?: SearchQuery()
        if (!query.valid) return RangeSet.empty()

        val theme = state.facet(editorTheme)
        val matchDeco = Decoration.mark(
            MarkDecorationSpec(
                style = SpanStyle(
                    background = theme.searchMatchBackground
                ),
                cssClass = "cm-searchMatch"
            )
        )
        val selectedDeco = Decoration.mark(
            MarkDecorationSpec(
                style = SpanStyle(
                    background = theme.searchMatchSelectedBackground
                ),
                cssClass = "cm-searchMatch-selected"
            )
        )

        val cursor = query.getCursor(state)
        val sel = state.selection.main
        val builder = RangeSetBuilder<Decoration>()
        for (match in cursor) {
            val isSelected =
                match.from == sel.from && match.to == sel.to
            builder.add(
                match.from,
                match.to,
                if (isSelected) selectedDeco else matchDeco
            )
        }
        return builder.finish()
    }
}
