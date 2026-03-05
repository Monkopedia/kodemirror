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

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun words(str: String): Set<String> = str.split(" ").filter { it.isNotEmpty() }.toSet()

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

data class ClikeContext(
    val indented: Int,
    val column: Int,
    val type: String,
    val info: String?,
    var align: Boolean?,
    val prev: ClikeContext?
)

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class ClikeState(
    var tokenize: ((StringStream, ClikeState) -> String?)? = null,
    var context: ClikeContext = ClikeContext(-2, 0, "top", null, false, null),
    var indented: Int = 0,
    var startOfLine: Boolean = true,
    var prevToken: String? = null,
    var typeAtEndOfLine: Boolean = false,
    // Dart interpolation support
    var interpolationStack: MutableList<((StringStream, ClikeState) -> String?)?> = mutableListOf(),
    // C++11 raw string delimiter
    var cpp11RawStringDelim: String = ""
)

// ---------------------------------------------------------------------------
// Config
// ---------------------------------------------------------------------------

data class ClikeConfig(
    val name: String,
    val keywords: Set<String> = emptySet(),
    val types: ((String) -> Boolean)? = null,
    val typesSet: Set<String> = emptySet(),
    val builtin: Set<String> = emptySet(),
    val blockKeywords: Set<String> = emptySet(),
    val defKeywords: Set<String> = emptySet(),
    val atoms: Set<String> = emptySet(),
    val hooks: Map<String, (StringStream, ClikeState) -> Any?> = emptyMap(),
    val tokenHook: ((StringStream, ClikeState, String?) -> String?)? = null,
    val indentHook: ((ClikeState, ClikeContext, String, Int) -> Int?)? = null,
    val multiLineStrings: Boolean = false,
    val indentStatements: Boolean = true,
    val indentSwitch: Boolean = true,
    val namespaceSeparator: String? = null,
    val isPunctuationChar: Regex = Regex("[\\[\\]{}(),:;.]"),
    val numberStart: Regex = Regex("[\\d.]"),
    val number: Regex = Regex(
        "^(?:0x[a-f\\d]+|0b[01]+|(?:\\d+\\.?\\d*|\\.\\d+)(?:e[-+]?\\d+)?)(u|ll?|l|f)?",
        RegexOption.IGNORE_CASE
    ),
    val isOperatorChar: Regex = Regex("[+\\-*&%=<>!?|/]"),
    val isIdentifierChar: Regex = Regex("[\\w\$_\u00a1-\uffff]"),
    val isReservedIdentifier: ((String) -> Boolean)? = null,
    val typeFirstDefinitions: Boolean = false,
    val dontAlignCalls: Boolean = false,
    val dontIndentStatements: Regex? = null,
    val allmanIndentation: Boolean = false,
    val styleDefs: Boolean = true,
    val languageData: Map<String, Any> = emptyMap()
)

// ---------------------------------------------------------------------------
// Type helpers
// ---------------------------------------------------------------------------

private fun containsType(config: ClikeConfig, word: String): Boolean {
    if (word in config.typesSet) return true
    return config.types?.invoke(word) == true
}

private fun containsBuiltin(config: ClikeConfig, word: String): Boolean {
    if (word in config.builtin) return true
    return config.isReservedIdentifier?.invoke(word) == true
}

// ---------------------------------------------------------------------------
// Factory
// ---------------------------------------------------------------------------

