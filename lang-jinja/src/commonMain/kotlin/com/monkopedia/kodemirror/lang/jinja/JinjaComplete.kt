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

import com.monkopedia.kodemirror.autocomplete.Completion
import com.monkopedia.kodemirror.autocomplete.CompletionContext
import com.monkopedia.kodemirror.autocomplete.CompletionResult
import com.monkopedia.kodemirror.autocomplete.CompletionSource
import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.state.EditorState

private fun completions(words: String, type: String): List<Completion> =
    words.split(" ").filter { it.isNotEmpty() }.map { Completion(label = it, type = type) }

/** Filter completions for Jinja. */
val Filters: List<Completion> = completions(
    "abs attr batch capitalize center default dictsort escape filesizeformat first float " +
        "forceescape format groupby indent int items join last length list lower map max min " +
        "pprint random reject rejectattr replace reverse round safe select selectattr slice " +
        "sort string striptags sum title tojson trim truncate unique upper " +
        "urlencode urlize wordcount wordwrap xmlattr",
    "function"
)

/** Built-in function completions for Jinja. */
val Functions: List<Completion> = completions(
    "boolean callable defined divisibleby eq escaped even filter float ge gt in integer " +
        "iterable le lower lt mapping ne none number odd sameas sequence string test undefined " +
        "upper range lipsum dict joiner namespace",
    "function"
)

/** Global variable/keyword completions for Jinja. */
val Globals: List<Completion> = completions(
    "loop super self true false varargs kwargs caller name arguments " +
        "catch_kwargs catch_varargs caller",
    "keyword"
)

/** Expression completions for Jinja (Functions + Globals). */
val Expressions: List<Completion> = Functions + Globals

/** Tag completions for Jinja. */
val Tags: List<Completion> = completions(
    "raw endraw filter endfilter trans pluralize endtrans with endwith autoescape endautoescape " +
        "if elif else endif for endfor call endcall block endblock set endset " +
        "macro endmacro import " +
        "include break continue debug do extends",
    "keyword"
)

private data class CompletionContext2(
    val type: String,
    val node: SyntaxNode? = null,
    val target: SyntaxNode? = null,
    val from: Int? = null
)

private fun findContext(context: CompletionContext): CompletionContext2? {
    val state = context.state
    val pos = context.pos
    val node = syntaxTree(state).resolveInner(pos, -1).enterUnfinishedNodesBefore(pos)
    val before = node.childBefore(pos)?.name ?: node.name
    if (node.name == "FilterName") return CompletionContext2(type = "filter", node = node)
    if (context.explicit && (before == "FilterOp" || before == "filter")) {
        return CompletionContext2(type = "filter")
    }
    if (node.name == "TagName") return CompletionContext2(type = "tag", node = node)
    if (context.explicit && before == "{%") return CompletionContext2(type = "tag")
    if (node.name == "PropertyName" && node.parent?.name == "MemberExpression") {
        return CompletionContext2(type = "prop", node = node, target = node.parent)
    }
    if (node.name == "." && node.parent?.name == "MemberExpression") {
        return CompletionContext2(type = "prop", target = node.parent)
    }
    if (node.name == "MemberExpression" && before == ".") {
        return CompletionContext2(type = "prop", target = node)
    }
    if (node.name == "VariableName") return CompletionContext2(type = "expr", from = node.from)
    if (node.name == "Comment" || node.name == "StringLiteral" || node.name == "NumberLiteral") {
        return null
    }
    val word = context.matchBefore(Regex("""[\w\u00c0-\uffff]+$"""))
    if (word != null) return CompletionContext2(type = "expr", from = word.from)
    if (context.explicit) return CompletionContext2(type = "expr")
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
            obj.name == "VariableName" -> {
                path.add(0, state.doc.sliceString(obj.from, obj.to))
                break
            }
            obj.name == "MemberExpression" -> {
                val name = obj.getChild("PropertyName")
                if (name != null) path.add(0, state.doc.sliceString(name.from, name.to))
                current = obj
            }
            else -> return emptyList()
        }
    }
    return properties?.invoke(path, state, context) ?: emptyList()
}

/**
 * Configuration for [jinjaCompletionSource].
 */
data class JinjaCompletionConfig(
    val tags: List<Completion>? = null,
    val variables: List<Completion>? = null,
    val properties: ((List<String>, EditorState, CompletionContext) -> List<Completion>)? = null
)

/**
 * Returns a completion source for Jinja templates. Optionally takes
 * a configuration that adds additional custom completions.
 */
fun jinjaCompletionSource(
    config: JinjaCompletionConfig = JinjaCompletionConfig()
): CompletionSource {
    val tags = if (config.tags != null) config.tags + Tags else Tags
    val exprs = if (config.variables != null) config.variables + Expressions else Expressions
    val properties = config.properties
    return source@{ context ->
        val cx = findContext(context) ?: return@source null
        val from = cx.from ?: (cx.node?.from ?: context.pos)
        val options: List<Completion> = when (cx.type) {
            "filter" -> Filters
            "tag" -> tags
            "expr" -> exprs
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
