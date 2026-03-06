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

private enum class XqTokenType {
    KEYWORD,
    ATOM,
    OPERATOR,
    PUNCTUATION,
    AXIS_SPECIFIER,
    FUNCTION_CALL
}

private data class XqKeyword(val type: XqTokenType, val style: String?)

private val xqKeywords: Map<String, XqKeyword> = buildMap {
    fun kw(type: XqTokenType = XqTokenType.KEYWORD) = XqKeyword(type, "keyword")
    val atom = XqKeyword(XqTokenType.ATOM, "atom")
    val punctuation = XqKeyword(XqTokenType.PUNCTUATION, null)
    val qualifier = XqKeyword(XqTokenType.AXIS_SPECIFIER, "qualifier")

    put(",", punctuation)

    val basic = listOf(
        "after", "all", "allowing", "ancestor", "ancestor-or-self", "any", "array", "as",
        "ascending", "at", "attribute", "base-uri", "before", "boundary-space", "by", "case",
        "cast", "castable", "catch", "child", "collation", "comment", "construction", "contains",
        "content", "context", "copy", "copy-namespaces", "count", "decimal-format", "declare",
        "default", "delete", "descendant", "descendant-or-self", "descending", "diacritics",
        "different", "distance", "document", "document-node", "element", "else", "empty",
        "empty-sequence", "encoding", "end", "entire", "every", "exactly", "except", "external",
        "first", "following", "following-sibling", "for", "from", "ftand", "ftnot", "ft-option",
        "ftor", "function", "fuzzy", "greatest", "group", "if", "import", "in", "inherit",
        "insensitive", "insert", "instance", "intersect", "into", "invoke", "is", "item",
        "language", "last", "lax", "least", "let", "levels", "lowercase", "map", "modify",
        "module", "most", "namespace", "next", "no", "node", "nodes", "no-inherit", "no-preserve",
        "not", "occurs", "of", "only", "option", "order", "ordered", "ordering", "paragraph",
        "paragraphs", "parent", "phrase", "preceding", "preceding-sibling", "preserve", "previous",
        "processing-instruction", "relationship", "rename", "replace", "return", "revalidation",
        "same", "satisfies", "schema", "schema-attribute", "schema-element", "score", "self",
        "sensitive", "sentence", "sentences", "sequence", "skip", "sliding", "some", "stable",
        "start", "stemming", "stop", "strict", "strip", "switch", "text", "then", "thesaurus",
        "times", "to", "transform", "treat", "try", "tumbling", "type", "typeswitch", "union",
        "unordered", "update", "updating", "uppercase", "using", "validate", "value", "variable",
        "version", "weight", "when", "where", "wildcards", "window", "with", "without", "word",
        "words", "xquery"
    )
    for (b in basic) put(b, kw())

    val types = listOf(
        "xs:anyAtomicType", "xs:anySimpleType", "xs:anyType", "xs:anyURI",
        "xs:base64Binary", "xs:boolean", "xs:byte", "xs:date", "xs:dateTime", "xs:dateTimeStamp",
        "xs:dayTimeDuration", "xs:decimal", "xs:double", "xs:duration", "xs:ENTITIES", "xs:ENTITY",
        "xs:float", "xs:gDay", "xs:gMonth", "xs:gMonthDay", "xs:gYear", "xs:gYearMonth",
        "xs:hexBinary", "xs:ID", "xs:IDREF", "xs:IDREFS", "xs:int", "xs:integer", "xs:item",
        "xs:java", "xs:language", "xs:long", "xs:Name", "xs:NCName", "xs:negativeInteger",
        "xs:NMTOKEN", "xs:NMTOKENS", "xs:nonNegativeInteger", "xs:nonPositiveInteger",
        "xs:normalizedString", "xs:NOTATION", "xs:numeric", "xs:positiveInteger",
        "xs:precisionDecimal", "xs:QName", "xs:short", "xs:string",
        "xs:time", "xs:token", "xs:unsignedByte", "xs:unsignedInt", "xs:unsignedLong",
        "xs:unsignedShort", "xs:untyped", "xs:untypedAtomic", "xs:yearMonthDuration"
    )
    for (t in types) put(t, atom)

    val operators = listOf(
        "eq", "ne", "lt", "le", "gt", "ge", ":=", "=", ">", ">=", "<", "<=",
        ".", "|", "?", "and", "or", "div", "idiv", "mod", "*", "/", "+", "-"
    )
    for (op in operators) put(op, kw(XqTokenType.OPERATOR))

    val axisSpecifiers = listOf(
        "self::", "attribute::", "child::", "descendant::", "descendant-or-self::",
        "parent::", "ancestor::", "ancestor-or-self::", "following::", "preceding::",
        "following-sibling::", "preceding-sibling::"
    )
    for (ax in axisSpecifiers) put(ax, qualifier)
}

