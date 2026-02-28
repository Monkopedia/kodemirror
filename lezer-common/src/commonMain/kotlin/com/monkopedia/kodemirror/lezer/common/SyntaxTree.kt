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
 * A range with from/to positions.
 */
data class TextRange(val from: Int, val to: Int)

/**
 * Type for a function that wraps a [PartialParse] in additional logic.
 */
typealias ParseWrapper = (
    parse: PartialParse,
    input: Input,
    fragments: List<TreeFragment>,
    ranges: List<TextRange>
) -> PartialParse

/**
 * Linked list of syntax nodes returned by [Tree.resolveStack].
 */
data class NodeIterator(val node: SyntaxNode, val next: NodeIterator?)

/**
 * Input interface for parsers.
 */
interface Input {
    val length: Int
    fun chunk(pos: Int): String
    fun read(from: Int, to: Int): String
    val lineChunks: Boolean get() = false
}

/**
 * A simple string-based Input implementation.
 */
class StringInput(private val string: String) : Input {
    override val length: Int get() = string.length
    override fun chunk(pos: Int): String = string.substring(pos)
    override fun read(from: Int, to: Int): String = string.substring(from, to)
}

/**
 * Interface for incremental parsing.
 */
interface PartialParse {
    fun advance(): Tree?
    val parsedPos: Int
    val stoppedAt: Int?
    fun stopAt(pos: Int)
}

/**
 * Abstract base class for parsers.
 */
abstract class Parser {
    abstract fun createParse(
        input: Input,
        fragments: List<TreeFragment>,
        ranges: List<TextRange>
    ): PartialParse

    fun startParse(
        input: Input,
        fragments: List<TreeFragment> = emptyList(),
        ranges: List<TextRange>? = null
    ): PartialParse {
        val effectiveRanges = ranges ?: listOf(TextRange(0, input.length))
        return createParse(input, fragments, effectiveRanges)
    }

    fun startParse(
        input: String,
        fragments: List<TreeFragment> = emptyList(),
        ranges: List<TextRange>? = null
    ): PartialParse {
        return startParse(StringInput(input), fragments, ranges)
    }

    fun parse(
        input: Input,
        fragments: List<TreeFragment> = emptyList(),
        ranges: List<TextRange>? = null
    ): Tree {
        val parse = startParse(input, fragments, ranges)
        while (true) {
            val done = parse.advance()
            if (done != null) return done
        }
    }

    fun parse(
        input: String,
        fragments: List<TreeFragment> = emptyList(),
        ranges: List<TextRange>? = null
    ): Tree {
        return parse(StringInput(input), fragments, ranges)
    }
}

/**
 * Tree fragments for incremental parsing.
 */
class TreeFragment(
    val from: Int,
    val to: Int,
    val tree: Tree,
    val offset: Int,
    val openStart: Boolean = false,
    val openEnd: Boolean = false
) {
    companion object {
        fun addTree(
            tree: Tree,
            fragments: List<TreeFragment> = emptyList(),
            partial: Boolean = false
        ): List<TreeFragment> {
            val result = mutableListOf<TreeFragment>()
            result.add(TreeFragment(0, tree.length, tree, 0))
            for (f in fragments) {
                if (f.to <= tree.length && !partial) continue
                result.add(f)
            }
            return result
        }

        fun applyChanges(
            fragments: List<TreeFragment>,
            changes: List<ChangedRange>,
            minGap: Int = 128
        ): List<TreeFragment> {
            if (changes.isEmpty()) return fragments
            val result = mutableListOf<TreeFragment>()
            var fI = 1
            var nextF = if (fragments.isNotEmpty()) fragments[0] else null
            var cI = 0
            var pos = 0
            var off = 0

            while (true) {
                val nextC = if (cI < changes.size) changes[cI] else null

                if (nextF != null && (nextC == null || nextF.from <= nextC.fromA)) {
                    val cut = if (nextC != null) nextC.fromA - off else Int.MAX_VALUE
                    val from = nextF.from + off
                    val to = minOf(nextF.to + off, cut)
                    if (to > from && result.isNotEmpty()) {
                        val prev = result.last()
                        if (to - from < minGap && prev.to + minGap > from) {
                            // Too close to previous fragment, merge
                            result[result.lastIndex] = TreeFragment(
                                prev.from,
                                to,
                                prev.tree,
                                prev.offset,
                                prev.openStart,
                                to < nextF.to + off
                            )
                            nextF = if (fI < fragments.size) fragments[fI++] else null
                            continue
                        }
                    }
                    if (to > from) {
                        result.add(
                            TreeFragment(
                                from,
                                to,
                                nextF.tree,
                                nextF.offset + off,
                                nextF.openStart || from > nextF.from + off,
                                to < nextF.to + off
                            )
                        )
                    }
                    if (nextC != null && nextC.fromA <= nextF.to + off) {
                        // Change falls within/after fragment
                        nextF = if (fI < fragments.size) fragments[fI++] else null
                    } else {
                        nextF = if (fI < fragments.size) fragments[fI++] else null
                    }
                } else if (nextC != null) {
                    pos = nextC.toA
                    off = nextC.toB - nextC.toA
                    cI++
                    // Skip past fragments wholly before this change
                    while (nextF != null && nextF.to + off <= nextC.fromB) {
                        nextF = if (fI < fragments.size) fragments[fI++] else null
                    }
                } else {
                    break
                }
            }
            return result
        }
    }
}

/**
 * Represents a changed range for incremental parsing.
 */
data class ChangedRange(
    val fromA: Int,
    val toA: Int,
    val fromB: Int,
    val toB: Int
)

/**
 * Iteration mode flags.
 */
object IterMode {
    const val ExcludeBuffers = 1
    const val IncludeAnonymous = 2
    const val IgnoreMounts = 4
    const val IgnoreOverlays = 8
    const val EnterBracketed = 16
}