@Suppress("CyclomaticComplexMethod", "LongMethod")
fun mkClike(config: ClikeConfig): StreamParser<ClikeState> {
    // Forward declaration for mutual recursion
    var tokenBaseRef: (StringStream, ClikeState) -> String? = { _, _ -> null }

    fun typeBefore(stream: StringStream, state: ClikeState, pos: Int): Boolean {
        if (state.prevToken == "variable" || state.prevToken == "type") return true
        val slice = stream.string.substring(0, pos)
        if (Regex("\\S(?:[^- ]>|[*\\]])\\s*$|\\*$").containsMatchIn(slice)) return true
        if (state.typeAtEndOfLine && stream.column() == stream.indentation()) return true
        return false
    }

    fun isTopScope(context: ClikeContext?): Boolean {
        var ctx: ClikeContext? = context
        while (true) {
            if (ctx == null || ctx.type == "top") return true
            if (ctx.type == "}" && ctx.prev?.info != "namespace") return false
            ctx = ctx.prev
        }
    }

    fun pushContext(state: ClikeState, col: Int, type: String, info: String? = null) {
        var indent = state.indented
        if (state.context.type == "statement" && type != "statement") {
            indent = state.context.indented
        }
        state.context = ClikeContext(indent, col, type, info, null, state.context)
    }

    fun popContext(state: ClikeState): ClikeContext? {
        val t = state.context.type
        if (t == ")" || t == "]" || t == "}") {
            state.indented = state.context.indented
        }
        val prev = state.context.prev
        if (prev != null) state.context = prev
        return state.context
    }

    fun tokenString(quote: String): (StringStream, ClikeState) -> String? = { stream, state ->
        var escaped = false
        var end = false
        var next: String?
        while (true) {
            next = stream.next()
            if (next == null) break
            if (next == quote && !escaped) {
                end = true
                break
            }
            escaped = !escaped && next == "\\"
        }
        if (end || !(escaped || config.multiLineStrings)) {
            state.tokenize = null
        }
        "string"
    }

    fun tokenComment(stream: StringStream, state: ClikeState): String {
        var maybeEnd = false
        while (true) {
            val ch = stream.next() ?: break
            if (ch == "/" && maybeEnd) {
                state.tokenize = null
                break
            }
            maybeEnd = ch == "*"
        }
        return "comment"
    }

    fun maybeEOL(stream: StringStream, state: ClikeState) {
        if (config.typeFirstDefinitions && stream.eol() && isTopScope(state.context)) {
            state.typeAtEndOfLine = typeBefore(stream, state, stream.pos)
        }
    }

    fun tokenBase(stream: StringStream, state: ClikeState): String? {
        val ch = stream.next() ?: return null
        val hook = config.hooks[ch]
        if (hook != null) {
            val result = hook(stream, state)
            if (result != false) {
                return result as? String
            }
        }
        if (ch == "\"" || ch == "'") {
            state.tokenize = tokenString(ch)
            return state.tokenize!!(stream, state)
        }
        if (config.numberStart.containsMatchIn(ch)) {
            stream.backUp(1)
            if (stream.match(config.number) != null) return "number"
            stream.next()
        }
        if (config.isPunctuationChar.containsMatchIn(ch)) {
            return null // curPunc handled externally by state
        }
        if (ch == "/") {
            if (stream.eat("*") != null) {
                state.tokenize = ::tokenComment
                return tokenComment(stream, state)
            }
            if (stream.eat("/") != null) {
                stream.skipToEnd()
                return "comment"
            }
        }
        if (config.isOperatorChar.containsMatchIn(ch)) {
            while (stream.match(Regex("^/[/*]"), consume = false) == null &&
                stream.eat(config.isOperatorChar) != null
            ) {
                // consume
            }
            return "operator"
        }
        stream.eatWhile(config.isIdentifierChar)
        val sep = config.namespaceSeparator
        if (sep != null) {
            while (stream.match(sep)) {
                stream.eatWhile(config.isIdentifierChar)
            }
        }
        val cur = stream.current()
        if (cur in config.keywords) {
            return "keyword"
        }
        if (containsType(config, cur)) return "type"
        if (containsBuiltin(config, cur)) return "builtin"
        if (cur in config.atoms) return "atom"
        return "variable"
    }

    tokenBaseRef = ::tokenBase

    return object : StreamParser<ClikeState> {
        override val name: String get() = config.name

        override fun startState(indentUnit: Int) = ClikeState(
            context = ClikeContext(-indentUnit, 0, "top", null, false, null)
        )

        override fun copyState(state: ClikeState) = state.copy(
            interpolationStack = state.interpolationStack.toMutableList()
        )

        @Suppress("CyclomaticComplexMethod", "LongMethod", "ComplexCondition")
        override fun token(stream: StringStream, state: ClikeState): String? {
            val ctx = state.context
            if (stream.sol()) {
                if (ctx.align == null) ctx.align = false
                state.indented = stream.indentation()
                state.startOfLine = true
            }
            if (stream.eatSpace()) {
                maybeEOL(stream, state)
                return null
            }
            // Track curPunc via a local mechanism: we read the char and detect punctuation
            // by checking if it changed the stream without returning a style.
            var curPunc: String? = null
            var isDefKeyword = false

            // We wrap tokenBase to detect curPunc
            val savedStart = stream.start
            val style: String? = run {
                val tokenFn = state.tokenize ?: ::tokenBase
                // Peek at next char for punctuation detection before calling
                val nextCh = stream.peek()
                val s = tokenFn(stream, state)
                // If the function consumed one char and returned null, check if it's punctuation
                if (s == null && nextCh != null && stream.start == savedStart) {
                    val consumed = stream.string.substring(savedStart, stream.pos)
                    val isPunct = config.isPunctuationChar
                        .containsMatchIn(consumed)
                    if (consumed.length == 1 && isPunct) {
                        curPunc = consumed
                    }
                }
                s
            }

            // Re-check: if style is null and we consumed a single punct char
            if (curPunc == null && style == null) {
                val consumed = stream.string.substring(savedStart, stream.pos)
                if (consumed.length == 1 && config.isPunctuationChar.containsMatchIn(consumed)) {
                    curPunc = consumed
                }
            }

            // Detect keyword-driven curPunc from keywords/blockKeywords
            if (style == "keyword") {
                val cur = stream.current()
                if (cur in config.blockKeywords) curPunc = "newstatement"
                if (cur in config.defKeywords) isDefKeyword = true
            }
            if (style == "builtin") {
                val cur = stream.current()
                if (cur in config.blockKeywords) curPunc = "newstatement"
            }

            if (style == "comment" || style == "meta") {
                maybeEOL(stream, state)
                return style
            }
            if (ctx.align == null) ctx.align = true

            val trailingCommaEnd = curPunc == "," &&
                stream.match(Regex("^\\s*(?://.*)?$"), consume = false) != null
            if (curPunc == ";" || curPunc == ":" || trailingCommaEnd) {
                while (state.context.type == "statement") popContext(state)
            } else if (curPunc == "{") {
                pushContext(state, stream.column(), "}")
            } else if (curPunc == "[") {
                pushContext(state, stream.column(), "]")
            } else if (curPunc == "(") {
                pushContext(state, stream.column(), ")")
            } else if (curPunc == "}") {
                while (state.context.type == "statement") {
                    popContext(state)
                }
                if (state.context.type == "}") popContext(state)
                while (state.context.type == "statement") {
                    popContext(state)
                }
            } else if (curPunc == state.context.type) {
                popContext(state)
            } else if (config.indentStatements &&
                (
                    (state.context.type == "}" || state.context.type == "top") &&
                        curPunc != ";"
                    ) ||
                (state.context.type == "statement" && curPunc == "newstatement")
            ) {
                pushContext(state, stream.column(), "statement", stream.current())
            }

            var finalStyle = style
            if (style == "variable") {
                val prevIsDef = state.prevToken == "def"
                val typeFirstDef = config.typeFirstDefinitions &&
                    typeBefore(stream, state, stream.start) &&
                    isTopScope(state.context) &&
                    stream.match(Regex("^\\s*\\("), consume = false) != null
                if (prevIsDef || typeFirstDef) finalStyle = "def"
            }

            val tokenHookResult = config.tokenHook?.invoke(stream, state, finalStyle)
            if (tokenHookResult != null) finalStyle = tokenHookResult

            if (finalStyle == "def" && !config.styleDefs) finalStyle = "variable"

            state.startOfLine = false
            state.prevToken = if (isDefKeyword) "def" else (finalStyle ?: curPunc)
            maybeEOL(stream, state)
            return finalStyle
        }

        @Suppress("CyclomaticComplexMethod", "LongMethod")
        override fun indent(state: ClikeState, textAfter: String, context: IndentContext): Int? {
            val tokenize = state.tokenize
            if ((tokenize != null && tokenize != ::tokenBase) ||
                (state.typeAtEndOfLine && isTopScope(state.context))
            ) {
                return null
            }
            var ctx = state.context
            val firstChar = if (textAfter.isNotEmpty()) textAfter[0].toString() else ""

            if (ctx.type == "statement" && firstChar == "}") ctx = ctx.prev ?: ctx
            val dontIndent = config.dontIndentStatements
            if (dontIndent != null) {
                while (ctx.type == "statement" &&
                    ctx.info != null && dontIndent.containsMatchIn(ctx.info)
                ) {
                    ctx = ctx.prev ?: break
                }
            }

            val indentHookResult = config.indentHook?.invoke(state, ctx, textAfter, context.unit)
            if (indentHookResult != null) return indentHookResult

            val switchBlock = ctx.prev?.info == "switch"
            if (config.allmanIndentation && Regex("[{(]").containsMatchIn(firstChar)) {
                var c: ClikeContext? = ctx
                while (c != null && c.type != "top" && c.type != "}") c = c.prev
                return c?.indented ?: 0
            }
            val closing = firstChar == ctx.type
            return when {
                ctx.type == "statement" -> {
                    val stmtIndent =
                        config.statementIndentUnit ?: context.unit
                    ctx.indented + (if (firstChar == "{") 0 else stmtIndent)
                }
                ctx.align == true && !(config.dontAlignCalls && ctx.type == ")") -> {
                    ctx.column + if (closing) 0 else 1
                }
                ctx.type == ")" && !closing -> {
                    ctx.indented + (config.statementIndentUnit ?: context.unit)
                }
                else -> {
                    ctx.indented + (if (closing) 0 else context.unit) +
                        if (!closing && switchBlock &&
                            !Regex("^(?:case|default)\\b").containsMatchIn(textAfter)
                        ) {
                            context.unit
                        } else {
                            0
                        }
                }
            }
        }

        override val languageData: Map<String, Any>
            get() {
                val indentOnInput = if (config.indentSwitch) {
                    Regex("^\\s*(?:case .*?:|default:|\\{}?|\\})$")
                } else {
                    Regex("^\\s*[{}]$")
                }
                val base = mapOf(
                    "indentOnInput" to indentOnInput,
                    "commentTokens" to mapOf(
                        "line" to "//",
                        "block" to mapOf("open" to "/*", "close" to "*/")
                    )
                )
                return base + config.languageData
            }
    }
}

