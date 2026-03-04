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

package com.monkopedia.kodemirror.lang.xml

import com.monkopedia.kodemirror.lezer.lr.ContextTracker
import com.monkopedia.kodemirror.lezer.lr.ExternalTokenizer
import com.monkopedia.kodemirror.lezer.lr.InputStream
import com.monkopedia.kodemirror.lezer.lr.LRParser
import com.monkopedia.kodemirror.lezer.lr.ParserSpec

// Term constants
private const val START_TAG = 1
private const val START_CLOSE_TAG = 2
private const val MISSING_CLOSE_TAG = 3
private const val MISMATCHED_START_CLOSE_TAG = 4
private const val INCOMPLETE_START_CLOSE_TAG = 5
private const val COMMENT_CONTENT = 36
private const val PI_CONTENT = 37
private const val CDATA_CONTENT = 38
private const val ELEMENT = 11
private const val OPEN_TAG = 13

// Character constants
private const val LT = 60
private const val SLASH = 47
private const val BANG = 33
private const val QUESTION = 63

private fun nameChar(ch: Int): Boolean = ch == 45 || ch == 46 || ch == 58 ||
    ch in 65..90 || ch == 95 || ch in 97..122 || ch >= 161

private fun isSpace(ch: Int): Boolean = ch == 9 || ch == 10 || ch == 13 || ch == 32

private fun tagNameAfter(input: InputStream, offset: Int): String? {
    var off = offset
    while (isSpace(input.peek(off))) off++
    val sb = StringBuilder()
    while (true) {
        val next = input.peek(off)
        if (!nameChar(next)) break
        sb.append(next.toChar())
        off++
    }
    return sb.toString().ifEmpty { null }
}

private class ElementContext(val name: String, val parent: ElementContext?)

@Suppress("UNCHECKED_CAST")
private val elementContext = ContextTracker(
    start = null as ElementContext?,
    shift = { context, term, _, input ->
        if (term == START_TAG) {
            ElementContext(tagNameAfter(input, 1) ?: "", context)
        } else {
            context
        }
    },
    reduce = { context, term, _, _ ->
        if (term == ELEMENT && context != null) context.parent else context
    },
    reuse = { context, node, _, input ->
        val type = node.type.id
        if (type == START_TAG || type == OPEN_TAG) {
            ElementContext(tagNameAfter(input, 1) ?: "", context)
        } else {
            context
        }
    },
    strict = false
) as ContextTracker<Any?>

private val startTag = ExternalTokenizer(
    { input, stack ->
        if (input.next == LT) {
            input.advance()
            if (input.next == SLASH) {
                input.advance()
                val name = tagNameAfter(input, 0)
                if (name == null) {
                    input.acceptToken(INCOMPLETE_START_CLOSE_TAG)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val ctx = stack.context as? ElementContext
                    if (ctx != null && name == ctx.name) {
                        input.acceptToken(START_CLOSE_TAG)
                    } else {
                        var cx = ctx
                        while (cx != null) {
                            if (cx.name == name) {
                                input.acceptToken(MISSING_CLOSE_TAG, -2)
                                return@ExternalTokenizer
                            }
                            cx = cx.parent
                        }
                        input.acceptToken(MISMATCHED_START_CLOSE_TAG)
                    }
                }
            } else if (input.next != BANG && input.next != QUESTION) {
                input.acceptToken(START_TAG)
            }
        }
    },
    contextual = true
)

private fun scanTo(type: Int, end: String): ExternalTokenizer = ExternalTokenizer({ input, _ ->
    var len = 0
    val first = end[0].code
    while (true) {
        if (input.next < 0) break
        if (input.next == first) {
            var match = true
            for (i in 1 until end.length) {
                if (input.peek(i) != end[i].code) {
                    match = false
                    break
                }
            }
            if (match) break
        }
        input.advance()
        len++
    }
    if (len > 0) input.acceptToken(type)
})

private val commentContent = scanTo(COMMENT_CONTENT, "-->")
private val piContent = scanTo(PI_CONTENT, "?>")
private val cdataContent = scanTo(CDATA_CONTENT, "]]>")

