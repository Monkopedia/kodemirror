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

import com.monkopedia.kodemirror.lezer.common.IterateSpec
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `@lezer/lr`'s `advanceStack` folds an over-deep stack before reading
 * actions (1.4.8 `dist/index.js`, unchanged through 1.4.10):
 *
 * ```js
 * if (stack.stack.length >= 8400 /* Rec.CutDepth */) {
 *     while (stack.stack.length > 6000 /* Rec.CutTo */ && stack.forceReduce()) { }
 * }
 * ```
 *
 * On [parenParser] each forced reduction closes an open `list` whose `)` is
 * missing, so the cut shows up as error nodes. Every expectation is the list
 * of error-node extents `@lezer/lr` 1.4.8, the version this port tracks,
 * produces for the same input.
 */
class StackDepthCutTest {

    private fun errorsWhenParsing(depth: Int): String {
        val tree = parenParser.parse("(".repeat(depth) + ")".repeat(depth))
        val errors = mutableListOf<String>()
        tree.iterate(
            IterateSpec(enter = { node ->
                if (node.type.isError) errors.add("${node.from}-${node.to}")
                null
            })
        )
        return errors.joinToString(",")
    }

    @Test
    fun stackBelowTheCutDepthIsLeftAlone() = assertEquals("", errorsWhenParsing(2799))

    @Test
    fun stackReachingTheCutDepthIsFoldedBackToTheCutSize() =
        assertEquals("2800-2800,4799-5600,5599-5599", errorsWhenParsing(2800))

    @Test
    fun deeperStackIsFoldedTheSameWay() =
        assertEquals("2800-3000,4999-6000,5999-5999", errorsWhenParsing(3000))
}
