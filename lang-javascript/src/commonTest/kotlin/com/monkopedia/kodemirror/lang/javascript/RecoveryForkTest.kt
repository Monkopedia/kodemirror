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
package com.monkopedia.kodemirror.lang.javascript

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Error recovery that reaches an ambiguous LR state (#354).
 *
 * `@lezer/lr` 1.4.10's `advanceStack` takes `last = i == actions.length || !split`:
 * recovery drives stacks through `advanceFully`, which passes no `split` list, so
 * a state offering several actions there applies the first one to the stack
 * rather than forking. Each input below makes upstream's recovery hit such a
 * state (confirmed with a counter patched into upstream's `advanceStack`), and
 * each expectation is the tree `@lezer/javascript` 1.5.7 builds for it.
 */
class RecoveryForkTest {

    private fun parse(doc: String) = treeToString(jsParser.parse(doc))

    @Test
    fun arrowWithoutParameters() = assertEquals(
        "Script(⚠(Arrow),ExpressionStatement(SequenceExpression(" +
            "BinaryExpression(ParenthesizedExpression(⚠),CompareOp,⚠),⚠)))",
        parse("=>?(<,")
    )

    @Test
    fun strayPunctuationAfterABlock() = assertEquals(
        "Script(Block,⚠,⚠,⚠,ExpressionStatement(ParenthesizedExpression(⚠)))",
        parse("{; }?};:(")
    )

    @Test
    fun labelFollowedByAnUnclosedParen() = assertEquals(
        "Script(LabeledStatement(Label,ExpressionStatement(" +
            "SequenceExpression(ParenthesizedExpression(⚠),⚠))))",
        parse("asynclet:(,")
    )
}
