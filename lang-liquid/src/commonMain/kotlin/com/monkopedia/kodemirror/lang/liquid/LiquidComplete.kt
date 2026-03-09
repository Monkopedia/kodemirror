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
package com.monkopedia.kodemirror.lang.liquid

import com.monkopedia.kodemirror.autocomplete.Completion
import com.monkopedia.kodemirror.autocomplete.CompletionContext
import com.monkopedia.kodemirror.autocomplete.CompletionResult
import com.monkopedia.kodemirror.autocomplete.CompletionSource
import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState

private fun completions(words: String, type: String): List<Completion> =
    words.split(" ").filter { it.isNotEmpty() }.map { Completion(label = it, type = type) }

/** Filter completions for Liquid. */
val Filters: List<Completion> = completions(
    "abs append at_least at_most capitalize ceil compact concat date default " +
        "divided_by downcase escape escape_once first floor join last lstrip map minus modulo " +
        "newline_to_br plus prepend remove remove_first replace replace_first " +
        "reverse round rstrip " +
        "size slice sort sort_natural split strip strip_html strip_newlines sum times truncate " +
        "truncatewords uniq upcase url_decode url_encode where",
    "function"
)

/** Tag completions for Liquid. */
val Tags: List<Completion> = completions(
    "cycle comment endcomment raw endraw echo increment decrement liquid if elsif " +
        "else endif unless endunless case endcase for endfor tablerow endtablerow break continue " +
        "assign capture endcapture render include",
    "keyword"
)

/** Expression keyword completions for Liquid. */
val Expressions: List<Completion> = completions(
    "empty forloop tablerowloop in with as",
    "keyword"
)

private val forloop: List<Completion> = completions(
    "first index index0 last length rindex",
    "property"
)

private val tablerowloop: List<Completion> = completions(
    "col col0 col_first col_last first index index0 last length rindex rindex0 row",
    "property"
)

private data class CompletionContext2(
    val type: String,
    val node: SyntaxNode? = null,
    val target: SyntaxNode? = null,
    val from: DocPos? = null
)

private fun findContext(context: CompletionContext): CompletionContext2? {
    val state = context.state
    val pos = context.pos
    val node = syntaxTree(state).resolveInner(pos.value, -1).enterUnfinishedNodesBefore(pos.value)
    val before = node.childBefore(pos.value)?.name ?: node.name
    if (node.name == "FilterName") return CompletionContext2(type = "filter", node = node)
    if (context.explicit && before == "|") return CompletionContext2(type = "filter")
    if (node.name == "TagName") return CompletionContext2(type = "tag", node = node)
    if (context.explicit && before == "{%") return CompletionContext2(type = "tag")
    if (node.name == "PropertyName" && node.parent?.name == "MemberExpression") {
        return CompletionContext2(type = "property", node = node, target = node.parent)
    }
    if (node.name == "." && node.parent?.name == "MemberExpression") {
        return CompletionContext2(type = "property", target = node.parent)
    }
    if (node.name == "MemberExpression" && before == ".") {
        return CompletionContext2(type = "property", target = node)
    }
    if (node.name == "VariableName") {
        return CompletionContext2(type = "expression", from = DocPos(node.from))
    }
    val word = context.matchBefore(Regex("""[\w\u00c0-\uffff]+$"""))
    if (word != null) return CompletionContext2(type = "expression", from = word.from)
    if (context.explicit && node.name != "CommentText" && node.name != "StringLiteral" &&
        node.name != "NumberLiteral" && node.name != "InlineComment"
    ) {
        return CompletionContext2(type = "expression")
    }
    return null
}

private fun resolveProperties(
    state: EditorState,
    node: SyntaxNode,
    context: CompletionContext,
    properties: ((List<String>, EditorState, CompletionContext) -> List<Completion>)?
): List<Completion> {
    val path = mutableListOf<String>()
    var current = node
    while (true) {
        val obj = current.getChild("Expression") ?: return emptyList()
        when {
            obj.name == "VariableName" || obj.name == "forloop" || obj.name == "tablerowloop" -> {
                val text = state.doc.sliceString(DocPos(obj.from), DocPos(obj.to))
                if (text == "forloop") return if (path.isEmpty()) forloop else emptyList()
                if (text == "tablerowloop") {
                    return if (path.isEmpty()) tablerowloop else emptyList()
                }
                path.add(0, text)
                break
            }
            obj.name == "MemberExpression" -> {
                val name = obj.getChild("PropertyName")
                if (name != null) {
                    path.add(
                        0,
                        state.doc.sliceString(DocPos(name.from), DocPos(name.to))
                    )
                }
                current = obj
            }
            obj.name == "SubscriptExpression" -> {
                val exprs = obj.getChildren("Expression")
                val expr = if (exprs.size > 1) exprs[1] else null
                val part = if (expr?.name == "StringLiteral") {
                    state.doc.sliceString(DocPos(expr.from + 1), DocPos(expr.to - 1))
                } else {
                    "[]"
                }
                path.add(0, part)
                current = obj
            }
            else -> return emptyList()
        }
    }
    return properties?.invoke(path, state, context) ?: emptyList()
}

/**
 * Configuration for [liquidCompletionSource].
 */
data class LiquidCompletionConfig(
    val filters: List<Completion>? = null,
    val tags: List<Completion>? = null,
    val variables: List<Completion>? = null,
    val properties: ((List<String>, EditorState, CompletionContext) -> List<Completion>)? = null
)

/**
 * Returns a completion source for liquid templates. Optionally takes
 * a configuration that adds additional custom completions.
 */
fun liquidCompletionSource(
    config: LiquidCompletionConfig = LiquidCompletionConfig()
): CompletionSource {
    val filters = if (config.filters != null) config.filters + Filters else Filters
    val tags = if (config.tags != null) config.tags + Tags else Tags
    val exprs = if (config.variables != null) config.variables + Expressions else Expressions
    val properties = config.properties
    return source@{ context ->
        val cx = findContext(context) ?: return@source null
        val from = cx.from ?: (cx.node?.let { DocPos(it.from) } ?: context.pos)
        val options: List<Completion> = when (cx.type) {
            "filter" -> filters
            "tag" -> tags
            "expression" -> exprs
            else -> {
                val target = cx.target ?: return@source null
                resolveProperties(context.state, target, context, properties)
            }
        }
        if (options.isEmpty()) {
            null
        } else {
            CompletionResult(
                from = from,
                options = options,
                validFor = Regex("""^[\w\u00c0-\uffff]*$""")
            )
        }
    }
}
