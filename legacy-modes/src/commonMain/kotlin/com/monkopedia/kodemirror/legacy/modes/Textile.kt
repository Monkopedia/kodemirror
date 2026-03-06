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

private val textileTokenStyles = mapOf(
    "addition" to "inserted",
    "attributes" to "propertyName",
    "bold" to "strong",
    "cite" to "keyword",
    "code" to "monospace",
    "definitionList" to "list",
    "deletion" to "deleted",
    "div" to "punctuation",
    "em" to "emphasis",
    "footnote" to "variable",
    "footCite" to "qualifier",
    "header" to "heading",
    "html" to "comment",
    "image" to "atom",
    "italic" to "emphasis",
    "link" to "link",
    "linkDefinition" to "link",
    "list1" to "list",
    "list2" to "list.special",
    "list3" to "list",
    "notextile" to "string.special",
    "pre" to "operator",
    "p" to "content",
    "quote" to "bracket",
    "span" to "quote",
    "specialChar" to "character",
    "strong" to "strong",
    "sub" to "content.special",
    "sup" to "content.special",
    "table" to "variableName.special",
    "tableHeading" to "operator"
)

data class TextileState(
    var mode: (StringStream, TextileState) -> String? = ::textileModeNewLayout,
    var layoutType: String? = null,
    var spanningLayout: Boolean = false,
    var tableHeading: Boolean = false,
    var header: Int = 0,
    var addition: Boolean = false,
    var bold: Boolean = false,
    var cite: Boolean = false,
    var code: Boolean = false,
    var deletion: Boolean = false,
    var em: Boolean = false,
    var footCite: Boolean = false,
    var image: Boolean = false,
    var italic: Boolean = false,
    var link: Boolean = false,
    var notextile: Boolean = false,
    var span: Boolean = false,
    var strong: Boolean = false,
    var sub: Boolean = false,
    var sup: Boolean = false
)

// RE cache system
private val textileReCache = mutableMapOf<String, Regex>()

private fun textileRe(name: String): Regex = textileReCache.getOrPut(name) { textileCreateRe(name) }

private fun textileCreateRe(name: String): Regex {
    return when (name) {
        "drawTable" -> textileMakeRe("^", textileSingles["drawTable"]!!, "$")
        "html" -> textileMakeRe(
            "^",
            textileSingles["html"]!!,
            "(?:",
            textileSingles["html"]!!,
            ")*",
            "$"
        )
        "linkDefinition" -> textileMakeRe("^", textileSingles["linkDefinition"]!!, "$")
        "listLayout" -> textileMakeRe(
            "^",
            textileSingles["list"]!!,
            textileRe("allAttributes"),
            "*\\s+"
        )
        "tableCellAttributes" -> textileMakeRe(
            "^",
            textileChoiceRe(textileSingles["tableCellAttributes"]!!, textileRe("allAttributes")),
            "+\\."
        )
        "type" -> textileMakeRe("^", textileRe("allTypes"))
        "typeLayout" -> textileMakeRe(
            "^",
            textileRe("allTypes"),
            textileRe("allAttributes"),
            "*\\.\\..?",
            "(\\s+|$)"
        )
        "attributes" -> textileMakeRe("^", textileRe("allAttributes"), "+")
        "allTypes" -> textileChoiceRe(
            textileSingles["div"]!!,
            textileSingles["foot"]!!,
            textileSingles["header"]!!,
            textileSingles["bc"]!!,
            textileSingles["bq"]!!,
            textileSingles["notextile"]!!,
            textileSingles["pre"]!!,
            textileSingles["table"]!!,
            textileSingles["para"]!!
        )
        "allAttributes" -> textileChoiceRe(
            textileAttr["selector"]!!,
            textileAttr["css"]!!,
            textileAttr["lang"]!!,
            textileAttr["align"]!!,
            textileAttr["pad"]!!
        )
        else -> textileMakeRe("^", textileSingles[name]!!)
    }
}

private val textileSingles: Map<String, Any> = mapOf(
    "bc" to "bc",
    "bq" to "bq",
    "definitionList" to Regex("- .*?:=+"),
    "definitionListEnd" to Regex(".*=:\\s*$"),
    "div" to "div",
    "drawTable" to Regex("\\|.*\\|"),
    "foot" to Regex("fn\\d+"),
    "header" to Regex("h[1-6]"),
    "html" to Regex("\\s*<(?:/)?(\\w+)(?:[^>]+)?>(?:[^<]+<\\/\\1>)?"),
    "link" to Regex("[^\"]+ \":\\S"),
    "linkDefinition" to Regex("\\[[^\\s\\]]+\\]\\S+"),
    "list" to Regex("(?:#+|\\*+)"),
    "notextile" to "notextile",
    "para" to "p",
    "pre" to "pre",
    "table" to "table",
    "tableCellAttributes" to Regex("[\\/\\\\]\\d+"),
    "tableHeading" to Regex("\\|_."),
    "tableText" to Regex("[^\"_*\\[()?+~^%@|-]+"),
    "text" to Regex("[^!\"_=*\\[(<`?+~^%@-]+")
)

