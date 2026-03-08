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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.LocalEditorTheme

private val toggleGotoLinePanel: StateEffectType<Boolean> =
    StateEffect.define()

internal val gotoLinePanelOpenField: StateField<Boolean> =
    StateField.define(
        StateFieldSpec(
            create = { false },
            update = { value, tr ->
                var result = value
                for (effect in tr.effects) {
                    val panelEffect =
                        effect.asType(toggleGotoLinePanel)
                    if (panelEffect != null) {
                        result = panelEffect.value
                    }
                }
                result
            }
        )
    )

/** Command that opens the go-to-line dialog. */
val gotoLine: (EditorSession) -> Boolean = { view ->
    view.dispatch(
        TransactionSpec(
            effects = listOf(toggleGotoLinePanel.of(true))
        )
    )
    true
}

/** Composable panel for the go-to-line dialog. */
@Composable
internal fun GoToLinePanel(view: EditorSession) {
    val theme = LocalEditorTheme.current
    val panelTextStyle = theme.contentTextStyle.copy(
        color = theme.foreground,
        fontSize = (theme.contentTextStyle.fontSize.value * 0.8).sp
    )
    var lineText by remember { mutableStateOf("") }

    fun goToLine() {
        val lineNum = lineText.toIntOrNull() ?: return
        val doc = view.state.doc
        val clampedLine = lineNum.coerceIn(1, doc.lines)
        val line = doc.line(clampedLine)
        view.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(line.from),
                scrollIntoView = true,
                effects = listOf(
                    toggleGotoLinePanel.of(false)
                ),
                userEvent = "select.gotoLine"
            )
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(4.dp)
    ) {
        BasicText(
            "Go to line: ",
            style = panelTextStyle,
            modifier = Modifier.padding(end = 4.dp)
        )
        BasicTextField(
            value = lineText,
            onValueChange = { lineText = it },
            modifier = Modifier.width(80.dp)
                .background(theme.inputBackground)
                .border(1.dp, theme.inputBorderColor)
                .padding(2.dp),
            textStyle = panelTextStyle,
            cursorBrush = SolidColor(theme.cursor),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = { goToLine() }
            ),
            singleLine = true
        )
        val buttonShape = RoundedCornerShape(1.dp)
        BasicText(
            " Go",
            style = panelTextStyle,
            modifier = Modifier.padding(start = 4.dp)
                .background(theme.buttonBackground, buttonShape)
                .border(1.dp, theme.buttonBorderColor, buttonShape)
                .clickable { goToLine() }
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}
