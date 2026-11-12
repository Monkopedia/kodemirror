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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.WidgetDecorationSpec
import com.monkopedia.kodemirror.view.WidgetType
import com.monkopedia.kodemirror.view.decorations
import com.monkopedia.kodemirror.view.lineWrapping
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Inline widget decorations render at their document position (#397), like
 * upstream `@codemirror/view`: a widget at `pos` sits between characters
 * `pos - 1` and `pos`, before the caret at `pos` when `side <= 0` and after it
 * when `side > 0`, and pushes the following text right by its width.
 *
 * Before #397 every inline widget was drawn at the line's origin, so each
 * assertion below on the widget's x against the caret's x failed by the width
 * of the text in front of it.
 *
 * All coordinates are editor-local px at density 1 ([runEditorTest]). The
 * assertions avoid assuming a monospace font: headless browsers render the
 * wasmJs suite with a proportional fallback font.
 */
@OptIn(ExperimentalTestApi::class)
class InlineWidgetPositionTest {

    private class BoxWidget(val tag: String, val height: Int) : WidgetType() {
        @Composable
        override fun Content() {
            Box(Modifier.size(20.dp, height.dp).testTag(tag))
        }
    }

    private fun widgets(vararg placed: Triple<Int, Int, String>, height: Int = 10): Extension {
        val builder = RangeSetBuilder<Decoration>()
        for ((pos, side, tag) in placed) {
            builder.add(
                DocPos(pos),
                DocPos(pos),
                Decoration.widget(WidgetDecorationSpec(BoxWidget(tag, height), side = side))
            )
        }
        return decorations.of(builder.finish())
    }

    private fun ComposeUiTest.widgetBounds(tag: String): Rect {
        val editor = onNodeWithTag("KodeMirror").getUnclippedBoundsInRoot()
        val widget = onNodeWithTag(tag).getUnclippedBoundsInRoot()
        return Rect(
            (widget.left - editor.left).value,
            (widget.top - editor.top).value,
            (widget.right - editor.left).value,
            (widget.bottom - editor.top).value
        )
    }

    private fun SessionHolder.caretX(pos: Int): Float =
        assertNotNull(session.coordsAtPos(pos), "no coords for $pos").left

    private fun assertNear(expected: Float, actual: Float, what: String) {
        assertTrue(abs(expected - actual) <= 1f, "$what: expected $expected but was $actual")
    }

    @Test
    fun positiveSideWidgetSitsAfterTheCaret() = runEditorTest(
        doc = "hello world",
        extensions = widgets(Triple(5, 1, "w"))
    ) { holder ->
        val widget = widgetBounds("w")
        assertTrue(widget.left > holder.caretX(4), "widget is after character 4")
        assertNear(holder.caretX(5), widget.left, "widget left vs caret at 5")
        // The text after the widget is pushed right past it.
        assertTrue(holder.caretX(6) > widget.right, "character 5 follows the widget")
    }

    @Test
    fun negativeSideWidgetSitsBeforeTheCaret() = runEditorTest(
        doc = "hello world",
        extensions = widgets(Triple(5, -1, "w"))
    ) { holder ->
        val widget = widgetBounds("w")
        assertTrue(widget.left > holder.caretX(4), "widget is after character 4")
        assertNear(holder.caretX(5), widget.right, "widget right vs caret at 5")
    }

    @Test
    fun widgetsAtOnePositionAreOrderedBySide() = runEditorTest(
        doc = "hello world",
        // Added in side order (RangeSet requires it); rendered in side order.
        extensions = widgets(Triple(5, -2, "a"), Triple(5, 0, "b"), Triple(5, 1, "c"))
    ) { holder ->
        val a = widgetBounds("a")
        val b = widgetBounds("b")
        val c = widgetBounds("c")
        assertNear(a.right, b.left, "a then b")
        assertNear(b.right, holder.caretX(5), "caret after the side <= 0 widgets")
        assertNear(holder.caretX(5), c.left, "side > 0 widget after the caret")
    }

    @Test
    fun widgetAtLineEndFollowsTheText() = runEditorTest(
        doc = "hello world\nsecond",
        extensions = widgets(Triple(11, 1, "w"))
    ) { holder ->
        val widget = widgetBounds("w")
        assertNear(holder.caretX(11), widget.left, "widget left vs line end")
        val line1Top = assertNotNull(holder.session.coordsAtPos(0)).top
        assertTrue(widget.top >= line1Top && widget.top < line1Top + 15f, "widget on line 1")
    }

    @Test
    fun widgetAfterATabAccountsForTheExpansion() = runEditorTest(
        doc = "\tx\n    x",
        extensions = widgets(Triple(1, 1, "w"))
    ) { holder ->
        val widget = widgetBounds("w")
        // The tab expands to four spaces, as wide as line 2's indentation.
        assertNear(holder.caretX(7), holder.caretX(1), "caret after tab")
        assertNear(holder.caretX(1), widget.left, "widget left vs caret at 1")
    }

    @Test
    fun widgetOnAWrappedRowSitsOnThatRow() = runEditorTest(
        doc = "word ".repeat(40),
        extensions = ExtensionList(listOf(lineWrapping, widgets(Triple(152, 1, "w")))),
        width = 300
    ) { holder ->
        val caret = assertNotNull(holder.session.coordsAtPos(152))
        val firstRowTop = assertNotNull(holder.session.coordsAtPos(0)).top
        assertTrue(caret.top > firstRowTop, "pos 152 wraps below the first row")
        val widget = widgetBounds("w")
        assertNear(caret.left, widget.left, "widget left vs caret at 152")
        assertTrue(
            widget.top >= caret.top - 1f && widget.bottom <= caret.bottom + 1f,
            "widget ${widget.top}..${widget.bottom} within caret row ${caret.top}..${caret.bottom}"
        )
    }

    @Test
    fun aTallWidgetDoesNotMoveTheLinesText() = runEditorTest(
        doc = "hello\nhello",
        // Taller than the line: it must not grow the line or shift its text.
        extensions = widgets(Triple(2, 0, "w"), height = 30)
    ) { holder ->
        val withWidget = assertNotNull(holder.session.coordsAtPos(4))
        val plain = assertNotNull(holder.session.coordsAtPos(10))
        // Line 2 has no widget, so its caret is where line 1's should be, one row (the default
        // 15sp * 1.4 line height, at density 1) further down.
        assertNear(DEFAULT_LINE_HEIGHT, plain.top - withWidget.top, "caret top, one row apart")
        assertNear(
            DEFAULT_LINE_HEIGHT,
            plain.bottom - withWidget.bottom,
            "caret bottom, one row apart"
        )
        // The widget is centred on the text, overflowing the line evenly.
        val widget = widgetBounds("w")
        assertNear((withWidget.top + withWidget.bottom) / 2, widget.center.y, "widget centre")
    }

    @Test
    fun hitTestingAroundAWidgetResolvesToDocumentPositions() = runEditorTest(
        doc = "hello world",
        extensions = widgets(Triple(5, 1, "w"))
    ) { holder ->
        val widget = widgetBounds("w")
        val y = widget.center.y
        // Anywhere on the widget resolves to the widget's own position.
        assertEquals(5, holder.session.posAtCoords(widget.left + 2f, y))
        assertEquals(5, holder.session.posAtCoords(widget.right - 2f, y))
        // The far half of character 6, two past the widget, resolves to 7.
        val x = holder.caretX(6) + 0.75f * (holder.caretX(7) - holder.caretX(6))
        assertEquals(7, holder.session.posAtCoords(x, y))
        // A real click there lands the cursor at the same place.
        onNodeWithTag("KodeMirror").performMouseInput { click(Offset(x, y)) }
        waitForIdle()
        holder.assertCursorAt(7)
    }

    private companion object {
        const val DEFAULT_LINE_HEIGHT = 21f
    }
}
