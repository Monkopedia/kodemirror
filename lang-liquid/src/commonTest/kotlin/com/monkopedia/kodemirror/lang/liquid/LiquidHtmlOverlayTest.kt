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
package com.monkopedia.kodemirror.lang.liquid

import com.monkopedia.kodemirror.lezer.common.MountedTree
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The HTML overlay `liquidLanguage` mounts over the markup between Liquid tags is a
 * multi-range LR parse. Tokens must start where `@lezer/lr` clips them into those ranges,
 * never in a gap holding Liquid text the HTML parser does not see (#472, #355).
 *
 * Expectations are what `@codemirror/lang-liquid` 6.3.2 (with `@lezer/lr` 1.4.10) mounts
 * for the same document.
 */
class LiquidHtmlOverlayTest {

    @Test
    fun htmlTextDoesNotSpanTheGapsBetweenOverlayRanges() {
        val doc = "<p class=\"a\">{% if a %}{{ b | c }}</p>{% endif %} <b onclick=\"q()\">x</b>"
        val mounted = MountedTree.get(liquidLanguage.parser.parse(doc))!!
        assertEquals(
            "0-13 34-38 49-72",
            mounted.overlay!!.joinToString(" ") { "${it.from}-${it.to}" }
        )
        assertEquals(
            "Document[0,72](" +
                "Element[0,38](" +
                "OpenTag[0,13](StartTag[0,1],TagName[1,2]," +
                "Attribute[3,12](AttributeName[3,8],Is[8,9],AttributeValue[9,12])," +
                "EndTag[12,13])," +
                "CloseTag[34,38](StartCloseTag[34,36],TagName[36,37],EndTag[37,38]))," +
                "Text[49,50]," +
                "Element[50,72](" +
                "OpenTag[50,67](StartTag[50,51],TagName[51,52]," +
                "Attribute[53,66](AttributeName[53,60],Is[60,61],AttributeValue[61,66])," +
                "EndTag[66,67])," +
                "Text[67,68]," +
                "CloseTag[68,72](StartCloseTag[68,70],TagName[70,71],EndTag[71,72])))",
            treeToString(mounted.tree, withPositions = true)
        )
    }
}
