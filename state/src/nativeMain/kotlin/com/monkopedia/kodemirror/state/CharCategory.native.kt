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
// viramas. The circled letters (U+24B6-U+24E9) are Alphabetic but category So, so they are
// added; the squared letters at U+1F130-U+1F189 are not.
internal actual fun hasWordChar(str: String): Boolean {
    var i = 0
    while (i < str.length) {
        val cp = codePointAt(str, i)
        if (cp == '_'.code ||
            cp in 0x24b6..0x24e9 ||
            alphanumeric.longCharacterIsMember(cp.toUInt())
        ) {
            return true
        }
        i += codePointSize(cp)
    }
    return false
}