private val textileAttr: Map<String, Any> = mapOf(
    "align" to Regex("(?:<>|<|>|=)"),
    "selector" to Regex("\\([^\\(][^\\)]+\\)"),
    "lang" to Regex("\\[[^\\[\\]]+\\]"),
    "pad" to Regex("(?:\\(+|\\)+){1,2}"),
    "css" to Regex("\\{[^}]+}")
)

private fun textileMakeRe(vararg parts: Any): Regex {
    val pattern = parts.joinToString("") { arg ->
        when (arg) {
            is String -> arg
            is Regex -> arg.pattern
            else -> arg.toString()
        }
    }
    return Regex(pattern)
}

private fun textileChoiceRe(vararg parts: Any): Regex {
    val choiceParts = mutableListOf<String>()
    for (part in parts) {
        val src = when (part) {
            is String -> Regex.escape(part)
            is Regex -> part.pattern
            else -> part.toString()
        }
        choiceParts.add(src)
    }
    return Regex("(?:${choiceParts.joinToString("|")})")
}

private fun textileDisabled(state: TextileState): String? {
    return when (state.layoutType) {
        "notextile" -> textileTokenStyles["notextile"]
        "code" -> textileTokenStyles["code"]
        "pre" -> textileTokenStyles["pre"]
        else -> if (state.notextile) {
            textileTokenStyles["notextile"] +
                (state.layoutType?.let { " ${textileTokenStyles[it]}" } ?: "")
        } else {
            null
        }
    }
}

private fun textileTokenStyles(state: TextileState): String? {
    val disabled = textileDisabled(state)
    if (disabled != null) return disabled

    val styles = mutableListOf<String>()
    state.layoutType?.let { textileTokenStyles[it]?.let { s -> styles.add(s) } }

    for (key in listOf(
        "addition", "bold", "cite", "code", "deletion", "em", "footCite",
        "image", "italic", "link", "span", "strong", "sub", "sup", "table", "tableHeading"
    )) {
        if (getTextileFlag(state, key)) {
            textileTokenStyles[key]?.let { styles.add(it) }
        }
    }

    if (state.layoutType == "header") {
        styles.add("${textileTokenStyles["header"]}-${state.header}")
    }

    return if (styles.isNotEmpty()) styles.joinToString(" ") else null
}

private fun getTextileFlag(state: TextileState, key: String): Boolean = when (key) {
    "addition" -> state.addition
    "bold" -> state.bold
    "cite" -> state.cite
    "code" -> state.code
    "deletion" -> state.deletion
    "em" -> state.em
    "footCite" -> state.footCite
    "image" -> state.image
    "italic" -> state.italic
    "link" -> state.link
    "notextile" -> state.notextile
    "span" -> state.span
    "strong" -> state.strong
    "sub" -> state.sub
    "sup" -> state.sup
    "table" -> state.layoutType == "table"
    "tableHeading" -> state.tableHeading
    else -> false
}

private fun setTextileFlag(state: TextileState, key: String, value: Boolean) {
    when (key) {
        "addition" -> state.addition = value
        "bold" -> state.bold = value
        "cite" -> state.cite = value
        "code" -> state.code = value
        "deletion" -> state.deletion = value
        "em" -> state.em = value
        "footCite" -> state.footCite = value
        "image" -> state.image = value
        "italic" -> state.italic = value
        "link" -> state.link = value
        "notextile" -> state.notextile = value
        "span" -> state.span = value
        "strong" -> state.strong = value
        "sub" -> state.sub = value
        "sup" -> state.sup = value
    }
}

private fun textileTogglePhraseModifier(
    stream: StringStream,
    state: TextileState,
    phraseModifier: String,
    closeRE: Regex,
    openSize: Int
): String? {
    val charBefore = if (stream.pos > openSize) {
        stream.string.getOrNull(
            stream.pos - openSize - 1
        )?.toString()
    } else {
        null
    }
    val charAfter = stream.peek()
    if (getTextileFlag(state, phraseModifier)) {
        if ((charAfter == null || Regex("\\W").containsMatchIn(charAfter)) &&
            charBefore != null && Regex("\\S").containsMatchIn(charBefore)
        ) {
            val type = textileTokenStyles(state)
            setTextileFlag(state, phraseModifier, false)
            return type
        }
    } else if ((charBefore == null || Regex("\\W").containsMatchIn(charBefore)) &&
        charAfter != null && Regex("\\S").containsMatchIn(charAfter) &&
        stream.match(Regex("^.*\\S${closeRE.pattern}(?:\\W|$)"), consume = false) != null
    ) {
        setTextileFlag(state, phraseModifier, true)
        state.mode = ::textileModeAttributes
    }
    return textileTokenStyles(state)
}

