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
package com.monkopedia.kodemirror.state

import kotlin.math.max
import kotlin.math.min

internal object Tree {
    const val BRANCH_SHIFT = 5
    const val BRANCH = 1 shl BRANCH_SHIFT
}

internal enum class Open(val value: Int) {
    None(0),
    From(1),
    To(2),
    Both(3);

    infix fun and(other: Open): Open = fromValue(value and other.value)
    infix fun or(other: Open): Open = fromValue(value or other.value)

    companion object {
        fun fromValue(v: Int): Open = when (v) {
            0 -> None
            1 -> From
            2 -> To
            3 -> Both
            else -> None
        }

        fun of(from: Boolean, to: Boolean): Open = fromValue(
            (if (from) From.value else 0) or (if (to) To.value else 0)
        )
    }
}

/**
 * A text iterator iterates over a sequence of strings. When
 * iterating over a [Text] document, result values will
 * either be lines or line breaks.
 */
interface TextIterator {
    /**
     * Retrieve the next string. Optionally skip a given number of
     * positions after the current position. Always returns the
     * object itself.
     */
    fun next(skip: Int = 0): TextIterator

    /**
     * The current string. Will be the empty string when the cursor
     * is at its end or `next` hasn't been called on it yet.
     */
    val value: String

    /**
     * Whether the end of the iteration has been reached. You should
     * probably check this right after calling `next`.
     */
    val done: Boolean

    /** Whether the current string represents a line break. */
    val lineBreak: Boolean
}

/** The data structure for documents. */
abstract class Text {
    /** The length of the string. */
    abstract val length: Int

    /** The number of lines in the string (always >= 1). */
    abstract val lines: Int

    /** Get the line description around the given position. */
    fun lineAt(pos: Int): Line {
        if (pos < 0 || pos > length) {
            throw IllegalArgumentException(
                "Invalid position $pos in document of length $length"
            )
        }
        return lineInner(pos, false, 1, 0)
    }

    /** Get the description for the given (1-based) line number. */
    fun line(n: Int): Line {
        if (n < 1 || n > lines) {
            throw IllegalArgumentException(
                "Invalid line number $n in $lines-line document"
            )
        }
        return lineInner(n, true, 1, 0)
    }

    internal abstract fun lineInner(target: Int, isLine: Boolean, line: Int, offset: Int): Line

    /** Replace a range of the text with the given content. */
    open fun replace(from: Int, to: Int, text: Text): Text {
        val (clippedFrom, clippedTo) = clip(this, from, to)
        val parts = mutableListOf<Text>()
        decompose(0, clippedFrom, parts, Open.To)
        if (text.length > 0) {
            text.decompose(
                0,
                text.length,
                parts,
                Open.Both
            )
        }
        decompose(clippedTo, length, parts, Open.From)
        return TextNode.from(
            parts,
            length - (clippedTo - clippedFrom) + text.length
        )
    }

    /** Append another document to this one. */
    fun append(other: Text): Text {
        return replace(length, length, other)
    }

    /** Retrieve the text between the given points. */
    fun slice(from: Int, to: Int = length): Text {
        val (clippedFrom, clippedTo) = clip(this, from, to)
        val parts = mutableListOf<Text>()
        decompose(clippedFrom, clippedTo, parts, Open.None)
        return TextNode.from(parts, clippedTo - clippedFrom)
    }

    /** Retrieve a part of the document as a string. */
    abstract fun sliceString(from: Int, to: Int = length, lineSep: String = "\n"): String

    /** Retrieve a part of the document as a string using an [IntRange]. */
    operator fun get(range: IntRange): String = sliceString(range.first, range.last + 1)

    /** True if this document has no content. */
    val isEmpty: Boolean get() = length == 0

    /** True if this document has content. */
    val isNotEmpty: Boolean get() = length > 0

    /** Return a sequence of lines in this document. */
    fun lineSequence(): Sequence<Line> = sequence {
        for (i in 1..lines) {
            yield(line(i))
        }
    }

    internal abstract fun flatten(target: MutableList<String>)

