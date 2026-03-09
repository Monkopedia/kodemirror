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
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.lint.Diagnostic
import com.monkopedia.kodemirror.lint.LintSource
import com.monkopedia.kodemirror.lint.Severity
import com.monkopedia.kodemirror.lint.linter
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.rememberEditorSession

private val todoPattern = Regex("\\b(TODO|FIXME|HACK)\\b")

private val myLinter: LintSource = { session ->
    val diagnostics = mutableListOf<Diagnostic>()
    val doc = session.state.doc
    for (i in 1..doc.lines) {
        val line = doc.line(LineNumber(i))
        val text = line.text
        // Long line warning
        if (text.length > 80) {
            diagnostics.add(
                Diagnostic(
                    from = line.from + 80,
                    to = line.to,
                    severity = Severity.WARNING,
                    message = "Line exceeds 80 characters (${text.length})",
                    source = "line-length"
                )
            )
        }
        // TODO/FIXME markers
        for (match in todoPattern.findAll(text)) {
            diagnostics.add(
                Diagnostic(
                    from = line.from + match.range.first,
                    to = line.from + match.range.last + 1,
                    severity = Severity.INFO,
                    message = "${match.value} comment found",
                    source = "todo-finder"
                )
            )
        }
    }
    diagnostics
}

private val lintDoc = """
    // TODO: refactor this function to be more readable
    function processData(data) {
        // FIXME: this is a temporary workaround for the API issue
        const result = data.map(item => item.value * 2)
            .filter(v => v > 10).reduce((a, b) => a + b, 0);
        console.log("Result:", result);
        return result;
    }

    // HACK: quick fix for deployment
    const config = { debug: true, verbose: true };
""".trimIndent()

@Composable
fun LintDemo() {
    DemoScaffold(
        title = "Linting",
        description = "Custom linter that warns on lines > 80 chars " +
            "and flags TODO/FIXME/HACK."
    ) {
        val session = rememberEditorSession(
            doc = lintDoc,
            extensions = basicSetup + javascript().extension + linter(myLinter)
        )
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
