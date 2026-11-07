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

// Upstream's own pattern and escaping, run by the JS engine over the whole text in one call.
// Returns the runs' bounds as "start,end,start,end...".
@Suppress("UNUSED_PARAMETER")
private fun findWordsJs(text: String, wordChars: String): String = js(
    """{
        const re = new RegExp(
            "[\\p{Alphabetic}\\p{Number}_" + wordChars.replace(/[\]\-\\]/g, "\\$&") + "]+",
            "ug"
        );
        const out = [];
        for (const m of text.matchAll(re)) out.push(m.index, m.index + m[0].length);
        return out.join(",");
    }"""
)

internal actual fun findWords(text: String, wordChars: String): List<IntRange> {
    // Parsed by hand: splitting allocates a string per bound, which costs more than the scan.
    val runs = mutableListOf<IntRange>()
    var start = -1
    var n = 0
    for (c in findWordsJs(text, wordChars) + ",") {
        if (c != ',') {
            n = n * 10 + (c - '0')
        } else if (start < 0) {
            start = n
            n = 0
        } else {
            runs += start until n
            start = -1
            n = 0
        }
    }
    return runs
}
