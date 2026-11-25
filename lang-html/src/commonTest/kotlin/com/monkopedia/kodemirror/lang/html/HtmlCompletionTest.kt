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
package com.monkopedia.kodemirror.lang.html

import com.monkopedia.kodemirror.autocomplete.CompletionContext
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Expected labels come from upstream `@codemirror/lang-html` on the same input (#448). */
class HtmlCompletionTest {
    private fun labels(doc: String, explicit: Boolean = false): List<String> {
        val state = EditorState.create(
            EditorStateConfig(doc = doc.asDoc(), extensions = htmlLanguage.extension)
        )
        val result = htmlCompletionSource(CompletionContext(state, DocPos(doc.length), explicit))
        return assertNotNull(result).options.map { it.label }.sorted()
    }

    @Test
    fun closeTagCompletesTheOpenElement() {
        assertEquals(listOf("div"), labels("<div></"))
    }

    @Test
    fun childTagsAreLimitedToTheParentsAllowedChildren() {
        assertEquals(
            listOf("</ul>", "<li", "<ol", "<script", "<template", "<ul"),
            labels("<ul>", true)
        )
    }

    @Test
    fun attributeValuesComeFromTheTagsAttributeSpec() {
        val values = labels("<input type=")
        assertEquals(23, values.size)
        assertTrue("text" in values)
    }
}