/**
 * Interface for a reference to a syntax node in a tree.
 */
interface SyntaxNodeRef {
    val from: Int
    val to: Int
    val type: NodeType
    val name: String get() = type.name

    /** Match this node against a list of parent names (innermost last). */
    fun matchContext(context: List<String>): Boolean
}

/**
 * A syntax node that provides navigation to parent and siblings.
 */
interface SyntaxNode : SyntaxNodeRef {
    val parent: SyntaxNode?
    val firstChild: SyntaxNode?
    val lastChild: SyntaxNode?
    val nextSibling: SyntaxNode?
    val prevSibling: SyntaxNode?

    /** Get the tree backing this node, if any. */
    val tree: Tree?

    /** Convert this node to a standalone Tree. */
    fun toTree(): Tree

    /** Get a child by name (optionally between before/after). */
    fun getChild(type: String, before: String? = null, after: String? = null): SyntaxNode?

    /** Get all children matching a type. */
    fun getChildren(type: String, before: String? = null, after: String? = null): List<SyntaxNode>

    /** Enter the node from a position. */
    fun enter(pos: Int, side: Int, mode: Int = 0): SyntaxNode?

    /** Navigate to a child after a position. */
    fun childAfter(pos: Int): SyntaxNode?

    /** Navigate to a child before a position. */
    fun childBefore(pos: Int): SyntaxNode?

    /** Resolve to the innermost node at a position. */
    fun resolve(pos: Int, side: Int = 0): SyntaxNode

    /**
     * Like [resolve], but enters overlay-mounted trees to find
     * the innermost node.
     */
    fun resolveInner(pos: Int, side: Int = 0): SyntaxNode

    /**
     * Walk the tree back from a position, finding the innermost
     * unfinished node (an error node) before the position.
     */
    fun enterUnfinishedNodesBefore(pos: Int): SyntaxNode

    /** Create a cursor from this node. */
    fun cursor(): TreeCursor
}

/**
 * Build specification for tree construction from a buffer cursor or flat buffer.
 */
data class TreeBuildSpec(
    // List<Int> or BufferCursor
    val buffer: Any,
    val nodeSet: NodeSet,
    val topID: Int,
    val start: Int = 0,
    val bufferStart: Int = 0,
    val length: Int? = null,
    val maxBufferLength: Int = DefaultBufferLength,
    val reused: List<Tree> = emptyList(),
    val minRepeatType: Int = -1
)

/**
 * Configuration for [Tree.balance].
 */
data class BalanceConfig(
    val makeTree: ((children: List<Any>, positions: List<Int>, length: Int) -> Tree)? = null
)

/**
 * Special marker values in the buffer for reuse, context change, and lookahead.
 */
internal object SpecialRecord {
    const val Reuse = -1
    const val ContextChange = -3
    const val LookAhead = -4
}

private const val BALANCE_BRANCH_FACTOR = 8

/**
 * A syntax tree. Immutable, constructed by parsers.
 * Children may be [Tree] or [TreeBuffer] instances.
 */
class Tree(
    val type: NodeType,
    // Tree | TreeBuffer
    val children: List<Any>,
    val positions: List<Int>,
    val length: Int,
    private val propValues: Map<Int, Any?> = emptyMap()
) {
    /** Look up a per-node prop value. */
    @Suppress("UNCHECKED_CAST")
    fun <T> prop(prop: NodeProp<T>): T? {
        if (prop.perNode) {
            return propValues[prop.id] as T?
        }
        return type.prop(prop)
    }

    /** The top node of this tree (wraps the tree as a SyntaxNode). */
    val topNode: SyntaxNode get() = TreeNode(this, 0, null, 0)

    /** Create a cursor that starts at the top of the tree. */
    fun cursor(mode: Int = 0): TreeCursor = TreeCursor(this, mode)

    /** Create a cursor positioned at a given position. */
    fun cursorAt(pos: Int, side: Int = 0, mode: Int = 0): TreeCursor {
        val cursor = TreeCursor(this, mode)
        cursor.moveTo(pos, side)
        return cursor
    }

    /** Resolve to the innermost node at position [pos]. */
    fun resolve(pos: Int, side: Int = 0): SyntaxNode {
        return topNode.resolve(pos, side)
    }

    /**
     * Like [resolve], but enters overlay-mounted trees.
     */
    fun resolveInner(pos: Int, side: Int = 0): SyntaxNode {
        return resolveNode(this, pos, side, false)
    }

    /**
     * Returns a [NodeIterator] pointing at the innermost node at [pos],
     * with its [NodeIterator.next] linking to enclosing nodes up to the top.
     */
    fun resolveStack(pos: Int, side: Int = 0): NodeIterator {
        return stackIterator(this, pos, side)
    }

    /**
     * Balance a tree, making sure no children arrays grow
     * too large for optimal performance.
     */
    fun balance(config: BalanceConfig = BalanceConfig()): Tree {
        if (children.size <= BALANCE_BRANCH_FACTOR) return this
        return balanceRange(
            type,
            children,
            positions,
            0,
            children.size,
            0,
            length,
            { ch, pos, len ->
                Tree(
                    config.makeTree?.invoke(ch, pos, len)?.type ?: type,
                    ch,
                    pos,
                    len
                )
            },
            { ch, pos, len ->
                Tree(
                    config.makeTree?.invoke(ch, pos, len)?.type ?: type,
                    ch,
                    pos,
                    len
                )
            }
        )
    }

    /** Iterate over all nodes in the tree within a range. */
    fun iterate(spec: IterateSpec) {
        val from = spec.from ?: 0
        val to = spec.to ?: this.length
        val mode = spec.mode ?: 0
        val anon = (mode and IterMode.IncludeAnonymous) != 0
        val c = this.cursor(mode or IterMode.IncludeAnonymous)
        loop@ while (true) {
            var entered = false
            if (c.from <= to && c.to >= from &&
                (!anon && c.type.isAnonymous || spec.enter(c) != false)
            ) {
                if (c.firstChild()) continue@loop
                entered = true
            }
            while (true) {
                if (entered && spec.leave != null &&
                    (anon || !c.type.isAnonymous)
                ) {
                    spec.leave.invoke(c)
                }
                if (c.nextSibling()) break
                if (!c.parent()) return
                entered = true
            }
        }
    }

    override fun toString(): String {
        return buildString {
            appendTreeString(this@Tree, this)
        }
    }

    companion object {
        /** An empty tree (zero-length, anonymous type). */
        val empty = Tree(NodeType.none, emptyList(), emptyList(), 0)

        /**
         * Build a tree from a flat buffer representation or BufferCursor.
         */
        fun build(spec: TreeBuildSpec): Tree {
            return buildTree(spec)
        }
    }
}

