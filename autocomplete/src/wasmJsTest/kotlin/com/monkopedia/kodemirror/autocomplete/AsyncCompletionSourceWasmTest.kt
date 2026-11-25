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

import com.monkopedia.kodemirror.state.DocPos
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CompletableDeferred

class AsyncCompletionSourceWasmTest {
    @Test
    fun callingASourceThatSuspendsThrows() {
        // Nothing can block on wasmJs; the editor still runs it (see AsyncCompletionSourceTest).
        val source = asyncCompletionSource {
            CompletableDeferred<Unit>().await()
            null
        }
        val context = CompletionContext(completionState("foo "), DocPos(4), explicit = true)
        assertFailsWith<UnsupportedOperationException> { source(context) }
    }
}
