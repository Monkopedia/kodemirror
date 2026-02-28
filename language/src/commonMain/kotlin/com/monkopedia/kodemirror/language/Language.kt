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

import com.monkopedia.kodemirror.lezer.common.Parser
import com.monkopedia.kodemirror.lezer.common.StringInput
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.FacetEnabler
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
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
class Language(
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

private class LanguageState(
    val tree: Tree
) {
    fun apply(tr: Transaction): LanguageState {
        if (!tr.docChanged) return this
        val lang = tr.state.facet(language) ?: return LanguageState(Tree.empty)
        val newTree = lang.parser.parse(StringInput(tr.newDoc.toString()))
        return LanguageState(newTree)
    }

    companion object {
        fun init(state: EditorState): LanguageState {
            val lang = state.facet(language) ?: return LanguageState(Tree.empty)
            val tree = lang.parser.parse(StringInput(state.doc.toString()))
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
