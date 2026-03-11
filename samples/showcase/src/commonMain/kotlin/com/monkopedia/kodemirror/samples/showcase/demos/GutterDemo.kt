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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.samples.showcase.showcaseSetup
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.EditorTheme
import com.monkopedia.kodemirror.view.GutterConfig
import com.monkopedia.kodemirror.view.GutterMarker
import com.monkopedia.kodemirror.view.GutterType
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.gutter
import com.monkopedia.kodemirror.view.rememberEditorSession

private val toggleBreakpoint = StateEffect.define<LineNumber>()

private class BreakpointMarker : GutterMarker() {
    @Composable
    override fun Content(theme: EditorTheme) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(0xFFE06C75))
        )
    }
}

private val breakpointState: StateField<Set<LineNumber>> = StateField.define(
    StateFieldSpec(
        create = { emptySet() },
        update = { value, tr ->
            var result = value
            for (effect in tr.effects) {
                val e = effect.asType(toggleBreakpoint)
                if (e != null) {
                    val line = e.value
                    result = if (line in result) result - line else result + line
                }
            }
            result
        }
    )
)

private val breakpointGutter = gutter(
    GutterConfig(
        type = GutterType.Custom("breakpoints"),
        lineMarker = { session, lineFrom ->
            val breakpoints = session.state.field(breakpointState)
            val lineNumber = session.state.doc.lineAt(DocPos(lineFrom)).number
            if (lineNumber in breakpoints) BreakpointMarker() else null
        },
        lineMarkerChange = { update ->
            update.transactions.any { tr ->
                tr.effects.any { it.asType(toggleBreakpoint) != null }
            }
        }
    )
)

@Composable
fun GutterDemo() {
    val session = rememberEditorSession(
        doc = SampleDocs.javascript,
        extensions = showcaseSetup + javascript().extension +
            breakpointState + breakpointGutter
    )

    // Set initial breakpoints once
    LaunchedEffect(Unit) {
        session.dispatch(
            TransactionSpec(
                effects = listOf(
                    toggleBreakpoint.of(LineNumber(2)),
                    toggleBreakpoint.of(LineNumber(5)),
                    toggleBreakpoint.of(LineNumber(8))
                )
            )
        )
    }

    DemoScaffold(
        title = "Gutters",
        description = "Custom breakpoint gutter using StateField + GutterMarker. " +
            "Breakpoints shown on lines 2, 5, and 8."
    ) {
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
