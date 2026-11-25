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
package com.monkopedia.kodemirror.autocomplete

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

actual fun asyncCompletionSource(source: SuspendCompletionSource): CompletionSource =
    SuspendingCompletionSource(source) { context -> runUntilSuspended(source, context) }

// Nothing can block on wasmJs, so a direct call runs the source until it first suspends:
// one that finishes without suspending answers, and one that suspends can't.
private fun runUntilSuspended(
    source: SuspendCompletionSource,
    context: CompletionContext
): CompletionResult? {
    var outcome: Result<CompletionResult?>? = null
    suspend { source(context) }.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    val done = outcome ?: throw UnsupportedOperationException(
        "This asyncCompletionSource suspended, and wasmJs can't block to wait for it. " +
            "Use it as an editor completion source, which runs it without blocking."
    )
    return done.getOrThrow()
}
