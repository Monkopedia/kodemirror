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
@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:comment-wrapping",
    "ktlint:standard:discouraged-comment-location"
)

package com.monkopedia.kodemirror.lang.yaml

import com.monkopedia.kodemirror.lezer.lr.ContextTracker
import com.monkopedia.kodemirror.lezer.lr.ExternalTokenizer
import com.monkopedia.kodemirror.lezer.lr.InputStream
import com.monkopedia.kodemirror.lezer.lr.LRParser
import com.monkopedia.kodemirror.lezer.lr.ParserSpec

// Term constants from the generated grammar
private const val BLOCK_END = 63
private const val EOF = 64
private const val DIRECTIVE_END = 1
private const val DOC_END = 2
private const val SEQUENCE_START_MARK = 3
private const val SEQUENCE_CONTINUE_MARK = 4
private const val EXPLICIT_MAP_START_MARK = 5
private const val EXPLICIT_MAP_CONTINUE_MARK = 6
private const val FLOW_MAP_MARK = 7
private const val MAP_START_MARK = 65
private const val MAP_CONTINUE_MARK = 66
private const val LITERAL = 8
private const val QUOTED_LITERAL = 9
private const val ANCHOR = 10
private const val ALIAS = 11
private const val TAG = 12
private const val BLOCK_LITERAL_CONTENT = 13
private const val BRACKET_L = 19
private const val FLOW_SEQUENCE = 20
private const val COLON = 29
private const val BRACE_L = 33
private const val FLOW_MAPPING = 34
private const val BLOCK_LITERAL_HEADER = 47

// Context type constants
private const val TYPE_TOP = 0 // Top document level
private const val TYPE_SEQ = 1 // Block sequence
private const val TYPE_MAP = 2 // Block mapping
private const val TYPE_FLOW = 3 // Inside flow content
private const val TYPE_LIT = 4 // Block literal with explicit indentation

private class Context(
    val parent: Context?,
    val depth: Int,
    val type: Int
) {
    val hash: Int = (
        (if (parent != null) (parent.hash + parent.hash) shl 8 else 0) +
            depth + (depth shl 4) + type
        )

    companion object {
        val top = Context(null, -1, TYPE_TOP)
    }
}

private fun findColumn(input: InputStream, pos: Int): Int {
    var col = 0
    var p = pos - input.pos - 1
    while (true) {
        val ch = input.peek(p)
        if (isBreakSpace(ch) || ch == -1) return col
        p--
        col++
    }
}

private fun isNonBreakSpace(ch: Int): Boolean = ch == 32 || ch == 9

private fun isBreakSpace(ch: Int): Boolean = ch == 10 || ch == 13

private fun isSpace(ch: Int): Boolean = isNonBreakSpace(ch) || isBreakSpace(ch)

private fun isSep(ch: Int): Boolean = ch < 0 || isSpace(ch)

@Suppress("UNCHECKED_CAST")
private val indentation = ContextTracker(
    start = Context.top,
    reduce = { context, term, _, _ ->
        if (context.type == TYPE_FLOW && (term == FLOW_SEQUENCE || term == FLOW_MAPPING)) {
            context.parent ?: context
        } else {
            context
        }
    },
    shift = { context, term, stack, input ->
        if (term == SEQUENCE_START_MARK) {
            Context(context, findColumn(input, input.pos), TYPE_SEQ)
        } else if (term == MAP_START_MARK || term == EXPLICIT_MAP_START_MARK) {
            Context(context, findColumn(input, input.pos), TYPE_MAP)
        } else if (term == BLOCK_END) {
            context.parent ?: context
        } else if (term == BRACKET_L || term == BRACE_L) {
            Context(context, 0, TYPE_FLOW)
        } else if (term == BLOCK_LITERAL_CONTENT && context.type == TYPE_LIT) {
            context.parent ?: context
        } else if (term == BLOCK_LITERAL_HEADER) {
            val text = input.read(input.pos, stack.pos)
            val match = Regex("[1-9]").find(text)
            if (match != null) {
                Context(context, context.depth + match.value.toInt(), TYPE_LIT)
            } else {
                context
            }
        } else {
            context
        }
    },
    hash = { context -> context.hash },
    strict = false
) as ContextTracker<Any?>

