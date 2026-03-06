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
import com.monkopedia.kodemirror.lezer.highlight.Tags

/**
 * A default highlight style (works well with light themes).
 */
val defaultHighlightStyle = HighlightStyle.define(
    listOf(
        TagStyleSpec(Tags.meta, SpanStyle(color = Color(0xFF404740))),
        TagStyleSpec(
            Tags.link,
            SpanStyle(textDecoration = TextDecoration.Underline)
        ),
        TagStyleSpec(
            Tags.heading,
            SpanStyle(
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Bold
            )
        ),
        TagStyleSpec(Tags.emphasis, SpanStyle(fontStyle = FontStyle.Italic)),
        TagStyleSpec(Tags.strong, SpanStyle(fontWeight = FontWeight.Bold)),
        TagStyleSpec(
            Tags.strikethrough,
            SpanStyle(textDecoration = TextDecoration.LineThrough)
        ),
        TagStyleSpec(Tags.keyword, SpanStyle(color = Color(0xFF770088))),
        TagStyleSpec(
            listOf(
                Tags.atom,
                Tags.bool,
                Tags.url,
                Tags.contentSeparator,
                Tags.labelName
            ),
            SpanStyle(color = Color(0xFF221199))
        ),
        TagStyleSpec(
            listOf(Tags.literal, Tags.inserted),
            SpanStyle(color = Color(0xFF116644))
        ),
        TagStyleSpec(
            listOf(Tags.string, Tags.deleted),
            SpanStyle(color = Color(0xFFAA1111))
        ),
        TagStyleSpec(
            listOf(Tags.regexp, Tags.escape, Tags.special(Tags.string)),
            SpanStyle(color = Color(0xFFEE4400))
        ),
        TagStyleSpec(
            Tags.definition(Tags.variableName),
            SpanStyle(color = Color(0xFF0000FF))
        ),
        TagStyleSpec(
            Tags.local(Tags.variableName),
            SpanStyle(color = Color(0xFF3300AA))
        ),
        TagStyleSpec(
            listOf(Tags.typeName, Tags.namespace),
            SpanStyle(color = Color(0xFF008855))
        ),
        TagStyleSpec(Tags.className, SpanStyle(color = Color(0xFF116677))),
        TagStyleSpec(
            listOf(Tags.special(Tags.variableName), Tags.macroName),
            SpanStyle(color = Color(0xFF225566))
        ),
        TagStyleSpec(
            Tags.definition(Tags.propertyName),
            SpanStyle(color = Color(0xFF0000CC))
        ),
        TagStyleSpec(Tags.comment, SpanStyle(color = Color(0xFF994400))),
        TagStyleSpec(Tags.invalid, SpanStyle(color = Color(0xFFFF0000)))
    )
)

/**
 * One Dark inspired highlight style.
 */
val oneDarkHighlightStyle = HighlightStyle.define(
    listOf(
        TagStyleSpec(Tags.keyword, SpanStyle(color = Color(0xFFC678DD))),
        TagStyleSpec(
            listOf(
                Tags.name,
                Tags.deleted,
                Tags.character,
                Tags.propertyName,
                Tags.macroName
            ),
            SpanStyle(color = Color(0xFFE06C75))
        ),
        TagStyleSpec(
            listOf(Tags.function(Tags.variableName), Tags.labelName),
            SpanStyle(color = Color(0xFF61AFEF))
        ),
        TagStyleSpec(
            listOf(Tags.color, Tags.constant(Tags.name), Tags.standard(Tags.name)),
            SpanStyle(color = Color(0xFFD19A66))
        ),
        TagStyleSpec(
            listOf(Tags.definition(Tags.name), Tags.separator),
            SpanStyle(color = Color(0xFFABB2BF))
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
            SpanStyle(color = Color(0xFFE5C07B))
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
            SpanStyle(color = Color(0xFF56B6C2))
        ),
        TagStyleSpec(
            listOf(Tags.meta, Tags.comment),
            SpanStyle(color = Color(0xFF7D8799))
        ),
        TagStyleSpec(Tags.strong, SpanStyle(fontWeight = FontWeight.Bold)),
        TagStyleSpec(Tags.emphasis, SpanStyle(fontStyle = FontStyle.Italic)),
        TagStyleSpec(
            Tags.strikethrough,
            SpanStyle(textDecoration = TextDecoration.LineThrough)
        ),
        TagStyleSpec(Tags.link, SpanStyle(textDecoration = TextDecoration.Underline)),
        TagStyleSpec(
            Tags.heading,
            SpanStyle(
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE06C75)
            )
        ),
        TagStyleSpec(
            listOf(Tags.atom, Tags.bool, Tags.special(Tags.variableName)),
            SpanStyle(color = Color(0xFFD19A66))
        ),
        TagStyleSpec(
            listOf(Tags.processingInstruction, Tags.string, Tags.inserted),
            SpanStyle(color = Color(0xFF98C379))
        ),
        TagStyleSpec(Tags.invalid, SpanStyle(color = Color(0xFFFF0000)))
    )
)
