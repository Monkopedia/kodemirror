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
package com.monkopedia.kodemirror.lang.jinja

import com.monkopedia.kodemirror.lezer.common.MountedTree
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The HTML overlay `jinjaLanguage` mounts over the markup between Jinja tags is a
 * multi-range LR parse. Tokens must start where `@lezer/lr` clips them into those ranges,
 * never in a gap holding Jinja text the HTML parser does not see (#355).
 *
 * Expectations are what `@codemirror/lang-jinja` 6.0.0 (with `@lezer/lr` 1.4.10) mounts
 * for the same document. The mounted tree is positioned relative to the first range.
 */
class JinjaHtmlOverlayTest {

    @Test
    fun htmlTextDoesNotStartInAGapBetweenOverlayRanges() {
        val doc = "{% if a %}<div>\n{% else %}<span>{% endif %}\n<style>b{}</style>"
        val mounted = MountedTree.get(jinjaLanguage.parser.parse(doc))!!
        assertEquals(
            "10-16 26-32 43-62",
            mounted.overlay!!.joinToString(" ") { "${it.from}-${it.to}" }
        )
        assertEquals(
            "Document[0,52](Element[0,52](" +
                "OpenTag[0,5](StartTag[0,1],TagName[1,4],EndTag[4,5]),Text[5,16]," +
                "Element[16,52](" +
                "OpenTag[16,22](StartTag[16,17],TagName[17,21],EndTag[21,22]),Text[33,34]," +
                "Element[34,52](" +
                "OpenTag[34,41](StartTag[34,35],TagName[35,40],EndTag[40,41])," +
                "StyleSheet[41,44](" +
                "RuleSet[41,44](TagSelector[41,42](TagName[41,42]),Block[42,44]))," +
                "CloseTag[44,52](StartCloseTag[44,46],TagName[46,51],EndTag[51,52])))))",
            treeToString(mounted.tree, withPositions = true)
        )
    }
}
