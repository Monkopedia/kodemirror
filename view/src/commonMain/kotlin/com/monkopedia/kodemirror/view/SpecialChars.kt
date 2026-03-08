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
import androidx.compose.ui.graphics.Color
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.RangeSet

/** Default regex that matches special / control characters to highlight. */
val specialCharRegex: Regex =
    @Suppress("ktlint:standard:max-line-length")
    Regex(
        "[\u0000-\u0008\u000e-\u001f\u007f-\u009f\u00ad\u061c\u200b\u200e\u200f\u2028\u2029\ufeff\ufff9-\ufffc]"
    )

/**
 * Extension that highlights special (non-printing) characters with a
 * placeholder widget.
 *
 * Port of `special-chars.ts` from CodeMirror 6.
 */
val highlightSpecialChars: Extension = run {
    val decorator = MatchDecorator(
        regexp = specialCharRegex,
        decorate = { add, match, _ ->
            val char = match.value
            val codePoint = char[0].code
            val label = specialCharLabel(codePoint)
            val widget = SpecialCharWidget(label, codePoint)
            add(
                match.range.first,
                match.range.last + 1,
                Decoration.replace(
                    ReplaceDecorationSpec(widget = widget)
                )
            )
        }
    )

    ViewPlugin.define(
        create = { view ->
            SpecialCharsPlugin(view, decorator)
        },
        configure = {
            copy(
                decorations = { plugin ->
                    (plugin as? SpecialCharsPlugin)?.decos ?: RangeSet.empty()
                }
            )
        }
    ).asExtension()
}

private fun specialCharLabel(codePoint: Int): String = when {
    codePoint < 32 -> "^${('A'.code + codePoint - 1).toChar()}"
    codePoint == 127 -> "DEL"
    codePoint in 0x80..0x9F -> "x${codePoint.toString(16).uppercase()}"
    codePoint == 0x00AD -> "SHY"
    codePoint == 0x200B -> "ZWS"
    codePoint == 0xFEFF -> "BOM"
    else -> "?".also { }
}

private class SpecialCharWidget(val label: String, val codePoint: Int) : WidgetType() {
    @Composable
    override fun Content() {
        BasicText(
            text = label,
            style = LocalEditorTheme.current.contentTextStyle.copy(
                color = Color.White,
                background = Color(0xFFCC0000)
            )
        )
    }

    override fun eq(other: WidgetType): Boolean =
        other is SpecialCharWidget && label == other.label && codePoint == other.codePoint
}

private class SpecialCharsPlugin(view: EditorSession, private val decorator: MatchDecorator) :
    PluginValue {
    var decos: DecorationSet = decorator.createDeco(view)

    override fun update(update: ViewUpdate) {
        decos = decorator.updateDeco(update, decos)
    }
}
