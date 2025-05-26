package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.ChangeSet
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.SpanIterator
import com.monkopedia.kodemirror.decoration.DecorationSet
import com.monkopedia.kodemirror.decoration.PointDecoration
import com.monkopedia.kodemirror.decoration.Decoration
import com.monkopedia.kodemirror.decoration.BlockType
import com.monkopedia.kodemirror.decoration.WidgetType

private val wrappingWhiteSpace = listOf("pre-wrap", "normal", "pre-line", "break-spaces")

// Used to track, during updateHeight, if any actual heights changed
var heightChangeFlag = false

fun clearHeightChangeFlag() { 
    heightChangeFlag = false 
}

class HeightOracle(var lineWrapping: Boolean) {
    var doc: Text = Text.empty
    val heightSamples = mutableMapOf<Int, Boolean>()
    var lineHeight: Double = 14.0 // The height of an entire line (line-height)
    var charWidth: Double = 7.0
    var textHeight: Double = 14.0 // The height of the actual font (font-size)
    var lineLength: Int = 30

    fun heightForGap(from: Int, to: Int): Double {
        var lines = doc.lineAt(to).number - doc.lineAt(from).number + 1
        if (lineWrapping) {
            lines += maxOf(0.0, kotlin.math.ceil(((to - from) - (lines * lineLength * 0.5)) / lineLength.toDouble())).toInt()
        }
        return lineHeight * lines
    }

    fun heightForLine(length: Int): Double {
        if (!lineWrapping) return lineHeight
        val lines = 1 + maxOf(0.0, kotlin.math.ceil((length - lineLength) / (lineLength - 5.0))).toInt()
        return lines * lineHeight
    }

    fun setDoc(newDoc: Text): HeightOracle {
        doc = newDoc
        return this
    }

    fun mustRefreshForWrapping(whiteSpace: String): Boolean {
        return wrappingWhiteSpace.contains(whiteSpace) != lineWrapping
    }

    fun mustRefreshForHeights(lineHeights: List<Double>): Boolean {
        var newHeight = false
        var i = 0
        while (i < lineHeights.size) {
            val h = lineHeights[i]
            if (h < 0) {
                i++
            } else if (!heightSamples.containsKey(kotlin.math.floor(h * 10).toInt())) { // Round to .1 pixels
                newHeight = true
                heightSamples[kotlin.math.floor(h * 10).toInt()] = true
            }
            i++
        }
        return newHeight
    }

    fun refresh(
        whiteSpace: String,
        lineHeight: Double,
        charWidth: Double,
        textHeight: Double,
        lineLength: Int,
        knownHeights: List<Double>
    ): Boolean {
        val lineWrapping = wrappingWhiteSpace.contains(whiteSpace)
        val changed = kotlin.math.round(lineHeight) != kotlin.math.round(this.lineHeight) || this.lineWrapping != lineWrapping
        this.lineWrapping = lineWrapping
        this.lineHeight = lineHeight
        this.charWidth = charWidth
        this.textHeight = textHeight
        this.lineLength = lineLength
        if (changed) {
            heightSamples.clear()
            var i = 0
            while (i < knownHeights.size) {
                val h = knownHeights[i]
                if (h < 0) i++
                else heightSamples[kotlin.math.floor(h * 10).toInt()] = true
                i++
            }
        }
        return changed
    }
}

// This object is used by updateHeight to make DOM measurements
// arrive at the right nodes. The heights array is a sequence of
// block heights, starting from position from.
class MeasuredHeights(val from: Int, val heights: List<Double>) {
    var index = 0
    val more: Boolean get() = index < heights.size
}

