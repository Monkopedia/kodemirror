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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.Tooltip
import com.monkopedia.kodemirror.view.hoverTooltip
import com.monkopedia.kodemirror.view.rememberEditorSession

private val hoverInfo = hoverTooltip { session, pos ->
    val doc = session.state.doc
    val line = doc.lineAt(DocPos(pos))
    val text = line.text
    // Find word boundaries
    val lineStart = line.from.value
    var start = pos - lineStart
    var end = start
    while (start > 0 && text[start - 1].isLetterOrDigit()) start--
    while (end < text.length && text[end].isLetterOrDigit()) end++
    if (start == end) return@hoverTooltip null
    val word = text.substring(start, end)
    Tooltip(
        pos = lineStart + start,
        above = true
    ) {
        Text(
            text = "\"$word\" at line ${line.number}, col ${start + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFABB2BF),
            modifier = Modifier
                .background(Color(0xFF2C313C))
                .padding(8.dp)
        )
    }
}

@Composable
fun TooltipDemo() {
    DemoScaffold(
        title = "Tooltips",
        description = "Hover over any word to see a tooltip with its position."
    ) {
        val session = rememberEditorSession(
            doc = SampleDocs.javascript,
            extensions = basicSetup + javascript().extension + hoverInfo
        )
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
