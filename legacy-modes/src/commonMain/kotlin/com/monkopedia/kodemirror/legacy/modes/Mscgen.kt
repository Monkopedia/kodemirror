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
package com.monkopedia.kodemirror.legacy.modes

import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.StringStream

data class MscgenState(
    var inComment: Boolean = false,
    var inString: Boolean = false,
    var inAttributeList: Boolean = false,
    var inScript: Boolean = false
)

private data class MscgenLangConfig(
    val keywords: List<String>?,
    val options: List<String>,
    val constants: List<String>?,
    val attributes: List<String>?,
    val brackets: List<String>,
    val arcsWords: List<String>,
    val arcsOthers: List<String>,
    val singlecomment: List<String>,
    val operators: List<String>?
)

private fun mscgenWordRegexpBoundary(words: List<String>): Regex {
    return Regex("^\\b(" + words.joinToString("|") + ")\\b", RegexOption.IGNORE_CASE)
}

private fun mscgenWordRegexp(words: List<String>): Regex {
    return Regex("^(?:" + words.joinToString("|") + ")", RegexOption.IGNORE_CASE)
}

private fun mkMscgenParser(config: MscgenLangConfig): StreamParser<MscgenState> {
    val bracketsRE = mscgenWordRegexp(config.brackets)
    val singleCommentRE = mscgenWordRegexp(config.singlecomment)
    val arcsWordsRE = mscgenWordRegexpBoundary(config.arcsWords)
    val arcsOthersRE = mscgenWordRegexp(config.arcsOthers)
    val keywordsRE = config.keywords?.let { mscgenWordRegexpBoundary(it) }
    val optionsRE = mscgenWordRegexpBoundary(config.options)
    val constantsRE = config.constants?.let { mscgenWordRegexp(it) }
    val operatorsRE = config.operators?.let { mscgenWordRegexp(it) }
    val attributesRE = config.attributes?.let { mscgenWordRegexpBoundary(it) }

    // Track attribute list state on the config level (mutable)
    var inAttributeList = false

    return object : StreamParser<MscgenState> {
        override val name: String get() = "mscgen"

        override fun startState(indentUnit: Int) = MscgenState()
        override fun copyState(state: MscgenState) = state.copy()

        @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
        override fun token(stream: StringStream, state: MscgenState): String? {
            if (stream.match(bracketsRE) != null) return "bracket"

            if (!state.inComment) {
                if (stream.match(Regex("^/\\*[^*/]*")) != null) {
                    state.inComment = true
                    return "comment"
                }
                if (stream.match(singleCommentRE) != null) {
                    stream.skipToEnd()
                    return "comment"
                }
            }
            if (state.inComment) {
                if (stream.match(Regex("^[^*/]*\\*/")) != null) {
                    state.inComment = false
                } else {
                    stream.skipToEnd()
                }
                return "comment"
            }

            if (!state.inString && stream.match(Regex("^\"(\\\\\"|[^\"])*")) != null) {
                state.inString = true
                return "string"
            }
            if (state.inString) {
                if (stream.match(Regex("^[^\"]*\"")) != null) {
                    state.inString = false
                } else {
                    stream.skipToEnd()
                }
                return "string"
            }

            if (keywordsRE != null && stream.match(keywordsRE) != null) return "keyword"
            if (stream.match(optionsRE) != null) return "keyword"
            if (stream.match(arcsWordsRE) != null) return "keyword"
            if (stream.match(arcsOthersRE) != null) return "keyword"
            if (operatorsRE != null && stream.match(operatorsRE) != null) return "operator"
            if (constantsRE != null && stream.match(constantsRE) != null) return "variable"

            if (!inAttributeList && attributesRE != null && stream.match("[")) {
                inAttributeList = true
                return "bracket"
            }
            if (inAttributeList) {
                if (attributesRE != null && stream.match(attributesRE) != null) {
                    return "attribute"
                }
                if (stream.match("]")) {
                    inAttributeList = false
                    return "bracket"
                }
            }

            stream.next()
            return null
        }

        override val languageData: Map<String, Any>
            get() = mapOf(
                "commentTokens" to mapOf(
                    "line" to "#",
                    "block" to mapOf("open" to "/*", "close" to "*/")
                )
            )
    }
}

