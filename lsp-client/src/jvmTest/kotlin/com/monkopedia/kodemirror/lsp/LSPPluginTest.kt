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

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.monkopedia.kodemirror.lint.Severity
import com.monkopedia.kodemirror.lint.diagnosticCount
import com.monkopedia.kodemirror.lint.forEachDiagnostic
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.lsp.Diagnostic as LSPDiagnostic
import com.monkopedia.lsp.DiagnosticSeverity
import com.monkopedia.lsp.HoverContents
import com.monkopedia.lsp.MarkedStringObject
import com.monkopedia.lsp.MarkupContent
import com.monkopedia.lsp.MarkupKind
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.PublishDiagnosticsParams
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.StringOr
import com.monkopedia.lsp.TextDocumentSyncKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Exercises the LSPPlugin rendering seam: the markdown/markup doc-string →
 * Compose [androidx.compose.ui.text.AnnotatedString] conversion (upstream's
 * `docToHTML`, replaced here because the editor renders on a Compose canvas with
 * no DOM), and server-pushed diagnostics surfacing into the editor via the
 * plugin's [LSPClient.languageClient] notification handler.
 *
 * Position conversion (covered by [DocumentSyncTest]) is not duplicated here.
 */
class LSPPluginTest {

    // --- "can render doc strings": markdown -> Compose AnnotatedString ---

