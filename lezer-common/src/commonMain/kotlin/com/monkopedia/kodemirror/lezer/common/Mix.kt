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
package com.monkopedia.kodemirror.lezer.common

/**
 * Objects returned by the function passed to [parseMixed] should
 * conform to this interface.
 */
class NestedParse(
    val parser: Parser,
    val overlay: Any? = null,
    val bracketed: Boolean = false
)

/**
 * Create a parse wrapper that, after the inner parse completes,
 * scans its tree for mixed language regions with the [nest]
 * function, runs the resulting inner parses, and then mounts
 * their results onto the tree.
 */
fun parseMixed(nest: (node: SyntaxNodeRef, input: Input) -> NestedParse?): ParseWrapper {
    return { parse, input, fragments, ranges ->
        MixedParse(parse, nest, input, fragments, ranges)
    }
}

private val stoppedInner = NodeProp<Int>(perNode = true)

private class InnerParse(
    val parser: Parser,
    val parse: PartialParse,
    val overlay: List<TextRange>?,
    val bracketed: Boolean,
    val target: Tree,
    val from: Int
)

private class ActiveOverlay(
    val parser: Parser,
    val predicate: (SyntaxNodeRef) -> Any?,
    val mounts: List<ReusableMount>,
    val index: Int,
    val start: Int,
    val bracketed: Boolean,
    val target: Tree,
    val prev: ActiveOverlay?
) {
    var depth = 0
    val ranges = mutableListOf<TextRange>()
}

private data class ReusableMount(
    val frag: TreeFragment,
    val mount: MountedTree,
    val pos: Int
)

private data class CoverInfo(
    val ranges: List<TextRange>,
    var depth: Int,
    val prev: CoverInfo?
)

private enum class Cover { None, Partial, Full }

