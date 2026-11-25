/*
 * Copyright 2025 Jason Monk
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
package com.monkopedia.kodemirror.lang.markdown

import com.monkopedia.kodemirror.language.FoldRange
import com.monkopedia.kodemirror.language.foldable
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownFoldTest {
    @Test
    fun headingSectionsFoldFromTheEndOfTheHeadingLine() {
        // Expected values from @codemirror/lang-markdown run on the same input (#469).
        val state = EditorState.create(
            EditorStateConfig(
                doc = "# A\ntext\n## A2\nx\n# B\nmore\n".asDoc(),
                extensions = markdown().extension
            )
        )
        val folds = (1..state.doc.lines).map { n ->
            val line = state.doc.line(LineNumber(n))
            foldable(state, line.from, line.to)
        }
        val expected = listOf(
            FoldRange(DocPos(3), DocPos(16)),
            null,
            FoldRange(DocPos(14), DocPos(16)),
            null,
            FoldRange(DocPos(20), DocPos(25)),
            null,
            null
        )
        assertEquals(expected, folds)
    }
}
