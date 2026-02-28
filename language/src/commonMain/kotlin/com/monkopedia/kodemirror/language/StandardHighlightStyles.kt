/*
 * Copyright 2025 Jason Monk
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
package com.monkopedia.kodemirror.language

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.monkopedia.kodemirror.lezer.highlight.tags

/**
 * A default highlight style (works well with light themes).
 */
val defaultHighlightStyle = HighlightStyle.define(
    listOf(
        TagStyleSpec(tags.meta, SpanStyle(color = Color(0xFF404740))),
        TagStyleSpec(
            tags.link,
            SpanStyle(textDecoration = TextDecoration.Underline)
        ),
        TagStyleSpec(
            tags.heading,
            SpanStyle(
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Bold
            )
        ),
        TagStyleSpec(tags.emphasis, SpanStyle(fontStyle = FontStyle.Italic)),
        TagStyleSpec(tags.strong, SpanStyle(fontWeight = FontWeight.Bold)),
        TagStyleSpec(
            tags.strikethrough,
            SpanStyle(textDecoration = TextDecoration.LineThrough)
        ),
        TagStyleSpec(tags.keyword, SpanStyle(color = Color(0xFF770088))),
        TagStyleSpec(
            listOf(
                tags.atom,
                tags.bool,
                tags.url,
                tags.contentSeparator,
                tags.labelName
            ),
            SpanStyle(color = Color(0xFF221199))
        ),
        TagStyleSpec(
            listOf(tags.literal, tags.inserted),
            SpanStyle(color = Color(0xFF116644))
        ),
        TagStyleSpec(
            listOf(tags.string, tags.deleted),
            SpanStyle(color = Color(0xFFAA1111))
        ),
        TagStyleSpec(
            listOf(tags.regexp, tags.escape, tags.special(tags.string)),
            SpanStyle(color = Color(0xFFEE4400))
        ),
        TagStyleSpec(
            tags.definition(tags.variableName),
            SpanStyle(color = Color(0xFF0000FF))
        ),
        TagStyleSpec(
            tags.local(tags.variableName),
            SpanStyle(color = Color(0xFF3300AA))
        ),
        TagStyleSpec(
            listOf(tags.typeName, tags.namespace),
            SpanStyle(color = Color(0xFF008855))
        ),
        TagStyleSpec(tags.className, SpanStyle(color = Color(0xFF116677))),
        TagStyleSpec(
            listOf(tags.special(tags.variableName), tags.macroName),
            SpanStyle(color = Color(0xFF225566))
        ),
        TagStyleSpec(
            tags.definition(tags.propertyName),
            SpanStyle(color = Color(0xFF0000CC))
        ),
        TagStyleSpec(tags.comment, SpanStyle(color = Color(0xFF994400))),
        TagStyleSpec(tags.invalid, SpanStyle(color = Color(0xFFFF0000)))
    )
)

/**
 * One Dark inspired highlight style.
 */
val oneDarkHighlightStyle = HighlightStyle.define(
    listOf(
        TagStyleSpec(tags.keyword, SpanStyle(color = Color(0xFFC678DD))),
        TagStyleSpec(
            listOf(tags.name, tags.deleted, tags.character, tags.macroName),
            SpanStyle(color = Color(0xFFE06C75))
        ),
        TagStyleSpec(
            listOf(tags.propertyName, tags.definition(tags.variableName)),
            SpanStyle(color = Color(0xFFE5C07B))
        ),
        TagStyleSpec(
            listOf(tags.function(tags.variableName), tags.labelName),
            SpanStyle(color = Color(0xFF61AFEF))
        ),
        TagStyleSpec(
            listOf(tags.color, tags.constant(tags.name), tags.standard(tags.name)),
            SpanStyle(color = Color(0xFFD19A66))
        ),
        TagStyleSpec(
            listOf(tags.definition(tags.name), tags.separator),
            SpanStyle(color = Color(0xFFABB2BF))
        ),
        TagStyleSpec(
            listOf(
                tags.typeName,
                tags.className,
                tags.number,
                tags.changed,
                tags.annotation,
                tags.modifier,
                tags.self,
                tags.namespace
            ),
            SpanStyle(color = Color(0xFFE5C07B))
        ),
        TagStyleSpec(
            listOf(
                tags.operator,
                tags.operatorKeyword,
                tags.url,
                tags.escape,
                tags.regexp,
                tags.link,
                tags.special(tags.string)
            ),
            SpanStyle(color = Color(0xFF56B6C2))
        ),
        TagStyleSpec(tags.comment, SpanStyle(color = Color(0xFF5C6370))),
        TagStyleSpec(tags.strong, SpanStyle(fontWeight = FontWeight.Bold)),
        TagStyleSpec(tags.emphasis, SpanStyle(fontStyle = FontStyle.Italic)),
        TagStyleSpec(
            tags.strikethrough,
            SpanStyle(textDecoration = TextDecoration.LineThrough)
        ),
        TagStyleSpec(tags.link, SpanStyle(textDecoration = TextDecoration.Underline)),
        TagStyleSpec(
            tags.heading,
            SpanStyle(
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE06C75)
            )
        ),
        TagStyleSpec(
            listOf(tags.atom, tags.bool, tags.special(tags.variableName)),
            SpanStyle(color = Color(0xFFD19A66))
        ),
        TagStyleSpec(
            listOf(tags.processingInstruction, tags.string, tags.inserted),
            SpanStyle(color = Color(0xFF98C379))
        ),
        TagStyleSpec(tags.invalid, SpanStyle(color = Color(0xFFFF0000)))
    )
)
