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

private val swiftKeywords = (
    "_,var,let,actor,class,enum,extension,import,protocol,struct,func,typealias," +
        "associatedtype,open,public,internal,fileprivate,private,deinit,init,new,override," +
        "self,subscript,super,convenience,dynamic,final,indirect,lazy,required,static," +
        "unowned,unowned(safe),unowned(unsafe),weak,as,is,break,case,continue,default," +
        "else,fallthrough,for,guard,if,in,repeat,switch,where,while,defer,return,inout," +
        "mutating,nonmutating,isolated,nonisolated,catch,do,rethrows,throw,throws,async," +
        "await,try,didSet,get,set,willSet,assignment,associativity,infix,left,none," +
        "operator,postfix,precedence,precedencegroup,prefix,right,Any,AnyObject,Type," +
        "dynamicType,Self,Protocol,__COLUMN__,__FILE__,__FUNCTION__,__LINE__"
    ).split(",").toSet()

private val swiftDefiningKeywords = (
    "var,let,actor,class,enum,extension,import,protocol,struct,func,typealias," +
        "associatedtype,for"
    ).split(",").toSet()

private val swiftAtoms = "true,false,nil,self,super,_".split(",").toSet()

private val swiftTypes = (
    "Array,Bool,Character,Dictionary,Double,Float,Int,Int8,Int16,Int32,Int64," +
        "Never,Optional,Set,String,UInt8,UInt16,UInt32,UInt64,Void"
    ).split(",").toSet()

private const val SWIFT_OPERATORS = "+-/*%=|&<>~^?!"
private const val SWIFT_PUNC = ":;,.(){}[]"

private val swiftBinary = Regex("^-?0b[01][01_]*")
private val swiftOctal = Regex("^-?0o[0-7][0-7_]*")
private val swiftHexadecimal = Regex(
    "^-?0x[\\dA-Fa-f][\\dA-Fa-f_]*(?:(?:\\.[\\dA-Fa-f][\\dA-Fa-f_]*)?[Pp]-?\\d[\\d_]*)?"
)
private val swiftDecimal = Regex("^-?\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[Ee]-?\\d[\\d_]*)?")
private val swiftIdentifier = Regex("^\\$\\d+|(`?)[_A-Za-z][_A-Za-z\$0-9]*\\1")
private val swiftProperty = Regex("^\\.(?:\\$\\d+|(`?)[_A-Za-z][_A-Za-z\$0-9]*\\1)")
private val swiftInstruction = Regex("^#[A-Za-z]+")
private val swiftAttribute = Regex("^@(?:\\$\\d+|(`?)[_A-Za-z][_A-Za-z\$0-9]*\\1)")

data class SwiftContext(
    val prev: SwiftContext?,
    val align: Int?,
    val indented: Int
)

data class SwiftState(
    var prev: String? = null,
    var context: SwiftContext? = null,
    var indented: Int = 0,
    var tokenize: MutableList<(StringStream, SwiftState, String?) -> String?> = mutableListOf()
)