private fun three(input: InputStream, ch: Int, off: Int = 0): Boolean {
    return input.peek(off) == ch && input.peek(off + 1) == ch &&
        input.peek(off + 2) == ch && isSep(input.peek(off + 3))
}

private val newlines = ExternalTokenizer(
    { input, stack ->
        if (input.next == -1 && stack.canShift(EOF)) {
            input.acceptToken(EOF)
            return@ExternalTokenizer
        }
        val prev = input.peek(-1)

        @Suppress("UNCHECKED_CAST")
        val context = stack.context as Context
        if ((isBreakSpace(prev) || prev < 0) && context.type != TYPE_FLOW) {
            if (three(input, 45 /* '-' */)) {
                if (stack.canShift(BLOCK_END)) {
                    input.acceptToken(BLOCK_END)
                } else {
                    input.acceptToken(DIRECTIVE_END, 3)
                }
                return@ExternalTokenizer
            }
            if (three(input, 46 /* '.' */)) {
                if (stack.canShift(BLOCK_END)) {
                    input.acceptToken(BLOCK_END)
                } else {
                    input.acceptToken(DOC_END, 3)
                }
                return@ExternalTokenizer
            }
            var depth = 0
            while (input.next == 32 /* ' ' */) {
                depth++
                input.advance()
            }
            if ((
                    depth < context.depth ||
                        (
                            depth == context.depth && context.type == TYPE_SEQ &&
                                (input.next != 45 /* '-' */ || !isSep(input.peek(1)))
                            )
                    ) &&
                // Not blank
                input.next != -1 && !isBreakSpace(input.next) && input.next != 35 /* '#' */
            ) {
                input.acceptToken(BLOCK_END, -depth)
            }
        }
    },
    contextual = true
)

// "Safe char" info for char codes 33 to 125. s: safe, i: indicator, f: flow indicator
private const val CHAR_TABLE = "iiisiiissisfissssssssssssisssiiissssssssssssssssssssssssssfsfssissssssssssssssssssssssssssfif"

private fun charTag(ch: Int): Char {
    if (ch < 33) return 'u'
    if (ch > 125) return 's'
    return CHAR_TABLE[ch - 33]
}

private fun isSafe(ch: Int, inFlow: Boolean): Boolean {
    val tag = charTag(ch)
    return tag != 'u' && !(inFlow && tag == 'f')
}

private fun uriChar(ch: Int): Boolean {
    return ch > 32 && ch < 127 && ch != 34 && ch != 37 && ch != 44 && ch != 60 &&
        ch != 62 && ch != 92 && ch != 94 && ch != 96 && ch != 123 && ch != 124 && ch != 125
}

private fun hexChar(ch: Int): Boolean {
    return (ch in 48..57) || (ch in 97..102) || (ch in 65..70)
}

private fun readUriChar(input: InputStream, quoted: Boolean): Boolean {
    if (input.next == 37 /* '%' */) {
        input.advance()
        if (hexChar(input.next)) input.advance()
        if (hexChar(input.next)) input.advance()
        return true
    } else if (uriChar(input.next) || (quoted && input.next == 44 /* ',' */)) {
        input.advance()
        return true
    }
    return false
}

private fun readTag(input: InputStream) {
    input.advance() // !
    if (input.next == 60 /* '<' */) {
        input.advance()
        while (true) {
            if (!readUriChar(input, true)) {
                if (input.next == 62 /* '>' */) input.advance()
                break
            }
        }
    } else {
        while (readUriChar(input, false)) { /* keep reading */ }
    }
}

private fun readAnchor(input: InputStream) {
    input.advance()
    while (!isSep(input.next) && charTag(input.next) != 'f') input.advance()
}

private fun readQuoted(input: InputStream, scan: Boolean): Boolean {
    val quote = input.next
    var lineBreak = false
    val start = input.pos
    input.advance()
    while (true) {
        val ch = input.next
        if (ch < 0) break
        input.advance()
        if (ch == quote) {
            if (ch == 39 /* "'" */) {
                if (input.next == 39) {
                    input.advance()
                } else {
                    break
                }
            } else {
                break
            }
        } else if (ch == 92 /* '\\' */ && quote == 34 /* '"' */) {
            if (input.next >= 0) input.advance()
        } else if (isBreakSpace(ch)) {
            if (scan) return false
            lineBreak = true
        } else if (scan && input.pos >= start + 1024) {
            return false
        }
    }
    return !lineBreak
}