// Extension to expose statementIndentUnit
private val ClikeConfig.statementIndentUnit: Int? get() = null

// ---------------------------------------------------------------------------
// Language-specific token helpers
// ---------------------------------------------------------------------------

private fun clikeTokenComment(stream: StringStream, state: ClikeState): String {
    var maybeEnd = false
    while (true) {
        val ch = stream.next() ?: break
        if (ch == "/" && maybeEnd) {
            state.tokenize = null
            break
        }
        maybeEnd = ch == "*"
    }
    return "comment"
}

private fun tokenAtString(stream: StringStream, state: ClikeState): String {
    var next: String?
    while (true) {
        next = stream.next() ?: break
        if (next == "\"" && stream.eat("\"") == null) {
            state.tokenize = null
            break
        }
    }
    return "string"
}

private fun tokenTripleString(stream: StringStream, state: ClikeState): String {
    var escaped = false
    while (!stream.eol()) {
        if (!escaped && stream.match("\"\"\"")) {
            state.tokenize = null
            break
        }
        escaped = stream.next() == "\\" && !escaped
    }
    return "string"
}

private fun tokenNestedComment(depth: Int): (StringStream, ClikeState) -> String {
    return fn@{ stream, state ->
        var ch: String?
        while (true) {
            ch = stream.next() ?: break
            if (ch == "*" && stream.eat("/") != null) {
                if (depth == 1) {
                    state.tokenize = null
                    break
                } else {
                    state.tokenize = tokenNestedComment(depth - 1)
                    return@fn state.tokenize!!(stream, state) as String
                }
            } else if (ch == "/" && stream.eat("*") != null) {
                state.tokenize = tokenNestedComment(depth + 1)
                return@fn state.tokenize!!(stream, state) as String
            }
        }
        "comment"
    }
}

// ---------------------------------------------------------------------------
// C / C++ helpers
// ---------------------------------------------------------------------------

private val cKeywordsStr =
    "auto if break case register continue return default do sizeof " +
        "static else struct switch extern typedef union for goto while enum const " +
        "volatile inline restrict asm fortran"

private val cppKeywordsStr =
    "alignas alignof and and_eq audit axiom bitand bitor catch " +
        "class compl concept constexpr const_cast decltype delete dynamic_cast " +
        "explicit export final friend import module mutable namespace new noexcept " +
        "not not_eq operator or or_eq override private protected public " +
        "reinterpret_cast requires static_assert static_cast template this " +
        "thread_local throw try typeid typename using virtual xor xor_eq"

private val objCKeywordsStr =
    "bycopy byref in inout oneway out self super atomic nonatomic retain copy " +
        "readwrite readonly strong weak assign typeof nullable nonnull null_resettable _cmd " +
        "@interface @implementation @end @protocol @encode @property @synthesize @dynamic @class " +
        "@public @package @private @protected @required @optional @try @catch @finally @import " +
        "@selector @encode @defs @synchronized @autoreleasepool @compatibility_alias @available"

private val objCBuiltinsStr =
    "FOUNDATION_EXPORT FOUNDATION_EXTERN NS_INLINE NS_FORMAT_FUNCTION " +
        " NS_RETURNS_RETAINEDNS_ERROR_ENUM NS_RETURNS_NOT_RETAINED NS_RETURNS_INNER_POINTER " +
        "NS_DESIGNATED_INITIALIZER NS_ENUM NS_OPTIONS NS_REQUIRES_NIL_TERMINATION " +
        "NS_ASSUME_NONNULL_BEGIN NS_ASSUME_NONNULL_END NS_SWIFT_NAME NS_REFINED_FOR_SWIFT"

private val basicCTypes = words(
    "int long char short double float unsigned signed void bool"
)

private val basicObjCTypes = words("SEL instancetype id Class Protocol BOOL")

private fun cTypes(identifier: String): Boolean {
    return identifier in basicCTypes || Regex(".+_t$").containsMatchIn(identifier)
}

private fun objCTypes(identifier: String): Boolean {
    return cTypes(identifier) || identifier in basicObjCTypes
}

private val cBlockKeywordsStr = "case do else for if switch while struct enum union"
private val cDefKeywordsStr = "struct enum union"

private fun cppHook(stream: StringStream, state: ClikeState): Any {
    if (!state.startOfLine) return false
    var next: ((StringStream, ClikeState) -> String?)? = null
    while (true) {
        val ch = stream.peek() ?: break
        if (ch == "\\" && stream.match(Regex("^.$")) != null) {
            next = ::cppHookTokenizer
            break
        } else if (ch == "/" && stream.match(Regex("^/[/*]"), consume = false) != null) {
            break
        }
        stream.next()
    }
    state.tokenize = next
    return "meta"
}