    internal abstract fun scanIdentical(other: Text, dir: Int): Int

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Text) return false
        if (other.length != length || other.lines != lines) {
            return false
        }
        val start = scanIdentical(other, 1)
        val end = length - scanIdentical(other, -1)
        val a = RawTextCursor(this)
        val b = RawTextCursor(other)
        var skip = start
        var pos = start
        while (true) {
            a.next(skip)
            b.next(skip)
            skip = 0
            if (a.lineBreak != b.lineBreak ||
                a.done != b.done ||
                a.value != b.value
            ) {
                return false
            }
            pos += a.value.length
            if (a.done || pos >= end) return true
        }
    }

    override fun hashCode(): Int {
        var result = length
        result = 31 * result + lines
        return result
    }

    /**
     * Iterate over the text. When [dir] is `-1`, iteration happens
     * from end to start. This will return lines and the breaks
     * between them as separate strings.
     */
    fun iter(dir: Int = 1): TextIterator = RawTextCursor(this, dir)

    /**
     * Iterate over a range of the text. When [from] > [to], the
     * iterator will run in reverse.
     */
    fun iterRange(from: Int, to: Int = length): TextIterator = PartialTextCursor(this, from, to)

    /**
     * Return a cursor that iterates over the given range of lines,
     * _without_ returning the line breaks between, and yielding
     * empty strings for empty lines.
     *
     * When [from] and [to] are given, they should be 1-based line
     * numbers.
     */
    fun iterLines(from: Int? = null, to: Int? = null): TextIterator {
        val inner: TextIterator
        if (from == null) {
            inner = iter()
        } else {
            val toLine = to ?: (lines + 1)
            val start = line(from).from
            inner = iterRange(
                start,
                max(
                    start,
                    when {
                        toLine == lines + 1 -> length
                        toLine <= 1 -> 0
                        else -> line(toLine - 1).to
                    }
                )
            )
        }
        return LineCursor(inner)
    }

    internal abstract fun decompose(from: Int, to: Int, target: MutableList<Text>, open: Open)

    /**
     * Return the document as a string, using newline characters to
     * separate lines.
     */
    override fun toString(): String = sliceString(0)

    /**
     * Convert the document to an array of lines (which can be
     * deserialized again via [Text.of]).
     */
    fun toJSON(): List<String> {
        val lines = mutableListOf<String>()
        flatten(lines)
        return lines
    }

    /**
     * If this is a branch node, [children] will hold the [Text]
     * objects that it is made up of. For leaf nodes, this holds
     * null.
     */
    abstract val children: List<Text>?

    companion object {
        /** Create a [Text] instance for the given array of lines. */
        fun of(text: List<String>): Text {
            if (text.isEmpty()) {
                throw IllegalArgumentException(
                    "A document must have at least one line"
                )
            }
            if (text.size == 1 && text[0].isEmpty()) return empty
            return if (text.size <= Tree.BRANCH) {
                TextLeaf(text)
            } else {
                TextNode.from(TextLeaf.split(text, mutableListOf()))
            }
        }

        /** The empty document. */
        val empty: Text = TextLeaf(listOf(""), 0)
    }
}