private fun textileHandlePhraseModifier(
    stream: StringStream,
    state: TextileState,
    ch: String
): String? {
    if (ch == "_") {
        return if (stream.eat("_") != null) {
            textileTogglePhraseModifier(stream, state, "italic", Regex("__"), 2)
        } else {
            textileTogglePhraseModifier(stream, state, "em", Regex("_"), 1)
        }
    }
    if (ch == "*") {
        return if (stream.eat("*") != null) {
            textileTogglePhraseModifier(stream, state, "bold", Regex("\\*\\*"), 2)
        } else {
            textileTogglePhraseModifier(stream, state, "strong", Regex("\\*"), 1)
        }
    }
    if (ch == "[") {
        if (stream.match(Regex("\\d+\\]")) != null) state.footCite = true
        return textileTokenStyles(state)
    }
    if (ch == "(") {
        val spec = stream.match(Regex("^(r|tm|c)\\)"))
        if (spec != null) return textileTokenStyles["specialChar"]
    }
    if (ch == "<" && stream.match(Regex("(\\w+)[^>]+>[^<]+<\\/\\1>")) != null) {
        return textileTokenStyles["html"]
    }
    if (ch == "?" && stream.eat("?") != null) {
        return textileTogglePhraseModifier(stream, state, "cite", Regex("\\?\\?"), 2)
    }
    if (ch == "=" && stream.eat("=") != null) {
        return textileTogglePhraseModifier(stream, state, "notextile", Regex("=="), 2)
    }
    if (ch == "-" && stream.eat("-") == null) {
        return textileTogglePhraseModifier(stream, state, "deletion", Regex("-"), 1)
    }
    if (ch == "+") return textileTogglePhraseModifier(stream, state, "addition", Regex("\\+"), 1)
    if (ch == "~") return textileTogglePhraseModifier(stream, state, "sub", Regex("~"), 1)
    if (ch == "^") return textileTogglePhraseModifier(stream, state, "sup", Regex("\\^"), 1)
    if (ch == "%") return textileTogglePhraseModifier(stream, state, "span", Regex("%"), 1)
    if (ch == "@") return textileTogglePhraseModifier(stream, state, "code", Regex("@"), 1)
    if (ch == "!") {
        val type =
            textileTogglePhraseModifier(stream, state, "image", Regex("(?:\\([^)]+\\))?!"), 1)
        stream.match(Regex("^:\\S+"))
        return type
    }
    return textileTokenStyles(state)
}

private fun textileModeNewLayout(stream: StringStream, state: TextileState): String? {
    if (stream.match(textileRe("typeLayout"), consume = false) != null) {
        state.spanningLayout = false
        state.mode = ::textileModeBlockType
        return state.mode(stream, state)
    }
    val newMode: ((StringStream, TextileState) -> String?)? = when {
        !textileDisabled(state).let { it != null } -> when {
            stream.match(textileRe("listLayout"), consume = false) != null -> ::textileModeList
            stream.match(textileRe("drawTable"), consume = false) != null -> ::textileModeTable
            stream.match(textileRe("linkDefinition"), consume = false) != null ->
                ::textileModeLinkDefinition
            stream.match(textileRe("definitionList")) != null -> ::textileModeDefinitionList
            stream.match(textileRe("html"), consume = false) != null -> ::textileModeHtml
            else -> null
        }
        else -> null
    }
    state.mode = newMode ?: ::textileModeText
    return state.mode(stream, state)
}

private fun textileModeBlockType(stream: StringStream, state: TextileState): String? {
    state.layoutType = null

    val matchResult = stream.match(textileRe("type"))
    val type = (matchResult as? MatchResult)?.value ?: return run {
        state.mode = ::textileModeText
        state.mode(stream, state)
    }

    when {
        Regex("h[1-6]").containsMatchIn(type) -> {
            state.layoutType = "header"
            state.header = type[1].digitToInt()
        }
        type == "bq" -> state.layoutType = "quote"
        type == "bc" -> state.layoutType = "code"
        Regex("fn\\d+").containsMatchIn(type) -> state.layoutType = "footnote"
        type == "notextile" -> state.layoutType = "notextile"
        type == "pre" -> state.layoutType = "pre"
        type == "div" -> state.layoutType = "div"
        type == "table" -> state.layoutType = "table"
    }

    state.mode = ::textileModeAttributes
    return textileTokenStyles(state)
}

private fun textileModeText(stream: StringStream, state: TextileState): String? {
    if (stream.match(textileRe("text")) != null) return textileTokenStyles(state)
    val ch = stream.next() ?: return null
    if (ch == "\"") {
        state.mode = ::textileModeLink
        return state.mode(stream, state)
    }
    return textileHandlePhraseModifier(stream, state, ch)
}

