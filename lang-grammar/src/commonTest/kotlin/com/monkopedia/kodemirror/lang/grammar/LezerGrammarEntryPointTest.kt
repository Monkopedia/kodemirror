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
package com.monkopedia.kodemirror.lang.grammar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every public entry point of this module used to throw before doing any work: the highlight
 * spec at `LezerHighlight.kt` wrote the operator selector as `"!" ~ * + ? |`, and the unquoted
 * `*` is a `styleTags` wildcard whose empty path piece `styleTags` rejects — so the static
 * initializer of [lezerHighlighting] blew up, and with it [lezerParser], which references it
 * (#367). These tests exist because each entry point reached the failure independently.
 */
class LezerGrammarEntryPointTest {
    @Test
    fun highlightingInitializes() {
        assertTrue(lezerHighlighting.toString().isNotEmpty())
    }

    @Test
    fun parserInitializes() {
        assertEquals("Grammar", lezerParser.topNode.name)
    }

    @Test
    fun languageInitializes() {
        assertEquals("lezer", lezerGrammarLanguage.name)
    }

    @Test
    fun languageSupportInitializes() {
        assertEquals("lezer", lezerGrammar().language.name)
    }
}
