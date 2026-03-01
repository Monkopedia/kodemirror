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
 */
val language: Facet<Language, Language?> = Facet.define(
    combine = { languages -> languages.firstOrNull() },
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
        return doc.sliceString(pos, pos + chunkSize)
    }

    override fun read(from: Int, to: Int): String {
        return doc.sliceString(from, to)
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
