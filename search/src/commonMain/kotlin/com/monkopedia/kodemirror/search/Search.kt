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

import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.Slot
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.LocalEditorView
import com.monkopedia.kodemirror.view.Panel
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
                    Panel(top = true) {
                        val view = LocalEditorView.current
                        SearchPanel(view)
                    }
                )
            }
            if (state.field(gotoLinePanelOpenField, require = false) == true) {
                add(
                    Panel(top = true) {
                        val view = LocalEditorView.current
                        GoToLinePanel(view)
                    }
                )
            }
        }
    }

    return ExtensionList(
        listOf(
            searchQueryField,
            searchPanelOpenField,
            gotoLinePanelOpenField,
            searchPanelProvider,
            keymap.of(searchKeymap),
            keymap.of(listOf(KeyBinding(key = "Mod-l", run = gotoLine)))
        )
    )
}
