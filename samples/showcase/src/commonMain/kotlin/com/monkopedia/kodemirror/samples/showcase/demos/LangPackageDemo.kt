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
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.language.LanguageSupport
import com.monkopedia.kodemirror.language.StreamLanguage
import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.StringStream
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.rememberEditorSession

private data class SimpleState(val inString: Boolean = false)

private val simpleParser = object : StreamParser<SimpleState> {
    override val name = "simple-lang"

    override fun startState(indentUnit: Int) = SimpleState()

    override fun token(stream: StringStream, state: SimpleState): String? {
        // Comments
        if (stream.match("//")) {
            stream.skipToEnd()
            return "comment"
        }
        // Strings
        if (stream.match("\"")) {
            while (!stream.eol()) {
                if (stream.next() == "\"") break
            }
            return "string"
        }
        // Numbers
        if (stream.match(Regex("\\d+")) != null) return "number"
        // Keywords
        if (stream.match(Regex("\\b(fn|let|if|else|return|while|for|true|false)\\b")) != null) {
            return "keyword"
        }
        // Types
        if (stream.match(Regex("\\b(Int|String|Bool|Float)\\b")) != null) return "typeName"
        // Operators
        if (stream.match(Regex("[+\\-*/=<>!]+")) != null) return "operator"
        // Identifiers and other
        stream.next()
        return null
    }

    override fun copyState(state: SimpleState) = state.copy()
}

private val simpleLang = StreamLanguage.define(simpleParser)

private val sampleCode = """
    // A simple custom language
    fn fibonacci(n: Int): Int {
        if n <= 1 {
            return n
        }
        return fibonacci(n - 1) + fibonacci(n - 2)
    }

    let result = fibonacci(10)
    let message = "The answer is 55"
    let flag = true
""".trimIndent()

@Composable
fun LangPackageDemo() {
    DemoScaffold(
        title = "Custom Language",
        description = "StreamParser-based language with keywords, strings, numbers, and comments."
    ) {
        val session = rememberEditorSession(
            doc = sampleCode,
            extensions = basicSetup + LanguageSupport(simpleLang).extension
        )
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
