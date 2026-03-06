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

import com.monkopedia.kodemirror.language.IndentContext
import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.StringStream

private val sassPropertyKeywords = setOf(
    "align-content", "align-items", "align-self", "animation", "animation-delay",
    "animation-direction", "animation-duration", "animation-fill-mode",
    "animation-iteration-count", "animation-name", "animation-play-state",
    "animation-timing-function", "backface-visibility", "background",
    "background-attachment", "background-clip", "background-color",
    "background-image", "background-origin", "background-position",
    "background-repeat", "background-size", "border", "border-bottom",
    "border-bottom-color", "border-bottom-left-radius", "border-bottom-right-radius",
    "border-bottom-style", "border-bottom-width", "border-collapse", "border-color",
    "border-image", "border-left", "border-left-color", "border-left-style",
    "border-left-width", "border-radius", "border-right", "border-right-color",
    "border-right-style", "border-right-width", "border-spacing", "border-style",
    "border-top", "border-top-color", "border-top-left-radius", "border-top-right-radius",
    "border-top-style", "border-top-width", "border-width", "bottom", "box-shadow",
    "box-sizing", "color", "column-count", "column-gap", "column-rule", "column-width",
    "columns", "content", "cursor", "direction", "display", "fill", "flex", "flex-basis",
    "flex-direction", "flex-flow", "flex-grow", "flex-shrink", "flex-wrap", "float",
    "font", "font-family", "font-size", "font-style", "font-variant", "font-weight",
    "grid", "grid-area", "grid-auto-columns", "grid-auto-flow", "grid-auto-rows",
    "grid-column", "grid-row", "grid-template", "grid-template-areas",
    "grid-template-columns", "grid-template-rows", "height", "justify-content",
    "left", "letter-spacing", "line-height", "list-style", "list-style-image",
    "list-style-position", "list-style-type", "margin", "margin-bottom", "margin-left",
    "margin-right", "margin-top", "max-height", "max-width", "min-height", "min-width",
    "opacity", "order", "outline", "outline-color", "outline-offset", "outline-style",
    "outline-width", "overflow", "overflow-x", "overflow-y", "padding", "padding-bottom",
    "padding-left", "padding-right", "padding-top", "perspective", "position", "right",
    "stroke", "tab-size", "table-layout", "text-align", "text-decoration", "text-indent",
    "text-overflow", "text-shadow", "text-transform", "top", "transform", "transform-origin",
    "transition", "transition-delay", "transition-duration", "transition-property",
    "transition-timing-function", "vertical-align", "visibility", "white-space",
    "width", "word-break", "word-spacing", "word-wrap", "z-index"
)

private val sassColorKeywords = setOf(
    "aliceblue", "antiquewhite", "aqua", "aquamarine", "azure", "beige", "bisque",
    "black", "blanchedalmond", "blue", "blueviolet", "brown", "burlywood",
    "cadetblue", "chartreuse", "chocolate", "coral", "cornflowerblue", "cornsilk",
    "crimson", "cyan", "darkblue", "darkcyan", "darkgoldenrod", "darkgray",
    "darkgreen", "darkkhaki", "darkmagenta", "darkolivegreen", "darkorange",
    "darkorchid", "darkred", "darksalmon", "darkseagreen", "darkslateblue",
    "darkslategray", "darkturquoise", "darkviolet", "deeppink", "deepskyblue",
    "dimgray", "dodgerblue", "firebrick", "floralwhite", "forestgreen", "fuchsia",
    "gainsboro", "ghostwhite", "gold", "goldenrod", "gray", "green", "greenyellow",
    "honeydew", "hotpink", "indianred", "indigo", "ivory", "khaki", "lavender",
    "lavenderblush", "lawngreen", "lemonchiffon", "lightblue", "lightcoral",
    "lightcyan", "lightgoldenrodyellow", "lightgray", "lightgreen", "lightpink",
    "lightsalmon", "lightseagreen", "lightskyblue", "lightslategray", "lightsteelblue",
    "lightyellow", "lime", "limegreen", "linen", "magenta", "maroon",
    "mediumaquamarine", "mediumblue", "mediumorchid", "mediumpurple", "mediumseagreen",
    "mediumslateblue", "mediumspringgreen", "mediumturquoise", "mediumvioletred",
    "midnightblue", "mintcream", "mistyrose", "moccasin", "navajowhite", "navy",
    "oldlace", "olive", "olivedrab", "orange", "orangered", "orchid", "palegoldenrod",
    "palegreen", "paleturquoise", "palevioletred", "papayawhip", "peachpuff", "peru",
    "pink", "plum", "powderblue", "purple", "red", "rosybrown", "royalblue",
    "saddlebrown", "salmon", "sandybrown", "seagreen", "seashell", "sienna", "silver",
    "skyblue", "slateblue", "slategray", "snow", "springgreen", "steelblue", "tan",
    "teal", "thistle", "tomato", "turquoise", "violet", "wheat", "white",
    "whitesmoke", "yellow", "yellowgreen"
)

