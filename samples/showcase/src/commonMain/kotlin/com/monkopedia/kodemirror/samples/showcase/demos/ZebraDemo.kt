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
 */
package com.monkopedia.kodemirror.samples.showcase.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.Doc
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.DecorationSource
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.LineDecorationSpec
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.rememberEditorSession

// Dark-theme stripe color matching CodeMirror reference (#34474788)
private val stripe = Decoration.line(
    LineDecorationSpec(style = SpanStyle(background = Color(0x88344747)))
)

private fun buildStripes(doc: Doc, step: Int): DecorationSet {
    val builder = RangeSetBuilder<Decoration>()
    for (i in 1..doc.lines) {
        if (i % step == 0) {
            val line = doc.line(LineNumber(i))
            builder.add(line.from, line.from, stripe)
        }
    }
    return builder.finish()
}

private class ZebraPlugin(
    session: EditorSession,
    private val step: Int
) : PluginValue, DecorationSource {
    override var decorations: DecorationSet = buildStripes(session.state.doc, step)

    override fun update(update: ViewUpdate) {
        if (update.docChanged || update.viewportChanged) {
            decorations = buildStripes(update.state.doc, step)
        }
    }
}

private val zebraPlugin = ViewPlugin.fromDecorationSource { session ->
    ZebraPlugin(session = session, step = 2)
}

@Composable
fun ZebraDemo() {
    DemoScaffold(
        title = "Zebra Stripes",
        description = "ViewPlugin that adds a subtle background to every other line."
    ) {
        val session = rememberEditorSession(
            doc = SampleDocs.javascript,
            extensions = basicSetup + javascript().extension + zebraPlugin.asExtension()
        )
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