private class MixedParse(
    base: PartialParse,
    val nest: (SyntaxNodeRef, Input) -> NestedParse?,
    val input: Input,
    val fragments: List<TreeFragment>,
    val ranges: List<TextRange>
) : PartialParse {

    private var baseParse: PartialParse? = base
    private val inner = mutableListOf<InnerParse>()
    private var innerDone = 0
    private var baseTree: Tree? = null
    override var stoppedAt: Int? = null
        private set

    override fun advance(): Tree? {
        val bp = baseParse
        if (bp != null) {
            val done = bp.advance() ?: return null
            baseParse = null
            baseTree = done
            startInner()
            val sa = stoppedAt
            if (sa != null) {
                for (innerP in inner) innerP.parse.stopAt(sa)
            }
        }
        if (innerDone == inner.size) {
            return baseTree!!
        }
        val innerP = inner[innerDone]
        val done = innerP.parse.advance()
        if (done != null) {
            innerDone++
            val props = innerP.target.type.props.toMutableMap()
            props[NodeProp.mounted.id] = MountedTree(done, innerP.overlay, innerP.parser)
        }
        return null
    }

    override val parsedPos: Int
        get() {
            if (baseParse != null) return 0
            var pos = input.length
            for (i in innerDone until inner.size) {
                if (inner[i].from < pos) {
                    pos = minOf(pos, inner[i].parse.parsedPos)
                }
            }
            return pos
        }

    override fun stopAt(pos: Int) {
        stoppedAt = pos
        val bp = baseParse
        if (bp != null) {
            bp.stopAt(pos)
        } else {
            for (i in innerDone until inner.size) {
                inner[i].parse.stopAt(pos)
            }
        }
    }

    private fun startInner() {
        val fragmentCursor = FragmentCursor(fragments)
        var overlay: ActiveOverlay? = null
        var covered: CoverInfo? = null
        val cursor = baseTree!!.cursor(
            IterMode.INCLUDE_ANONYMOUS or IterMode.IGNORE_MOUNTS
        )

        scan@ while (true) {
            var enter = true
            val sa = stoppedAt
            if (sa != null && cursor.from >= sa) {
                enter = false
            } else if (fragmentCursor.hasNode(cursor)) {
                val ov = overlay
                if (ov != null) {
                    val match = ov.mounts.find { m ->
                        m.frag.from <= cursor.from &&
                            m.frag.to >= cursor.to &&
                            m.mount.overlay != null
                    }
                    if (match != null) {
                        for (r in match.mount.overlay!!) {
                            val from = r.from + match.pos
                            val to = r.to + match.pos
                            if (from >= cursor.from && to <= cursor.to &&
                                !ov.ranges.any { it.from < to && it.to > from }
                            ) {
                                ov.ranges.add(TextRange(from, to))
                            }
                        }
                    }
                }
                enter = false
            } else {
                val cov = covered
                val isCovered = if (cov != null) {
                    checkCover(cov.ranges, cursor.from, cursor.to)
                } else {
                    Cover.None
                }
                if (cov != null && isCovered != Cover.None) {
                    enter = isCovered != Cover.Full
                } else if (!cursor.type.isAnonymous) {
                    val nestResult = nest(cursor, input)
                    if (nestResult != null &&
                        (cursor.from < cursor.to || nestResult.overlay == null)
                    ) {
                        val oldMounts = fragmentCursor.findMounts(
                            cursor.from,
                            nestResult.parser
                        )
                        val ov = nestResult.overlay
                        if (ov is Function1<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            val pred = ov as (SyntaxNodeRef) -> Any?
                            overlay = ActiveOverlay(
                                nestResult.parser,
                                pred,
                                oldMounts,
                                inner.size,
                                cursor.from,
                                nestResult.bracketed,
                                cursor.tree!!,
                                overlay
                            )
                        } else {
                            @Suppress("UNCHECKED_CAST")
                            val overlayRanges = ov as? List<TextRange>
                            val parseRanges = punchRanges(
                                ranges,
                                overlayRanges ?: if (cursor.from < cursor.to) {
                                    listOf(TextRange(cursor.from, cursor.to))
                                } else {
                                    emptyList()
                                }
                            )
                            if (parseRanges.isNotEmpty() || overlayRanges == null) {
                                inner.add(
                                    InnerParse(
                                        nestResult.parser,
                                        if (parseRanges.isNotEmpty()) {
                                            nestResult.parser.startParse(
                                                input,
                                                enterFragments(oldMounts, parseRanges),
                                                parseRanges
                                            )
                                        } else {
                                            nestResult.parser.startParse("")
                                        },
                                        overlayRanges?.map {
                                            TextRange(
                                                it.from - cursor.from,
                                                it.to - cursor.from
                                            )
                                        },
                                        nestResult.bracketed,
                                        cursor.tree!!,
                                        if (parseRanges.isNotEmpty()) {
                                            parseRanges[0].from
                                        } else {
                                            cursor.from
                                        }
                                    )
                                )
                            }
                            if (overlayRanges == null) {
                                enter = false
                            } else if (parseRanges.isNotEmpty()) {
                                covered = CoverInfo(parseRanges, 0, covered)
                            }
                        }
                    }
                } else {
                    val ov = overlay
                    if (ov != null) {
                        val range = ov.predicate(cursor)
                        if (range != null) {
                            val r = if (range == true) {
                                TextRange(cursor.from, cursor.to)
                            } else {
                                range as TextRange
                            }
                            if (r.from < r.to) {
                                val last = ov.ranges.size - 1
                                if (last >= 0 && ov.ranges[last].to == r.from) {
                                    ov.ranges[last] = TextRange(
                                        ov.ranges[last].from, r.to
                                    )
                                } else {
                                    ov.ranges.add(r)
                                }
                            }
                        }
                    }
                }
            }
            if (enter && cursor.firstChild()) {
                overlay?.let { it.depth++ }
                covered?.let { it.depth++ }
            } else {
                while (true) {
                    if (cursor.nextSibling()) break
                    if (!cursor.parent()) break@scan
                    val ov = overlay
                    if (ov != null) {
                        ov.depth--
                        if (ov.depth == 0) {
                            val punchedRanges = punchRanges(ranges, ov.ranges)
                            if (punchedRanges.isNotEmpty()) {
                                inner.add(
                                    ov.index,
                                    InnerParse(
                                        ov.parser,
                                        ov.parser.startParse(
                                            input,
                                            enterFragments(
                                                ov.mounts,
                                                punchedRanges
                                            ),
                                            punchedRanges
                                        ),
                                        ov.ranges.map {
                                            TextRange(
                                                it.from - ov.start,
                                                it.to - ov.start
                                            )
                                        },
                                        ov.bracketed,
                                        ov.target,
                                        punchedRanges[0].from
                                    )
                                )
                            }
                            overlay = ov.prev
                        }
                    }
                    val cov = covered
                    if (cov != null) {
                        cov.depth--
                        if (cov.depth == 0) covered = cov.prev
                    }
                }
            }
        }
    }
}