private fun scanBrackets(input: InputStream): Boolean {
    val bracketStack = mutableListOf<Int>()
    val end = input.pos + 1024
    while (true) {
        if (input.next == 91 /* '[' */ || input.next == 123 /* '{' */) {
            bracketStack.add(input.next)
            input.advance()
        } else if (input.next == 39 /* "'" */ || input.next == 34 /* '"' */) {
            if (!readQuoted(input, true)) return false
        } else if (input.next == 93 /* ']' */ || input.next == 125 /* '}' */) {
            if (bracketStack.lastOrNull() != input.next - 2) return false
            bracketStack.removeLast()
            input.advance()
            if (bracketStack.isEmpty()) return true
        } else if (input.next < 0 || input.pos > end || isBreakSpace(input.next)) {
            return false
        } else {
            input.advance()
        }
    }
}

private fun readPlain(input: InputStream, scan: Boolean, inFlow: Boolean, indent: Int): Boolean {
    if (charTag(input.next) == 's' ||
        (
            (input.next == 63 /* '?' */ || input.next == 58 /* ':' */ || input.next == 45 /* '-' */) &&
                isSafe(input.peek(1), inFlow)
            )
    ) {
        input.advance()
    } else {
        return false
    }
    val start = input.pos
    while (true) {
        var next = input.next
        var off = 0
        var lineIndent = indent + 1
        while (isSpace(next)) {
            if (isBreakSpace(next)) {
                if (scan) return false
                lineIndent = 0
            } else {
                lineIndent++
            }
            next = input.peek(++off)
        }
        val safe = next >= 0 &&
            (
                if (next == 58 /* ':' */) {
                    isSafe(input.peek(off + 1), inFlow)
                } else if (next == 35 /* '#' */) {
                    input.peek(off - 1) != 32 /* ' ' */
                } else {
                    isSafe(next, inFlow)
                }
                )
        if (!safe || (!inFlow && lineIndent <= indent) ||
            (lineIndent == 0 && !inFlow && (three(input, 45, off) || three(input, 46, off)))
        ) {
            break
        }
        if (scan && charTag(next) == 'f') return false
        for (i in off downTo 0) input.advance()
        if (scan && input.pos > start + 1024) return false
    }
    return true
}

private val blockMark = ExternalTokenizer(
    { input, stack ->
        @Suppress("UNCHECKED_CAST")
        val context = stack.context as Context
        if (context.type == TYPE_FLOW) {
            if (input.next == 63 /* '?' */) {
                input.advance()
                if (isSep(input.next)) input.acceptToken(FLOW_MAP_MARK)
            }
            return@ExternalTokenizer
        }
        if (input.next == 45 /* '-' */) {
            input.advance()
            if (isSep(input.next)) {
                input.acceptToken(
                    if (context.type == TYPE_SEQ &&
                        context.depth == findColumn(input, input.pos - 1)
                    ) {
                        SEQUENCE_CONTINUE_MARK
                    } else {
                        SEQUENCE_START_MARK
                    }
                )
            }
        } else if (input.next == 63 /* '?' */) {
            input.advance()
            if (isSep(input.next)) {
                input.acceptToken(
                    if (context.type == TYPE_MAP &&
                        context.depth == findColumn(input, input.pos - 1)
                    ) {
                        EXPLICIT_MAP_CONTINUE_MARK
                    } else {
                        EXPLICIT_MAP_START_MARK
                    }
                )
            }
        } else {
            val start = input.pos
            // Scan over a potential key to see if it is followed by a colon.
            loop@ while (true) {
                if (isNonBreakSpace(input.next)) {
                    if (input.pos == start) return@ExternalTokenizer
                    input.advance()
                } else if (input.next == 33 /* '!' */) {
                    readTag(input)
                } else if (input.next == 38 /* '&' */) {
                    readAnchor(input)
                } else if (input.next == 42 /* '*' */) {
                    readAnchor(input)
                    break@loop
                } else if (input.next == 39 /* '\'' */ || input.next == 34 /* '"' */) {
                    if (readQuoted(input, true)) break@loop
                    return@ExternalTokenizer
                } else if (input.next == 91 /* '[' */ || input.next == 123 /* '{' */) {
                    if (!scanBrackets(input)) return@ExternalTokenizer
                    break@loop
                } else {
                    readPlain(input, true, false, 0)
                    break@loop
                }
            }
            while (isNonBreakSpace(input.next)) input.advance()
            if (input.next == 58 /* ':' */) {
                if (input.pos == start && stack.canShift(COLON)) return@ExternalTokenizer
                val after = input.peek(1)
                if (isSep(after)) {
                    input.acceptTokenTo(
                        if (context.type == TYPE_MAP &&
                            context.depth == findColumn(input, start)
                        ) {
                            MAP_CONTINUE_MARK
                        } else {
                            MAP_START_MARK
                        },
                        start
                    )
                }
            }
        }
    },
    contextual = true
)

