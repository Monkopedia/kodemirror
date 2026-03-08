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
package com.monkopedia.kodemirror.lang.json

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonParserTest {

    private fun parse(input: String): String = treeToString(jsonParser.parse(input))

    // Literals
    @Test
    fun parsesTrue() = assertEquals("JsonText(True)", parse("true"))

    @Test
    fun parsesFalse() = assertEquals("JsonText(False)", parse("false"))

    @Test
    fun parsesNull() = assertEquals("JsonText(Null)", parse("null"))

    // Numbers
    @Test
    fun parsesSimpleInteger() = assertEquals("JsonText(Number)", parse("42"))

    @Test
    fun parsesZero() = assertEquals("JsonText(Number)", parse("0"))

    @Test
    fun parsesLeadingZerosAsError() =
        assertEquals("JsonText(Array(Number,\u26A0(Number)))", parse("[0123]"))

    @Test
    fun parsesNegativeNumber() = assertEquals("JsonText(Number)", parse("-53"))

    @Test
    fun parsesDecimal() = assertEquals("JsonText(Number)", parse("123.4"))

    @Test
    fun parsesDecimalWithoutTrailingDigits() =
        assertEquals("JsonText(Number,\u26A0)", parse("123."))

    @Test
    fun parsesExponentLowercase() = assertEquals("JsonText(Number)", parse("1e5"))

    @Test
    fun parsesExponentUppercase() = assertEquals("JsonText(Number)", parse("1E5"))

    @Test
    fun parsesExponentPlus() = assertEquals("JsonText(Number)", parse("1e+5"))

    @Test
    fun parsesExponentMinus() = assertEquals("JsonText(Number)", parse("1E-5"))

    @Test
    fun parsesExponentWithoutDigit() = assertEquals("JsonText(Number,\u26A0)", parse("42e"))

    // Strings
    @Test
    fun parsesEmptyString() = assertEquals("JsonText(String)", parse("\"\""))

    @Test
    fun parsesNonEmptyString() =
        assertEquals("JsonText(String)", parse("\"This is a boring old string\""))

    @Test
    fun parsesStringWithEscapes() =
        assertEquals("JsonText(String)", parse("\"\\\"\\\\\\/" + "\\b\\f\\n\\r" + "t\\t\""))

    @Test
    fun parsesStringWithUnicodeEscape() = assertEquals("JsonText(String)", parse("\"\\u005C\""))

    // Arrays
    @Test
    fun parsesEmptyArray() = assertEquals("JsonText(Array)", parse("[ ]"))

    @Test
    fun parsesArrayWithOneValue() =
        assertEquals("JsonText(Array(String))", parse("[\"One is the loneliest number\"]"))

    @Test
    fun parsesArrayWithMultipleValues() = assertEquals(
        "JsonText(Array(String,Number,True,Object,Array(String,String)))",
        parse(
            "[\n  \"The more the merrier\",\n  1e5,\n  true,\n  { },\n" +
                "  [\"I'm\", \"nested\"]\n]"
        )
    )

    // Objects
    @Test
    fun parsesEmptyObject() = assertEquals("JsonText(Object)", parse("{ }"))

    @Test
    fun parsesOneProperty() = assertEquals(
        "JsonText(Object(Property(PropertyName,Number)))",
        parse("{\n  \"foo\": 123\n}")
    )

    @Test
    fun parsesMultipleProperties() = assertEquals(
        "JsonText(Object(" +
            "Property(PropertyName,Number)," +
            "Property(PropertyName,String)," +
            "Property(PropertyName,Object)," +
            "Property(PropertyName,Array(Number,Number,Number))))",
        parse(
            "{\n  \"foo\": 123,\n  \"bar\": \"I'm a bar!\",\n" +
                "  \"obj\": {},\n  \"arr\": [1, 2, 3]\n}"
        )
    )

    // Complex document
    @Test
    fun parsesComplexDocument() {
        val input = "{\n" +
            "  \"description\": \"Some description\",\n" +
            "  \"keywords\": [],\n" +
            "  \"author\": \"\",\n" +
            "  \"license\": \"ISC\",\n" +
            "  \"scripts\": {\n" +
            "    \"test\": \"echo \\\"no test specified\\\" && exit 1\"\n" +
            "  },\n" +
            "  \"dependencies\": {\n" +
            "    \"@lezer/common\": \"^1.2.1\"\n" +
            "  }\n" +
            "}"
        // The upstream test just expects "JsonText" (too complex to enumerate all children)
        val result = parse(input)
        assertEquals("JsonText", result.substringBefore("("))
    }
}
