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
package com.monkopedia.kodemirror.lsp

import com.monkopedia.kodemirror.autocomplete.CompletionContext
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.lsp.CompletionItem
import com.monkopedia.lsp.CompletionItemKind
import com.monkopedia.lsp.CompletionItemLabelDetails
import com.monkopedia.lsp.CompletionList
import com.monkopedia.lsp.InsertReplaceEdit
import com.monkopedia.lsp.InsertTextFormat
import com.monkopedia.lsp.MarkupContent
import com.monkopedia.lsp.MarkupKind
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.StringOr
import com.monkopedia.lsp.TextDocumentCompletionResult
import com.monkopedia.lsp.TextEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerCompletionTest {
    private fun doc(vararg lines: String): Text = Text.of(lines.toList())

    private fun completionContext(text: String, pos: Int): CompletionContext =
        CompletionContext(EditorState.create(text), DocPos(pos), explicit = false)

    // ── kind → type mapping ──

    @Test
    fun kindMapsToType() {
        assertEquals("function", kindToType(CompletionItemKind.FUNCTION))
        assertEquals("method", kindToType(CompletionItemKind.METHOD))
        assertEquals("class", kindToType(CompletionItemKind.CLASS))
        assertEquals("class", kindToType(CompletionItemKind.CONSTRUCTOR))
        assertEquals("class", kindToType(CompletionItemKind.STRUCT))
        assertEquals("property", kindToType(CompletionItemKind.FIELD))
        assertEquals("property", kindToType(CompletionItemKind.PROPERTY))
        assertEquals("variable", kindToType(CompletionItemKind.VARIABLE))
        assertEquals("interface", kindToType(CompletionItemKind.INTERFACE))
        assertEquals("namespace", kindToType(CompletionItemKind.MODULE))
        assertEquals("keyword", kindToType(CompletionItemKind.KEYWORD))
        assertEquals("keyword", kindToType(CompletionItemKind.UNIT))
        assertEquals("constant", kindToType(CompletionItemKind.CONSTANT))
        assertEquals("constant", kindToType(CompletionItemKind.ENUM))
        assertEquals("constant", kindToType(CompletionItemKind.ENUM_MEMBER))
        assertEquals("constant", kindToType(CompletionItemKind.VALUE))
        assertEquals("constant", kindToType(CompletionItemKind.COLOR))
        assertEquals("type", kindToType(CompletionItemKind.TYPE_PARAMETER))
        assertEquals("text", kindToType(CompletionItemKind.TEXT))
    }

    @Test
    fun unmappedKindsAreNull() {
        assertNull(kindToType(null))
        assertNull(kindToType(CompletionItemKind.SNIPPET))
        assertNull(kindToType(CompletionItemKind.FILE))
        assertNull(kindToType(CompletionItemKind.FOLDER))
        assertNull(kindToType(CompletionItemKind.OPERATOR))
        assertNull(kindToType(CompletionItemKind.EVENT))
        assertNull(kindToType(CompletionItemKind.REFERENCE))
    }

    // ── insertion text precedence ──

    @Test
    fun insertionTextPrefersTextEdit() {
        val item = CompletionItem(
            label = "label",
            insertText = "insert",
            textEditText = "editText",
            textEdit = TextEdit(
                range = Range(Position(0u, 0u), Position(0u, 1u)),
                newText = "fromEdit"
            )
        )
        assertEquals("fromEdit", item.insertionText())
    }

    @Test
    fun insertionTextFallsBackThroughChain() {
        assertEquals(
            "editText",
            CompletionItem(label = "l", insertText = "i", textEditText = "editText").insertionText()
        )
        assertEquals(
            "i",
            CompletionItem(label = "l", insertText = "i").insertionText()
        )
        assertEquals(
            "l",
            CompletionItem(label = "l").insertionText()
        )
    }

    // ── plain-text completion mapping ──

    @Test
    fun plainTextMappingUsesInsertionTextAsLabel() {
        val item = CompletionItem(
            label = "println",
            insertText = "println",
            kind = CompletionItemKind.FUNCTION,
            detail = "fun println(): Unit",
            sortText = "0001"
        )
        val c = mapCompletionItem(item, doc("x"))
        assertEquals("println", c.label)
        assertEquals("function", c.type)
        assertEquals("fun println(): Unit", c.detail)
        // Plain text with no extra edits => default apply (no custom applyFn).
        assertNull(c.applyFn)
    }

    @Test
    fun documentationStringBecomesInfo() {
        val plain = mapCompletionItem(
            CompletionItem(label = "a", documentation = StringOr.StringValue("hello docs")),
            doc("x")
        )
        assertEquals("hello docs", plain.info)

        val markup = mapCompletionItem(
            CompletionItem(
                label = "b",
                documentation = StringOr.Value(
                    MarkupContent(kind = MarkupKind.MARKDOWN, value = "# heading")
                )
            ),
            doc("x")
        )
        assertEquals("# heading", markup.info)

        val none = mapCompletionItem(CompletionItem(label = "c"), doc("x"))
        assertNull(none.info)
    }

    @Test
    fun emptyDocumentationIsNull() {
        val c = mapCompletionItem(
            CompletionItem(label = "a", documentation = StringOr.StringValue("")),
            doc("x")
        )
        assertNull(c.info)
    }

    // ── snippet completion mapping ──

    @Test
    fun snippetMappingUsesLabelAndInstallsApplyFn() {
        val item = CompletionItem(
            label = "for",
            insertText = "for (\${1:item} in \${2:items}) {\n\t\$0\n}",
            insertTextFormat = InsertTextFormat.SNIPPET,
            kind = CompletionItemKind.SNIPPET
        )
        val c = mapCompletionItem(item, doc("x"))
        // Display label is the item label, not the (expanded) snippet text.
        assertEquals("for", c.label)
        assertNotNull(c.applyFn)
    }

    // ── snippet template translation ──

    @Test
    fun lspSnippetTranslatesNumberedStops() {
        assertEquals("\${1}", lspSnippetToTemplate("\$1"))
        assertEquals("\${0}", lspSnippetToTemplate("\$0"))
        assertEquals(
            "foo(\${1}, \${2})",
            lspSnippetToTemplate("foo(\$1, \$2)")
        )
    }

    @Test
    fun lspSnippetTranslatesPlaceholderStops() {
        // Placeholder default text is dropped (deviation), only the name kept.
        assertEquals(
            "for (\${1} in \${2}) {}",
            lspSnippetToTemplate("for (\${1:item} in \${2:items}) {}")
        )
    }

    @Test
    fun lspSnippetTranslatesChoiceStops() {
        assertEquals("\${1}", lspSnippetToTemplate("\${1|a,b,c|}"))
    }

    @Test
    fun lspSnippetUnescapesBackslashes() {
        // \$ should become a literal $, not a tab stop.
        assertEquals("price: \$5", lspSnippetToTemplate("price: \\\$5"))
        assertEquals("a}b", lspSnippetToTemplate("a\\}b"))
    }

    @Test
    fun lspSnippetLeavesPlainTextUntouched() {
        assertEquals("println()", lspSnippetToTemplate("println()"))
    }

    // ── result range resolution ──

    @Test
    fun resultRangeFromTextEditRange() {
        // doc "foo bar", cursor after "ba" (offset 6); textEdit covers "bar".
        val item = CompletionItem(
            label = "bar",
            textEdit = TextEdit(
                range = Range(Position(0u, 4u), Position(0u, 7u)),
                newText = "bar"
            )
        )
        val ctx = completionContext("foo bar", 6)
        val (from, to) = completionResultRange(ctx, listOf(item))
        assertEquals(4, from.value)
        assertEquals(7, to.value)
    }

    @Test
    fun resultRangeFromInsertReplaceEditUsesReplace() {
        val item = CompletionItem(
            label = "bar",
            textEdit = InsertReplaceEdit(
                newText = "bar",
                insert = Range(Position(0u, 4u), Position(0u, 6u)),
                replace = Range(Position(0u, 4u), Position(0u, 7u))
            )
        )
        val ctx = completionContext("foo bar", 6)
        val (from, to) = completionResultRange(ctx, listOf(item))
        assertEquals(4, from.value)
        assertEquals(7, to.value)
    }

    @Test
    fun resultRangeFallsBackToWordAtCursor() {
        val item = CompletionItem(label = "barbecue")
        // doc "foo bar", cursor inside "bar" at offset 6 -> word [4,7).
        val ctx = completionContext("foo bar", 6)
        val (from, to) = completionResultRange(ctx, listOf(item))
        assertEquals(4, from.value)
        assertEquals(7, to.value)
    }

    @Test
    fun resultRangeEmptyItemsIsCursor() {
        val ctx = completionContext("foo bar", 6)
        val (from, to) = completionResultRange(ctx, emptyList())
        assertEquals(6, from.value)
        assertEquals(6, to.value)
    }

    // ── prefix regexp / validFor ──

    @Test
    fun prefixRegexpWordOnly() {
        val re = prefixRegexp(listOf(CompletionItem(label = "foo"), CompletionItem(label = "bar")))
        assertTrue(re.matches("foo"))
        assertTrue(re.matches(""))
        assertTrue(!re.matches(".foo"))
    }

    @Test
    fun prefixRegexpIncludesNonWordPrefixes() {
        val re = prefixRegexp(
            listOf(
                CompletionItem(label = "@foo"),
                CompletionItem(label = "bar")
            )
        )
        assertTrue(re.matches("@foo"))
        assertTrue(re.matches("@"))
        assertTrue(re.matches("plain"))
    }

    @Test
    fun prefixRegexpEmptyItems() {
        val re = prefixRegexp(emptyList())
        assertTrue(re.matches("anything123"))
        assertTrue(!re.matches("has space"))
    }

    // ── completion list result parsing ──

    @Test
    fun parseNullResult() {
        assertNull(parseCompletionResult(null))
    }

    @Test
    fun parseArrayResult() {
        val result = TextDocumentCompletionResult.CompletionItemArray(
            listOf(CompletionItem(label = "a"), CompletionItem(label = "b"))
        )
        val list = parseCompletionResult(result)
        assertNotNull(list)
        assertEquals(false, list.isIncomplete)
        assertEquals(2, list.items.size)
        assertEquals("a", list.items[0].label)
    }

    @Test
    fun parseCompletionListResult() {
        val result = TextDocumentCompletionResult.CompletionListValue(
            CompletionList(isIncomplete = true, items = listOf(CompletionItem(label = "x")))
        )
        val list = parseCompletionResult(result)
        assertNotNull(list)
        assertTrue(list.isIncomplete)
        assertEquals(1, list.items.size)
    }

    // ── labelDetails carried as detail when item.detail absent ──

    @Test
    fun detailFromItemDetail() {
        val c = mapCompletionItem(
            CompletionItem(
                label = "foo",
                detail = "Int",
                labelDetails = CompletionItemLabelDetails(detail = ": String")
            ),
            doc("x")
        )
        assertEquals("Int", c.detail)
    }
}
