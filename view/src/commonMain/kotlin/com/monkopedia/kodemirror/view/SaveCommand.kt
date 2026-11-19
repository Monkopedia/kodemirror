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
package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Facet
import kotlinx.coroutines.launch

/**
 * Facet that collects save handlers.
 *
 * Each handler is called (in a coroutine on the session's scope) when the
 * [save] command executes.  Multiple handlers are all invoked — not just
 * the first.
 *
 * ```kotlin
 * val ext = onSave.of { session ->
 *     myViewModel.save(session.state.doc.toString())
 * }
 * ```
 */
val onSave: Facet<suspend (EditorSession) -> Unit, List<suspend (EditorSession) -> Unit>> =
    Facet.define()

/**
 * Command that invokes all registered [onSave] handlers.
 *
 * Handlers run in a coroutine launched on the session's [EditorSession.coroutineScope].
 * Returns `true` when at least one handler is registered, `false` otherwise.
 */
fun save(session: EditorSession): Boolean {
    val handlers = session.state.facet(onSave)
    if (handlers.isEmpty()) return false
    session.coroutineScope.launch {
        // A failing handler is logged, so the others still run and the scope survives.
        for (handler in handlers) {
            session.runLogged { handler(session) }
        }
    }
    return true
}

/**
 * Extension that binds **Ctrl-S** (and **Cmd-S** on macOS) to the [save]
 * command.
 */
val saveKeymap: Extension = keymapOf(
    KeyBinding(
        key = "Ctrl-s",
        mac = "Meta-s",
        run = ::save,
        preventDefault = true
    )
)
