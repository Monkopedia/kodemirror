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

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder

/**
 * Create an extension that shows [text] as placeholder text when the document
 * is empty.
 */
fun placeholder(text: String): Extension = placeholder {
    val theme = LocalEditorTheme.current
    BasicText(
        text = text,
        style = theme.contentTextStyle.copy(color = theme.gutterForeground),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Create an extension that shows a composable widget as placeholder content
 * when the document is empty.
 */
fun placeholder(content: @Composable () -> Unit): Extension = ViewPlugin.define(
    create = { view -> PlaceholderPlugin(view, content) },
    configure = {
        copy(
            decorations = { plugin ->
                (plugin as? PlaceholderPlugin)?.decos ?: RangeSet.empty()
            }
        )
    }
).asExtension()

private class PlaceholderPlugin(
    view: EditorSession,
    private val content: @Composable () -> Unit
) : PluginValue {
    var decos: DecorationSet = buildDecos(view)

    override fun update(update: ViewUpdate) {
        if (update.docChanged) {
            decos = buildDecos(update.session)
        }
    }

    private fun buildDecos(view: EditorSession): DecorationSet {
        if (view.state.doc.length > 0) return RangeSet.empty()
        val builder = RangeSetBuilder<Decoration>()
        val widget = object : WidgetType() {
            @Composable
            override fun Content() = content()
            override fun equals(other: Any?): Boolean = other === this
        }
        builder.add(
            DocPos.ZERO,
            DocPos.ZERO,
            Decoration.widget(WidgetDecorationSpec(widget = widget, side = 1))
        )
        return builder.finish()
    }
}
