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

/**
 * A tiny grammar whose reduce chain is deliberately longer than
 * [Rec.FORCE_REDUCE_LIMIT], used to exercise error recovery through deep
 * reductions. Built with lezer-generator from:
 *
 * ```
 * @top Program { Item (Comma Item)* }
 * Item { L1 }
 * L1 { L2 }
 * ... (unit productions L1..L11)
 * L12 { Num }
 * @tokens {
 *   Num { $[0-9]+ }
 *   Comma { "," }
 *   space { $[ \t\n]+ }
 * }
 * @skip { space }
 * ```
 *
 * Reducing a single `Num` all the way up to `Item` takes twelve reductions,
 * none of which move the parse position, so recovering this grammar drives
 * far more non-advancing steps than the JSON fixture does.
 */
val chainParser: LRParser = LRParser.deserialize(
    ParserSpec(
        version = 14,
        states = "#fOVQPOOOOQO'#Cj'#CjOOQO'#Ci'#CiOOQO'#Ch'#ChOOQO'#Cg'#Cg" +
            "OOQO'#Cf'#CfOOQO'#Ce'#CeOOQO'#Cd'#CdOOQO'#Cc'#CcOOQO'#Cb'#Cb" +
            "OOQO'#Ca'#CaOOQO'#C`'#C`OOQO'#C_'#C_OOQO'#C^'#C^Q[QPOOOVQPO'#Cm" +
            "Q[QPOOOOQO,59X,59XOOQO-E6k-E6k",
        stateData = "a~OdOS~O_PO~O`_O~O",
        goto = "!qbPPcimquy}!R!V!Z!_!c!gPP!kQ^ORa_T]O_T[O_TZO_TYO_TXO_TWO_" +
            "TVO_TUO_TTO_TSO_TRO_TQO_Q`^Rb`",
        nodeNames = "\u26A0 Program Item L1 L2 L3 L4 L5 L6 L7 L8 L9 L10 L11 " +
            "L12 Num Comma",
        maxTerm = 20,
        skippedNodes = listOf(0),
        repeatNodeCount = 1,
        tokenData = "}~RTXYbYZbpqb|}p!Q![u~gRd~XYbYZbpqb~uO`~~zP_~!Q![u",
        tokenizers = listOf(0),
        topRules = mapOf("Program" to listOf(0, 1)),
        tokenPrec = 0
    )
)
