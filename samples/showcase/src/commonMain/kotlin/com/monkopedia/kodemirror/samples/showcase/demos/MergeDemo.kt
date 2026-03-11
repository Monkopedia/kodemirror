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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.samples.showcase.showcaseSetup
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.merge.MergeView
import com.monkopedia.kodemirror.merge.MergeViewConfig
import com.monkopedia.kodemirror.samples.showcase.DemoScaffold
import com.monkopedia.kodemirror.samples.showcase.SampleDocs
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.KodeMirror

@Composable
fun MergeDemo() {
    val mergeView = remember {
        MergeView(
            MergeViewConfig(
                a = EditorStateConfig(
                    doc = SampleDocs.mergeOriginal.asDoc(),
                    extensions = showcaseSetup + javascript().extension
                ),
                b = EditorStateConfig(
                    doc = SampleDocs.mergeModified.asDoc(),
                    extensions = showcaseSetup + javascript().extension
                ),
                highlightChanges = true,
                gutter = true
            )
        )
    }

    DemoScaffold(
        title = "Merge View",
        description = "Side-by-side diff of original vs modified code using MergeView."
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KodeMirror(
                session = mergeView.a,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            KodeMirror(
                session = mergeView.b,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}
