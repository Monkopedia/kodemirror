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
package com.monkopedia.kodemirror.state

// / Count the column position at the given offset into the string,
// / taking extending characters and tab size into account.
fun countColumn(string: String, tabSize: Int, to: Int = string.length): Int {
    var n = 0
    var i = 0
    while (i < to && i < string.length) {
        if (string[i] == '\t') {
            n += tabSize - (n % tabSize)
            i++
        } else {
            n++
            i = findClusterBreak(string, i)
        }
    }
    return n
}

// / Find the offset that corresponds to the given column position
// / in a string, taking extending characters and tab size into
// / account. By default, the string length is returned when it is
// / too short to reach the column. Pass `strict` true to make it
// / return -1 in that situation.
fun findColumn(string: String, col: Int, tabSize: Int, strict: Boolean = false): Int {
    var i = 0
    var n = 0
    while (true) {
        if (n >= col) return i
        if (i == string.length) break
        n += if (string[i] == '\t') tabSize - (n % tabSize) else 1
        i = findClusterBreak(string, i)
    }
    return if (strict) -1 else string.length
}
