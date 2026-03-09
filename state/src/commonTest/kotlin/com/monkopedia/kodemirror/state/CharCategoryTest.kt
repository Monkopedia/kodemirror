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

import kotlin.test.Test
import kotlin.test.assertEquals

class CharCategoryTest {

    private fun mk(vararg extensions: Extension): EditorState = EditorState.create(
        EditorStateConfig(
            extensions = if (extensions.isEmpty()) {
                null
            } else {
                ExtensionList(extensions.toList())
            }
        )
    )

    @Test
    fun categorisesIntoAlphanumeric() {
        val st = mk()
        assertEquals(CharCategory.Word, st.charCategorizer(DocPos.ZERO)("1"))
        assertEquals(CharCategory.Word, st.charCategorizer(DocPos.ZERO)("a"))
    }

    @Test
    fun categorisesIntoWhitespace() {
        val st = mk()
        assertEquals(CharCategory.Space, st.charCategorizer(DocPos.ZERO)(" "))
    }

    @Test
    fun categorisesIntoOther() {
        val st = mk()
        assertEquals(CharCategory.Other, st.charCategorizer(DocPos.ZERO)("/"))
        assertEquals(CharCategory.Other, st.charCategorizer(DocPos.ZERO)("<"))
    }
}