/** Get the length of a child (Tree or TreeBuffer). */
internal fun childLength(child: Any): Int = when (child) {
    is Tree -> child.length
    is TreeBuffer -> child.length
    else -> error("Unknown child type: $child")
}

/** Get the type of a child (Tree or TreeBuffer). */
internal fun childType(child: Any): NodeType = when (child) {
    is Tree -> child.type
    is TreeBuffer -> child.type
    else -> error("Unknown child type: $child")
}

/** Check if a child is anonymous. */
internal val NodeType.isAnonymous: Boolean
    get() = this == NodeType.none || this.name.isEmpty()

/**
 * Specification for [Tree.iterate].
 */
data class IterateSpec(
    val enter: (SyntaxNodeRef) -> Boolean?,
    val leave: ((SyntaxNodeRef) -> Unit)? = null,
    val from: Int? = null,
    val to: Int? = null,
    val mode: Int? = null
)

// ---- TreeNode: wraps Tree as a SyntaxNode ----

internal class TreeNode(
    internal val _tree: Tree,
    val treeFrom: Int,
    internal val _parent: TreeNode?,
    internal val index: Int
) : SyntaxNode {
    override val from: Int get() = treeFrom
    override val to: Int get() = treeFrom + _tree.length
    override val type: NodeType get() = _tree.type
    override val tree: Tree get() = _tree

    override val parent: SyntaxNode?
        get() = _parent?.nextSignificantParent()

    override fun matchContext(context: List<String>): Boolean {
        return matchNodeContext(parent, context)
    }

    override fun getChild(type: String, before: String?, after: String?): SyntaxNode? {
        return getChildren(type, before, after).firstOrNull()
    }

    override fun getChildren(type: String, before: String?, after: String?): List<SyntaxNode> {
        val cur = this.cursor()
        val result = mutableListOf<SyntaxNode>()
        if (!cur.firstChild()) return result
        if (before != null) {
            var found = false
            while (!found) {
                found = cur.type.`is`(before)
                if (!cur.nextSibling()) return result
            }
        }
        while (true) {
            if (after != null && cur.type.`is`(after)) return result
            if (cur.type.`is`(type)) result.add(cur.node)
            if (!cur.nextSibling()) return if (after == null) result else emptyList()
        }
    }

    override val firstChild: SyntaxNode?
        get() = nextChild(0, 1, 0, Side.DontCare)

    override val lastChild: SyntaxNode?
        get() = nextChild(_tree.children.size - 1, -1, 0, Side.DontCare)

    override val nextSibling: SyntaxNode?
        get() = if (_parent != null && index >= 0) {
            _parent.nextChild(index + 1, 1, 0, Side.DontCare)
        } else {
            null
        }

    override val prevSibling: SyntaxNode?
        get() = if (_parent != null && index >= 0) {
            _parent.nextChild(index - 1, -1, 0, Side.DontCare)
        } else {
            null
        }

    internal fun nextChild(i: Int, dir: Int, pos: Int, side: Int, mode: Int = 0): SyntaxNode? {
        var parent: TreeNode = this
        var idx = i
        while (true) {
            val children = parent._tree.children
            val positions = parent._tree.positions
            val e = if (dir > 0) children.size else -1
            while (idx != e) {
                val next = children[idx]
                val start = positions[idx] + parent.treeFrom
                if (!checkSide(side, pos, start, start + childLength(next))) {
                    idx += dir
                    continue
                }
                if (next is TreeBuffer) {
                    if (mode and IterMode.ExcludeBuffers != 0) {
                        idx += dir
                        continue
                    }
                    val bufIdx = next.findChild(
                        0,
                        next.buffer.size,
                        dir,
                        pos - start,
                        side
                    )
                    if (bufIdx > -1) {
                        return BufferNode(
                            BufferContext(parent, next, idx, start),
                            null,
                            bufIdx
                        )
                    }
                } else if (next is Tree) {
                    if (mode and IterMode.IncludeAnonymous != 0 ||
                        !next.type.isAnonymous || hasChild(next)
                    ) {
                        val inner = TreeNode(next, start, idx, parent)
                        return if (mode and IterMode.IncludeAnonymous != 0 ||
                            !inner.type.isAnonymous
                        ) {
                            inner
                        } else {
                            inner.nextChild(
                                if (dir < 0) next.children.size - 1 else 0,
                                dir,
                                pos,
                                side,
                                mode
                            )
                        }
                    }
                }
                idx += dir
            }
            if (mode and IterMode.IncludeAnonymous != 0 ||
                !parent.type.isAnonymous
            ) {
                return null
            }
            if (parent.index >= 0) {
                idx = parent.index + dir
            } else {
                idx = if (dir < 0) -1 else parent._parent!!._tree.children.size
            }
            parent = parent._parent ?: return null
        }
    }

    override fun enter(pos: Int, side: Int, mode: Int): SyntaxNode? {
        return nextChild(0, 1, pos, side, mode)
    }

    override fun childAfter(pos: Int): SyntaxNode? {
        return nextChild(0, 1, pos, Side.After)
    }

    override fun childBefore(pos: Int): SyntaxNode? {
        return nextChild(
            _tree.children.size - 1,
            -1,
            pos,
            Side.Before
        )
    }

    override fun resolve(pos: Int, side: Int): SyntaxNode {
        var node: SyntaxNode = this
        while (true) {
            val inner = node.enter(pos, side) ?: return node
            node = inner
        }
    }

    override fun resolveInner(pos: Int, side: Int): SyntaxNode {
        return resolveNode(this._tree, pos, side, false)
    }

    override fun enterUnfinishedNodesBefore(pos: Int): SyntaxNode {
        return enterUnfinishedNodesBefore(this, pos)
    }

    override fun cursor(): TreeCursor = _tree.cursor()

    override fun toTree(): Tree = _tree

    override fun toString(): String = _tree.toString()

    internal fun nextSignificantParent(): TreeNode {
        var v: TreeNode = this
        while (v.type.isAnonymous && v._parent != null) v = v._parent!!
        return v
    }
}

