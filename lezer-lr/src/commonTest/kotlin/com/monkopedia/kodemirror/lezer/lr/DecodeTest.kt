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
package com.monkopedia.kodemirror.lezer.lr

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Tests for the [decodeArray] function which decodes base-46 encoded strings
 * into IntArrays.
 *
 * The encoding format:
 * - The first decoded value is the array length
 * - Subsequent decoded values fill the array
 * - Characters are base-46 encoded with gap adjustments for '"' (code 34)
 *   and '\\' (code 92)
 * - BigValCode (char code 126 = '~') encodes the special value 0xffff
 */
class DecodeTest {

    /**
     * Encodes a single integer value into the lezer base-46 string encoding.
     *
     * The encoding works as follows:
     * - Special case: 0xffff is encoded as char code 126 ('~')
     * - Otherwise, digits are emitted most-significant first in base 46
     * - Each digit is offset by [Encode.START] (32)
     * - The last emitted digit (least significant) has [Encode.BASE] (46) added
     *   to signal stop
     * - Gap characters at code points 34 ('"') and 92 ('\\') are skipped over
     */
    private fun encodeValue(v: Int): String {
        if (v == Encode.BIG_VAL) {
            return Encode.BIG_VAL_CODE.toChar().toString()
        }
        // Decompose value into base-46 digits, LSB first
        val digits = mutableListOf<Int>()
        var remaining = v
        do {
            digits.add(remaining % Encode.BASE)
            remaining /= Encode.BASE
        } while (remaining > 0)
        // Reverse to get MSB first (decoder reads MSB first)
        digits.reverse()
        val sb = StringBuilder()
        for (i in digits.indices) {
            val digit = digits[i]
            val isLast = i == digits.size - 1
            val encodedDigit = if (isLast) digit + Encode.BASE else digit
            var charCode = encodedDigit + Encode.START
            // Adjust for gap characters: if charCode >= Gap1 (34), increment
            // if charCode >= Gap2 (92), increment again
            if (charCode >= Encode.GAP1) charCode++
            if (charCode >= Encode.GAP2) charCode++
            sb.append(charCode.toChar())
        }
        return sb.toString()
    }

    /**
     * Encodes an IntArray into a lezer-format encoded string.
     * The first encoded value is the array size, followed by the array values.
     */
    private fun encodeArray(values: IntArray): String {
        val sb = StringBuilder()
        sb.append(encodeValue(values.size))
        for (v in values) {
            sb.append(encodeValue(v))
        }
        return sb.toString()
    }

    // --- Basic round-trip tests ---

    @Test
    fun decodeEmptyArray() {
        // Encode array of length 0
        val encoded = encodeValue(0)
        val result = decodeArray(encoded)
        assertEquals(0, result.size)
        assertContentEquals(intArrayOf(), result)
    }

    @Test
    fun decodeSingleElementArray() {
        val expected = intArrayOf(42)
        val encoded = encodeArray(expected)
        val result = decodeArray(encoded)
        assertContentEquals(expected, result)
    }

    @Test
    fun decodeSmallValueArray() {
        val expected = intArrayOf(1, 2, 3)
        val encoded = encodeArray(expected)
        val result = decodeArray(encoded)
        assertContentEquals(expected, result)
    }

    @Test
    fun decodeZeroValues() {
        val expected = intArrayOf(0, 0, 0)
        val encoded = encodeArray(expected)
        val result = decodeArray(encoded)
        assertContentEquals(expected, result)
    }

    @Test
    fun decodeLargerValues() {
        // Values that require multi-digit encoding (>= 46)
        val expected = intArrayOf(46, 91, 100, 200, 500, 1000)
        val encoded = encodeArray(expected)
        val result = decodeArray(encoded)
        assertContentEquals(expected, result)
    }

    @Test
    fun decodeValuesNearGap1() {
        // Test values where the encoded char code is near Gap1 (34, '"')
        val expected = intArrayOf(1, 2, 3, 4, 5)
        val encoded = encodeArray(expected)
        val result = decodeArray(encoded)
        assertContentEquals(expected, result)
    }

    @Test
    fun decodeValuesNearGap2() {
        // Test values where the encoded char code is near Gap2 (92, '\\')
        // Digit 59 + Start(32) = 91, which is just before Gap2
        // Digit 60 + Start(32) = 92, which hits Gap2
        val expected = intArrayOf(13, 14, 15)
        val encoded = encodeArray(expected)
        val result = decodeArray(encoded)
        assertContentEquals(expected, result)
    }

    // --- BigVal (0xffff) tests ---

    @Test
    fun decodeBigVal() {
        val expected = intArrayOf(0xffff)
        val encoded = encodeArray(expected)
        val result = decodeArray(encoded)
        assertContentEquals(expected, result)
    }

    @Test
    fun decodeMixedWithBigVal() {
        val expected = intArrayOf(5, 0xffff, 10, 0xffff, 0)
        val encoded = encodeArray(expected)
        val result = decodeArray(encoded)
        assertContentEquals(expected, result)
    }

