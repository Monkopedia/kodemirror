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

import com.monkopedia.kodemirror.lezer.common.ChangedRange
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Incremental reparses of malformed JSON, from the two differential corpora
 * behind #338. Each expectation is `@lezer/lr` 1.4.10's tree for the same
 * table, edit and fragments built with `@lezer/common` 1.5.1's
 * `TreeFragment.addTree`/`applyChanges`.
 *
 * The divergences came from `applyChanges` keeping fragments that upstream
 * drops (unchanged stretches shorter than `minGap`), not from error recovery:
 * the port then reused error-recovered nodes upstream never offers.
 */
class IncrementalReparseTest {

    private val parser = jsonParser.configure(ParserConfig(bufferLength = 1))

    private fun reparse(doc: String, change: ChangedRange, edited: String): String {
        val fragments = TreeFragment.applyChanges(
            TreeFragment.addTree(parser.parse(doc)),
            listOf(change)
        )
        return treeToString(parser.parse(edited, fragments))
    }

    @Test
    fun appendingAfterAnUnclosedObjectInsideAnArray() = assertEquals(
        "JsonText(Array(String,⚠))",
        reparse("[\"s\"{\"", ChangedRange(6, 6, 6, 9), "[\"s\"{\"  \"")
    )

    @Test
    fun appendingAfterAnUnfinishedProperty() = assertEquals(
        "JsonText(Object(Property(PropertyName,⚠,⚠,⚠,String),⚠))",
        reparse("{\"a\"", ChangedRange(4, 4, 4, 8), "{\"a\"]]\"\"")
    )

    @Test
    fun appendingAfterAnObjectWithAnInvalidMember() = assertEquals(
        "JsonText(Array(Object(⚠),⚠,⚠))",
        reparse("[{x}", ChangedRange(4, 4, 4, 6), "[{x}x{")
    )

    @Test
    fun closingNestedArraysAfterAnError() = assertEquals(
        "JsonText(Number,⚠,⚠,Number,⚠,Array(Number,⚠,⚠,Number),⚠,Array(⚠))",
        reparse("4,4[8[3", ChangedRange(7, 7, 7, 10), "4,4[8[3]] ")
    )

    @Test
    fun deletingTheOpeningBraceReusesTheTail() {
        // Upstream's incremental tree here differs from its own fresh parse
        // (which ends `⚠,Object(⚠)`): the 34-character tail survives as
        // a fragment and its nodes are reused.
        val doc = "{\"k0\": [7.5, 7.5, null], \"k1\": []}"
        assertEquals(
            "JsonText(String,⚠,⚠,Array(Number,Number,Null),⚠,⚠,String,⚠,⚠,Array,⚠,⚠)",
            reparse(doc, ChangedRange(0, 1, 0, 0), doc.substring(1))
        )
    }
}