// Record used to represent information about a block-level element
// in the editor view.
class BlockInfo(
    // The start of the element in the document.
    val from: Int,
    // The length of the element.
    val length: Int,
    // The top position of the element (relative to the top of the document).
    val top: Double,
    // Its height.
    val height: Double,
    // Internal: Weird packed field that holds an array of children
    // for composite blocks, a decoration for block widgets, and a
    // number indicating the amount of widget-create line breaks for
    // text blocks.
    private val content: Any
) {
    // The type of element this is. When querying lines, this may be
    // an array of all the blocks that make up the line.
    val type: Any get() = when(content) {
        is Int -> BlockType.Text
        is List<*> -> content
        is PointDecoration -> content.type
        else -> throw IllegalStateException("Invalid content type")
    }

    // The end of the element as a document position.
    val to: Int get() = from + length
    // The bottom position of the element.
    val bottom: Double get() = top + height

    // If this is a widget block, this will return the widget associated with it.
    val widget: WidgetType? get() = 
        if (content is PointDecoration) content.widget else null

    // If this is a textblock, this holds the number of line breaks
    // that appear in widgets inside the block.
    val widgetLineBreaks: Int get() = 
        if (content is Int) content else 0

    // Internal
    fun join(other: BlockInfo): BlockInfo {
        val newContent = when {
            content is List<*> -> (content as List<BlockInfo>) + 
                if (other.content is List<*>) other.content as List<BlockInfo> 
                else listOf(other)
            else -> listOf(this) + 
                if (other.content is List<*>) other.content as List<BlockInfo>
                else listOf(other)
        }
        return BlockInfo(from, length + other.length, top, height + other.height, newContent)
    }
}

enum class QueryType {
    ByPos, ByHeight, ByPosNoHeight
}

private object Flag {
    const val Break = 1
    const val Outdated = 2
    const val SingleLine = 4
}

private const val Epsilon = 1e-3

