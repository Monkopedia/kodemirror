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
package com.monkopedia.kodemirror.legacy.modes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The JavaScript mode's language data, matching `@codemirror/legacy-modes` 6.5.2
 * `mode/javascript.js` (#348).
 */
class JavaScriptLanguageDataTest {
    @Test
    fun publishesUpstreamLanguageData() {
        val data = javaScriptLegacy.languageData
        assertEquals("$", data["wordChars"])
        assertEquals(
            mapOf("brackets" to listOf("(", "[", "{", "'", "\"", "`")),
            data["closeBrackets"]
        )
        assertEquals(
            mapOf("line" to "//", "block" to mapOf("open" to "/*", "close" to "*/")),
            data["commentTokens"]
        )
    }

    @Test
    fun indentOnInputMatchesUpstreamTriggers() {
        val rule = javaScriptLegacy.languageData["indentOnInput"] as Regex
        // Expected results from upstream's regex on the same lines.
        val expected = mapOf(
            "  }" to true,
            "{" to true,
            "    case 1:" to true,
            "default:" to true,
            "  case \"a\": " to false,
            "  x }" to false,
            "  case 1" to false,
            "  cases:" to false,
            "\t}" to true,
            "  case :" to true
        )
        assertEquals(expected, expected.keys.associateWith { rule.containsMatchIn(it) })
    }

    @Test
    fun jsonModeHasNoCommentTokens() {
        assertFalse("commentTokens" in json.languageData)
        assertTrue("indentOnInput" in json.languageData)
        assertEquals("$", json.languageData["wordChars"])
    }
}
