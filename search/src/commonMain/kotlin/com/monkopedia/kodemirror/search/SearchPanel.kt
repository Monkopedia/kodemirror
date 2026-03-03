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
package com.monkopedia.kodemirror.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorView
import com.monkopedia.kodemirror.view.LocalEditorTheme

/** Composable search/replace panel UI. */
@Composable
internal fun SearchPanel(view: EditorView) {
    val theme = LocalEditorTheme.current
    val panelTextStyle = theme.contentTextStyle.copy(
        color = theme.foreground,
        fontSize = (theme.contentTextStyle.fontSize.value * 0.8).sp
    )
    val buttonShape = RoundedCornerShape(1.dp)
    val buttonMod = Modifier
        .background(theme.buttonBackground, buttonShape)
        .border(1.dp, theme.buttonBorderColor, buttonShape)

    val currentQuery = getSearchQuery(view.state)
    var searchText by remember { mutableStateOf(currentQuery.search) }
    var replaceText by remember { mutableStateOf(currentQuery.replace) }
    var caseSensitive by remember {
        mutableStateOf(currentQuery.caseSensitive)
    }
    var useRegexp by remember { mutableStateOf(currentQuery.regexp) }
    var wholeWord by remember { mutableStateOf(currentQuery.wholeWord) }

    fun updateQuery() {
        view.dispatch(
            TransactionSpec(
                effects = listOf(
                    setSearchQuery.of(
                        SearchQuery(
                            search = searchText,
                            caseSensitive = caseSensitive,
                            regexp = useRegexp,
                            replace = replaceText,
                            wholeWord = wholeWord
                        )
                    )
                )
            )
        )
    }

    Column(modifier = Modifier.padding(4.dp)) {
        // Row 1: Find input + navigation + options + close
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BasicTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    updateQuery()
                },
                modifier = Modifier.width(200.dp)
                    .background(theme.inputBackground)
                    .border(1.dp, theme.inputBorderColor)
                    .padding(2.dp),
                textStyle = panelTextStyle,
                cursorBrush = SolidColor(theme.cursor),
                singleLine = true
            )
            BasicText(
                "next",
                style = panelTextStyle,
                modifier = buttonMod
                    .clickable { findNext(view) }
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
            BasicText(
                "previous",
                style = panelTextStyle,
                modifier = buttonMod
                    .clickable { findPrevious(view) }
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
            BasicText(
                "all",
                style = panelTextStyle,
                modifier = buttonMod
                    .clickable { selectMatches(view) }
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
            PanelCheckbox(
                checked = caseSensitive,
                label = "match case",
                textStyle = panelTextStyle,
                onToggle = {
                    caseSensitive = !caseSensitive
                    updateQuery()
                }
            )
            PanelCheckbox(
                checked = useRegexp,
                label = "regexp",
                textStyle = panelTextStyle,
                onToggle = {
                    useRegexp = !useRegexp
                    updateQuery()
                }
            )
            PanelCheckbox(
                checked = wholeWord,
                label = "by word",
                textStyle = panelTextStyle,
                onToggle = {
                    wholeWord = !wholeWord
                    updateQuery()
                }
            )
            Spacer(modifier = Modifier.weight(1f))
            BasicText(
                "\u00D7",
                style = panelTextStyle,
                modifier = Modifier
                    .clickable { closeSearchPanel(view) }
                    .padding(horizontal = 4.dp)
            )
        }
        // Row 2: Replace input + replace buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BasicTextField(
                value = replaceText,
                onValueChange = {
                    replaceText = it
                    updateQuery()
                },
                modifier = Modifier.width(200.dp)
                    .background(theme.inputBackground)
                    .border(1.dp, theme.inputBorderColor)
                    .padding(2.dp),
                textStyle = panelTextStyle,
                cursorBrush = SolidColor(theme.cursor),
                singleLine = true
            )
            BasicText(
                "replace",
                style = panelTextStyle,
                modifier = buttonMod
                    .clickable { replaceNext(view) }
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
            BasicText(
                "replace all",
                style = panelTextStyle,
                modifier = buttonMod
                    .clickable { replaceAll(view) }
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun PanelCheckbox(
    checked: Boolean,
    label: String,
    textStyle: TextStyle,
    onToggle: () -> Unit
) {
    val theme = LocalEditorTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onToggle() }
    ) {
        Box(
            modifier = Modifier.size(12.dp)
                .border(1.dp, theme.buttonBorderColor)
                .then(
                    if (checked) {
                        Modifier.background(theme.buttonBackground)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                BasicText(
                    "\u2713",
                    style = textStyle.copy(
                        fontSize = 9.sp,
                        lineHeight = 10.sp
                    )
                )
            }
        }
        BasicText(
            " $label",
            style = textStyle
        )
    }
}