data class XqStackItem(
    val type: String,
    val name: String? = null,
    val tokenize: ((StringStream, XQueryState) -> String?)? = null
)

data class XQueryState(
    var tokenize: (StringStream, XQueryState) -> String? = ::xqTokenBase,
    var stack: MutableList<XqStackItem> = mutableListOf()
)

private fun xqPushStateStack(state: XQueryState, newState: XqStackItem) {
    state.stack.add(newState)
}

private fun xqPopStateStack(state: XQueryState) {
    state.stack.removeLastOrNull()
    val reinstateTokenize = state.stack.lastOrNull()?.tokenize
    state.tokenize = reinstateTokenize ?: ::xqTokenBase
}

private fun xqIsIn(state: XQueryState, type: String): Boolean =
    state.stack.isNotEmpty() && state.stack.last().type == type

private fun xqIsInXmlBlock(state: XQueryState): Boolean = xqIsIn(state, "tag")
private fun xqIsInXmlAttributeBlock(state: XQueryState): Boolean = xqIsIn(state, "attribute")
private fun xqIsInXmlConstructor(state: XQueryState): Boolean = xqIsIn(state, "xmlconstructor")

private fun xqIsEQNameAhead(stream: StringStream): Boolean {
    val cur = stream.current()
    return when {
        cur == "\"" -> stream.match(Regex("^[^\"]+\":"), consume = false) != null
        cur == "'" -> stream.match(Regex("^[^']+':"), consume = false) != null
        else -> false
    }
}

private fun xqChain(
    stream: StringStream,
    state: XQueryState,
    f: (StringStream, XQueryState) -> String?
): String? {
    state.tokenize = f
    return f(stream, state)
}

private fun xqTokenComment(stream: StringStream, state: XQueryState): String? {
    var maybeEnd = false
    var maybeNested = false
    var nestedCount = 0
    var ch: String?
    while (stream.next().also { ch = it } != null) {
        if (ch == ")" && maybeEnd) {
            if (nestedCount > 0) {
                nestedCount--
            } else {
                xqPopStateStack(state)
                break
            }
        } else if (ch == ":" && maybeNested) {
            nestedCount++
        }
        maybeEnd = ch == ":"
        maybeNested = ch == "("
    }
    return "comment"
}

private fun xqTokenString(
    quote: String,
    f: ((StringStream, XQueryState) -> String?)?
): (StringStream, XQueryState) -> String? {
    return fn@{ stream, state ->
        var ch: String?
        while (stream.next().also { ch = it } != null) {
            if (ch == quote) {
                xqPopStateStack(state)
                if (f != null) state.tokenize = f
                break
            } else if (stream.match("{", consume = false) && xqIsInXmlAttributeBlock(state)) {
                xqPushStateStack(state, XqStackItem(type = "codeblock"))
                state.tokenize = ::xqTokenBase
                return@fn "string"
            }
        }
        "string"
    }
}

