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
 * A deliberately ambiguous grammar, used to exercise the split-stack handling
 * in `Parse.advanceStack`. Built with lezer-generator from:
 *
 * ```
 * @top Program { (X | Y)+ }
 * X { Num ~amb }
 * Y { Num ~amb Num }
 * @tokens { Num { $[0-9]+ } space { " " } }
 * @skip { space }
 * ```
 *
 * The `~amb` markers tell the generator to keep the shift/reduce conflict on
 * `Num` rather than resolving it, so a state in this grammar offers two
 * actions for the same token and the parser has to fork. That is the only
 * shape in which `advanceStack`'s split handling runs at all -- JSON, the
 * other fixture in this module, is unambiguous and never forks.
 */
val ambiguousParser: LRParser = LRParser.deserialize(
    ParserSpec(
        version = 14,
        states = "tOVQPOOO[QPO'#C^OOQO'#Ca'#CaQVQPOOOOQO,58z,58zOOQO-E6_-E6_",
        stateData = "g~OWOS~ORPO~ORSORQXUQX~O",
        goto = "aUPPVPVZTQORQRORTR",
        nodeNames = "\u26A0 Program X Num Y",
        maxTerm = 8,
        skippedNodes = listOf(0),
        repeatNodeCount = 1,
        tokenData = "f~RQpqX!Q![^~^OW~~cPR~!Q![^",
        tokenizers = listOf(0),
        topRules = mapOf("Program" to listOf(0, 1)),
        tokenPrec = 0
    )
)
