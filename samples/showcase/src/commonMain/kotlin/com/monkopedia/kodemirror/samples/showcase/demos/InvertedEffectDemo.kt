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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.samples.showcase.showcaseSetup
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.invertedEffects
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.rememberEditorSession

private val addToCounter = StateEffect.define<Int>()

private val counterField: StateField<Int> = StateField.define(
    StateFieldSpec(
        create = { 0 },
        update = { value, tr ->
            var result = value
            for (effect in tr.effects) {
                val e = effect.asType(addToCounter)
                if (e != null) result += e.value
            }
            result
        }
    )
)

private val counterExtension = counterField +
    invertedEffects.of { tr ->
        val inverted = mutableListOf<StateEffect<*>>()
        for (effect in tr.effects) {
            val e = effect.asType(addToCounter)
            if (e != null) {
                inverted.add(addToCounter.of(-e.value))
            }
        }
        inverted
    }

@Composable
fun InvertedEffectDemo() {
    var displayCounter by remember { mutableIntStateOf(0) }

    val session = rememberEditorSession(
        doc = SampleDocs.javascript,
        extensions = showcaseSetup + javascript().extension + counterExtension
    )

    DemoScaffold(
        title = "Inverted Effects",
        description = "Counter state effect integrated with undo. " +
            "Add to counter, then undo to see it reverse.",
        controls = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = {
                    session.dispatch(
                        TransactionSpec(effects = listOf(addToCounter.of(1)))
                    )
                    displayCounter = session.state.field(counterField)
                }) { Text("+1") }
                Button(onClick = {
                    session.dispatch(
                        TransactionSpec(effects = listOf(addToCounter.of(5)))
                    )
                    displayCounter = session.state.field(counterField)
                }) { Text("+5") }
                Text(
                    text = "Counter: $displayCounter",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            Text(
                text = "Use Ctrl+Z to undo and watch the counter reverse.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    ) {
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
