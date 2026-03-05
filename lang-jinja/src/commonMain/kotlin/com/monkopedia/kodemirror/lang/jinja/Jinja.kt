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
package com.monkopedia.kodemirror.lang.jinja

import com.monkopedia.kodemirror.autocomplete.CompletionConfig
import com.monkopedia.kodemirror.autocomplete.autocompletion
import com.monkopedia.kodemirror.lang.html.html
import com.monkopedia.kodemirror.language.FoldRange
import com.monkopedia.kodemirror.language.LRLanguage
import com.monkopedia.kodemirror.language.Language
import com.monkopedia.kodemirror.language.LanguageSupport
import com.monkopedia.kodemirror.language.TreeIndentContext
import com.monkopedia.kodemirror.language.delimitedIndent
import com.monkopedia.kodemirror.language.foldNodeProp
import com.monkopedia.kodemirror.language.indentNodeProp
import com.monkopedia.kodemirror.lezer.common.NestedParse
import com.monkopedia.kodemirror.lezer.common.SyntaxNodeRef
import com.monkopedia.kodemirror.lezer.common.parseMixed
import com.monkopedia.kodemirror.lezer.lr.LRParser
import com.monkopedia.kodemirror.lezer.lr.ParserConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList

private fun statementIndent(except: Regex): (TreeIndentContext) -> Int? = { context ->
    val back = except.containsMatchIn(context.textAfter)
    context.lineIndent(context.node.from) + (if (back) 0 else context.unit)
}

// All Jinja statement node names that benefit from indent and fold support.
private val allStatements = setOf(
    "IfStatement", "ForStatement", "RawStatement", "BlockStatement",
    "MacroStatement", "CallStatement", "FilterStatement", "SetStatement",
    "TransStatement", "WithStatement", "AutoescapeStatement"
)

/**
 * The core Jinja tag language (without HTML wrapping).
 */
val tagLanguage: LRLanguage = LRLanguage.define(
    parser = parser.configure(
        ParserConfig(
            props = listOf(
                jinjaHighlighting,
                indentNodeProp.add { type ->
                    when (type.name) {
                        "Tag" -> delimitedIndent(closing = "%}")
                        "IfStatement", "ForStatement" ->
                            statementIndent(Regex("""^\s*(\{%-?\s*)?(endif|endfor|else|elif)\b"""))
                        "RawStatement", "BlockStatement", "MacroStatement", "CallStatement",
                        "FilterStatement", "SetStatement", "TransStatement", "WithStatement",
                        "AutoescapeStatement" ->
                            statementIndent(Regex("""^\s*(\{%-?\s*)?end\w"""))
                        else -> null
                    }
                },
                foldNodeProp.add { type ->
                    if (type.name in allStatements || type.name == "Comment") {
                        { tree, _ ->
                            val first = tree.firstChild ?: return@add null
                            if (first.name != "Tag" && first.name != "{#") return@add null
                            val last = tree.lastChild ?: return@add null
                            val to = if (last.name == "EndTag" || last.name == "#}") {
                                last.from
                            } else {
                                tree.to
                            }
                            FoldRange(first.to, to)
                        }
                    } else {
                        null
                    }
                }
            )
        )
    ),
    name = "jinja"
)

private val baseHTML = html()

/**
 * Create a Jinja language configured to overlay HTML parsing on Text/RawText nodes.
 */
fun makeJinja(baseLanguage: Language): LRLanguage {
    val wrap = parseMixed { node: SyntaxNodeRef, _ ->
        if (node.type.isTop) {
            NestedParse(
                parser = baseLanguage.parser,
                overlay = { child: SyntaxNodeRef ->
                    if (child.name == "Text" || child.name == "RawText") true else null
                }
            )
        } else {
            null
        }
    }
    return LRLanguage.define(
        parser = (tagLanguage.parser as LRParser).configure(
            ParserConfig(wrap = wrap)
        ),
        name = "jinja"
    )
}

/**
 * A language provider for Jinja templates.
 */
val jinjaLanguage: LRLanguage = makeJinja(baseHTML.language)

/**
 * Jinja template support.
 *
 * @param config Optional configuration for completion behaviour.
 * @param base Optional base HTML language support to use (defaults to [html]).
 */
fun jinja(
    config: JinjaCompletionConfig = JinjaCompletionConfig(),
    base: LanguageSupport = baseHTML
): LanguageSupport {
    val lang = if (base.language == baseHTML.language) jinjaLanguage else makeJinja(base.language)
    val extensions = mutableListOf<Extension>()
    base.support?.let { extensions.add(it) }
    extensions.add(
        autocompletion(
            CompletionConfig(override = listOf(jinjaCompletionSource(config)))
        )
    )
    return LanguageSupport(
        lang,
        support = ExtensionList(extensions)
    )
}