/** Stream parser for MscGen. */
@Suppress("ktlint:standard:max-line-length")
val mscgen: StreamParser<MscgenState> = mkMscgenParser(
    MscgenLangConfig(
        keywords = listOf("msc"),
        options = listOf("hscale", "width", "arcgradient", "wordwraparcs"),
        constants = listOf("true", "false", "on", "off"),
        attributes = listOf(
            "label", "idurl", "id", "url", "linecolor", "linecolour",
            "textcolor", "textcolour", "textbgcolor", "textbgcolour",
            "arclinecolor", "arclinecolour", "arctextcolor", "arctextcolour",
            "arctextbgcolor", "arctextbgcolour", "arcskip"
        ),
        brackets = listOf("\\{", "\\}"),
        arcsWords = listOf("note", "abox", "rbox", "box"),
        arcsOthers = listOf(
            "\\|\\|\\|", "\\.\\.\\.", "---", "--", "<->", "==", "<<=>>",
            "<=>", "\\.\\.", "<<>>", "::", "<:>", "->", "=>>", "=>", ">>",
            ":>", "<-", "<<=", "<=", "<<", "<:", "x-", "-x"
        ),
        singlecomment = listOf("//", "#"),
        operators = listOf("=")
    )
)

/** Stream parser for MsGenny. */
@Suppress("ktlint:standard:max-line-length")
val msgenny: StreamParser<MscgenState> = mkMscgenParser(
    MscgenLangConfig(
        keywords = null,
        options = listOf(
            "hscale",
            "width",
            "arcgradient",
            "wordwraparcs",
            "wordwrapentities",
            "watermark"
        ),
        constants = listOf("true", "false", "on", "off", "auto"),
        attributes = null,
        brackets = listOf("\\{", "\\}"),
        arcsWords = listOf(
            "note", "abox", "rbox", "box", "alt", "else", "opt", "break",
            "par", "seq", "strict", "neg", "critical", "ignore", "consider",
            "assert", "loop", "ref", "exc"
        ),
        arcsOthers = listOf(
            "\\|\\|\\|", "\\.\\.\\.", "---", "--", "<->", "==", "<<=>>",
            "<=>", "\\.\\.", "<<>>", "::", "<:>", "->", "=>>", "=>", ">>",
            ":>", "<-", "<<=", "<=", "<<", "<:", "x-", "-x"
        ),
        singlecomment = listOf("//", "#"),
        operators = listOf("=")
    )
)

/** Stream parser for Xu. */
@Suppress("ktlint:standard:max-line-length")
val xu: StreamParser<MscgenState> = mkMscgenParser(
    MscgenLangConfig(
        keywords = listOf("msc", "xu"),
        options = listOf(
            "hscale",
            "width",
            "arcgradient",
            "wordwraparcs",
            "wordwrapentities",
            "watermark"
        ),
        constants = listOf("true", "false", "on", "off", "auto"),
        attributes = listOf(
            "label", "idurl", "id", "url", "linecolor", "linecolour",
            "textcolor", "textcolour", "textbgcolor", "textbgcolour",
            "arclinecolor", "arclinecolour", "arctextcolor", "arctextcolour",
            "arctextbgcolor", "arctextbgcolour", "arcskip", "title",
            "deactivate", "activate", "activation"
        ),
        brackets = listOf("\\{", "\\}"),
        arcsWords = listOf(
            "note", "abox", "rbox", "box", "alt", "else", "opt", "break",
            "par", "seq", "strict", "neg", "critical", "ignore", "consider",
            "assert", "loop", "ref", "exc"
        ),
        arcsOthers = listOf(
            "\\|\\|\\|", "\\.\\.\\.", "---", "--", "<->", "==", "<<=>>",
            "<=>", "\\.\\.", "<<>>", "::", "<:>", "->", "=>>", "=>", ">>",
            ":>", "<-", "<<=", "<=", "<<", "<:", "x-", "-x"
        ),
        singlecomment = listOf("//", "#"),
        operators = listOf("=")
    )
)