abstract class HeightMap(
    var length: Int, // The number of characters covered
    var height: Double, // Height of this part of the document
    var flags: Int = Flag.Outdated
) {
    var size: Int = 1

    var outdated: Boolean
        get() = (flags and Flag.Outdated) > 0
        set(value) { flags = (if (value) Flag.Outdated else 0) or (flags and Flag.Outdated.inv()) }

    abstract fun blockAt(height: Double, oracle: HeightOracle, top: Double, offset: Int): BlockInfo
    abstract fun lineAt(value: Int, type: QueryType, oracle: HeightOracle, top: Double, offset: Int): BlockInfo
    abstract fun forEachLine(from: Int, to: Int, oracle: HeightOracle, top: Double, offset: Int, f: (BlockInfo) -> Unit)

    abstract fun updateHeight(oracle: HeightOracle, offset: Int = 0, force: Boolean = false, measured: MeasuredHeights? = null): HeightMap
    abstract fun asString(): String

    override fun toString(): String {
        return asString()
    }

    fun setHeight(height: Double) {
        if (this.height != height) {
            if (kotlin.math.abs(this.height - height) > Epsilon) heightChangeFlag = true
            this.height = height
        }
    }

    // Base case is to replace a leaf node, which simply builds a tree
    // from the new nodes and returns that (HeightMapBranch and
    // HeightMapGap override this to actually use from/to)
    open fun replace(from: Int, to: Int, nodes: List<HeightMap?>): HeightMap {
        return Companion.of(nodes)
    }

    // Again, these are base cases, and are overridden for branch and gap nodes.
    open fun decomposeLeft(to: Int, result: MutableList<HeightMap?>) { result.add(this) }
    open fun decomposeRight(from: Int, result: MutableList<HeightMap?>) { result.add(this) }

    fun applyChanges(
        decorations: List<DecorationSet>,
        oldDoc: Text,
        oracle: HeightOracle,
        changes: List<ChangedRange>
    ): HeightMap {
        var me: HeightMap = this
        val doc = oracle.doc
        for (i in changes.indices.reversed()) {
            val change = changes[i]
            val start = me.lineAt(change.fromA, QueryType.ByPosNoHeight, oracle.setDoc(oldDoc), 0.0, 0)
            val end = if (start.to >= change.toA) start else me.lineAt(change.toA, QueryType.ByPosNoHeight, oracle, 0.0, 0)
            var toB = change.toB + (end.to - change.toA)
            var toA = end.to
            var fromA = change.fromA
            var fromB = change.fromB
            var j = i
            while (j > 0 && start.from <= changes[j - 1].toA) {
                fromA = changes[j - 1].fromA
                fromB = changes[j - 1].fromB
                j--
                if (fromA < start.from) {
                    val newStart = me.lineAt(fromA, QueryType.ByPosNoHeight, oracle, 0.0, 0)
                    fromB += newStart.from - fromA
                    fromA = newStart.from
                }
            }
            val nodes = NodeBuilder.build(oracle.setDoc(doc), decorations, fromB, toB)
            me = replace(me, me.replace(fromA, toA, nodes))
        }
        return me.updateHeight(oracle, 0)
    }

    companion object {
        fun empty(): HeightMap = HeightMapText(0, 0.0)

        // nodes uses null values to indicate the position of line breaks.
        // There are never line breaks at the start or end of the array, or
        // two line breaks next to each other, and the array isn't allowed
        // to be empty (same restrictions as return value from the builder).
        fun of(nodes: List<HeightMap?>): HeightMap {
            if (nodes.size == 1) return nodes[0] ?: throw IllegalStateException("Empty node")

            var i = 0
            var j = nodes.size
            var before = 0
            var after = 0
            while (true) {
                if (i == j) {
                    if (before > after * 2) {
                        val split = nodes[i - 1] as HeightMapBranch
                        if (split.break > 0) {
                            nodes.toMutableList().apply {
                                removeAt(i - 1)
                                add(i - 1, split.left)
                                add(i, null)
                                add(i + 1, split.right)
                            }
                            j += 1 + split.break
                            before -= split.size
                        } else {
                            nodes.toMutableList().apply {
                                removeAt(i - 1)
                                add(i - 1, split.left)
                                add(i, split.right)
                            }
                            j += 2
                            before -= split.size
                        }
                    } else if (after > before * 2) {
                        val split = nodes[j] as HeightMapBranch
                        if (split.break > 0) {
                            nodes.toMutableList().apply {
                                removeAt(j)
                                add(j, split.left)
                                add(j + 1, null)
                                add(j + 2, split.right)
                            }
                            j += 2 + split.break
                            after -= split.size
                        } else {
                            nodes.toMutableList().apply {
                                removeAt(j)
                                add(j, split.left)
                                add(j + 1, split.right)
                            }
                            j += 2
                            after -= split.size
                        }
                    } else {
                        break
                    }
                } else if (before < after) {
                    val next = nodes[i++]
                    if (next != null) before += next.size
                } else {
                    val next = nodes[--j]
                    if (next != null) after += next.size
                }
            }

            var brk = 0
            if (nodes[i - 1] == null) {
                brk = 1
                i--
            } else if (nodes[i] == null) {
                brk = 1
                j++
            }
            return HeightMapBranch(of(nodes.subList(0, i)), brk, of(nodes.subList(j, nodes.size)))
        }
    }
}

private fun replace(old: HeightMap, new: HeightMap): HeightMap {
    if (old === new) return old
    if (old::class != new::class) heightChangeFlag = true
    return new
}

open class HeightMapBlock(
    length: Int,
    height: Double,
    val deco: PointDecoration?
) : HeightMap(length, height) {

    override fun blockAt(height: Double, oracle: HeightOracle, top: Double, offset: Int): BlockInfo {
        return BlockInfo(offset, length, top, this.height, deco ?: 0)
    }

    override fun lineAt(value: Int, type: QueryType, oracle: HeightOracle, top: Double, offset: Int): BlockInfo {
        return blockAt(0.0, oracle, top, offset)
    }

    override fun forEachLine(from: Int, to: Int, oracle: HeightOracle, top: Double, offset: Int, f: (BlockInfo) -> Unit) {
        if (from <= offset + length && to >= offset) f(blockAt(0.0, oracle, top, offset))
    }

    override fun updateHeight(oracle: HeightOracle, offset: Int, force: Boolean, measured: MeasuredHeights?): HeightMap {
        if (measured != null && measured.from <= offset && measured.more)
            setHeight(measured.heights[measured.index++])
        outdated = false
        return this
    }

    override fun asString(): String = "block($length)"
}