val parser: LRParser = LRParser.deserialize(
    ParserSpec(
        version = 14,
        states = ",lOQOaOOOrOxO'#CfOzOpO'#CiO!tOaO'#CgOOOP'#Cg'#CgO!{OrO'#CrO#TOtO'#CsO#]OpO'#CtOOOP'#DT'#DTOOOP'#Cv'#CvQQOaOOOOOW'#Cw'#CwO#eOxO,59QOOOP,59Q,59QOOOO'#Cx'#CxO#mOpO,59TO#uO!bO,59TOOOP'#C|'#C|O\$TOaO,59RO\$[OpO'#CoOOOP,59R,59ROOOQ'#C}'#C}O\$dOrO,59^OOOP,59^,59^OOOS'#DO'#DOO\$lOtO,59_OOOP,59_,59_O\$tOpO,59`O\$|OpO,59`OOOP-E6t-E6tOOOW-E6u-E6uOOOP1G.l1G.lOOOO-E6v-E6vO%UO!bO1G.oO%UO!bO1G.oO%dOpO'#CkO%lO!bO'#CyO%zO!bO1G.oOOOP1G.o1G.oOOOP1G.w1G.wOOOP-E6z-E6zOOOP1G.m1G.mO&VOpO,59ZO&_OpO,59ZOOOQ-E6{-E6{OOOP1G.x1G.xOOOS-E6|-E6|OOOP1G.y1G.yO&gOpO1G.zO&gOpO1G.zOOOP1G.z1G.zO&oO!bO7+\$ZO&}O!bO7+\$ZOOOP7+\$Z7+\$ZOOOP7+\$c7+\$cO'YOpO,59VO'bOpO,59VO'mO!bO,59eOOOO-E6w-E6wO'{OpO1G.uO'{OpO1G.uOOOP1G.u1G.uO(TOpO7+\$fOOOP7+\$f7+\$fO(]O!bO<<GuOOOP<<Gu<<GuOOOP<<G}<<G}O'bOpO1G.qO'bOpO1G.qO(hO#tO'#CnO(vO&jO'#CnOOOO1G.q1G.qO)UOpO7+\$aOOOP7+\$a7+\$aOOOP<<HQ<<HQOOOPAN=aAN=aOOOPAN=iAN=iO'bOpO7+\$]OOOO7+\$]7+\$]OOOO'#Cz'#CzO)^O#tO,59YOOOO,59Y,59YOOOO'#C{'#C{O)lO&jO,59YOOOP<<G{<<G{OOOO<<Gw<<GwOOOO-E6x-E6xOOOO1G.t1G.tOOOO-E6y-E6y",
        stateData = ")z~OPQOSVOTWOVWOWWOXWOiXOyPO!QTO!SUO~OvZOx]O~O^`Oz^O~OPQOQcOSVOTWOVWOWWOXWOyPO!QTO!SUO~ORdO~P!SOteO!PgO~OuhO!RjO~O^lOz^O~OvZOxoO~O^qOz^O~O[vO`sOdwOz^O~ORyO~P!SO^{Oz^O~OteO!P}O~OuhO!R!PO~O^!QOz^O~O[!SOz^O~O[!VO`sOd!WOz^O~Oa!YOz^O~Oz^O[mX`mXdmX~O[!VO`sOd!WO~O^!]Oz^O~O[!_Oz^O~O[!aOz^O~O[!cO`sOd!dOz^O~O[!cO`sOd!dO~Oa!eOz^O~Oz^O{!gO}!hO~Oz^O[ma`madma~O[!kOz^O~O[!lOz^O~O[!mO`sOd!nO~OW!qOX!qO{!sO|!qO~OW!tOX!tO}!sO!O!tO~O[!vOz^O~OW!qOX!qO{!yO|!qO~OW!tOX!tO}!yO!O!tO~O",
        goto = "%cxPPPPPPPPPPyyP!PP!VPP!`!jP!pyyyP!v!|#S\$[\$k\$q\$w\$}%TPPPP%ZXWORYbXRORYb_t`qru!T!U!bQ!i!YS!p!e!fR!w!oQdRRybXSORYbQYORmYQ[PRn[Q_QQkVjp_krz!R!T!X!Z!^!`!f!j!oQr`QzcQ!RlQ!TqQ!XsQ!ZtQ!^{Q!`!QQ!f!YQ!j!]R!o!eQu`S!UqrU![u!U!bR!b!TQ!r!gR!x!rQ!u!hR!z!uQbRRxbQfTR|fQiUR!OiSXOYTaRb",
        nodeNames = "\u26A0 StartTag StartCloseTag MissingCloseTag StartCloseTag " +
            "StartCloseTag Document Text EntityReference CharacterReference Cdata " +
            "Element EndTag OpenTag TagName Attribute AttributeName Is " +
            "AttributeValue CloseTag SelfCloseEndTag SelfClosingTag Comment " +
            "ProcessingInst MismatchedCloseTag DoctypeDecl",
        maxTerm = 50,
        context = elementContext,
        nodeProps = listOf(
            listOf(
                "closedBy",
                1,
                "SelfCloseEndTag EndTag",
                13,
                "CloseTag MissingCloseTag"
            ),
            listOf(
                "openedBy",
                12,
                "StartTag StartCloseTag",
                19,
                "OpenTag",
                20,
                "StartTag"
            ),
            listOf("isolate", -6, 13, 18, 19, 21, 22, 24, "")
        ),
        propSources = listOf(xmlHighlighting),
        skippedNodes = listOf(0),
        repeatNodeCount = 9,
        tokenData = "!)v~R!YOX\$qXY)iYZ)iZ]\$q]^)i^p\$qpq)iqr\$qrs*vsv\$qvw+fwx/ix}\$q}!O0[!O!P\$q!P!Q2z!Q![\$q![!]4n!]!^\$q!^!_8U!_!`!#t!`!a!\$l!a!b!%d!b!c\$q!c!}4n!}#P\$q#P#Q!'W#Q#R\$q#R#S4n#S#T\$q#T#o4n#o%W\$q%W%o4n%o%p\$q%p&a4n&a&b\$q&b1p4n1p4U\$q4U4d4n4d4e\$q4e\$IS4n\$IS\$I`\$q\$I`\$Ib4n\$Ib\$Kh\$q\$Kh%#t4n%#t&/x\$q&/x&Et4n&Et&FV\$q&FV;'S4n;'S;:j8O;:j;=`)c<%l?&r\$q?&r?Ah4n?Ah?BY\$q?BY?Mn4n?MnO\$qi\$zXVP|W!O`Or\$qrs%gsv\$qwx'^x!^\$q!^!_(o!_;'S\$q;'S;=`)c<%lO\$qa%nVVP!O`Ov%gwx&Tx!^%g!^!_&o!_;'S%g;'S;=`'W<%lO%gP&YTVPOv&Tw!^&T!_;'S&T;'S;=`&i<%lO&TP&lP;=`<%l&T`&tS!O`Ov&ox;'S&o;'S;=`'Q<%lO&o`'TP;=`<%l&oa'ZP;=`<%l%gX'eWVP|WOr'^rs&Tsv'^w!^'^!^!_'}!_;'S'^;'S;=`(i<%lO'^W(ST|WOr'}sv'}w;'S'};'S;=`(c<%lO'}W(fP;=`<%l'}X(lP;=`<%l'^h(vV|W!O`Or(ors&osv(owx'}x;'S(o;'S;=`)]<%lO(oh)`P;=`<%l(oi)fP;=`<%l\$qo)t`VP|W!O`zUOX\$qXY)iYZ)iZ]\$q]^)i^p\$qpq)iqr\$qrs%gsv\$qwx'^x!^\$q!^!_(o!_;'S\$q;'S;=`)c<%lO\$qk+PV{YVP!O`Ov%gwx&Tx!^%g!^!_&o!_;'S%g;'S;=`'W<%lO%g~+iast,n![!]-r!c!}-r#R#S-r#T#o-r%W%o-r%p&a-r&b1p-r4U4d-r4e\$IS-r\$I`\$Ib-r\$Kh%#t-r&/x&Et-r&FV;'S-r;'S;:j/c?&r?Ah-r?BY?Mn-r~,qQ!Q![,w#l#m-V~,zQ!Q![,w!]!^-Q~-VOX~~-YR!Q![-c!c!i-c#T#Z-c~-fS!Q![-c!]!^-Q!c!i-c#T#Z-c~-ug}!O-r!O!P-r!Q![-r![!]-r!]!^/^!c!}-r#R#S-r#T#o-r\$}%O-r%W%o-r%p&a-r&b1p-r1p4U-r4U4d-r4e\$IS-r\$I`\$Ib-r\$Je\$Jg-r\$Kh%#t-r&/x&Et-r&FV;'S-r;'S;:j/c?&r?Ah-r?BY?Mn-r~/cOW~~/fP;=`<%l-rk/rW}bVP|WOr'^rs&Tsv'^w!^'^!^!_'}!_;'S'^;'S;=`(i<%lO'^k0eZVP|W!O`Or\$qrs%gsv\$qwx'^x}\$q}!O1W!O!^\$q!^!_(o!_;'S\$q;'S;=`)c<%lO\$qk1aZVP|W!O`Or\$qrs%gsv\$qwx'^x!^\$q!^!_(o!_!`\$q!`!a2S!a;'S\$q;'S;=`)c<%lO\$qk2_X!PQVP|W!O`Or\$qrs%gsv\$qwx'^x!^\$q!^!_(o!_;'S\$q;'S;=`)c<%lO\$qm3TZVP|W!O`Or\$qrs%gsv\$qwx'^x!^\$q!^!_(o!_!`\$q!`!a3v!a;'S\$q;'S;=`)c<%lO\$qm4RXdSVP|W!O`Or\$qrs%gsv\$qwx'^x!^\$q!^!_(o!_;'S\$q;'S;=`)c<%lO\$qo4{!P`S^QVP|W!O`Or\$qrs%gsv\$qwx'^x}\$q}!O4n!O!P4n!P!Q\$q!Q![4n![!]4n!]!^\$q!^!_(o!_!c\$q!c!}4n!}#R\$q#R#S4n#S#T\$q#T#o4n#o\$}\$q\$}%O4n%O%W\$q%W%o4n%o%p\$q%p&a4n&a&b\$q&b1p4n1p4U4n4U4d4n4d4e\$q4e\$IS4n\$IS\$I`\$q\$I`\$Ib4n\$Ib\$Je\$q\$Je\$Jg4n\$Jg\$Kh\$q\$Kh%#t4n%#t&/x\$q&/x&Et4n&Et&FV\$q&FV;'S4n;'S;:j8O;:j;=`)c<%l?&r\$q?&r?Ah4n?Ah?BY\$q?BY?Mn4n?MnO\$qo8RP;=`<%l4ni8]Y|W!O`Oq(oqr8{rs&osv(owx'}x!a(o!a!b!#U!b;'S(o;'S;=`)]<%lO(oi9S_|W!O`Or(ors&osv(owx'}x}(o}!O:R!O!f(o!f!g;e!g!}(o!}#ODh#O#W(o#W#XLp#X;'S(o;'S;=`)]<%lO(oi:YX|W!O`Or(ors&osv(owx'}x}(o}!O:u!O;'S(o;'S;=`)]<%lO(oi;OV!QP|W!O`Or(ors&osv(owx'}x;'S(o;'S;=`)]<%lO(oi;lX|W!O`Or(ors&osv(owx'}x!q(o!q!r<X!r;'S(o;'S;=`)]<%lO(oi<`X|W!O`Or(ors&osv(owx'}x!e(o!e!f<{!f;'S(o;'S;=`)]<%lO(oi=SX|W!O`Or(ors&osv(owx'}x!v(o!v!w=o!w;'S(o;'S;=`)]<%lO(oi=vX|W!O`Or(ors&osv(owx'}x!{(o!{!|>c!|;'S(o;'S;=`)]<%lO(oi>jX|W!O`Or(ors&osv(owx'}x!r(o!r!s?V!s;'S(o;'S;=`)]<%lO(oi?^X|W!O`Or(ors&osv(owx'}x!g(o!g!h?y!h;'S(o;'S;=`)]<%lO(oi@QY|W!O`Or?yrs@psv?yvwA[wxBdx!`?y!`!aCr!a;'S?y;'S;=`Db<%lO?ya@uV!O`Ov@pvxA[x!`@p!`!aAy!a;'S@p;'S;=`B^<%lO@pPA_TO!`A[!`!aAn!a;'SA[;'S;=`As<%lOA[PAsOiPPAvP;=`<%lA[aBQSiP!O`Ov&ox;'S&o;'S;=`'Q<%lO&oaBaP;=`<%l@pXBiX|WOrBdrsA[svBdvwA[w!`Bd!`!aCU!a;'SBd;'S;=`Cl<%lOBdXC]TiP|WOr'}sv'}w;'S'};'S;=`(c<%lO'}XCoP;=`<%lBdiC{ViP|W!O`Or(ors&osv(owx'}x;'S(o;'S;=`)]<%lO(oiDeP;=`<%l?yiDoZ|W!O`Or(ors&osv(owx'}x!e(o!e!fEb!f#V(o#V#WIr#W;'S(o;'S;=`)]<%lO(oiEiX|W!O`Or(ors&osv(owx'}x!f(o!f!gFU!g;'S(o;'S;=`)]<%lO(oiF]X|W!O`Or(ors&osv(owx'}x!c(o!c!dFx!d;'S(o;'S;=`)]<%lO(oiGPX|W!O`Or(ors&osv(owx'}x!v(o!v!wGl!w;'S(o;'S;=`)]<%lO(oiGsX|W!O`Or(ors&osv(owx'}x!c(o!c!dH`!d;'S(o;'S;=`)]<%lO(oiHgX|W!O`Or(ors&osv(owx'}x!}(o!}#OIS#O;'S(o;'S;=`)]<%lO(oiI]V|W!O`yPOr(ors&osv(owx'}x;'S(o;'S;=`)]<%lO(oiIyX|W!O`Or(ors&osv(owx'}x#W(o#W#XJf#X;'S(o;'S;=`)]<%lO(oiJmX|W!O`Or(ors&osv(owx'}x#T(o#T#UKY#U;'S(o;'S;=`)]<%lO(oiKaX|W!O`Or(ors&osv(owx'}x#h(o#h#iK|#i;'S(o;'S;=`)]<%lO(oiLTX|W!O`Or(ors&osv(owx'}x#T(o#T#UH`#U;'S(o;'S;=`)]<%lO(oiLwX|W!O`Or(ors&osv(owx'}x#c(o#c#dMd#d;'S(o;'S;=`)]<%lO(oiMkX|W!O`Or(ors&osv(owx'}x#V(o#V#WNW#W;'S(o;'S;=`)]<%lO(oiN_X|W!O`Or(ors&osv(owx'}x#h(o#h#iNz#i;'S(o;'S;=`)]<%lO(oi! RX|W!O`Or(ors&osv(owx'}x#m(o#m#n! n#n;'S(o;'S;=`)]<%lO(oi! uX|W!O`Or(ors&osv(owx'}x#d(o#d#e!!b#e;'S(o;'S;=`)]<%lO(oi!!iX|W!O`Or(ors&osv(owx'}x#X(o#X#Y?y#Y;'S(o;'S;=`)]<%lO(oi!#_V!SP|W!O`Or(ors&osv(owx'}x;'S(o;'S;=`)]<%lO(ok!\$PXaQVP|W!O`Or\$qrs%gsv\$qwx'^x!^\$q!^!_(o!_;'S\$q;'S;=`)c<%lO\$qo!\$wX[UVP|W!O`Or\$qrs%gsv\$qwx'^x!^\$q!^!_(o!_;'S\$q;'S;=`)c<%lO\$qk!%mZVP|W!O`Or\$qrs%gsv\$qwx'^x!^\$q!^!_(o!_!`\$q!`!a!&`!a;'S\$q;'S;=`)c<%lO\$qk!&kX!RQVP|W!O`Or\$qrs%gsv\$qwx'^x!^\$q!^!_(o!_;'S\$q;'S;=`)c<%lO\$qk!'aZVP|W!O`Or\$qrs%gsv\$qwx'^x!^\$q!^!_(o!_#P\$q#P#Q!(S#Q;'S\$q;'S;=`)c<%lO\$qk!(]ZVP|W!O`Or\$qrs%gsv\$qwx'^x!^\$q!^!_(o!_!`\$q!`!a!)O!a;'S\$q;'S;=`)c<%lO\$qk!)ZXxQVP|W!O`Or\$qrs%gsv\$qwx'^x!^\$q!^!_(o!_;'S\$q;'S;=`)c<%lO\$q",
        tokenizers = listOf(startTag, commentContent, piContent, cdataContent, 0, 1, 2, 3, 4),
        topRules = mapOf("Document" to listOf(0, 6)),
        tokenPrec = 0
    )
)