// Leaves store an array of line strings. There are always line
// breaks between these strings. Leaves are limited in size and
// have to be contained in TextNode instances for bigger documents.
internal class TextLeaf(
    val text: List<String>,
    override val length: Int = textLength(text)
) : Text() {
    override val lines: Int get() = text.size

    override val children: List<Text>? get() = null

    override fun lineInner(target: Int, isLine: Boolean, line: Int, offset: Int): Line {
        var currentLine = line
        var currentOffset = offset
        for (i in text.indices) {
            val str = text[i]
            val end = currentOffset + str.length
            if ((if (isLine) currentLine else end) >= target) {
                return Line(currentOffset, end, currentLine, str)
            }
            currentOffset = end + 1
            currentLine++
        }
        throw IllegalStateException("unreachable")
    }

    override fun decompose(from: Int, to: Int, target: MutableList<Text>, open: Open) {
        val text = if (from <= 0 && to >= length) {
            this
        } else {
            TextLeaf(
                sliceText(this.text, from, to),
                min(to, length) - max(0, from)
            )
        }
        if (open.value and Open.From.value != 0) {
            val prev = target.removeAt(target.size - 1) as TextLeaf
            val joined = appendText(
                text.text,
                prev.text.toMutableList(),
                0,
                text.length
            )
            if (joined.size <= Tree.BRANCH) {
                target.add(
                    TextLeaf(joined, prev.length + text.length)
                )
            } else {
                val mid = joined.size shr 1
                target.add(TextLeaf(joined.subList(0, mid)))
                target.add(TextLeaf(joined.subList(mid, joined.size)))
            }
        } else {
            target.add(text)
        }
    }

    override fun replace(from: Int, to: Int, text: Text): Text {
        if (text !is TextLeaf) return super.replace(from, to, text)
        val (clippedFrom, clippedTo) = clip(this, from, to)
        val lines = appendText(
            this.text,
            appendText(
                text.text,
                sliceText(this.text, 0, clippedFrom)
            ),
            clippedTo
        )
        val newLen = length + text.length - (clippedTo - clippedFrom)
        if (lines.size <= Tree.BRANCH) return TextLeaf(lines, newLen)
        return TextNode.from(
            TextLeaf.split(lines, mutableListOf()),
            newLen
        )
    }

    override fun sliceString(from: Int, to: Int, lineSep: String): String {
        val (clippedFrom, clippedTo) = clip(this, from, to)
        val result = StringBuilder()
        var pos = 0
        for (i in text.indices) {
            val line = text[i]
            val end = pos + line.length
            if (pos > clippedFrom && i > 0) result.append(lineSep)
            if (clippedFrom < end && clippedTo > pos) {
                result.append(
                    line.substring(
                        max(0, clippedFrom - pos),
                        min(line.length, clippedTo - pos)
                    )
                )
            }
            pos = end + 1
            if (pos > clippedTo) break
        }
        return result.toString()
    }

    override fun flatten(target: MutableList<String>) {
        for (line in text) target.add(line)
    }

    override fun scanIdentical(other: Text, dir: Int): Int = 0

    companion object {
        fun split(text: List<String>, target: MutableList<Text>): MutableList<Text> {
            val part = mutableListOf<String>()
            var len = -1
            for (line in text) {
                part.add(line)
                len += line.length + 1
                if (part.size == Tree.BRANCH) {
                    target.add(TextLeaf(part.toList(), len))
                    part.clear()
                    len = -1
                }
            }
            if (len > -1) target.add(TextLeaf(part.toList(), len))
            return target
        }
    }
}