private fun cppHookTokenizer(stream: StringStream, state: ClikeState): String? {
    return cppHook(stream, state) as? String
}

private fun pointerHook(
    @Suppress(
        "UNUSED_PARAMETER"
    ) stream: StringStream,
    state: ClikeState
): Any {
    return if (state.prevToken == "type") "type" else false
}

private fun cIsReservedIdentifier(token: String): Boolean {
    if (token.length < 2) return false
    if (token[0] != '_') return false
    return token[1] == '_' || token[1] != token[1].lowercaseChar()
}

private fun cpp14Literal(
    stream: StringStream,
    @Suppress(
        "UNUSED_PARAMETER"
    ) state: ClikeState
): Any {
    stream.eatWhile(Regex("[\\w.']"))
    return "number"
}

private fun cpp11StringHook(stream: StringStream, state: ClikeState): Any {
    stream.backUp(1)
    // Raw strings
    if (stream.match(Regex("^(?:R|u8R|uR|UR|LR)")) != null) {
        val match = stream.match(Regex("^\"([^\\s\\\\()]{0,16})\\("))
        if (match == null) {
            return false
        }
        state.cpp11RawStringDelim = match.groupValues[1]
        state.tokenize = ::tokenRawString
        return tokenRawString(stream, state) as Any
    }
    // Unicode strings/chars
    if (stream.match(Regex("^(?:u8|u|U|L)")) != null) {
        if (stream.match(Regex("^[\"']"), consume = false) != null) {
            return "string"
        }
        return false
    }
    stream.next()
    return false
}

private fun tokenRawString(stream: StringStream, state: ClikeState): String {
    val delim = state.cpp11RawStringDelim.replace(Regex("[^\\w\\s]"), "\\$0")
    val match = stream.match(Regex(".*?\\)$delim\""))
    if (match != null) {
        state.tokenize = null
    } else {
        stream.skipToEnd()
    }
    return "string"
}

private fun cppLooksLikeConstructor(word: String): Boolean {
    val match = Regex("(\\w+)::~?(\\w+)$").find(word)
    return match != null && match.groupValues[1] == match.groupValues[2]
}

// ---------------------------------------------------------------------------
// Kotlin-specific helpers
// ---------------------------------------------------------------------------

private fun tokenKotlinString(tripleString: Boolean): (StringStream, ClikeState) -> String? {
    return fn@{ stream, state ->
        var escaped = false
        var end = false
        while (!stream.eol()) {
            if (!tripleString && !escaped && stream.match("\"")) {
                end = true
                break
            }
            if (tripleString && stream.match("\"\"\"")) {
                end = true
                break
            }
            val next = stream.next()
            if (!escaped && next == "$" && stream.match("{")) {
                stream.skipTo("}")
            }
            escaped = !escaped && next == "\\" && !tripleString
        }
        if (end || !tripleString) state.tokenize = null
        "string"
    }
}

// ---------------------------------------------------------------------------
// Scala-specific helpers
// ---------------------------------------------------------------------------

private fun scalaEqualsHook(stream: StringStream, state: ClikeState): Any {
    val cx = state.context
    if (cx.type == "}" && cx.align == true && stream.eat(">") != null) {
        state.context = ClikeContext(cx.indented, cx.column, cx.type, cx.info, null, cx.prev)
        return "operator"
    }
    return false
}

// ---------------------------------------------------------------------------
// Dart-specific helpers
// ---------------------------------------------------------------------------

private fun pushInterpolationStack(state: ClikeState) {
    state.interpolationStack.add(state.tokenize)
}

private fun popInterpolationStack(state: ClikeState): ((StringStream, ClikeState) -> String?)? {
    return if (state.interpolationStack.isNotEmpty()) {
        state.interpolationStack.removeAt(state.interpolationStack.size - 1)
    } else {
        null
    }
}

private fun sizeInterpolationStack(state: ClikeState): Int = state.interpolationStack.size

private fun tokenDartString(
    quote: String,
    stream: StringStream,
    state: ClikeState,
    raw: Boolean
): String? {
    var tripleQuoted = false
    if (stream.eat(quote) != null) {
        if (stream.eat(quote) != null) {
            tripleQuoted = true
        } else {
            return "string" // empty string
        }
    }

    fun tokenStringHelper(stream: StringStream, state: ClikeState): String {
        var escaped = false
        while (!stream.eol()) {
            if (!raw && !escaped && stream.peek() == "$") {
                pushInterpolationStack(state)
                state.tokenize = ::tokenInterpolation
                return "string"
            }
            val next = stream.next()
            if (next == quote && !escaped &&
                (!tripleQuoted || stream.match(quote + quote))
            ) {
                state.tokenize = null
                break
            }
            escaped = !raw && !escaped && next == "\\"
        }
        return "string"
    }

    val helper: (StringStream, ClikeState) -> String = { s, st -> tokenStringHelper(s, st) }
    state.tokenize = helper
    return helper(stream, state)
}

private fun tokenInterpolation(stream: StringStream, state: ClikeState): String? {
    stream.eat("$")
    if (stream.eat("{") != null) {
        state.tokenize = null
    } else {
        state.tokenize = ::tokenInterpolationIdentifier
    }
    return null
}

private fun tokenInterpolationIdentifier(stream: StringStream, state: ClikeState): String {
    stream.eatWhile(Regex("[\\w_]"))
    state.tokenize = popInterpolationStack(state)
    return "variable"
}

// ---------------------------------------------------------------------------
// Ceylon-specific helpers
// ---------------------------------------------------------------------------

private var ceylonStringTokenizer: ((StringStream, ClikeState) -> String?)? = null

private fun tokenCeylonString(type: String): (StringStream, ClikeState) -> String? {
    return fn@{ stream, state ->
        var escaped = false
        var end = false
        while (!stream.eol()) {
            if (!escaped && stream.match("\"") &&
                (type == "single" || stream.match("\"\""))
            ) {
                end = true
                break
            }
            if (!escaped && stream.match("``")) {
                ceylonStringTokenizer = tokenCeylonString(type)
                end = true
                break
            }
            val next = stream.next()
            escaped = type == "single" && !escaped && next == "\\"
        }
        if (end) state.tokenize = null
        "string"
    }
}

// ---------------------------------------------------------------------------
// C configs
// ---------------------------------------------------------------------------