class HeightMapText(
    length: Int,
    height: Double
) : HeightMapBlock(length, height, null) {
    var collapsed: Int = 0 // Amount of collapsed content in the line
    var widgetHeight: Double = 0.0 // Maximum inline widget height
    var breaks: Int = 0 // Number of widget-introduced line breaks on the line

    override fun blockAt(height: Double, oracle: HeightOracle, top: Double, offset: Int): BlockInfo {
        return BlockInfo(offset, length, top, this.height, breaks)
    }

    override fun replace(from: Int, to: Int, nodes: List<HeightMap?>): HeightMap {
        val node = nodes[0]
        if (nodes.size == 1 && (node is HeightMapText || 
            (node is HeightMapGap && (node.flags and Flag.SingleLine) != 0)) &&
            kotlin.math.abs(length - (node?.length ?: 0)) < 10) {
            val newNode = when (node) {
                is HeightMapGap -> HeightMapText(node.length, this.height)
                is HeightMapText -> {
                    node.height = this.height
                    node
                }
                else -> throw IllegalStateException("Invalid node type")
            }
            if (!outdated) newNode.outdated = false
            return newNode
        } else {
            return HeightMap.of(nodes)
        }
    }

    override fun updateHeight(oracle: HeightOracle, offset: Int, force: Boolean, measured: MeasuredHeights?): HeightMap {
        if (measured != null && measured.from <= offset && measured.more)
            setHeight(measured.heights[measured.index++])
        else if (force || outdated)
            setHeight(kotlin.math.max(widgetHeight, oracle.heightForLine(length - collapsed)) +
                breaks * oracle.lineHeight)
        outdated = false
        return this
    }

    override fun asString(): String {
        return "line($length${if (collapsed != 0) "-$collapsed" else ""}${if (widgetHeight != 0.0) ":$widgetHeight" else ""})"
    }
}

class HeightMapGap(length: Int) : HeightMap(length, 0.0) {
    private data class HeightMetrics(
        val firstLine: Int,
        val lastLine: Int,
        val perLine: Double,
        val perChar: Double
    )

    private fun heightMetrics(oracle: HeightOracle, offset: Int): HeightMetrics {
        val firstLine = oracle.doc.lineAt(offset).number
        val lastLine = oracle.doc.lineAt(offset + length).number
        val lines = lastLine - firstLine + 1
        var perLine: Double
        var perChar = 0.0
        if (oracle.lineWrapping) {
            val totalPerLine = kotlin.math.min(height, oracle.lineHeight * lines)
            perLine = totalPerLine / lines
            if (length > lines + 1)
                perChar = (height - totalPerLine) / (length - lines - 1)
        } else {
            perLine = height / lines
        }
        return HeightMetrics(firstLine, lastLine, perLine, perChar)
    }

    override fun blockAt(height: Double, oracle: HeightOracle, top: Double, offset: Int): BlockInfo {
        val metrics = heightMetrics(oracle, offset)
        return if (oracle.lineWrapping) {
            val guess = offset + if (height < oracle.lineHeight) 0 else
                kotlin.math.round(kotlin.math.max(0.0, kotlin.math.min(1.0, (height - top) / this.height)) * length)
            val line = oracle.doc.lineAt(guess.toInt())
            val lineHeight = metrics.perLine + line.length * metrics.perChar
            val lineTop = kotlin.math.max(top, height - lineHeight / 2)
            BlockInfo(line.from, line.length, lineTop, lineHeight, 0)
        } else {
            val line = kotlin.math.max(0, kotlin.math.min(metrics.lastLine - metrics.firstLine,
                kotlin.math.floor((height - top) / metrics.perLine).toInt()))
            val lineInfo = oracle.doc.line(metrics.firstLine + line)
            BlockInfo(lineInfo.from, lineInfo.length, top + metrics.perLine * line, metrics.perLine, 0)
        }
    }