private fun xqStartString(
    stream: StringStream,
    state: XQueryState,
    quote: String,
    f: ((StringStream, XQueryState) -> String?)? = null
): String? {
    val tokenizeFn = xqTokenString(quote, f)
    xqPushStateStack(state, XqStackItem(type = "string", name = quote, tokenize = tokenizeFn))
    return xqChain(stream, state, tokenizeFn)
}

private fun xqTokenVariable(stream: StringStream, state: XQueryState): String? {
    val isVariableChar = Regex("[\\w\$_-]")
    if (stream.eat("\"") != null) {
        while (stream.next() != "\"") { /* consume */ }
        stream.eat(":")
    } else {
        stream.eatWhile(isVariableChar)
        if (!stream.match(":=", consume = false)) stream.eat(":")
    }
    stream.eatWhile(isVariableChar)
    state.tokenize = ::xqTokenBase
    return "variable"
}

private fun xqTokenTag(name: String, isClose: Boolean): (StringStream, XQueryState) -> String? {
    return fn@{ stream, state ->
        stream.eatSpace()
        if (isClose && stream.eat(">") != null) {
            xqPopStateStack(state)
            state.tokenize = ::xqTokenBase
            return@fn "tag"
        }
        if (stream.eat("/") == null) {
            xqPushStateStack(
                state,
                XqStackItem(type = "tag", name = name, tokenize = ::xqTokenBase)
            )
        }
        if (stream.eat(">") == null) {
            state.tokenize = ::xqTokenAttribute
        } else {
            state.tokenize = ::xqTokenBase
        }
        "tag"
    }
}

private fun xqTokenAttribute(stream: StringStream, state: XQueryState): String? {
    val ch = stream.next() ?: return null

    if (ch == "/" && stream.eat(">") != null) {
        if (xqIsInXmlAttributeBlock(state)) xqPopStateStack(state)
        if (xqIsInXmlBlock(state)) xqPopStateStack(state)
        return "tag"
    }
    if (ch == ">") {
        if (xqIsInXmlAttributeBlock(state)) xqPopStateStack(state)
        return "tag"
    }
    if (ch == "=") return null
    if (ch == "\"" || ch == "'") {
        return xqStartString(stream, state, ch, ::xqTokenAttribute)
    }
    if (!xqIsInXmlAttributeBlock(state)) {
        xqPushStateStack(state, XqStackItem(type = "attribute", tokenize = ::xqTokenAttribute))
    }
    stream.eat(Regex("[a-zA-Z_:]"))
    stream.eatWhile(Regex("[-a-zA-Z0-9_:.]"))
    stream.eatSpace()
    if (stream.match(">", consume = false) || stream.match("/", consume = false)) {
        xqPopStateStack(state)
        state.tokenize = ::xqTokenBase
    }
    return "attribute"
}

private fun xqTokenXMLComment(stream: StringStream, state: XQueryState): String? {
    var ch: String?
    while (stream.next().also { ch = it } != null) {
        if (ch == "-" && stream.match("->")) {
            state.tokenize = ::xqTokenBase
            return "comment"
        }
    }
    return "comment"
}

private fun xqTokenCDATA(stream: StringStream, state: XQueryState): String? {
    var ch: String?
    while (stream.next().also { ch = it } != null) {
        if (ch == "]" && stream.match("]")) {
            state.tokenize = ::xqTokenBase
            return "comment"
        }
    }
    return "comment"
}

private fun xqTokenPreProcessing(stream: StringStream, state: XQueryState): String? {
    var ch: String?
    while (stream.next().also { ch = it } != null) {
        if (ch == "?" && stream.match(">")) {
            state.tokenize = ::xqTokenBase
            return "processingInstruction"
        }
    }
    return "processingInstruction"
}

