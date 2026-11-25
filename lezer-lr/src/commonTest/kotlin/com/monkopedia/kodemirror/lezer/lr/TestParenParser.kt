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
package com.monkopedia.kodemirror.lezer.lr

/**
 * Nested parentheses through an anonymous rule, so the parse stack grows by
 * one frame per `(` while the tree stays flat. Built with `@lezer/generator`
 * 1.8.0 from:
 *
 * ```
 * @top Program { list }
 * list { "(" list? ")" }
 * ```
 *
 * That makes it the cheapest way to push `Stack.stack` past
 * [Rec.CUT_DEPTH] without also building a tree thousands of levels deep, and
 * every forced reduction of an open `list` is missing its `)`, so the cut
 * leaves error nodes whose extents depend on both [Rec.CUT_DEPTH] and
 * [Rec.CUT_TO].
 */
val parenParser: LRParser = LRParser.deserialize(
    ParserSpec(
        version = 14,
        states = "tOQOPOOOVOPO'#C_QOOOOOOOOO,58y,58yO_OPO,58yOOOO1G.e1G.e",
        stateData = "d~OSPO~OSPOTRO~OTTO~O",
        goto = "ZSPPPTQQORSP",
        nodeNames = "⚠ Program",
        maxTerm = 5,
        skippedNodes = listOf(0),
        repeatNodeCount = 0,
        tokenData = "c~RQxyXyz^~^OS~~cOT~",
        tokenizers = listOf(0),
        topRules = mapOf("Program" to listOf(0, 1)),
        tokenPrec = 0
    )
)