/** Secondary constructor for TreeNode with swapped parent/index params. */
private fun TreeNode(tree: Tree, from: Int, index: Int, parent: TreeNode): TreeNode =
    TreeNode(tree, from, parent, index)

// ---- BufferContext and BufferNode ----

internal class BufferContext(
    val parent: TreeNode,
    val buffer: TreeBuffer,
    val index: Int,
    val start: Int
)

internal class BufferNode(
    val context: BufferContext,
    val _parent: BufferNode?,
    val index: Int
) : SyntaxNode {
    override val type: NodeType =
        context.buffer.set.types[context.buffer.buffer[index]]

    override val name: String get() = type.name

    override val from: Int
        get() = context.start + context.buffer.buffer[index + 1]

    override val to: Int
        get() = context.start + context.buffer.buffer[index + 2]

    override val tree: Tree? get() = null

    private fun child(dir: Int, pos: Int, side: Int): BufferNode? {
        val buffer = context.buffer
        val childIdx = buffer.findChild(
            index + 4,
            buffer.buffer[index + 3],
            dir,
            pos - context.start,
            side
        )
        return if (childIdx < 0) {
            null
        } else {
            BufferNode(context, this, childIdx)
        }
    }

    override val firstChild: SyntaxNode?
        get() = child(1, 0, Side.DontCare)
    override val lastChild: SyntaxNode?
        get() = child(-1, 0, Side.DontCare)

    override fun childAfter(pos: Int): SyntaxNode? = child(1, pos, Side.After)

    override fun childBefore(pos: Int): SyntaxNode? = child(-1, pos, Side.Before)

    override fun enter(pos: Int, side: Int, mode: Int): SyntaxNode? {
        if (mode and IterMode.ExcludeBuffers != 0) return null
        val buffer = context.buffer
        val childIdx = buffer.findChild(
            index + 4,
            buffer.buffer[index + 3],
            if (side > 0) 1 else -1,
            pos - context.start,
            side
        )
        return if (childIdx < 0) {
            null
        } else {
            BufferNode(context, this, childIdx)
        }
    }

    override val parent: SyntaxNode?
        get() = _parent ?: context.parent.nextSignificantParent()

    private fun externalSibling(dir: Int): SyntaxNode? {
        return if (_parent != null) {
            null
        } else {
            context.parent.nextChild(
                context.index + dir,
                dir,
                0,
                Side.DontCare
            )
        }
    }

    override val nextSibling: SyntaxNode?
        get() {
            val buffer = context.buffer
            val after = buffer.buffer[index + 3]
            val limit = if (_parent != null) {
                buffer.buffer[_parent.index + 3]
            } else {
                buffer.buffer.size
            }
            return if (after < limit) {
                BufferNode(context, _parent, after)
            } else {
                externalSibling(1)
            }
        }

    override val prevSibling: SyntaxNode?
        get() {
            val buffer = context.buffer
            val parentStart = if (_parent != null) _parent.index + 4 else 0
            return if (index == parentStart) {
                externalSibling(-1)
            } else {
                BufferNode(
                    context,
                    _parent,
                    buffer.findChild(
                        parentStart,
                        index,
                        -1,
                        0,
                        Side.DontCare
                    )
                )
            }
        }

    override fun getChild(type: String, before: String?, after: String?): SyntaxNode? {
        return getChildren(type, before, after).firstOrNull()
    }

    override fun getChildren(type: String, before: String?, after: String?): List<SyntaxNode> {
        val cur = this.cursor()
        val result = mutableListOf<SyntaxNode>()
        if (!cur.firstChild()) return result
        if (before != null) {
            var found = false
            while (!found) {
                found = cur.type.`is`(before)
                if (!cur.nextSibling()) return result
            }
        }
        while (true) {
            if (after != null && cur.type.`is`(after)) return result
            if (cur.type.`is`(type)) result.add(cur.node)
            if (!cur.nextSibling()) {
                return if (after == null) result else emptyList()
            }
        }
    }

    override fun resolve(pos: Int, side: Int): SyntaxNode {
        var node: SyntaxNode = this
        while (true) {
            val inner = node.enter(pos, side) ?: return node
            node = inner
        }
    }

    override fun resolveInner(pos: Int, side: Int): SyntaxNode {
        return resolveNode(context.parent._tree, pos, side, false)
    }

    override fun enterUnfinishedNodesBefore(pos: Int): SyntaxNode {
        return enterUnfinishedNodesBefore(this, pos)
    }

    override fun cursor(): TreeCursor = context.parent._tree.cursor()

    override fun toTree(): Tree {
        val children = mutableListOf<Any>()
        val positions = mutableListOf<Int>()
        val buffer = context.buffer
        val startI = index + 4
        val endI = buffer.buffer[index + 3]
        if (endI > startI) {
            val bufFrom = buffer.buffer[index + 1]
            children.add(buffer.slice(startI, endI, bufFrom))
            positions.add(0)
        }
        return Tree(type, children, positions, to - from)
    }

    override fun matchContext(context: List<String>): Boolean {
        return matchNodeContext(parent, context)
    }

    override fun toString(): String = this.context.buffer.childString(index)
}

