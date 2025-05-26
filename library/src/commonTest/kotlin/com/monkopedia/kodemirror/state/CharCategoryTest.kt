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
