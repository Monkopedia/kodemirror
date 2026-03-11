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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.samples.showcase.showcaseSetup
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.Compartment
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.rememberEditorSession

private val spanishPhrases = mapOf(
    "Find" to "Buscar",
    "Replace" to "Reemplazar",
    "next" to "siguiente",
    "previous" to "anterior",
    "replace" to "reemplazar",
    "replace all" to "reemplazar todo",
    "close" to "cerrar",
    "Go to line" to "Ir a linea",
    "Folded lines" to "Lineas plegadas",
    "Unfolded lines" to "Lineas desplegadas"
)

private val frenchPhrases = mapOf(
    "Find" to "Rechercher",
    "Replace" to "Remplacer",
    "next" to "suivant",
    "previous" to "precedent",
    "replace" to "remplacer",
    "replace all" to "tout remplacer",
    "close" to "fermer",
    "Go to line" to "Aller a la ligne",
    "Folded lines" to "Lignes pliees",
    "Unfolded lines" to "Lignes depliees"
)

private enum class Lang(val label: String, val phrases: Map<String, String>) {
    EN("English", emptyMap()),
    ES("Spanish", spanishPhrases),
    FR("French", frenchPhrases)
}

@Composable
fun TranslateDemo() {
    var lang by remember { mutableStateOf(Lang.EN) }
    val phrasesCompartment = remember { Compartment() }

    fun phrasesExt(l: Lang): Extension =
        if (l.phrases.isEmpty()) ExtensionList(emptyList())
        else EditorState.phrases.of(l.phrases)

    val session = rememberEditorSession(
        doc = SampleDocs.javascript,
        extensions = showcaseSetup + javascript().extension +
            phrasesCompartment.of(phrasesExt(lang))
    )

    DemoScaffold(
        title = "Phrase Translation",
        description = "Translate editor UI phrases (Find, Replace, etc.) via the phrases facet. " +
            "Open search with Ctrl+F to see translated labels.",
        controls = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Lang.entries.forEach { l ->
                    FilterChip(
                        selected = lang == l,
                        onClick = {
                            lang = l
                            session.dispatch(
                                TransactionSpec(
                                    effects = listOf(
                                        phrasesCompartment.reconfigure(phrasesExt(l))
                                    )
                                )
                            )
                        },
                        label = { Text(l.label) }
                    )
                }
            }
        }
    ) {
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}
