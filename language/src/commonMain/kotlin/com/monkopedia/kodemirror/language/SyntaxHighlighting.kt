/*
 * Copyright 2025 Jason Monk
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
package com.monkopedia.kodemirror.language

import androidx.compose.ui.text.SpanStyle
import com.monkopedia.kodemirror.lezer.highlight.Highlighter
import com.monkopedia.kodemirror.lezer.highlight.highlightTree
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Prec
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.EditorView
import com.monkopedia.kodemirror.view.MarkDecoration
import com.monkopedia.kodemirror.view.MarkDecorationSpec
import com.monkopedia.kodemirror.view.PluginSpec
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate

/**
 * Wrap a highlighter in an editor extension that uses it to apply
 * syntax highlighting to the editor content.
 */
fun syntaxHighlighting(highlighter: Highlighter): Extension {
    val plugin = ViewPlugin.define(
        PluginSpec(
            create = { view -> TreeHighlighter(view, highlighter) },
            decorations = { v -> v.decorations }
        )
    )
    return Prec.high(plugin.asExtension())
}

private class TreeHighlighter(
    view: EditorView,
    private val highlighter: Highlighter
) : PluginValue {
    var decorations: DecorationSet = buildDeco(view)
        private set

    private val markCache = mutableMapOf<String, MarkDecoration>()

    override fun update(update: ViewUpdate) {
        val tree = syntaxTree(update.state)
        val oldTree = syntaxTree(update.startState)
        if (tree !== oldTree || update.docChanged) {
            decorations = buildDeco(update.view)
        }
    }

    private fun buildDeco(view: EditorView): DecorationSet {
        val tree = syntaxTree(view.state)
        if (tree.length == 0) return RangeSet.empty()

        val builder = RangeSetBuilder<Decoration>()
        highlightTree(tree, highlighter, { from, to, style ->
            val mark = markCache.getOrPut(style) {
                val spanStyle = resolveSpanStyle(style)
                Decoration.mark(MarkDecorationSpec(style = spanStyle))
            }
            builder.add(from, to, mark)
        })
        return builder.finish()
    }

    private fun resolveSpanStyle(cls: String): SpanStyle? {
        if (highlighter is HighlightStyle) {
            // Try each class in the space-separated list
            for (part in cls.split(" ")) {
                val resolved = highlighter.spanStyleFor(part)
                if (resolved != null) return resolved
            }
        }
        return null
    }
}