private fun xqTokenBase(stream: StringStream, state: XQueryState): String? {
    val ch = stream.next() ?: return null
    var mightBeFunction = false
    val isEQName = xqIsEQNameAhead(stream)

    if (ch == "<") {
        if (stream.match("!--")) {
            return xqChain(stream, state, ::xqTokenXMLComment)
        }
        if (stream.match("![CDATA", consume = false)) {
            state.tokenize = ::xqTokenCDATA
            return "tag"
        }
        if (stream.match("?", consume = false)) {
            return xqChain(stream, state, ::xqTokenPreProcessing)
        }
        val isClose = stream.eat("/") != null
        stream.eatSpace()
        var tagName = ""
        var c: String?
        while (stream.eat(Regex("[^\\s\u00a0=<>\"'/?]")).also { c = it } != null) tagName += c
        return xqChain(stream, state, xqTokenTag(tagName, isClose))
    } else if (ch == "{") {
        xqPushStateStack(state, XqStackItem(type = "codeblock"))
        return null
    } else if (ch == "}") {
        xqPopStateStack(state)
        return null
    } else if (xqIsInXmlBlock(state)) {
        return when {
            ch == ">" -> "tag"
            ch == "/" && stream.eat(">") != null -> {
                xqPopStateStack(state)
                "tag"
            }
            else -> "variable"
        }
    } else if (Regex("\\d").containsMatchIn(ch)) {
        stream.match(Regex("^\\d*(?:\\.\\d*)?(?:E[+\\-]?\\d+)?"))
        return "atom"
    } else if (ch == "(" && stream.eat(":") != null) {
        xqPushStateStack(state, XqStackItem(type = "comment"))
        return xqChain(stream, state, ::xqTokenComment)
    } else if (!isEQName && (ch == "\"" || ch == "'")) {
        return xqStartString(stream, state, ch)
    } else if (ch == "\$") {
        return xqChain(stream, state, ::xqTokenVariable)
    } else if (ch == ":" && stream.eat("=") != null) {
        return "keyword"
    } else if (ch == "(") {
        xqPushStateStack(state, XqStackItem(type = "paren"))
        return null
    } else if (ch == ")") {
        xqPopStateStack(state)
        return null
    } else if (ch == "[") {
        xqPushStateStack(state, XqStackItem(type = "bracket"))
        return null
    } else if (ch == "]") {
        xqPopStateStack(state)
        return null
    } else {
        var known = xqKeywords[ch]

        if (isEQName && ch == "\"") while (stream.next() != "\"") { /* consume */ }
        if (isEQName && ch == "'") while (stream.next() != "'") { /* consume */ }

        if (known == null) stream.eatWhile(Regex("[\\w\$_-]"))

        val foundColon = stream.eat(":")
        if (foundColon != null && stream.eat(":") == null) {
            stream.eatWhile(Regex("[\\w\$_-]"))
        }

        if (stream.match(Regex("^[ \\t]*\\("), consume = false) != null) {
            mightBeFunction = true
        }

        val word = stream.current()
        known = xqKeywords[word]

        if (mightBeFunction && known == null) {
            known = XqKeyword(XqTokenType.FUNCTION_CALL, "def")
        }

        if (xqIsInXmlConstructor(state)) {
            xqPopStateStack(state)
            return "variable"
        }

        if (word == "element" || word == "attribute" || known?.type == XqTokenType.AXIS_SPECIFIER) {
            xqPushStateStack(state, XqStackItem(type = "xmlconstructor"))
        }

        return known?.style ?: "variable"
    }
}

/** Stream parser for XQuery. */
val xQuery: StreamParser<XQueryState> = object : StreamParser<XQueryState> {
    override val name: String get() = "xquery"

    override fun startState(indentUnit: Int) = XQueryState()

    override fun copyState(state: XQueryState) = XQueryState(
        tokenize = state.tokenize,
        stack = state.stack.toMutableList()
    )

    override fun token(stream: StringStream, state: XQueryState): String? {
        if (stream.eatSpace()) return null
        return state.tokenize(stream, state)
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "block" to mapOf("open" to "(:", "close" to ":)")
            )
        )
}
