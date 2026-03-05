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
package com.monkopedia.kodemirror.lang.vue

import com.monkopedia.kodemirror.lang.html.htmlLanguage
import com.monkopedia.kodemirror.lang.javascript.javascriptLanguage
import com.monkopedia.kodemirror.language.CommentTokens
import com.monkopedia.kodemirror.language.LRLanguage
import com.monkopedia.kodemirror.language.LanguageSupport
import com.monkopedia.kodemirror.language.commentTokens
import com.monkopedia.kodemirror.lezer.common.Input
import com.monkopedia.kodemirror.lezer.common.NestedParse
import com.monkopedia.kodemirror.lezer.common.SyntaxNodeRef
import com.monkopedia.kodemirror.lezer.common.parseMixed
import com.monkopedia.kodemirror.lezer.lr.LRParser
import com.monkopedia.kodemirror.lezer.lr.ParserConfig

private val exprParser = (javascriptLanguage.parser as LRParser).configure(
    ParserConfig(top = "SingleExpression")
)

private val baseParser = vueParser.configure(
    ParserConfig(props = listOf(vueHighlighting))
)

private val exprMixed = NestedParse(parser = exprParser)

private val textParser = baseParser.configure(
    ParserConfig(
        wrap = parseMixed { node, _ ->
            if (node.name == "InterpolationContent") exprMixed else null
        }
    )
)

private val attrParser = baseParser.configure(
    ParserConfig(
        wrap = parseMixed { node, _ ->
            if (node.name == "AttributeScript") exprMixed else null
        },
        top = "Attribute"
    )
)

private val textMixed = NestedParse(parser = textParser)
private val attrMixed = NestedParse(parser = attrParser)

private val atOrColonOrVDash = Regex("^(@|:|v-)")

private fun mixVue(node: SyntaxNodeRef, input: Input): NestedParse? {
    return when (node.name) {
        "Attribute" -> {
            val prefix = input.read(node.from, minOf(node.from + 3, node.to))
            if (atOrColonOrVDash.containsMatchIn(prefix)) attrMixed else null
        }
        "Text" -> textMixed
        else -> null
    }
}

private fun makeVue(base: LRLanguage): LRLanguage {
    return LRLanguage.define(
        parser = (base.parser as LRParser).configure(
            ParserConfig(
                dialect = "selfClosing",
                wrap = parseMixed(::mixVue)
            )
        ),
        name = "vue"
    )
}

/**
 * A language provider for Vue templates.
 */
val vueLanguage: LRLanguage = makeVue(htmlLanguage)

/**
 * Vue template support.
 */
fun vue(): LanguageSupport = LanguageSupport(
    vueLanguage,
    support = commentTokens.of(
        CommentTokens(
            block = CommentTokens.BlockComment("<!--", "-->")
        )
    )
)