private val sassValueKeywords = setOf(
    "absolute", "auto", "baseline", "block", "bold", "bolder", "border-box", "bottom",
    "break-all", "capitalize", "center", "circle", "collapse", "column", "content-box",
    "cover", "dashed", "default", "disc", "dotted", "double", "ease", "ease-in",
    "ease-in-out", "ease-out", "fill", "fixed", "flex", "flex-end", "flex-start",
    "hidden", "horizontal", "inherit", "initial", "inline", "inline-block", "inline-flex",
    "inset", "italic", "justify", "large", "left", "lighter", "line-through", "linear",
    "medium", "middle", "monospace", "none", "normal", "nowrap", "oblique", "outside",
    "overline", "padding-box", "pointer", "relative", "repeat", "repeat-x", "repeat-y",
    "right", "row", "row-reverse", "sans-serif", "scroll", "serif", "solid",
    "space-around", "space-between", "square", "static", "stretch", "table",
    "table-cell", "table-row", "text", "top", "transparent", "underline", "uppercase",
    "vertical", "visible", "wrap", "wrap-reverse"
)

private val sassFontPropertiesSet = setOf("font-family", "font-weight", "font-style", "src")

private val sassKeywordsSet = listOf("true", "false", "null", "auto")
private val sassKeywordsRegexp = Regex("^(${sassKeywordsSet.joinToString("|")})")
private val sassOperatorParts = listOf(
    "\\(", "\\)", "=", ">", "<", "==", ">=", "<=", "\\+", "-",
    "\\!=", "/", "\\*", "%", "and", "or", "not", ";", "\\{", "\\}", ":"
)
private val sassOpRegexp = Regex("^(${sassOperatorParts.joinToString("|")})")
private val sassPseudoElementsRegexp = Regex("^::?[a-zA-Z_][\\w\\-]*")

data class SassScopeEntry(val offset: Int)

data class SassState(
    var tokenizer: ((StringStream, SassState) -> String?)? = null,
    var scopes: MutableList<SassScopeEntry> = mutableListOf(SassScopeEntry(0)),
    var indentCount: Int = 0,
    var cursorHalf: Int = 0,
    var prevProp: String = "",
    var lastToken: Pair<String?, String>? = null
)

private fun sassIsEndLine(stream: StringStream): Boolean {
    return stream.peek() == null || stream.match(Regex("\\s+$"), consume = false) != null
}

private fun sassUrlTokens(stream: StringStream, state: SassState): String? {
    val ch = stream.peek()
    return when (ch) {
        ")" -> {
            stream.next()
            state.tokenizer = ::sassTokenBase
            "operator"
        }
        "(" -> {
            stream.next()
            stream.eatSpace()
            "operator"
        }
        "'", "\"" -> {
            state.tokenizer = sassBuildStringTokenizer(stream.next()!!)
            "string"
        }
        else -> {
            state.tokenizer = sassBuildStringTokenizer(")", false)
            "string"
        }
    }
}

private fun sassComment(
    indentation: Int,
    multiLine: Boolean
): (StringStream, SassState) -> String? = fn@{ stream, state ->
    if (stream.sol() && stream.indentation() <= indentation) {
        state.tokenizer = null
        // Return null without consuming to let the framework re-call with sassTokenBase.
        // Calling sassTokenBase directly here caused infinite mutual recursion.
        return@fn null
    }
    if (multiLine && stream.skipTo("*/")) {
        stream.next()
        stream.next()
        state.tokenizer = ::sassTokenBase
    } else {
        stream.skipToEnd()
    }
    "comment"
}

