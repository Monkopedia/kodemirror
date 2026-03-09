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

import com.monkopedia.kodemirror.lezer.common.Input
import com.monkopedia.kodemirror.lezer.common.NodeProp
import com.monkopedia.kodemirror.lezer.common.Parser
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.FacetEnabler
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.Transaction

// Internal state field holding the parsed tree.
// Defined first so the `language` facet can reference it in enables.
// The lambdas inside capture `language` lazily (not at field-init time).
private val languageStateField: StateField<LanguageState> = StateField.define(
    StateFieldSpec(
        create = { state -> LanguageState.init(state) },
        update = { value, tr ->
            if (tr.startState.facet(language) != tr.state.facet(language)) {
                LanguageState.init(tr.state)
            } else {
                value.apply(tr)
            }
        }
    )
)

/**
 * The facet used to associate a language with an editor state.
 *
 * Only one language can be active at a time. Providing multiple language
 * extensions will throw [IllegalStateException].
 */
val language: Facet<Language, Language?> = Facet.define(
    combine = { languages ->
        require(languages.size <= 1) {
            "Multiple language extensions configured: " +
                "${languages.map { it.name }.filter { it.isNotEmpty() }}. " +
                "Only one language can be active at a time."
        }
        languages.firstOrNull()
    },
    enables = FacetEnabler.StaticExtension(languageStateField)
)

/**
 * A language object manages parsing and per-language metadata.
 * Parse data is managed as a Lezer tree.
 */
open class Language(
    val parser: Parser,
    val name: String = ""
) {
    /** The extension value to install this as the document language. */
    val extension: Extension = language.of(this)

    /** Whether this language allows nesting other languages inside it. */
    open val allowsNesting: Boolean get() = true
}

/**
 * Bundles a [Language] with optional supporting extensions.
 */
class LanguageSupport(
    val language: Language,
    val support: Extension? = null
) {
    val extension: Extension = if (support != null) {
        ExtensionList(listOf(language.extension, support))
    } else {
        language.extension
    }
}

/**
 * A [NodeProp] that can be attached to the top node of a language's
 * syntax tree to associate language metadata with that node type.
 *
 * The value is a [Facet] that can be queried for per-language data.
 */
val languageDataProp: NodeProp<Facet<*, *>> = NodeProp()

/**
 * Create a new language-specific data facet.
 *
 * @param baseData Optional base values that are always present.
 */
fun defineLanguageFacet(
    baseData: List<Extension> = emptyList()
): Facet<Extension, List<Extension>> = Facet.define(
    combine = { values -> baseData + values }
)

/**
 * Query language-specific data at a given position by walking up the syntax
 * tree and looking for nodes with [languageDataProp] attached.
 *
 * Returns the facet value from the first node that has language data, or null.
 */
@Suppress("UNCHECKED_CAST")
fun <T> languageDataAt(state: EditorState, facet: Facet<T, *>, pos: Int): T? {
    val tree = syntaxTree(state)
    var node = tree.resolveInner(pos, 0)
    while (true) {
        val dataFacet = node.type.prop(languageDataProp)
        if (dataFacet != null) {
            return state.facet(dataFacet as Facet<T, T>)
        }
        node = node.parent ?: break
    }
    return null
}

/**
 * Subclass of [Language] for LR-parser-based languages.
 *
 * Provides additional functionality for reconfiguring the parser
 * and integrating with [languageDataProp].
 */
class LRLanguage(
    parser: Parser,
    name: String = ""
) : Language(parser, name) {
    companion object {
        /**
         * Define a new LR-parser-based language.
         */
        fun define(parser: Parser, name: String = ""): LRLanguage {
            return LRLanguage(parser, name)
        }
    }
}

/**
 * An [Input] implementation that reads directly from the [Text] rope,
 * avoiding a full `toString()` copy of the document for parsing.
 */
class DocInput(private val doc: Text) : Input {
    override val length: Int get() = doc.length

    override fun chunk(pos: Int): String {
        // Return a chunk starting at pos. Use sliceString to read a
        // reasonably-sized chunk without copying the whole document.
        val chunkSize = minOf(1024, doc.length - pos)
        return doc.sliceString(DocPos(pos), DocPos(pos + chunkSize))
    }

    override fun read(from: Int, to: Int): String {
        return doc.sliceString(DocPos(from), DocPos(to))
    }

    override val lineChunks: Boolean get() = true
}

private class LanguageState(
    val tree: Tree
) {
    fun apply(tr: Transaction): LanguageState {
        if (!tr.docChanged) return this
        val lang = tr.state.facet(language) ?: return LanguageState(Tree.empty)
        val newTree = lang.parser.parse(DocInput(tr.newDoc))
        return LanguageState(newTree)
    }

    companion object {
        fun init(state: EditorState): LanguageState {
            val lang = state.facet(language) ?: return LanguageState(Tree.empty)
            val tree = lang.parser.parse(DocInput(state.doc))
            return LanguageState(tree)
        }
    }
}