// ---- TreeCursor ----

/**
 * Mutable tree walker. Walks a [Tree] depth-first, handling both
 * [Tree] and [TreeBuffer] children.
 */
class TreeCursor internal constructor(
    root: Tree,
    internal val mode: Int = 0
) : SyntaxNodeRef {
    internal var _tree: TreeNode = TreeNode(root, 0, null, 0)
    internal var buffer: BufferContext? = null
    private val stack = mutableListOf<Int>()
    internal var index: Int = 0
    private var bufferNode: BufferNode? = null

    override var type: NodeType = root.type
        private set
    override var from: Int = 0
        private set
    override var to: Int = root.length
        private set
    override val name: String get() = type.name

    private fun yieldNode(node: TreeNode?): Boolean {
        if (node == null) return false
        _tree = node
        type = node.type
        from = node.from
        to = node.to
        return true
    }

    private fun yieldBuf(index: Int, nodeType: NodeType? = null): Boolean {
        this.index = index
        val buf = buffer!!
        type = nodeType
            ?: buf.buffer.set.types[buf.buffer.buffer[index]]
        from = buf.start + buf.buffer.buffer[index + 1]
        to = buf.start + buf.buffer.buffer[index + 2]
        return true
    }

    private fun yieldResult(node: SyntaxNode?): Boolean {
        if (node == null) return false
        if (node is TreeNode) {
            buffer = null
            return yieldNode(node)
        }
        if (node is BufferNode) {
            buffer = node.context
            return yieldBuf(node.index, node.type)
        }
        return false
    }

    /** The Tree object backing the current node, or null if in a buffer. */
    val tree: Tree? get() = if (buffer != null) null else _tree._tree

    /** Get the current position as a SyntaxNode. */
    val node: SyntaxNode
        get() {
            if (buffer == null) return _tree
            val cache = bufferNode
            var result: BufferNode? = null
            var depth = 0
            if (cache != null && cache.context == buffer) {
                scan@ for (d in stack.size downTo 0) {
                    val idx = if (d < stack.size) stack[d] else index
                    var c: BufferNode? = cache
                    while (c != null) {
                        if (c.index == idx) {
                            if (idx == index) return c
                            result = c
                            depth = d + 1
                            break@scan
                        }
                        c = c._parent
                    }
                }
            }
            for (i in depth until stack.size) {
                result = BufferNode(buffer!!, result, stack[i])
            }
            val final = BufferNode(buffer!!, result, index)
            bufferNode = final
            return final
        }

    override fun matchContext(context: List<String>): Boolean {
        if (buffer == null) return matchNodeContext(node.parent, context)
        val buf = buffer!!
        val types = buf.buffer.set.types
        var i = context.size - 1
        var d = stack.size - 1
        while (i >= 0) {
            if (d < 0) return matchNodeContext(_tree, context, i)
            val nodeType = types[buf.buffer.buffer[stack[d]]]
            if (!nodeType.isAnonymous) {
                if (context[i].isNotEmpty() && context[i] != nodeType.name) {
                    return false
                }
                i--
            }
            d--
        }
        return true
    }

    private fun enterChild(dir: Int, pos: Int, side: Int): Boolean {
        if (buffer == null) {
            return yieldResult(
                _tree.nextChild(
                    if (dir < 0) _tree._tree.children.size - 1 else 0,
                    dir,
                    pos,
                    side,
                    mode
                )
            )
        }
        val buf = buffer!!
        val bufIdx = buf.buffer.findChild(
            index + 4,
            buf.buffer.buffer[index + 3],
            dir,
            pos - buf.start,
            side
        )
        if (bufIdx < 0) return false
        stack.add(index)
        return yieldBuf(bufIdx)
    }

    /** Move to the first child. Returns true if successful. */
    fun firstChild(): Boolean = enterChild(1, 0, Side.DontCare)

    /** Move to the last child. */
    fun lastChild(): Boolean = enterChild(-1, 0, Side.DontCare)

    /** Move to the first child that ends after [pos]. */
    fun childAfter(pos: Int): Boolean = enterChild(1, pos, Side.After)

    /** Move to the last child that starts before [pos]. */
    fun childBefore(pos: Int): Boolean = enterChild(-1, pos, Side.Before)

    /** Enter child at position. */
    fun enter(pos: Int, side: Int, enterMode: Int = mode): Boolean {
        if (buffer == null) {
            return yieldResult(_tree.enter(pos, side, enterMode))
        }
        return if (enterMode and IterMode.ExcludeBuffers != 0) {
            false
        } else {
            enterChild(1, pos, side)
        }
    }

    private fun sibling(dir: Int): Boolean {
        if (buffer == null) {
            if (_tree._parent == null) return false
            return if (_tree.index < 0) {
                false
            } else {
                yieldResult(
                    _tree._parent!!.nextChild(
                        _tree.index + dir,
                        dir,
                        0,
                        Side.DontCare,
                        mode
                    )
                )
            }
        }
        val buf = buffer!!
        val d = stack.size - 1
        if (dir < 0) {
            val parentStart = if (d < 0) 0 else stack[d] + 4
            if (index != parentStart) {
                return yieldBuf(
                    buf.buffer.findChild(
                        parentStart,
                        index,
                        -1,
                        0,
                        Side.DontCare
                    )
                )
            }
        } else {
            val after = buf.buffer.buffer[index + 3]
            val limit = if (d < 0) {
                buf.buffer.buffer.size
            } else {
                buf.buffer.buffer[stack[d] + 3]
            }
            if (after < limit) return yieldBuf(after)
        }
        return if (d < 0) {
            yieldResult(
                buf.parent.nextChild(
                    buf.index + dir,
                    dir,
                    0,
                    Side.DontCare,
                    mode
                )
            )
        } else {
            false
        }
    }

    /** Move to the next sibling. Returns true if successful. */
    fun nextSibling(): Boolean = sibling(1)

    /** Move to the previous sibling. */
    fun prevSibling(): Boolean = sibling(-1)

    /** Move to the parent node. Returns true if successful. */
    fun parent(): Boolean {
        if (buffer == null) {
            return yieldNode(
                if (mode and IterMode.IncludeAnonymous != 0) {
                    _tree._parent
                } else {
                    _tree.parent as? TreeNode
                }
            )
        }
        if (stack.isNotEmpty()) return yieldBuf(stack.removeLast())
        val parentNode = if (mode and IterMode.IncludeAnonymous != 0) {
            buffer!!.parent
        } else {
            buffer!!.parent.nextSignificantParent()
        }
        buffer = null
        return yieldNode(parentNode)
    }

    private fun atLastNode(dir: Int): Boolean {
        var idx: Int
        var parentNode: TreeNode?
        val buf = buffer
        if (buf != null) {
            if (dir > 0) {
                if (index < buf.buffer.buffer.size) return false
            } else {
                for (i in 0 until index) {
                    if (buf.buffer.buffer[i + 3] < index) return false
                }
            }
            idx = buf.index
            parentNode = buf.parent
        } else {
            idx = _tree.index
            parentNode = _tree._parent
        }
        while (parentNode != null) {
            if (idx > -1) {
                var i = idx + dir
                val e = if (dir < 0) -1 else parentNode._tree.children.size
                while (i != e) {
                    val child = parentNode!!._tree.children[i]
                    if (mode and IterMode.IncludeAnonymous != 0 ||
                        child is TreeBuffer ||
                        (child is Tree && !child.type.isAnonymous) ||
                        (child is Tree && hasChild(child))
                    ) {
                        return false
                    }
                    i += dir
                }
            }
            idx = parentNode.index
            parentNode = parentNode._parent
        }
        return true
    }

    private fun move(dir: Int, enter: Boolean): Boolean {
        if (enter && enterChild(dir, 0, Side.DontCare)) return true
        while (true) {
            if (sibling(dir)) return true
            if (atLastNode(dir) || !parent()) return false
        }
    }

    /** Move depth-first to the next node. */
    fun next(enter: Boolean = true): Boolean = move(1, enter)

    /** Move depth-first backward. */
    fun prev(enter: Boolean = true): Boolean = move(-1, enter)

    /**
     * Move to the given position, finding the deepest node at that
     * point.
     */
    fun moveTo(pos: Int, side: Int = 0): TreeCursor {
        while (from == to ||
            (if (side < 1) from >= pos else from > pos) ||
            (if (side > -1) to <= pos else to < pos)
        ) {
            if (!parent()) break
        }
        while (enterChild(1, pos, side)) { /* descend */ }
        return this
    }
}

