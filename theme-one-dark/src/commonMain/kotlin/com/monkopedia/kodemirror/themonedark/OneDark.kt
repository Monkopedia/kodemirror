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
@file:OptIn(ExperimentalTextApi::class)

package com.monkopedia.kodemirror.themonedark

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.unit.sp
import com.monkopedia.kodemirror.language.oneDarkHighlightStyle
import com.monkopedia.kodemirror.language.syntaxHighlighting
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.view.EditorTheme
import com.monkopedia.kodemirror.view.editorTheme

/**
 * Named color constants from the upstream One Dark palette.
 */
object OneDarkColors {
    val chalky = Color(0xFFE5C07B)
    val coral = Color(0xFFE06C75)
    val cyan = Color(0xFF56B6C2)
    val invalid = Color(0xFFFFFFFF)
    val ivory = Color(0xFFABB2BF)
    val stone = Color(0xFF7D8799)
    val malibu = Color(0xFF61AFEF)
    val sage = Color(0xFF98C379)
    val whiskey = Color(0xFFD19A66)
    val violet = Color(0xFFC678DD)
    val darkBackground = Color(0xFF21252B)
    val highlightBackground = Color(0xFF2C313A)
    val background = Color(0xFF282C34)
    val tooltipBackground = Color(0xFF353A42)
    val selection = Color(0xFF3E4451)
    val cursor = Color(0xFF528BFF)
}

private val editorFontFamily = FontFamily(SystemFont("DejaVu Sans Mono"))

/**
 * Complete One Dark editor theme with UI styling.
 *
 * Provides background, gutter, cursor, selection, search-match,
 * bracket-match, tooltip, panel, and fold-placeholder colors matching
 * the upstream `@codemirror/theme-one-dark` package.
 */
val oneDarkTheme: EditorTheme = EditorTheme(
    background = OneDarkColors.background,
    foreground = OneDarkColors.ivory,
    cursor = OneDarkColors.cursor,
    selection = OneDarkColors.selection,
    activeLineBackground = OneDarkColors.highlightBackground,
    gutterBackground = OneDarkColors.background,
    gutterForeground = OneDarkColors.stone,
    gutterActiveForeground = Color(0xFFCCCCCC),
    gutterBorderColor = Color.Transparent,
    contentTextStyle = TextStyle(
        fontFamily = editorFontFamily,
        fontSize = 13.sp,
        lineHeight = (13 * 1.4).sp,
        color = OneDarkColors.ivory
    ),
    searchMatchBackground = Color(0x5972A1FF),
    searchMatchSelectedBackground = Color(0x2F6199FF),
    selectionMatchBackground = Color(0x1AAAFE66),
    matchingBracketBackground = Color(0x47BAD0F8),
    nonMatchingBracketBackground = Color(0x47BAD0F8),
    panelBackground = OneDarkColors.darkBackground,
    tooltipBackground = OneDarkColors.tooltipBackground,
    foldPlaceholderColor = Color(0xFFDDDDDD),
    activeLineGutterBackground = OneDarkColors.highlightBackground,
    dark = true
)

/**
 * Extension combining [oneDarkTheme] UI styling with
 * [oneDarkHighlightStyle] syntax highlighting.
 *
 * Drop this into an editor's extension list for a complete One Dark look.
 */
val oneDark: Extension = ExtensionList(
    listOf(
        editorTheme.of(oneDarkTheme),
        syntaxHighlighting(oneDarkHighlightStyle)
    )
)