private val literals = ExternalTokenizer({ input, stack ->
    if (input.next == 33 /* '!' */) {
        readTag(input)
        input.acceptToken(TAG)
    } else if (input.next == 38 /* '&' */ || input.next == 42 /* '*' */) {
        val token = if (input.next == 38) ANCHOR else ALIAS
        readAnchor(input)
        input.acceptToken(token)
    } else if (input.next == 39 /* '\'' */ || input.next == 34 /* '"' */) {
        readQuoted(input, false)
        input.acceptToken(QUOTED_LITERAL)
    } else {
        @Suppress("UNCHECKED_CAST")
        val context = stack.context as Context
        if (readPlain(input, false, context.type == TYPE_FLOW, context.depth)) {
            input.acceptToken(LITERAL)
        }
    }
})

private val blockLiteral = ExternalTokenizer({ input, stack ->
    @Suppress("UNCHECKED_CAST")
    val context = stack.context as Context
    var indent = if (context.type == TYPE_LIT) context.depth else -1
    var upto = input.pos
    scan@ while (true) {
        var depth = 0
        var next = input.next
        while (next == 32 /* ' ' */) {
            next = input.peek(++depth)
        }
        if (depth == 0 && (three(input, 45, depth) || three(input, 46, depth))) break
        if (!isBreakSpace(next)) {
            if (indent < 0) indent = maxOf(context.depth + 1, depth)
            if (depth < indent) break
        }
        while (true) {
            if (input.next < 0) break@scan
            val isBreak = isBreakSpace(input.next)
            input.advance()
            if (isBreak) continue@scan
            upto = input.pos
        }
    }
    input.acceptTokenTo(BLOCK_LITERAL_CONTENT, upto)
})