// ---- Helper functions ----

private fun matchNodeContext(
    node: SyntaxNode?,
    context: List<String>,
    startIndex: Int = context.size - 1
): Boolean {
    var p = node
    var i = startIndex
    while (i >= 0) {
        if (p == null) return false
        if (!p.type.isAnonymous) {
            if (context[i].isNotEmpty() && context[i] != p.name) return false
            i--
        }
        p = p.parent
    }
    return true
}

private fun hasChild(tree: Tree): Boolean {
    return tree.children.any { ch ->
        ch is TreeBuffer ||
            (ch is Tree && (!ch.type.isAnonymous || hasChild(ch)))
    }
}

private fun appendTreeString(tree: Tree, sb: StringBuilder) {
    if (tree.type != NodeType.none) {
        sb.append(tree.type.name)
    }
    val allVisibleChildren = mutableListOf<Pair<Int, Any>>()
    collectVisibleChildren(tree, 0, allVisibleChildren)
    if (allVisibleChildren.isNotEmpty()) {
        if (tree.type != NodeType.none) sb.append("(")
        var first = true
        for ((_, child) in allVisibleChildren) {
            if (!first) sb.append(",")
            first = false
            when (child) {
                is Tree -> appendTreeString(child, sb)
                is TreeBuffer -> sb.append(child.toString())
            }
        }
        if (tree.type != NodeType.none) sb.append(")")
    }
}

private fun collectVisibleChildren(tree: Tree, basePos: Int, result: MutableList<Pair<Int, Any>>) {
    for (i in tree.children.indices) {
        val child = tree.children[i]
        val childPos = basePos + tree.positions[i]
        when (child) {
            is Tree -> {
                if (child.type == NodeType.none) {
                    collectVisibleChildren(child, childPos, result)
                } else {
                    result.add(childPos to child)
                }
            }
            is TreeBuffer -> result.add(childPos to child)
        }
    }
}

// ---- resolveNode helper (handles overlay-mounted trees) ----

private fun resolveNode(tree: Tree, pos: Int, side: Int, overlays: Boolean): SyntaxNode {
    var node: SyntaxNode = tree.topNode
    while (true) {
        val inner = node.enter(pos, side) ?: break
        if (inner === node) break
        node = inner
    }
    return node
}

// ---- stackIterator helper (resolveStack) ----

private fun stackIterator(tree: Tree, pos: Int, side: Int): NodeIterator {
    val cursor = tree.cursorAt(pos, side)
    // Build list from innermost to outermost
    val nodes = mutableListOf<SyntaxNode>()
    nodes.add(cursor.node)
    while (cursor.parent()) {
        nodes.add(cursor.node)
    }
    // Build linked list: innermost first, outermost last (via .next)
    // nodes[0] = innermost, nodes[last] = outermost
    var result: NodeIterator? = null
    for (i in nodes.indices.reversed()) {
        result = NodeIterator(nodes[i], result)
    }
    return result ?: NodeIterator(tree.topNode, null)
}

