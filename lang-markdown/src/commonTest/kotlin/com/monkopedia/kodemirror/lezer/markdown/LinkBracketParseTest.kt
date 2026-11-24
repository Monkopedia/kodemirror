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
package com.monkopedia.kodemirror.lezer.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Bracket pairs that do not form a link, such as task-list checkboxes, `[]` and `[ ]`, must parse
 * instead of throwing (#447). Expected trees are `@lezer/markdown` 1.6.3's output for the same
 * input, for the CommonMark parser and the GFM-configured one.
 */
class LinkBracketParseTest {

    private val gfmParser = markdownParser.configure(GFM)

    private fun assertParses(input: String, commonMark: String, gfm: String) {
        assertEquals(commonMark, treeToString(markdownParser.parse(input)), "CommonMark: $input")
        assertEquals(gfm, treeToString(gfmParser.parse(input)), "GFM: $input")
    }

    @Test
    fun uncheckedTask() = assertParses(
        "- [ ] task",
        "Document(BulletList(ListItem(ListMark,Paragraph)))",
        "Document(BulletList(ListItem(ListMark,Task(TaskMarker))))"
    )

    @Test
    fun checkedTask() = assertParses(
        "- [x] done",
        "Document(BulletList(ListItem(ListMark,Paragraph(Link(LinkMark,LinkMark)))))",
        "Document(BulletList(ListItem(ListMark,Task(TaskMarker))))"
    )

    @Test
    fun emptyBrackets() = assertParses("[]", "Document(Paragraph)", "Document(Paragraph)")

    @Test
    fun emptyBracketsInline() =
        assertParses("see [] here", "Document(Paragraph)", "Document(Paragraph)")

    @Test
    fun spaceBracketsInParagraph() =
        assertParses("a [ ] b", "Document(Paragraph)", "Document(Paragraph)")

    @Test
    fun imageOfSpace() = assertParses("![ ]", "Document(Paragraph)", "Document(Paragraph)")

    @Test
    fun spaceBracketsFollowedByUrl() = assertParses(
        "[ ](x)",
        "Document(Paragraph(Link(LinkMark,LinkMark,LinkMark,URL,LinkMark)))",
        "Document(Paragraph(Link(LinkMark,LinkMark,LinkMark,URL,LinkMark)))"
    )

    @Test
    fun mixedDocument() = assertParses(
        "# Todo\n\n- [ ] task\n- [x] done\n\n" +
            "See [] and [ ] here, a [link](u) and [ref][] too.\n\n[ref]: /url",
        "Document(ATXHeading1(HeaderMark),BulletList(ListItem(ListMark,Paragraph)," +
            "ListItem(ListMark,Paragraph(Link(LinkMark,LinkMark)))),Paragraph(Link(LinkMark," +
            "LinkMark,LinkMark,URL,LinkMark),Link(LinkMark,LinkMark,LinkLabel))," +
            "LinkReference(LinkLabel,LinkMark,URL))",
        "Document(ATXHeading1(HeaderMark),BulletList(ListItem(ListMark,Task(TaskMarker))," +
            "ListItem(ListMark,Task(TaskMarker))),Paragraph(Link(LinkMark,LinkMark,LinkMark,URL," +
            "LinkMark),Link(LinkMark,LinkMark,LinkLabel)),LinkReference(LinkLabel,LinkMark,URL))"
    )
}
