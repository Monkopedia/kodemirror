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
package com.monkopedia.kodemirror.lang.xml

import com.monkopedia.kodemirror.language.matchBrackets
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tags match through their `bracketMatchingHandle`, the tag name. Expected values come from
 * @codemirror/lang-xml run on the same input (#450).
 */
class XmlBracketMatchingTest {
    private fun match(doc: String, pos: Int, dir: Int): String? {
        val state = EditorState.create(
            EditorStateConfig(doc = doc.asDoc(), extensions = xml().extension)
        )
        return matchBrackets(state, DocPos(pos), dir)?.let {
            "${it.start.from.value}-${it.start.to.value}>" +
                (it.end?.let { e -> "${e.from.value}-${e.to.value}" } ?: "none") + ":${it.matched}"
        }
    }

    @Test
    fun tagNamesAreTheBracketHandles() {
        assertEquals("1-2>6-7:true", match("<a>x</a>", 1, 1))
        assertEquals(null, match("<a>x</a>", 2, 1))
        assertEquals("6-7>1-2:true", match("<a>x</a>", 7, -1))
    }
}