private fun sassBuildStringTokenizer(
    quote: String,
    greedy: Boolean = true
): (StringStream, SassState) -> String? {
    fun stringTokenizer(stream: StringStream, state: SassState): String? {
        val nextChar = stream.next() ?: return "string"
        val peekChar = stream.peek()
        val prevCharStr = stream.string.getOrNull(stream.pos - 2)?.toString()
        val endingString = (nextChar != "\\" && peekChar == quote) ||
            (nextChar == quote && prevCharStr != "\\")
        if (endingString) {
            if (nextChar != quote && greedy) stream.next()
            if (sassIsEndLine(stream)) state.cursorHalf = 0
            state.tokenizer = ::sassTokenBase
            return "string"
        } else if (nextChar == "#" && peekChar == "{") {
            state.tokenizer = sassBuildInterpolationTokenizer(::stringTokenizer)
            stream.next()
            return "operator"
        }
        return "string"
    }
    return ::stringTokenizer
}

private fun sassBuildInterpolationTokenizer(
    currentTokenizer: (StringStream, SassState) -> String?
): (StringStream, SassState) -> String? = { stream, state ->
    if (stream.peek() == "}") {
        stream.next()
        state.tokenizer = currentTokenizer
        "operator"
    } else {
        sassTokenBase(stream, state)
    }
}

private fun sassIndent(state: SassState, stream: StringStream) {
    if (state.indentCount == 0) {
        state.indentCount++
        val lastScopeOffset = state.scopes[0].offset
        val currentOffset = lastScopeOffset + stream.indentUnit
        state.scopes.add(0, SassScopeEntry(currentOffset))
    }
}

private fun sassDedent(state: SassState) {
    if (state.scopes.size == 1) return
    state.scopes.removeFirst()
}

