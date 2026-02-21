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
    // The branch factor as an exponent of 2
    val BranchShift = 5

    // The approximate branch factor of the tree (both in leaf and
    // branch nodes)
    val Branch = 1 shl BranchShift
}

// Flags passed to decompose
internal sealed class Open(val from: Boolean, val to: Boolean) {
    data object From : Open(true, false)
    data object To : Open(false, true)
    data object Either : Open(true, true)
    data object Neither : Open(false, false)
    companion object : (Boolean, Boolean) -> Open {
        override fun invoke(p1: Boolean, p2: Boolean): Open {
            return when {
                p1 && p1 -> Either
                p1 -> From
                p2 -> To
                else -> Neither
            }
        }
    }
}

// / A text iterator iterates over a sequence of strings. When
// / iterating over a [`Text`](#state.Text) document, result values will
// / either be lines or line breaks.
interface TextIterator<T : TextIterator<T>> : Iterator<String>, Iterable<String> {
    override fun next(): String {
        next(null)
        return value
    }

    override fun iterator(): Iterator<String> {
        return this
    }

    // / Retrieve the next string. Optionally skip a given Int of
    // / positions after the current position. Always returns the object
    // / itself.
    fun next(skip: Int?): T

    // / The current string. Will be the empty string when the cursor is
    // / at its end or `next` hasn't been called on it yet.
    val value: String

    override fun hasNext(): Boolean {
        return !done
    }

    // / Whether the end of the iteration has been reached. You should
    // / probably check this right after calling `next`.
    val done: Boolean

    // / Whether the current string represents a line break.
    val lineBreak: Boolean
}

val String.asText: Text
    get() = Text.of(listOf(this))

// / The data structure for documents. @nonabstract
abstract class Text internal constructor() : Iterable<String> {
    // / The length of the string.
    abstract val length: Int

    // / The Int of lines in the string (always >= 1).
    abstract val lines: Int

    // / Get the line description around the given position.
    fun lineAt(pos: Int): Line {
        if (pos < 0 || pos > this.length) {
            throw IllegalArgumentException(
                "Invalid position $pos in document of length ${this.length}"
            )
        }
        return this.lineInner(pos, false, 1, 0)
    }

    // / Get the description for the given (1-based) line Int.
    fun line(n: Int): Line {
        if (n < 1 || n > this.lines) {
            throw IllegalArgumentException(
                "Invalid line Int $n in ${this.lines}-line document"
            )
        }
        return this.lineInner(n, true, 1, 0)
    }

    // / @internal
    internal abstract fun lineInner(target: Int, isLine: Boolean, line: Int, offset: Int): Line

    // / Replace a range of the text with the given content.
    open fun replace(from: Int, to: Int, text: Text): Text {
        val (clipFrom, clipTo) = clip(this, from, to)
        val parts = mutableListOf<Text>()
        this.decompose(0, clipFrom, parts, Open.To)
        if (text.length != 0) text.decompose(0, text.length, parts, Open.Either)
        this.decompose(clipTo, this.length, parts, Open.From)
        return TextNode.from(parts, this.length - (clipTo - clipFrom) + text.length)
    }

    // / Append another document to this one.
    fun append(other: Text): Text {
        return this.replace(this.length, this.length, other)
    }

    // / Retrieve the text between the given points.
    fun slice(from: Int, to: Int? = null): Text {
        val (clipFrom, clipTo) = clip(this, from, to ?: this.length)
        val parts = mutableListOf<Text>()
        this.decompose(clipFrom, clipTo, parts, Open.Neither)
        return TextNode.from(parts, clipTo - clipFrom)
    }

    // / Retrieve a part of the document as a string
    abstract fun sliceString(from: Int, to: Int? = null, lineSep: String? = null): String

    // / @internal
    internal abstract fun flatten(
        target: MutableList<String> = mutableListOf()
    ): MutableList<String>

    // / @internal
    internal abstract fun scanIdentical(other: Text, dir: Boolean): Int

    // / Test whether this text is equal to another instance.
    fun eq(other: Text): Boolean {
        if (other == this) return true
        if (other.length != this.length || other.lines != this.lines) return false
        val start = this.scanIdentical(other, true)
        val end = this.length - this.scanIdentical(other, false)
        val a = RawTextCursor(this)
        val b = RawTextCursor(other)
        var skip = start
        var pos = start
        while (true) {
            a.next(skip)
            b.next(skip)
            skip = 0
            if (a.lineBreak != b.lineBreak || a.done != b.done || a.value != b.value) return false
            pos += a.value.length
            if (a.done || pos >= end) return true
        }
    }

