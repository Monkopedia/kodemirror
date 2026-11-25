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
package com.monkopedia.kodemirror.language

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.CharCategory
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.LanguageDataKey
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.allowMultipleSelections
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.asInsert
import com.monkopedia.kodemirror.state.languageData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Language data publication and the `indentOnInput` filter (#449). Expected values come from
 * @codemirror/language 6.12.2 run on the same input.
 */
class LanguageDataTest {
    private val dataLanguage = StreamLanguage.define(object : StreamParser<Unit> {
        override val name = "data"
        override fun startState(indentUnit: Int) = Unit
        override fun copyState(state: Unit) = Unit
        override fun token(stream: StringStream, state: Unit): String? {
            stream.next()
            return null
        }
        override val languageData: Map<String, Any> = mapOf(
            "wordChars" to "$",
            "indentOnInput" to Regex("^\\s*end$"),
            "commentTokens" to mapOf("line" to "#")
        )
    })

    @Test
    fun streamLanguagePublishesItsLanguageData() {
        val state = EditorState.create(
            EditorStateConfig(doc = "abc".asDoc(), extensions = dataLanguage.extension)
        )
        assertEquals(listOf("$"), state.languageDataAt(LanguageDataKey.WORD_CHARS, DocPos(1)))
        assertEquals(1, state.languageDataAt(LanguageDataKey.INDENT_ON_INPUT, DocPos(1)).size)
        assertEquals(
            listOf(mapOf("line" to "#")),
            state.languageDataAt(LanguageDataKey.COMMENT_TOKENS, DocPos(1))
        )
        assertEquals(CharCategory.Word, state.charCategorizer(DocPos(1))("$"))
    }

    @Test
    fun dataAddedThroughTheFacetComesBeforeTheBaseData() {
        val state = EditorState.create(
            EditorStateConfig(
                doc = "abc".asDoc(),
                extensions = ExtensionList(
                    listOf(dataLanguage.extension, dataLanguage.data.of(mapOf("wordChars" to "x")))
                )
            )
        )
        assertEquals(listOf("x", "$"), state.languageDataAt(LanguageDataKey.WORD_CHARS, DocPos(1)))
        assertEquals(CharCategory.Other, state.charCategorizer(DocPos(1))("$"))
    }

    // An indent service putting `}` lines at column 0 and `x` lines at column 4.
    private val service = indentService.of { cx, pos ->
        val text = cx.state.doc.lineAt(pos).text.trim()
        when {
            text.startsWith("}") -> 0
            text == "x" -> 4
            else -> null
        }
    }

    private fun type(
        doc: String,
        at: Int,
        text: String,
        rule: Regex = Regex("^\\s*\\}$"),
        userEvent: String = "input.type"
    ): Pair<String, Int> {
        val rules = languageData.of { _, _, _ -> listOf(mapOf("indentOnInput" to rule)) }
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                extensions = ExtensionList(listOf<Extension>(indentOnInput, service, rules))
            )
        )
        val next = state.update(
            TransactionSpec(
                changes = ChangeSpec.Single(DocPos(at), DocPos(at), text.asInsert()),
                selection = SelectionSpec.CursorSpec(DocPos(at + text.length)),
                userEvent = userEvent
            )
        ).state
        return next.doc.toString() to next.selection.main.head.value
    }

    @Test
    fun reindentKeepsTheTypedCharacter() {
        assertEquals("a {\n  x\n}\nfoo()" to 9, type("a {\n  x\n  \nfoo()", 10, "}"))
    }

    @Test
    fun reindentAtTheDocumentEnd() {
        assertEquals("a {\n  x\n}" to 9, type("a {\n  x\n  ", 10, "}"))
    }

    @Test
    fun completionInputAlsoReindents() {
        assertEquals(
            "a {\n  x\n}" to 9,
            type("a {\n  x\n  ", 10, "}", userEvent = "input.complete")
        )
        assertEquals(
            "a {\n  x\n  }" to 11,
            type("a {\n  x\n  ", 10, "}", userEvent = "input.paste")
        )
    }

    @Test
    fun reindentNormalizesTheIndentString() {
        assertEquals("a {\n    x" to 9, type("a {\n\t", 5, "x", rule = Regex("^\\s*x$")))
    }

    @Test
    fun reindentUpToColumn200() {
        val spaces = " ".repeat(199)
        assertEquals("a {\n}" to 5, type("a {\n$spaces", 203, "}"))
    }

    @Test
    fun noReindentBeyondColumn200() {
        val spaces = " ".repeat(201)
        assertEquals("a {\n$spaces}" to 206, type("a {\n$spaces", 205, "}"))
    }

    @Test
    fun reindentEveryCursorLineAfterTheInput() {
        val rules = languageData.of { _, _, _ ->
            listOf(mapOf("indentOnInput" to Regex("^\\s*\\}$")))
        }
        val state = EditorState.create(
            EditorStateConfig(
                doc = "a {\n  \n  ".asDoc(),
                selection = SelectionSpec.EditorSelectionSpec(
                    EditorSelection.create(
                        listOf(
                            EditorSelection.cursor(DocPos(6)),
                            EditorSelection.cursor(DocPos(9))
                        ),
                        1
                    )
                ),
                extensions = ExtensionList(
                    listOf(indentOnInput, service, rules, allowMultipleSelections.of(true))
                )
            )
        )
        // Both positions refer to the typed document, so the second line's edit must be
        // applied after the input rather than to the original document.
        val typed = ChangeSpec.Multi(
            listOf(
                ChangeSpec.Single(DocPos(6), DocPos(6), "}".asInsert()),
                ChangeSpec.Single(DocPos(9), DocPos(9), "}".asInsert())
            )
        )
        val next = state.update(
            TransactionSpec(
                changes = typed,
                selection = SelectionSpec.EditorSelectionSpec(
                    EditorSelection.create(
                        listOf(
                            EditorSelection.cursor(DocPos(7)),
                            EditorSelection.cursor(DocPos(11))
                        ),
                        1
                    )
                ),
                userEvent = "input.type"
            )
        ).state
        assertEquals("a {\n}\n}", next.doc.toString())
        assertEquals(listOf(5, 7), next.selection.ranges.map { it.head.value })
    }

    @Test
    fun reindentKeepsTheInputsEffectsAnnotationsAndScroll() {
        val marker = StateEffect.define<Int>()
        val rules = languageData.of { _, _, _ ->
            listOf(mapOf("indentOnInput" to Regex("^\\s*\\}")))
        }
        val state = EditorState.create(
            EditorStateConfig(
                doc = "a {\n  ".asDoc(),
                extensions = ExtensionList(listOf(indentOnInput, service, rules))
            )
        )
        val tr = state.update(
            TransactionSpec(
                changes = ChangeSpec.Single(DocPos(6), DocPos(6), "}".asInsert()),
                selection = SelectionSpec.CursorSpec(DocPos(7)),
                effects = listOf(marker.of(7)),
                userEvent = "input.type",
                scrollIntoView = true
            )
        )
        assertEquals("a {\n}", tr.state.doc.toString())
        assertTrue(tr.isUserEvent("input.type"))
        assertTrue(tr.effects.any { it.asType(marker) != null })
        assertTrue(tr.scrollIntoView)
    }

    @Test
    fun rulesAreReadAtTheHeadBeforeTheInput() {
        // This provider only answers up to position 6, where the cursor was before typing.
        val rules = languageData.of { _, pos, _ ->
            if (pos.value <= 6) listOf(mapOf("indentOnInput" to Regex("^\\s*\\}"))) else emptyList()
        }
        val state = EditorState.create(
            EditorStateConfig(
                doc = "a {\n  ".asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(6)),
                extensions = ExtensionList(listOf(indentOnInput, service, rules))
            )
        )
        val next = state.update(
            TransactionSpec(
                changes = ChangeSpec.Single(DocPos(6), DocPos(6), "}".asInsert()),
                selection = SelectionSpec.CursorSpec(DocPos(7)),
                userEvent = "input.type"
            )
        ).state
        assertEquals("a {\n}", next.doc.toString())
    }
}
