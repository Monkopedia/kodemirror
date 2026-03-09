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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.endPos

/**
 * Extension that adds empty space after the last line of the document,
 * allowing the user to scroll so the last line sits at the top.
 *
 * Port of `scrollpastend.ts` from CodeMirror 6.
 *
 * @param padding The height of the trailing spacer. Defaults to 50% of the
 *                editor height, which mimics CodeMirror's default.
 */
fun scrollPastEnd(padding: Dp = 200.dp): Extension = ViewPlugin.define(
    create = { view -> ScrollPastEndPlugin(view, padding) },
    configure = {
        copy(
            decorations = { plugin ->
                (plugin as? ScrollPastEndPlugin)?.decos ?: RangeSet.empty()
            }
        )
    }
).asExtension()

private class ScrollPastEndPlugin(view: EditorSession, private val padding: Dp) : PluginValue {
    var decos: DecorationSet = buildDecos(view)

    override fun update(update: ViewUpdate) {
        if (update.docChanged) decos = buildDecos(update.session)
    }

    private fun buildDecos(view: EditorSession): DecorationSet {
        val docEnd = view.state.doc.endPos
        val builder = RangeSetBuilder<Decoration>()
        val widget = SpacerWidget(padding)
        builder.add(
            docEnd,
            docEnd,
            Decoration.widget(WidgetDecorationSpec(widget = widget, side = 1, block = true))
        )
        return builder.finish()
    }
}

private class SpacerWidget(val height: Dp) : WidgetType() {
    @Composable
    override fun Content() {
        Spacer(modifier = Modifier.height(height))
    }

    override fun equals(other: Any?): Boolean = other is SpacerWidget && height == other.height

    override fun hashCode(): Int = height.hashCode()
}
