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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.RangeSet

/**
 * Extension that makes whitespace characters (spaces and tabs) visible
 * by applying a subtle mark decoration. Spaces are shown with a
 * centered dot character and tabs with an arrow.
 */
val highlightWhitespace: Extension = run {
    val spaceMark = Decoration.mark(
        MarkDecorationSpec(
            style = SpanStyle(color = Color(0x40808080)),
            cssClass = "cm-highlightSpace"
        )
    )
    val tabMark = Decoration.mark(
        MarkDecorationSpec(
            style = SpanStyle(color = Color(0x40808080)),
            cssClass = "cm-highlightTab"
        )
    )

    val decorator = MatchDecorator(
        regexp = Regex("[ \t]"),
        decorate = { add, match, _ ->
            val from = match.range.first
            val to = match.range.last + 1
            val mark = if (match.value == "\t") tabMark else spaceMark
            add(from, to, mark)
        }
    )

    ViewPlugin.define(
        create = { view -> WhitespacePlugin(view, decorator) },
        configure = {
            copy(
                decorations = { plugin ->
                    (plugin as? WhitespacePlugin)?.decos ?: RangeSet.empty()
                }
            )
        }
    ).asExtension()
}

/**
 * Extension that highlights trailing whitespace (spaces and tabs at
 * end of lines) with a warning-style background.
 */
val highlightTrailingWhitespace: Extension = run {
    val trailingMark = Decoration.mark(
        MarkDecorationSpec(
            style = SpanStyle(background = Color(0x30FF6666)),
            cssClass = "cm-trailingSpace"
        )
    )

    val decorator = MatchDecorator(
        regexp = Regex("[ \t]+$", RegexOption.MULTILINE),
        decorate = { add, match, _ ->
            add(match.range.first, match.range.last + 1, trailingMark)
        }
    )

    ViewPlugin.define(
        create = { view -> WhitespacePlugin(view, decorator) },
        configure = {
            copy(
                decorations = { plugin ->
                    (plugin as? WhitespacePlugin)?.decos ?: RangeSet.empty()
                }
            )
        }
    ).asExtension()
}

private class WhitespacePlugin(
    view: EditorSession,
    private val decorator: MatchDecorator
) : PluginValue {
    var decos: DecorationSet = decorator.createDeco(view)

    override fun update(update: ViewUpdate) {
        decos = decorator.updateDeco(update, decos)
    }
}