    override fun lineAt(value: Int, type: QueryType, oracle: HeightOracle, top: Double, offset: Int): BlockInfo {
        return when (type) {
            QueryType.ByHeight -> blockAt(value.toDouble(), oracle, top, offset)
            QueryType.ByPosNoHeight -> {
                val line = oracle.doc.lineAt(value)
                BlockInfo(line.from, line.to - line.from, 0.0, 0.0, 0)
            }
            else -> {
                val metrics = heightMetrics(oracle, offset)
                val line = oracle.doc.lineAt(value)
                val lineHeight = metrics.perLine + line.length * metrics.perChar
                val linesAbove = line.number - metrics.firstLine
                val lineTop = top + metrics.perLine * linesAbove + metrics.perChar * (line.from - offset - linesAbove)
                BlockInfo(line.from, line.length,
                    kotlin.math.max(top, kotlin.math.min(lineTop, top + height - lineHeight)),
                    lineHeight, 0)
            }
        }
    }

    override fun forEachLine(from: Int, to: Int, oracle: HeightOracle, top: Double, offset: Int, f: (BlockInfo) -> Unit) {
        val fromPos = kotlin.math.max(from, offset)
        val toPos = kotlin.math.min(to, offset + length)
        val metrics = heightMetrics(oracle, offset)
        var pos = fromPos
        var lineTop = top
        while (pos <= toPos) {
            val line = oracle.doc.lineAt(pos)
            if (pos == fromPos) {
                val linesAbove = line.number - metrics.firstLine
                lineTop += metrics.perLine * linesAbove + metrics.perChar * (fromPos - offset - linesAbove)
            }
            val lineHeight = metrics.perLine + metrics.perChar * line.length
            f(BlockInfo(line.from, line.length, lineTop, lineHeight, 0))
            lineTop += lineHeight
            pos = line.to + 1
        }
    }

    override fun replace(from: Int, to: Int, nodes: List<HeightMap?>): HeightMap {
        val after = length - to
        val mutableNodes = nodes.toMutableList()
        if (after > 0) {
            val last = nodes.lastOrNull()
            if (last is HeightMapGap) {
                mutableNodes[mutableNodes.lastIndex] = HeightMapGap(last.length + after)
            } else {
                mutableNodes.add(null)
                mutableNodes.add(HeightMapGap(after - 1))
            }
        }
        if (from > 0) {
            val first = nodes.firstOrNull()
            if (first is HeightMapGap) {
                mutableNodes[0] = HeightMapGap(from + first.length)
            } else {
                mutableNodes.add(0, null)
                mutableNodes.add(0, HeightMapGap(from - 1))
            }
        }
        return HeightMap.of(mutableNodes)
    }

    override fun decomposeLeft(to: Int, result: MutableList<HeightMap?>) {
        result.add(HeightMapGap(to - 1))
        result.add(null)
    }

    override fun decomposeRight(from: Int, result: MutableList<HeightMap?>) {
        result.add(null)
        result.add(HeightMapGap(length - from - 1))
    }

