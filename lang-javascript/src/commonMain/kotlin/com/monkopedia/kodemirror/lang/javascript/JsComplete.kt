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
package com.monkopedia.kodemirror.lang.javascript

import com.monkopedia.kodemirror.autocomplete.Completion
import com.monkopedia.kodemirror.autocomplete.CompletionContext
import com.monkopedia.kodemirror.autocomplete.CompletionResult
import com.monkopedia.kodemirror.autocomplete.CompletionSource
import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.lezer.common.NodeWeakMap
import com.monkopedia.kodemirror.lezer.common.SyntaxNode

private val Identifier = Regex("[\\w$\\xa1-\\uffff]+")

private val dontComplete = setOf(
    "TemplateString",
    "String",
    "RegExp",
    "LineComment",
    "BlockComment",
    "VariableDefinition",
    "TypeDefinition",
    "Label",
    "PropertyDefinition",
    "PropertyName",
    "PrivatePropertyDefinition",
    "PrivatePropertyName",
    ".",
    "?."
)

/**
 * Scope-boundary node types. When walking the tree upward to collect
 * definitions, these mark where a new scope begins.
 */
private val ScopeNodes = setOf(
    "Script",
    "Block",
    "FunctionExpression",
    "FunctionDeclaration",
    "ArrowFunction",
    "MethodDeclaration",
    "ForStatement",
    "ForInStatement",
    "ForOfStatement",
    "CatchClause",
    "ClassBody"
)

/**
 * Result of [completionPath].
 *
 * @param path The dotted access segments before the final name,
 *   e.g. for `console.log` this is `["console"]`.
 * @param name The identifier being completed (e.g. `"log"`).
 * @param from The document offset where the completed text starts.
 */
data class CompletionPathResult(
    val path: List<String>,
    val name: String,
    val from: Int
)

/**
 * Extract the completion path at the cursor. Reads backwards from the
 * cursor, splitting on `.` to determine whether we're completing a
 * property access (like `console.l|`) or a bare identifier.
 *
 * Returns `null` when the cursor isn't in a completable position.
 */
fun completionPath(context: CompletionContext): CompletionPathResult? {
    val inner = syntaxTree(context.state).resolveInner(context.pos, -1)
    if (dontComplete.contains(inner.name)) return null
    val isWord = inner.name == "VariableName" ||
        inner.name == "MemberExpression" && inner.to == context.pos
    if (!isWord && !context.explicit) return null

    val textBefore = context.state.doc.sliceString(
        maxOf(0, context.pos - 500),
        context.pos
    )
    val match = Regex("[\\w$\\xa1-\\uffff]*$").find(textBefore)
    val matchText = match?.value ?: ""

    // Walk backwards through dots
    val path = mutableListOf<String>()
    var scanPos = context.pos - matchText.length
    while (true) {
        val dotBefore = if (scanPos > 0) {
            context.state.doc.sliceString(scanPos - 1, scanPos)
        } else {
            ""
        }
        if (dotBefore != ".") break
        scanPos--
        val idBefore = context.state.doc.sliceString(
            maxOf(0, scanPos - 500),
            scanPos
        )
        val idMatch = Regex("[\\w$\\xa1-\\uffff]*$").find(idBefore)
        val idText = idMatch?.value ?: ""
        if (idText.isEmpty()) break
        path.add(0, idText)
        scanPos -= idText.length
    }

    return CompletionPathResult(
        path = path,
        name = matchText,
        from = context.pos - matchText.length
    )
}

private fun getScope(
    doc: com.monkopedia.kodemirror.state.Text,
    node: SyntaxNode
): List<Completion> {
    val completions = mutableListOf<Completion>()
    val seen = mutableSetOf<String>()
    var child = node.firstChild
    while (child != null) {
        addCompletions(doc, child, completions, seen)
        child = child.nextSibling
    }
    return completions
}