// ---- enterUnfinishedNodesBefore helper ----

private fun enterUnfinishedNodesBefore(node: SyntaxNode, pos: Int): SyntaxNode {
    var scan: SyntaxNode = node
    val cursor = scan.childBefore(pos)
    while (cursor != null) {
        val last = cursor.lastChild
        if (last == null || last.to != cursor.to) break
        if (last.type.isError && last.from == last.to) {
            scan = cursor
            break
        }
        scan = cursor
        break
    }
    return scan
}

// ---- Tree.build implementation ----

private fun buildTree(spec: TreeBuildSpec): Tree {
    val nodeSet = spec.nodeSet
    val maxBufferLength = spec.maxBufferLength
    val reused = spec.reused
    val minRepeatType = if (spec.minRepeatType == -1) {
        nodeSet.types.size
    } else {
        spec.minRepeatType
    }

    val cursor: BufferCursor = when (val buf = spec.buffer) {
        is List<*> -> {
            @Suppress("UNCHECKED_CAST")
            FlatBufferCursor(buf as List<Int>, buf.size)
        }
        is BufferCursor -> buf
        else -> error("Buffer must be List<Int> or BufferCursor")
    }

    val types = nodeSet.types
    var contextHash = 0
    var lookAhead = 0

    fun makeTree(
        type: NodeType,
        children: List<Any>,
        positions: List<Int>,
        length: Int,
        lookAheadVal: Int,
        contextHashVal: Int
    ): Tree {
        val props = mutableMapOf<Int, Any?>()
        if (contextHashVal != 0) {
            props[NodeProp.contextHash.id] = contextHashVal
        }
        if (lookAheadVal > 25) {
            props[NodeProp.lookAhead.id] = lookAheadVal
        }
        return Tree(type, children, positions, length, props)
    }

    fun copyToBuffer(bufferStart: Int, buffer: IntArray, indexIn: Int): Int {
        var idx = indexIn
        val id = cursor.id
        val start = cursor.start
        val end = cursor.end
        val size = cursor.size
        cursor.next()
        if (size >= 0 && id < minRepeatType) {
            val startIndex = idx
            if (size > 4) {
                val endPos = cursor.pos - (size - 4)
                while (cursor.pos > endPos) {
                    idx = copyToBuffer(bufferStart, buffer, idx)
                }
            }
            buffer[--idx] = startIndex
            buffer[--idx] = end - bufferStart
            buffer[--idx] = start - bufferStart
            buffer[--idx] = id
        } else if (size == SpecialRecord.ContextChange) {
            contextHash = id
        } else if (size == SpecialRecord.LookAhead) {
            lookAhead = id
        }
        return idx
    }

    fun findBufferSize(maxSize: Int, inRepeat: Int): BufferSizeResult? {
        val fork = cursor.fork()
        var size = 0
        var start = 0
        var skip = 0
        val minStart = fork.end - maxBufferLength
        val result = BufferSizeResult(0, 0, 0)
        val minPos = fork.pos - maxSize
        while (fork.pos > minPos) {
            val nodeSize = fork.size
            if (fork.id == inRepeat && nodeSize >= 0) {
                result.size = size
                result.start = start
                result.skip = skip
                skip += 4
                size += 4
                fork.next()
                continue
            }
            val startPos = fork.pos - nodeSize
            if (nodeSize < 0 || startPos < minPos || fork.start < minStart) break
            var localSkipped = if (fork.id >= minRepeatType) 4 else 0
            val nodeStart = fork.start
            fork.next()
            while (fork.pos > startPos) {
                if (fork.size < 0) {
                    if (fork.size == SpecialRecord.ContextChange ||
                        fork.size == SpecialRecord.LookAhead
                    ) {
                        localSkipped += 4
                    } else {
                        // Break out of both loops
                        // Set flag and break
                        size = -1
                        break
                    }
                } else if (fork.id >= minRepeatType) {
                    localSkipped += 4
                }
                fork.next()
            }
            if (size == -1) break
            start = nodeStart
            size += nodeSize
            skip += localSkipped
        }
        if (inRepeat < 0 || size == maxSize) {
            result.size = size
            result.start = start
            result.skip = skip
        }
        return if (result.size > 4) result else null
    }

    fun makeBalanced(type: NodeType, ctxHash: Int): (List<Any>, List<Int>, Int) -> Tree {
        return { children, positions, length ->
            var la = 0
            val lastI = children.size - 1
            if (lastI >= 0) {
                val last = children[lastI]
                if (last is Tree) {
                    if (lastI == 0 && last.type == type && last.length == length) {
                        last
                    } else {
                        val laProp = last.prop(NodeProp.lookAhead)
                        if (laProp != null) {
                            la = positions[lastI] + last.length + laProp
                        }
                        makeTree(type, children, positions, length, la, ctxHash)
                    }
                } else {
                    makeTree(type, children, positions, length, la, ctxHash)
                }
            } else {
                makeTree(type, children, positions, length, la, ctxHash)
            }
        }
    }

    fun makeRepeatLeaf(
        children: MutableList<Any>,
        positions: MutableList<Int>,
        base: Int,
        i: Int,
        from: Int,
        to: Int,
        typeId: Int,
        la: Int,
        ctxHash: Int
    ) {
        val localChildren = mutableListOf<Any>()
        val localPositions = mutableListOf<Int>()
        while (children.size > i) {
            localChildren.add(children.removeLast())
            localPositions.add(positions.removeLast() + base - from)
        }
        children.add(
            makeTree(
                nodeSet.types[typeId],
                localChildren,
                localPositions,
                to - from,
                la - to,
                ctxHash
            )
        )
        positions.add(from - base)
    }

    fun takeNode(
        parentStart: Int,
        minPos: Int,
        children: MutableList<Any>,
        positions: MutableList<Int>,
        inRepeat: Int,
        depth: Int
    ) {
        val id = cursor.id
        val start = cursor.start
        val end = cursor.end
        val size = cursor.size
        val lookAheadAtStart = lookAhead
        val contextAtStart = contextHash
        if (size < 0) {
            cursor.next()
            when (size) {
                SpecialRecord.Reuse -> {
                    children.add(reused[id])
                    positions.add(start - parentStart)
                    return
                }
                SpecialRecord.ContextChange -> {
                    contextHash = id
                    return
                }
                SpecialRecord.LookAhead -> {
                    lookAhead = id
                    return
                }
                else -> error("Unrecognized record size: $size")
            }
        }

        val type = types[id]
        val node: Any
        var startPos = start - parentStart

        val bufResult = if (end - start <= maxBufferLength) {
            findBufferSize(cursor.pos - minPos, inRepeat)
        } else {
            null
        }

        if (bufResult != null) {
            val data = IntArray(bufResult.size - bufResult.skip)
            val endPos = cursor.pos - bufResult.size
            var bufIdx = data.size
            while (cursor.pos > endPos) {
                bufIdx = copyToBuffer(bufResult.start, data, bufIdx)
            }
            node = TreeBuffer(data, end - bufResult.start, nodeSet)
            startPos = bufResult.start - parentStart
        } else {
            val endPos = cursor.pos - size
            cursor.next()
            val localChildren = mutableListOf<Any>()
            val localPositions = mutableListOf<Int>()
            val localInRepeat = if (id >= minRepeatType) id else -1
            var lastGroup = 0
            var lastEnd = end
            while (cursor.pos > endPos) {
                if (localInRepeat >= 0 && cursor.id == localInRepeat &&
                    cursor.size >= 0
                ) {
                    if (cursor.end <= lastEnd - maxBufferLength) {
                        makeRepeatLeaf(
                            localChildren, localPositions, start,
                            lastGroup, cursor.end, lastEnd,
                            localInRepeat, lookAheadAtStart, contextAtStart
                        )
                        lastGroup = localChildren.size
                        lastEnd = cursor.end
                    }
                    cursor.next()
                } else {
                    takeNode(
                        start,
                        endPos,
                        localChildren,
                        localPositions,
                        localInRepeat,
                        depth + 1
                    )
                }
            }
            if (localInRepeat >= 0 && lastGroup > 0 &&
                lastGroup < localChildren.size
            ) {
                makeRepeatLeaf(
                    localChildren, localPositions, start,
                    lastGroup, start, lastEnd,
                    localInRepeat, lookAheadAtStart, contextAtStart
                )
            }
            localChildren.reverse()
            localPositions.reverse()

            if (localInRepeat > -1 && lastGroup > 0) {
                val make = makeBalanced(type, contextAtStart)
                node = balanceRange(
                    type, localChildren, localPositions,
                    0, localChildren.size, 0, end - start, make, make
                )
            } else {
                node = makeTree(
                    type, localChildren, localPositions,
                    end - start, lookAheadAtStart - end, contextAtStart
                )
            }
        }

        children.add(node)
        positions.add(startPos)
    }

    val children = mutableListOf<Any>()
    val positions = mutableListOf<Int>()
    while (cursor.pos > 0) {
        takeNode(
            spec.start,
            spec.bufferStart,
            children,
            positions,
            -1,
            0
        )
    }
    val length = spec.length ?: if (children.isNotEmpty()) {
        positions[0] + childLength(children[0])
    } else {
        0
    }
    return Tree(
        types[spec.topID],
        children.reversed(),
        positions.reversed(),
        length
    )
}

