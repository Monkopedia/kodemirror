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
package com.monkopedia.kodemirror.view

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.monkopedia.kodemirror.state.Facet

/**
 * Color/style tokens for the editor.  Passed through a [CompositionLocal] so
 * every composable in the editor tree can read them without prop-drilling.
 */
data class EditorTheme(
    /** Background color of the editor container. */
    val background: Color = Color(0xFF1E1E1E),
    /** Default foreground (text) color. */
    val foreground: Color = Color(0xFFD4D4D4),
    /** Cursor color. */
    val cursor: Color = Color(0xFFAEAFAD),
    /** Selection background color. */
    val selection: Color = Color(0x6038618C),
    /** Active line background highlight. */
    val activeLineBackground: Color = Color(0x1AFFFFFF),
    /** Gutter background. */
    val gutterBackground: Color = Color(0xFF252526),
    /** Gutter foreground (line numbers). */
    val gutterForeground: Color = Color(0xFF858585),
    /** Gutter active foreground. */
    val gutterActiveForeground: Color = Color(0xFFC6C6C6),
    /** Default text style for content. */
    val contentTextStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        color = Color(0xFFD4D4D4)
    ),
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
    selection = Color(0x60ADD6FF),
    activeLineBackground = Color(0x0A000000),
    gutterBackground = Color(0xFFF5F5F5),
    gutterForeground = Color(0xFF999999),
    gutterActiveForeground = Color(0xFF333333),
    contentTextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        color = Color(0xFF000000)
    ),
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
