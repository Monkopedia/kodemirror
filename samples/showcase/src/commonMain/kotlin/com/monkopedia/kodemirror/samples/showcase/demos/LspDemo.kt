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
 */
package com.monkopedia.kodemirror.samples.showcase.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.lsp.LSPClient
import com.monkopedia.kodemirror.lsp.languageServerSupport
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.showcaseSetup
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.rememberEditorSession

/** The document URI this demo's editor is opened against in the workspace. */
private const val LSP_URI = "inmemory://lsp-demo/sample.js"

/** The LSP language id reported for the document. */
private const val LSP_LANGUAGE_ID = "javascript"

private val lspDoc = """
    function greet(name) {
        return "Hello, " + name;
    }
    greet("world");
""".trimIndent()

/**
 * Demonstrates the `:lsp-client` module driven by an in-memory
 * [StubLanguageServer]. The stub returns canned responses for every feature
 * `languageServerSupport` wires up, so each can be exercised without a real
 * language server process or JSON-RPC transport.
 *
 * Wiring (mirrors what a real consumer does):
 * 1. construct the stub server and wrap it in an [LSPClient];
 * 2. register `client.languageClient` with the stub via
 *    [StubLanguageServer.connect] so server→client notifications (most notably
 *    `publishDiagnostics`) are delivered in-process;
 * 3. drive the `initialize` handshake (also done lazily by the plugin);
 * 4. add `languageServerSupport(client, uri, languageId)` to the editor.
 *
 * Try it:
 * - Diagnostics are pushed on open/change (squiggles on the first lines).
 * - Type `.` or Ctrl-Space for completions (`greet`, `main`, `name`).
 * - Hover over a symbol for documentation.
 * - Type inside `greet(` to see signature help.
 * - F12 jump-to-definition, Shift-F12 find references.
 * - F2 to rename, Shift-Alt-F to format (prepends a header comment).
 */
@Composable
fun LspDemo() {
    // The client + stub outlive recomposition; remember them per demo instance.
    val client = remember {
        val stub = StubLanguageServer()
        val lspClient = LSPClient(stub)
        // Register the client side so the stub can push diagnostics back in-process.
        stub.connect(lspClient.languageClient)
        lspClient
    }

    // Eagerly run the handshake so feature capabilities are available promptly.
    // (The LSPPlugin also calls initialize() lazily; this is idempotent.)
    LaunchedEffect(client) {
        client.initialize()
    }

    DemoScaffold(
        title = "Language Server Protocol",
        description = "Drives :lsp-client against an in-memory stub server: " +
            "diagnostics, completion, hover, signature help, go-to-definition, " +
            "find references, rename (F2), and formatting (Shift-Alt-F)."
    ) {
        val session = rememberEditorSession(
            doc = lspDoc,
            extensions = showcaseSetup + javascript().extension +
                languageServerSupport(client, LSP_URI, LSP_LANGUAGE_ID)
        )

        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
