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

import java.lang.Character.DECIMAL_DIGIT_NUMBER
import java.lang.Character.LETTER_NUMBER
import java.lang.Character.OTHER_NUMBER

// Upstream's `[\p{Alphabetic}\p{Number}_]`, by code point. `\p{Alphabetic}` does not compile in
// java.util.regex, and Character avoids depending on how Android's ICU-backed regex spells it.
internal actual fun hasWordChar(str: String): Boolean {
    var i = 0
    while (i < str.length) {
        val cp = Character.codePointAt(str, i)
        if (cp == '_'.code || Character.isAlphabetic(cp)) return true
        when (Character.getType(cp).toByte()) {
            DECIMAL_DIGIT_NUMBER, LETTER_NUMBER, OTHER_NUMBER -> return true
        }
        i += Character.charCount(cp)
    }
    return false
}