private val cConfig = ClikeConfig(
    name = "c",
    keywords = words(cKeywordsStr),
    types = ::cTypes,
    blockKeywords = words(cBlockKeywordsStr),
    defKeywords = words(cDefKeywordsStr),
    typeFirstDefinitions = true,
    atoms = words("NULL true false"),
    isReservedIdentifier = ::cIsReservedIdentifier,
    hooks = mapOf(
        "#" to { stream, state -> cppHook(stream, state) },
        "*" to { stream, state -> pointerHook(stream, state) }
    )
)

private val cppConfig = ClikeConfig(
    name = "cpp",
    keywords = words("$cKeywordsStr $cppKeywordsStr"),
    types = ::cTypes,
    blockKeywords = words("$cBlockKeywordsStr class try catch"),
    defKeywords = words("$cDefKeywordsStr class namespace"),
    typeFirstDefinitions = true,
    atoms = words("true false NULL nullptr"),
    dontIndentStatements = Regex("^template$"),
    isIdentifierChar = Regex("[\\w\$_~\u00a1-\uffff]"),
    isReservedIdentifier = ::cIsReservedIdentifier,
    hooks = mapOf(
        "#" to { stream, state -> cppHook(stream, state) },
        "*" to { stream, state -> pointerHook(stream, state) },
        "u" to { stream, state -> cpp11StringHook(stream, state) },
        "U" to { stream, state -> cpp11StringHook(stream, state) },
        "L" to { stream, state -> cpp11StringHook(stream, state) },
        "R" to { stream, state -> cpp11StringHook(stream, state) },
        "0" to { stream, state -> cpp14Literal(stream, state) },
        "1" to { stream, state -> cpp14Literal(stream, state) },
        "2" to { stream, state -> cpp14Literal(stream, state) },
        "3" to { stream, state -> cpp14Literal(stream, state) },
        "4" to { stream, state -> cpp14Literal(stream, state) },
        "5" to { stream, state -> cpp14Literal(stream, state) },
        "6" to { stream, state -> cpp14Literal(stream, state) },
        "7" to { stream, state -> cpp14Literal(stream, state) },
        "8" to { stream, state -> cpp14Literal(stream, state) },
        "9" to { stream, state -> cpp14Literal(stream, state) }
    ),
    tokenHook = { stream, state, style ->
        if (style == "variable" && stream.peek() == "(" &&
            (
                state.prevToken == ";" || state.prevToken == null ||
                    state.prevToken == "}"
                ) &&
            cppLooksLikeConstructor(stream.current())
        ) {
            "def"
        } else {
            null
        }
    },
    namespaceSeparator = "::"
)

private val javaConfig = ClikeConfig(
    name = "java",
    keywords = words(
        "abstract assert break case catch class const continue default " +
            "do else enum extends final finally for goto if implements import " +
            "instanceof interface native new package private protected public " +
            "return static strictfp super switch synchronized this throw throws transient " +
            "try volatile while @interface"
    ),
    typesSet = words(
        "var byte short int long float double boolean char void " +
            "Boolean Byte Character Double Float " +
            "Integer Long Number Object Short String " +
            "StringBuffer StringBuilder Void"
    ),
    blockKeywords = words("catch class do else finally for if switch try while"),
    defKeywords = words("class interface enum @interface"),
    typeFirstDefinitions = true,
    atoms = words("true false null"),
    number = Regex(
        "^(?:0x[a-f\\d_]+|0b[01_]+|(?:[\\d_]+\\.?\\d*|\\.\\d+)(?:e[-+]?[\\d_]+)?)(u|ll?|l|f)?",
        RegexOption.IGNORE_CASE
    ),
    hooks = mapOf(
        "@" to { stream, _ ->
            if (stream.match("interface", consume = false)) {
                false
            } else {
                stream.eatWhile(Regex("[\\w\$_]"))
                "meta"
            }
        },
        "\"" to { stream, state ->
            if (!stream.match("\"\"")) {
                false
            } else {
                state.tokenize = ::tokenTripleString
                tokenTripleString(stream, state)
            }
        }
    )
)

private val csharpConfig = ClikeConfig(
    name = "csharp",
    keywords = words(
        "abstract as async await base break case catch checked class const continue" +
            " default delegate do else enum event explicit extern finally fixed for" +
            " foreach goto if implicit in init interface internal is lock namespace new" +
            " operator out override params private protected public" +
            " readonly record ref required return sealed" +
            " sizeof stackalloc static struct switch this throw" +
            " try typeof unchecked" +
            " unsafe using virtual void volatile while add alias" +
            " ascending descending dynamic from get" +
            " global group into join let orderby partial remove select set value var yield"
    ),
    typesSet = words(
        "Action Boolean Byte Char DateTime DateTimeOffset Decimal Double Func" +
            " Guid Int16 Int32 Int64 Object SByte Single String Task TimeSpan UInt16 UInt32" +
            " UInt64 bool byte char decimal double short int long object" +
            " sbyte float string ushort uint ulong"
    ),
    blockKeywords = words("catch class do else finally for foreach if struct switch try while"),
    defKeywords = words("class interface namespace record struct var"),
    typeFirstDefinitions = true,
    atoms = words("true false null"),
    hooks = mapOf(
        "@" to { stream, state ->
            if (stream.eat("\"") != null) {
                state.tokenize = ::tokenAtString
                tokenAtString(stream, state)
            } else {
                stream.eatWhile(Regex("[\\w\$_]"))
                "meta"
            }
        }
    )
)

