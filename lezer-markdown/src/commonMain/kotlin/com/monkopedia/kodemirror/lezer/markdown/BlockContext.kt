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
package com.monkopedia.kodemirror.lezer.markdown

import com.monkopedia.kodemirror.lezer.common.Input
import com.monkopedia.kodemirror.lezer.common.PartialParse
import com.monkopedia.kodemirror.lezer.common.TextRange
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.lezer.common.TreeFragment

internal class ScanLineResult(var text: String = "", var end: Int = 0)

private val scanLineResult = ScanLineResult()

class BlockContext(
    val parser: MarkdownParser,
    internal val input: Input,
    fragments: List<TreeFragment>,
    internal val ranges: List<TextRange>
) : PartialParse {
    var block: CompositeBlock
    val stack: MutableList<CompositeBlock>
    private val line = Line()
    private var atEnd = false
    private val to: Int
    override var stoppedAt: Int? = null

    var lineStart: Int
    internal var absoluteLineStart: Int
    internal var rangeI = 0
    internal var absoluteLineEnd: Int

    init {
        to = ranges.last().to
        lineStart = ranges[0].from
        absoluteLineStart = ranges[0].from
        absoluteLineEnd = ranges[0].from
        block = CompositeBlock.create(Type.Document, 0, lineStart, 0, 0)
        stack = mutableListOf(block)
        readLine()
    }

    override val parsedPos: Int get() = absoluteLineStart

    override fun advance(): Tree? {
        val stopped = stoppedAt
        if (stopped != null && absoluteLineStart > stopped) return finish()

        val line = this.line
        while (true) {
            var markI = 0
            while (true) {
                val next = if (line.depth < stack.size) stack[stack.size - 1] else null
                while (markI < line.markers.size &&
                    (next == null || line.markers[markI].from < next.end)
                ) {
                    val mark = line.markers[markI++]
                    addNode(mark.type, mark.from, mark.to)
                }
                if (next == null) break
                finishContext()
            }
            if (line.pos < line.text.length) break
            if (!nextLine()) return finish()
        }

        var startLoop = true
        while (startLoop) {
            startLoop = false
            for (type in parser.blockParsers) {
                if (type != null) {
                    val result = type(this, line)
                    if (result != false) {
                        if (result == true) return null
                        line.forward()
                        startLoop = true
                        break
                    }
                }
            }
        }

        val leaf = LeafBlock(lineStart + line.pos, line.text.substring(line.pos))
        for (parse in parser.leafBlockParsers) {
            if (parse != null) {
                val p = parse(this, leaf)
                if (p != null) leaf.parsers.add(p)
            }
        }
        lines@ while (nextLine()) {
            if (line.pos == line.text.length) break
            if (line.indent < line.baseIndent + 4) {
                for (stop in parser.endLeafBlock) {
                    if (stop(this, line, leaf)) break@lines
                }
            }
            for (p in leaf.parsers) {
                if (p.nextLine(this, line, leaf)) return null
            }
            leaf.content += "\n" + line.scrub()
            for (m in line.markers) leaf.marks.add(m)
        }
        finishLeaf(leaf)
        return null
    }

    override fun stopAt(pos: Int) {
        val stopped = stoppedAt
        if (stopped != null && stopped < pos) {
            throw IllegalArgumentException("Can't move stoppedAt forward")
        }
        stoppedAt = pos
    }

    val depth: Int get() = stack.size

    fun parentType(depth: Int = this.depth - 1) = parser.nodeSet.types[stack[depth].type]

    fun nextLine(): Boolean {
        lineStart += line.text.length
        if (absoluteLineEnd >= to) {
            absoluteLineStart = absoluteLineEnd
            atEnd = true
            readLine()
            return false
        } else {
            lineStart++
            absoluteLineStart = absoluteLineEnd + 1
            moveRangeI()
            readLine()
            return true
        }
    }

    fun prevLineEnd(): Int = if (atEnd) lineStart else lineStart - 1

    internal fun startContext(type: Int, start: Int, value: Int = 0) {
        block = CompositeBlock.create(
            type, value,
            lineStart + start, block.hash,
            lineStart + line.text.length
        )
        stack.add(block)
    }

    fun startComposite(type: String, start: Int, value: Int = 0) {
        startContext(parser.getNodeType(type), start, value)
    }

    fun addNode(block: Tree, from: Int) {
        this.block.addChild(block, from - this.block.from)
    }

    fun addNode(type: Int, from: Int, to: Int? = null) {
        val tree = Tree(
            parser.nodeSet.types[type],
            none,
            emptyList(),
            (to ?: prevLineEnd()) - from
        )
        this.block.addChild(tree, from - this.block.from)
    }

    fun addElement(elt: Element) {
        block.addChild(elt.toTree(parser.nodeSet), elt.from - block.from)
    }

    fun addLeafElement(leaf: LeafBlock, elt: Element) {
        addNode(
            buffer.writeElements(injectMarks(elt.children, leaf.marks), -elt.from)
                .finish(elt.type, elt.to - elt.from),
            elt.from
        )
    }

    internal fun finishContext() {
        val cx = stack.removeLast()
        val top = stack.last()
        top.addChild(cx.toTree(parser.nodeSet), cx.from - top.from)
        block = top
    }

    private fun finish(): Tree {
        while (stack.size > 1) finishContext()
        return block.toTree(parser.nodeSet, lineStart)
    }

    internal fun finishLeaf(leaf: LeafBlock) {
        for (p in leaf.parsers) if (p.finish(this, leaf)) return
        val inline = injectMarks(
            parser.parseInline(leaf.content, leaf.start),
            leaf.marks
        )
        addNode(
            buffer.writeElements(inline, -leaf.start)
                .finish(Type.Paragraph, leaf.content.length),
            leaf.start
        )
    }

    fun elt(type: String, from: Int, to: Int, children: List<Element>? = null): Element =
        elt(parser.getNodeType(type), from, to, children ?: emptyList())

    fun elt(tree: Tree, at: Int): Element = TreeElement(tree, at)

    val buffer: Buffer get() = Buffer(parser.nodeSet)

    // ---- Internal line scanning ----

    private fun moveRangeI() {
        while (rangeI < ranges.size - 1 && absoluteLineStart >= ranges[rangeI].to) {
            rangeI++
            absoluteLineStart = maxOf(absoluteLineStart, ranges[rangeI].from)
        }
    }

    internal fun scanLine(start: Int): ScanLineResult {
        val r = scanLineResult
        r.end = start
        if (start >= to) {
            r.text = ""
        } else {
            r.text = lineChunkAt(start)
            r.end += r.text.length
            if (ranges.size > 1) {
                var textOffset = absoluteLineStart
                var ri = rangeI
                while (ranges[ri].to < r.end) {
                    ri++
                    val nextFrom = ranges[ri].from
                    val after = lineChunkAt(nextFrom)
                    r.end = nextFrom + after.length
                    r.text = r.text.substring(0, ranges[ri - 1].to - textOffset) + after
                    textOffset = r.end - r.text.length
                }
            }
        }
        return r
    }

    internal fun readLine() {
        val line = this.line
        val result = scanLine(absoluteLineStart)
        absoluteLineEnd = result.end
        line.reset(result.text)
        while (line.depth < stack.size) {
            val cx = stack[line.depth]
            val handler = parser.skipContextMarkup[cx.type]
                ?: error("Unhandled block context ${cx.type}")
            val marks = this.line.markers.size
            if (!handler(cx, this, line)) {
                if (this.line.markers.size > marks) {
                    cx.end = this.line.markers.last().to
                }
                line.forward()
                break
            }
            line.forward()
            line.depth++
        }
    }

    private fun lineChunkAt(pos: Int): String {
        val next = input.chunk(pos)
        val text: String
        if (!input.lineChunks) {
            val eol = next.indexOf('\n')
            text = if (eol < 0) next else next.substring(0, eol)
        } else {
            text = if (next == "\n") "" else next
        }
        return if (pos + text.length > to) text.substring(0, to - pos) else text
    }
}