// Nodes provide the tree structure of the `Text` type. They store
// a number of other nodes or leaves, taking care to balance
// themselves on changes. There are implied line breaks _between_
// the children of a node (but not before the first or after the
// last child).
internal class TextNode(
    override val children: List<Text>,
    override val length: Int
) : Text() {
    override var lines: Int = 0
        private set

    init {
        for (child in children) lines += child.lines
    }

    override fun lineInner(target: Int, isLine: Boolean, line: Int, offset: Int): Line {
        var currentLine = line
        var currentOffset = offset
        for (i in children.indices) {
            val child = children[i]
            val end = currentOffset + child.length
            val endLine = currentLine + child.lines - 1
            if ((if (isLine) endLine else end) >= target) {
                return child.lineInner(
                    target,
                    isLine,
                    currentLine,
                    currentOffset
                )
            }
            currentOffset = end + 1
            currentLine = endLine + 1
        }
        throw IllegalStateException("unreachable")
    }

    override fun decompose(from: Int, to: Int, target: MutableList<Text>, open: Open) {
        var pos = 0
        for (i in children.indices) {
            if (pos > to) break
            val child = children[i]
            val end = pos + child.length
            if (from <= end && to >= pos) {
                val childOpen = Open.fromValue(
                    open.value and (
                        (if (pos <= from) Open.From.value else 0) or
                            (if (end >= to) Open.To.value else 0)
                        )
                )
                if (pos >= from && end <= to &&
                    childOpen == Open.None
                ) {
                    target.add(child)
                } else {
                    child.decompose(
                        from - pos,
                        to - pos,
                        target,
                        childOpen
                    )
                }
            }
            pos = end + 1
        }
    }

    override fun replace(from: Int, to: Int, text: Text): Text {
        val (clippedFrom, clippedTo) = clip(this, from, to)
        if (text.lines < lines) {
            var pos = 0
            for (i in children.indices) {
                val child = children[i]
                val end = pos + child.length
                if (clippedFrom >= pos && clippedTo <= end) {
                    val updated = child.replace(
                        clippedFrom - pos,
                        clippedTo - pos,
                        text
                    )
                    val totalLines =
                        lines - child.lines + updated.lines
                    if (updated.lines <
                        (totalLines shr (Tree.BRANCH_SHIFT - 1)) &&
                        updated.lines >
                        (totalLines shr (Tree.BRANCH_SHIFT + 1))
                    ) {
                        val copy = children.toMutableList()
                        copy[i] = updated
                        return TextNode(
                            copy,
                            length -
                                (clippedTo - clippedFrom) +
                                text.length
                        )
                    }
                    return super.replace(pos, end, updated)
                }
                pos = end + 1
            }
        }
        return super.replace(clippedFrom, clippedTo, text)
    }

    override fun sliceString(from: Int, to: Int, lineSep: String): String {
        val (clippedFrom, clippedTo) = clip(this, from, to)
        val result = StringBuilder()
        var pos = 0
        for (i in children.indices) {
            if (pos > clippedTo) break
            val child = children[i]
            val end = pos + child.length
            if (pos > clippedFrom && i > 0) result.append(lineSep)
            if (clippedFrom < end && clippedTo > pos) {
                result.append(
                    child.sliceString(
                        clippedFrom - pos,
                        clippedTo - pos,
                        lineSep
                    )
                )
            }
            pos = end + 1
        }
        return result.toString()
    }

    override fun flatten(target: MutableList<String>) {
        for (child in children) child.flatten(target)
    }

    override fun scanIdentical(other: Text, dir: Int): Int {
        if (other !is TextNode) return 0
        var length = 0
        val iAStart: Int
        val iBStart: Int
        val eA: Int
        val eB: Int
        if (dir > 0) {
            iAStart = 0
            iBStart = 0
            eA = children.size
            eB = other.children.size
        } else {
            iAStart = children.size - 1
            iBStart = other.children.size - 1
            eA = -1
            eB = -1
        }
        var iA = iAStart
        var iB = iBStart
        while (true) {
            if (iA == eA || iB == eB) return length
            val chA = children[iA]
            val chB = other.children[iB]
            if (chA !== chB) {
                return length + chA.scanIdentical(chB, dir)
            }
            length += chA.length + 1
            iA += dir
            iB += dir
        }
    }

    companion object {
        fun from(
            children: MutableList<Text>,
            length: Int = children.fold(-1) { l, ch ->
                l + ch.length + 1
            }
        ): Text {
            var lines = 0
            for (ch in children) lines += ch.lines
            if (lines < Tree.BRANCH) {
                val flat = mutableListOf<String>()
                for (ch in children) ch.flatten(flat)
                return TextLeaf(flat, length)
            }
            val chunk = max(
                Tree.BRANCH,
                lines shr Tree.BRANCH_SHIFT
            )
            val maxChunk = chunk shl 1
            val minChunk = chunk shr 1
            val chunked = mutableListOf<Text>()
            var currentLines = 0
            var currentLen = -1
            val currentChunk = mutableListOf<Text>()

            fun flush() {
                if (currentLines == 0) return
                chunked.add(
                    if (currentChunk.size == 1) {
                        currentChunk[0]
                    } else {
                        from(
                            currentChunk.toMutableList(),
                            currentLen
                        )
                    }
                )
                currentLen = -1
                currentLines = 0
                currentChunk.clear()
            }

            fun add(child: Text) {
                if (child.lines > maxChunk &&
                    child is TextNode
                ) {
                    for (node in child.children) add(node)
                } else if (child.lines > minChunk &&
                    (currentLines > minChunk || currentLines == 0)
                ) {
                    flush()
                    chunked.add(child)
                } else if (child is TextLeaf &&
                    currentLines > 0 &&
                    currentChunk.isNotEmpty() &&
                    currentChunk.last() is TextLeaf
                ) {
                    val last = currentChunk.last() as TextLeaf
                    if (child.lines + last.lines <= Tree.BRANCH) {
                        currentLines += child.lines
                        currentLen += child.length + 1
                        currentChunk[currentChunk.size - 1] =
                            TextLeaf(
                                last.text + child.text,
                                last.length + 1 + child.length
                            )
                    } else {
                        if (currentLines + child.lines > chunk) {
                            flush()
                        }
                        currentLines += child.lines
                        currentLen += child.length + 1
                        currentChunk.add(child)
                    }
                } else {
                    if (currentLines + child.lines > chunk) {
                        flush()
                    }
                    currentLines += child.lines
                    currentLen += child.length + 1
                    currentChunk.add(child)
                }
            }

            for (child in children) add(child)
            flush()
            return if (chunked.size == 1) {
                chunked[0]
            } else {
                TextNode(chunked, length)
            }
        }
    }
}

