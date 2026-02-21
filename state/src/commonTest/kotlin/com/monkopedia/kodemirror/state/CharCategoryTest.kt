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

// EditorState char categorizer
class CharCategoryTest {

    fun mk(vararg extensions: Extension): EditorState {
        return EditorState.create(extensions = extensions.toList().extension)
    }

    @Test
    fun categorises_into_alphanumeric() {
        val st = mk()
        assertEquals(st.charCategorizer(0)("1"), CharCategory.Word)
        assertEquals(st.charCategorizer(0)("a"), CharCategory.Word)
    }

    @Test
    fun categorises_into_whitespace() {
        val st = mk()
        assertEquals(st.charCategorizer(0)(" "), CharCategory.Space)
    }

    @Test
    fun categorises_into_other() {
        val st = mk()
        assertEquals(st.charCategorizer(0)("/"), CharCategory.Other)
        assertEquals(st.charCategorizer(0)("<"), CharCategory.Other)
    }
}
