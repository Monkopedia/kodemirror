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
package com.monkopedia.kodemirror.vim

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.Text
import kotlin.math.max
import kotlin.math.min

/**
 * A position in the editor, using 0-based line numbers and character offsets.
 * This mirrors CM5's LinePos(line, ch) used throughout the vim engine.
 */
data class LinePos(val line: Int, val ch: Int, val sticky: String? = null) : Comparable<LinePos> {
    override fun compareTo(other: LinePos): Int {
        val lineCmp = line.compareTo(other.line)
        return if (lineCmp != 0) lineCmp else ch.compareTo(other.ch)
    }

    override fun toString(): String = "LinePos($line, $ch)"
}

/** Create a LinePos, mirroring the CM5 `LinePos(line, ch)` constructor. */
internal fun makeCursor(line: Int, ch: Int): LinePos = LinePos(line, ch)

/** Returns true if [a] is before [b] in the document. */
internal fun cursorIsBefore(a: LinePos, b: LinePos): Boolean =
    a.line < b.line || (a.line == b.line && a.ch < b.ch)

/** Returns true if the two positions are equal (ignoring sticky). */
internal fun cursorEqual(a: LinePos, b: LinePos): Boolean = a.line == b.line && a.ch == b.ch

/** Returns the earlier of two positions. */
internal fun cursorMin(a: LinePos, b: LinePos): LinePos = if (cursorIsBefore(a, b)) a else b

/** Returns the later of two positions. */
internal fun cursorMax(a: LinePos, b: LinePos): LinePos = if (cursorIsBefore(a, b)) b else a

// ---------------------------------------------------------------------------
// Position conversion helpers
// ---------------------------------------------------------------------------

/** Convert a vim LinePos (0-based line, ch) to an absolute document offset. */
internal fun indexFromPos(doc: Text, pos: LinePos): DocPos {
    var ch = pos.ch
    var lineNumber = pos.line + 1
    if (lineNumber < 1) {
        lineNumber = 1
        ch = 0
    }
    if (lineNumber > doc.lines) {
        lineNumber = doc.lines
        ch = Int.MAX_VALUE
    }
    val line = doc.line(LineNumber(lineNumber))
    // Clamp ch to line length to avoid integer overflow when ch is Int.MAX_VALUE
    val clampedCh = min(max(0, ch).toLong(), (line.to.value - line.from.value).toLong()).toInt()
    return DocPos(line.from.value + clampedCh)
}

/** Convert an absolute document offset to a vim LinePos (0-based line, ch). */
internal fun posFromIndex(doc: Text, offset: DocPos): LinePos {
    val line = doc.lineAt(offset)
    return LinePos(line.number.value - 1, offset.value - line.from.value)
}
