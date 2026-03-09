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
package com.monkopedia.kodemirror.lang.sql

import com.monkopedia.kodemirror.autocomplete.Completion
import com.monkopedia.kodemirror.autocomplete.CompletionContext
import com.monkopedia.kodemirror.autocomplete.CompletionResult
import com.monkopedia.kodemirror.autocomplete.CompletionSource
import com.monkopedia.kodemirror.autocomplete.completeFromList
import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Text

// Schema completion types
/**
 * A schema definition for SQL autocompletion. Keys are schema/table names, values
 * are either lists of column names (strings or [Completion]) or nested schema maps.
 */
typealias SqlSchema = Map<String, Any>

/**
 * Configuration for [schemaCompletionSource].
 */
data class SqlCompletionConfig(
    val dialect: SQLDialect? = null,
    val schema: SqlSchema? = null,
    val tables: List<Any>? = null,
    val schemas: List<Any>? = null,
    val defaultTable: String? = null,
    val defaultSchema: String? = null,
    val upperCaseKeywords: Boolean = false,
    val keywordCompletion: ((String, String) -> Completion)? = null
)

// ---------- Source-context helpers ----------

private fun tokenBefore(tree: SyntaxNode): SyntaxNode {
    var cursor = tree.cursor()
    cursor.moveTo(tree.from, -1)
    while (cursor.name.contains("Comment"))
        cursor.moveTo(cursor.from, -1)
    return cursor.node
}

private fun idName(doc: Text, node: SyntaxNode): String {
    val text = doc.sliceString(DocPos(node.from), DocPos(node.to))
    val quoted = Regex("""^([`'"\[])(.*)([`'"\]])$""").find(text)
    return quoted?.groupValues?.get(2) ?: text
}

private fun plainID(node: SyntaxNode?): Boolean =
    node != null && (node.name == "Identifier" || node.name == "QuotedIdentifier")

private fun pathFor(doc: Text, id: SyntaxNode): List<String> {
    if (id.name == "CompositeIdentifier") {
        val path = mutableListOf<String>()
        var ch = id.firstChild
        while (ch != null) {
            if (plainID(ch)) path.add(idName(doc, ch))
            ch = ch.nextSibling
        }
        return path
    }
    return listOf(idName(doc, id))
}

private fun parentsFor(doc: Text, node: SyntaxNode?): List<String> {
    var n = node
    val path = mutableListOf<String>()
    while (true) {
        if (n == null || n.name != ".") return path
        val name = tokenBefore(n)
        if (!plainID(name)) return path
        path.add(0, idName(doc, name))
        n = tokenBefore(name)
    }
}

private val endFrom = setOf(
    "where", "group", "having", "order", "union", "intersect", "except",
    "all", "distinct", "limit", "offset", "fetch", "for"
)

private fun getAliases(doc: Text, at: SyntaxNode): Map<String, List<String>>? {
    var statement: SyntaxNode? = null
    var parent: SyntaxNode? = at
    while (statement == null) {
        if (parent == null) return null
        if (parent.name == "Statement") {
            statement = parent
        } else {
            parent = parent.parent
        }
    }
    var aliases: MutableMap<String, List<String>>? = null
    var scan = statement!!.firstChild
    var sawFrom = false
    var prevID: SyntaxNode? = null
    while (scan != null) {
        val kw = if (scan.name == "Keyword") {
            doc.sliceString(DocPos(scan.from), DocPos(scan.to)).lowercase()
        } else {
            null
        }
        var alias: String? = null
        if (!sawFrom) {
            if (kw == "from") sawFrom = true
        } else if (kw == "as" && prevID != null && plainID(scan.nextSibling)) {
            alias = idName(doc, scan.nextSibling!!)
        } else if (kw != null && endFrom.contains(kw)) {
            break
        } else if (prevID != null && plainID(scan)) {
            alias = idName(doc, scan)
        }
        if (alias != null) {
            if (aliases == null) aliases = mutableMapOf()
            aliases[alias] = pathFor(doc, prevID!!)
        }
        prevID = if (scan.name.endsWith("Identifier")) scan else null
        scan = scan.nextSibling
    }
    return aliases
}

private data class SourceContextResult(
    val from: DocPos,
    val quoted: String?,
    val parents: List<String>,
    val empty: Boolean = false,
    val aliases: Map<String, List<String>>?
)

