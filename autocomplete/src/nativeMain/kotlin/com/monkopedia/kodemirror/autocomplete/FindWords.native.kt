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
package com.monkopedia.kodemirror.autocomplete

import platform.Foundation.NSCharacterSet

private val alphanumeric = NSCharacterSet.alphanumericCharacterSet

// Foundation's L/M/N set by code point, as :state's hasWordChar: a slight superset of
// `\p{Alphabetic}\p{Number}` (it also counts non-Alphabetic marks such as viramas), plus the
// enclosed letters, which are Alphabetic but category So. Keep the ranges in step with
// :state's CharCategory.native.kt.
internal actual fun findWords(text: String, wordChars: String): List<IntRange> =
    wordRuns(text, wordChars) { cp ->
        isEnclosedLetter(cp) || alphanumeric.longCharacterIsMember(cp.toUInt())
    }

// Circled, squared, negative circled and negative squared Latin letters.
private fun isEnclosedLetter(cp: Int): Boolean = cp in 0x24b6..0x24e9 ||
    cp in 0x1f130..0x1f149 ||
    cp in 0x1f150..0x1f169 ||
    cp in 0x1f170..0x1f189
