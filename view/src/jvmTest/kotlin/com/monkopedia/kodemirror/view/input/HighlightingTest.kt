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
import androidx.compose.ui.test.performTextInput
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.commands.standardKeymap
import com.monkopedia.kodemirror.lang.javascript.jsParser
import com.monkopedia.kodemirror.language.Language
import com.monkopedia.kodemirror.language.LanguageSupport
import com.monkopedia.kodemirror.language.StreamLanguage
import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.StringStream
import com.monkopedia.kodemirror.language.defaultHighlightStyle
import com.monkopedia.kodemirror.language.syntaxHighlighting
import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.lezer.highlight.highlightTree
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.PluginSpec
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.keymapOf
import org.junit.Test

/**
 * Integration tests verifying that syntax highlighting decorations
 * stay correctly positioned after document edits.
 *
 * These tests catch bugs where decoration positions become stale
 * (e.g. if a plugin's update() callback sees the old state instead
 * of the new one).
 */
@OptIn(ExperimentalTestApi::class)
class HighlightingTest {

    /**
     * A highlight range: document positions [from, to) tagged with
     * a style class string.
     */
    data class HighlightRange(val from: Int, val to: Int, val cls: String)

    /**
     * A test plugin that mirrors TreeHighlighter's pattern — it uses
     * `session.state` (not `update.state`) to build highlight ranges.
     * This makes it sensitive to the dispatch-order bug: if session.state
     * is stale during update(), the ranges will be wrong.
     */
    private class HighlightTracker(view: EditorSession) : PluginValue {
        var ranges: List<HighlightRange> = buildRanges(view)
            private set

        override fun update(update: ViewUpdate) {
            if (update.docChanged) {
                // Intentionally uses update.session (like TreeHighlighter's
                // buildDeco(update.session)) to catch dispatch-order bugs.
                ranges = buildRanges(update.session)
            }
        }

        private fun buildRanges(view: EditorSession): List<HighlightRange> {
            val tree = syntaxTree(view.state)
            val result = mutableListOf<HighlightRange>()
            highlightTree(
                tree,
                defaultHighlightStyle,
                { from, to, style ->
                    result.add(HighlightRange(from, to, style))
                }
            )
            return result
        }
    }

    private fun buildExtensions(tracker: ViewPlugin<HighlightTracker>) = ExtensionList(
        listOf(
            keymapOf(standardKeymap),
            Language(jsParser, "javascript").extension,
            syntaxHighlighting(defaultHighlightStyle),
            tracker.asExtension()
        )
    )

    @Test
    fun highlightPositionsShiftAfterInsertion() {
        lateinit var trackerInstance: HighlightTracker
        val tracker = ViewPlugin.define(
            PluginSpec(
                create = { view ->
                    HighlightTracker(view).also { trackerInstance = it }
                }
            )
        )

        runEditorTest(
            doc = "var x = 1;",
            extensions = buildExtensions(tracker)
        ) { holder ->
            onNodeWithTag("KodeMirror").performMouseInput {
                click(Offset(10f, 15f))
            }
            waitForIdle()

            // Before typing: find the "var" keyword highlight (0-3)
            val before = trackerInstance.ranges
            val varBefore = before.find { it.from == 0 && it.to == 3 }
            assert(varBefore != null) {
                "Expected keyword highlight at 0-3 for 'var', " +
                    "got: $before"
            }

            // Move cursor to position 0 and type a space
            holder.session.dispatch(
                TransactionSpec(
                    selection = SelectionSpec.CursorSpec(DocPos(0))
                )
            )
            waitForIdle()
            onNodeWithTag("KodeMirror_input").performTextInput(" ")
            waitForIdle()

            holder.assertDoc(" var x = 1;")

            // After typing: "var" keyword should now be at 1-4
            val after = trackerInstance.ranges
            val varAfter = after.find { it.from == 1 && it.to == 4 }
            assert(varAfter != null) {
                "Expected keyword highlight at 1-4 for 'var' after " +
                    "inserting space, got: $after"
            }
            // The old position should NOT still be present
            val staleVar = after.find { it.from == 0 && it.to == 3 }
            assert(staleVar == null) {
                "Found stale keyword highlight at 0-3 after insertion — " +
                    "decorations were not rebuilt from new state"
            }
        }
    }

    @Test
    fun highlightPositionsCorrectAfterMultipleEdits() {
        lateinit var trackerInstance: HighlightTracker
        val tracker = ViewPlugin.define(
            PluginSpec(
                create = { view ->
                    HighlightTracker(view).also { trackerInstance = it }
                }
            )
        )

        runEditorTest(
            doc = "var x = 1;",
            extensions = buildExtensions(tracker)
        ) { holder ->
            onNodeWithTag("KodeMirror").performMouseInput {
                click(Offset(10f, 15f))
            }
            waitForIdle()

            // Type 3 spaces at position 0, one at a time
            holder.session.dispatch(
                TransactionSpec(
                    selection = SelectionSpec.CursorSpec(DocPos(0))
                )
            )
            waitForIdle()

            repeat(3) {
                onNodeWithTag("KodeMirror_input").performTextInput(" ")
                waitForIdle()
            }

            holder.assertDoc("   var x = 1;")

            // "var" should now be at positions 3-6
            val ranges = trackerInstance.ranges
            val varRange = ranges.find { it.from == 3 && it.to == 6 }
            assert(varRange != null) {
                "Expected keyword highlight at 3-6 for 'var' after " +
                    "inserting 3 spaces, got: $ranges"
            }
        }
    }