    override fun iterator(): Iterator<String> {
        return iter()
    }

    // / Iterate over the text. When `dir` is `-1`, iteration happens
    // / from end to start. This will return lines and the breaks between
    // / them as separate strings.
    fun iter(dir: Boolean = true): TextIterator<*> {
        return RawTextCursor(this, dir)
    }

    // / Iterate over a range of the text. When `from` > `to`, the
    // / iterator will run in reverse.
    fun iterRange(from: Int, to: Int = this.length): TextIterator<*> {
        return PartialTextCursor(this, from, to)
    }

    // / Return a cursor that iterates over the given range of lines,
    // / _without_ returning the line breaks between, and yielding empty
    // / strings for empty lines.
    // /
    // / When `from` and `to` are given, they should be 1-based line Ints.
    fun iterLines(from: Int? = null, to: Int? = null): TextIterator<*> {
        val inner = if (from == null) {
            this.iter()
        } else {
            val to = to ?: (this.lines + 1)
            val start = this.line(from).from
            this.iterRange(
                start,
                max(
                    start,
                    when {
                        to == this.lines + 1 -> this.length
                        to <= 1 -> 0
                        else -> this.line(to - 1).to
                    }
                )
            )
        }
        return LineCursor(inner)
    }

    // / @internal
    internal abstract fun decompose(from: Int, to: Int, target: MutableList<Text>, open: Open)

    // / Return the document as a string, using newline characters to
    // / separate lines.
    override fun toString(): String {
        return this.sliceString(0)
    }

    // / Convert the document to an array of lines (which can be
    // / deserialized again via [`Text.of`](#state.Text^of)).
//    fun toJSON() {
//        val lines: String[] = []
//        this.flatten(lines)
//        return lines
//    }

    // / If this is a branch node, `children` will hold the `Text`
    // / objects that it is made up of. For leaf nodes, this holds null.
    abstract val children: List<Text>?

//    abstract val iterator: () -> Iterator<String>
//    [Symbol.iterator]!: () => Iterator<string>

    companion object {
        // / Create a `Text` instance for the given array of lines.
        fun of(text: List<String>): Text {
            if (text.isEmpty()) {
                throw IllegalArgumentException(
                    "A document must have at least one line"
                )
            }
            if (text.isEmpty() || text.singleOrNull()?.isBlank() == true) return empty
            return if (text.size <= Tree.Branch) {
                TextLeaf(text)
            } else {
                TextNode.from(TextLeaf.split(text))
            }
        }

        // / The empty document.
        val empty: Text = TextLeaf(listOf(""), 0)
    }
}

