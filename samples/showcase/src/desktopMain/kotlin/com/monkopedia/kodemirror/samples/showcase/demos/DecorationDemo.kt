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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.DecorationSource
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.MarkDecorationSpec
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.WidgetDecorationSpec
import com.monkopedia.kodemirror.view.WidgetType
import com.monkopedia.kodemirror.view.rememberEditorSession

private val highlightMark = Decoration.mark(
    MarkDecorationSpec(
        style = SpanStyle(
            background = Color(0x4400FF00),
            fontWeight = FontWeight.Bold
        )
    )
)

private class InfoWidget : WidgetType() {
    @Composable
    override fun Content() {
        Text(
            text = " [info] ",
            color = Color(0xFF61AFEF),
            modifier = Modifier
                .background(Color(0xFF2C313C))
                .padding(horizontal = 4.dp)
        )
    }
}

private class DecorationPlugin : PluginValue, DecorationSource {
    override var decorations: DecorationSet = RangeSet.empty()

    override fun update(update: ViewUpdate) {
        if (update.docChanged || decorations === RangeSet.empty<Decoration>()) {
            val builder = RangeSetBuilder<Decoration>()
            val doc = update.state.doc
            val text = doc.toString()
            // Highlight "function" keywords
            var idx = text.indexOf("function")
            while (idx >= 0) {
                builder.add(DocPos(idx), DocPos(idx + 8), highlightMark)
                idx = text.indexOf("function", idx + 1)
            }
            // Widget after first line
            if (doc.lines >= 1) {
                val firstLineEnd = doc.line(LineNumber(1)).to
                builder.add(
                    firstLineEnd, firstLineEnd,
                    Decoration.widget(WidgetDecorationSpec(InfoWidget()))
                )
            }
            decorations = builder.finish()
        }
    }
}

private val decorationPlugin = ViewPlugin.fromDecorationSource { _ ->
    DecorationPlugin()
}

@Composable
fun DecorationDemo() {
    DemoScaffold(
        title = "Decorations",
        description = "Mark decorations highlight 'function' keywords. " +
            "A widget decoration inserts an [info] badge after line 1."
    ) {
        val session = rememberEditorSession(
            doc = SampleDocs.javascript,
            extensions = basicSetup + javascript().extension +
                decorationPlugin.asExtension()
        )
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