private fun textLength(text: List<String>): Int {
    var length = -1
    for (line in text) length += line.length + 1
    return length
}

private fun appendText(
    text: List<String>,
    target: MutableList<String>,
    from: Int = 0,
    to: Int = Int.MAX_VALUE
): MutableList<String> {
    var first = true
    var pos = 0
    for (i in text.indices) {
        if (pos > to) break
        var line = text[i]
        val end = pos + line.length
        if (end >= from) {
            if (end > to) line = line.substring(0, to - pos)
            if (pos < from) line = line.substring(from - pos)
            if (first) {
                target[target.size - 1] =
                    target[target.size - 1] + line
                first = false
            } else {
                target.add(line)
            }
        }
        pos = end + 1
    }
    return target
}

private fun sliceText(text: List<String>, from: Int, to: Int): MutableList<String> {
    return appendText(text, mutableListOf(""), from, to)
}

internal class RawTextCursor(
    text: Text,
    val dir: Int = 1
) : TextIterator {
    override var done: Boolean = false
        private set
    override var lineBreak: Boolean = false
        private set
    override var value: String = ""
        private set
    private val nodes = mutableListOf<Text>(text)
    private val offsets = mutableListOf(
        if (dir > 0) {
            1
        } else {
            val size = if (text is TextLeaf) {
                text.text.size
            } else {
                text.children!!.size
            }
            size shl 1
        }
    )

    private fun nextInner(skip: Int, dir: Int): RawTextCursor {
        var remaining = skip
        done = false
        lineBreak = false
        while (true) {
            val last = nodes.size - 1
            val top = nodes[last]
            val offsetValue = offsets[last]
            val offset = offsetValue shr 1
            val size = if (top is TextLeaf) {
                top.text.size
            } else {
                top.children!!.size
            }
            if (offset == (if (dir > 0) size else 0)) {
                if (last == 0) {
                    done = true
                    value = ""
                    return this
                }
                if (dir > 0) offsets[last - 1]++
                nodes.removeAt(last)
                offsets.removeAt(last)
            } else if (
                (offsetValue and 1) ==
                (if (dir > 0) 0 else 1)
            ) {
                offsets[last] = offsets[last] + dir
                if (remaining == 0) {
                    lineBreak = true
                    value = "\n"
                    return this
                }
                remaining--
            } else if (top is TextLeaf) {
                val next =
                    top.text[offset + (if (dir < 0) -1 else 0)]
                offsets[last] = offsets[last] + dir
                if (next.length > max(0, remaining)) {
                    value = when {
                        remaining == 0 -> next
                        dir > 0 -> next.substring(remaining)
                        else -> next.substring(
                            0, next.length - remaining
                        )
                    }
                    return this
                }
                remaining -= next.length
            } else {
                val children = top.children!!
                val next =
                    children[offset + (if (dir < 0) -1 else 0)]
                if (remaining > next.length) {
                    remaining -= next.length
                    offsets[last] = offsets[last] + dir
                } else {
                    if (dir < 0) {
                        offsets[last] = offsets[last] - 1
                    }
                    nodes.add(next)
                    val nextSize = if (next is TextLeaf) {
                        next.text.size
                    } else {
                        next.children!!.size
                    }
                    offsets.add(
                        if (dir > 0) 1 else nextSize shl 1
                    )
                }
            }
        }
    }

    override fun next(skip: Int): RawTextCursor {
        var s = skip
        if (s < 0) {
            nextInner(-s, -dir)
            s = value.length
        }
        return nextInner(s, dir)
    }
}