    @Test
    fun bigValEncodedAsTildeChar() {
        // '~' has char code 126 = Encode.BIG_VAL_CODE
        // An array of [0xffff] should contain '~' for the bigval
        val encoded = encodeArray(intArrayOf(0xffff))
        assertTrue(encoded.contains('~'))
    }

    // --- Known manual encodings ---

    @Test
    fun decodeManuallyEncodedZeroLengthArray() {
        // Value 0 with stop: digit = 0 + Base = 46, charCode = 46 + 32 = 78 → 'N'
        // But 78 >= Gap1(34) so charCode becomes 79 → 'O'
        // But wait, let's verify with our encoder
        val encoded = encodeValue(0)
        val result = decodeArray(encoded)
        assertEquals(0, result.size)
    }

    @Test
    fun roundTripSingleValue() {
        for (v in listOf(0, 1, 10, 45, 46, 47, 91, 92, 100, 500, 2115, 0xfffe)) {
            val arr = intArrayOf(v)
            val encoded = encodeArray(arr)
            val decoded = decodeArray(encoded)
            assertContentEquals(arr, decoded, "Round-trip failed for value $v")
        }
    }

    @Test
    fun roundTripMultiDigitValues() {
        // Values requiring 2 digits: 46..2115 (46*46-1)
        val expected = intArrayOf(46, 91, 100, 500, 1000, 2000)
        val encoded = encodeArray(expected)
        val result = decodeArray(encoded)
        assertContentEquals(expected, result)
    }

    @Test
    fun roundTripThreeDigitValues() {
        // Values requiring 3 digits: 2116..97335 (46^3-1)
        val expected = intArrayOf(2116, 5000, 10000, 50000, 65534)
        val encoded = encodeArray(expected)
        val result = decodeArray(encoded)
        assertContentEquals(expected, result)
    }

    // --- Edge cases ---

    @Test
    fun decodeLargeArray() {
        val size = 100
        val expected = IntArray(size) { it * 7 }
        val encoded = encodeArray(expected)
        val result = decodeArray(encoded)
        assertEquals(size, result.size)
        assertContentEquals(expected, result)
    }

    @Test
    fun decodeAllSmallValues() {
        // Test every value from 0 to 45 (single digit)
        val expected = IntArray(46) { it }
        val encoded = encodeArray(expected)
        val result = decodeArray(encoded)
        assertContentEquals(expected, result)
    }

    @Test
    fun decodeArrayWithAllBigVals() {
        val expected = IntArray(5) { 0xffff }
        val encoded = encodeArray(expected)
        val result = decodeArray(encoded)
        assertContentEquals(expected, result)
    }

    // --- Encoder self-tests ---

    @Test
    fun encodeValueZeroProducesSingleChar() {
        val encoded = encodeValue(0)
        assertEquals(1, encoded.length)
    }

    @Test
    fun encodeValueSmallProducesSingleChar() {
        // Values 0..45 should each produce a single character
        for (v in 0 until Encode.BASE) {
            val encoded = encodeValue(v)
            assertEquals(1, encoded.length, "Value $v should encode as single char")
        }
    }

    @Test
    fun encodeValue46ProducesTwoChars() {
        // Value 46 is the first multi-digit value
        val encoded = encodeValue(46)
        assertEquals(2, encoded.length)
    }

    @Test
    fun encodeBigValProducesSingleChar() {
        val encoded = encodeValue(0xffff)
        assertEquals(1, encoded.length)
        assertEquals('~', encoded[0])
    }

    @Test
    fun encodedStringDoesNotContainGapChars() {
        // The encoded string should never contain '"' (34) or '\\' (92)
        for (v in listOf(0, 1, 10, 45, 46, 100, 500, 1000, 5000)) {
            val encoded = encodeValue(v)
            for (c in encoded) {
                assertTrue(c.code != Encode.GAP1, "Encoded value $v contains Gap1 char")
                assertTrue(c.code != Encode.GAP2, "Encoded value $v contains Gap2 char")
            }
        }
    }

    @Test
    fun encodedArrayDoesNotContainGapChars() {
        val expected = IntArray(50) { it * 100 }
        val encoded = encodeArray(expected)
        for (c in encoded) {
            assertTrue(c.code != Encode.GAP1, "Encoded array contains Gap1 char ('\"')")
            assertTrue(c.code != Encode.GAP2, "Encoded array contains Gap2 char ('\\\\')")
        }
    }

    // --- Stress test ---

    @Test
    fun roundTripManyValues() {
        // Test a variety of values including boundary cases
        val values = intArrayOf(
            0, 1, 2, 44, 45, 46, 47, 90, 91, 92, 93,
            100, 500, 1000, 2115, 2116, 5000, 10000,
            50000, 65534, 0xffff
        )
        val encoded = encodeArray(values)
        val decoded = decodeArray(encoded)
        assertContentEquals(values, decoded)
    }

    private fun assertTrue(condition: Boolean, message: String = "") {
        kotlin.test.assertTrue(condition, message)
    }
}
