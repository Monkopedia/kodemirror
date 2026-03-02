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

package com.monkopedia.kodemirror.view

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.unit.sp
import com.monkopedia.kodemirror.state.Facet

private val editorFontFamily = FontFamily(SystemFont("DejaVu Sans Mono"))

/**
 * Color/style tokens for the editor.  Passed through a [CompositionLocal] so
 * every composable in the editor tree can read them without prop-drilling.
 */
data class EditorTheme(
    /** Background color of the editor container. */
    val background: Color = Color(0xFF282C34),
    /** Default foreground (text) color. */
    val foreground: Color = Color(0xFFABB2BF),
    /** Cursor color. */
    val cursor: Color = Color(0xFF528BFF),
    /** Selection background color. */
    val selection: Color = Color(0xFF3E4451),
    /** Active line background highlight. */
    val activeLineBackground: Color = Color(0x0B6699FF),
    /** Gutter background. */
    val gutterBackground: Color = Color(0xFF282C34),
    /** Gutter foreground (line numbers). */
    val gutterForeground: Color = Color(0xFF7D8799),
    /** Gutter active foreground. */
    val gutterActiveForeground: Color = Color(0xFFCCCCCC),
    /** Gutter right border color. */
    val gutterBorderColor: Color = Color.Transparent,
    /** Default text style for content. */
    val contentTextStyle: TextStyle = TextStyle(
        fontFamily = editorFontFamily,
        fontSize = 13.sp,
        lineHeight = (13 * 1.4).sp,
        color = Color(0xFFABB2BF)
    ),
    /** Background for search matches. */
    val searchMatchBackground: Color = Color(0x5972A1FF),
    /** Background for the selected/active search match. */
    val searchMatchSelectedBackground: Color = Color(0x2F6199FF),
    /** Background for matches of the current selection word. */
    val selectionMatchBackground: Color = Color(0x1AAAFE66),
    /** Background for matching brackets. */
    val matchingBracketBackground: Color = Color(0x4400CC00),
    /** Background for non-matching brackets. */
    val nonMatchingBracketBackground: Color = Color(0x44CC0000),
    /** Panel background. */
    val panelBackground: Color = Color(0xFF282C34),
    /** Tooltip background. */
    val tooltipBackground: Color = Color(0xFF353A42),
    /** Fold placeholder text color. */
    val foldPlaceholderColor: Color = Color(0xFFDDDDDD),
    /** Active line gutter background. */
    val activeLineGutterBackground: Color = Color(0x0B6699FF),
    /** Whether this is a dark theme (affects some rendering decisions). */
    val dark: Boolean = true
)

/** A default dark theme. */
val defaultEditorTheme: EditorTheme = EditorTheme()

/** A light theme. */
val lightEditorTheme: EditorTheme = EditorTheme(
    background = Color(0xFFFFFFFF),
    foreground = Color(0xFF000000),
    cursor = Color(0xFF000000),
    selection = Color(0xFFD7D4F0),
    activeLineBackground = Color(0x44CCEEFF),
    gutterBackground = Color(0xFFF5F5F5),
    gutterForeground = Color(0xFF6C6C6C),
    gutterActiveForeground = Color(0xFF333333),
    gutterBorderColor = Color(0xFFDDDDDD),
    contentTextStyle = TextStyle(
        fontFamily = editorFontFamily,
        fontSize = 13.sp,
        lineHeight = (13 * 1.4).sp,
        color = Color(0xFF000000)
    ),
    searchMatchBackground = Color(0x80FFD54F),
    searchMatchSelectedBackground = Color(0x4000BFA5),
    selectionMatchBackground = Color(0x30A0D000),
    matchingBracketBackground = Color(0x4400CC00),
    nonMatchingBracketBackground = Color(0x44CC0000),
    panelBackground = Color(0xFFF5F5F5),
    tooltipBackground = Color(0xFFF5F5F5),
    foldPlaceholderColor = Color(0xFF555555),
    activeLineGutterBackground = Color(0x44CCEEFF),
    dark = false
)

/** CompositionLocal that provides the current [EditorTheme]. */
val LocalEditorTheme = compositionLocalOf { defaultEditorTheme }

/** Facet that lets extensions override the editor theme. */
val editorTheme: Facet<EditorTheme, EditorTheme> = Facet.define(
    combine = { values -> values.lastOrNull() ?: defaultEditorTheme }
)

/** Convenience extension to build a [SpanStyle] from theme colors. */
fun EditorTheme.selectionStyle(): SpanStyle = SpanStyle(background = selection)

/** Convenience extension to build a [SpanStyle] for active line. */
fun EditorTheme.activeLineStyle(): SpanStyle = SpanStyle(background = activeLineBackground)
