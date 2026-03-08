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
package com.monkopedia.kodemirror.state

/**
 * A type-safe key for language data fields.
 *
 * Language data is associated with positions in the document via
 * the [languageData] facet. Each provider returns a map of string
 * keys to values. `LanguageDataKey` wraps a string name and provides
 * type safety when looking up values with [EditorState.languageDataAt].
 *
 * Standard keys are defined in [LanguageDataKey.Companion]. Custom
 * keys can be created with [LanguageDataKey]`("myKey")`.
 *
 * @param T The type of value this key maps to.
 * @param name The string name used as the map key in provider results.
 */
class LanguageDataKey<T>(val name: String) {
    override fun equals(other: Any?): Boolean = other is LanguageDataKey<*> && name == other.name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = "LanguageDataKey($name)"

    companion object {
        /** Extra characters to include in word detection (e.g. `"$"` for Perl). */
        val WORD_CHARS: LanguageDataKey<String> = LanguageDataKey("wordChars")

        /** Regex that triggers automatic indentation when matched against input. */
        val INDENT_ON_INPUT: LanguageDataKey<Regex> = LanguageDataKey("indentOnInput")

        /** Comment token configuration (line and/or block comment syntax). */
        val COMMENT_TOKENS: LanguageDataKey<Map<String, Any>> =
            LanguageDataKey("commentTokens")

        /** Auto-closing bracket configuration. */
        val CLOSE_BRACKETS: LanguageDataKey<Map<String, List<String>>> =
            LanguageDataKey("closeBrackets")

        /** Keyword/identifier list for autocompletion. */
        val AUTOCOMPLETE: LanguageDataKey<List<String>> = LanguageDataKey("autocomplete")

        /** Parser states where indentation should be disabled. */
        val DONT_INDENT_STATES: LanguageDataKey<List<String>> =
            LanguageDataKey("dontIndentStates")
    }
}