private val scalaConfig = ClikeConfig(
    name = "scala",
    keywords = words(
        "abstract case catch class def do else extends final finally for forSome if " +
            "implicit import lazy match new null object override " +
            "package private protected return " +
            "sealed super this throw trait try type val var " +
            "while with yield _ " +
            "assert assume require print println printf " +
            "readLine readBoolean readByte readShort " +
            "readChar readInt readLong readFloat readDouble"
    ),
    typesSet = words(
        "AnyVal App Application Array BufferedIterator BigDecimal BigInt Char Console Either " +
            "Enumeration Equiv Error Exception Fractional " +
            "Function IndexedSeq Int Integral Iterable " +
            "Iterator List Map Numeric Nil NotNull Option " +
            "Ordered Ordering PartialFunction " +
            "PartialOrdering " +
            "Product Proxy Range Responder Seq Serializable " +
            "Set Specializable Stream StringBuilder " +
            "StringContext Symbol Throwable Traversable " +
            "TraversableOnce Tuple Unit Vector " +
            "Boolean Byte Character CharSequence Class " +
            "ClassLoader Cloneable Comparable " +
            "Compiler Double Exception Float Integer Long " +
            "Math Number Object Package Pair Process " +
            "Runtime Runnable SecurityManager Short StackTraceElement StrictMath String " +
            "StringBuffer System Thread ThreadGroup ThreadLocal Throwable Triple Void"
    ),
    multiLineStrings = true,
    blockKeywords = words("catch class enum do else finally for forSome if match switch try while"),
    defKeywords = words("class enum def object package trait type val var"),
    atoms = words("true false null"),
    indentStatements = false,
    indentSwitch = false,
    isOperatorChar = Regex("[+\\-*&%=<>!?|/#:@]"),
    hooks = mapOf(
        "@" to { stream, _ ->
            stream.eatWhile(Regex("[\\w\$_]"))
            "meta"
        },
        "\"" to { stream, state ->
            if (!stream.match("\"\"")) {
                false
            } else {
                state.tokenize = ::tokenTripleString
                tokenTripleString(stream, state)
            }
        },
        "'" to { stream, _ ->
            if (stream.match(Regex("^(\\\\[^'\\s]+|[^\\\\'])'")) != null) {
                "character"
            } else {
                stream.eatWhile(Regex("[\\w\$_\u00a1-\uffff]"))
                "atom"
            }
        },
        "=" to { stream, state -> scalaEqualsHook(stream, state) },
        "/" to { stream, state ->
            if (stream.eat("*") == null) {
                false
            } else {
                state.tokenize = tokenNestedComment(1)
                state.tokenize!!(stream, state)
            }
        }
    ),
    languageData = mapOf(
        "closeBrackets" to mapOf(
            "brackets" to listOf("(", "[", "{", "'", "\"", "\"\"\"")
        )
    )
)

private val kotlinConfig = ClikeConfig(
    name = "kotlin",
    keywords = words(
        "package as typealias class interface this super val operator " +
            "var fun for is in This throw return annotation " +
            "break continue object if else while do try when !in !is as? " +
            "file import where by get set abstract enum open " +
            "inner override private public internal " +
            "protected catch finally out final vararg reified " +
            "dynamic companion constructor init " +
            "sealed field property receiver param sparam lateinit data inline noinline tailrec " +
            "external annotation crossinline const operator infix suspend actual expect setparam"
    ),
    typesSet = words(
        "Boolean Byte Character CharSequence Class ClassLoader Cloneable Comparable " +
            "Compiler Double Exception Float Integer Long " +
            "Math Number Object Package Pair Process " +
            "Runtime Runnable SecurityManager Short " +
            "StackTraceElement StrictMath String " +
            "StringBuffer System Thread ThreadGroup " +
            "ThreadLocal Throwable Triple Void " +
            "Annotation Any BooleanArray " +
            "ByteArray Char CharArray DeprecationLevel " +
            "DoubleArray Enum FloatArray Function Int " +
            "IntArray Lazy " +
            "LazyThreadSafetyMode LongArray Nothing " +
            "ShortArray Unit"
    ),
    indentSwitch = false,
    indentStatements = false,
    multiLineStrings = true,
    number = Regex(
        "^(?:0x[a-f\\d_]+|0b[01_]+|(?:[\\d_]+(\\.[\\d]+)?|\\.\\d+)(?:e[-+]?[\\d_]+)?)(ul?|l|f)?",
        RegexOption.IGNORE_CASE
    ),
    blockKeywords = words("catch class do else finally for if where try while enum"),
    defKeywords = words("class val var object interface fun"),
    atoms = words("true false null this"),
    hooks = mapOf(
        "@" to { stream, _ ->
            stream.eatWhile(Regex("[\\w\$_]"))
            "meta"
        },
        "*" to { _, state ->
            if (state.prevToken == ".") "variable" else "operator"
        },
        "\"" to { stream, state ->
            state.tokenize = tokenKotlinString(stream.match("\"\""))
            state.tokenize!!(stream, state)
        },
        "/" to { stream, state ->
            if (stream.eat("*") == null) {
                false
            } else {
                state.tokenize = tokenNestedComment(1)
                state.tokenize!!(stream, state)
            }
        }
    ),
    indentHook = { state, ctx, textAfter, indentUnit ->
        val firstChar = if (textAfter.isNotEmpty()) textAfter[0].toString() else ""
        when {
            (state.prevToken == "}" || state.prevToken == ")") && textAfter.isEmpty() ->
                state.indented
            (state.prevToken == "operator" && textAfter != "}" && state.context.type != "}") ||
                (state.prevToken == "variable" && firstChar == ".") ||
                ((state.prevToken == "}" || state.prevToken == ")") && firstChar == ".") ->
                indentUnit * 2 + ctx.indented
            ctx.align == true && ctx.type == "}" -> {
                val ta = if (textAfter.isNotEmpty()) {
                    textAfter[0].toString()
                } else {
                    ""
                }
                val offset = if (state.context.type == ta) {
                    0
                } else {
                    indentUnit
                }
                ctx.indented + offset
            }
            else -> null
        }
    },
    languageData = mapOf(
        "closeBrackets" to mapOf(
            "brackets" to listOf("(", "[", "{", "'", "\"", "\"\"\"")
        )
    )
)

