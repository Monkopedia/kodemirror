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
package com.monkopedia.kodemirror.view.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import com.monkopedia.kodemirror.view.setDoc
import kotlin.test.Test

/**
 * Clicking after the document is replaced in place (#313).
 *
 * The pointer-input gesture coroutine is keyed on the session, which does not
 * change when its document does, so it kept whichever position resolver the
 * first composition handed it. That resolver closed over that composition's
 * `columnItems`, i.e. the OLD document's line spans. Clicking the second line
 * of a shorter replacement document resolved through the old line's `from`
 * (offset 201 here) and the dispatch threw `Selection points outside of
 * document`.
 *
 * Both tests assert an exact offset rather than merely surviving: clamping the
 * out-of-bounds result to `doc.length` would also stop the throw, but would
 * park the caret at 3 (the end of `"a\nb"`) instead of 2 (the start of the line
 * that was actually clicked).
 */
@OptIn(ExperimentalTestApi::class)
class DocReplacementClickTest {

    /** A first line long enough that the old line 2 starts past the new doc's end. */
    private val longDoc = "A".repeat(200) + "\nold second line"

    /** Replacement: two short lines, so line 2 starts at offset 2 and the doc ends at 3. */
    private val shortDoc = "a\nb"

    /** Left edge of the content area, so the click lands on the line's first character. */
    private val lineStartClick = Offset(8f, 35f)

    @Test
    fun clickAfterReplacingWithShorterDoc() = runEditorTest(
        doc = longDoc,
        width = 400,
        height = 160
    ) { holder ->
        // Interact with the editor BEFORE the replacement. This is what arms
        // the bug: the gesture coroutine is started lazily by the first pointer
        // event, so without a prior interaction it is launched after the
        // replacement and closes over the fresh resolver by luck.
        onNodeWithTag("KodeMirror").performMouseInput {
            click(Offset(50f, 15f))
        }
        waitForIdle()
        holder.assertCursorOnLine(1)

        holder.session.setDoc(shortDoc)
        waitForIdle()
        holder.assertDoc(shortDoc)

        onNodeWithTag("KodeMirror").performMouseInput {
            click(lineStartClick)
        }
        waitForIdle()

        holder.assertCursorAt(2)
    }

    /**
     * The same click on the same document with no replacement — the resolver
     * this test guards must keep placing an ordinary click exactly where it did
     * before, not become a no-op that leaves the caret wherever it was.
     */
    @Test
    fun clickWithoutReplacement() = runEditorTest(
        doc = shortDoc,
        width = 400,
        height = 160
    ) { holder ->
        onNodeWithTag("KodeMirror").performMouseInput {
            click(lineStartClick)
        }
        waitForIdle()

        holder.assertCursorAt(2)
    }
}