    @Test
    fun rendersMarkdownDocString() {
        val contents = HoverContents.MarkupContentValue(
            MarkupContent(kind = MarkupKind.MARKDOWN, value = "**bold** and `code`")
        )
        val blocks = parseHoverContents(contents)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].markdown)

        val annotated = hoverBlockToAnnotatedString(blocks[0])
        // The literal markdown markers are consumed, leaving the rendered text.
        assertEquals("bold and code", annotated.text)
        // "bold" is rendered with the bold span; "code" with the monospace span.
        val boldRange = annotated.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals("bold", annotated.text.substring(boldRange.start, boldRange.end))
        val codeRange = annotated.spanStyles.single { it.item.fontFamily == FontFamily.Monospace }
        assertEquals("code", annotated.text.substring(codeRange.start, codeRange.end))
    }

    @Test
    fun plainTextDocStringIsNotInterpretedAsMarkdown() {
        // A bare MarkupContent of kind PLAINTEXT keeps markdown punctuation literal.
        val contents = HoverContents.MarkupContentValue(
            MarkupContent(kind = MarkupKind.PLAIN_TEXT, value = "**not bold**")
        )
        val blocks = parseHoverContents(contents)
        assertEquals(1, blocks.size)
        assertTrue(!blocks[0].markdown)
        val annotated = hoverBlockToAnnotatedString(blocks[0])
        assertEquals("**not bold**", annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }

    @Test
    fun rendersMarkdownHeadingAsBoldLine() {
        val contents = HoverContents.MarkupContentValue(
            MarkupContent(kind = MarkupKind.MARKDOWN, value = "# Title\nbody")
        )
        val annotated = hoverBlockToAnnotatedString(parseHoverContents(contents).single())
        // The leading "# " marker is stripped; the title text is bolded.
        assertEquals("Title\nbody", annotated.text)
        val boldRange = annotated.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals("Title", annotated.text.substring(boldRange.start, boldRange.end))
    }

    // --- "can render doc strings with highlighting": code fences / typed code ---

    @Test
    fun rendersFencedCodeBlockAsMonospace() {
        // A markdown fenced code block in the doc string is rendered monospace
        // (the conversion's stand-in for upstream's syntax-highlighted code).
        val contents = HoverContents.MarkupContentValue(
            MarkupContent(
                kind = MarkupKind.MARKDOWN,
                value = "doc\n```kotlin\nval x = 1\n```"
            )
        )
        val annotated = hoverBlockToAnnotatedString(parseHoverContents(contents).single())
        // Fence delimiters are dropped; the code line survives.
        assertTrue(annotated.text.contains("val x = 1"))
        assertTrue(!annotated.text.contains("```"))
        val mono = annotated.spanStyles.single { it.item.fontFamily == FontFamily.Monospace }
        assertEquals("val x = 1", annotated.text.substring(mono.start, mono.end))
    }

    @Test
    fun rendersTypedMarkedStringAsMonospaceCode() {
        // A MarkedString with a language is a fenced code block: rendered entirely
        // monospace with no markdown interpretation.
        val contents = HoverContents.MarkedStringValue(
            StringOr.Value(MarkedStringObject(language = "kotlin", value = "fun f() = 1"))
        )
        val block = parseHoverContents(contents).single()
        assertEquals("kotlin", block.language)
        val annotated = hoverBlockToAnnotatedString(block)
        assertEquals("fun f() = 1", annotated.text)
        val mono = annotated.spanStyles.single()
        assertEquals(FontFamily.Monospace, mono.item.fontFamily)
        assertEquals(0, mono.start)
        assertEquals(annotated.text.length, mono.end)
    }

    @Test
    fun signatureDocumentationReusesHoverRendering() {
        // Signature-help documentation funnels through the same conversion: a
        // MarkupContent markdown value renders as a markdown block.
        val blocks = documentationBlocks(
            StringOr.Value(MarkupContent(kind = MarkupKind.MARKDOWN, value = "*italic*"))
        )
        assertEquals(1, blocks.size)
        val annotated = hoverBlockToAnnotatedString(blocks[0])
        assertEquals("italic", annotated.text)
        assertTrue(annotated.spanStyles.any { it.item.fontStyle != null })
    }

    @Test
    fun customStylesAreAppliedDuringRendering() {
        // The rendering is parameterized by HoverMarkdownStyles, so a caller's
        // theme attributes flow into the produced spans.
        val styles = HoverMarkdownStyles(
            bold = SpanStyle(fontWeight = FontWeight.Black)
        )
        val annotated = hoverBlockToAnnotatedString(
            HoverBlock(text = "**hi**", markdown = true),
            styles
        )
        assertEquals("hi", annotated.text)
        assertEquals(FontWeight.Black, annotated.spanStyles.single().item.fontWeight)
    }

    // --- "can display errors": server diagnostics surface through the plugin ---

    @Test
    fun publishDiagnosticsSurfacesErrorsIntoEditor() = runBlocking {
        val fixture = TestLanguageServer(
            capabilities = ServerCapabilities(textDocumentSync = TextDocumentSyncKind.INCREMENTAL)
        )
        val client = LSPClient(fixture.server)
        // serverDiagnostics() installs the lint-display state the handler feeds.
        val state = EditorState.create(doc = "let x", extensions = serverDiagnostics())
        val session = EditorSession(state)
        client.workspace.openFile("file:///a.kt", "kotlin", session)

        client.languageClient.textDocumentPublishDiagnostics(
            PublishDiagnosticsParams(
                uri = "file:///a.kt",
                diagnostics = listOf(
                    LSPDiagnostic(
                        range = Range(start = Position(0u, 0u), end = Position(0u, 3u)),
                        severity = DiagnosticSeverity.ERROR,
                        message = "unexpected token"
                    )
                )
            )
        )

        assertEquals(1, diagnosticCount(session.state))
        val collected = mutableListOf<String>()
        var severity: Severity? = null
        forEachDiagnostic(session.state) {
            collected.add(it.message)
            severity = it.severity
        }
        assertEquals(listOf("unexpected token"), collected)
        assertEquals(Severity.ERROR, severity)
    }

    @Test
    fun publishDiagnosticsClearsPreviousDiagnostics() = runBlocking {
        val fixture = TestLanguageServer(
            capabilities = ServerCapabilities(textDocumentSync = TextDocumentSyncKind.INCREMENTAL)
        )
        val client = LSPClient(fixture.server)
        val state = EditorState.create(doc = "let x", extensions = serverDiagnostics())
        val session = EditorSession(state)
        client.workspace.openFile("file:///a.kt", "kotlin", session)
        val lc = client.languageClient

        lc.textDocumentPublishDiagnostics(
            PublishDiagnosticsParams(
                uri = "file:///a.kt",
                diagnostics = listOf(
                    LSPDiagnostic(
                        range = Range(start = Position(0u, 0u), end = Position(0u, 1u)),
                        message = "boom"
                    )
                )
            )
        )
        assertEquals(1, diagnosticCount(session.state))

        // An empty publish (the server resolved the problem) clears the editor.
        lc.textDocumentPublishDiagnostics(
            PublishDiagnosticsParams(uri = "file:///a.kt", diagnostics = emptyList())
        )
        assertEquals(0, diagnosticCount(session.state))
    }

    @Test
    fun diagnosticsForFileWithoutSessionAreDropped() = runBlocking {
        // A workspace file opened without an attached editor session has nowhere
        // to render; the handler must no-op rather than crash.
        val fixture = TestLanguageServer()
        val client = LSPClient(fixture.server)
        client.workspace.openFile("file:///headless.kt", "kotlin", session = null)
        client.languageClient.textDocumentPublishDiagnostics(
            PublishDiagnosticsParams(
                uri = "file:///headless.kt",
                diagnostics = listOf(
                    LSPDiagnostic(
                        range = Range(start = Position(0u, 0u), end = Position(0u, 1u)),
                        message = "ignored"
                    )
                )
            )
        )
        // No session to read from; assert the file is still tracked and nothing threw.
        assertNull(client.workspace.getFile("file:///headless.kt")?.session)
    }
}