val parser: LRParser = LRParser.deserialize(
    ParserSpec(
        version = 14,
        states = "5lQ!ZQgOOO#PQfO'#CpO#uQfO'#DOOOQR'#Dv'#DvO\$qQgO'#DRO%gQdO'#DUO%nQgO'#DUO&ROaO'#D[OOQR'#Du'#DuO&{QgO'#D^O'rQgO'#D`OOQR'#Dt'#DtO(iOqO'#DbOOQP'#Dj'#DjO(zQaO'#CmO)YQgO'#CmOOQP'#Cm'#CmQ)jQaOOQ)uQgOOQ]QgOOO*PQdO'#CrO*nQdO'#CtOOQO'#Dw'#DwO+]Q`O'#CxO+hQdO'#CwO+rQ`O'#CwOOQO'#Cv'#CvO+wQdO'#CvOOQO'#Cq'#CqO,UQ`O,59[O,^QfO,59[OOQR,59[,59[OOQO'#Cx'#CxO,eQ`O'#DPO,pQdO'#DPOOQO'#Dx'#DxO,zQdO'#DxO-XQ`O,59jO-aQfO,59jOOQR,59j,59jOOQR'#DS'#DSO-hQcO,59mO-sQgO'#DVO.TQ`O'#DVO.YQcO,59pOOQR'#DX'#DXO#|QfO'#DWO.hQcO'#DWOOQR,59v,59vO.yOWO,59vO/OOaO,59vO/WOaO,59vO/cQgO'#D_OOQR,59x,59xO0VQgO'#DaOOQR,59z,59zOOQP,59|,59|O0yOaO,59|O1ROaO,59|O1aOqO,59|OOQP-E7h-E7hO1oQgO,59XOOQP,59X,59XO2PQaO'#DeO2_QgO'#DeO2oQgO'#DkOOQP'#Dk'#DkQ)jQaOOO3PQdO'#CsOOQO,59^,59^O3kQdO'#CuOOQO,59`,59`OOQO,59c,59cO4VQdO,59cO4aQdO'#CzO4kQ`O'#CzOOQO,59b,59bOOQU,5:Q,5:QOOQR1G.v1G.vO4pQ`O1G.vOOQU-E7d-E7dO4xQdO,59kOOQO,59k,59kO5SQdO'#DQO5^Q`O'#DQOOQO,5:d,5:dOOQU,5:R,5:ROOQR1G/U1G/UO5cQ`O1G/UOOQU-E7e-E7eO5kQgO'#DhO5xQcO1G/XOOQR1G/X1G/XOOQR,59q,59qO6TQgO,59qO6eQdO'#DiO6lQgO'#DiO7PQcO1G/[OOQR1G/[1G/[OOQR,59r,59rO#|QfO,59rOOQR1G/b1G/bO7_OWO1G/bO7dOaO1G/bOOQR,59y,59yOOQR,59{,59{OOQP1G/h1G/hO7lOaO1G/hO7tOaO1G/hO8POaO1G/hOOQP1G.s1G.sO8_QgO,5:POOQP,5:P,5:POOQP,5:V,5:VOOQP-E7i-E7iOOQO,59_,59_OOQO,59a,59aOOQO1G.}1G.}OOQO,59f,59fO8oQdO,59fOOQR7+\$b7+\$bP,XQ`O'#DfOOQO1G/V1G/VOOQO,59l,59lO8yQdO,59lOOQR7+\$p7+\$pP9TQ`O'#DgOOQR'#DT'#DTOOQR,5:S,5:SOOQR-E7f-E7fOOQR7+\$s7+\$sOOQR1G/]1G/]O9YQgO'#DYO9jQ`O'#DYOOQR,5:T,5:TO#|QfO'#DZO9oQcO'#DZOOQR-E7g-E7gOOQR7+\$v7+\$vOOQR1G/^1G/^OOQR7+\$|7+\$|O:QOWO7+\$|OOQP7+%S7+%SO:VOaO7+%SO:_OaO7+%SOOQP1G/k1G/kOOQO1G/Q1G/QOOQO1G/W1G/WOOQR,59t,59tO:jQgO,59tOOQR,59u,59uO#|QfO,59uOOQR<<Hh<<HhOOQP<<Hn<<HnO:zOaO<<HnOOQR1G/`1G/`OOQR1G/a1G/aOOQPAN>YAN>Y",
        stateData = ";S~O!fOS!gOS^OS~OP_OQbORSOTUOWROXROYYOZZO[XOcPOqQO!PVO!V[O!cTO~O`cO~P]OVkOWROXROYeOZfO[dOcPOmhOqQO~OboO~P!bOVtOWROXROYeOZfO[dOcPOmrOqQO~OpwO~P#WORSOTUOWROXROYYOZZO[XOcPOqQO!PVO!cTO~OSvP!avP!bvP~P#|OWROXROYeOZfO[dOcPOqQO~OmzO~P%OOm!OOUzP!azP!bzP!dzP~P#|O^!SO!b!QO!f!TO!g!RO~ORSOTUOWROXROcPOqQO!PVO!cTO~OY!UOP!QXQ!QX!V!QX!`!QXS!QX!a!QX!b!QXU!QXm!QX!d!QX~P&aO[!WOP!SXQ!SX!V!SX!`!SXS!SX!a!SX!b!SXU!SXm!SX!d!SX~P&aO^!ZO!W![O!b!YO!f!]O!g!YO~OP!_O!V[OQaX!`aX~OPaXQaX!VaX!`aX~P#|OP!bOQ!cO!V[O~OP_O!V[O~P#|OWROXROY!fOcPOqQObfXmfXofXpfX~OWROXRO[!hOcPOqQObhXmhXohXphX~ObeXmlXoeX~ObkXokX~P%OOm!kO~Om!lObnPonP~P%OOb!pOo!oO~Ob!pO~P!bOm!sOosXpsX~OosXpsX~P%OOm!uOotPptP~P%OOo!xOp!yO~Op!yO~P#WOS!|O!a#OO!b#OO~OUyX!ayX!byX!dyX~P#|Om#QO~OU#SO!a#UO!b#UO!d#RO~Om#WOUzX!azX!bzX!dzX~O]#XO~O!b#XO!g#YO~O^#ZO!b#XO!g#YO~OP!RXQ!RX!V!RX!`!RXS!RX!a!RX!b!RXU!RXm!RX!d!RX~P&aOP!TXQ!TX!V!TX!`!TXS!TX!a!TX!b!TXU!TXm!TX!d!TX~P&aO!b#^O!g#^O~O^#_O!b#^O!f#`O!g#^O~O^#_O!W#aO!b#^O!g#^O~OPaaQaa!Vaa!`aa~P#|OP#cO!V[OQ!XX!`!XX~OP!XXQ!XX!V!XX!`!XX~P#|OP_O!V[OQ!_X!`!_X~P#|OWROXROcPOqQObgXmgXogXpgX~OWROXROcPOqQObiXmiXoiXpiX~Obkaoka~P%OObnXonX~P%OOm#kO~Ob#lOo!oO~Oosapsa~P%OOotXptX~P%OOm#pO~Oo!xOp#qO~OSwP!awP!bwP~P#|OS!|O!a#vO!b#vO~OUya!aya!bya!dya~P#|Om#xO~P%OOm#{OU}P!a}P!b}P!d}P~P#|OU#SO!a\$OO!b\$OO!d#RO~O]\$QO~O!b\$QO!g\$RO~O!b\$SO!g\$SO~O^\$TO!b\$SO!g\$SO~O^\$TO!b\$SO!f\$UO!g\$SO~OP!XaQ!Xa!V!Xa!`!Xa~P#|Obnaona~P%OOotapta~P%OOo!xO~OU|X!a|X!b|X!d|X~P#|Om\$ZO~Om\$]OU}X!a}X!b}X!d}X~O]\$^O~O!b\$_O!g\$_O~O^\$`O!b\$_O!g\$_O~OU|a!a|a!b|a!d|a~P#|O!b\$cO!g\$cO~O",
        goto = ",]!mPPPPPPPPPPPPPPPPP!nPP!v#v#|\$`#|\$c\$f\$j\$nP%VPPP!v%Y%^%a%{&O%a&R&U&X&_&b%aP&e&{&e'O'RPP']'a'g'm's'y(XPPPPPPPP(_)e*X+c,VUaObcR#e!c!{ROPQSTUXY_bcdehknrtvz!O!U!W!_!b!c!f!h!k!l!s!u!|#Q#R#S#W#c#k#p#x#{\$Z\$]QmPR!qnqfPQThknrtv!k!l!s!u#R#k#pR!gdR!ieTlPnTjPnSiPnSqQvQ{TQ!mkQ!trQ!vtR#y#RR!nkTsQvR!wt!RWOSUXY_bcz!O!U!W!_!b!c!|#Q#S#W#c#x#{\$Z\$]RySR#t!|R|TR|UQ!PUR#|#SR#z#RR#z#SyZOSU_bcz!O!_!b!c!|#Q#S#W#c#x#{\$Z\$]R!VXR!XYa]O^abc!a!c!eT!da!eQnPR!rnQvQR!{vQ!}yR#u!}Q#T|R#}#TW^Obc!cS!^^!aT!aa!eQ!eaR#f!eW`Obc!cQxSS}U#SQ!`_Q#PzQ#V!OQ#b!_Q#d!bQ#s!|Q#w#QQ\$P#WQ\$V#cQ\$Y#xQ\$[#{Q\$a\$ZR\$b\$]xZOSU_bcz!O!_!b!c!|#Q#S#W#c#x#{\$Z\$]Q!VXQ!XYQ#[!UR#]!W!QWOSUXY_bcz!O!U!W!_!b!c!|#Q#S#W#c#x#{\$Z\$]pfPQThknrtv!k!l!s!u#R#k#pQ!gdQ!ieQ#g!fR#h!hSgPn^pQTkrtv#RQ!jhQ#i!kQ#j!lQ#n!sQ#o!uQ\$W#kR\$X#pQuQR!zv",
        nodeNames = "⚠ DirectiveEnd DocEnd - - ? ? ? Literal QuotedLiteral Anchor Alias Tag BlockLiteralContent Comment Stream BOM Document ] [ FlowSequence Item Tagged Anchored Anchored Tagged FlowMapping Pair Key : Pair , } { FlowMapping Pair Pair BlockSequence Item Item BlockMapping Pair Pair Key Pair Pair BlockLiteral BlockLiteralHeader Tagged Anchored Anchored Tagged Directive DirectiveName DirectiveContent Document",
        maxTerm = 74,
        context = indentation,
        nodeProps = listOf(
            listOf("isolate", -3, 8, 9, 14, ""),
            listOf("openedBy", 18, "[", 32, "{"),
            listOf("closedBy", 19, "]", 33, "}")
        ),
        propSources = listOf(yamlHighlighting),
        skippedNodes = listOf(0),
        repeatNodeCount = 6,
        tokenData = "-Y~RnOX#PXY\$QYZ\$]Z]#P]^\$]^p#Ppq\$Qqs#Pst\$btu#Puv\$yv|#P|}&e}![#P![!]'O!]!`#P!`!a'i!a!}#P!}#O*g#O#P#P#P#Q+Q#Q#o#P#o#p+k#p#q'i#q#r,U#r;'S#P;'S;=`#z<%l?HT#P?HT?HU,o?HUO#PQ#UU!WQOY#PZp#Ppq#hq;'S#P;'S;=`#z<%lO#PQ#kTOY#PZs#Pt;'S#P;'S;=`#z<%lO#PQ#}P;=`<%l#P~\$VQ!f~XY\$Qpq\$Q~\$bO!g~~\$gS^~OY\$bZ;'S\$b;'S;=`\$s<%lO\$b~\$vP;=`<%l\$bR%OX!WQOX%kXY#PZ]%k]^#P^p%kpq#hq;'S%k;'S;=`&_<%lO%kR%rX!WQ!VPOX%kXY#PZ]%k]^#P^p%kpq#hq;'S%k;'S;=`&_<%lO%kR&bP;=`<%l%kR&lUoP!WQOY#PZp#Ppq#hq;'S#P;'S;=`#z<%lO#PR'VUmP!WQOY#PZp#Ppq#hq;'S#P;'S;=`#z<%lO#PR'p[!PP!WQOY#PZp#Ppq#hq{#P{|(f|}#P}!O(f!O!R#P!R![)p![;'S#P;'S;=`#z<%lO#PR(mW!PP!WQOY#PZp#Ppq#hq!R#P!R![)V![;'S#P;'S;=`#z<%lO#PR)^U!PP!WQOY#PZp#Ppq#hq;'S#P;'S;=`#z<%lO#PR)wY!PP!WQOY#PZp#Ppq#hq{#P{|)V|}#P}!O)V!O;'S#P;'S;=`#z<%lO#PR*nUcP!WQOY#PZp#Ppq#hq;'S#P;'S;=`#z<%lO#PR+XUbP!WQOY#PZp#Ppq#hq;'S#P;'S;=`#z<%lO#PR+rUqP!WQOY#PZp#Ppq#hq;'S#P;'S;=`#z<%lO#PR,]UpP!WQOY#PZp#Ppq#hq;'S#P;'S;=`#z<%lO#PR,vU`P!WQOY#PZp#Ppq#hq;'S#P;'S;=`#z<%lO#P",
        tokenizers = listOf(newlines, blockMark, literals, blockLiteral, 0, 1),
        topRules = mapOf("Stream" to listOf(0, 15)),
        tokenPrec = 0
    )
)
