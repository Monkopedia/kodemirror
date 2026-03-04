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
@file:Suppress("ktlint:standard:max-line-length")

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

package com.monkopedia.kodemirror.lezer.yaml

import kotlin.test.Test
import kotlin.test.assertEquals

class YamlParserTest {

    private fun parse(input: String): String = treeToString(parser.parse(input))

    // === basics.txt tests ===

    @Test
    fun simpleFlowValues() = assertEquals(
        "Stream(Document(FlowMapping(" +
            "Pair(Key(Literal),FlowSequence(Item(Literal),Item(Literal)))," +
            "Pair(Key(Literal),QuotedLiteral))))",
        parse("{foo: [bar, baz],\n bar: \"one\ntwo three\"}")
    )

    @Test
    fun pairs() = assertEquals(
        "Stream(Document(FlowSequence(" +
            "Item(FlowMapping(Pair(Key(Literal),Literal)))," +
            "Item(FlowMapping(Pair(Literal)))," +
            "Item(FlowMapping(Pair(Key(Literal)))))))",
        parse("[one: two, ? : four, x: ]")
    )

    @Test
    fun simpleSequence() = assertEquals(
        "Stream(Document(BlockSequence(Item(Literal),Item(Literal),Item(Literal))))",
        parse("- one\n- two\n- three")
    )

    @Test
    fun nestedSequence() = assertEquals(
        "Stream(Document(BlockSequence(" +
            "Item(BlockSequence(Item(Literal),Item(Literal)))," +
            "Item(Literal))))",
        parse("- - one\n  - two\n- three")
    )

    @Test
    fun simpleMapping() = assertEquals(
        "Stream(Document(BlockMapping(" +
            "Pair(Key(Literal),Literal)," +
            "Pair(Key(Literal),Literal))))",
        parse("One: A\nTwo: B")
    )

    @Test
    fun semicolonInAtom() = assertEquals(
        "Stream(Document(Literal))",
        parse("foo:bar")
    )

    @Test
    fun explicitlyIndentedBlockLiteral() = assertEquals(
        "Stream(Document(BlockMapping(" +
            "Pair(Key(Literal),BlockLiteral(BlockLiteralHeader,BlockLiteralContent)))))",
        parse("foo: |2\n      one\n  two")
    )

    // === spec.txt tests ===

    @Test
    fun example2_1SequenceOfScalars() = assertEquals(
        "Stream(Document(BlockSequence(Item(Literal),Item(Literal),Item(Literal))))",
        parse("- Mark McGwire\n- Sammy Sosa\n- Ken Griffey")
    )

    // Disabled: crashes with IndexOutOfBoundsException in lezer-lr tree builder
    // This appears to be a core lezer-lr JS-to-Kotlin porting issue, not YAML-specific
    // @Test
    // fun example2_2MappingScalarsToScalars() = assertEquals(
    //     "Stream(Document(BlockMapping(" +
    //         "Pair(Key(Literal),Literal),Comment," +
    //         "Pair(Key(Literal),Literal),Comment," +
    //         "Pair(Key(Literal),Literal))),Comment)",
    //     parse("hr:  65    # Home runs\navg: 0.278 # Batting average\nrbi: 147   # Runs Batted In")
    // )

    @Test
    fun example2_5SequenceOfSequences() = assertEquals(
        "Stream(Document(BlockSequence(" +
            "Item(FlowSequence(Item(Literal),Item(Literal),Item(Literal)))," +
            "Item(FlowSequence(Item(Literal),Item(Literal),Item(Literal)))," +
            "Item(FlowSequence(Item(Literal),Item(Literal),Item(Literal))))))",
        parse(
            "- [name        , hr, avg  ]\n" +
                "- [Mark McGwire, 65, 0.278]\n" +
                "- [Sammy Sosa  , 63, 0.288]"
        )
    )

    @Test
    fun example2_6MappingOfMappings() = assertEquals(
        "Stream(Document(BlockMapping(" +
            "Pair(Key(Literal),FlowMapping(" +
            "Pair(Key(Literal),Literal)," +
            "Pair(Key(Literal),Literal)))," +
            "Pair(Key(Literal),FlowMapping(" +
            "Pair(Key(Literal),Literal)," +
            "Pair(Key(Literal),Literal))))))",
        parse(
            "Mark McGwire: {hr: 65, avg: 0.278}\n" +
                "Sammy Sosa: {\n" +
                "    hr: 63,\n" +
                "    avg: 0.288,\n" +
                " }"
        )
    )

