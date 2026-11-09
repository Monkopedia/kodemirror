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

import platform.Foundation.NSCharacterSet

private val alphanumeric = NSCharacterSet.alphanumericCharacterSet

// Kotlin's own Regex engine, which Native shares with Wasm, rejects `\p{Alphabetic}` and (as
// measured on Wasm) classifies astral code points by their low 16 bits, so use Foundation's
// L/M/N set by code point. Against upstream's `[\p{Alphabetic}\p{Number}_]` this
// is a slight superset: it also counts non-Alphabetic combining marks such as U+0301 and
// viramas. The only Alphabetic code points outside L/M/N are the enclosed letters below
// (category So), identical in Unicode 10, 15 and 17. :autocomplete's FindWords.native.kt keeps
// the same list.
internal actual fun hasWordChar(str: String): Boolean {
    var i = 0
    while (i < str.length) {
        val cp = codePointAt(str, i)
        if (cp == '_'.code ||
            isEnclosedLetter(cp) ||
            alphanumeric.longCharacterIsMember(cp.toUInt())
        ) {
            return true
        }
        i += codePointSize(cp)
    }
    return false
}

// Circled, squared, negative circled and negative squared Latin letters.
private fun isEnclosedLetter(cp: Int): Boolean = cp in 0x24b6..0x24e9 ||
    cp in 0x1f130..0x1f149 ||
    cp in 0x1f150..0x1f169 ||
    cp in 0x1f170..0x1f189