/**
 * Get the syntax tree for a state, which is the current parse tree
 * of the active language, or [Tree.empty] if no language is configured.
 */
fun syntaxTree(state: EditorState): Tree {
    return state.field(languageStateField, require = false)?.tree ?: Tree.empty
}

/**
 * Get the syntax tree for the state if it covers at least up to
 * position [upto]. In Kodemirror's synchronous parsing model, the
 * tree always covers the full document, so this always returns the
 * tree when one is available.
 *
 * @param upto The minimum document position the tree must cover.
 * @param timeout Ignored in the synchronous parsing model. Present
 *   for API compatibility with upstream CodeMirror.
 * @return The syntax tree, or `null` if no language is configured.
 */
fun ensureSyntaxTree(
    state: EditorState,
    upto: Int,
    @Suppress("UNUSED_PARAMETER") timeout: Int = 0
): Tree? {
    val tree = syntaxTree(state)
    return if (tree.length == 0 && state.facet(language) == null) null else tree
}

/**
 * Check whether a complete syntax tree is available up to the
 * given position. In Kodemirror's synchronous parsing model, the
 * tree always covers the full document, so this returns `true`
 * whenever a language is configured.
 *
 * @param upto The position to check coverage for. Defaults to the
 *   full document length.
 */
fun syntaxTreeAvailable(
    state: EditorState,
    @Suppress("UNUSED_PARAMETER") upto: Int = state.doc.length
): Boolean {
    return state.facet(language) != null
}

/**
 * Check whether the syntax parser is currently running in the
 * background. In Kodemirror's synchronous parsing model, parsing
 * completes immediately on every state update, so this always
 * returns `false`.
 */
@Suppress("UNUSED_PARAMETER")
fun syntaxParserRunning(state: EditorState): Boolean {
    return false
}

/**
 * Force a complete re-parse of the document. In Kodemirror's
 * synchronous parsing model, the tree is always current, so this
 * returns the existing tree.
 *
 * This function is provided for API compatibility with upstream
 * CodeMirror, where it forces async parsing to complete
 * synchronously.
 */
fun forceParsing(state: EditorState): Tree {
    return syntaxTree(state)
}

/**
 * Metadata descriptor for a language. Used for language detection
 * and dynamic language loading.
 *
 * @param name Human-readable name of the language (e.g., "JavaScript").
 * @param alias Alternative names for the language (e.g., "js", "ecmascript").
 * @param extensions File extensions associated with the language
 *   (e.g., `listOf("js", "mjs", "cjs")`).
 * @param filename Regex patterns matching filenames for this language
 *   (e.g., `listOf(Regex("Makefile"))` for Makefiles without extensions).
 * @param load Factory function that creates a [LanguageSupport] instance.
 */
data class LanguageDescription(
    val name: String,
    val alias: List<String> = emptyList(),
    val extensions: List<String> = emptyList(),
    val filename: List<Regex> = emptyList(),
    val load: (() -> LanguageSupport)? = null
) {
    /**
     * Check whether this language matches a given file name.
     */
    fun matchFilename(filename: String): Boolean {
        val ext = filename.substringAfterLast('.', "")
        if (ext.isNotEmpty() && extensions.any { it.equals(ext, ignoreCase = true) }) {
            return true
        }
        return this.filename.any { it.containsMatchIn(filename) }
    }

    /**
     * Check whether this language matches a given name or alias.
     */
    fun matchLanguageName(name: String): Boolean {
        if (this.name.equals(name, ignoreCase = true)) return true
        return alias.any { it.equals(name, ignoreCase = true) }
    }

    companion object {
        /**
         * Find a language description matching a file name from a
         * list of descriptions.
         */
        fun matchFilename(
            descriptions: List<LanguageDescription>,
            filename: String
        ): LanguageDescription? {
            return descriptions.firstOrNull { it.matchFilename(filename) }
        }

        /**
         * Find a language description matching a language name from
         * a list of descriptions.
         */
        fun matchLanguageName(
            descriptions: List<LanguageDescription>,
            name: String
        ): LanguageDescription? {
            return descriptions.firstOrNull { it.matchLanguageName(name) }
        }
    }
}

/**
 * Comment tokens for a language, used by comment toggle commands.
 */
data class CommentTokens(
    val line: String? = null,
    val block: BlockComment? = null
) {
    data class BlockComment(val open: String, val close: String)
}

/**
 * Facet that provides comment token information for comment commands.
 * Languages should register their comment tokens via this facet.
 */
val commentTokens: Facet<CommentTokens, CommentTokens?> = Facet.define(
    combine = { values -> values.firstOrNull() }
)
