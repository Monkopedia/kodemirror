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

package com.monkopedia.kodemirror.themegithublight

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
 * Named color constants from the GitHub Light Default palette.
 */
object GitHubLightColors {
    val background = Color(0xFFFFFFFF)
    val foreground = Color(0xFF1F2328)
    val cursor = Color(0xFF044289)
    val selection = Color(0xFFBBDFFF)
    val activeLineBackground = Color(0xFFF6F8FA)
    val gutterBackground = Color(0xFFFFFFFF)
    val gutterForeground = Color(0xFF8C959F)
    val gutterActiveForeground = Color(0xFF1F2328)
    val gutterBorder = Color(0xFFD0D7DE)
    val comment = Color(0xFF6E7781)
    val keyword = Color(0xFFCF222E)
    val string = Color(0xFF0A3069)
    val variable = Color(0xFF953800)
    val function = Color(0xFF8250DF)
    val typeName = Color(0xFF0550AE)
    val tag = Color(0xFF116329)
    val attribute = Color(0xFF0550AE)
    val constant = Color(0xFF0550AE)
    val number = Color(0xFF0550AE)
}

private val editorFontFamily = FontFamily(SystemFont("DejaVu Sans Mono"))

/**
 * GitHub Light syntax highlighting style.
 *
 * Maps Lezer syntax tags to colors matching the GitHub Light Default theme.
 */
val gitHubLightHighlightStyle = HighlightStyle.define(
    listOf(
        TagStyleSpec(Tags.keyword, SpanStyle(color = GitHubLightColors.keyword)),
        TagStyleSpec(
            listOf(Tags.name, Tags.deleted, Tags.character, Tags.macroName),
            SpanStyle(color = GitHubLightColors.variable)
        ),
        TagStyleSpec(
            listOf(Tags.propertyName),
            SpanStyle(color = GitHubLightColors.typeName)
        ),
        TagStyleSpec(
            listOf(Tags.function(Tags.variableName), Tags.labelName),
            SpanStyle(color = GitHubLightColors.function)
        ),
        TagStyleSpec(
            listOf(Tags.color, Tags.constant(Tags.name), Tags.standard(Tags.name)),
            SpanStyle(color = GitHubLightColors.constant)
        ),
        TagStyleSpec(
            listOf(Tags.definition(Tags.name), Tags.separator),
            SpanStyle(color = GitHubLightColors.foreground)
        ),
        TagStyleSpec(
            listOf(
                Tags.typeName,
                Tags.className,
                Tags.number,
                Tags.changed,
                Tags.annotation,
                Tags.modifier,
                Tags.self,
                Tags.namespace
            ),
            SpanStyle(color = GitHubLightColors.typeName)
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
            SpanStyle(color = GitHubLightColors.keyword)
        ),
        TagStyleSpec(
            listOf(Tags.meta, Tags.comment),
            SpanStyle(color = GitHubLightColors.comment)
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
                color = GitHubLightColors.typeName,
                textDecoration = TextDecoration.Underline
            )
        ),
        TagStyleSpec(
            Tags.heading,
            SpanStyle(
                fontWeight = FontWeight.Bold,
                color = GitHubLightColors.foreground
            )
        ),
        TagStyleSpec(
            listOf(Tags.atom, Tags.bool, Tags.special(Tags.variableName)),
            SpanStyle(color = GitHubLightColors.constant)
        ),
        TagStyleSpec(
            listOf(Tags.processingInstruction, Tags.string, Tags.inserted),
            SpanStyle(color = GitHubLightColors.string)
        ),
        TagStyleSpec(Tags.invalid, SpanStyle(color = Color(0xFFFF0000)))
    )
)

/**
 * Complete GitHub Light editor theme with UI styling.
 *
 * Provides background, gutter, cursor, selection, search-match,
 * bracket-match, tooltip, panel, and fold-placeholder colors matching
 * the GitHub Light Default color scheme.
 */
val gitHubLightTheme: EditorTheme = EditorTheme(
    background = GitHubLightColors.background,
    foreground = GitHubLightColors.foreground,
    cursor = GitHubLightColors.cursor,
    selection = GitHubLightColors.selection,
    activeLineBackground = GitHubLightColors.activeLineBackground,
    gutterBackground = GitHubLightColors.gutterBackground,
    gutterForeground = GitHubLightColors.gutterForeground,
    gutterActiveForeground = GitHubLightColors.gutterActiveForeground,
    gutterBorderColor = GitHubLightColors.gutterBorder,
    contentTextStyle = TextStyle(
        fontFamily = editorFontFamily,
        fontSize = 13.sp,
        lineHeight = (13 * 1.4).sp,
        color = GitHubLightColors.foreground
    ),
    searchMatchBackground = Color(0x80FFF8C5),
    searchMatchSelectedBackground = Color(0xBFFFF8C5),
    selectionMatchBackground = Color(0x30A0D000),
    matchingBracketBackground = Color(0x4400CC00),
    nonMatchingBracketBackground = Color(0x44CC0000),
    panelBackground = Color(0xFFF6F8FA),
    panelBorderColor = GitHubLightColors.gutterBorder,
    buttonBackground = Color(0xFFF3F4F6),
    buttonBorderColor = Color(0xFFD0D7DE),
    inputBackground = GitHubLightColors.background,
    inputBorderColor = GitHubLightColors.gutterBorder,
    tooltipBackground = Color(0xFFF6F8FA),
    foldPlaceholderColor = Color(0xFF57606A),
    foldPlaceholderBackground = Color(0xFFEFF2F5),
    activeLineGutterBackground = GitHubLightColors.activeLineBackground,
    dark = false
)

/**
 * Extension combining [gitHubLightTheme] UI styling with
 * [gitHubLightHighlightStyle] syntax highlighting.
 *
 * Drop this into an editor's extension list for a complete GitHub Light look.
 */
val gitHubLight: Extension = ExtensionList(
    listOf(
        editorTheme.of(gitHubLightTheme),
        syntaxHighlighting(gitHubLightHighlightStyle)
    )
)