    override fun updateHeight(oracle: HeightOracle, offset: Int, force: Boolean, measured: MeasuredHeights?): HeightMap {
        val end = offset + length
        if (measured != null && measured.from <= end && measured.more) {
            // Fill in part of this gap with measured lines
            val nodes = mutableListOf<HeightMap?>()
            var pos = kotlin.math.max(offset, measured.from)
            var singleHeight = -1.0
            if (measured.from > offset) {
                nodes.add(HeightMapGap(measured.from - offset - 1).updateHeight(oracle, offset))
            }
            while (pos <= end && measured.more) {
                val len = oracle.doc.lineAt(pos).length
                if (nodes.isNotEmpty()) nodes.add(null)
                val height = measured.heights[measured.index++]
                if (singleHeight == -1.0) singleHeight = height
                else if (kotlin.math.abs(height - singleHeight) >= Epsilon) singleHeight = -2.0
                val line = HeightMapText(len, height)
                line.outdated = false
                nodes.add(line)
                pos += len + 1
            }
            if (pos <= end) {
                nodes.add(null)
                nodes.add(HeightMapGap(end - pos).updateHeight(oracle, pos))
            }
            val result = HeightMap.of(nodes)
            if (singleHeight < 0 || kotlin.math.abs(result.height - height) >= Epsilon ||
                kotlin.math.abs(singleHeight - heightMetrics(oracle, offset).perLine) >= Epsilon)
                heightChangeFlag = true
            return replace(this, result)
        } else if (force || outdated) {
            setHeight(oracle.heightForGap(offset, offset + length))
            outdated = false
        }
        return this
    }

    override fun asString(): String = "gap($length)"
}

class HeightMapBranch(
    val left: HeightMap,
    brk: Int,
    val right: HeightMap
) : HeightMap(left.length + brk + right.length, left.height + right.height,
    brk or (if (left.outdated || right.outdated) Flag.Outdated else 0)) {

    init {
        size = left.size + right.size
    }

    val break: Int get() = flags and Flag.Break

    override fun blockAt(height: Double, oracle: HeightOracle, top: Double, offset: Int): BlockInfo {
        val mid = top + left.height
        return if (height < mid)
            left.blockAt(height, oracle, top, offset)
        else
            right.blockAt(height, oracle, mid, offset + left.length + break)
    }

    override fun lineAt(value: Int, type: QueryType, oracle: HeightOracle, top: Double, offset: Int): BlockInfo {
        val rightTop = top + left.height
        val rightOffset = offset + left.length + break
        val isLeft = if (type == QueryType.ByHeight) value < rightTop else value < rightOffset
        val base = if (isLeft)
            left.lineAt(value, type, oracle, top, offset)
        else
            right.lineAt(value, type, oracle, rightTop, rightOffset)
        if (break != 0 || (if (isLeft) base.to < rightOffset else base.from > rightOffset)) return base
        val subQuery = if (type == QueryType.ByPosNoHeight) QueryType.ByPosNoHeight else QueryType.ByPos
        return if (isLeft)
            base.join(right.lineAt(rightOffset, subQuery, oracle, rightTop, rightOffset))
        else
            left.lineAt(rightOffset, subQuery, oracle, top, offset).join(base)
    }

    override fun forEachLine(from: Int, to: Int, oracle: HeightOracle, top: Double, offset: Int, f: (BlockInfo) -> Unit) {
        val rightTop = top + left.height
        val rightOffset = offset + left.length + break
        if (break != 0) {
            if (from < rightOffset) left.forEachLine(from, to, oracle, top, offset, f)
            if (to >= rightOffset) right.forEachLine(from, to, oracle, rightTop, rightOffset, f)
        } else {
            val mid = lineAt(rightOffset, QueryType.ByPos, oracle, top, offset)
            if (from < mid.from) left.forEachLine(from, mid.from - 1, oracle, top, offset, f)
            if (mid.to >= from && mid.from <= to) f(mid)
            if (to > mid.to) right.forEachLine(mid.to + 1, to, oracle, rightTop, rightOffset, f)
        }
    }

    override fun replace(from: Int, to: Int, nodes: List<HeightMap?>): HeightMap {
        val rightStart = left.length + break
        return when {
            to < rightStart -> balanced(left.replace(from, to, nodes), right)
            from > left.length -> balanced(left, right.replace(from - rightStart, to - rightStart, nodes))
            else -> {
                val result = mutableListOf<HeightMap?>()
                if (from > 0) decomposeLeft(from, result)
                val leftSize = result.size
                result.addAll(nodes)
                if (from > 0) mergeGaps(result, leftSize - 1)
                if (to < length) {
                    val rightSize = result.size
                    decomposeRight(to, result)
                    mergeGaps(result, rightSize)
                }
                HeightMap.of(result)
            }
        }
    }

    override fun decomposeLeft(to: Int, result: MutableList<HeightMap?>) {
        val leftLen = left.length
        if (to <= leftLen) return left.decomposeLeft(to, result)
        result.add(left)
        if (break != 0) {
            if (to >= leftLen + 1) result.add(null)
        }
        if (to > leftLen) right.decomposeLeft(to - leftLen - break, result)
    }

    override fun decomposeRight(from: Int, result: MutableList<HeightMap?>) {
        val leftLen = left.length
        val rightStart = leftLen + break
        if (from >= rightStart) return right.decomposeRight(from - rightStart, result)
        if (from < leftLen) left.decomposeRight(from, result)
        if (break != 0 && from < rightStart) result.add(null)
        result.add(right)
    }

    fun balanced(left: HeightMap, right: HeightMap): HeightMap {
        return if (left.size > 2 * right.size || right.size > 2 * left.size)
            HeightMap.of(if (break != 0) listOf(left, null, right) else listOf(left, right))
        else {
            this.left = replace(this.left, left)
            this.right = replace(this.right, right)
            setHeight(left.height + right.height)
            outdated = left.outdated || right.outdated
            size = left.size + right.size
            length = left.length + break + right.length
            this
        }
    }

    override fun updateHeight(oracle: HeightOracle, offset: Int, force: Boolean, measured: MeasuredHeights?): HeightMap {
        val rightStart = offset + left.length + break
        var rebalance: HeightMap? = null
        var newLeft = left
        var newRight = right
        
        if (measured != null && measured.from <= offset + left.length && measured.more)
            rebalance = left.also { newLeft = it.updateHeight(oracle, offset, force, measured) }
        else
            left.updateHeight(oracle, offset, force)
            
        if (measured != null && measured.from <= rightStart + right.length && measured.more)
            rebalance = right.also { newRight = it.updateHeight(oracle, rightStart, force, measured) }
        else
            right.updateHeight(oracle, rightStart, force)
            
        return if (rebalance != null) balanced(newLeft, newRight)
        else {
            height = left.height + right.height
            outdated = false
            this
        }
    }

    override fun asString(): String = "$left${if (break != 0) " " else "-"}$right"
}