// Leaves store an array of line strings. There are always line breaks
// between these strings. Leaves are limited in size and have to be
// contained in TextNode instances for bigger documents.
internal class TextLeaf(val text: List<String>, override val length: Int = textLength(text)) :
    Text() {

    override val lines: Int
        get() {
            return this.text.size
        }

    override val children: Nothing?
        get() {
            return null
        }

    override fun lineInner(target: Int, isLine: Boolean, line: Int, offset: Int): Line {
        var i = 0
        var vline = line
        var voffset = offset
        this.text.forEach { string ->
            val end = voffset + string.length
            if ((if (isLine) vline else end) >= target) {
                return Line(voffset, end, vline, string)
            }
            voffset = end + 1
            vline++
        }
        throw IllegalStateException("Not found")
    }

    override fun decompose(from: Int, to: Int, target: MutableList<Text>, open: Open) {
        val text = if (from <= 0 && to >= this.length) {
            this
        } else {
            TextLeaf(
                sliceText(this.text, from, to),
                to.coerceAtMost(this.length) - from.coerceAtLeast(0)
            )
        }
        if (open.from) {
            val prev = target.removeLast() as TextLeaf
            val joined = appendText(text.text, prev.text.toMutableList(), 0, text.length)
            if (joined.size <= Tree.Branch) {
                target.add(TextLeaf(joined, prev.length + text.length))
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
        val (clipFrom, clipTo) = clip(this, from, to)
        val lines = appendText(
            this.text,
            appendText(text.text, sliceText(this.text, 0, clipFrom).toMutableList())
                .toMutableList(),
            clipTo
        )
        val newLen = this.length + text.length - (clipTo - clipFrom)
        if (lines.size <= Tree.Branch) return TextLeaf(lines, newLen)
        return TextNode.from(TextLeaf.split(lines), newLen)
    }

    override fun sliceString(from: Int, to: Int?, lineSep: String?): String {
        val (clipFrom, clipTo) = clip(this, from, to ?: this.length)
        var result = ""
        var pos = 0
        for (i in this.text.indices) {
            val line = this.text[i]
            val end = pos + line.length
            if (pos > clipFrom && i != 0) {
                result += lineSep ?: "\n"
            }
            if (clipFrom < end && clipTo > pos) {
                result += line.substring(max(0, clipFrom - pos), min(clipTo - pos, line.length))
            }
            pos = end + 1
            if (pos > clipTo) break
        }
        return result
    }

    override fun flatten(target: MutableList<String>): MutableList<String> {
        target.addAll(text)
        return target
    }

    override fun scanIdentical(other: Text, dir: Boolean): Int {
        return 0
    }

    companion object {
        fun split(text: List<String>, target: MutableList<Text> = mutableListOf()): List<Text> {
            var part = mutableListOf<String>()
            var len = -1
            for (line in text) {
                part.add(line)
                len += line.length + 1
                if (part.size == Tree.Branch) {
                    target.add(TextLeaf(part, len))
                    part = mutableListOf()
                    len = -1
                }
            }
            if (len > -1) target.add(TextLeaf(part, len))
            return target
        }
    }
}

// Nodes provide the tree structure of the `Text` type. They store a
// Int of other nodes or leaves, taking care to balance themselves
// on changes. There are implied line breaks _between_ the children of
// a node (but not before the first or after the last child).
internal class TextNode(override val children: List<Text>, override val length: Int) : Text() {
    override var lines = children.sumOf { it.lines }
        private set

    override fun lineInner(target: Int, isLine: Boolean, line: Int, offset: Int): Line {
        var i = 0
        var vline = line
        var voffset = offset
        while (true) {
            val child = this.children[i]
            val end = voffset + child.length
            val endLine = vline + child.lines - 1
            if ((if (isLine) endLine else end) >= target) {
                return child.lineInner(target, isLine, vline, voffset)
            }
            voffset = end + 1
            vline = endLine + 1
            i++
        }
    }

    override fun decompose(from: Int, to: Int, target: MutableList<Text>, open: Open) {
        var pos = 0
        children.forEach { child ->
            val end = pos + child.length
            if (from <= end && to >= pos) {
                val childOpen = Open(
                    if (pos <= from) open.from else false,
                    if (end >= to) open.to else false
                )
                if (pos >= from && end <= to && childOpen != Open.Neither) {
                    target.add(child)
                } else {
                    child.decompose(from - pos, to - pos, target, childOpen)
                }
            }
            pos = end + 1
            if (pos > to) return
        }
    }

    override fun replace(from: Int, to: Int, text: Text): Text {
        val (from, to) = clip(this, from, to)
        if (text.lines < this.lines) {
            var pos = 0
            children.forEachIndexed { i, child ->
                val end = pos + child.length
                // Fast path: if the change only affects one child and the
                // child's size remains in the acceptable range, only update
                // that child
                if (from >= pos && to <= end) {
                    val updated = child.replace(from - pos, to - pos, text)
                    val totalLines = this.lines - child.lines + updated.lines
                    if (updated.lines < (totalLines shr (Tree.BranchShift - 1)) &&
                        updated.lines > (totalLines shr (Tree.BranchShift + 1))
                    ) {
                        val copy = this.children.toMutableList()
                        copy[i] = updated
                        return TextNode(copy, this.length - (to - from) + text.length)
                    }
                    return super.replace(pos, end, updated)
                }
                pos = end + 1
            }
        }
        return super.replace(from, to, text)
    }

    override fun sliceString(from: Int, to: Int?, lineSep: String?): String {
        val (clipFrom, clipTo) = clip(this, from, to ?: this.length)
        val sep = lineSep ?: "\n"
        var result = ""
        var pos = 0
        children.forEachIndexed { i, child ->
            val end = pos + child.length
            if (pos > clipFrom && i != 0) result += sep
            if (clipFrom < end && clipTo > pos) {
                result += child.sliceString(clipFrom - pos, clipTo - pos, sep)
            }
            pos = end + 1
            if (pos > clipTo) return result
        }
        return result
    }

    override fun flatten(target: MutableList<String>): MutableList<String> {
        children.forEach { it.flatten(target) }
        return target
    }

    override fun scanIdentical(other: Text, dir: Boolean): Int {
        if (other !is TextNode) return 0
        var length = 0
        val indicesA = if (dir) this.children.indices else this.children.indices.reversed()
        val indicesB = if (dir) other.children.indices else other.children.indices.reversed()
        indicesA.zip(indicesB).forEach { (iA, iB) ->
            val chA = this.children[iA]
            val chB = other.children[iB]
            if (chA != chB) return length + chA.scanIdentical(chB, dir)
            length += chA.length + 1
        }
        return length
    }

    companion object {
        fun from(
            children: List<Text>,
            length: Int = children.fold(-1) { l, ch -> l + ch.length + 1 }
        ): Text {
            val lines = children.sumOf { it.lines }
            if (lines < Tree.Branch) {
                return TextLeaf(
                    buildList {
                        children.forEach { it.flatten(this) }
                    },
                    length
                )
            }
            val chunk = max(
                Tree.Branch,
                lines shr Tree.BranchShift
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
                        TextNode.from(currentChunk.toMutableList(), currentLen)
                    }
                )
                currentLen = -1
                currentLines = 0
                currentChunk.clear()
            }

            fun add(child: Text) {
                if (child.lines > maxChunk && child is TextNode) {
                    children.forEach(::add)
                } else if (
                    child.lines > minChunk &&
                    (currentLines > minChunk || currentLines == 0)
                ) {
                    flush()
                    chunked.add(child)
                } else if (child is TextLeaf && currentLines != 0 &&
                    (currentChunk.lastOrNull() as? TextLeaf)
                        ?.let { child.lines + it.lines <= Tree.Branch } == true
                ) {
                    val last = currentChunk.last()
                    currentLines += child.lines
                    currentLen += child.length + 1
                    currentChunk[currentChunk.size - 1] =
                        TextLeaf(
                            (last as TextLeaf).text + child.text,
                            last.length + 1 + child.length
                        )
                } else {
                    if (currentLines + child.lines > chunk) flush()
                    currentLines += child.lines
                    currentLen += child.length + 1
                    currentChunk.add(child)
                }
            }

            children.forEach(::add)
            flush()
            return chunked.singleOrNull() ?: TextNode(chunked, length)
        }
    }
}

