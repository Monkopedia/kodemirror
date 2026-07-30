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
package com.monkopedia.kodemirror.vim

import kotlin.test.Test
import kotlin.test.assertEquals

class VimTextObjectTest {

    @Test
    fun diw_deletes_inner_word() = testVim(value = "foo bar baz", cursor = LinePos(0, 5)) { h ->
        h.doKeys("d", "i", "w")
        assertEquals("foo  baz", h.cm.getValue())
    }

    @Test
    fun daw_deletes_a_word() = testVim(value = "foo bar baz", cursor = LinePos(0, 4)) { h ->
        h.doKeys("d", "a", "w")
        assertEquals("foo baz", h.cm.getValue())
    }

    @Test
    fun di_paren_deletes_inner_parens() =
        testVim(value = "foo (bar baz) qux", cursor = LinePos(0, 6)) { h ->
            h.doKeys("d", "i", "(")
            assertEquals("foo () qux", h.cm.getValue())
        }

    @Test
    fun da_paren_deletes_including_parens() =
        testVim(value = "foo (bar baz) qux", cursor = LinePos(0, 6)) { h ->
            h.doKeys("d", "a", "(")
            assertEquals("foo  qux", h.cm.getValue())
        }

    @Test
    fun di_quote_deletes_inner_quotes() =
        testVim(value = """foo "bar baz" qux""", cursor = LinePos(0, 6)) { h ->
            h.doKeys("d", "i", "\"")
            assertEquals("""foo "" qux""", h.cm.getValue())
        }

    @Test
    fun da_quote_deletes_including_quotes() =
        testVim(value = """foo "bar baz" qux""", cursor = LinePos(0, 6)) { h ->
            h.doKeys("d", "a", "\"")
            assertEquals("foo  qux", h.cm.getValue())
        }

    @Test
    fun di_brace_deletes_inner_braces() =
        testVim(value = "foo {bar baz} qux", cursor = LinePos(0, 6)) { h ->
            h.doKeys("d", "i", "{")
            assertEquals("foo {} qux", h.cm.getValue())
        }

    @Test
    fun di_bracket_deletes_inner_brackets() =
        testVim(value = "foo [bar baz] qux", cursor = LinePos(0, 6)) { h ->
            h.doKeys("d", "i", "[")
            assertEquals("foo [] qux", h.cm.getValue())
        }
}
