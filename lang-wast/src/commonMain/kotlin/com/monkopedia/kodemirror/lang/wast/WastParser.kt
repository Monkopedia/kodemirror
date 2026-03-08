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
package com.monkopedia.kodemirror.lang.wast

import com.monkopedia.kodemirror.lezer.lr.LRParser
import com.monkopedia.kodemirror.lezer.lr.ParserSpec
import com.monkopedia.kodemirror.lezer.lr.SpecializerSpec

private val specKeyword = mapOf(
    "anyref" to 34, "dataref" to 34, "eqref" to 34, "externref" to 34,
    "i31ref" to 34, "funcref" to 34, "i8" to 34, "i16" to 34,
    "i32" to 34, "i64" to 34, "f32" to 34, "f64" to 34
)

val wastParser: LRParser = LRParser.deserialize(
    ParserSpec(
        version = 14,
        states = "!^Q]QPOOOqQPO'#CbOOQO'#Cd'#CdOOQO'#Cl'#ClOOQO'#Ch'#Ch" +
            "Q]QPOOOOQO,58|,58|OxQPO,58|OOQO-E6f-E6fOOQO1G.h1G.h",
        stateData = "!P~O_OSPOSQOS~OTPOVROXROYROZROaQO~OSUO~P]OSXO~P]O",
        goto = "xaPPPPPPbPbPPPhPPPrXROPTVQTOQVPTWTVXSOPTV",
        nodeNames = "\u26A0 LineComment BlockComment Module ) ( App Identifier " +
            "Type Keyword Number String",
        maxTerm = 17,
        nodeProps = listOf(
            listOf("isolate", -3, 1, 2, 11, ""),
            listOf("openedBy", 4, "("),
            listOf("closedBy", 5, ")"),
            listOf("group", -6, 6, 7, 8, 9, 10, 11, "Expression")
        ),
        propSources = listOf(wastHighlighting),
        skippedNodes = listOf(0, 1, 2),
        repeatNodeCount = 1,
        tokenData = "0o~R^XY}YZ}]^}pq}rs!Stu#pxy'Uyz(e{|(j}!O(j!Q!R(s" +
            "!R![*p!]!^.^#T#o.{~!SO_~~!VVOr!Srs!ls#O!S#O#P!q#P;'S!S" +
            ";'S;=`#j<%lO!S~!qOZ~~!tRO;'S!S;'S;=`!};=`O!S~#QWOr!S" +
            "rs!ls#O!S#O#P!q#P;'S!S;'S;=`#j;=`<%l!S<%lO!S~#mP;=`" +
            "<%l!S~#siqr%bst%btu%buv%bvw%bwx%bz{%b{|%b}!O%b!O!P%b" +
            "!P!Q%b!Q![%b![!]%b!^!_%b!_!`%b!`!a%b!a!b%b!b!c%b!c!}" +
            "%b#Q#R%b#R#S%b#S#T%b#T#o%b#p#q%b#r#s%b~%giV~qr%bst%b" +
            "tu%buv%bvw%bwx%bz{%b{|%b}!O%b!O!P%b!P!Q%b!Q![%b![!]" +
            "%b!^!_%b!_!`%b!`!a%b!a!b%b!b!c%b!c!}%b#Q#R%b#R#S%b#S" +
            "#T%b#T#o%b#p#q%b#r#s%b~'ZPT~!]!^'^~'aTO!]'^!]!^'p!^" +
            ";'S'^;'S;=`(_<%lO'^~'sVOy'^yz(Yz!]'^!]!^'p!^;'S'^;'S" +
            ";=`(_<%lO'^~(_OQ~~(bP;=`<%l'^~(jOS~~(mQ!Q!R(s!R![*p" +
            "~(xUY~!O!P)[!Q![*p!g!h){#R#S+U#X#Y){~)aRY~!Q![)j!g" +
            "!h){#X#Y){~)oSY~!Q![)j!g!h){#R#S*j#X#Y){~*OR{|*X}!O" +
            "*X!Q![*_~*[P!Q![*_~*dQY~!Q![*_#R#S*X~*mP!Q![)j~*uTY~" +
            "!O!P)[!Q![*p!g!h){#R#S+U#X#Y){~+XP!Q![*p~+_R!Q![+h" +
            "!c!i+h#T#Z+h~+mVY~!O!P,S!Q![+h!c!i+h!r!s-P#R#S+[#T" +
            "#Z+h#d#e-P~,XTY~!Q![,h!c!i,h!r!s-P#T#Z,h#d#e-P~,mUY~" +
            "!Q![,h!c!i,h!r!s-P#R#S.Q#T#Z,h#d#e-P~-ST{|-c}!O-c!Q" +
            "![-o!c!i-o#T#Z-o~-fR!Q![-o!c!i-o#T#Z-o~-tSY~!Q![-o" +
            "!c!i-o#R#S-c#T#Z-o~.TR!Q![,h!c!i,h#T#Z,h~.aP!]!^.d" +
            "~.iSP~OY.dZ;'S.d;'S;=`.u<%lO.d~.xP;=`<%l.d~/QiX~qr" +
            ".{st.{tu.{uv.{vw.{wx.{z{.{{|.{}!O.{!O!P.{!P!Q.{!Q![" +
            ".{![!].{!^!_.{!_!`.{!`!a.{!a!b.{!b!c.{!c!}.{#Q#R.{#R" +
            "#S.{#S#T.{#T#o.{#p#q.{#r#s.{",
        tokenizers = listOf(0),
        topRules = mapOf("Module" to listOf(0, 3)),
        specialized = listOf(
            SpecializerSpec(
                term = 9,
                get = { value, _ -> specKeyword[value] ?: -1 }
            )
        ),
        tokenPrec = 0
    )
)
