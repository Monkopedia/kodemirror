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
package com.monkopedia.kodemirror.lezer.lr

import com.monkopedia.kodemirror.lezer.common.IterMode
import com.monkopedia.kodemirror.lezer.common.NodeProp
import com.monkopedia.kodemirror.lezer.common.NodeSet
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.lezer.common.TreeBuffer
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import kotlin.math.max
import kotlin.math.min

/**
 * Cut a tree at [pos], returning a safe boundary position on the given [side].
 *
 * Walks down the tree to find a non-error node boundary near [pos] and
 * returns a position offset by [Lookahead.Margin] so that incremental
 * reparsing does not lose context.
 */
internal fun cutAt(tree: Tree, pos: Int, side: Int): Int {
    val cursor = tree.cursor(IterMode.IncludeAnonymous)
    cursor.moveTo(pos)
    while (true) {
        if (!(if (side < 0) cursor.childBefore(pos) else cursor.childAfter(pos))) {
            while (true) {
                if ((if (side < 0) cursor.to < pos else cursor.from > pos) &&
                    !cursor.type.isError
                ) {
                    return if (side < 0) {
                        max(0, min(cursor.to - 1, pos - Lookahead.Margin))
                    } else {
                        min(tree.length, max(cursor.from + 1, pos + Lookahead.Margin))
                    }
                }
                if (if (side < 0) cursor.prevSibling() else cursor.nextSibling()) break
                if (!cursor.parent()) return if (side < 0) 0 else tree.length
            }
        }
    }
}

/**
 * Cursor that walks through [TreeFragment] instances to find reusable
 * [Tree] nodes during incremental parsing.
 *
 * Used by the LR parser to skip over unchanged portions of the input
 * by reusing subtrees from a previous parse.
 */
internal class FragmentCursor(
    private val fragments: List<TreeFragment>,
    private val nodeSet: NodeSet
) {
    private var i = 0
    private var fragment: TreeFragment? = null
    private var safeFrom = -1
    private var safeTo = -1
    private val trees = mutableListOf<Tree>()
    private val start = mutableListOf<Int>()
    private val index = mutableListOf<Int>()
    var nextStart: Int = 0
        private set

    init {
        nextFragment()
    }

    /**
     * Advance to the next [TreeFragment], computing safe reuse
     * boundaries via [cutAt] when the fragment has open start/end.
     */
    fun nextFragment() {
        val fr = if (i == fragments.size) null else fragments[i++]
        fragment = fr
        if (fr != null) {
            safeFrom = if (fr.openStart) {
                cutAt(fr.tree, fr.from + fr.offset, 1) - fr.offset
            } else {
                fr.from
            }
            safeTo = if (fr.openEnd) {
                cutAt(fr.tree, fr.to + fr.offset, -1) - fr.offset
            } else {
                fr.to
            }
            trees.clear()
            start.clear()
            index.clear()
            trees.add(fr.tree)
            start.add(-fr.offset)
            index.add(0)
            nextStart = safeFrom
        } else {
            nextStart = 1_000_000_000
        }
    }

    /**
     * Try to find a reusable [Tree] node at the given [pos].
     *
     * [pos] must be >= any previously given pos for this cursor.
     * Returns a [Tree] that can be reused, or null if none is available.
     */
    fun nodeAt(pos: Int): Tree? {
        if (pos < nextStart) return null
        while (fragment != null && safeTo <= pos) nextFragment()
        if (fragment == null) return null

        while (true) {
            val last = trees.size - 1
            if (last < 0) {
                // End of tree
                nextFragment()
                return null
            }
            val top = trees[last]
            val idx = index[last]
            if (idx == top.children.size) {
                trees.removeAt(last)
                start.removeAt(last)
                index.removeAt(last)
                continue
            }
            val next = top.children[idx]
            val childStart = start[last] + top.positions[idx]
            if (childStart > pos) {
                nextStart = childStart
                return null
            }
            if (next is Tree) {
                if (childStart == pos) {
                    if (childStart < safeFrom) return null
                    val end = childStart + next.length
                    if (end <= safeTo) {
                        val lookAhead = next.prop(NodeProp.lookAhead)
                        if (lookAhead == null || end + lookAhead < fragment!!.to) {
                            return next
                        }
                    }
                }
                index[last]++
                if (childStart + next.length >= max(safeFrom, pos)) {
                    // Enter this node
                    trees.add(next)
                    start.add(childStart)
                    index.add(0)
                }
            } else {
                index[last]++
                nextStart = childStart + (next as TreeBuffer).length
            }
        }
    }
}