internal fun textLength(text: List<String>): Int {
    return -1 + text.sumOf { it.length + 1 }
}

internal fun appendText(
    text: List<String>,
    target: MutableList<String> = mutableListOf(),
    from: Int = 0,
    to: Int = Int.MAX_VALUE
): List<String> {
    var pos = 0
    var first = true
    text.forEachIndexed { i, child ->
        var line = child
        val end = pos + line.length
        if (end >= from) {
            if (end > to) line = line.substring(0, to - pos)
            if (pos < from) line = line.substring(from - pos)
            if (first) {
                target[target.size - 1] += line
                first = false
            } else {
                target.add(line)
            }
        }
        pos = end + 1
        if (pos > to) return target
    }
    return target
}

internal fun sliceText(text: List<String>, from: Int = 0, to: Int = Int.MAX_VALUE): List<String> {
    return appendText(text, mutableListOf(""), from, to)
}

internal class RawTextCursor(val text: Text, val dir: Boolean = true) :
    TextIterator<RawTextCursor> {
    override var done: Boolean = false
    override var lineBreak: Boolean = false
    override var value: String = ""
    val nodes = mutableListOf(text)

    // The offset into the node at each level, shifted one to the left
    // with the top bit indicating whether the position is before (0) or
    // after(1) the line break between the adjacent nodes.
    val offsets = mutableListOf(
        if (dir) {
            1
        } else if (text is TextLeaf) {
            text.text.size shl 1
        } else {
            text.children!!.size shl 1
        }
    )

    fun nextInner(skip: Int, dir: Boolean): RawTextCursor {
        var skip = skip
        this.done = false
        this.lineBreak = false
        while (true) {
            val last = this.nodes.size - 1
            val top = this.nodes[last]
            val offsetValue = this.offsets[last]
            val offset = offsetValue shr 1
            val size = (top as? TextLeaf)?.text?.size ?: top.children!!.size
            if (offset == (if (dir) size else 0)) {
                if (last == 0) {
                    this.done = true
                    this.value = ""
                    return this
                }
                if (dir) this.offsets[last - 1]++
                this.nodes.removeLast()
                this.offsets.removeLast()
            } else if ((offsetValue and 1) == (if (dir) 0 else 1)) {
                this.offsets[last] = this.offsets[last] + if (dir) 1 else -1
                if (skip == 0) {
                    this.lineBreak = true
                    this.value = "\n"
                    return this
                }
                skip--
            } else if (top is TextLeaf) {
                // Move to the next string
                val next = top.text[offset + (if (!dir) -1 else 0)]
                this.offsets[last] += if (dir) 1 else -1
                if (next.length > max(0, skip)) {
                    this.value =
                        if (skip == 0) {
                            next
                        } else if (dir) {
                            next.substring(skip)
                        } else {
                            next.substring(0, next.length - skip)
                        }
                    return this
                }
                skip -= next.length
            } else {
                val next = top.children!![offset + (if (!dir) -1 else 0)]
                if (skip > next.length) {
                    skip -= next.length
                    this.offsets[last] += if (dir) 1 else 0
                } else {
                    if (!dir) this.offsets[last]--
                    this.nodes.add(next)
                    this.offsets.add(
                        if (dir) {
                            1
                        } else {
                            ((next as? TextLeaf)?.text?.size ?: next.children!!.size) shl 1
                        }
                    )
                }
            }
        }
    }

    override fun next(skip: Int?): RawTextCursor {
        var skip = skip ?: 0
        if (skip < 0) {
            this.nextInner(-skip, !dir)
            skip = this.value.length
        }
        return this.nextInner(skip, this.dir)
    }

    // / @internal
//    [Symbol.iterator]!: () => Iterator<string>
}

