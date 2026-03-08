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
package com.monkopedia.kodemirror.lang.xml

import kotlin.test.Test
import kotlin.test.assertEquals

class XmlParserTest {

    private fun parse(input: String): String = treeToString(xmlParser.parse(input))

    @Test
    fun parsesSelfClosingTag() = assertEquals(
        "Document(Element(SelfClosingTag(StartTag,TagName,SelfCloseEndTag)))",
        parse("<foo/>")
    )

    @Test
    fun parsesRegularTag() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "Text," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<foo>bar</foo>")
    )

    @Test
    fun parsesNestedTag() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "Element(OpenTag(StartTag,TagName,EndTag),Text," +
            "CloseTag(StartCloseTag,TagName,EndTag))," +
            "Element(SelfClosingTag(StartTag,TagName,SelfCloseEndTag))," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<a><b>c</b><d/></a>")
    )

    @Test
    fun parsesAttributeDoubleQuote() = assertEquals(
        "Document(Element(SelfClosingTag(StartTag,TagName," +
            "Attribute(AttributeName,Is,AttributeValue)," +
            "SelfCloseEndTag)))",
        parse("<a foo=\"bar\"/>")
    )

    @Test
    fun parsesAttributeSingleQuote() = assertEquals(
        "Document(Element(SelfClosingTag(StartTag,TagName," +
            "Attribute(AttributeName,Is,AttributeValue)," +
            "SelfCloseEndTag)))",
        parse("<a foo='bar'/>")
    )

    @Test
    fun parsesMultipleAttributes() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName," +
            "Attribute(AttributeName,Is,AttributeValue)," +
            "Attribute(AttributeName,Is,AttributeValue)," +
            "Attribute(AttributeName,Is,AttributeValue)," +
            "Attribute(AttributeName,Is,AttributeValue)," +
            "EndTag)," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<a b=\"one\" c='two' d=\"three\" e='four' ></a>")
    )

    @Test
    fun parsesEntities() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName," +
            "Attribute(AttributeName,Is,AttributeValue(EntityReference)),EndTag)," +
            "EntityReference," +
            "CharacterReference," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<a attr=\"one&amp;two\">&amp;&#67;</a>")
    )

    @Test
    fun parsesDoctype() = assertEquals(
        "Document(DoctypeDecl,Text," +
            "Element(SelfClosingTag(StartTag,TagName,SelfCloseEndTag)))",
        parse("<!doctype html>\n<doc/>")
    )

    @Test
    fun parsesProcessingInstructions() = assertEquals(
        "Document(ProcessingInst," +
            "Element(OpenTag(StartTag,TagName,EndTag)," +
            "ProcessingInst," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<?foo?><bar><?baz?></bar>")
    )

    @Test
    fun parsesComments() = assertEquals(
        "Document(Comment,Text," +
            "Element(OpenTag(StartTag,TagName,EndTag)," +
            "Comment,Text," +
            "CloseTag(StartCloseTag,TagName,EndTag))," +
            "Text,Comment)",
        parse(
            "<!-- top comment -->\n<element>" +
                "<!-- inner comment --> text</element>\n<!--c--->"
        )
    )

    @Test
    fun parsesMismatchedTag() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "MismatchedCloseTag(StartCloseTag,TagName,EndTag),\u26A0))",
        parse("<a></b>")
    )

    @Test
    fun parsesNestedMismatchedTag() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "Element(OpenTag(StartTag,TagName,EndTag)," +
            "Element(OpenTag(StartTag,TagName,EndTag)," +
            "CloseTag(StartCloseTag,TagName,EndTag))," +
            "MismatchedCloseTag(StartCloseTag,TagName,EndTag)," +
            "MissingCloseTag)," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<a><b><c></c></x></a>")
    )

    @Test
    fun parsesCdata() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "Cdata," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<doc><![CDATA[ hello ]]]></doc>")
    )
}