private fun checkCover(covered: List<TextRange>, from: Int, to: Int): Cover {
    for (range in covered) {
        if (range.from >= to) break
        if (range.to > from) {
            return if (range.from <= from && range.to >= to) {
                Cover.Full
            } else {
                Cover.Partial
            }
        }
    }
    return Cover.None
}

private class StructureCursor(root: Tree, private val offset: Int) {
    val cursor: TreeCursor = root.cursor(
        IterMode.INCLUDE_ANONYMOUS or IterMode.IGNORE_MOUNTS
    )
    var done = false

    fun moveTo(pos: Int) {
        val p = pos - offset
        while (!done && cursor.from < p) {
            if (cursor.to >= pos) {
                cursor.enter(
                    p,
                    1,
                    IterMode.IGNORE_OVERLAYS or IterMode.EXCLUDE_BUFFERS
                )
            } else if (!cursor.next(false)) {
                done = true
            }
        }
    }

    fun hasNode(cursor: TreeCursor): Boolean {
        moveTo(cursor.from)
        if (!done && this.cursor.from + offset == cursor.from &&
            this.cursor.tree != null
        ) {
            var tree = this.cursor.tree!!
            while (true) {
                if (tree === cursor.tree) return true
                if (tree.children.isNotEmpty() &&
                    tree.positions[0] == 0 &&
                    tree.children[0] is Tree
                ) {
                    tree = tree.children[0] as Tree
                } else {
                    break
                }
            }
        }
        return false
    }
}

private class FragmentCursor(val fragments: List<TreeFragment>) {
    var curFrag: TreeFragment? = null
    var curTo = 0
    var fragI = 0
    var inner: StructureCursor? = null

    init {
        if (fragments.isNotEmpty()) {
            val first = fragments[0]
            curFrag = first
            curTo = first.tree.prop(stoppedInner) ?: first.to
            inner = StructureCursor(first.tree, -first.offset)
        }
    }

    fun hasNode(node: TreeCursor): Boolean {
        while (curFrag != null && node.from >= curTo) nextFrag()
        val cf = curFrag ?: return false
        return cf.from <= node.from && curTo >= node.to &&
            inner!!.hasNode(node)
    }

    fun nextFrag() {
        fragI++
        if (fragI == fragments.size) {
            curFrag = null
            inner = null
        } else {
            val frag = fragments[fragI]
            curFrag = frag
            curTo = frag.tree.prop(stoppedInner) ?: frag.to
            inner = StructureCursor(frag.tree, -frag.offset)
        }
    }

    fun findMounts(pos: Int, parser: Parser): List<ReusableMount> {
        val result = mutableListOf<ReusableMount>()
        val inn = inner ?: return result
        inn.cursor.moveTo(pos, 1)
        var node: SyntaxNode? = inn.cursor.node
        while (node != null) {
            val mount = node.tree?.prop(NodeProp.mounted)
            if (mount != null && mount.parser === parser) {
                for (i in fragI until fragments.size) {
                    val frag = fragments[i]
                    if (frag.from >= node.to) break
                    if (frag.tree === curFrag!!.tree) {
                        result.add(
                            ReusableMount(
                                frag,
                                mount,
                                node.from - frag.offset
                            )
                        )
                    }
                }
            }
            node = node.parent
        }
        return result
    }
}