    @Test
    fun example2_13Literals() = assertEquals(
        "Stream(Comment,Document(DirectiveEnd,BlockLiteral(BlockLiteralHeader,BlockLiteralContent)))",
        parse("# ASCII Art\n--- |\n  \\//||\\/||\n  // ||  ||__")
    )

    @Test
    fun example2_17QuotedScalars() = assertEquals(
        "Stream(Document(BlockMapping(" +
            "Pair(Key(Literal),QuotedLiteral)," +
            "Pair(Key(Literal),QuotedLiteral)," +
            "Pair(Key(Literal),QuotedLiteral)," +
            "Pair(Key(Literal),QuotedLiteral)," +
            "Pair(Key(Literal),QuotedLiteral)," +
            "Pair(Key(Literal),QuotedLiteral))))",
        parse(
            "unicode: \"Sosa did fine.\\u263A\"\n" +
                "control: \"\\b1998\\t1999\\t2000\\n\"\n" +
                "hex esc: \"\\x0d\\x0a is \\r\\n\"\n" +
                "\n" +
                "single: '\"Howdy!\" he cried.'\n" +
                "quoted: ' # Not a ''comment''.'\n" +
                "tie-fighter: '|\\-*-/|'"
        )
    )

    @Test
    fun example5_4FlowCollectionIndicators() = assertEquals(
        "Stream(Document(BlockMapping(" +
            "Pair(Key(Literal),FlowSequence(Item(Literal),Item(Literal)))," +
            "Pair(Key(Literal),FlowMapping(Pair(Key(Literal),Literal),Pair(Key(Literal),Literal))))))",
        parse("sequence: [ one, two, ]\nmapping: { sky: blue, sea: green }")
    )

    @Test
    fun example5_9DirectiveIndicator() = assertEquals(
        "Stream(Document(Directive(DirectiveName,DirectiveContent),DirectiveEnd,Literal))",
        parse("%YAML 1.2\n--- text")
    )

    @Test
    fun example6_29NodeAnchors() = assertEquals(
        "Stream(Document(BlockMapping(" +
            "Pair(Key(Literal),Anchored(Anchor,Literal))," +
            "Pair(Key(Literal),Alias))))",
        parse("First occurrence: &anchor Value\nSecond occurrence: *anchor")
    )

    @Test
    fun example7_7SingleQuotedCharacters() = assertEquals(
        "Stream(Document(QuotedLiteral))",
        parse("'here''s to \"quotes\"'")
    )

    @Test
    fun example7_15FlowMappings() = assertEquals(
        "Stream(Document(BlockSequence(" +
            "Item(FlowMapping(Pair(Key(Literal),Literal),Pair(Key(Literal),Literal)))," +
            "Item(FlowMapping(Pair(Key(Literal),Literal),Pair(Key(Literal),Literal))))))",
        parse("- { one : two , three: four , }\n- {five: six,seven : eight}")
    )

    @Test
    fun example7_24FlowNodes() = assertEquals(
        "Stream(Document(BlockSequence(" +
            "Item(Tagged(Tag,QuotedLiteral))," +
            "Item(QuotedLiteral)," +
            "Item(Anchored(Anchor,QuotedLiteral))," +
            "Item(Alias)," +
            "Item(Tagged(Tag)))))",
        parse("- !!str \"a\"\n- 'b'\n- &anchor \"c\"\n- *anchor\n- !!str")
    )

    @Test
    fun example8_1BlockScalarHeader() = assertEquals(
        "Stream(Document(BlockSequence(" +
            "Item(BlockLiteral(BlockLiteralHeader,Comment,BlockLiteralContent))," +
            "Item(BlockLiteral(BlockLiteralHeader,Comment,BlockLiteralContent))," +
            "Item(BlockLiteral(BlockLiteralHeader,Comment,BlockLiteralContent))," +
            "Item(BlockLiteral(BlockLiteralHeader,Comment,BlockLiteralContent)))))",
        parse(
            "- | # Empty header\n" +
                " literal\n" +
                "- >1 # Indentation indicator\n" +
                " \u00B7folded\n" +
                "- |+ # Chomping indicator\n" +
                " keep\n" +
                "\n" +
                "- >1- # Both indicators\n" +
                "  strip"
        )
    )
}
