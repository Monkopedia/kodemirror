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
package com.monkopedia.kodemirror.samples.showcase

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.monkopedia.kodemirror.autocomplete.Completion
import com.monkopedia.kodemirror.autocomplete.CompletionConfig
import com.monkopedia.kodemirror.autocomplete.CompletionResult
import com.monkopedia.kodemirror.autocomplete.CompletionSource
import com.monkopedia.kodemirror.autocomplete.completionConfig
import com.monkopedia.kodemirror.autocomplete.startCompletion
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.rememberEditorSession
import kotlin.JsFun

@JsFun(
    """(cb) => {
    globalThis.__kodemirror.openCompletion = () => cb();
}"""
)
private external fun registerOpenCompletion(cb: () -> Unit)

private val completionKeywords = listOf(
    Completion(label = "console", type = "variable", detail = "Console API"),
    Completion(label = "console.log", type = "function", detail = "Log to console"),
    Completion(label = "const", type = "keyword", detail = "Constant binding"),
    Completion(label = "constructor", type = "method"),
    Completion(label = "continue", type = "keyword")
)

private val completionTestSource: CompletionSource = { ctx ->
    val word = ctx.matchBefore(Regex("[\\w.]+"))
    if (word != null || ctx.explicit) {
        CompletionResult(
            from = word?.from ?: ctx.pos,
            options = completionKeywords,
            validFor = Regex("[\\w.]*")
        )
    } else {
        null
    }
}

/**
 * Test harness page for the completion popup, served at `?test=completion`.
 *
 * The document is the typed prefix `co` with the cursor after it, and a
 * completion source provides keywords whose matched `co` prefix is bold (#111).
 * The page registers `globalThis.__kodemirror.openCompletion()` so the Playwright
 * gap-analysis spec can deterministically open the popup and screenshot the live
 * wasmJs canvas, verifying the [androidx.compose.ui.text.AnnotatedString] label
 * actually paints (the #111 regression was a blank label on wasmJs).
 */
@Composable
fun CompletionTestEditorPage() {
    val session = rememberEditorSession(
        config = EditorStateConfig(
            doc = "co".asDoc(),
            selection = SelectionSpec.CursorSpec(DocPos(2)),
            extensions = showcaseSetup + javascript().extension +
                completionConfig.of(
                    CompletionConfig(override = listOf(completionTestSource))
                ) + testBridgeExtension
        )
    )
    LaunchedEffect(session) {
        registerOpenCompletion { startCompletion(session) }
    }
    Box(Modifier.fillMaxSize()) {
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
