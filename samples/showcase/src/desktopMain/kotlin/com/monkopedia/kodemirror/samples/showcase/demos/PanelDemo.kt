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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.Panel
import com.monkopedia.kodemirror.view.rememberEditorSession
import com.monkopedia.kodemirror.view.showPanels

@Composable
fun PanelDemo() {
    val topPanel = Panel(top = true) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2C313C))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Top Panel: editor-toolbar.js",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFABB2BF)
            )
        }
    }
    val bottomPanel = Panel(top = false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF21252B))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Bottom Panel: Ln 1, Col 1 | JavaScript | UTF-8",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF636D83)
            )
        }
    }

    DemoScaffold(
        title = "Panels",
        description = "Top and bottom panels via the showPanels facet."
    ) {
        val session = rememberEditorSession(
            doc = SampleDocs.javascript,
            extensions = basicSetup + javascript().extension +
                showPanels.of(listOf(topPanel, bottomPanel))
        )
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