private fun sourceContext(state: EditorState, startPos: DocPos): SourceContextResult {
    val pos = syntaxTree(state).resolveInner(startPos.value, -1)
    val aliases = getAliases(state.doc, pos)
    return when (pos.name) {
        "Identifier", "QuotedIdentifier", "Keyword" -> SourceContextResult(
            from = DocPos(pos.from),
            quoted = if (pos.name == "QuotedIdentifier") {
                state.doc.sliceString(DocPos(pos.from), DocPos(pos.from + 1))
            } else {
                null
            },
            parents = parentsFor(state.doc, tokenBefore(pos)),
            aliases = aliases
        )
        "." -> SourceContextResult(
            from = startPos,
            quoted = null,
            parents = parentsFor(state.doc, pos),
            aliases = aliases
        )
        else -> SourceContextResult(
            from = startPos,
            quoted = null,
            parents = emptyList(),
            empty = true,
            aliases = aliases
        )
    }
}

// ---------- CompletionLevel ----------

/**
 * A tree node used to build the schema-completion hierarchy.
 */
class CompletionLevel(
    private val idQuote: String,
    private val idCaseInsensitive: Boolean
) {
    val list: MutableList<Completion> = mutableListOf()
    var children: MutableMap<String, CompletionLevel>? = null

    fun child(name: String): CompletionLevel {
        val ch = children ?: mutableMapOf<String, CompletionLevel>().also { children = it }
        return ch.getOrPut(name) {
            if (name.isNotEmpty() && list.none { it.label == name }) {
                list.add(nameCompletion(name, "type", idQuote, idCaseInsensitive))
            }
            CompletionLevel(idQuote, idCaseInsensitive)
        }
    }

    fun maybeChild(name: String): CompletionLevel? = children?.get(name)

    fun addCompletion(option: Completion) {
        val idx = list.indexOfFirst { it.label == option.label }
        if (idx > -1) list[idx] = option else list.add(option)
    }

    fun addCompletions(completions: List<Any>) {
        for (option in completions) {
            when (option) {
                is String -> addCompletion(
                    nameCompletion(option, "property", idQuote, idCaseInsensitive)
                )
                is Completion -> addCompletion(option)
            }
        }
    }

    fun addNamespace(namespace: Any) {
        when (namespace) {
            is List<*> -> {
                @Suppress("UNCHECKED_CAST")
                addCompletions(namespace as List<Any>)
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                addNamespaceObject(namespace as Map<String, Any>)
            }
        }
    }

    private fun addNamespaceObject(namespace: Map<String, Any>) {
        for ((name, children) in namespace) {
            // Handle dotted path names (escaped dots with backslash)
            val parts = name.replace(Regex("""\\?\.""")) { m ->
                if (m.value == ".") "\u0000" else m.value
            }.split("\u0000")
            var scope = this
            for (part in parts) {
                scope = scope.child(part.replace("\\.", "."))
            }
            scope.addNamespace(children)
        }
    }
}

private fun nameCompletion(
    label: String,
    type: String,
    idQuote: String,
    idCaseInsensitive: Boolean
): Completion {
    val pattern = if (idCaseInsensitive) {
        Regex("^[a-z_][a-z_\\d]*$", RegexOption.IGNORE_CASE)
    } else {
        Regex("^[a-z_][a-z_\\d]*$")
    }
    return if (pattern.matches(label)) {
        Completion(label = label, type = type)
    } else {
        Completion(
            label = label,
            type = type,
            apply = idQuote + label + getClosingQuote(idQuote)
        )
    }
}

private fun getClosingQuote(openingQuote: String): String =
    if (openingQuote == "[") "]" else openingQuote

private fun maybeQuoteCompletions(
    openingQuote: String,
    closingQuote: String,
    completions: List<Completion>
): List<Completion> {
    return completions.map { c ->
        val newLabel = if (c.label.startsWith(openingQuote)) {
            c.label
        } else {
            openingQuote + c.label + closingQuote
        }
        c.copy(label = newLabel, apply = null)
    }
}

private val spanPattern = Regex("^\\w*$")
private val quotedSpanPattern = Regex("^[`'\"\\[]?\\w*[`'\"\\]]?$")

/**
 * Create a completion source from a schema definition.
 *
 * @param schema The schema map (schema -> table -> columns).
 * @param tables Optional flat list of tables.
 * @param schemas Optional flat list of schemas.
 * @param defaultTableName Optional default table name for column completion.
 * @param defaultSchemaName Optional default schema name.
 * @param dialect The SQL dialect (used for identifier quoting settings).
 */
