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
package com.monkopedia.kodemirror.lezer.html

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlParserTest {

    private fun parse(input: String): String = treeToString(parser.parse(input))

    // === tags.txt tests ===

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
            "Element(SelfClosingTag(StartTag,TagName,EndTag))," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<a><b>c</b><br></a>")
    )

    @Test
    fun parsesAttribute() = assertEquals(
        "Document(Element(SelfClosingTag(StartTag,TagName," +
            "Attribute(AttributeName,Is,AttributeValue)," +
            "EndTag)))",
        parse("<br foo=\"bar\">")
    )

    @Test
    fun parsesMultipleAttributes() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName," +
            "Attribute(AttributeName,Is,AttributeValue)," +
            "Attribute(AttributeName,Is,AttributeValue)," +
            "Attribute(AttributeName,Is,AttributeValue)," +
            "EndTag)," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<a x=\"one\" y=\"two\" z=\"three\"></a>")
    )

    @Test
    fun parsesValuelessAttributes() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName," +
            "Attribute(AttributeName)," +
            "Attribute(AttributeName,Is,AttributeValue)," +
            "Attribute(AttributeName)," +
            "EndTag)," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<a x y=\"one\" z></a>")
    )

    @Test
    fun parsesUnquotedAttributes() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName," +
            "Attribute(AttributeName,Is,UnquotedAttributeValue)," +
            "Attribute(AttributeName)," +
            "Attribute(AttributeName,Is,UnquotedAttributeValue)," +
            "EndTag)," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<a x=one y z=two></a>")
    )

    @Test
    fun parsesEntities() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName," +
            "Attribute(AttributeName,Is,AttributeValue(EntityReference))," +
            "EndTag)," +
            "EntityReference,CharacterReference," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<a attr=\"one&amp;two\">&amp;&#67;</a>")
    )

    @Test
    fun parsesDoctype() = assertEquals(
        "Document(DoctypeDecl,Text," +
            "Element(OpenTag(StartTag,TagName,EndTag)," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<!doctype html>\n<doc></doc>")
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
            "Text,Comment,Text,Comment)",
        parse(
            "<!-- top comment -->\n<element>" +
                "<!-- inner comment --> text</element>\n" +
                "<!----> \n<!--\n-->"
        )
    )

    @Test
    fun parsesMismatchedTag() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "MismatchedCloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<a></b>")
    )

    @Test
    fun parsesUnclosedTag() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)))",
        parse("<a>")
    )

    @Test
    fun parsesIgnorePseudoXmlSelfClosers() = assertEquals(
        "Document(Element(SelfClosingTag(StartTag,TagName,EndTag)))",
        parse("<br/>")
    )

    @Test
    fun parsesNestedMismatchedTag() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "Element(OpenTag(StartTag,TagName,EndTag)," +
            "Element(OpenTag(StartTag,TagName,EndTag)," +
            "CloseTag(StartCloseTag,TagName,EndTag))," +
            "MismatchedCloseTag(StartCloseTag,TagName,EndTag)," +
            "\u26A0)," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<a><b><c></c></x></a>")
    )

    @Test
    fun parsesSelfClosingTags() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "Element(SelfClosingTag(StartTag,TagName," +
            "Attribute(AttributeName,Is,UnquotedAttributeValue)," +
            "EndTag))," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<a><img src=blah></a>")
    )

    @Test
    fun parsesImplicitlyClosed() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "Element(OpenTag(StartTag,TagName,EndTag),Text)," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<dl><dd>Hello</dl>")
    )

    @Test
    fun parsesClosedBySibling() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "Text," +
            "Element(OpenTag(StartTag,TagName,EndTag),Text)," +
            "Element(OpenTag(StartTag,TagName,EndTag),Text)," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<div>\n  <p>Foo\n  <p>Bar\n</div>")
    )

    @Test
    fun parsesTextarea() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "Text," +
            "Element(OpenTag(StartTag,TagName," +
            "Attribute(AttributeName,Is,UnquotedAttributeValue)," +
            "EndTag)," +
            "TextareaText," +
            "CloseTag(StartCloseTag,TagName,EndTag))))",
        parse(
            "<p>Enter something: <textarea code-lang=javascript>" +
                "function foo() {\n  return \"</bar>\"\n}</textarea>"
        )
    )

    @Test
    fun parsesScript() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "ScriptText," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<script>This is not an entity: &lt;</script>")
    )

    @Test
    fun parsesStrayAmpersand() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "Text,InvalidEntity,Text," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<html>a&b</html>")
    )

    @Test
    fun parsesTopLevelMismatchedCloseTag() = assertEquals(
        "Document(" +
            "Element(OpenTag(StartTag,TagName,EndTag)," +
            "CloseTag(StartCloseTag,TagName,EndTag))," +
            "MismatchedCloseTag(StartCloseTag,TagName,EndTag))",
        parse("<a></a></a>")
    )

    @Test
    fun parsesIncompleteCloseTag() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "Element(OpenTag(StartTag,TagName,EndTag)," +
            "IncompleteCloseTag,\u26A0)," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<html><body></</html>")
    )

    @Test
    fun parsesForeignElementSelfClosing() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName,EndTag)," +
            "Element(OpenTag(StartTag,TagName,EndTag)," +
            "Element(SelfClosingTag(StartTag,TagName,SelfClosingEndTag))," +
            "CloseTag(StartCloseTag,TagName,EndTag))," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<div><svg><circle/></svg></div>")
    )

    @Test
    fun parsesOnlyLessThanAsOpeningWhenFollowedByName() = assertEquals(
        "Document(IncompleteTag,Text)",
        parse("< div>x")
    )

    // === vue.txt tests ===

    @Test
    fun parsesVueBuiltinDirectives() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName," +
            "Attribute(AttributeName,Is,AttributeValue)," +
            "EndTag)," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<span v-text=\"msg\"></span>")
    )

    @Test
    fun parsesVueClickShorthand() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName," +
            "Attribute(AttributeName,Is,AttributeValue)," +
            "EndTag)," +
            "Text," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<button @click=\"handler()\">Click me</button>")
    )

    @Test
    fun parsesVueDynamicArguments() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName," +
            "Attribute(AttributeName,Is,AttributeValue)," +
            "EndTag)," +
            "Text," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<a v-bind:[attributeName]=\"url\">Link</a>")
    )

    // === mixed.txt tests (without JS parser nesting) ===

    @Test
    fun parsesScriptWithType() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName," +
            "Attribute(AttributeName,Is,AttributeValue)," +
            "EndTag)," +
            "ScriptText," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<script type=\"text/visualbasic\">let something = 20</script>")
    )

    @Test
    fun parsesScriptWithUnquotedType() = assertEquals(
        "Document(Element(OpenTag(StartTag,TagName," +
            "Attribute(AttributeName,Is,UnquotedAttributeValue)," +
            "EndTag)," +
            "ScriptText," +
            "CloseTag(StartCloseTag,TagName,EndTag)))",
        parse("<script type=something></foo></script>")
    )
}
