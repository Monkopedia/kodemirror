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
package com.monkopedia.kodemirror.lsp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.Slot
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.EditorTheme
import com.monkopedia.kodemirror.view.LocalEditorSession
import com.monkopedia.kodemirror.view.LocalEditorTheme
import com.monkopedia.kodemirror.view.Panel
import com.monkopedia.kodemirror.view.showPanels

/**
 * The `showPanels` provider for the reference panel: emits a single bottom
 * [Panel] (mirroring upstream's `showPanel.from`) whenever the
 * [referencePanel][referencePanel] field holds an open panel.
 */
internal fun referencePanelProvider() = showPanels.compute(
    listOf(Slot.FieldSlot(referencePanel))
) { state ->
    val panel = state.field(referencePanel, require = false)
    if (panel != null) {
        listOf(
            Panel(top = false) {
                val view = LocalEditorSession.current
                ReferencePanelContent(view, panel)
            }
        )
    } else {
        emptyList()
    }
}

/**
 * Composable reference-panel content: a header with a close button and a
 * scrollable list of references grouped by file, each row showing the line
 * number plus a context preview with the matched span emphasized.
 *
 * Ports upstream's `createReferencePanel` DOM as far as Compose allows:
 * clicking a row selects (highlights) and navigates to it, and a `[Close]`
 * affordance dismisses the panel. Upstream's in-panel keyboard list navigation
 * (Arrow/Home/End/Enter) is not reproduced — closing is instead handled by the
 * editor-level `Escape` binding in [findReferencesKeymap].
 */
@Composable
internal fun ReferencePanelContent(view: EditorSession, panel: ReferencePanelState) {
    val theme = LocalEditorTheme.current
    val textStyle = TextStyle(color = theme.foreground)

    // Resolve previews against the workspace's open documents.
    val server = view.state.facet(referenceServer)
    val entries = remember(panel) {
        buildReferenceEntries(panel.locations) { uri ->
            server?.client?.workspace?.getFile(uri)?.session?.state?.doc
        }
    }
    var selected by remember(panel) { mutableStateOf(0) }

    Column(modifier = Modifier.padding(4.dp)) {
        Row(modifier = Modifier.padding(bottom = 4.dp)) {
            BasicText("References (${entries.size})", style = textStyle)
            BasicText(
                " [Close]",
                modifier = Modifier.clickable { closeReferencePanel(view) },
                style = textStyle
            )
        }
        if (entries.isEmpty()) {
            BasicText("No references", style = textStyle)
        } else {
            LazyColumn {
                itemsIndexed(entries) { index, entry ->
                    val showHeader = index == 0 || entries[index - 1].fileName != entry.fileName
                    if (showHeader) {
                        BasicText(
                            text = entry.fileName,
                            modifier = Modifier.padding(top = if (index == 0) 0.dp else 4.dp),
                            style = TextStyle(
                                color = theme.foreground,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    ReferenceRow(
                        entry = entry,
                        selected = index == selected,
                        theme = theme,
                        onClick = {
                            selected = index
                            server?.let { showReference(it, entry) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferenceRow(
    entry: ReferenceEntry,
    selected: Boolean,
    theme: EditorTheme,
    onClick: () -> Unit
) {
    val lineLabel = entry.lineNumber?.let { "$it: " }?.padStart(5, ' ') ?: ""
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(if (selected) Modifier.background(theme.selection) else Modifier)
        .clickable(onClick = onClick)
        .padding(vertical = 1.dp)
    Row(modifier = rowModifier) {
        BasicText(
            text = buildAnnotatedString {
                if (lineLabel.isNotEmpty()) {
                    append(lineLabel)
                }
                append(entry.before)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(entry.matched)
                }
                append(entry.after)
            },
            style = TextStyle(color = theme.foreground)
        )
    }
}