private fun addCompletions(
    doc: com.monkopedia.kodemirror.state.Text,
    node: SyntaxNode,
    completions: MutableList<Completion>,
    seen: MutableSet<String>
) {
    when (node.name) {
        "VariableDefinition" -> {
            addName(doc, node, completions, seen, "variable")
        }
        "TypeDefinition" -> {
            addName(doc, node, completions, seen, "type")
        }
        "FunctionDeclaration" -> {
            val nameNode = node.getChild("VariableDefinition")
            if (nameNode != null) addName(doc, nameNode, completions, seen, "function")
        }
        "ClassDeclaration" -> {
            val nameNode = node.getChild("VariableDefinition")
            if (nameNode != null) addName(doc, nameNode, completions, seen, "class")
        }
        "PropertyDefinition" -> {
            addName(doc, node, completions, seen, "property")
        }
    }
    // Import declarations can introduce variable names
    if (node.name == "ImportDeclaration") {
        val specs = node.getChildren("ImportSpec")
        for (spec in specs) {
            val nameNode = spec.getChild("VariableDefinition")
            if (nameNode != null) addName(doc, nameNode, completions, seen, "variable")
        }
        // Default import
        val defaultName = node.getChild("VariableDefinition")
        if (defaultName != null) addName(doc, defaultName, completions, seen, "variable")
    }
}

private fun addName(
    doc: com.monkopedia.kodemirror.state.Text,
    node: SyntaxNode,
    completions: MutableList<Completion>,
    seen: MutableSet<String>,
    type: String
) {
    val name = doc.sliceString(node.from, node.to)
    if (name.isNotEmpty() && seen.add(name)) {
        completions.add(Completion(label = name, type = type))
    }
}

private val cache = NodeWeakMap<List<Completion>>()

/**
 * Completion source that walks the JavaScript/TypeScript syntax tree
 * upward from the cursor, collecting locally declared names
 * (variables, functions, classes, types, imports) within scope
 * boundaries.
 */
val localCompletionSource: CompletionSource = { context ->
    val inner = syntaxTree(context.state).resolveInner(context.pos, -1)
    if (dontComplete.contains(inner.name)) {
        null
    } else {
        val isWord = inner.name == "VariableName" ||
            inner.to == context.pos && (
                inner.name == "MemberExpression" ||
                    inner.name == "PropertyName"
                )
        if (!isWord && !context.explicit) {
            null
        } else {
            var options: List<Completion>? = null
            var node: SyntaxNode? = inner
            while (node != null) {
                if (ScopeNodes.contains(node.name)) {
                    val cached = cache.get(node)
                    if (cached != null) {
                        options = if (options != null) options + cached else cached
                    } else {
                        val scope = getScope(context.state.doc, node)
                        cache.set(node, scope)
                        options = if (options != null) options + scope else scope
                    }
                }
                node = node.parent
            }
            if (options != null) {
                CompletionResult(
                    from = if (isWord) {
                        inner.from
                    } else {
                        context.pos
                    },
                    options = options,
                    validFor = Identifier
                )
            } else {
                null
            }
        }
    }
}

/**
 * Create a completion source that provides property completions from
 * a scope map. The map represents a global scope object where keys
 * are property names and values are either nested maps (for object
 * properties) or `null` (for leaf names).
 *
 * Example:
 * ```kotlin
 * val globals = mapOf(
 *     "console" to mapOf("log" to null, "error" to null),
 *     "Math" to mapOf("PI" to null, "sqrt" to null)
 * )
 * val source = scopeCompletionSource(globals)
 * ```
 */
fun scopeCompletionSource(scope: Map<String, Any?>): CompletionSource = { context ->
    val pathResult = completionPath(context)
    if (pathResult != null) {
        var current: Any? = scope
        for (segment in pathResult.path) {
            current = when (current) {
                is Map<*, *> -> current[segment]
                else -> null
            }
            if (current == null) break
        }
        when (current) {
            is Map<*, *> -> {
                val options = current.keys.filterIsInstance<String>().map { key ->
                    val value = current[key]
                    val type = when (value) {
                        is Map<*, *> -> "namespace"
                        else -> "variable"
                    }
                    Completion(label = key, type = type)
                }
                CompletionResult(
                    from = pathResult.from,
                    options = options,
                    validFor = Identifier
                )
            }
            else -> null
        }
    } else {
        null
    }
}