private class PartialTextCursor(
    text: Text,
    start: Int,
    end: Int
) : TextIterator {
    private val cursor: RawTextCursor =
        RawTextCursor(text, if (start > end) -1 else 1)
    override var value: String = ""
        private set
    private var pos: Int = if (start > end) text.length else 0
    private val from: Int = min(start, end)
    private val to: Int = max(start, end)
    override var done: Boolean = false
        private set

    private fun nextInner(skip: Int, dir: Int): PartialTextCursor {
        if (if (dir < 0) pos <= from else pos >= to) {
            value = ""
            done = true
            return this
        }
        var s = skip + max(
            0,
            if (dir < 0) pos - to else from - pos
        )
        var limit = if (dir < 0) pos - from else to - pos
        if (s > limit) s = limit
        limit -= s
        val cursorValue = cursor.next(s).value
        pos += (cursorValue.length + s) * dir
        value = if (cursorValue.length <= limit) {
            cursorValue
        } else if (dir < 0) {
            cursorValue.substring(cursorValue.length - limit)
        } else {
            cursorValue.substring(0, limit)
        }
        done = value.isEmpty()
        return this
    }

    override fun next(skip: Int): PartialTextCursor {
        var s = skip
        if (s < 0) {
            s = max(s, from - pos)
        } else if (s > 0) {
            s = min(s, to - pos)
        }
        return nextInner(s, cursor.dir)
    }

    override val lineBreak: Boolean
        get() = cursor.lineBreak && value.isNotEmpty()
}

private class LineCursor(
    private val inner: TextIterator
) : TextIterator {
    private var afterBreak = true
    override var value: String = ""
        private set
    override var done: Boolean = false
        private set

    override fun next(skip: Int): LineCursor {
        inner.next(skip)
        val innerDone = inner.done
        val innerLineBreak = inner.lineBreak
        val innerValue = inner.value
        if (innerDone && afterBreak) {
            value = ""
            afterBreak = false
        } else if (innerDone) {
            done = true
            value = ""
        } else if (innerLineBreak) {
            if (afterBreak) {
                value = ""
            } else {
                afterBreak = true
                next()
            }
        } else {
            value = innerValue
            afterBreak = false
        }
        return this
    }

    override val lineBreak: Boolean get() = false
}

/**
 * This type describes a line in the document. It is created
 * on-demand when lines are [queried][Text.lineAt].
 */
class Line internal constructor(
    /** The position of the start of the line. */
    val from: Int,
    /**
     * The position at the end of the line (_before_ the line
     * break, or at the end of document for the last line).
     */
    val to: Int,
    /** This line's line number (1-based). */
    val number: Int,
    /** The line's content. */
    val text: String
) {
    /**
     * The length of the line (not including any line break after
     * it).
     */
    val length: Int get() = to - from
}

private fun clip(text: Text, from: Int, to: Int): Pair<Int, Int> {
    val clippedFrom = max(0, min(text.length, from))
    return Pair(
        clippedFrom,
        max(clippedFrom, min(text.length, to))
    )
}
