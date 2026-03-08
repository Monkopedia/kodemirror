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

package com.monkopedia.kodemirror.themedracula

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.monkopedia.kodemirror.language.HighlightStyle
import com.monkopedia.kodemirror.language.TagStyleSpec
import com.monkopedia.kodemirror.language.syntaxHighlighting
import com.monkopedia.kodemirror.lezer.highlight.Tags
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.view.EditorTheme
import com.monkopedia.kodemirror.view.editorTheme

/**
 * Named color constants from the Dracula palette.
 *
 * @see <a href="https://draculatheme.com/contribute">Dracula Color Palette</a>
 */
object DraculaColors {
    val background = Color(0xFF282A36)
    val currentLine = Color(0xFF44475A)
    val foreground = Color(0xFFF8F8F2)
    val comment = Color(0xFF6272A4)
    val cyan = Color(0xFF8BE9FD)
    val green = Color(0xFF50FA7B)
    val orange = Color(0xFFFFB86C)
    val pink = Color(0xFFFF79C6)
    val purple = Color(0xFFBD93F9)
    val red = Color(0xFFFF5555)
    val yellow = Color(0xFFF1FA8C)
    val selection = Color(0xFF44475A)
    val darkBackground = Color(0xFF21222C)
}

private val editorFontFamily = FontFamily(SystemFont("DejaVu Sans Mono"))

/**
 * Dracula syntax highlighting style.
 *
 * Maps Lezer syntax tags to colors matching the Dracula color scheme.
 */
val draculaHighlightStyle = HighlightStyle.define(
    listOf(
        TagStyleSpec(Tags.keyword, SpanStyle(color = DraculaColors.pink)),
        TagStyleSpec(
            listOf(Tags.name, Tags.deleted, Tags.character, Tags.macroName),
            SpanStyle(color = DraculaColors.foreground)
        ),
        TagStyleSpec(
            listOf(Tags.propertyName),
            SpanStyle(color = DraculaColors.green)
        ),
        TagStyleSpec(
            listOf(Tags.function(Tags.variableName), Tags.labelName),
            SpanStyle(color = DraculaColors.green)
        ),
        TagStyleSpec(
            listOf(Tags.color, Tags.constant(Tags.name), Tags.standard(Tags.name)),
            SpanStyle(color = DraculaColors.purple)
        ),
        TagStyleSpec(
            listOf(Tags.definition(Tags.name), Tags.separator),
            SpanStyle(color = DraculaColors.foreground)
        ),
        TagStyleSpec(
            listOf(
                Tags.typeName,
                Tags.className,
                Tags.changed,
                Tags.annotation,
                Tags.modifier,
                Tags.self,
                Tags.namespace
            ),
            SpanStyle(color = DraculaColors.cyan, fontStyle = FontStyle.Italic)
        ),
        TagStyleSpec(
            Tags.number,
            SpanStyle(color = DraculaColors.purple)
        ),
        TagStyleSpec(
            listOf(
                Tags.operator,
                Tags.operatorKeyword,
                Tags.url,
                Tags.escape,
                Tags.regexp,
                Tags.link,
                Tags.special(Tags.string)
            ),
            SpanStyle(color = DraculaColors.pink)
        ),
        TagStyleSpec(
            listOf(Tags.meta, Tags.comment),
            SpanStyle(color = DraculaColors.comment)
        ),
        TagStyleSpec(Tags.strong, SpanStyle(fontWeight = FontWeight.Bold)),
        TagStyleSpec(Tags.emphasis, SpanStyle(fontStyle = FontStyle.Italic)),
        TagStyleSpec(
            Tags.strikethrough,
            SpanStyle(textDecoration = TextDecoration.LineThrough)
        ),
        TagStyleSpec(
            Tags.link,
            SpanStyle(
                color = DraculaColors.cyan,
                textDecoration = TextDecoration.Underline
            )
        ),
        TagStyleSpec(
            Tags.heading,
            SpanStyle(
                fontWeight = FontWeight.Bold,
                color = DraculaColors.purple
            )
        ),
        TagStyleSpec(
            listOf(Tags.atom, Tags.bool, Tags.special(Tags.variableName)),
            SpanStyle(color = DraculaColors.purple)
        ),
        TagStyleSpec(
            listOf(Tags.processingInstruction, Tags.string, Tags.inserted),
            SpanStyle(color = DraculaColors.yellow)
        ),
        TagStyleSpec(Tags.invalid, SpanStyle(color = DraculaColors.red))
    )
)

/**
 * Complete Dracula editor theme with UI styling.
 *
 * Provides background, gutter, cursor, selection, search-match,
 * bracket-match, tooltip, panel, and fold-placeholder colors matching
 * the Dracula color scheme.
 */
val draculaTheme: EditorTheme = EditorTheme(
    background = DraculaColors.background,
    foreground = DraculaColors.foreground,
    cursor = DraculaColors.foreground,
    selection = DraculaColors.selection,
    activeLineBackground = DraculaColors.currentLine,
    gutterBackground = DraculaColors.background,
    gutterForeground = DraculaColors.comment,
    gutterActiveForeground = DraculaColors.foreground,
    gutterBorderColor = Color.Transparent,
    contentTextStyle = TextStyle(
        fontFamily = editorFontFamily,
        fontSize = 13.sp,
        lineHeight = (13 * 1.4).sp,
        color = DraculaColors.foreground
    ),
    searchMatchBackground = Color(0x59FFB86C),
    searchMatchSelectedBackground = Color(0x2FFFB86C),
    selectionMatchBackground = Color(0x1ABD93F9),
    matchingBracketBackground = Color(0x4750FA7B),
    nonMatchingBracketBackground = Color(0x47FF5555),
    panelBackground = DraculaColors.darkBackground,
    panelBorderColor = Color(0xFF555555),
    buttonBackground = Color(0xFF44475A),
    buttonBorderColor = Color(0xFF6272A4),
    inputBackground = Color.Transparent,
    inputBorderColor = Color(0xFF6272A4),
    tooltipBackground = DraculaColors.darkBackground,
    foldPlaceholderColor = DraculaColors.foreground,
    foldPlaceholderBackground = Color(0x33F8F8F2),
    activeLineGutterBackground = DraculaColors.currentLine,
    dark = true
)

/**
 * Extension combining [draculaTheme] UI styling with
 * [draculaHighlightStyle] syntax highlighting.
 *
 * Drop this into an editor's extension list for a complete Dracula look.
 */
val dracula: Extension = ExtensionList(
    listOf(
        editorTheme.of(draculaTheme),
        syntaxHighlighting(draculaHighlightStyle)
    )
)