private fun punchRanges(outer: List<TextRange>, ranges: List<TextRange>): List<TextRange> {
    var copy: MutableList<TextRange>? = null
    var current: List<TextRange> = ranges
    var j = 0
    for (i in 1 until outer.size) {
        val gapFrom = outer[i - 1].to
        val gapTo = outer[i].from
        while (j < current.size) {
            val r = current[j]
            if (r.from >= gapTo) break
            if (r.to <= gapFrom) {
                j++
                continue
            }
            if (copy == null) {
                copy = ranges.toMutableList()
                current = copy
            }
            if (r.from < gapFrom) {
                copy[j] = TextRange(r.from, gapFrom)
                if (r.to > gapTo) {
                    copy.add(j + 1, TextRange(gapTo, r.to))
                }
            } else if (r.to > gapTo) {
                copy[j] = TextRange(gapTo, r.to)
                j--
            } else {
                copy.removeAt(j)
                j--
            }
            j++
        }
    }
    return current
}

private fun findCoverChanges(
    a: List<TextRange>,
    b: List<TextRange>,
    from: Int,
    to: Int
): List<TextRange> {
    var iA = 0
    var iB = 0
    var inA = false
    var inB = false
    var pos = Int.MIN_VALUE
    val result = mutableListOf<TextRange>()
    while (true) {
        val nextA = when {
            iA == a.size -> Int.MAX_VALUE
            inA -> a[iA].to
            else -> a[iA].from
        }
        val nextB = when {
            iB == b.size -> Int.MAX_VALUE
            inB -> b[iB].to
            else -> b[iB].from
        }
        if (inA != inB) {
            val start = maxOf(pos, from)
            val end = minOf(nextA, nextB, to)
            if (start < end) result.add(TextRange(start, end))
        }
        pos = minOf(nextA, nextB)
        if (pos == Int.MAX_VALUE) break
        if (nextA == pos) {
            if (!inA) {
                inA = true
            } else {
                inA = false
                iA++
            }
        }
        if (nextB == pos) {
            if (!inB) {
                inB = true
            } else {
                inB = false
                iB++
            }
        }
    }
    return result
}

private fun enterFragments(
    mounts: List<ReusableMount>,
    ranges: List<TextRange>
): List<TreeFragment> {
    val result = mutableListOf<TreeFragment>()
    for (rm in mounts) {
        val pos = rm.pos
        val mount = rm.mount
        val frag = rm.frag
        val startPos = pos + (mount.overlay?.get(0)?.from ?: 0)
        val endPos = startPos + mount.tree.length
        val fragFrom = maxOf(frag.from, startPos)
        val fragTo = minOf(frag.to, endPos)
        if (mount.overlay != null) {
            val overlayRanges = mount.overlay.map {
                TextRange(it.from + pos, it.to + pos)
            }
            val changes = findCoverChanges(ranges, overlayRanges, fragFrom, fragTo)
            var i = 0
            var p = fragFrom
            while (true) {
                val last = i == changes.size
                val end = if (last) fragTo else changes[i].from
                if (end > p) {
                    result.add(
                        TreeFragment(
                            p,
                            end,
                            mount.tree,
                            -startPos,
                            frag.from >= p || frag.openStart,
                            frag.to <= end || frag.openEnd
                        )
                    )
                }
                if (last) break
                p = changes[i].to
                i++
            }
        } else {
            result.add(
                TreeFragment(
                    fragFrom,
                    fragTo,
                    mount.tree,
                    -startPos,
                    frag.from >= startPos || frag.openStart,
                    frag.to <= endPos || frag.openEnd
                )
            )
        }
    }
    return result
}
