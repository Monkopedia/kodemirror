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

import com.monkopedia.lsp.DidChangeTextDocumentParams
import com.monkopedia.lsp.DidCloseTextDocumentParams
import com.monkopedia.lsp.DidOpenTextDocumentParams
import com.monkopedia.lsp.InitializeResult
import com.monkopedia.lsp.LanguageServer
import com.monkopedia.lsp.ServerCapabilities
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A reusable, scriptable stub [LanguageServer] for jvmTest, built with a
 * coroutine-aware [Proxy] (suspend methods receive a trailing [Continuation]
 * which the handler resolves synchronously — the same trick the per-feature
 * `*Test` fixtures use).
 *
 * It mirrors the role of upstream `@codemirror/lsp-client`'s `test/server.ts`
 * `DummyServer`: it advertises [capabilities], records the document-lifecycle
 * calls the client drives (`textDocument/didOpen` / `didClose` / `didChange`),
 * lets a test return a value for a named method, and lets a test make a named
 * method *throw* so error-routing can be exercised. Restricted to jvmTest
 * because `Proxy` is JVM-only.
 *
 * @param capabilities Capabilities reported from `initialize`.
 * @param throwingMethods Method names (e.g. `"textDocumentHover"`) that should
 *   raise [throwable] instead of returning, modelling upstream's `brokenPipe` /
 *   `ServerError`. The exception surfaces to the suspend caller.
 * @param throwable The exception thrown by [throwingMethods].
 * @param responses Per-method canned return values, keyed by method name. A
 *   method not listed here (and not a recorded lifecycle/handshake call)
 *   resolves to [Unit].
 */
class TestLanguageServer(
    private val capabilities: ServerCapabilities = ServerCapabilities(),
    private val throwingMethods: Set<String> = emptySet(),
    private val throwable: Throwable = RuntimeException("Broken Pipe"),
    private val responses: Map<String, Any?> = emptyMap()
) {
    /** Number of times `initialize` was invoked. */
    var initializeCount: Int = 0
        private set

    /** True once `initialized` has been received. */
    var initialized: Boolean = false
        private set

    /** True once `shutdown` then `exit` have been received. */
    var shutDown: Boolean = false
        private set

    /** URIs the server currently considers open, in arrival order. */
    val openFiles: MutableList<String> = mutableListOf()

    /** URIs the server saw a `didOpen` for, in arrival order (never pruned). */
    val didOpenUris: MutableList<String> = mutableListOf()

    /** URIs the server saw a `didClose` for, in arrival order. */
    val didCloseUris: MutableList<String> = mutableListOf()

    /** URIs the server saw a `didChange` for, in arrival order (may repeat). */
    val didChangeUris: MutableList<String> = mutableListOf()

    /** Every method name invoked through the proxy, in order. */
    val calls: MutableList<String> = mutableListOf()

    private fun handle(method: Method, args: Array<Any?>?): Any? {
        val name = method.name
        calls.add(name)
        if (name in throwingMethods) throw throwable
        return when (name) {
            "initialize" -> {
                initializeCount++
                InitializeResult(capabilities = capabilities)
            }
            "initialized" -> {
                initialized = true
                Unit
            }
            "shutdown" -> Unit
            "exit" -> {
                shutDown = true
                Unit
            }
            "textDocumentDidOpen" -> {
                val uri = (args?.firstOrNull() as? DidOpenTextDocumentParams)?.textDocument?.uri
                if (uri != null) {
                    didOpenUris.add(uri)
                    openFiles.add(uri)
                }
                Unit
            }
            "textDocumentDidClose" -> {
                val uri = (args?.firstOrNull() as? DidCloseTextDocumentParams)?.textDocument?.uri
                if (uri != null) {
                    didCloseUris.add(uri)
                    openFiles.remove(uri)
                }
                Unit
            }
            "textDocumentDidChange" -> {
                val uri = (args?.firstOrNull() as? DidChangeTextDocumentParams)?.textDocument?.uri
                if (uri != null) didChangeUris.add(uri)
                Unit
            }
            else -> if (responses.containsKey(name)) responses[name] else Unit
        }
    }

    /** The [LanguageServer] proxy backed by this fixture's scripted behavior. */
    val server: LanguageServer = Proxy.newProxyInstance(
        LanguageServer::class.java.classLoader,
        arrayOf(LanguageServer::class.java),
        InvocationHandler { _, method: Method, args: Array<Any?>? ->
            @Suppress("UNCHECKED_CAST")
            val continuation = args?.lastOrNull() as? Continuation<Any?>
            try {
                val result = handle(method, args)
                continuation?.resume(result)
            } catch (t: Throwable) {
                // Route a server-side failure back to the suspend caller, the
                // way a real transport surfaces a JSON-RPC error.
                continuation?.resumeWithException(t)
            }
            COROUTINE_SUSPENDED
        }
    ) as LanguageServer
}
