package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Range
import com.monkopedia.kodemirror.state.RangeValue.Companion.range
import com.monkopedia.kodemirror.state.SingleOrList.Companion.list

// import {Extension} from "@codemirror/state"
// import {EditorView} from "./editorview"
// import {ViewPlugin, ViewUpdate} from "./extension"
// import {Decoration, DecorationSet} from "./decoration"

// / Mark lines that have a cursor on them with the `"cm-activeLine"`
// / DOM class.
fun highlightActiveLine(): Extension = activeLineHighlighter.extension

val lineDeco = Decoration.line(LineDecorationSpec(cls = "cm-activeLine"))

private class ActiveLineHightlighterPlugin(view: EditorView) : PluginValue {
    var decorations: DecorationSet = this.getDeco(view)

    override fun update(update: ViewUpdate) {
        if (update.docChanged || update.selectionSet) {
            this.decorations = this.getDeco(update.view)
        }
    }

    fun getDeco(view: EditorView): DecorationSet {
        var lastLineStart = -1
        var deco = mutableListOf<Range<Decoration<*>>>()
        for (r in view.state.selection.ranges) {
            val line = view.lineBlockAt(r.head)
            if (line.from > lastLineStart) {
                deco.add(lineDeco.range(line.from))
                lastLineStart = line.from
            }
        }
        return Decoration.set(deco.list)
    }
}

val activeLineHighlighter: ViewPlugin<*> = ViewPlugin.fromClass(
    ::ActiveLineHightlighterPlugin,
    PluginSpec(
        decorations = { v -> v.decorations }
    )
)