private fun sassTokenBase(stream: StringStream, state: SassState): String? {
    val ch = stream.peek() ?: return null

    if (stream.match("/*")) {
        state.tokenizer = sassComment(stream.indentation(), true)
        return state.tokenizer!!(stream, state)
    }
    if (stream.match("//")) {
        state.tokenizer = sassComment(stream.indentation(), false)
        return state.tokenizer!!(stream, state)
    }
    if (stream.match("#{")) {
        state.tokenizer = sassBuildInterpolationTokenizer(::sassTokenBase)
        return "operator"
    }
    if (ch == "\"" || ch == "'") {
        stream.next()
        state.tokenizer = sassBuildStringTokenizer(ch)
        return "string"
    }

    if (state.cursorHalf == 0) {
        if (ch == "-") {
            if (stream.match(Regex("^-\\w+-")) != null) return "meta"
        }
        if (ch == ".") {
            stream.next()
            if (stream.match(Regex("^[\\w-]+")) != null) {
                sassIndent(state, stream)
                return "qualifier"
            } else if (stream.peek() == "#") {
                sassIndent(state, stream)
                return "tag"
            }
        }
        if (ch == "#") {
            stream.next()
            if (stream.match(Regex("^[\\w-]+")) != null) {
                sassIndent(state, stream)
                return "builtin"
            }
            if (stream.peek() == "#") {
                sassIndent(state, stream)
                return "tag"
            }
        }
        if (ch == "\$") {
            stream.next()
            stream.eatWhile(Regex("[\\w-]"))
            return "variable-2"
        }
        if (stream.match(Regex("^-?[0-9.]+")) != null) return "number"
        if (stream.match(Regex("^(px|em|in)\\b")) != null) return "unit"
        if (stream.match(sassKeywordsRegexp) != null) return "keyword"
        if (stream.match(Regex("^url")) != null && stream.peek() == "(") {
            state.tokenizer = ::sassUrlTokens
            return "atom"
        }
        if (ch == "=") {
            if (stream.match(Regex("^=[\\w-]+")) != null) {
                sassIndent(state, stream)
                return "meta"
            }
        }
        if (ch == "+") {
            if (stream.match(Regex("^\\+[\\w-]+")) != null) return "meta"
        }
        if (ch == "@") {
            if (stream.match("@extend")) {
                if (stream.match(Regex("\\s*[\\w]"), consume = false) == null) sassDedent(state)
            }
        }
        if (stream.match(
                Regex("^@(else if|if|media|else|for|each|while|mixin|function)")
            ) != null
        ) {
            sassIndent(state, stream)
            return "def"
        }
        if (ch == "@") {
            stream.next()
            stream.eatWhile(Regex("[\\w-]"))
            return "def"
        }
        if (stream.eatWhile(Regex("[\\w-]"))) {
            val word = stream.current().lowercase()
            val prop = state.prevProp + "-" + word
            return when {
                stream.match(Regex(" *: *[\\w\\-+\$#!(\"']"), consume = false) != null -> when {
                    sassPropertyKeywords.contains(prop) -> "property"
                    sassPropertyKeywords.contains(word) -> {
                        state.prevProp = word
                        "property"
                    }
                    sassFontPropertiesSet.contains(word) -> "property"
                    else -> "tag"
                }
                stream.match(Regex(" *:"), consume = false) != null -> {
                    sassIndent(state, stream)
                    state.cursorHalf = 1
                    state.prevProp = word
                    "property"
                }
                stream.match(Regex(" *,"), consume = false) != null -> "tag"
                else -> {
                    sassIndent(state, stream)
                    "tag"
                }
            }
        }
        if (ch == ":") {
            if (stream.match(sassPseudoElementsRegexp) != null) return "type"
            stream.next()
            state.cursorHalf = 1
            return "operator"
        }
    } else {
        if (ch == "#") {
            stream.next()
            if (stream.match(Regex("[0-9a-fA-F]{6}|[0-9a-fA-F]{3}")) != null) {
                if (sassIsEndLine(stream)) state.cursorHalf = 0
                return "number"
            }
        }
        if (stream.match(Regex("^-?[0-9.]+")) != null) {
            if (sassIsEndLine(stream)) state.cursorHalf = 0
            return "number"
        }
        if (stream.match(Regex("^(px|em|in)\\b")) != null) {
            if (sassIsEndLine(stream)) state.cursorHalf = 0
            return "unit"
        }
        if (stream.match(sassKeywordsRegexp) != null) {
            if (sassIsEndLine(stream)) state.cursorHalf = 0
            return "keyword"
        }
        if (stream.match(Regex("^url")) != null && stream.peek() == "(") {
            state.tokenizer = ::sassUrlTokens
            if (sassIsEndLine(stream)) state.cursorHalf = 0
            return "atom"
        }
        if (ch == "\$") {
            stream.next()
            stream.eatWhile(Regex("[\\w-]"))
            if (sassIsEndLine(stream)) state.cursorHalf = 0
            return "variable-2"
        }
        if (ch == "!") {
            stream.next()
            state.cursorHalf = 0
            return if (stream.match(Regex("^[\\w]+")) != null) "keyword" else "operator"
        }
        if (stream.match(sassOpRegexp) != null) {
            if (sassIsEndLine(stream)) state.cursorHalf = 0
            return "operator"
        }
        if (stream.eatWhile(Regex("[\\w-]"))) {
            if (sassIsEndLine(stream)) state.cursorHalf = 0
            val word = stream.current().lowercase()
            return when {
                sassValueKeywords.contains(word) -> "atom"
                sassColorKeywords.contains(word) -> "keyword"
                sassPropertyKeywords.contains(word) -> {
                    state.prevProp = word
                    "property"
                }
                else -> "tag"
            }
        }
        if (sassIsEndLine(stream)) {
            state.cursorHalf = 0
            return null
        }
    }

    if (stream.match(sassOpRegexp) != null) return "operator"
    stream.next()
    return null
}

private fun sassTokenLexer(stream: StringStream, state: SassState): String? {
    if (stream.sol()) state.indentCount = 0
    val style = (state.tokenizer ?: ::sassTokenBase)(stream, state)
    val current = stream.current()
    if (current == "@return" || current == "}") sassDedent(state)
    if (style != null) {
        val startOfToken = stream.pos - current.length
        val withCurrentIndent = startOfToken + (stream.indentUnit * state.indentCount)
        state.scopes = state.scopes.filter { it.offset <= withCurrentIndent }.toMutableList()
        if (state.scopes.isEmpty()) state.scopes.add(SassScopeEntry(0))
    }
    return style
}

/** Stream parser for Sass (indented syntax). */
val sassLegacy: StreamParser<SassState> = object : StreamParser<SassState> {
    override val name: String get() = "sass"

    override fun startState(indentUnit: Int) = SassState()

    override fun copyState(state: SassState) = state.copy(
        scopes = state.scopes.toMutableList()
    )

    override fun token(stream: StringStream, state: SassState): String? {
        val style = sassTokenLexer(stream, state)
        state.lastToken = Pair(style, stream.current())
        return style
    }

    override fun indent(state: SassState, textAfter: String, context: IndentContext): Int {
        return state.scopes[0].offset
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "line" to "//",
                "block" to mapOf("open" to "/*", "close" to "*/")
            )
        )
}
