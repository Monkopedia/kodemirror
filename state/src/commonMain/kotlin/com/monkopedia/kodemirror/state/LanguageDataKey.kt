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

    /** A type-safe entry pairing a [LanguageDataKey] with its value. */
    infix fun of(value: T): LanguageDataEntry<T> = LanguageDataEntry(this, value)

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

/** A type-safe entry pairing a [LanguageDataKey] with its value. */
data class LanguageDataEntry<T>(val key: LanguageDataKey<T>, val value: T)

/**
 * A type-safe wrapper around language data maps.
 *
 * Provides typed access to language data values that are normally stored
 * as `Map<String, Any?>`. Use [languageDataMapOf] to construct instances.
 *
 * ```kotlin
 * val data = languageDataMapOf(
 *     LanguageDataKey.COMMENT_TOKENS of mapOf("line" to "//"),
 *     LanguageDataKey.WORD_CHARS of "$"
 * )
 * val tokens = data[LanguageDataKey.COMMENT_TOKENS] // Map<String, Any>?
 * ```
 */
class LanguageDataMap(private val data: Map<String, Any?>) {
    /** Look up a value by its type-safe key. Returns `null` if the key is absent. */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: LanguageDataKey<T>): T? = data[key.name] as T?

    /** Check whether the map contains a given key. */
    fun <T> contains(key: LanguageDataKey<T>): Boolean = data.containsKey(key.name)

    /** Convert back to a raw map for interop with the [languageData] facet. */
    fun toMap(): Map<String, Any?> = data
}

/**
 * Build a [LanguageDataMap] from type-safe entries.
 *
 * ```kotlin
 * val data = languageDataMapOf(
 *     LanguageDataKey.COMMENT_TOKENS of mapOf("line" to "//"),
 *     LanguageDataKey.INDENT_ON_INPUT of Regex("^\\s*\\}$")
 * )
 * ```
 */
fun languageDataMapOf(vararg entries: LanguageDataEntry<*>): LanguageDataMap =
    LanguageDataMap(entries.associate { it.key.name to it.value })