private fun mergeGaps(nodes: MutableList<HeightMap?>, around: Int) {
    val before = nodes.getOrNull(around - 1)
    val after = nodes.getOrNull(around + 1)
    if (nodes[around] == null && before is HeightMapGap && after is HeightMapGap) {
        nodes.subList(around - 1, around + 2).clear()
        nodes.add(around - 1, HeightMapGap(before.length + 1 + after.length))
    }
}

private const val relevantWidgetHeight = 5.0

class NodeBuilder(
    var pos: Int,
    val oracle: HeightOracle
) : SpanIterator<Decoration> {
    val nodes = mutableListOf<HeightMap?>()
    var writtenTo: Int = pos
    var lineStart: Int = -1
    var lineEnd: Int = -1
    var covering: HeightMapBlock? = null

    val isCovered: Boolean get() =
        covering != null && nodes.lastOrNull() === covering

    override fun span(from: Int, to: Int) {
        if (lineStart > -1) {
            val end = kotlin.math.min(to, lineEnd)
            val last = nodes.lastOrNull()
            if (last is HeightMapText)
                last.length += end - pos
            else if (end > pos || !isCovered)
                nodes.add(HeightMapText(end - pos, -1.0))
            writtenTo = end
            if (to > end) {
                nodes.add(null)
                writtenTo++
                lineStart = -1
            }
        }
        pos = to
    }

    override fun point(from: Int, to: Int, deco: PointDecoration) {
        if (from < to || deco.heightRelevant) {
            val height = deco.widget?.estimatedHeight ?: 0.0
            val breaks = deco.widget?.lineBreaks ?: 0
            val finalHeight = if (height < 0) oracle.lineHeight else height
            val len = to - from
            when {
                deco.block -> addBlock(HeightMapBlock(len, finalHeight, deco))
                len > 0 || breaks > 0 || finalHeight >= relevantWidgetHeight -> addLineDeco(finalHeight, breaks, len)
            }
        } else if (to > from) {
            span(from, to)
        }
        if (lineEnd > -1 && lineEnd < pos)
            lineEnd = oracle.doc.lineAt(pos).to
    }

    fun enterLine() {
        if (lineStart > -1) return
        val line = oracle.doc.lineAt(pos)
        lineStart = line.from
        lineEnd = line.to
        if (writtenTo < line.from) {
            if (writtenTo < line.from - 1 || nodes.lastOrNull() == null)
                nodes.add(blankContent(writtenTo, line.from - 1))
            nodes.add(null)
        }
        if (pos > line.from)
            nodes.add(HeightMapText(pos - line.from, -1.0))
        writtenTo = pos
    }

    fun blankContent(from: Int, to: Int): HeightMap {
        val gap = HeightMapGap(to - from)
        if (oracle.doc.lineAt(from).to == to) gap.flags = gap.flags or Flag.SingleLine
        return gap
    }

    fun ensureLine(): HeightMapText {
        enterLine()
        val last = nodes.lastOrNull()
        return if (last is HeightMapText) last
        else HeightMapText(0, -1.0).also { nodes.add(it) }
    }

    fun addBlock(block: HeightMapBlock) {
        enterLine()
        val deco = block.deco
        if (deco != null && deco.startSide > 0 && !isCovered) ensureLine()
        nodes.add(block)
        writtenTo = pos + block.length
        pos = writtenTo
        if (deco != null && deco.endSide > 0) covering = block
    }

    fun addLineDeco(height: Double, breaks: Int, length: Int) {
        val line = ensureLine()
        line.length += length
        line.collapsed += length
        line.widgetHeight = kotlin.math.max(line.widgetHeight, height)
        line.breaks += breaks
        writtenTo = pos + length
        pos = writtenTo
    }

    fun finish(from: Int): List<HeightMap?> {
        val last = nodes.lastOrNull()
        if (lineStart > -1 && last !is HeightMapText && !isCovered)
            nodes.add(HeightMapText(0, -1.0))
        else if (writtenTo < pos || last == null)
            nodes.add(blankContent(writtenTo, pos))
        var pos = from
        for (node in nodes) {
            if (node is HeightMapText) node.updateHeight(oracle, pos)
            pos += node?.length ?: 1
        }
        return nodes
    }

    companion object {
        // Always called with a region that on both sides either stretches
        // to a line break or the end of the document.
        // The returned array uses null to indicate line breaks, but never
        // starts or ends in a line break, or has multiple line breaks next
        // to each other.
        fun build(
            oracle: HeightOracle,
            decorations: List<DecorationSet>,
            from: Int,
            to: Int
        ): List<HeightMap?> {
            val builder = NodeBuilder(from, oracle)
            RangeSet.spans(decorations, from, to, builder, 0)
            return builder.finish(from)
        }
    }
}

fun heightRelevantDecoChanges(
    a: List<DecorationSet>,
    b: List<DecorationSet>,
    diff: ChangeSet
): List<Int> {
    val comp = DecorationComparator()
    RangeSet.compare(a, b, diff, comp, 0)
    return comp.changes
}

class DecorationComparator {
    val changes = mutableListOf<Int>()

    fun compareRange() {}

    fun comparePoint(from: Int, to: Int, a: Decoration?, b: Decoration?) {
        if (from < to || (a?.heightRelevant == true) || (b?.heightRelevant == true)) {
            addRange(from, to, changes, 5)
        }
    }
}