val swiftLang: StreamParser<SwiftState> = object : StreamParser<SwiftState> {
    override val name: String get() = "swift"
    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "line" to "//",
                "block" to mapOf("open" to "/*", "close" to "*/")
            ),
            "closeBrackets" to mapOf(
                "brackets" to listOf("(", "[", "{", "'", "\"", "`")
            )
        )

    private fun tokenComment(stream: StringStream, state: SwiftState): String {
        var ch: String?
        while (true) {
            ch = stream.next()
            if (ch == null) break
            if (ch == "/" && stream.eat("*") != null) {
                state.tokenize.add(::tokenCommentWrapper)
            } else if (ch == "*" && stream.eat("/") != null) {
                state.tokenize.removeLastOrNull()
                break
            }
        }
        return "comment"
    }

    @Suppress("UNUSED_PARAMETER")
    private fun tokenCommentWrapper(
        stream: StringStream,
        state: SwiftState,
        prev: String?
    ): String {
        return tokenComment(stream, state)
    }

    private fun tokenString(openQuote: String, stream: StringStream, state: SwiftState): String {
        val singleLine = openQuote.length == 1
        var escaped = false
        var ch: String?
        while (true) {
            ch = stream.peek()
            if (ch == null) break
            if (escaped) {
                stream.next()
                if (ch == "(") {
                    state.tokenize.add(tokenUntilClosingParen())
                    return "string"
                }
                escaped = false
            } else if (stream.match(openQuote)) {
                state.tokenize.removeLastOrNull()
                return "string"
            } else {
                stream.next()
                escaped = ch == "\\"
            }
        }
        if (singleLine) {
            state.tokenize.removeLastOrNull()
        }
        return "string"
    }

    private fun tokenUntilClosingParen(): (StringStream, SwiftState, String?) -> String? {
        var depth = 0
        return fun(stream: StringStream, state: SwiftState, prev: String?): String? {
            val inner = tokenBase(stream, state, prev)
            if (inner == "punctuation") {
                val cur = stream.current()
                if (cur == "(") {
                    ++depth
                } else if (cur == ")") {
                    if (depth == 0) {
                        stream.backUp(1)
                        state.tokenize.removeLastOrNull()
                        val tokenizer = state.tokenize.lastOrNull()
                        return if (tokenizer != null) {
                            tokenizer(stream, state, prev)
                        } else {
                            tokenBase(stream, state, prev)
                        }
                    } else {
                        --depth
                    }
                }
            }
            return inner
        }
    }

    private fun tokenBase(stream: StringStream, state: SwiftState, prev: String?): String? {
        if (stream.sol()) state.indented = stream.indentation()
        if (stream.eatSpace()) return null

        val ch = stream.peek() ?: return null
        if (ch == "/") {
            if (stream.match("//")) {
                stream.skipToEnd()
                return "comment"
            }
            if (stream.match("/*")) {
                state.tokenize.add(::tokenCommentWrapper)
                return tokenComment(stream, state)
            }
        }
        if (stream.match(swiftInstruction) != null) return "builtin"
        if (stream.match(swiftAttribute) != null) return "attribute"
        if (stream.match(swiftBinary) != null) return "number"
        if (stream.match(swiftOctal) != null) return "number"
        if (stream.match(swiftHexadecimal) != null) return "number"
        if (stream.match(swiftDecimal) != null) return "number"
        if (stream.match(swiftProperty) != null) return "property"
        if (SWIFT_OPERATORS.contains(ch[0])) {
            stream.next()
            return "operator"
        }
        if (SWIFT_PUNC.contains(ch[0])) {
            stream.next()
            stream.match("..")
            return "punctuation"
        }
        val stringMatch = stream.match(Regex("^(\"\"\"|\"|\')"))
        if (stringMatch != null) {
            val quote = stringMatch.value
            val tokenize: (StringStream, SwiftState, String?) -> String? =
                fun(
                    s: StringStream,
                    st: SwiftState,
                    @Suppress("UNUSED_PARAMETER") _p: String?
                ): String {
                    return tokenString(quote, s, st)
                }
            state.tokenize.add(tokenize)
            return tokenize(stream, state, prev)
        }

        if (stream.match(swiftIdentifier) != null) {
            val ident = stream.current()
            if (swiftTypes.contains(ident)) return "type"
            if (swiftAtoms.contains(ident)) return "atom"
            if (swiftKeywords.contains(ident)) {
                if (swiftDefiningKeywords.contains(ident)) {
                    state.prev = "define"
                }
                return "keyword"
            }
            if (prev == "define") return "def"
            return "variable"
        }

        stream.next()
        return null
    }

    private fun pushContext(state: SwiftState, stream: StringStream) {
        val matchRes = stream.match(Regex("^\\s*($|/[/*]|[)}\\]])"), false)
        val align = if (matchRes != null) null else stream.column() + 1
        state.context = SwiftContext(
            prev = state.context,
            align = align,
            indented = state.indented
        )
    }

    private fun popContext(state: SwiftState) {
        val ctx = state.context ?: return
        state.indented = ctx.indented
        state.context = ctx.prev
    }

    override fun startState(indentUnit: Int) = SwiftState()

    override fun copyState(state: SwiftState) = SwiftState(
        prev = state.prev,
        context = state.context,
        indented = state.indented,
        tokenize = state.tokenize.toMutableList()
    )

    override fun token(stream: StringStream, state: SwiftState): String? {
        val prev = state.prev
        state.prev = null
        val tokenize = state.tokenize.lastOrNull()
        val style = if (tokenize != null) {
            tokenize(stream, state, prev)
        } else {
            tokenBase(stream, state, prev)
        }
        if (style == null || style == "comment") {
            state.prev = prev
        } else if (state.prev == null) state.prev = style

        if (style == "punctuation") {
            val bracket = Regex("[\\(\\[{]|([)\\]}])").find(stream.current())
            if (bracket != null) {
                if (bracket.groupValues[1].isNotEmpty()) {
                    popContext(state)
                } else {
                    pushContext(state, stream)
                }
            }
        }

        return style
    }

    override fun indent(state: SwiftState, textAfter: String, context: IndentContext): Int? {
        val cx = state.context ?: return 0
        val closing = Regex("^[\\]})]]").containsMatchIn(textAfter)
        if (cx.align != null) return cx.align - (if (closing) 1 else 0)
        return cx.indented + (if (closing) 0 else context.unit)
    }
}