private val shaderConfig = ClikeConfig(
    name = "shader",
    keywords = words(
        "sampler1D sampler2D sampler3D samplerCube " +
            "sampler1DShadow sampler2DShadow " +
            "const attribute uniform varying " +
            "break continue discard return " +
            "for while do if else struct " +
            "in out inout"
    ),
    typesSet = words(
        "float int bool void " +
            "vec2 vec3 vec4 ivec2 ivec3 ivec4 bvec2 bvec3 bvec4 " +
            "mat2 mat3 mat4"
    ),
    blockKeywords = words("for while do if else struct"),
    builtin = words(
        "radians degrees sin cos tan asin acos atan " +
            "pow exp log exp2 sqrt inversesqrt " +
            "abs sign floor ceil fract mod min max clamp mix step smoothstep " +
            "length distance dot cross normalize ftransform faceforward " +
            "reflect refract matrixCompMult " +
            "lessThan lessThanEqual greaterThan greaterThanEqual " +
            "equal notEqual any all not " +
            "texture1D texture1DProj texture1DLod texture1DProjLod " +
            "texture2D texture2DProj texture2DLod texture2DProjLod " +
            "texture3D texture3DProj texture3DLod texture3DProjLod " +
            "textureCube textureCubeLod " +
            "shadow1D shadow2D shadow1DProj shadow2DProj " +
            "shadow1DLod shadow2DLod shadow1DProjLod shadow2DProjLod " +
            "dFdx dFdy fwidth " +
            "noise1 noise2 noise3 noise4"
    ),
    atoms = words(
        "true false " +
            "gl_FragColor gl_SecondaryColor gl_Normal gl_Vertex " +
            "gl_MultiTexCoord0 gl_MultiTexCoord1 gl_MultiTexCoord2 gl_MultiTexCoord3 " +
            "gl_MultiTexCoord4 gl_MultiTexCoord5 gl_MultiTexCoord6 gl_MultiTexCoord7 " +
            "gl_FogCoord gl_PointCoord " +
            "gl_Position gl_PointSize gl_ClipVertex " +
            "gl_FrontColor gl_BackColor gl_FrontSecondaryColor gl_BackSecondaryColor " +
            "gl_TexCoord gl_FogFragCoord " +
            "gl_FragCoord gl_FrontFacing " +
            "gl_FragData gl_FragDepth " +
            "gl_ModelViewMatrix gl_ProjectionMatrix gl_ModelViewProjectionMatrix " +
            "gl_TextureMatrix gl_NormalMatrix gl_ModelViewMatrixInverse " +
            "gl_ProjectionMatrixInverse gl_ModelViewProjectionMatrixInverse " +
            "gl_TextureMatrixTranspose gl_ModelViewMatrixInverseTranspose " +
            "gl_ProjectionMatrixInverseTranspose " +
            "gl_ModelViewProjectionMatrixInverseTranspose " +
            "gl_TextureMatrixInverseTranspose " +
            "gl_NormalScale gl_DepthRange gl_ClipPlane " +
            "gl_Point gl_FrontMaterial gl_BackMaterial gl_LightSource gl_LightModel " +
            "gl_FrontLightModelProduct gl_BackLightModelProduct " +
            "gl_TextureColor gl_EyePlaneS gl_EyePlaneT gl_EyePlaneR gl_EyePlaneQ " +
            "gl_FogParameters " +
            "gl_MaxLights gl_MaxClipPlanes gl_MaxTextureUnits gl_MaxTextureCoords " +
            "gl_MaxVertexAttribs gl_MaxVertexUniformComponents gl_MaxVaryingFloats " +
            "gl_MaxVertexTextureImageUnits gl_MaxTextureImageUnits " +
            "gl_MaxFragmentUniformComponents gl_MaxCombineTextureImageUnits " +
            "gl_MaxDrawBuffers"
    ),
    indentSwitch = false,
    hooks = mapOf(
        "#" to { stream, state -> cppHook(stream, state) }
    )
)

private val nesCConfig = ClikeConfig(
    name = "nesc",
    keywords = words(
        "$cKeywordsStr as atomic async call command " +
            "component components configuration " +
            "event generic " +
            "implementation includes interface module " +
            "new norace nx_struct nx_union post provides " +
            "signal task uses abstract extends"
    ),
    types = ::cTypes,
    blockKeywords = words(cBlockKeywordsStr),
    atoms = words("null true false"),
    hooks = mapOf(
        "#" to { stream, state -> cppHook(stream, state) }
    )
)

private val objectiveCConfig = ClikeConfig(
    name = "objectivec",
    keywords = words("$cKeywordsStr $objCKeywordsStr"),
    types = ::objCTypes,
    builtin = words(objCBuiltinsStr),
    blockKeywords = words(
        "$cBlockKeywordsStr @synthesize @try @catch @finally @autoreleasepool @synchronized"
    ),
    defKeywords = words("$cDefKeywordsStr @interface @implementation @protocol @class"),
    dontIndentStatements = Regex("^@.*$"),
    typeFirstDefinitions = true,
    atoms = words("YES NO NULL Nil nil true false nullptr"),
    isReservedIdentifier = ::cIsReservedIdentifier,
    hooks = mapOf(
        "#" to { stream, state -> cppHook(stream, state) },
        "*" to { stream, state -> pointerHook(stream, state) }
    )
)

private val objectiveCppConfig = ClikeConfig(
    name = "objectivecpp",
    keywords = words("$cKeywordsStr $objCKeywordsStr $cppKeywordsStr"),
    types = ::objCTypes,
    builtin = words(objCBuiltinsStr),
    blockKeywords = words(
        "$cBlockKeywordsStr @synthesize @try @catch " +
            "@finally @autoreleasepool @synchronized " +
            "class try catch"
    ),
    defKeywords = words(
        "$cDefKeywordsStr @interface @implementation " +
            "@protocol @class class namespace"
    ),
    dontIndentStatements = Regex("^@.*$|^template$"),
    typeFirstDefinitions = true,
    atoms = words("YES NO NULL Nil nil true false nullptr"),
    isReservedIdentifier = ::cIsReservedIdentifier,
    hooks = mapOf(
        "#" to { stream, state -> cppHook(stream, state) },
        "*" to { stream, state -> pointerHook(stream, state) },
        "u" to { stream, state -> cpp11StringHook(stream, state) },
        "U" to { stream, state -> cpp11StringHook(stream, state) },
        "L" to { stream, state -> cpp11StringHook(stream, state) },
        "R" to { stream, state -> cpp11StringHook(stream, state) },
        "0" to { stream, state -> cpp14Literal(stream, state) },
        "1" to { stream, state -> cpp14Literal(stream, state) },
        "2" to { stream, state -> cpp14Literal(stream, state) },
        "3" to { stream, state -> cpp14Literal(stream, state) },
        "4" to { stream, state -> cpp14Literal(stream, state) },
        "5" to { stream, state -> cpp14Literal(stream, state) },
        "6" to { stream, state -> cpp14Literal(stream, state) },
        "7" to { stream, state -> cpp14Literal(stream, state) },
        "8" to { stream, state -> cpp14Literal(stream, state) },
        "9" to { stream, state -> cpp14Literal(stream, state) }
    ),
    tokenHook = { stream, state, style ->
        if (style == "variable" && stream.peek() == "(" &&
            (
                state.prevToken == ";" || state.prevToken == null ||
                    state.prevToken == "}"
                ) &&
            cppLooksLikeConstructor(stream.current())
        ) {
            "def"
        } else {
            null
        }
    },
    namespaceSeparator = "::"
)