    @Test
    fun highlightPositionsCorrectAfterMidDocumentInsert() {
        lateinit var trackerInstance: HighlightTracker
        val tracker = ViewPlugin.define(
            PluginSpec(
                create = { view ->
                    HighlightTracker(view).also { trackerInstance = it }
                }
            )
        )

        // "return" keyword at position 15
        runEditorTest(
            doc = "var x = 1;\nvar y = 2;\nreturn x + y;",
            extensions = buildExtensions(tracker)
        ) { holder ->
            onNodeWithTag("KodeMirror").performMouseInput {
                click(Offset(10f, 15f))
            }
            waitForIdle()

            // Find "return" keyword before edit
            val before = trackerInstance.ranges
            val returnBefore = before.find {
                holder.session.state.doc
                    .sliceString(DocPos(it.from), DocPos(it.to)) ==
                    "return"
            }
            assert(returnBefore != null) {
                "Expected highlight range for 'return', got: $before"
            }
            val returnPos = returnBefore!!.from

            // Insert "Z" at position 5 (middle of first line)
            holder.session.dispatch(
                TransactionSpec(
                    selection = SelectionSpec.CursorSpec(DocPos(5))
                )
            )
            waitForIdle()
            onNodeWithTag("KodeMirror_input").performTextInput("Z")
            waitForIdle()

            // "return" should have shifted by 1
            val after = trackerInstance.ranges
            val returnAfter = after.find {
                holder.session.state.doc
                    .sliceString(DocPos(it.from), DocPos(it.to)) ==
                    "return"
            }
            assert(returnAfter != null) {
                "Expected highlight for 'return' after edit, got: $after"
            }
            assert(returnAfter!!.from == returnPos + 1) {
                "Expected 'return' at ${returnPos + 1} but was at " +
                    "${returnAfter.from}"
            }
        }
    }

    // ---- StreamLanguage highlighting tests ----

    private data class SimpleState(val inString: Boolean = false)

    private val simpleStreamParser = object : StreamParser<SimpleState> {
        override val name = "simple-lang"

        override fun startState(indentUnit: Int) = SimpleState()

        override fun token(stream: StringStream, state: SimpleState): String? {
            if (stream.match("//")) {
                stream.skipToEnd()
                return "comment"
            }
            if (stream.match("\"")) {
                while (!stream.eol()) {
                    if (stream.next() == "\"") break
                }
                return "string"
            }
            if (stream.match(Regex("\\d+")) != null) return "number"
            if (stream.match(
                    Regex("\\b(fn|let|if|else|return)\\b")
                ) != null
            ) {
                return "keyword"
            }
            if (stream.match(Regex("\\b(Int|String)\\b")) != null) {
                return "typeName"
            }
            stream.next()
            return null
        }

        override fun copyState(state: SimpleState) = state.copy()
    }

    @Test
    fun streamLanguageHighlightingProducesDecorations() {
        val simpleLang = StreamLanguage.define(simpleStreamParser)

        lateinit var trackerInstance: HighlightTracker
        val tracker = ViewPlugin.define(
            PluginSpec(
                create = { view ->
                    HighlightTracker(view).also { trackerInstance = it }
                }
            )
        )

        runEditorTest(
            doc = "fn test(n: Int): Int {\n  if n <= 1 { return n }\n}",
            extensions = basicSetup +
                LanguageSupport(simpleLang).extension +
                tracker.asExtension()
        ) { holder ->
            waitForIdle()

            val ranges = trackerInstance.ranges
            assert(ranges.isNotEmpty()) {
                "StreamLanguage highlighting should produce decoration " +
                    "ranges, got empty list. Tree: " +
                    syntaxTree(holder.session.state)
            }

            // "fn" keyword at position 0-2
            val fnRange = ranges.find { it.from == 0 && it.to == 2 }
            assert(fnRange != null) {
                "Expected highlight for 'fn' keyword at 0-2, " +
                    "got: $ranges"
            }
        }
    }

    @Test
    fun streamLanguageAnnotatedStringHasSpanStyles() {
        val simpleLang = StreamLanguage.define(simpleStreamParser)

        runEditorTest(
            doc = "fn foo",
            extensions = basicSetup +
                LanguageSupport(simpleLang).extension
        ) { holder ->
            waitForIdle()

            val state = holder.session.state
            val allDecos = state.facet(
                com.monkopedia.kodemirror.view.decorations
            )
            val impl = holder.session as com.monkopedia.kodemirror.view.EditorSessionImpl
            val pluginDecos = impl.pluginHost?.collectDecorations()
                ?: emptyList()
            val decoSets = allDecos + pluginDecos

            val items = com.monkopedia.kodemirror.view.buildColumnItems(
                state,
                com.monkopedia.kodemirror.view.Viewport(0, state.doc.length),
                decoSets
            )

            val textLine = items
                .filterIsInstance<com.monkopedia.kodemirror.view.ColumnItem.TextLine>()
                .first()
            val annotated = textLine.content
            val styles = annotated.spanStyles
            assert(styles.isNotEmpty()) {
                "Expected AnnotatedString to have SpanStyles from " +
                    "highlighting, got none. Text='${annotated.text}', " +
                    "allDecos=$allDecos, pluginDecos=$pluginDecos"
            }
        }
    }
}
