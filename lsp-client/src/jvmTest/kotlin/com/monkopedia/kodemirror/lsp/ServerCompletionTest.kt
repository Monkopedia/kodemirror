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

import com.monkopedia.kodemirror.autocomplete.Completion
import com.monkopedia.kodemirror.autocomplete.CompletionApplyContext
import com.monkopedia.kodemirror.autocomplete.CompletionContext
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.lsp.CompletionItem
import com.monkopedia.lsp.CompletionItemKind
import com.monkopedia.lsp.CompletionItemLabelDetails
import com.monkopedia.lsp.CompletionList
import com.monkopedia.lsp.CompletionOptions
import com.monkopedia.lsp.InsertReplaceEdit
import com.monkopedia.lsp.InsertTextFormat
import com.monkopedia.lsp.LSPAny
import com.monkopedia.lsp.MarkupContent
import com.monkopedia.lsp.MarkupKind
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.StringOr
import com.monkopedia.lsp.TextDocumentCompletionResult
import com.monkopedia.lsp.TextEdit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    @Test
    fun isIncompleteResultStillGetsValidFor() = runBlocking {
        // #114 regression: kotlin-lsp marks EVERY member list isIncomplete=true. The
        // source must still set validFor so the client filters the returned items by
        // the typed prefix — gating validFor on isIncomplete left the list unfiltered
        // (and the accept-range stale → "`.xplus`"). The bug was a literal
        // `if (result.isIncomplete) null else ...`.
        val fixture = TestLanguageServer(
            capabilities = ServerCapabilities(completionProvider = CompletionOptions()),
            responses = mapOf(
                "textDocumentCompletion" to TextDocumentCompletionResult.CompletionListValue(
                    CompletionList(
                        isIncomplete = true,
                        items = listOf(
                            CompletionItem(label = "plus"),
                            CompletionItem(label = "minus")
                        )
                    )
                )
            )
        )
        val client = LSPClient(fixture.server)
        client.initialize()
        val source = serverCompletionSource(client, "inmemory://t.kt")
        // explicit = true skips the trigger-character gate so we reach the result.
        val result = source(CompletionContext(EditorState.create("x."), DocPos(2), explicit = true))
        assertNotNull(result)
        assertNotNull(result.validFor)
        // A word prefix matches → the client narrows the list as the user types.
        assertTrue(result.validFor!!.matches("pl"))
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

    // ── lazy completion: completionItem/resolve (#112) ──

    /** An [LSPAny] `data` payload, the canonical signal that an item is lazy. */
    private fun dataPayload(id: String): LSPAny = JsonObject(mapOf("id" to JsonPrimitive(id)))

    @Test
    fun needsResolveDetection() {
        // Lazy item: empty textEdit.newText + a data payload → needs resolve.
        assertTrue(
            CompletionItem(
                label = "transform",
                textEdit = TextEdit(
                    range = Range(Position(0u, 0u), Position(0u, 0u)),
                    newText = ""
                ),
                data = dataPayload("1")
            ).needsResolve()
        )
        // Data present even with concrete insertion → still resolve (auto-imports).
        assertTrue(
            CompletionItem(label = "x", insertText = "x", data = dataPayload("2")).needsResolve()
        )
        // No insertion and no edits, no data → still worth resolving.
        assertTrue(CompletionItem(label = "y", insertText = "").needsResolve())
        // Fully-formed item (real insertion, no data) → no resolve.
        assertTrue(!CompletionItem(label = "z", insertText = "z").needsResolve())
        // Item already carrying additionalTextEdits and insertion, no data → no resolve.
        assertTrue(
            !CompletionItem(
                label = "w",
                insertText = "w",
                additionalTextEdits = listOf(
                    TextEdit(range = Range(Position(0u, 0u), Position(0u, 0u)), newText = "import x\n")
                )
            ).needsResolve()
        )
    }

    @Test
    fun lazyApplyResolvesAndAppliesInsertionPlusAutoImport() = runBlocking {
        // Server: completion returns a lazy item (empty newText + data); resolve
        // returns the real insertion AND an auto-import additionalTextEdit at the top.
        val lazyItem = CompletionItem(
            label = "transform",
            kind = CompletionItemKind.FUNCTION,
            textEdit = TextEdit(
                range = Range(Position(2u, 0u), Position(2u, 0u)),
                newText = ""
            ),
            data = dataPayload("transform")
        )
        val resolvedItem = lazyItem.copy(
            textEdit = TextEdit(
                range = Range(Position(2u, 0u), Position(2u, 9u)),
                newText = "transform"
            ),
            additionalTextEdits = listOf(
                TextEdit(
                    range = Range(Position(0u, 0u), Position(0u, 0u)),
                    newText = "import foo.transform\n"
                )
            )
        )
        val fixture = TestLanguageServer(
            capabilities = ServerCapabilities(
                completionProvider = CompletionOptions(resolveProvider = true)
            ),
            responses = mapOf(
                "textDocumentCompletion" to TextDocumentCompletionResult.CompletionListValue(
                    CompletionList(isIncomplete = false, items = listOf(lazyItem))
                ),
                "completionItemResolve" to resolvedItem
            )
        )
        val client = LSPClient(fixture.server)
        client.initialize()

        // Cursor at end (no trailing newline) so the assertions are exact.
        val docText = "package p\n\ntrans"
        val cursor = docText.length
        val completion = mapCompletionItem(lazyItem, doc(docText), client, resolveProvider = true)
        // Lazy → custom applyFn that resolves on apply.
        assertNotNull(completion.applyFn)

        val session = recordingSession(docText)
        // Apply replacing the typed prefix "trans" on line 2 with the resolved text.
        completion.applyFn!!(
            CompletionApplyContext(session, completion, DocPos(11), DocPos(cursor))
        )

        assertTrue(
            "completionItemResolve" in fixture.calls,
            "apply must trigger completionItem/resolve"
        )
        // BOTH the resolved insertion AND the auto-import landed, in one document.
        val text = session.state.doc.toString()
        assertEquals("import foo.transform\npackage p\n\ntransform", text)
        // Cursor sits just after the inserted "transform", shifted forward by the
        // earlier auto-import insertion.
        assertEquals(text.length, session.state.selection.main.head.value)
    }

    @Test
    fun noResolveWhenServerLacksResolveProvider() = runBlocking {
        val lazyItem = CompletionItem(
            label = "transform",
            textEdit = TextEdit(
                range = Range(Position(0u, 0u), Position(0u, 0u)),
                newText = ""
            ),
            data = dataPayload("transform")
        )
        val fixture = TestLanguageServer(
            // resolveProvider omitted (null) → no resolve support.
            capabilities = ServerCapabilities(completionProvider = CompletionOptions()),
            responses = mapOf("completionItemResolve" to lazyItem)
        )
        val client = LSPClient(fixture.server)
        client.initialize()

        val completion = mapCompletionItem(lazyItem, doc("x"), client, resolveProvider = false)
        // No resolve support → eager mapping, no custom applyFn for a plain item.
        assertNull(completion.applyFn)

        val session = recordingSession("x")
        applyDefault(session, completion, DocPos(1), DocPos(1))
        assertTrue(
            "completionItemResolve" !in fixture.calls,
            "must not resolve when the server has no resolveProvider"
        )
    }

    @Test
    fun noResolveWhenItemAlreadyComplete() = runBlocking {
        // resolveProvider = true, but the item is fully formed (real insertion,
        // no data) → no resolve, eager apply.
        val completeItem = CompletionItem(label = "println", insertText = "println")
        val fixture = TestLanguageServer(
            capabilities = ServerCapabilities(
                completionProvider = CompletionOptions(resolveProvider = true)
            ),
            responses = mapOf("completionItemResolve" to completeItem)
        )
        val client = LSPClient(fixture.server)
        client.initialize()

        val completion = mapCompletionItem(completeItem, doc("x"), client, resolveProvider = true)
        assertNull(completion.applyFn)

        val session = recordingSession("x")
        applyDefault(session, completion, DocPos(0), DocPos(0))
        assertTrue("completionItemResolve" !in fixture.calls)
    }

    // ── test session plumbing ──

    /**
     * Mimic the framework's default (no-applyFn) apply so the "no resolve" tests
     * exercise the same path the real editor would.
     */
    private fun applyDefault(
        session: EditorSession,
        completion: Completion,
        from: DocPos,
        to: DocPos
    ) {
        val text = completion.apply ?: completion.label
        session.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from,
                    to,
                    InsertContent.StringContent(text)
                )
            )
        )
    }

    /**
     * An [EditorSession] that delegates [dispatch]/[state] to a real headless
     * session but supplies an eager [coroutineScope] (the real one errors unless
     * attached to a composable). [Dispatchers.Unconfined] runs the launched
     * resolve-then-apply synchronously since the stub resolves immediately.
     */
    private fun recordingSession(doc: String): EditorSession =
        DelegatingSession(EditorState.create(doc))

    private class DelegatingSession(
        initial: EditorState
    ) : EditorSession by EditorSession(initial) {
        override val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
    }
}
