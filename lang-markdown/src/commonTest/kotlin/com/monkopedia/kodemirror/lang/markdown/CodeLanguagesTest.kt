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
package com.monkopedia.kodemirror.lang.markdown

import com.monkopedia.kodemirror.lang.html.htmlLanguage
import com.monkopedia.kodemirror.lezer.common.MountedTree
import com.monkopedia.kodemirror.lezer.common.Tree
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `codeLanguages` / `defaultCodeLanguage` mount a nested parse over the `CodeText` of a code
 * block through a `parseMixed` overlay predicate (#457). The expected strings are the result of
 * `tree.resolveInner(pos, 1)` at every position, recorded from npm `@codemirror/lang-markdown`
 * 6.5.0 with `@codemirror/lang-html` 6.4.11, so they include both the nested HTML nodes and the
 * positions between overlay ranges that stay on the markdown node.
 */
class CodeLanguagesTest {

    private val fenceLanguages = MarkdownSupportConfig(
        codeLanguages = { info -> if (info == "html") htmlLanguage else null }
    )

    private fun resolveAll(config: MarkdownSupportConfig, doc: String): String {
        val tree: Tree = markdown(config).language.parser.parse(doc)
        return (0..doc.length).joinToString(" ") { pos ->
            val node = tree.resolveInner(pos, 1)
            "$pos:${node.name}[${node.from},${node.to}]"
        }
    }

    @Test
    fun fencedHtmlMountsHtml() {
        assertEquals(
            "0:Paragraph[0,1] 1:Document[0,24] 2:Document[0,24] 3:CodeMark[3,6] " +
                "4:CodeMark[3,6] 5:CodeMark[3,6] 6:CodeInfo[6,10] 7:CodeInfo[6,10] " +
                "8:CodeInfo[6,10] 9:CodeInfo[6,10] 10:FencedCode[3,23] 11:StartTag[11,12] " +
                "12:TagName[12,13] 13:EndTag[13,14] 14:Text[14,15] 15:StartCloseTag[15,17] " +
                "16:StartCloseTag[15,17] 17:TagName[17,18] 18:EndTag[18,19] " +
                "19:FencedCode[3,23] 20:CodeMark[20,23] 21:CodeMark[20,23] 22:CodeMark[20,23] " +
                "23:Document[0,24] 24:Document[0,24]",
            resolveAll(fenceLanguages, "a\n\n```html\n<b>x</b>\n```\n")
        )
    }

    @Test
    fun multiLineFenceMountsAndInfolessFenceStaysPlain() {
        assertEquals(
            "0:HeaderMark[0,1] 1:ATXHeading1[0,3] 2:ATXHeading1[0,3] 3:Document[0,70] " +
                "4:Document[0,70] 5:CodeMark[5,8] 6:CodeMark[5,8] 7:CodeMark[5,8] " +
                "8:CodeInfo[8,12] 9:CodeInfo[8,12] 10:CodeInfo[8,12] 11:CodeInfo[8,12] " +
                "12:FencedCode[5,49] 13:StartTag[13,14] 14:TagName[14,17] 15:TagName[14,17] " +
                "16:TagName[14,17] 17:OpenTag[13,28] 18:AttributeName[18,23] " +
                "19:AttributeName[18,23] 20:AttributeName[18,23] 21:AttributeName[18,23] " +
                "22:AttributeName[18,23] 23:Is[23,24] 24:AttributeValue[24,27] " +
                "25:AttributeValue[24,27] 26:AttributeValue[24,27] 27:EndTag[27,28] " +
                "28:Text[28,30] 29:Text[28,30] 30:StartCloseTag[30,32] " +
                "31:StartCloseTag[30,32] 32:TagName[32,35] 33:TagName[32,35] " +
                "34:TagName[32,35] 35:EndTag[35,36] 36:Text[36,37] 37:StartTag[37,38] " +
                "38:TagName[38,39] 39:EndTag[39,40] 40:Text[40,41] 41:StartCloseTag[41,43] " +
                "42:StartCloseTag[41,43] 43:TagName[43,44] 44:EndTag[44,45] " +
                "45:FencedCode[5,49] 46:CodeMark[46,49] 47:CodeMark[46,49] 48:CodeMark[46,49] " +
                "49:Document[0,70] 50:Document[0,70] 51:Paragraph[51,55] 52:Paragraph[51,55] " +
                "53:Paragraph[51,55] 54:Paragraph[51,55] 55:Document[0,70] 56:Document[0,70] " +
                "57:CodeMark[57,60] 58:CodeMark[57,60] 59:CodeMark[57,60] " +
                "60:FencedCode[57,69] 61:CodeText[61,65] 62:CodeText[61,65] " +
                "63:CodeText[61,65] 64:CodeText[61,65] 65:FencedCode[57,69] " +
                "66:CodeMark[66,69] 67:CodeMark[66,69] 68:CodeMark[66,69] 69:Document[0,70] " +
                "70:Document[0,70]",
            resolveAll(
                fenceLanguages,
                "# T\n\n```html\n<div class=\"c\">hi</div>\n<p>q</p>\n```\n\n" +
                    "text\n\n```\nnone\n```\n"
            )
        )
    }

    @Test
    fun indentedCodeUsesDefaultCodeLanguage() {
        assertEquals(
            "0:Paragraph[0,1] 1:Document[0,29] 2:Document[0,29] 3:Document[0,29] " +
                "4:Document[0,29] 5:Document[0,29] 6:Document[0,29] 7:StartTag[7,8] " +
                "8:TagName[8,9] 9:EndTag[9,10] 10:Text[10,11] 11:StartCloseTag[11,13] " +
                "12:StartCloseTag[11,13] 13:TagName[13,14] 14:EndTag[14,15] 15:Text[15,20] " +
                "16:CodeBlock[7,28] 17:CodeBlock[7,28] 18:CodeBlock[7,28] 19:CodeBlock[7,28] " +
                "20:StartTag[20,21] 21:TagName[21,22] 22:EndTag[22,23] 23:Text[23,24] " +
                "24:StartCloseTag[24,26] 25:StartCloseTag[24,26] 26:TagName[26,27] " +
                "27:EndTag[27,28] 28:Document[0,29] 29:Document[0,29]",
            resolveAll(
                MarkdownSupportConfig(defaultCodeLanguage = htmlLanguage),
                "x\n\n    <i>y</i>\n    <b>z</b>\n"
            )
        )
    }

    /**
     * A fenced block's mount is bracketed (its fences delimit it) and an indented block's is not,
     * as `NestedParse.bracketed` declares (#462; npm `@codemirror/lang-markdown` 6.5.0).
     */
    @Test
    fun fencedCodeMountIsBracketed() {
        val tree = markdown(MarkdownSupportConfig(defaultCodeLanguage = htmlLanguage))
            .language.parser.parse("```\n<b>x</b>\n```\n\n    <i>y</i>\n")
        fun mount(name: String) = tree.topNode.getChild(name)?.tree?.let { MountedTree.get(it) }
        assertEquals(true, mount("FencedCode")?.bracketed)
        assertEquals(false, mount("CodeBlock")?.bracketed)
    }
}
