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

/**
 * Decode a string-encoded integer array.
 *
 * The first decoded value is the length of the resulting array.
 * Subsequent decoded values fill the array sequentially.
 */
fun decodeArray(input: String): IntArray {
    var array: IntArray? = null
    var pos = 0
    var out = 0
    while (pos < input.length) {
        var value = 0
        while (true) {
            var next = input[pos++].code
            if (next == Encode.BigValCode) {
                value = Encode.BigVal
                break
            }
            if (next >= Encode.Gap2) next--
            if (next >= Encode.Gap1) next--
            var digit = next - Encode.Start
            val stop: Boolean
            if (digit >= Encode.Base) {
                digit -= Encode.Base
                stop = true
            } else {
                stop = false
            }
            value += digit
            if (stop) break
            value *= Encode.Base
        }
        if (array != null) {
            array[out++] = value
        } else {
            array = IntArray(value)
        }
    }
    return array!!
}
