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

private val haskellSmallRE = Regex("[a-z_]")
private val haskellLargeRE = Regex("[A-Z]")
private val haskellDigitRE = Regex("\\d")
private val haskellHexitRE = Regex("[0-9A-Fa-f]")
private val haskellOctitRE = Regex("[0-7]")
private val haskellIdRE = Regex("[a-z_A-Z0-9'\\u00a1-\\uffff]")
private val haskellSymbolRE = Regex("[-!#\$%&*+./<=>?@\\\\^|~:]")
private val haskellSpecialRE = Regex("[(),;\\[\\]`{}]")
private val haskellWhiteCharRE = Regex("[ \\t\\u000B\\u000C]")

fun interface HaskellTokenFn {
    fun invoke(stream: StringStream, setState: (HaskellTokenFn) -> Unit): String?
}

private fun switchHaskellState(
    source: StringStream,
    setState: (HaskellTokenFn) -> Unit,
    f: HaskellTokenFn
): String? {
    setState(f)
    return f.invoke(source, setState)
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
private fun haskellNormal(): HaskellTokenFn {
    return HaskellTokenFn { source, setState ->
        if (source.eatWhile(haskellWhiteCharRE)) {
            return@HaskellTokenFn null
        }

        val ch = source.next() ?: return@HaskellTokenFn "error"

        if (haskellSpecialRE.containsMatchIn(ch)) {
            if (ch == "{" && source.eat("-") != null) {
                var t = "comment"
                if (source.eat("#") != null) {
                    t = "meta"
                }
                return@HaskellTokenFn switchHaskellState(source, setState, haskellNcomment(t, 1))
            }
            return@HaskellTokenFn null
        }

        if (ch == "'") {
            if (source.eat("\\") != null) {
                source.next()
            } else {
                source.next()
            }
            if (source.eat("'") != null) {
                return@HaskellTokenFn "string"
            }
            return@HaskellTokenFn "error"
        }

        if (ch == "\"") {
            return@HaskellTokenFn switchHaskellState(source, setState, haskellStringLiteral())
        }

        if (haskellLargeRE.containsMatchIn(ch)) {
            source.eatWhile(haskellIdRE)
            if (source.eat(".") != null) {
                return@HaskellTokenFn "qualifier"
            }
            return@HaskellTokenFn "type"
        }

        if (haskellSmallRE.containsMatchIn(ch)) {
            source.eatWhile(haskellIdRE)
            return@HaskellTokenFn "variable"
        }

        if (haskellDigitRE.containsMatchIn(ch)) {
            if (ch == "0") {
                if (source.eat(Regex("[xX]")) != null) {
                    source.eatWhile(haskellHexitRE)
                    return@HaskellTokenFn "integer"
                }
                if (source.eat(Regex("[oO]")) != null) {
                    source.eatWhile(haskellOctitRE)
                    return@HaskellTokenFn "number"
                }
            }
            source.eatWhile(haskellDigitRE)
            var t = "number"
            if (source.match(Regex("^\\.\\d+")) != null) {
                t = "number"
            }
            if (source.eat(Regex("[eE]")) != null) {
                t = "number"
                source.eat(Regex("[-+]"))
                source.eatWhile(haskellDigitRE)
            }
            return@HaskellTokenFn t
        }

        if (ch == "." && source.eat(".") != null) return@HaskellTokenFn "keyword"

        if (haskellSymbolRE.containsMatchIn(ch)) {
            if (ch == "-" && source.eat(Regex("-")) != null) {
                source.eatWhile(Regex("-"))
                if (source.eat(haskellSymbolRE) == null) {
                    source.skipToEnd()
                    return@HaskellTokenFn "comment"
                }
            }
            source.eatWhile(haskellSymbolRE)
            return@HaskellTokenFn "variable"
        }

        "error"
    }
}

private fun haskellNcomment(type: String, nest: Int): HaskellTokenFn {
    if (nest == 0) {
        return haskellNormal()
    }
    return HaskellTokenFn { source, setState ->
        var currNest = nest
        while (!source.eol()) {
            val ch = source.next()
            if (ch == "{" && source.eat("-") != null) {
                currNest++
            } else if (ch == "-" && source.eat("}") != null) {
                currNest--
                if (currNest == 0) {
                    setState(haskellNormal())
                    return@HaskellTokenFn type
                }
            }
        }
        setState(haskellNcomment(type, currNest))
        type
    }
}

private fun haskellStringLiteral(): HaskellTokenFn {
    return HaskellTokenFn { source, setState ->
        while (!source.eol()) {
            val ch = source.next()
            if (ch == "\"") {
                setState(haskellNormal())
                return@HaskellTokenFn "string"
            }
            if (ch == "\\") {
                if (source.eol() || source.eat(haskellWhiteCharRE) != null) {
                    setState(haskellStringGap())
                    return@HaskellTokenFn "string"
                }
                if (source.eat("&") != null) {
                    // empty
                } else {
                    source.next()
                }
            }
        }
        setState(haskellNormal())
        "error"
    }
}

private fun haskellStringGap(): HaskellTokenFn {
    return HaskellTokenFn { source, setState ->
        if (source.eat("\\") != null) {
            return@HaskellTokenFn switchHaskellState(source, setState, haskellStringLiteral())
        }
        source.next()
        setState(haskellNormal())
        "error"
    }
}

@Suppress("ktlint:standard:max-line-length")
private val haskellWellKnownWords: Map<String, String> = buildMap {
    fun setType(t: String, vararg words: String) {
        for (w in words) put(w, t)
    }

    setType(
        "keyword",
        "case", "class", "data", "default", "deriving", "do", "else", "foreign",
        "if", "import", "in", "infix", "infixl", "infixr", "instance", "let",
        "module", "newtype", "of", "then", "type", "where", "_"
    )
    setType(
        "keyword",
        "..", ":", "::", "=", "\\", "<-", "->", "@", "~", "=>"
    )
    setType(
        "builtin",
        "!!", "\$!", "\$", "&&", "+", "++", "-", ".", "/", "/=", "<", "<*", "<=",
        "<\$>", "<*>", "=<<", "==", ">", ">=", ">>", ">>=", "^", "^^", "||", "*",
        "*>", "**"
    )
    setType(
        "builtin",
        "Applicative", "Bool", "Bounded", "Char", "Double", "EQ", "Either", "Enum",
        "Eq", "False", "FilePath", "Float", "Floating", "Fractional", "Functor",
        "GT", "IO", "IOError", "Int", "Integer", "Integral", "Just", "LT", "Left",
        "Maybe", "Monad", "Nothing", "Num", "Ord", "Ordering", "Rational", "Read",
        "ReadS", "Real", "RealFloat", "RealFrac", "Right", "Show", "ShowS",
        "String", "True"
    )
    setType(
        "builtin",
        "abs", "acos", "acosh", "all", "and", "any", "appendFile", "asTypeOf",
        "asin", "asinh", "atan", "atan2", "atanh", "break", "catch", "ceiling",
        "compare", "concat", "concatMap", "const", "cos", "cosh", "curry",
        "cycle", "decodeFloat", "div", "divMod", "drop", "dropWhile", "either",
        "elem", "encodeFloat", "enumFrom", "enumFromThen", "enumFromThenTo",
        "enumFromTo", "error", "even", "exp", "exponent", "fail", "filter",
        "flip", "floatDigits", "floatRadix", "floatRange", "floor", "fmap",
        "foldl", "foldl1", "foldr", "foldr1", "fromEnum", "fromInteger",
        "fromIntegral", "fromRational", "fst", "gcd", "getChar", "getContents",
        "getLine", "head", "id", "init", "interact", "ioError", "isDenormalized",
        "isIEEE", "isInfinite", "isNaN", "isNegativeZero", "iterate", "last",
        "lcm", "length", "lex", "lines", "log", "logBase", "lookup", "map",
        "mapM", "mapM_", "max", "maxBound", "maximum", "maybe", "min", "minBound",
        "minimum", "mod", "negate", "not", "notElem", "null", "odd", "or",
        "otherwise", "pi", "pred", "print", "product", "properFraction", "pure",
        "putChar", "putStr", "putStrLn", "quot", "quotRem", "read", "readFile",
        "readIO", "readList", "readLn", "readParen", "reads", "readsPrec",
        "realToFrac", "recip", "rem", "repeat", "replicate", "return", "reverse",
        "round", "scaleFloat", "scanl", "scanl1", "scanr", "scanr1", "seq",
        "sequence", "sequence_", "show", "showChar", "showList", "showParen",
        "showString", "shows", "showsPrec", "significand", "signum", "sin",
        "sinh", "snd", "span", "splitAt", "sqrt", "subtract", "succ", "sum",
        "tail", "take", "takeWhile", "tan", "tanh", "toEnum", "toInteger",
        "toRational", "truncate", "uncurry", "undefined", "unlines", "until",
        "unwords", "unzip", "unzip3", "userError", "words", "writeFile", "zip",
        "zip3", "zipWith", "zipWith3"
    )
}

data class HaskellState(
    var f: HaskellTokenFn = haskellNormal()
)

val haskell: StreamParser<HaskellState> = object : StreamParser<HaskellState> {
    override val name: String get() = "haskell"

    override fun startState(indentUnit: Int) = HaskellState(f = haskellNormal())
    override fun copyState(state: HaskellState) = state.copy()

    override fun token(stream: StringStream, state: HaskellState): String? {
        val t = state.f.invoke(stream) { s -> state.f = s }
        val w = stream.current()
        return haskellWellKnownWords[w] ?: t
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "line" to "--",
                "block" to mapOf("open" to "{-", "close" to "-}")
            )
        )
}