internal class PartialTextCursor(text: Text, start: Int, end: Int) :
    TextIterator<PartialTextCursor> {
    internal val cursor: RawTextCursor = RawTextCursor(text, start <= end)
    override var value: String = ""
    var pos: Int = if (start > end) text.length else 0
    val from: Int = min(start, end)
    val to: Int = max(start, end)
    override var done = false

    fun nextInner(skip: Int, dir: Boolean): PartialTextCursor {
        var skip = skip
        if (if (!dir) this.pos <= this.from else this.pos >= this.to) {
            this.value = ""
            this.done = true
            return this
        }
        skip += max(0, if (!dir) this.pos - this.to else this.from - this.pos)
        var limit = if (!dir) this.pos - this.from else this.to - this.pos
        if (skip > limit) skip = limit
        limit -= skip
        val value = this.cursor.next(skip).value
        this.pos += (value.length + skip) * if (dir) 1 else -1
        this.value =
            if (value.length <= limit) {
                value
            } else if (!dir) {
                value.substring(value.length - limit)
            } else {
                value.substring(0, limit)
            }
        this.done = this.value.isEmpty()
        return this
    }

    override fun next(skip: Int?): PartialTextCursor {
        var skip = skip ?: 0
        if (skip < 0) {
            skip = max(skip, this.from - this.pos)
        } else if (skip > 0) skip = min(skip, this.to - this.pos)
        return this.nextInner(skip, this.cursor.dir)
    }

    override val lineBreak: Boolean
        get() {
            return this.cursor.lineBreak && this.value != ""
        }

    // / @internal
//    [Symbol.iterator]!: () => Iterator<string>
}

internal class LineCursor(val inner: TextIterator<*>) : TextIterator<LineCursor> {
    var afterBreak = true
    override var value = ""
    override var done = false

    override fun next(skip: Int?): LineCursor {
        val next = this.inner.next(skip)
        val done = next.done
        val lineBreak = next.lineBreak
        val value = next.value
        if (done && this.afterBreak) {
            this.value = ""
            this.afterBreak = false
        } else if (done) {
            this.done = true
            this.value = ""
        } else if (lineBreak) {
            if (this.afterBreak) {
                this.value = ""
            } else {
                this.afterBreak = true
                this.next()
            }
        } else {
            this.value = value
            this.afterBreak = false
        }
        return this
    }

    override val lineBreak: Boolean
        get() {
            return false
        }

    // / @internal
//    [Symbol.iterator]!: () => Iterator<string>
}

// if (typeof Symbol != "undefined") {
//    Text.prototype[Symbol.iterator] = fun() { return this.iter() }
//    RawTextCursor.prototype[Symbol.iterator] = PartialTextCursor.prototype[Symbol.iterator] =
//        LineCursor.prototype[Symbol.iterator] = fun(this: Iterator<string>) { return this }
// }

// / This type describes a line in the document. It is created
// / on-demand when lines are [queried](#state.Text.lineAt).
class Line internal constructor(
    // / The position of the start of the line.
    val from: Int,
    // / The position at the end of the line (_before_ the line break,
    // / or at the end of document for the last line).
    val to: Int,
    // / This line's line number (1-based).
    val number: Int,
    // / The line's content.
    val text: String
) {

    // / The length of the line (not including any line break after it).
    val length: Int
        get() {
            return this.to - this.from
        }
}

internal fun clip(text: Text, from: Int, to: Int): Pair<Int, Int> {
    val start = from.coerceIn(0, text.length)
    return start to to.coerceIn(start, text.length)
}