private fun textileModeAttributes(stream: StringStream, state: TextileState): String? {
    state.mode = ::textileModeLayoutLength
    return if (stream.match(textileRe("attributes")) != null) {
        textileTokenStyles["attributes"]
    } else {
        textileTokenStyles(state)
    }
}

private fun textileModeLayoutLength(stream: StringStream, state: TextileState): String? {
    if (stream.eat(".") != null && stream.eat(".") != null) state.spanningLayout = true
    state.mode = ::textileModeText
    return textileTokenStyles(state)
}

private fun textileModeList(stream: StringStream, state: TextileState): String? {
    val match = stream.match(textileRe("list"))
    val depth = match?.value?.length ?: 1
    val listMod = (depth - 1) % 3
    state.layoutType = when (listMod) {
        0 -> "list1"
        1 -> "list2"
        else -> "list3"
    }
    state.mode = ::textileModeAttributes
    return textileTokenStyles(state)
}

private fun textileModeLink(stream: StringStream, state: TextileState): String? {
    state.mode = ::textileModeText
    return if (stream.match(textileRe("link")) != null) {
        stream.match(Regex("\\S+"))
        textileTokenStyles["link"]
    } else {
        textileTokenStyles(state)
    }
}

private fun textileModeLinkDefinition(stream: StringStream, state: TextileState): String? {
    stream.skipToEnd()
    return textileTokenStyles["linkDefinition"]
}

private fun textileModeDefinitionList(stream: StringStream, state: TextileState): String? {
    stream.match(textileRe("definitionList"))
    state.layoutType = "definitionList"
    if (stream.match(Regex("\\s*$")) != null) {
        state.spanningLayout = true
    } else {
        state.mode = ::textileModeAttributes
    }
    return textileTokenStyles(state)
}

private fun textileModeHtml(stream: StringStream, state: TextileState): String? {
    stream.skipToEnd()
    return textileTokenStyles["html"]
}

private fun textileModeTable(stream: StringStream, state: TextileState): String? {
    state.layoutType = "table"
    state.mode = ::textileModeTableCell
    return state.mode(stream, state)
}

private fun textileModeTableCell(stream: StringStream, state: TextileState): String? {
    if (stream.match(textileRe("tableHeading")) != null) {
        state.tableHeading = true
    } else {
        stream.eat("|")
    }
    state.mode = ::textileModeTableCellAttributes
    return textileTokenStyles(state)
}

private fun textileModeTableCellAttributes(stream: StringStream, state: TextileState): String? {
    state.mode = ::textileModeTableText
    return if (stream.match(textileRe("tableCellAttributes")) != null) {
        textileTokenStyles["attributes"]
    } else {
        textileTokenStyles(state)
    }
}

private fun textileModeTableText(stream: StringStream, state: TextileState): String? {
    if (stream.match(textileRe("tableText")) != null) return textileTokenStyles(state)
    if (stream.peek() == "|") {
        state.mode = ::textileModeTableCell
        return textileTokenStyles(state)
    }
    val ch = stream.next() ?: return null
    return textileHandlePhraseModifier(stream, state, ch)
}

private fun textileBlankLine(state: TextileState) {
    val spanningLayout = state.spanningLayout
    val type = state.layoutType

    // Reset phrase modifiers
    state.addition = false
    state.bold = false
    state.cite = false
    state.code = false
    state.deletion = false
    state.em = false
    state.footCite = false
    state.image = false
    state.italic = false
    state.link = false
    state.notextile = false
    state.span = false
    state.strong = false
    state.sub = false
    state.sup = false
    state.tableHeading = false
    state.header = 0
    state.layoutType = null
    state.spanningLayout = false

    state.mode = ::textileModeNewLayout

    if (spanningLayout) {
        state.layoutType = type
        state.spanningLayout = true
    }
}

private fun textileStartNewLine(stream: StringStream, state: TextileState) {
    state.mode = ::textileModeNewLayout
    state.tableHeading = false
    if (state.layoutType == "definitionList" && state.spanningLayout &&
        stream.match(textileRe("definitionListEnd"), consume = false) != null
    ) {
        state.spanningLayout = false
    }
}

/** Stream parser for Textile. */
val textile: StreamParser<TextileState> = object : StreamParser<TextileState> {
    override val name: String get() = "textile"

    override fun startState(indentUnit: Int) = TextileState()

    override fun copyState(state: TextileState) = state.copy()

    override fun token(stream: StringStream, state: TextileState): String? {
        if (stream.sol()) textileStartNewLine(stream, state)
        return state.mode(stream, state)
    }

    override fun blankLine(state: TextileState, indentUnit: Int) {
        textileBlankLine(state)
    }
}