fun completeFromSchema(
    schema: SqlSchema,
    tables: List<Any>? = null,
    schemas: List<Any>? = null,
    defaultTableName: String? = null,
    defaultSchemaName: String? = null,
    dialect: SQLDialect? = null
): CompletionSource {
    val idQuote = dialect?.spec?.identifierQuotes?.firstOrNull()?.toString() ?: "\""
    val idCaseInsensitive = dialect?.spec?.caseInsensitiveIdentifiers ?: false
    val top = CompletionLevel(idQuote, idCaseInsensitive)
    val defaultSchema = if (defaultSchemaName != null) top.child(defaultSchemaName) else null
    top.addNamespace(schema)
    if (tables != null) (defaultSchema ?: top).addCompletions(tables)
    if (schemas != null) top.addCompletions(schemas)
    if (defaultSchema != null) top.addCompletions(defaultSchema.list)
    if (defaultTableName != null) {
        top.addCompletions((defaultSchema ?: top).child(defaultTableName).list)
    }

    return { context: CompletionContext ->
        val ctx = sourceContext(context.state, context.pos)
        if (ctx.empty && !context.explicit) {
            null
        } else {
            var parents = ctx.parents
            if (ctx.aliases != null && parents.size == 1) {
                parents = ctx.aliases[parents[0]] ?: parents
            }
            var level = top
            var found = true
            for (name in parents) {
                while (level.children == null || !level.children!!.containsKey(name)) {
                    if (level === top && defaultSchema != null) {
                        level = defaultSchema
                    } else if (level === defaultSchema && defaultTableName != null) {
                        level = level.child(defaultTableName)
                    } else {
                        found = false
                        break
                    }
                }
                if (!found) break
                val next = level.maybeChild(name)
                if (next == null) {
                    found = false
                    break
                }
                level = next
            }
            if (!found) {
                null
            } else {
                var options = level.list.toList()
                if (level === top && ctx.aliases != null) {
                    options = options + ctx.aliases.keys.map {
                        Completion(label = it, type = "constant")
                    }
                }
                val quoted = ctx.quoted
                if (quoted != null) {
                    val openingQuote = quoted
                    val closingQuote = getClosingQuote(openingQuote)
                    val quoteAfter = context.state.sliceDoc(
                        context.pos,
                        context.pos + 1
                    ) == closingQuote
                    CompletionResult(
                        from = ctx.from,
                        to = if (quoteAfter) context.pos + 1 else null,
                        options = maybeQuoteCompletions(openingQuote, closingQuote, options),
                        validFor = quotedSpanPattern
                    )
                } else {
                    CompletionResult(
                        from = ctx.from,
                        options = options,
                        validFor = spanPattern
                    )
                }
            }
        }
    }
}

private fun completionType(tokenType: Int): String = when (tokenType) {
    TYPE -> "type"
    KEYWORD -> "keyword"
    else -> "variable"
}

private fun completeKeywords(
    words: Map<String, Int>,
    upperCase: Boolean,
    build: ((String, String) -> Completion)?
): CompletionSource {
    val completions = words.keys.map { keyword ->
        val label = if (upperCase) keyword.uppercase() else keyword
        val type = completionType(words[keyword] ?: IDENTIFIER)
        build?.invoke(label, type) ?: Completion(label = label, type = type, boost = -1)
    }
    val excluded = listOf("QuotedIdentifier", "String", "LineComment", "BlockComment", ".")
    return { context: CompletionContext ->
        // Only complete when not inside excluded node types
        val nodeAtCursor = syntaxTree(context.state).resolveInner(context.pos.value, -1)
        if (excluded.contains(nodeAtCursor.name)) {
            null
        } else {
            completeFromList(completions)(context)
        }
    }
}

/**
 * Returns a completion source that provides keyword completion for the given SQL dialect.
 *
 * @param dialect The SQL dialect.
 * @param upperCase Whether to return keywords in upper case.
 * @param build Optional custom function to build completions from (label, type).
 */
fun keywordCompletionSource(
    dialect: SQLDialect,
    upperCase: Boolean = false,
    build: ((String, String) -> Completion)? = null
): CompletionSource = completeKeywords(dialect.dialect.words, upperCase, build)

/**
 * Returns a completion source that provides schema-based completion for the given config.
 */
fun schemaCompletionSource(config: SqlCompletionConfig): CompletionSource {
    val schema = config.schema ?: return { _ -> null }
    return completeFromSchema(
        schema,
        config.tables,
        config.schemas,
        config.defaultTable,
        config.defaultSchema,
        config.dialect ?: StandardSQL
    )
}
