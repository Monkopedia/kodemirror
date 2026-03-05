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

data class XmlConfig(
    val autoSelfClosers: Set<String> = emptySet(),
    val implicitlyClosed: Set<String> = emptySet(),
    val contextGrabbers: Map<String, Set<String>> = emptyMap(),
    val doNotIndent: Set<String> = emptySet(),
    val allowUnquoted: Boolean = false,
    val allowMissing: Boolean = false,
    val allowMissingTagName: Boolean = false,
    val caseFold: Boolean = false,
    val htmlMode: Boolean = false,
    val matchClosing: Boolean = true,
    val multilineTagIndentPastTag: Boolean = true,
    val multilineTagIndentFactor: Int = 1,
    val alignCDATA: Boolean = false
)

private val defaultHtmlConfig = XmlConfig(
    autoSelfClosers = setOf(
        "area", "base", "br", "col", "command", "embed", "frame", "hr",
        "img", "input", "keygen", "link", "meta", "param", "source",
        "track", "wbr", "menuitem"
    ),
    implicitlyClosed = setOf(
        "dd", "li", "optgroup", "option", "p", "rp", "rt", "tbody", "td",
        "tfoot", "th", "tr"
    ),
    contextGrabbers = mapOf(
        "dd" to setOf("dd", "dt"),
        "dt" to setOf("dd", "dt"),
        "li" to setOf("li"),
        "option" to setOf("option", "optgroup"),
        "optgroup" to setOf("optgroup"),
        "p" to setOf(
            "address", "article", "aside", "blockquote", "dir", "div", "dl",
            "fieldset", "footer", "form", "h1", "h2", "h3", "h4", "h5",
            "h6", "header", "hgroup", "hr", "menu", "nav", "ol", "p",
            "pre", "section", "table", "ul"
        ),
        "rp" to setOf("rp", "rt"),
        "rt" to setOf("rp", "rt"),
        "tbody" to setOf("tbody", "tfoot"),
        "td" to setOf("td", "th"),
        "tfoot" to setOf("tbody"),
        "th" to setOf("td", "th"),
        "thead" to setOf("tbody", "tfoot"),
        "tr" to setOf("tr")
    ),
    doNotIndent = setOf("pre"),
    allowUnquoted = true,
    allowMissing = true,
    caseFold = true,
    htmlMode = true
)

private val defaultXmlConfig = XmlConfig()

class XmlContext(
    val prev: XmlContext?,
    val tagName: String,
    val indent: Int,
    val startOfLine: Boolean,
    val noIndent: Boolean = false
)

data class XmlState(
    var tokenize: Int = 0, // 0=inText, 1=inTag, 2+=inAttribute/inBlock
    var stateHandler: Int = 0, // 0=base, 1=tagName, 2=closeName, etc.
    var indented: Int = 0,
    var tagName: String? = null,
    var tagStart: Int? = null,
    var context: XmlContext? = null,
    var stringStartCol: Int = 0,
    var tokenizeData: String? = null, // for inBlock/inAttribute/doctype
    var tokenizeDepth: Int = 0, // for doctype
    var isInAttribute: Boolean = false,
    var baseIndent: Int = 0
)

