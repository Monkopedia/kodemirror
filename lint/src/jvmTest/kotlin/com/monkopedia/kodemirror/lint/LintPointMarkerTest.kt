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
package com.monkopedia.kodemirror.lint

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KodeMirror
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A diagnostic with nothing to underline is drawn as upstream's `cm-lintPoint` triangle under
 * its position (#397). Before, it was stored but drew nothing.
 *
 * These probe painted pixels: the marker is a few pixels in size, far below the screenshot
 * gate's tolerance, so only a pixel check at the expected coordinates can tell it is there.
 */
@OptIn(ExperimentalTestApi::class)
class LintPointMarkerTest {

    private fun DesktopComposeUiTest.render(
        doc: String,
        vararg diagnostics: Diagnostic
    ): EditorSession {
        lateinit var session: EditorSession
        setContent {
            session = remember {
                EditorSession(
                    EditorState.create(
                        EditorStateConfig(doc = doc.asDoc(), extensions = lintState)
                    )
                )
            }
            KodeMirror(session = session)
        }
        waitForIdle()
        runOnIdle { setDiagnostics(session, diagnostics.toList()) }
        waitForIdle()
        return session
    }

    private fun DesktopComposeUiTest.editorPixels(): PixelMap =
        onNodeWithTag("KodeMirror").captureToImage().toPixelMap()

    /** Pixels of [color] in the box around ([x], [y]) spanning [dx] and [dy] each way. */
    private fun PixelMap.count(color: Color, x: Float, y: Float, dx: Int, dy: Int): Int {
        var n = 0
        for (px in x.roundToInt() - dx..x.roundToInt() + dx) {
            for (py in y.roundToInt() - dy..y.roundToInt() + dy) {
                if (px !in 0 until width || py !in 0 until height) continue
                val c = this[px, py]
                if (abs(c.red - color.red) < 0.1f &&
                    abs(c.green - color.green) < 0.1f &&
                    abs(c.blue - color.blue) < 0.1f
                ) {
                    n++
                }
            }
        }
        return n
    }

    private val error = lintErrorColor.default
    private val warning = lintWarningColor.default

    @Test
    fun pointDiagnosticDrawsAMarkerUnderItsPosition() = runDesktopComposeUiTest {
        val session = render("hello world", Diagnostic(DocPos(5), DocPos(5), Severity.ERROR, "x"))
        val at = assertNotNull(session.coordsAtPos(5))
        val pixels = editorPixels()
        val marker = pixels.count(error, at.left + 1f, at.bottom - 2f, dx = 3, dy = 3)
        assert(marker > 0) { "no error-colored marker under position 5 (${at.left}, ${at.bottom})" }
        // Nothing at the start of the line, where widgets used to be drawn.
        val origin = assertNotNull(session.coordsAtPos(0))
        assertEquals(0, pixels.count(error, origin.left + 1f, origin.bottom - 2f, dx = 3, dy = 3))
        // The marker is zero-width: in the monospace editor font, the character after it is
        // as wide as the one before.
        val before = at.left - assertNotNull(session.coordsAtPos(4)).left
        val after = assertNotNull(session.coordsAtPos(6)).left - at.left
        assertEquals(before, after, 0.5f)
    }

    @Test
    fun lineBreakDiagnosticDrawsAMarkerAtTheLineEnd() = runDesktopComposeUiTest {
        // Spans only the line break after "hello": nothing a mark could underline.
        val session =
            render("hello\nworld", Diagnostic(DocPos(5), DocPos(6), Severity.WARNING, "x"))
        val at = assertNotNull(session.coordsAtPos(5))
        val marker = editorPixels().count(warning, at.left + 1f, at.bottom - 2f, dx = 3, dy = 3)
        assert(marker > 0) { "no warning-colored marker at the end of line 1" }
    }

    @Test
    fun overlappingPointsDrawOneMarkerOfTheHighestSeverity() = runDesktopComposeUiTest {
        // The warning comes second, so a marker per diagnostic would draw it over the error.
        // Upstream draws one marker for both, in the error's color (#419).
        val session = render(
            "hello world",
            Diagnostic(DocPos(5), DocPos(5), Severity.ERROR, "e"),
            Diagnostic(DocPos(5), DocPos(5), Severity.WARNING, "w")
        )
        val at = assertNotNull(session.coordsAtPos(5))
        val pixels = editorPixels()
        val errorPixels = pixels.count(error, at.left + 1f, at.bottom - 2f, dx = 3, dy = 3)
        val warningPixels = pixels.count(warning, at.left + 1f, at.bottom - 2f, dx = 3, dy = 3)
        assert(errorPixels > 0) { "no error-colored marker under position 5" }
        assertEquals(0, warningPixels, "warning-colored marker drawn under position 5")
    }
}
