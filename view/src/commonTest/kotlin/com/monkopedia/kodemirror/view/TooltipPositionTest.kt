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
package com.monkopedia.kodemirror.view

import androidx.compose.ui.unit.IntOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [computeTooltipOffset] — the viewport-clamping / flip geometry
 * behind hover-tooltip positioning (#110). A hover tooltip used to be placed at
 * the raw caret coordinate, so near a viewport edge (or for a multi-line hover
 * range whose end sits low in the viewport) it rendered offscreen.
 */
class TooltipPositionTest {

    private fun offset(
        anchorLeft: Int = 0,
        anchorTop: Int = 0,
        coordsLeft: Int = 0,
        coordsTop: Int = 0,
        coordsBottom: Int = 0,
        above: Boolean = false,
        strictSide: Boolean = false,
        contentWidth: Int = 100,
        contentHeight: Int = 40,
        windowWidth: Int = 800,
        windowHeight: Int = 600,
        gap: Int = 4
    ): IntOffset = computeTooltipOffset(
        anchorLeft, anchorTop, coordsLeft, coordsTop, coordsBottom,
        above, strictSide, contentWidth, contentHeight,
        windowWidth, windowHeight, gap
    )

    @Test
    fun placesBelowWhenItFits() {
        // caret at (10, top=20, bottom=36); below = bottom + gap.
        assertEquals(IntOffset(10, 40), offset(coordsLeft = 10, coordsTop = 20, coordsBottom = 36))
    }

    @Test
    fun clampsHorizontallyAtRightEdge() {
        // left 750 + width 100 would overflow window 800 → clamp to 700.
        assertEquals(700, offset(coordsLeft = 750).x)
    }

    @Test
    fun clampsHorizontallyAtLeftEdge() {
        assertEquals(0, offset(coordsLeft = -5).x)
    }

    @Test
    fun flipsAboveWhenBelowOverflows() {
        // window 100 tall; below (66+4=70, +40=110) overflows; above (50-4-40=6) fits.
        assertEquals(
            6,
            offset(coordsTop = 50, coordsBottom = 66, windowHeight = 100).y
        )
    }

    @Test
    fun flipsBelowWhenAboveOverflows() {
        // above requested but above (10-4-40=-34) underflows; below (26+4=30) fits.
        assertEquals(
            30,
            offset(coordsTop = 10, coordsBottom = 26, above = true).y
        )
    }

    @Test
    fun placesAboveWhenRequestedAndFits() {
        assertEquals(
            56,
            offset(coordsTop = 100, coordsBottom = 116, above = true).y
        )
    }

    @Test
    fun strictSideDoesNotFlipButStillClamps() {
        // strictSide keeps it below even though below overflows; final clamp pins
        // it to windowHeight - contentHeight = 100 - 40 = 60.
        assertEquals(
            60,
            offset(coordsTop = 50, coordsBottom = 66, windowHeight = 100, strictSide = true).y
        )
    }

    @Test
    fun tooltipTallerThanWindowPinsToTop() {
        assertEquals(0, offset(coordsBottom = 200, contentHeight = 700).y)
    }

    @Test
    fun appliesAnchorOffsetToWindowCoordinates() {
        // editor area at (200, 100) in the window; caret-relative coords add onto it.
        assertEquals(
            IntOffset(210, 140),
            offset(anchorLeft = 200, anchorTop = 100, coordsLeft = 10, coordsTop = 20, coordsBottom = 36)
        )
    }
}