fun mkXML(parserConfig: XmlConfig): StreamParser<XmlState> {
    val config = if (parserConfig.htmlMode) {
        defaultHtmlConfig
    } else {
        defaultXmlConfig
    }.let { defaults ->
        XmlConfig(
            autoSelfClosers = if (parserConfig.htmlMode) {
                defaults.autoSelfClosers
            } else {
                parserConfig.autoSelfClosers
            },
            implicitlyClosed = if (parserConfig.htmlMode) {
                defaults.implicitlyClosed
            } else {
                parserConfig.implicitlyClosed
            },
            contextGrabbers = if (parserConfig.htmlMode) {
                defaults.contextGrabbers
            } else {
                parserConfig.contextGrabbers
            },
            doNotIndent = if (parserConfig.htmlMode) {
                defaults.doNotIndent
            } else {
                parserConfig.doNotIndent
            },
            allowUnquoted = parserConfig.allowUnquoted ||
                defaults.allowUnquoted,
            allowMissing = parserConfig.allowMissing || defaults.allowMissing,
            allowMissingTagName = parserConfig.allowMissingTagName ||
                defaults.allowMissingTagName,
            caseFold = if (parserConfig.htmlMode) {
                defaults.caseFold
            } else {
                parserConfig.caseFold
            },
            htmlMode = parserConfig.htmlMode,
            matchClosing = parserConfig.matchClosing,
            multilineTagIndentPastTag =
            parserConfig.multilineTagIndentPastTag,
            multilineTagIndentFactor =
            parserConfig.multilineTagIndentFactor,
            alignCDATA = parserConfig.alignCDATA
        )
    }

    fun lower(tagName: String?): String? = tagName?.lowercase()

    fun popContext(state: XmlState) {
        if (state.context != null) state.context = state.context?.prev
    }

    fun maybePopContext(state: XmlState, nextTagName: String?) {
        while (true) {
            val ctx = state.context ?: return
            val parentTagName = ctx.tagName
            val grabbers =
                config.contextGrabbers[lower(parentTagName)] ?: return
            if (lower(nextTagName) !in grabbers) return
            popContext(state)
        }
    }

    // Tokenizer type constants:
    // 0 = inText, 1 = inTag, 10 = inAttribute('), 11 = inAttribute(")
    // 20+ = inBlock(style, terminator)

    var type: String? = null
    var setStyle: String? = null

    // State handler constants:
    // 0=base, 1=tagName, 2=closeTagName, 3=closeState,
    // 4=closeStateErr, 5=attrState, 6=attrEq, 7=attrValue, 8=attrCont

    fun handleAttrState(stateType: String, stream: StringStream, state: XmlState) {
        if (stateType == "word") {
            setStyle = "attribute"
            state.stateHandler = 6
        } else if (stateType == "endTag" || stateType == "selfcloseTag") {
            val tagName = state.tagName
            val tagStart = state.tagStart
            state.tagName = null
            state.tagStart = null
            if (stateType == "selfcloseTag" ||
                lower(tagName) in config.autoSelfClosers
            ) {
                maybePopContext(state, tagName)
            } else {
                maybePopContext(state, tagName)
                val noIndent =
                    lower(tagName) in config.doNotIndent ||
                        (state.context?.noIndent == true)
                state.context = XmlContext(
                    prev = state.context,
                    tagName = tagName ?: "",
                    indent = state.indented,
                    startOfLine = tagStart == state.indented,
                    noIndent = noIndent
                )
            }
            state.stateHandler = 0
        } else {
            setStyle = "error"
            // stay in attrState
        }
    }

    fun handleState(stateType: String, stream: StringStream, state: XmlState) {
        when (state.stateHandler) {
            0 -> { // baseState
                when (stateType) {
                    "openTag" -> {
                        state.tagStart = stream.column()
                        state.stateHandler = 1
                    }
                    "closeTag" -> state.stateHandler = 2
                }
            }
            1 -> { // tagNameState
                if (stateType == "word") {
                    state.tagName = stream.current()
                    setStyle = "tag"
                    state.stateHandler = 5
                } else if (config.allowMissingTagName &&
                    stateType == "endTag"
                ) {
                    setStyle = "angleBracket"
                    // attrState(type, stream, state)
                    handleAttrState(stateType, stream, state)
                } else {
                    setStyle = "error"
                    // stay in tagNameState
                }
            }
            2 -> { // closeTagNameState
                if (stateType == "word") {
                    val tagName = stream.current()
                    val ctx = state.context
                    if (ctx != null && ctx.tagName != tagName &&
                        lower(ctx.tagName) in config.implicitlyClosed
                    ) {
                        popContext(state)
                    }
                    if ((
                            state.context != null &&
                                state.context?.tagName == tagName
                            ) ||
                        !config.matchClosing
                    ) {
                        setStyle = "tag"
                        state.stateHandler = 3
                    } else {
                        setStyle = "error"
                        state.stateHandler = 4
                    }
                } else if (config.allowMissingTagName &&
                    stateType == "endTag"
                ) {
                    setStyle = "angleBracket"
                    // closeState
                    if (stateType != "endTag") {
                        setStyle = "error"
                    } else {
                        popContext(state)
                        state.stateHandler = 0
                    }
                } else {
                    setStyle = "error"
                    state.stateHandler = 4
                }
            }
            3 -> { // closeState
                if (stateType != "endTag") {
                    setStyle = "error"
                } else {
                    popContext(state)
                    state.stateHandler = 0
                }
            }
            4 -> { // closeStateErr
                setStyle = "error"
                if (stateType == "endTag") {
                    popContext(state)
                    state.stateHandler = 0
                }
            }
            5 -> { // attrState
                handleAttrState(stateType, stream, state)
            }
            6 -> { // attrEqState
                if (stateType == "equals") {
                    state.stateHandler = 7
                } else {
                    if (!config.allowMissing) setStyle = "error"
                    handleAttrState(stateType, stream, state)
                }
            }
            7 -> { // attrValueState
                if (stateType == "string") {
                    state.stateHandler = 8
                } else if (stateType == "word" && config.allowUnquoted) {
                    setStyle = "string"
                    state.stateHandler = 5
                } else {
                    setStyle = "error"
                    handleAttrState(stateType, stream, state)
                }
            }
            8 -> { // attrContinuedState
                if (stateType == "string") {
                    // stay
                } else {
                    handleAttrState(stateType, stream, state)
                }
            }
        }
    }

    fun inAttribute(stream: StringStream, state: XmlState, quote: String): String? {
        while (!stream.eol()) {
            if (stream.next() == quote) {
                state.tokenize = 1 // inTag
                state.isInAttribute = false
                break
            }
        }
        return "string"
    }

    fun inBlockToken(stream: StringStream, state: XmlState): String? {
        val data = state.tokenizeData ?: return null
        val colonIdx = data.indexOf(':')
        val blockStyle = data.substring(0, colonIdx)
        val terminator = data.substring(colonIdx + 1)
        while (!stream.eol()) {
            if (stream.match(terminator)) {
                state.tokenize = 0
                state.tokenizeData = null
                break
            }
            stream.next()
        }
        return blockStyle
    }

    fun doctypeToken(stream: StringStream, state: XmlState): String? {
        var depth = state.tokenizeDepth
        while (true) {
            val ch = stream.next() ?: break
            if (ch == "<") {
                depth++
                state.tokenizeDepth = depth
                return doctypeToken(stream, state)
            } else if (ch == ">") {
                if (depth == 1) {
                    state.tokenize = 0
                    state.tokenizeData = null
                    break
                } else {
                    depth--
                    state.tokenizeDepth = depth
                    return doctypeToken(stream, state)
                }
            }
        }
        return "meta"
    }

    fun inText(stream: StringStream, state: XmlState): String? {
        val ch = stream.next() ?: return null
        if (ch == "<") {
            if (stream.eat("!") != null) {
                if (stream.eat("[") != null) {
                    if (stream.match("CDATA[")) {
                        // inBlock("atom", "]]>")
                        state.tokenize = 20
                        state.tokenizeData = "atom:]]>"
                        return inBlockToken(stream, state)
                    }
                    return null
                } else if (stream.match("--")) {
                    state.tokenize = 20
                    state.tokenizeData = "comment:-->"
                    return inBlockToken(stream, state)
                } else if (stream.match("DOCTYPE", consume = true, caseInsensitive = true)) {
                    stream.eatWhile(Regex("[\\w\\._\\-]"))
                    state.tokenize = 30 // doctype depth=1
                    state.tokenizeDepth = 1
                    return doctypeToken(stream, state)
                }
                return null
            } else if (stream.eat("?") != null) {
                stream.eatWhile(Regex("[\\w\\._\\-]"))
                state.tokenize = 20
                state.tokenizeData = "meta:?>"
                return "meta"
            } else {
                type = if (stream.eat("/") != null) "closeTag" else "openTag"
                state.tokenize = 1 // inTag
                return "angleBracket"
            }
        } else if (ch == "&") {
            val ok: Boolean
            if (stream.eat("#") != null) {
                ok = if (stream.eat("x") != null) {
                    stream.eatWhile(Regex("[a-fA-F\\d]")) &&
                        stream.eat(";") != null
                } else {
                    stream.eatWhile(Regex("[\\d]")) &&
                        stream.eat(";") != null
                }
            } else {
                ok = stream.eatWhile(Regex("[\\w\\.\\-:]")) &&
                    stream.eat(";") != null
            }
            return if (ok) "atom" else "error"
        } else {
            stream.eatWhile(Regex("[^&<]"))
            return null
        }
    }

    fun inTag(stream: StringStream, state: XmlState): String? {
        val ch = stream.next() ?: return null
        if (ch == ">" || (ch == "/" && stream.eat(">") != null)) {
            state.tokenize = 0 // inText
            type = if (ch == ">") "endTag" else "selfcloseTag"
            return "angleBracket"
        } else if (ch == "=") {
            type = "equals"
            return null
        } else if (ch == "<") {
            state.tokenize = 0
            state.stateHandler = 0
            state.tagName = null
            state.tagStart = null
            inText(stream, state)
            return "invalid"
        } else if (ch == "'" || ch == "\"") {
            state.tokenize = if (ch == "'") 10 else 11
            state.stringStartCol = stream.column()
            state.isInAttribute = true
            return inAttribute(stream, state, ch)
        } else {
            stream.match(Regex("^[^\\s\\u00a0=<>\"']*[^\\s\\u00a0=<>\"'/]"))
            return "word"
        }
    }

    return object : StreamParser<XmlState> {
        override val name: String get() = "xml"

        override fun startState(indentUnit: Int) = XmlState()

        override fun copyState(state: XmlState): XmlState = state.copy(
            context = state.context // XmlContext is immutable-ish
        )

        override fun token(stream: StringStream, state: XmlState): String? {
            if (state.tagName == null && stream.sol()) {
                state.indented = stream.indentation()
            }
            if (stream.eatSpace()) return null
            type = null
            val style = when (state.tokenize) {
                0 -> inText(stream, state)
                1 -> inTag(stream, state)
                10 -> inAttribute(stream, state, "'")
                11 -> inAttribute(stream, state, "\"")
                20 -> inBlockToken(stream, state)
                30 -> doctypeToken(stream, state)
                else -> {
                    stream.next()
                    null
                }
            }
            if ((style != null || type != null) && style != "comment") {
                setStyle = null
                handleState(type ?: style ?: "", stream, state)
                if (setStyle != null) return setStyle
            }
            return style
        }

        override fun indent(state: XmlState, textAfter: String, context: IndentContext): Int? {
            var ctx = state.context
            if (state.isInAttribute) {
                return if (state.tagStart == state.indented) {
                    state.stringStartCol + 1
                } else {
                    state.indented + context.unit
                }
            }
            if (ctx != null && ctx.noIndent) return null
            if (state.tokenize != 1 && state.tokenize != 0) return null
            if (state.tagName != null) {
                return if (config.multilineTagIndentPastTag) {
                    (state.tagStart ?: 0) +
                        (state.tagName?.length ?: 0) + 2
                } else {
                    (state.tagStart ?: 0) +
                        context.unit * config.multilineTagIndentFactor
                }
            }
            if (config.alignCDATA &&
                Regex("<!\\[CDATA\\[").containsMatchIn(textAfter)
            ) {
                return 0
            }
            val tagAfter =
                Regex("^<(/)?([\\w_:\\.-]*)").find(textAfter)
            if (tagAfter != null && tagAfter.groupValues[1].isNotEmpty()) {
                // Closing tag
                while (ctx != null) {
                    if (ctx.tagName == tagAfter.groupValues[2]) {
                        ctx = ctx.prev
                        break
                    } else if (lower(ctx.tagName) in
                        config.implicitlyClosed
                    ) {
                        ctx = ctx.prev
                    } else {
                        break
                    }
                }
            } else if (tagAfter != null) {
                // Opening tag
                while (ctx != null) {
                    val grabbers =
                        config.contextGrabbers[lower(ctx.tagName)]
                    if (grabbers != null &&
                        lower(tagAfter.groupValues[2]) in grabbers
                    ) {
                        ctx = ctx.prev
                    } else {
                        break
                    }
                }
            }
            while (ctx != null && ctx.prev != null && !ctx.startOfLine) {
                ctx = ctx.prev
            }
            return if (ctx != null) {
                ctx.indent + context.unit
            } else {
                state.baseIndent
            }
        }

        override val languageData: Map<String, Any>
            get() = mapOf(
                "commentTokens" to mapOf(
                    "block" to mapOf(
                        "open" to "<!--",
                        "close" to "-->"
                    )
                )
            )
    }
}

val xmlLegacy: StreamParser<XmlState> = mkXML(XmlConfig())
val html: StreamParser<XmlState> = mkXML(XmlConfig(htmlMode = true))
