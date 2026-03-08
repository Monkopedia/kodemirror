/*
 * Copyright 2025 Jason Monk
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
@file:Suppress("ktlint:standard:max-line-length")

package com.monkopedia.kodemirror.lang.sass

import com.monkopedia.kodemirror.lezer.lr.ContextTracker
import com.monkopedia.kodemirror.lezer.lr.ExternalTokenizer
import com.monkopedia.kodemirror.lezer.lr.LRParser
import com.monkopedia.kodemirror.lezer.lr.ParserSpec
import com.monkopedia.kodemirror.lezer.lr.SpecializerSpec

// Term constants from generated grammar
private const val INDENT = 168
private const val DEDENT = 169
private const val DESCENDANT_OP = 170
private const val INTERPOLATION_END = 1
private const val INTERPOLATION_CONTINUE = 2
private const val UNIT = 3
private const val CALLEE = 171
private const val IDENTIFIER = 172
private const val VARIABLE_NAME = 4
private const val QUERY_IDENTIFIER = 173
private const val INTERPOLATION_START = 5
private const val NEWLINE_TOKEN = 174
private const val BLANK_LINE_START = 175
private const val EOF_TOKEN = 176
private const val WHITESPACE = 177
private const val LINE_COMMENT = 6
private const val COMMENT = 7
private const val INDENTED_MIXIN = 8
private const val INDENTED_INCLUDE = 9
private const val DIALECT_INDENTED = 0

// Character constants
private const val COLON = 58
private const val PAREN_L = 40
private const val UNDERSCORE = 95
private const val BRACKET_L = 91
private const val DASH = 45
private const val PERIOD = 46
private const val HASH = 35
private const val PERCENT = 37
private const val BRACE_L = 123
private const val BRACE_R = 125
private const val SLASH = 47
private const val ASTERISK = 42
private const val NEWLINE_CHAR = 10
private const val EQUALS = 61
private const val PLUS = 43
private const val AND = 38

private val space = intArrayOf(
    9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195,
    8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239,
    8287, 12288
)

private fun isAlpha(ch: Int): Boolean = ch in 65..90 || ch in 97..122 || ch >= 161

private fun isDigit(ch: Int): Boolean = ch in 48..57

private fun startOfComment(input: com.monkopedia.kodemirror.lezer.lr.InputStream): Boolean {
    val next: Int
    return input.next == SLASH && run {
        next = input.peek(1)
        next == SLASH || next == ASTERISK
    }
}

// --- IndentLevel context for indented dialect ---

private class IndentLevel(val parent: IndentLevel?, val depth: Int) {
    val hash: Int = ((parent?.let { it.hash + (it.hash shl 8) } ?: 0) + depth + (depth shl 4))
}

private val topIndent = IndentLevel(null, 0)

@Suppress("UNCHECKED_CAST")
private val trackIndent = ContextTracker(
    start = topIndent,
    shift = { context, term, stack, input ->
        when (term) {
            INDENT -> IndentLevel(context, stack.pos - input.pos)
            DEDENT -> context.parent ?: context
            else -> context
        }
    },
    hash = { context -> context.hash }
) as ContextTracker<Any?>

// --- External tokenizer 1: spaces ---

private val spaces = ExternalTokenizer({ input, stack ->
    if (stack.dialectEnabled(DIALECT_INDENTED)) {
        val prev: Int
        if (input.next < 0 && stack.canShift(EOF_TOKEN)) {
            input.acceptToken(EOF_TOKEN)
        } else {
            prev = input.peek(-1)
            if ((prev == NEWLINE_CHAR || prev < 0) &&
                stack.canShift(BLANK_LINE_START)
            ) {
                var spaceCount = 0
                while (input.next != NEWLINE_CHAR && input.next in space) {
                    input.advance()
                    spaceCount++
                }
                if (input.next == NEWLINE_CHAR || startOfComment(input)) {
                    input.acceptToken(BLANK_LINE_START, -spaceCount)
                } else if (spaceCount > 0) {
                    input.acceptToken(WHITESPACE)
                }
            } else if (input.next == NEWLINE_CHAR) {
                input.acceptToken(NEWLINE_TOKEN, 1)
            } else if (input.next in space) {
                input.advance()
                while (input.next != NEWLINE_CHAR && input.next in space) {
                    input.advance()
                }
                input.acceptToken(WHITESPACE)
            }
        }
    } else {
        var length = 0
        while (input.next in space) {
            input.advance()
            length++
        }
        if (length > 0) input.acceptToken(WHITESPACE)
    }
}, contextual = true)

// --- External tokenizer 2: comments ---

private val comments = ExternalTokenizer({ input, stack ->
    if (startOfComment(input)) {
        input.advance()
        if (stack.dialectEnabled(DIALECT_INDENTED)) {
            var indentedComment = -1
            var off = 1
            while (true) {
                val prev = input.peek(-off - 1)
                if (prev == NEWLINE_CHAR || prev < 0) {
                    indentedComment = off + 1
                    break
                } else if (prev !in space) {
                    break
                }
                off++
            }
            if (indentedComment > -1) {
                val block = input.next == ASTERISK
                var end = 0
                input.advance()
                while (input.next >= 0) {
                    if (input.next == NEWLINE_CHAR) {
                        input.advance()
                        var indented = 0
                        while (input.next != NEWLINE_CHAR && input.next in space) {
                            indented++
                            input.advance()
                        }
                        if (indented < indentedComment) {
                            end = -indented - 1
                            break
                        }
                    } else if (block && input.next == ASTERISK &&
                        input.peek(1) == SLASH
                    ) {
                        end = 2
                        break
                    } else {
                        input.advance()
                    }
                }
                input.acceptToken(
                    if (block) COMMENT else LINE_COMMENT,
                    end
                )
                return@ExternalTokenizer
            }
        }
        if (input.next == SLASH) {
            while (input.next != NEWLINE_CHAR && input.next >= 0) input.advance()
            input.acceptToken(LINE_COMMENT)
        } else {
            input.advance()
            while (input.next >= 0) {
                val next = input.next
                input.advance()
                if (next == ASTERISK && input.next == SLASH) {
                    input.advance()
                    break
                }
            }
            input.acceptToken(COMMENT)
        }
    }
})

// --- External tokenizer 3: indentedMixins ---

private val indentedMixins = ExternalTokenizer({ input, stack ->
    if ((input.next == PLUS || input.next == EQUALS) &&
        stack.dialectEnabled(DIALECT_INDENTED)
    ) {
        input.acceptToken(
            if (input.next == EQUALS) INDENTED_MIXIN else INDENTED_INCLUDE,
            1
        )
    }
})

// --- External tokenizer 4: indentation ---

private val indentation = ExternalTokenizer({ input, stack ->
    if (stack.dialectEnabled(DIALECT_INDENTED)) {
        val ctx = stack.context as? IndentLevel
        val cDepth = ctx?.depth ?: 0
        if (input.next < 0 && cDepth > 0) {
            input.acceptToken(DEDENT)
        } else {
            val prev = input.peek(-1)
            if (prev == NEWLINE_CHAR) {
                var depth = 0
                while (input.next != NEWLINE_CHAR && input.next in space) {
                    input.advance()
                    depth++
                }
                if (depth != cDepth && input.next != NEWLINE_CHAR &&
                    !startOfComment(input)
                ) {
                    if (depth < cDepth) {
                        input.acceptToken(DEDENT, -depth)
                    } else {
                        input.acceptToken(INDENT)
                    }
                }
            }
        }
    }
})

// --- External tokenizer 5: identifiers ---

private val identifiers = ExternalTokenizer({ input, stack ->
    var inside = false
    var dashes = 0
    var i = 0
    while (true) {
        val next = input.next
        if (isAlpha(next) || next == DASH || next == UNDERSCORE ||
            (inside && isDigit(next))
        ) {
            if (!inside && (next != DASH || i > 0)) inside = true
            if (dashes == i && next == DASH) dashes++
            input.advance()
        } else if (next == HASH && input.peek(1) == BRACE_L) {
            input.acceptToken(INTERPOLATION_START, 2)
            break
        } else {
            if (inside) {
                input.acceptToken(
                    when {
                        dashes == 2 && stack.canShift(VARIABLE_NAME) ->
                            VARIABLE_NAME
                        stack.canShift(QUERY_IDENTIFIER) ->
                            QUERY_IDENTIFIER
                        next == PAREN_L -> CALLEE
                        else -> IDENTIFIER
                    }
                )
            }
            break
        }
        i++
    }
})

// --- External tokenizer 6: interpolationEnd ---

private val interpolationEnd = ExternalTokenizer({ input, _ ->
    if (input.next == BRACE_R) {
        input.advance()
        while (isAlpha(input.next) || input.next == DASH ||
            input.next == UNDERSCORE || isDigit(input.next)
        ) {
            input.advance()
        }
        if (input.next == HASH && input.peek(1) == BRACE_L) {
            input.acceptToken(INTERPOLATION_CONTINUE, 2)
        } else {
            input.acceptToken(INTERPOLATION_END)
        }
    }
})

// --- External tokenizer 7: descendant ---

private val descendant = ExternalTokenizer({ input, _ ->
    if (input.peek(-1) in space) {
        val next = input.next
        if (isAlpha(next) || next == UNDERSCORE || next == HASH ||
            next == PERIOD || next == BRACKET_L ||
            (next == COLON && isAlpha(input.peek(1))) ||
            next == DASH || next == AND || next == ASTERISK
        ) {
            input.acceptToken(DESCENDANT_OP)
        }
    }
})

// --- External tokenizer 8: unitToken ---

private val unitToken = ExternalTokenizer({ input, _ ->
    if (input.peek(-1) !in space) {
        val next = input.next
        if (next == PERCENT) {
            input.advance()
            input.acceptToken(UNIT)
        }
        if (isAlpha(next)) {
            do {
                input.advance()
            } while (isAlpha(input.next) || isDigit(input.next))
            input.acceptToken(UNIT)
        }
    }
})

// --- Specializer maps ---

private val specIdentifier = mapOf(
    "not" to 62,
    "using" to 197,
    "as" to 207,
    "with" to 211,
    "without" to 211,
    "hide" to 225,
    "show" to 225,
    "if" to 263,
    "from" to 269,
    "to" to 271,
    "through" to 273,
    "in" to 279
)

private val specCallee = mapOf(
    "url" to 82,
    "url-prefix" to 82,
    "domain" to 82,
    "regexp" to 82,
    "lang" to 104,
    "nth-child" to 104,
    "nth-last-child" to 104,
    "nth-of-type" to 104,
    "nth-last-of-type" to 104,
    "dir" to 104,
    "host-context" to 104
)

private val specAtKeyword = mapOf(
    "@import" to 162,
    "@include" to 194,
    "@mixin" to 200,
    "@function" to 200,
    "@use" to 204,
    "@extend" to 214,
    "@at-root" to 218,
    "@forward" to 222,
    "@media" to 228,
    "@charset" to 232,
    "@namespace" to 236,
    "@keyframes" to 242,
    "@supports" to 254,
    "@if" to 258,
    "@else" to 260,
    "@for" to 266,
    "@each" to 276,
    "@while" to 282,
    "@debug" to 286,
    "@warn" to 286,
    "@error" to 286,
    "@return" to 286
)

private val specQueryIdentifier = mapOf(
    "layer" to 166,
    "not" to 184,
    "only" to 184,
    "selector" to 190
)

val sassParser: LRParser = LRParser.deserialize(
    ParserSpec(
        version = 14,
        states = "!\$WQ`Q+tOOO#fQ+tOOP#mOpOOOOQ#U'#Ch'#ChO#rQ(pO'#CjOOQ#U'#Ci'#CiO%_Q)QO'#GXO%rQ.jO'#CnO&mQ#dO'#D]O'dQ(pO'#CgO'kQ)OO'#D_O'vQ#dO'#DfO'{Q#dO'#DiO(QQ#dO'#DqOOQ#U'#GX'#GXO(VQ(pO'#GXO(^Q(nO'#DuO%rQ.jO'#D}O%rQ.jO'#E`O%rQ.jO'#EcO%rQ.jO'#EeO(cQ)OO'#EjO)TQ)OO'#ElO%rQ.jO'#EnO)bQ)OO'#EqO%rQ.jO'#EsO)|Q)OO'#EuO*XQ)OO'#ExO*aQ)OO'#FOO*uQ)OO'#FbOOQ&Z'#GW'#GWOOQ&Y'#Fe'#FeO+PQ(nO'#FeQ`Q+tOOO%rQ.jO'#FQO+[Q(nO'#FUO+aQ)OO'#FZO%rQ.jO'#F^O%rQ.jO'#F`OOQ&Z'#Fm'#FmO+iQ+uO'#GaO+vQ(oO'#GaQOQ#SOOP,XO#SO'#GVPOOO)CAz)CAzOOQ#U'#Cm'#CmOOQ#U,59W,59WOOQ#i'#Cp'#CpO%rQ.jO'#CsO,xQ.wO'#CuO/dQ.^O,59YO%rQ.jO'#CzOOQ#S'#DP'#DPO/uQ(nO'#DUO/zQ)OO'#DZOOQ#i'#GZ'#GZO0SQ(nO'#DOOOQ#U'#D^'#D^OOQ#U,59w,59wO&mQ#dO,59wO0XQ)OO,59yO'vQ#dO,5:QO'{Q#dO,5:TO(cQ)OO,5:WO(cQ)OO,5:YO(cQ)OO,5:ZO(cQ)OO'#FlO0dQ(nO,59RO0oQ+tO'#DsO0vQ#TO'#DsOOQ&Z,59R,59ROOQ#U'#Da'#DaOOQ#S'#Dd'#DdOOQ#U,59y,59yO0{Q(nO,59yO1QQ(nO,59yOOQ#U'#Dh'#DhOOQ#U,5:Q,5:QOOQ#S'#Dj'#DjO1VQ9`O,5:TOOQ#U'#Dr'#DrOOQ#U,5:],5:]O2YQ.jO,5:aO2dQ.jO,5:iO3`Q.jO,5:zO3mQ.YO,5:}O4OQ.jO,5;POOQ#U'#Cj'#CjO4wQ(pO,5;UO5UQ(pO,5;WOOQ&Z,5;W,5;WO5]Q)OO,5;WO5bQ.jO,5;YOOQ#S'#ET'#ETO6TQ.jO'#E]O6kQ(nO'#GcO*aQ)OO'#EZO7PQ(nO'#E^OOQ#S'#Gd'#GdO0gQ(nO,5;]O4UQ.YO,5;_OOQ#d'#Ew'#EwO+PQ(nO,5;aO7UQ)OO,5;aOOQ#S'#Ez'#EzO7^Q(nO,5;dO7cQ(nO,5;jO7nQ(nO,5;|OOQ&Z'#Gf'#GfOOQ&Y,5<P,5<POOQ&Y-E9c-E9cO3mQ.YO,5;lO7|Q)OO,5;pO8RQ)OO'#GhO8ZQ)OO,5;uO3mQ.YO,5;xO4UQ.YO,5;zOOQ&Z-E9k-E9kO8`Q(oO,5<{OOQ&Z'#Gb'#GbO8qQ+uO'#FpO8`Q(oO,5<{POO#S'#Fd'#FdP9UO#SO,5<qPOOO,5<q,5<qO9dQ.YO,59_OOQ#i,59a,59aO%rQ.jO,59cO%rQ.jO,59hO%rQ.jO'#FiO9rQ#WO1G.tOOQ#k1G.t1G.tO9zQ.oO,59fO<pQ! lO,59pOOQ#d'#D['#D[OOQ#d'#Fh'#FhO<{Q)OO,59uOOQ#i,59u,59uO={Q.jO'#DQOOQ#i,59j,59jOOQ#U1G/c1G/cOOQ#U1G/e1G/eO0{Q(nO1G/eO1QQ(nO1G/eOOQ#U1G/l1G/lO>VQ9`O1G/oO>pQ(pO1G/rO?dQ(pO1G/tO@WQ(pO1G/uO@zQ(pO,5<WOOQ#S-E9j-E9jOOQ&Z1G.m1G.mOAXQ(nO,5:_OA^Q+uO,5:_OAeQ)OO'#DeOAlQ.jO'#DcOOQ#U1G/o1G/oO%rQ.jO1G/oOBkQ.jO'#DwOBuQ.kO1G/{OOQ#T1G/{1G/{OCrQ)OO'#EQO+PQ(nO1G0TO2pQ)OO1G0TODaQ+uO'#GfOOQ&Z1G0f1G0fO0SQ(nO1G0fOOQ&Z1G0i1G0iOOQ&Z1G0k1G0kO0SQ(nO1G0kOFyQ)OO1G0kOOQ&Z1G0p1G0pOOQ&Z1G0r1G0rOGRQ)" +
            "OO1G0rOGWQ(nO1G0rOG]Q)OO1G0tOOQ&Z1G0t1G0tOGkQ.jO'#FsOG{Q#dO1G0tOHQQ!N^O'#CuOH]Q!NUO'#ETOHkQ!NUO,5:pOHsQ(nO,5:wOOQ#S'#Ge'#GeOHnQ!NUO,5:sO*aQ)OO,5:rOH{Q)OO'#FrOI`Q(nO,5<}OIqQ(nO,5:uO(cQ)OO,5:xOOQ&Z1G0w1G0wOOQ&Z1G0y1G0yOOQ&Z1G0{1G0{O+PQ(nO1G0{OJYQ)OO'#E{OOQ&Z1G1O1G1OOOQ&Z1G1U1G1UOOQ&Z1G1h1G1hOJeQ+uO1G1WO%rQ.jO1G1[OL}Q)OO'#FxOMYQ)OO,5=SO%rQ.jO1G1aOOQ&Z1G1d1G1dOOQ&Z1G1f1G1fOMbQ(oO1G2gOMsQ+uO,5<[OOQ#T,5<[,5<[OOQ#T-E9n-E9nPOO#S-E9b-E9bPOOO1G2]1G2]OOQ#i1G.y1G.yONWQ.oO1G.}OOQ#i1G/S1G/SO!!|Q.^O,5<TOOQ#W-E9g-E9gOOQ#k7+\$`7+\$`OOQ#i1G/[1G/[O!#_Q(nO1G/[OOQ#d-E9f-E9fOOQ#i1G/a1G/aO!#dQ.jO'#FfO!\$qQ.jO'#G]O!&]Q.jO'#GZO!&dQ(nO,59lOOQ#U7+%P7+%POOQ#U7+%Z7+%ZO%rQ.jO7+%ZOOQ&Z1G/y1G/yO!&iQ#TO1G/yO!&nQ(pO'#G_O!&xQ(nO,5:PO!&}Q.jO'#G^O!'XQ(nO,59}O!'^Q.YO7+%ZO!'lQ.YO'#GZO!'}Q(nO,5:cOOQ#T,5:c,5:cO!(VQ.kO'#FoO%rQ.jO'#FoO!)yQ.kO7+%gOOQ#T7+%g7+%gO!*mQ#dO,5:lOOQ&Z7+%o7+%oO+PQ(nO7+%oO7nQ(nO7+&QO+PQ(nO7+&VOOQ#d'#Eh'#EhO!*rQ)OO7+&VO!+QQ(nO7+&^O*aQ)OO7+&^OOQ#d-E9q-E9qOOQ&Z7+&`7+&`O!+VQ.jO'#GgOOQ#d,5<_,5<_OF|Q(nO7+&`O%rQ.jO1G0[O!+qQ.jO1G0_OOQ#S1G0c1G0cOOQ#S1G0^1G0^O!+xQ(nO,5<^OOQ#S-E9p-E9pO!,^Q(pO1G0dOOQ&Z7+&g7+&gO,gQ(vO'#CuOOQ#S'#E}'#E}O!,eQ(nO'#E|OOQ#S'#E|'#E|O!,sQ(nO'#FuO!-OQ)OO,5;gOOQ&Z,5;g,5;gO!-ZQ+uO7+&rO!/sQ)OO7+&rO!0OQ.jO7+&vOOQ#d,5<d,5<dOOQ#d-E9v-E9vO3mQ.YO7+&{OOQ#T1G1v1G1vOOQ#i7+\$v7+\$vOOQ#d-E9d-E9dO!0aQ.jO'#FgO!0nQ(nO,5<wO!0nQ(nO,5<wO%rQ.jO,5<wOOQ#i1G/W1G/WO!0vQ.YO<<HuOOQ&Z7+%e7+%eO!1UQ)OO'#FkO!1`Q(nO,5<yOOQ#U1G/k1G/kO!1hQ.jO'#FjO!1rQ(nO,5<xOOQ#U1G/i1G/iOOQ#U<<Hu<<HuO1_Q.jO,5<YO!1zQ(nO'#FnOOQ#S-E9l-E9lOOQ#T1G/}1G/}O!2PQ.kO,5<ZOOQ#e-E9m-E9mOOQ#T<<IR<<IROOQ#S'#ES'#ESO!3sQ(nO1G0WOOQ&Z<<IZ<<IZOOQ&Z<<Il<<IlOOQ&Z<<Iq<<IqO0SQ(nO<<IqO*aQ)OO<<IxO!3{Q(nO<<IxO!4TQ.jO'#FtO!4hQ)OO,5=ROG]Q)OO<<IzO!4yQ.jO7+%vOOQ#S'#EV'#EVO!5QQ!NUO7+%yOOQ#S7+&O7+&OOOQ#S,5;h,5;hOJ]Q)OO'#FvO!,sQ(nO,5<aOOQ#d,5<a,5<aOOQ#d-E9s-E9sOOQ&Z1G1R1G1ROOQ&Z-E9u-E9uO!/sQ)OO<<J^O%rQ.jO,5<cOOQ&Z<<J^<<J^O%rQ.jO<<JbOOQ&Z<<Jg<<JgO!5YQ.jO,5<RO!5gQ.jO,5<ROOQ#S-E9e-E9eO!5nQ(nO1G2cO!5vQ.jO1G2cOOQ#UAN>aAN>aO!6QQ(pO,5<VOOQ#S-E9i-E9iO!6[Q.jO,5<UOOQ" +
            "#S-E9h-E9hO!6fQ.YO1G1tO!6oQ(nO1G1tO!*mQ#dO'#FqO!6zQ(nO7+%rOOQ#d7+%r7+%rO+PQ(nOAN?]O!7SQ(nOAN?dO0gQ(nOAN?dO!7[Q.jO,5<`OOQ#d-E9r-E9rOG]Q)OOAN?fOOQ&ZAN?fAN?fOOQ#S<<Ib<<IbOOQ#S<<Ie<<IeO!7vQ.jO<<IeOOQ#S,5<b,5<bOOQ#S-E9t-E9tOOQ#d1G1{1G1{P!8_Q)OO'#FwOOQ&ZAN?xAN?xO3mQ.YO1G1}O3mQ.YOAN?|OOQ#S1G1m1G1mO%rQ.jO1G1mO!8dQ(nO7+'}OOQ#S7+'`7+'`OOQ#S,5<],5<]OOQ#S-E9o-E9oOOQ#d<<I^<<I^OOQ&ZG24wG24wO0gQ(nOG25OOOQ&ZG25OG25OOOQ&ZG25QG25QO!8lQ(nOAN?POOQ&Z7+'i7+'iOOQ&ZG25hG25hO!8qQ.jO7+'XOOQ&ZLD*jLD*jOOQ#SG24kG24k",
        stateData = "!9R~O\$wOSVOSUOS\$uQQ~OS`OTVOWcOXbO_UOc`OqWOuYO|[O!SYO!ZZO!rmO!saO#TbO#WcO#YdO#_eO#afO#cgO#fhO#hiO#jjO#mkO#slO#urO#ysO\$OtO\$RuO\$TvO\$rSO\$|RO%S]O~O\$m%TP~P`O\$u{O~Oq^Xu^Xu!jXw^X|^X!S^X!Z^X!a^X!d^X!h^X\$p^X\$t^X~Oq\${Xu\${Xw\${X|\${X!S\${X!Z\${X!a\${X!d\${X!h\${X\$p\${X\$t\${X~O\$r}O!o\${X\$v\${Xf\${Xe\${X~P\$jOS!XOTVO_!XOc!XOf!QOh!XOj!XOo!TOy!VO|!WO\$q!UO\$r!PO%O!RO~O\$r!ZO~Oq!]Ou!^O|!`O!S!^O!Z!_O!a!aO!d!cO!h!fO\$p!bO\$t!gO~Ow!dO~P&rO!U!mO\$q!jO\$r!iO~O\$r!nO~O\$r!pO~O\$r!rO~Ou!tO~P\$jOu!tO~OTVO_UOqWOuYO|[O!SYO!ZZO\$r!yO\$|RO%S]O~Of!}O!h!fO\$t!gO~P(cOTVOc#UOf#QO#O#SO#R#TO\$s#PO!h%VP\$t%VP~Oj#YOy!VO\$r#XO~Oj#[O\$r#[O~OTVOc#UOf#QO#O#SO#R#TO\$s#PO~O!o%VP\$v%VP~P)bO!o#`O\$t#`O\$v#`O~Oc#dO~Oc#eO\$P%[P~O\$m%TX!p%TX\$o%TX~P`O!o#kO\$t#kO\$m%TX!p%TX\$o%TX~OU#nOV#nO\$t#pO\$w#nO~OR#rO\$tiX!hiXeiXwiX~OPiXQiXliXmiXqiXTiXciXfiX!oiX!uiX#OiX#RiX\$siX\$viX#UiX#ZiX#]iX#diXSiX_iXhiXjiXoiXyiX|iX!liX!miX!niX\$qiX\$riX%OiX\$miXviX{iX#{iX#|iX!piX\$oiX~P,gOP#wOQ#uOl#sOm#sOq#tO~Of#yO~O{#}O\$r#zO~Of\$OO~O!U\$TO\$q!jO\$r!iO~Ow!dO!h!fO\$t!gO~O!p%TP~P`O\$n\$_O~Of\$`O~Of\$aO~O{\$bO!_\$cO~OS!XOTVO_!XOc!XOf\$dOh!XOj!XOo!TOy!VO|!WO\$q!UO\$r!PO%O!RO~O!h!fO\$t!gO~P1_Ol#sOm#sOq#tO!u\$gO!o%VP\$t%VP\$v%VP~P*aOl#sOm#sOq#tO!o#`O\$v#`O~O!h!fO#U\$lO\$t\$jO~P2}Ol#sOm#sOq#tO!h!fO\$t!gO~O#Z\$pO#]\$oO\$t#`O~P2}Oq!]Ou!^O|!`O!S!^O!Z!_O!a!aO!d!cO\$p!bO~O!o#`O\$t#`O\$v#`O~P4]Of\$sO~P&rO#]\$tO~O#Z\$xO#d\$wO\$t#`O~P2}OS\$}Oh\$}Oj\$}Oy!VO\$q!UO%O\$yO~OTVOc#UOf#QO#O#SO#R#TO\$s\$zO~P5oOm%POw%QO!h%VX\$t%VX!o%VX\$v%VX~Of%TO~Oj%XOy!VO~O!h%YO~Om%PO!h!fO\$t!gO~O!h!fO!o#`O\$t\$jO\$v#`O~O#z%_O~Ow%`O\$P%[X~O\$P%bO~O!o#kO\$t#kO\$m%Ta!p%Ta\$o%Ta~O!o\$dX\$m\$dX\$t\$dX!p\$dX\$o\$dX~P`OU#nOV#nO\$t%jO\$w#nO~Oe%kOl#sOm#sOq#tO~OP%pOQ#uO~Ol#sOm#sOq#tOPnaQnaTnacnafna!ona!una#Ona#Rna\$sna\$tna\$vna!hna#Una#Zna#]na#dnaenaSna_nahnajnaonawnayna|na!lna!mna!nna\$qna\$rna%Ona\$mnavna{na#{na#|na!pna\$ona~Oe%qOj%rOz%rO~O{%tO\$r#zO~OS!XOTVO_!XOf!QOh!XOj!XOo!TOy!VO|!WO\$q!UO\$r!PO%O!RO~Oc%wOe%PP~P=TO{%zO!_%{O~Oq!]Ou!^O|!`O!S!^O!Z!_O~Ow!`i!a!`i!d!" +
            "`i!h!`i\$p!`i\$t!`i!o!`i\$v!`if!`ie!`i~P>_Ow!bi!a!bi!d!bi!h!bi\$p!bi\$t!bi!o!bi\$v!bif!bie!bi~P>_Ow!ci!a!ci!d!ci!h!ci\$p!ci\$t!ci!o!ci\$v!cif!cie!ci~P>_Ow\$`a!h\$`a\$t\$`a~P4]O!p%|O~O\$o%TP~P`Oe%RP~P(cOe%QP~P%rOS!XOTVO_!XOc!XOf!QOh!XOo!TOy!VO|!WO\$q!UO\$r!PO%O!RO~Oe&VOj&TO~PAsOl#sOm#sOq#tOw&XO!l&ZO!m&ZO!n&ZO!o!ii\$t!ii\$v!ii\$m!ii!p!ii\$o!ii~P%rOf&[OT!tXc!tX!o!tX#O!tX#R!tX\$s!tX\$t!tX\$v!tX~O\$n\$_OS%YXT%YXW%YXX%YX_%YXc%YXq%YXu%YX|%YX!S%YX!Z%YX!r%YX!s%YX#T%YX#W%YX#Y%YX#_%YX#a%YX#c%YX#f%YX#h%YX#j%YX#m%YX#s%YX#u%YX#y%YX\$O%YX\$R%YX\$T%YX\$m%YX\$r%YX\$|%YX%S%YX!p%YX!o%YX\$t%YX\$o%YX~O\$r!PO\$|&aO~O#]&cO~Ou&dO~O!o#`O#d\$wO\$t#`O\$v#`O~O!o%ZP#d%ZP\$t%ZP\$v%ZP~P%rO\$r!PO~OR#rO!|iXeiX~Oe!wXm!wXu!yX!|!yX~Ou&jO!|&kO~Oe&lOm%PO~Ow\$fX!h\$fX\$t\$fX!o\$fX\$v\$fX~P*aOw%QO!h%Va\$t%Va!o%Va\$v%Va~Om%POw!}a!h!}a\$t!}a!o!}a\$v!}ae!}a~O!p&xO\$r&sO%O&rO~O#v&zOS#tiT#tiW#tiX#ti_#tic#tiq#tiu#ti|#ti!S#ti!Z#ti!r#ti!s#ti#T#ti#W#ti#Y#ti#_#ti#a#ti#c#ti#f#ti#h#ti#j#ti#m#ti#s#ti#u#ti#y#ti\$O#ti\$R#ti\$T#ti\$m#ti\$r#ti\$|#ti%S#ti!p#ti!o#ti\$t#ti\$o#ti~Oc&|Ow\$lX\$P\$lX~Ow%`O\$P%[a~O!o#kO\$t#kO\$m%Ti!p%Ti\$o%Ti~O!o\$da\$m\$da\$t\$da!p\$da\$o\$da~P`Oq#tOPkiQkilkimkiTkickifki!oki!uki#Oki#Rki\$ski\$tki\$vki!hki#Uki#Zki#]ki#dkiekiSki_kihkijkiokiwkiyki|ki!lki!mki!nki\$qki\$rki%Oki\$mkivki{ki#{ki#|ki!pki\$oki~Ol#sOm#sOq#tOP\$]aQ\$]a~Oe'QO~Ol#sOm#sOq#tOS\$YXT\$YX_\$YXc\$YXe\$YXf\$YXh\$YXj\$YXo\$YXv\$YXw\$YXy\$YX|\$YX\$q\$YX\$r\$YX%O\$YX~Ov'UOw'SOe%PX~P%rOS\$}XT\$}X_\$}Xc\$}Xe\$}Xf\$}Xh\$}Xj\$}Xl\$}Xm\$}Xo\$}Xq\$}Xv\$}Xw\$}Xy\$}X|\$}X\$q\$}X\$r\$}X%O\$}X~Ou'VO~P!%OOe'WO~O\$o'YO~Ow'ZOe%RX~P4]Oe']O~Ow'^Oe%QX~P%rOe'`O~Ol#sOm#sOq#tO{'aO~Ou'bOe\$}Xl\$}Xm\$}Xq\$}X~Oe'eOj'cO~Ol#sOm#sOq#tOS\$cXT\$cX_\$cXc\$cXf\$cXh\$cXj\$cXo\$cXw\$cXy\$cX|\$cX!l\$cX!m\$cX!n\$cX!o\$cX\$q\$cX\$r\$cX\$t\$cX\$v\$cX%O\$cX\$m\$cX!p\$cX\$o\$cX~Ow&XO!l'hO!m'hO!n'hO!o!iq\$t!iq\$v!iq\$m!iq!p!iq\$o!iq~P%rO\$r'iO~O!o#`O#]'nO\$t#`O\$v#`O~Ou'oO~Ol#sOm#sOq#tOw'qO!o%ZX#d%ZX\$t%ZX\$v%ZX~O\$s'uO~P5oOm%POw\$fa!h\$fa\$t\$fa!o\$fa\$v\$fa~Oe'wO~P4]O%O&" +
            "rOw#pX!h#pX\$t#pX~Ow'yO!h!fO\$t!gO~O!p'}O\$r&sO%O&rO~O#v(POS#tqT#tqW#tqX#tq_#tqc#tqq#tqu#tq|#tq!S#tq!Z#tq!r#tq!s#tq#T#tq#W#tq#Y#tq#_#tq#a#tq#c#tq#f#tq#h#tq#j#tq#m#tq#s#tq#u#tq#y#tq\$O#tq\$R#tq\$T#tq\$m#tq\$r#tq\$|#tq%S#tq!p#tq!o#tq\$t#tq\$o#tq~O!h!fO#w(QO\$t!gO~Ol#sOm#sOq#tO#{(SO#|(SO~Oc(VOe\$ZXw\$ZX~P=TOw'SOe%Pa~Ol#sOm#sOq#tO{(ZO~Oe\$_Xw\$_X~P(cOw'ZOe%Ra~Oe\$^Xw\$^X~P%rOw'^Oe%Qa~Ou'bO~Ol#sOm#sOq#tOS\$caT\$ca_\$cac\$caf\$cah\$caj\$cao\$caw\$cay\$ca|\$ca!l\$ca!m\$ca!n\$ca!o\$ca\$q\$ca\$r\$ca\$t\$ca\$v\$ca%O\$ca\$m\$ca!p\$ca\$o\$ca~Oe(dOq(bO~Oe(gOm%PO~Ow\$hX!o\$hX#d\$hX\$t\$hX\$v\$hX~P%rOw'qO!o%Za#d%Za\$t%Za\$v%Za~Oe(lO~P%rOe(mO!|(nO~Ov(vOe\$Zaw\$Za~P%rOu(wO~P!%OOw'SOe%Pi~Ow'SOe%Pi~P%rOe\$_aw\$_a~P4]Oe\$^aw\$^a~P%rOl#sOm#sOq#tOw(yOe\$bij\$bi~Oe(|Oq(bO~Oe)OOm%PO~Ol#sOm#sOq#tOw\$ha!o\$ha#d\$ha\$t\$ha\$v\$ha~OS\$}Oh\$}Oj\$}Oy!VO\$q!UO\$s'uO%O&rO~O#w(QO~Ow'SOe%Pq~Oe)WO~Oe\$Zqw\$Zq~P%rO%Oql!dl~",
        goto = "=Y%]PPPPPPPPPPP%^%h%h%{P%h&`&cP(UPP)ZP*YP)ZPP)ZP)ZP+f,j-lPPP-xPPPP)Z/S%h/W%hP/^P/d/j/p%hP/v%h/|P%hP%h%hP%h0S0VP1k1}2XPPPPP%^PP2_P2b'w'w2h'w'wP'wP'w'wP%^PP%^P%^PP2qP%^P%^P%^PP%^P%^P%^P2w%^P2z2}3Q3X%^P%^PPP%^PPPP%^PP%^P%^P%^P3^3d3j4Y4h4n4t4z5Q5W5d5j5p5z6Q6W6b6h6n6t6zPPPPPPPPPPPP7Q7T7aP8WP:_:b:eP:h:q:w;T;p;y=S=VanOPqx!f#l\$_%fs^OPefqx!a!b!c!d!f#l\$_\$`%T%f'ZsTOPefqx!a!b!c!d!f#l\$_\$`%T%f'ZR!OUb^ef!a!b!c!d\$`%T'Z`_OPqx!f#l\$_%f!x!XVabcdgiruv!Q!T!t#s#t#u\$O\$a\$c\$d\$e\$w%_%b%v%{&Q&X&Y&j'S'V'^'b'q't(Q(S(U(Y(^(w)Ug#Uhlm!u#Q#S\$i%P%Q&d'o!x!XVabcdgiruv!Q!T!t#s#t#u\$O\$a\$c\$d\$e\$w%_%b%v%{&Q&X&Y&j'S'V'^'b'q't(Q(S(U(Y(^(w)UQ&b\$pR&i\$x!y!XVabcdgiruv!Q!T!t#s#t#u\$O\$a\$c\$d\$e\$w%_%b%v%{&Q&X&Y&j'S'V'^'b'q't(Q(S(U(Y(^(w)U!x!XVabcdgiruv!Q!T!t#s#t#u\$O\$a\$c\$d\$e\$w%_%b%v%{&Q&X&Y&j'S'V'^'b'q't(Q(S(U(Y(^(w)UU\$}#Q&k(nU&u%Y&w'yR'x&t!x!XVabcdgiruv!Q!T!t#s#t#u\$O\$a\$c\$d\$e\$w%_%b%v%{&Q&X&Y&j'S'V'^'b'q't(Q(S(U(Y(^(w)UV\$}#Q&k(n#P!YVabcdgiruv!Q!T!t#Q#s#t#u\$O\$a\$c\$d\$e\$w%_%b%v%{&Q&X&Y&j&k'S'V'^'b'q't(Q(S(U(Y(^(n(w)UQ\$P!YQ&_\$lQ&`\$oR(e'n!x!XVabcdgiruv!Q!T!t#s#t#u\$O\$a\$c\$d\$e\$w%_%b%v%{&Q&X&Y&j'S'V'^'b'q't(Q(S(U(Y(^(w)UQ#YjU\$}#Q&k(nR%X#ZT#{!W#|Q![WR\$Q!]Q!kYR\$R!^Q\$R!mR%y\$TQ!lYR\$S!^Q\$R!lR%y\$SQ!oZR\$U!_Q!q[R\$V!`R!s]Q!hXQ!|fQ\$]!eQ\$f!tQ\$k!vQ\$m!wQ\$r!{Q%U#VQ%[#^Q%]#_Q%^#cQ%c#gQ'l&_Q'{&vQ(R&zQ(T'OQ(q'zQ(s(PQ)P(gQ)S(tQ)T(uR)V)OSpOqUyP!f\$_Q#jxQ%g#lR'P%fa`OPqx!f#l\$_%fQ\$f!tR(a'bR\$i!uQ'j&[R(z(bQ\${#QQ'v&kR)R(nQ&b\$pR's&iR#ZjR#]kR%Z#]S&v%Y&wR(o'yV&t%Y&w'yQ#o{R%i#oQqOR#bqQ%v\$OQ&Q\$a^'R%v&Q't(U(Y(^)UQ't&jQ(U'SQ(Y'VQ(^'^R)U(wQ'T%vU(W'T(X(xQ(X'UR(x(YQ#|!WR%s#|Q#v!SR%o#vQ'_&QR(_'_Q'[&OR(]'[Q!eXR\$[!eUxP!f\$_S#ix%fR%f#lQ&U\$dR'd&UQ&Y\$eR'g&YQ#myQ%e#jT%h#m%eQ(c'jR({(cQ%R#RR&o%RQ\$u#OS&e\$u(jR(j'sQ'r&gR(i'rQ&w%YR'|&wQ'z&vR(p'zQ&y%^R(O&yQ%a#eR&}%aR|QSoOq]wPx!f#l\$_%f`XOPqx!f#l\$_%fQ!zeQ!{fQ\$W!aQ\$X!bQ\$Y!cQ\$Z!dQ&O\$`Q&p%TR(['ZQ!SVQ!uaQ!vbQ!wcQ!xdQ#OgQ#WiQ#crQ#guQ#hvS#q!Q\$dQ#x!TQ\$e!tQ%l#sQ%m#tQ%n#ul%u\$O\$a%v&Q&j'S'V'^'t(U(Y(^(w)UQ&S\$cS&W\$e&YQ&g\$wQ&{%_Q'O%bQ'X%{Q'f&XQ(`'bQ(" +
            "h'qQ(t(QR(u(SR%x\$OR&R\$aR&P\$`QzPQ\$^!fR%}\$_X#ly#j#m%eQ#VhQ#_mQ\$h!uR&^\$iW#Rhm!u\$iQ#^lQ\$|#QQ%S#SQ&m%PQ&n%QQ'p&dR(f'oQ%O#QQ'v&kR)R(nQ#apQ\$k!vQ\$n!xQ\$q!zQ\$v#OQ%V#WQ%W#YQ%]#_Q%d#hQ&]\$hQ&f\$uQ&q%XQ'k&^Q'l&_S'm&`&bQ(k'sQ(}(eR)Q(jR&h\$wR#ft",
        nodeNames = "⚠ InterpolationEnd InterpolationContinue Unit VariableName InterpolationStart LineComment Comment IndentedMixin IndentedInclude StyleSheet RuleSet UniversalSelector TagSelector TagName NestingSelector SuffixedSelector Suffix Interpolation SassVariableName ValueName ) ( ParenthesizedValue ColorLiteral NumberLiteral StringLiteral BinaryExpression BinOp LogicOp UnaryExpression LogicOp NamespacedValue . CallExpression Callee ArgList : ... , CallLiteral CallTag ParenthesizedContent ] [ LineNames LineName ClassSelector ClassName PseudoClassSelector :: PseudoClassName PseudoClassName ArgList PseudoClassName ArgList IdSelector # IdName AttributeSelector AttributeName MatchOp ChildSelector ChildOp DescendantSelector SiblingSelector SiblingOp PlaceholderSelector ClassName Block { Declaration PropertyName Map Important Global Default ; } ImportStatement AtKeyword import Layer layer LayerName KeywordQuery FeatureQuery FeatureName BinaryQuery ComparisonQuery CompareOp UnaryQuery LogicOp ParenthesizedQuery SelectorQuery selector IncludeStatement include Keyword MixinStatement mixin UseStatement use Keyword Star Keyword ExtendStatement extend RootStatement at-root ForwardStatement forward Keyword MediaStatement media CharsetStatement charset NamespaceStatement namespace NamespaceName KeyframesStatement keyframes KeyframeName KeyframeList KeyframeSelector KeyframeRangeName SupportsStatement supports IfStatement ControlKeyword ControlKeyword Keyword ForStatement ControlKeyword Keyword Keyword Keyword EachStatement ControlKeyword Keyword WhileStatement ControlKeyword OutputStatement ControlKeyword AtRule Styles",
        maxTerm = 196,
        context = trackIndent,
        nodeProps = listOf(
            listOf("openedBy", 1, "InterpolationStart", 5, "InterpolationEnd", 21, "(", 43, "[", 78, "{"),
            listOf("isolate", -3, 6, 7, 26, ""),
            listOf("closedBy", 22, ")", 44, "]", 70, "}")
        ),
        propSources = listOf(sassHighlighting),
        skippedNodes = listOf(0, 6, 7, 146),
        repeatNodeCount = 21,
        tokenData = "!\$Q~RyOq#rqr\$jrs0jst2^tu8{uv;hvw;{wx<^xy={yz>^z{>c{|>||}Co}!ODQ!O!PDo!P!QFY!Q![Fk![!]Gf!]!^Hb!^!_Hs!_!`Is!`!aJ^!a!b#r!b!cKa!c!}#r!}#OMn#O#P#r#P#QNP#Q#RNb#R#T#r#T#UNw#U#c#r#c#d!!Y#d#o#r#o#p!!o#p#qNb#q#r!#Q#r#s!#c#s;'S#r;'S;=`!#z<%lO#rW#uSOy\$Rz;'S\$R;'S;=`\$d<%lO\$RW\$WSzWOy\$Rz;'S\$R;'S;=`\$d<%lO\$RW\$gP;=`<%l\$RY\$m[Oy\$Rz!_\$R!_!`%c!`#W\$R#W#X%v#X#Z\$R#Z#[)Z#[#]\$R#]#^,V#^;'S\$R;'S;=`\$d<%lO\$RY%jSzWlQOy\$Rz;'S\$R;'S;=`\$d<%lO\$RY%{UzWOy\$Rz#X\$R#X#Y&_#Y;'S\$R;'S;=`\$d<%lO\$RY&dUzWOy\$Rz#Y\$R#Y#Z&v#Z;'S\$R;'S;=`\$d<%lO\$RY&{UzWOy\$Rz#T\$R#T#U'_#U;'S\$R;'S;=`\$d<%lO\$RY'dUzWOy\$Rz#i\$R#i#j'v#j;'S\$R;'S;=`\$d<%lO\$RY'{UzWOy\$Rz#`\$R#`#a(_#a;'S\$R;'S;=`\$d<%lO\$RY(dUzWOy\$Rz#h\$R#h#i(v#i;'S\$R;'S;=`\$d<%lO\$RY(}S!nQzWOy\$Rz;'S\$R;'S;=`\$d<%lO\$RY)`UzWOy\$Rz#`\$R#`#a)r#a;'S\$R;'S;=`\$d<%lO\$RY)wUzWOy\$Rz#c\$R#c#d*Z#d;'S\$R;'S;=`\$d<%lO\$RY*`UzWOy\$Rz#U\$R#U#V*r#V;'S\$R;'S;=`\$d<%lO\$RY*wUzWOy\$Rz#T\$R#T#U+Z#U;'S\$R;'S;=`\$d<%lO\$RY+`UzWOy\$Rz#`\$R#`#a+r#a;'S\$R;'S;=`\$d<%lO\$RY+yS!mQzWOy\$Rz;'S\$R;'S;=`\$d<%lO\$RY,[UzWOy\$Rz#a\$R#a#b,n#b;'S\$R;'S;=`\$d<%lO\$RY,sUzWOy\$Rz#d\$R#d#e-V#e;'S\$R;'S;=`\$d<%lO\$RY-[UzWOy\$Rz#c\$R#c#d-n#d;'S\$R;'S;=`\$d<%lO\$RY-sUzWOy\$Rz#f\$R#f#g.V#g;'S\$R;'S;=`\$d<%lO\$RY.[UzWOy\$Rz#h\$R#h#i.n#i;'S\$R;'S;=`\$d<%lO\$RY.sUzWOy\$Rz#T\$R#T#U/V#U;'S\$R;'S;=`\$d<%lO\$RY/[UzWOy\$Rz#b\$R#b#c/n#c;'S\$R;'S;=`\$d<%lO\$RY/sUzWOy\$Rz#h\$R#h#i0V#i;'S\$R;'S;=`\$d<%lO\$RY0^S!lQzWOy\$Rz;'S\$R;'S;=`\$d<%lO\$R~0mWOY0jZr0jrs1Vs#O0j#O#P1[#P;'S0j;'S;=`2W<%lO0j~1[Oj~~1_RO;'S0j;'S;=`1h;=`O0j~1kXOY0jZr0jrs1Vs#O0j#O#P1[#P;'S0j;'S;=`2W;=`<%l0j<%lO0j~2ZP;=`<%l0jZ2cY!ZPOy\$Rz!Q\$R!Q![3R![!c\$R!c!i3R!i#T\$R#T#Z3R#Z;'S\$R;'S;=`\$d<%lO\$RY3WYzWOy\$Rz!Q\$R!Q![3v![!c\$R!c!i3v!i#T\$R#T#Z3v#Z;'S\$R;'S;=`\$d<%lO\$RY3{YzWOy\$Rz!Q\$R!Q![4k![!c\$R!c!i4k!i#T\$R#T#Z4k#Z;'S\$R;'S;=`\$d<%lO\$RY4rYhQzWOy\$Rz!Q\$R!Q![5b![!c\$R!c!i5b!i#T\$R#T#Z5b#Z;'S\$R;'S;=`\$d<%lO\$RY5iYhQzWOy\$Rz!Q\$R!Q![6X![!c\$R!c!i6X!i#T\$R#T#Z6X#Z;'S\$R;'S;=`\$d<%lO\$RY6^YzWOy\$Rz!Q\$R!Q![6|![!c\$R!c!i6|!i#T\$R" +
            "#T#Z6|#Z;'S\$R;'S;=`\$d<%lO\$RY7TYhQzWOy\$Rz!Q\$R!Q![7s![!c\$R!c!i7s!i#T\$R#T#Z7s#Z;'S\$R;'S;=`\$d<%lO\$RY7xYzWOy\$Rz!Q\$R!Q![8h![!c\$R!c!i8h!i#T\$R#T#Z8h#Z;'S\$R;'S;=`\$d<%lO\$RY8oShQzWOy\$Rz;'S\$R;'S;=`\$d<%lO\$R_9O`Oy\$Rz}\$R}!O:Q!O!Q\$R!Q![:Q![!_\$R!_!`;T!`!c\$R!c!}:Q!}#R\$R#R#S:Q#S#T\$R#T#o:Q#o;'S\$R;'S;=`\$d<%lO\$RZ:X^zWcROy\$Rz}\$R}!O:Q!O!Q\$R!Q![:Q![!c\$R!c!}:Q!}#R\$R#R#S:Q#S#T\$R#T#o:Q#o;'S\$R;'S;=`\$d<%lO\$R[;[S!_SzWOy\$Rz;'S\$R;'S;=`\$d<%lO\$RZ;oS%SPlQOy\$Rz;'S\$R;'S;=`\$d<%lO\$RZ<QS_ROy\$Rz;'S\$R;'S;=`\$d<%lO\$R~<aWOY<^Zw<^wx1Vx#O<^#O#P<y#P;'S<^;'S;=`=u<%lO<^~<|RO;'S<^;'S;=`=V;=`O<^~=YXOY<^Zw<^wx1Vx#O<^#O#P<y#P;'S<^;'S;=`=u;=`<%l<^<%lO<^~=xP;=`<%l<^Z>QSfROy\$Rz;'S\$R;'S;=`\$d<%lO\$R~>cOe~_>jU\$|PlQOy\$Rz!_\$R!_!`;T!`;'S\$R;'S;=`\$d<%lO\$RZ?TWlQ!dPOy\$Rz!O\$R!O!P?m!P!Q\$R!Q![Br![;'S\$R;'S;=`\$d<%lO\$RZ?rUzWOy\$Rz!Q\$R!Q![@U![;'S\$R;'S;=`\$d<%lO\$RZ@]YzW%OROy\$Rz!Q\$R!Q![@U![!g\$R!g!h@{!h#X\$R#X#Y@{#Y;'S\$R;'S;=`\$d<%lO\$RZAQYzWOy\$Rz{\$R{|Ap|}\$R}!OAp!O!Q\$R!Q![BX![;'S\$R;'S;=`\$d<%lO\$RZAuUzWOy\$Rz!Q\$R!Q![BX![;'S\$R;'S;=`\$d<%lO\$RZB`UzW%OROy\$Rz!Q\$R!Q![BX![;'S\$R;'S;=`\$d<%lO\$RZBy[zW%OROy\$Rz!O\$R!O!P@U!P!Q\$R!Q![Br![!g\$R!g!h@{!h#X\$R#X#Y@{#Y;'S\$R;'S;=`\$d<%lO\$RZCtSwROy\$Rz;'S\$R;'S;=`\$d<%lO\$RZDVWlQOy\$Rz!O\$R!O!P?m!P!Q\$R!Q![Br![;'S\$R;'S;=`\$d<%lO\$RZDtWqROy\$Rz!O\$R!O!PE^!P!Q\$R!Q![@U![;'S\$R;'S;=`\$d<%lO\$RYEcUzWOy\$Rz!O\$R!O!PEu!P;'S\$R;'S;=`\$d<%lO\$RYE|SvQzWOy\$Rz;'S\$R;'S;=`\$d<%lO\$RYF_SlQOy\$Rz;'S\$R;'S;=`\$d<%lO\$RZFp[%OROy\$Rz!O\$R!O!P@U!P!Q\$R!Q![Br![!g\$R!g!h@{!h#X\$R#X#Y@{#Y;'S\$R;'S;=`\$d<%lO\$RkGkUucOy\$Rz![\$R![!]G}!];'S\$R;'S;=`\$d<%lO\$RXHUS!SPzWOy\$Rz;'S\$R;'S;=`\$d<%lO\$RZHgS!oROy\$Rz;'S\$R;'S;=`\$d<%lO\$RjHzU!|`lQOy\$Rz!_\$R!_!`I^!`;'S\$R;'S;=`\$d<%lO\$RjIgS!|`zWlQOy\$Rz;'S\$R;'S;=`\$d<%lO\$RnIzU!|`!_SOy\$Rz!_\$R!_!`%c!`;'S\$R;'S;=`\$d<%lO\$RkJgV!aP!|`lQOy\$Rz!_\$R!_!`I^!`!aJ|!a;'S\$R;'S;=`\$d<%lO\$RXKTS!aPzWOy\$Rz;'S\$R;'S;=`\$d<%lO\$RXKdYOy\$Rz}\$R}!OLS!O!c\$R!c!}Lq!}#T\$R#T#oLq#o;'S\$R;'S;=`\$d<%lO\$RXLXWzWOy\$Rz!c\$R!c" +
            "!}Lq!}#T\$R#T#oLq#o;'S\$R;'S;=`\$d<%lO\$RXLx[!rPzWOy\$Rz}\$R}!OLq!O!Q\$R!Q![Lq![!c\$R!c!}Lq!}#T\$R#T#oLq#o;'S\$R;'S;=`\$d<%lO\$RZMsS|ROy\$Rz;'S\$R;'S;=`\$d<%lO\$R_NUS{VOy\$Rz;'S\$R;'S;=`\$d<%lO\$R[NeUOy\$Rz!_\$R!_!`;T!`;'S\$R;'S;=`\$d<%lO\$RkNzUOy\$Rz#b\$R#b#c! ^#c;'S\$R;'S;=`\$d<%lO\$Rk! cUzWOy\$Rz#W\$R#W#X! u#X;'S\$R;'S;=`\$d<%lO\$Rk! |SmczWOy\$Rz;'S\$R;'S;=`\$d<%lO\$Rk!!]UOy\$Rz#f\$R#f#g! u#g;'S\$R;'S;=`\$d<%lO\$RZ!!tS!hROy\$Rz;'S\$R;'S;=`\$d<%lO\$RZ!#VS!pROy\$Rz;'S\$R;'S;=`\$d<%lO\$R]!#hU!dPOy\$Rz!_\$R!_!`;T!`;'S\$R;'S;=`\$d<%lO\$RW!#}P;=`<%l#r",
        tokenizers = listOf(
            indentation, descendant, interpolationEnd, unitToken,
            identifiers, spaces, comments, indentedMixins,
            0, 1, 2, 3, 4
        ),
        topRules = mapOf(
            "StyleSheet" to listOf(0, 10),
            "Styles" to listOf(1, 145)
        ),
        dialects = mapOf("indented" to 0),
        specialized = listOf(
            SpecializerSpec(
                term = 172,
                get = { value, _ -> specIdentifier[value] ?: -1 }
            ),
            SpecializerSpec(
                term = 171,
                get = { value, _ -> specCallee[value] ?: -1 }
            ),
            SpecializerSpec(
                term = 80,
                get = { value, _ -> specAtKeyword[value] ?: -1 }
            ),
            SpecializerSpec(
                term = 173,
                get = { value, _ -> specQueryIdentifier[value] ?: -1 }
            )
        ),
        tokenPrec = 3217
    )
)