private val squirrelConfig = ClikeConfig(
    name = "squirrel",
    keywords = words(
        "base break clone continue const default delete enum extends function in class" +
            " foreach local resume return this throw typeof yield constructor instanceof static"
    ),
    types = ::cTypes,
    blockKeywords = words("case catch class else for foreach if switch try while"),
    defKeywords = words("function local class"),
    typeFirstDefinitions = true,
    atoms = words("true false null"),
    hooks = mapOf(
        "#" to { stream, state -> cppHook(stream, state) }
    )
)

private val ceylonConfig = ClikeConfig(
    name = "ceylon",
    keywords = words(
        "abstracts alias assembly assert assign break case catch class continue dynamic else" +
            " exists extends finally for function given if import in interface is let module new" +
            " nonempty object of out outer package return satisfies super switch then this throw" +
            " try value void while"
    ),
    types = { word ->
        val first = if (word.isNotEmpty()) word[0] else return@ClikeConfig false
        first == first.uppercaseChar() && first != first.lowercaseChar()
    },
    blockKeywords = words(
        "case catch class dynamic else finally for " +
            "function if interface module new object " +
            "switch try while"
    ),
    defKeywords = words("class dynamic function interface module object package value"),
    builtin = words(
        "abstract actual aliased annotation by default deprecated doc final formal late license" +
            " native optional sealed see serializable " +
            "shared suppressWarnings tagged throws variable"
    ),
    isPunctuationChar = Regex("[\\[\\]{}(),:;.`]"),
    isOperatorChar = Regex("[+\\-*&%=<>!?|^~:/]"),
    numberStart = Regex("[\\d#$]"),
    number = Regex(
        "^(?:#[\\da-fA-F_]+|\\$[01_]+|[\\d_]+[kMGTPmunpf]?" +
            "|[\\d_]+\\.[\\d_]+(?:[eE][-+]?\\d+|[kMGTPmunpf]|)|)",
        RegexOption.IGNORE_CASE
    ),
    multiLineStrings = true,
    typeFirstDefinitions = true,
    atoms = words("true false null larger smaller equal empty finished"),
    indentSwitch = false,
    styleDefs = false,
    hooks = mapOf(
        "@" to { stream, _ ->
            stream.eatWhile(Regex("[\\w\$_]"))
            "meta"
        },
        "\"" to { stream, state ->
            state.tokenize = tokenCeylonString(if (stream.match("\"\"")) "triple" else "single")
            state.tokenize!!(stream, state)
        },
        "`" to { stream, state ->
            val st = ceylonStringTokenizer
            if (st == null || !stream.match("`")) {
                false
            } else {
                state.tokenize = st
                ceylonStringTokenizer = null
                state.tokenize!!(stream, state)
            }
        },
        "'" to { stream, _ ->
            if (stream.match(Regex("^(\\\\[^'\\s]+|[^\\\\'])'")) != null) {
                "string.special"
            } else {
                stream.eatWhile(Regex("[\\w\$_\u00a1-\uffff]"))
                "atom"
            }
        }
    ),
    tokenHook = { _, state, style ->
        if ((style == "variable" || style == "type") && state.prevToken == ".") {
            "variableName.special"
        } else {
            null
        }
    },
    languageData = mapOf(
        "closeBrackets" to mapOf(
            "brackets" to listOf("(", "[", "{", "'", "\"", "\"\"\"")
        )
    )
)

private val dartConfig = ClikeConfig(
    name = "dart",
    keywords = words(
        "this super static final const abstract class extends external factory " +
            "implements mixin get native set typedef with enum throw rethrow assert break case " +
            "continue default in return new deferred async await covariant try catch finally " +
            "do else for if switch while import library export part of show hide is as extension " +
            "on yield late required sealed base interface when inline"
    ),
    blockKeywords = words("try catch finally do else for if switch while"),
    builtin = words("void bool num int double dynamic var String Null Never"),
    atoms = words("true false null"),
    number = Regex(
        "^(?:0x[a-f\\d_]+|(?:[\\d_]+\\.?[\\d_]*|\\.[\\d_]+)(?:e[-+]?[\\d_]+)?)",
        RegexOption.IGNORE_CASE
    ),
    hooks = mapOf(
        "@" to { stream, _ ->
            stream.eatWhile(Regex("[\\w\$_.]+"))
            "meta"
        },
        "'" to { stream, state ->
            tokenDartString("'", stream, state, false)
        },
        "\"" to { stream, state ->
            tokenDartString("\"", stream, state, false)
        },
        "r" to { stream, state ->
            val peek = stream.peek()
            if (peek == "'" || peek == "\"") {
                val q = stream.next()!!
                tokenDartString(q, stream, state, true)
            } else {
                false
            }
        },
        "}" to { _, state ->
            if (sizeInterpolationStack(state) > 0) {
                state.tokenize = popInterpolationStack(state)
                null
            } else {
                false
            }
        },
        "/" to { stream, state ->
            if (stream.eat("*") == null) {
                false
            } else {
                state.tokenize = tokenNestedComment(1)
                state.tokenize!!(stream, state)
            }
        }
    ),
    tokenHook = { stream, _, style ->
        if (style == "variable") {
            val isUpper = Regex("^[_\$]*[A-Z][a-zA-Z0-9_\$]*$")
            if (isUpper.containsMatchIn(stream.current())) "type" else null
        } else {
            null
        }
    }
)

// ---------------------------------------------------------------------------
// Exported parsers
// ---------------------------------------------------------------------------

val c: StreamParser<ClikeState> = mkClike(cConfig)
val cpp: StreamParser<ClikeState> = mkClike(cppConfig)
val java: StreamParser<ClikeState> = mkClike(javaConfig)
val csharp: StreamParser<ClikeState> = mkClike(csharpConfig)
val scala: StreamParser<ClikeState> = mkClike(scalaConfig)
val kotlin: StreamParser<ClikeState> = mkClike(kotlinConfig)
val shader: StreamParser<ClikeState> = mkClike(shaderConfig)
val nesC: StreamParser<ClikeState> = mkClike(nesCConfig)
val objectiveC: StreamParser<ClikeState> = mkClike(objectiveCConfig)
val objectiveCpp: StreamParser<ClikeState> = mkClike(objectiveCppConfig)
val squirrel: StreamParser<ClikeState> = mkClike(squirrelConfig)
val ceylon: StreamParser<ClikeState> = mkClike(ceylonConfig)
val dart: StreamParser<ClikeState> = mkClike(dartConfig)
