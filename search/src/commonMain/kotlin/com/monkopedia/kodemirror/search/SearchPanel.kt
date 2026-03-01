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

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorView

/** Composable search/replace panel UI. */
@Composable
internal fun SearchPanel(view: EditorView) {
    val currentQuery = getSearchQuery(view.state)
    var searchText by remember { mutableStateOf(currentQuery.search) }
    var replaceText by remember { mutableStateOf(currentQuery.replace) }
    var caseSensitive by remember { mutableStateOf(currentQuery.caseSensitive) }
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BasicText("Find:")
            BasicTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    updateQuery()
                },
                modifier = Modifier.width(200.dp).border(1.dp, Color.Gray).padding(2.dp),
                singleLine = true
            )
            BasicText("[Next]", modifier = Modifier.clickable { findNext(view) })
            BasicText("[Prev]", modifier = Modifier.clickable { findPrevious(view) })
            BasicText("[Close]", modifier = Modifier.clickable { closeSearchPanel(view) })
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BasicText("Replace:")
            BasicTextField(
                value = replaceText,
                onValueChange = {
                    replaceText = it
                    updateQuery()
                },
                modifier = Modifier.width(200.dp).border(1.dp, Color.Gray).padding(2.dp),
                singleLine = true
            )
            BasicText("[Replace]", modifier = Modifier.clickable { replaceNext(view) })
            BasicText("[All]", modifier = Modifier.clickable { replaceAll(view) })
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicText(
                text = if (caseSensitive) "[x] Case" else "[ ] Case",
                modifier = Modifier.clickable {
                    caseSensitive = !caseSensitive
                    updateQuery()
                }
            )
            BasicText(
                text = if (useRegexp) "[x] Regex" else "[ ] Regex",
                modifier = Modifier.clickable {
                    useRegexp = !useRegexp
                    updateQuery()
                }
            )
            BasicText(
                text = if (wholeWord) "[x] Word" else "[ ] Word",
                modifier = Modifier.clickable {
                    wholeWord = !wholeWord
                    updateQuery()
                }
            )
        }
    }
}
