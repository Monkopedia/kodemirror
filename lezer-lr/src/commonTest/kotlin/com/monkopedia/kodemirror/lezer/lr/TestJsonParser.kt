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
package com.monkopedia.kodemirror.lezer.lr

import com.monkopedia.kodemirror.lezer.common.IterateSpec
import com.monkopedia.kodemirror.lezer.common.Tree

/**
 * Inline JSON parser for lezer-lr tests. Uses the same ParserSpec as :lang-json
 * but without propSources to avoid a cross-module test dependency.
 */
val jsonParser: LRParser = LRParser.deserialize(
    ParserSpec(
        version = 14,
        states = "\$bOVQPOOOOQO'#Cb'#CbOnQPO'#CeOvQPO'#ClOOQO'#Cr'#CrQOQPOO" +
            "OOQO'#Cg'#CgO}QPO'#CfO!SQPO'#CtOOQO,59P,59PO![QPO,59PO!aQPO'#Cu" +
            "OOQO,59W,59WO!iQPO,59WOVQPO,59QOqQPO'#CmO!nQPO,59`OOQO1G.k1G.k" +
            "OVQPO'#CnO!vQPO,59aOOQO1G.r1G.rOOQO1G.l1G.lOOQO,59X,59XOOQO" +
            "-E6k-E6kOOQO,59Y,59YOOQO-E6l-E6l",
        stateData = "#O~OeOS~OQSORSOSSOTSOWQO_ROgPO~OVXOgUO~O^[O~PVO[^O~O]_OVhX" +
            "~OVaO~O]bO^iX~O^dO~O]_OVha~O]bO^ia~O",
        goto = "!kjPPPPPPkPPkqwPPPPk{!RPPP!XP!e!hXSOR^bQWQRf_TVQ_Q`WRg`QcZ" +
            "RicQTOQZRQe^RhbRYQR]R",
        nodeNames = "\u26A0 JsonText True False Null Number String } { Object " +
            "Property PropertyName : , ] [ Array",
        maxTerm = 25,
        nodeProps = listOf(
            listOf("isolate", -2, 6, 11, ""),
            listOf("openedBy", 7, "{", 14, "["),
            listOf("closedBy", 8, "}", 15, "]")
        ),
        skippedNodes = listOf(0),
        repeatNodeCount = 2,
        tokenData = "(|~RaXY!WYZ!W]^!Wpq!Wrs!]|}\$u}!O\$z!Q!R%T!R![&c![!]&t!}#O&y" +
            "#P#Q'O#Y#Z'T#b#c'r#h#i(Z#o#p(r#q#r(w~!]Oe~~!`Wpq!]qr!]rs!xs#O!]" +
            "#O#P!}#P;'S!];'S;=`\$o<%lO!]~!}Og~~#QXrs!]!P!Q!]#O#P!]#U#V!]#Y#Z" +
            "!]#b#c!]#f#g!]#h#i!]#i#j#m~#pR!Q![#y!c!i#y#T#Z#y~#|R!Q![\$V!c!i" +
            "\$V#T#Z\$V~\$YR!Q![\$c!c!i\$c#T#Z\$c~\$fR!Q![!]!c!i!]#T#Z!]~\$rP" +
            ";=`<%l!]~\$zO]~~\$}Q!Q!R%T!R![&c~%YRT~!O!P%c!g!h%w#X#Y%w~%fP!Q![" +
            "%i~%nRT~!Q![%i!g!h%w#X#Y%w~%zR{|&T}!O&T!Q![&Z~&WP!Q![&Z~&`PT~!Q!" +
            "[&Z~&hST~!O!P%c!Q![&c!g!h%w#X#Y%w~&yO[~~'OO_~~'TO^~~'WP#T#U'Z~'^" +
            "P#`#a'a~'dP#g#h'g~'jP#X#Y'm~'rOR~~'uP#i#j'x~'{P#`#a(O~(RP#`#a(U" +
            "~(ZOS~~(^P#f#g(a~(dP#i#j(g~(jP#X#Y(m~(rOQ~~(wOW~~(|OV~",
        tokenizers = listOf(0),
        topRules = mapOf("JsonText" to listOf(0, 1)),
        tokenPrec = 0
    )
)

private val nonWordRegex = Regex("\\W")

private fun shouldIgnore(name: String, isError: Boolean): Boolean {
    if (isError) return false
    return name.isEmpty() || nonWordRegex.containsMatchIn(name)
}

/**
 * Convert a tree to the upstream s-expression format.
 * Error node (id=0) renders as "\u26A0", anonymous/punctuation nodes are skipped.
 */
fun treeToString(tree: Tree): String {
    val stack = mutableListOf<Pair<String, MutableList<String>>>()

    tree.iterate(
        IterateSpec(
            enter = { nodeRef ->
                val name = nodeRef.type.name
                val isError = nodeRef.type.isError
                val displayName = if (isError) "\u26A0" else name
                if (shouldIgnore(name, isError)) {
                    false
                } else {
                    stack.add(displayName to mutableListOf())
                    null
                }
            },
            leave = { nodeRef ->
                val name = nodeRef.type.name
                val isError = nodeRef.type.isError
                if (!shouldIgnore(name, isError)) {
                    val (nodeName, children) = stack.removeLast()
                    val str = if (children.isEmpty()) {
                        nodeName
                    } else {
                        "$nodeName(${children.joinToString(",")})"
                    }
                    if (stack.isEmpty()) {
                        stack.add(str to mutableListOf())
                    } else {
                        stack.last().second.add(str)
                    }
                }
            }
        )
    )

    return stack.firstOrNull()?.first ?: ""
}
