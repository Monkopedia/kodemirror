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
import androidx.compose.ui.Modifier
import com.monkopedia.kodemirror.autocomplete.Completion
import com.monkopedia.kodemirror.autocomplete.CompletionConfig
import com.monkopedia.kodemirror.autocomplete.CompletionResult
import com.monkopedia.kodemirror.autocomplete.CompletionSource
import com.monkopedia.kodemirror.autocomplete.autocompletion
import com.monkopedia.kodemirror.samples.showcase.showcaseSetup
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.rememberEditorSession

private val keywords = listOf(
    Completion(label = "function", type = "keyword", detail = "Function declaration"),
    Completion(label = "const", type = "keyword", detail = "Constant binding"),
    Completion(label = "let", type = "keyword", detail = "Block-scoped variable"),
    Completion(label = "var", type = "keyword", detail = "Function-scoped variable"),
    Completion(label = "if", type = "keyword"),
    Completion(label = "else", type = "keyword"),
    Completion(label = "for", type = "keyword"),
    Completion(label = "while", type = "keyword"),
    Completion(label = "return", type = "keyword"),
    Completion(label = "class", type = "keyword"),
    Completion(label = "console", type = "variable", detail = "Console API"),
    Completion(label = "console.log", type = "function", detail = "Log to console"),
    Completion(label = "document", type = "variable", detail = "DOM document"),
    Completion(label = "Math.random", type = "function", detail = "Random number"),
    Completion(label = "Array.from", type = "function", detail = "Create array")
)

private val myCompletionSource: CompletionSource = { ctx ->
    val word = ctx.matchBefore(Regex("[\\w.]+"))
    if (word != null || ctx.explicit) {
        CompletionResult(
            from = word?.from ?: ctx.pos,
            options = keywords,
            validFor = Regex("[\\w.]*")
        )
    } else {
        null
    }
}

@Composable
fun AutocompletionDemo() {
    DemoScaffold(
        title = "Autocompletion",
        description = "Custom CompletionSource providing JavaScript keywords and builtins. " +
            "Type or press Ctrl+Space to trigger."
    ) {
        val session = rememberEditorSession(
            doc = SampleDocs.javascript,
            extensions = showcaseSetup + javascript().extension +
                autocompletion(
                    CompletionConfig(override = listOf(myCompletionSource))
                )
        )
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
