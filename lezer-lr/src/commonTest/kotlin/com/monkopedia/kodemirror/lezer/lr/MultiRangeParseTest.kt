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
import com.monkopedia.kodemirror.lezer.common.TextRange
import com.monkopedia.kodemirror.lezer.common.Tree
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * LR parses over discontinuous ranges, against `@lezer/lr` 1.4.10.
 *
 * A stack that has consumed a token ending at a range's end sits in the gap
 * before the next range. Upstream clips that position into the next range
 * before tokenizing (`TokenCache.updateCachedToken`) and applies the
 * resulting actions at the main token's clipped start
 * (`advanceStack`: `main ? main.start : localStack.pos`), so no node starts
 * inside a gap (#355).
 *
 * Every expectation is `@lezer/lr` 1.4.10's tree for the same document and
 * ranges, listing every node (including anonymous punctuation) in document
 * order with its extent.
 */
class MultiRangeParseTest {

    private fun dump(tree: Tree): String {
        val out = mutableListOf<String>()
        tree.iterate(
            IterateSpec(enter = { node ->
                val name = if (node.type.isError) "⚠" else node.type.name.ifEmpty { "?" }
                out.add("$name[${node.from},${node.to}]")
                null
            })
        )
        return out.joinToString(" ")
    }

    private fun parse(doc: String, vararg ranges: TextRange): String =
        dump(jsonParser.parse(doc, ranges = ranges.toList()))

    @Test
    fun tokenAfterAGapStartsAtTheNextRange() = assertEquals(
        "JsonText[0,9] Array[0,9] [[0,1] Number[1,2] ,[2,3] Number[7,8] ][8,9]",
        parse("[1, xxx2]", TextRange(0, 4), TextRange(7, 9))
    )

    @Test
    fun punctuationAfterAGapStartsAtTheNextRange() = assertEquals(
        "JsonText[0,8] Array[0,8] [[0,1] Number[1,5] ,[5,6] Number[6,7] ][7,8]",
        parse("[1xxx,2]", TextRange(0, 2), TextRange(5, 8))
    )

    @Test
    fun propertyNameEndingAtARangeEnd() = assertEquals(
        "JsonText[0,10] Object[0,10] {[0,1] Property[1,9] PropertyName[1,6] :[6,7] Number[8,9] }[9,10]",
        parse("{\"a\"xx: 1}", TextRange(0, 4), TextRange(6, 10))
    )

    @Test
    fun errorTokenAtTheEndOfARange() = assertEquals(
        "JsonText[0,13] Array[0,13] [[0,1] Number[1,2] ⚠[2,3] ,[10,11] Number[11,12] ][12,13]",
        parse("[1@xxxxxxx,2]", TextRange(0, 3), TextRange(10, 13))
    )

    @Test
    fun errorTokenBeforeAGap() = assertEquals(
        "JsonText[0,6] Array[0,6] [[0,1] ⚠[1,2] Number[4,5] ][5,6]",
        parse("[@xx1]", TextRange(0, 2), TextRange(4, 6))
    )

    @Test
    fun errorTokenAfterASeparatorBeforeAGap() = assertEquals(
        "JsonText[0,8] Array[0,8] [[0,1] Number[1,2] ,[2,3] ⚠[3,4] Number[6,7] ][7,8]",
        parse("[1,@xx2]", TextRange(0, 4), TextRange(6, 8))
    )

    // `ambiguousParser` forks on every `Num` after the first, so these reach
    // the split branch of `advanceStack` with a stack sitting in a gap.

    private fun parseAmbiguous(doc: String, vararg ranges: TextRange): String =
        dump(ambiguousParser.parse(doc, ranges = ranges.toList()))

    @Test
    fun forkedStacksApplyAfterAGap() = assertEquals(
        "Program[0,5] Y[0,5] Num[0,1] Num[4,5]",
        parseAmbiguous("1 xx2", TextRange(0, 2), TextRange(4, 5))
    )

    @Test
    fun forkedStacksApplyAfterAGapMidDocument() = assertEquals(
        "Program[0,7] X[0,1] Num[0,1] Y[2,7] Num[2,5] Num[6,7]",
        parseAmbiguous("1 2xx 3", TextRange(0, 3), TextRange(5, 7))
    )

    @Test
    fun forkedStacksWithATokenSpanningTheGap() = assertEquals(
        "Program[0,6] Y[0,6] Num[0,4] Num[5,6]",
        parseAmbiguous("1xx2 3", TextRange(0, 1), TextRange(3, 6))
    )
}
