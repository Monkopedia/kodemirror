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

import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder

/**
 * Extension that highlights the line containing the primary cursor.
 *
 * Adds a [LineDecoration] with [EditorTheme.activeLineStyle] to the active
 * line on each view update.
 */
val highlightActiveLine: Extension = ViewPlugin.define(
    create = { view -> ActiveLinePlugin(view) },
    configure = {
        copy(
            decorations = { plugin ->
                (plugin as? ActiveLinePlugin)?.decos ?: RangeSet.empty()
            }
        )
    }
).asExtension()

private class ActiveLinePlugin(view: EditorSession) : PluginValue {
    var decos: DecorationSet = buildDecos(view)

    override fun update(update: ViewUpdate) {
        if (update.docChanged || update.selectionSet) {
            decos = buildDecos(update.session)
        }
    }

    private fun buildDecos(view: EditorSession): DecorationSet {
        val state = view.state
        val theme = state.facet(editorTheme)
        val builder = RangeSetBuilder<Decoration>()
        val activeLine = state.doc.lineAt(state.selection.main.head)
        builder.add(
            activeLine.from,
            activeLine.from,
            Decoration.line(LineDecorationSpec(style = theme.activeLineStyle()))
        )
        return builder.finish()
    }
}