private class BufferSizeResult(
    var size: Int,
    var start: Int,
    var skip: Int
)

private fun nodeSize(balanceType: NodeType, node: Any): Int {
    if (!balanceType.isAnonymous || node is TreeBuffer) return 1
    if (node !is Tree || node.type != balanceType) return 1
    var size = 1
    for (child in node.children) {
        if (child !is Tree || child.type != balanceType) {
            return 1
        }
        size += nodeSize(balanceType, child)
    }
    return size
}

private fun balanceRange(
    balanceType: NodeType,
    children: List<Any>,
    positions: List<Int>,
    from: Int,
    to: Int,
    start: Int,
    length: Int,
    mkTop: ((List<Any>, List<Int>, Int) -> Tree)?,
    mkTree: (List<Any>, List<Int>, Int) -> Tree
): Tree {
    var total = 0
    for (i in from until to) total += nodeSize(balanceType, children[i])

    val maxChild = ((total * 1.5) / BALANCE_BRANCH_FACTOR).toInt() + 1
    val localChildren = mutableListOf<Any>()
    val localPositions = mutableListOf<Int>()

    fun divide(ch: List<Any>, pos: List<Int>, divFrom: Int, divTo: Int, offset: Int) {
        var i = divFrom
        while (i < divTo) {
            val groupFrom = i
            val groupStart = pos[i]
            var groupSize = nodeSize(balanceType, ch[i])
            i++
            while (i < divTo) {
                val nextSize = nodeSize(balanceType, ch[i])
                if (groupSize + nextSize >= maxChild) break
                groupSize += nextSize
                i++
            }
            if (i == groupFrom + 1) {
                if (groupSize > maxChild) {
                    val only = ch[groupFrom] as Tree
                    divide(
                        only.children,
                        only.positions,
                        0,
                        only.children.size,
                        pos[groupFrom] + offset
                    )
                    continue
                }
                localChildren.add(ch[groupFrom])
            } else {
                val len = pos[i - 1] + childLength(ch[i - 1]) - groupStart
                localChildren.add(
                    balanceRange(
                        balanceType, ch, pos, groupFrom, i,
                        groupStart, len, null, mkTree
                    )
                )
            }
            localPositions.add(groupStart + offset - start)
        }
    }
    divide(children, positions, from, to, 0)
    return (mkTop ?: mkTree)(localChildren, localPositions, length)
}
