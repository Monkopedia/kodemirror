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
    val offset: Int
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
            changes: List<ChangedRange>
        ): List<TreeFragment> {
            if (changes.isEmpty()) return fragments
            val result = mutableListOf<TreeFragment>()
            var pos = 0
            var off = 0
            for (frag in fragments) {
                var from = frag.from + off
                var to = frag.to + off
                var valid = true
                for (change in changes) {
                    if (change.toB <= from || change.fromB >= to) continue
                    valid = false
                    break
                }
                if (valid && from < to) {
                    result.add(TreeFragment(from, to, frag.tree, frag.offset + off))
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

    /** Create a cursor from this node. */
    fun cursor(): TreeCursor
}

/**
 * Build specification for tree construction.
 */
data class TreeBuildSpec(
    val buffer: List<Int>,
    val nodeSet: NodeSet,
    val topID: Int,
    val maxBufferLength: Int = 1024,
    val minRepeatType: Int = -1
)

/**
 * A syntax tree. Immutable, constructed by parsers.
 */
class Tree(
    val type: NodeType,
    val children: List<Tree>,
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

    /** Iterate over all nodes in the tree within a range. */
    fun iterate(spec: IterateSpec) {
        val from = spec.from ?: 0
        val to = spec.to ?: this.length
        iterateInner(this, 0, from, to, spec.enter, spec.leave, spec.mode ?: 0)
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
         * Build a tree from a flat buffer representation.
         * The buffer encodes groups of 4 ints: type, start, end, size.
         */
        fun build(spec: TreeBuildSpec): Tree {
            return buildTree(spec)
        }
    }
}

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

// ---- TreeNode: wraps Tree + child trees as a SyntaxNode ----

private class TreeNode(
    private val _tree: Tree,
    val treeFrom: Int,
    override val parent: TreeNode?,
    private val index: Int
) : SyntaxNode {
    override val from: Int get() = treeFrom
    override val to: Int get() = treeFrom + _tree.length
    override val type: NodeType get() = _tree.type
    override val tree: Tree get() = _tree

    override fun matchContext(context: List<String>): Boolean {
        return matchContextInner(this, context)
    }

    override fun getChild(type: String, before: String?, after: String?): SyntaxNode? {
        return getChildren(type, before, after).firstOrNull()
    }

    override fun getChildren(type: String, before: String?, after: String?): List<SyntaxNode> {
        val result = mutableListOf<SyntaxNode>()
        var passedBefore = before == null
        for (i in _tree.children.indices) {
            val child = _tree.children[i]
            val childFrom = treeFrom + _tree.positions[i]
            val childNode = TreeNode(child, childFrom, this, i)
            if (!passedBefore) {
                if (childNode.type.`is`(before!!)) passedBefore = true
                continue
            }
            if (after != null && childNode.type.`is`(after)) break
            if (childNode.type.`is`(type)) result.add(childNode)
        }
        return result
    }

    override val firstChild: SyntaxNode?
        get() = findChild(true)

    override val lastChild: SyntaxNode?
        get() = findChild(false)

    override val nextSibling: SyntaxNode?
        get() = findSibling(true)

    override val prevSibling: SyntaxNode?
        get() = findSibling(false)

    private fun findChild(forward: Boolean): SyntaxNode? {
        val range = if (forward) _tree.children.indices else _tree.children.indices.reversed()
        for (i in range) {
            val child = _tree.children[i]
            val childFrom = treeFrom + _tree.positions[i]
            if (child.type != NodeType.none) {
                return TreeNode(child, childFrom, this, i)
            }
            val inner = TreeNode(child, childFrom, this, i)
            val found = if (forward) inner.firstChild else inner.lastChild
            if (found != null) return found
        }
        return null
    }

    private fun findSibling(forward: Boolean): SyntaxNode? {
        val p = parent ?: return null
        val start = this.index + (if (forward) 1 else -1)
        val range = if (forward) {
            start until p._tree.children.size
        } else {
            start downTo 0
        }
        for (i in range) {
            if (i < 0 || i >= p._tree.children.size) return null
            val child = p._tree.children[i]
            val childFrom = p.treeFrom + p._tree.positions[i]
            if (child.type != NodeType.none) {
                return TreeNode(child, childFrom, p, i)
            }
            val inner = TreeNode(child, childFrom, p, i)
            val found = if (forward) inner.firstChild else inner.lastChild
            if (found != null) return found
        }
        return null
    }

    override fun enter(pos: Int, side: Int, mode: Int): SyntaxNode? {
        for (i in _tree.children.indices) {
            val child = _tree.children[i]
            val childFrom = treeFrom + _tree.positions[i]
            val childTo = childFrom + child.length
            if (childFrom > pos || childTo < pos) continue
            if (childFrom == pos && side < 0) continue
            if (childTo == pos && side > 0) continue
            if (child.type == NodeType.none ||
                (mode and IterMode.IncludeAnonymous) != 0
            ) {
                val inner = TreeNode(child, childFrom, this, i)
                    .enter(pos, side, mode)
                if (inner != null) return inner
            }
            if (child.type != NodeType.none) {
                return TreeNode(child, childFrom, this, i)
            }
        }
        return null
    }

    override fun childAfter(pos: Int): SyntaxNode? {
        for (i in _tree.children.indices) {
            val child = _tree.children[i]
            val childFrom = treeFrom + _tree.positions[i]
            if (childFrom >= pos && child.type != NodeType.none) {
                return TreeNode(child, childFrom, this, i)
            }
            if (child.type == NodeType.none) {
                val inner = TreeNode(child, childFrom, this, i)
                val found = inner.childAfter(pos)
                if (found != null) return found
            }
        }
        return null
    }

    override fun childBefore(pos: Int): SyntaxNode? {
        for (i in _tree.children.indices.reversed()) {
            val child = _tree.children[i]
            val childFrom = treeFrom + _tree.positions[i]
            val childTo = childFrom + child.length
            if (childTo <= pos && child.type != NodeType.none) {
                return TreeNode(child, childFrom, this, i)
            }
            if (child.type == NodeType.none) {
                val inner = TreeNode(child, childFrom, this, i)
                val found = inner.childBefore(pos)
                if (found != null) return found
            }
        }
        return null
    }

    override fun resolve(pos: Int, side: Int): SyntaxNode {
        val entered = enter(pos, side)
        if (entered != null) {
            return entered.resolve(pos, side)
        }
        return this
    }

    override fun cursor(): TreeCursor = _tree.cursor()

    override fun toTree(): Tree = _tree

    override fun toString(): String = _tree.toString()
}

// ---- TreeCursor ----

/**
 * Mutable tree walker. Walks a [Tree] depth-first.
 */
class TreeCursor internal constructor(
    private val root: Tree,
    private val mode: Int = 0
) : SyntaxNodeRef {
    // Stack of (tree, position-in-parent-children, treeFrom)
    private data class Frame(
        val tree: Tree,
        val childIndex: Int,
        val treeFrom: Int
    )

    private val stack = mutableListOf<Frame>()
    private var currentTree: Tree = root
    private var currentFrom: Int = 0

    override val type: NodeType get() = currentTree.type
    override val from: Int get() = currentFrom
    override val to: Int get() = currentFrom + currentTree.length
    override val name: String get() = type.name

    /** The Tree object backing the current node, if this is a tree node. */
    val tree: Tree? get() = currentTree

    /** Get the current position as a SyntaxNode. */
    val node: SyntaxNode
        get() {
            var parentNode: TreeNode? = null
            for (frame in stack) {
                parentNode = TreeNode(frame.tree, frame.treeFrom, parentNode, frame.childIndex)
            }
            return TreeNode(currentTree, currentFrom, parentNode, 0)
        }

    override fun matchContext(context: List<String>): Boolean {
        return matchContextInner(this, context)
    }

    /**
     * Move to the given position, finding the deepest node at that point.
     */
    fun moveTo(pos: Int, side: Int = 0): TreeCursor {
        // Reset to root
        stack.clear()
        currentTree = root
        currentFrom = 0
        // Now descend
        moveInto(pos, side)
        return this
    }

    private fun moveInto(pos: Int, side: Int) {
        while (true) {
            var found = false
            val skipAnon = (mode and IterMode.IncludeAnonymous) == 0
            for (i in currentTree.children.indices) {
                val child = currentTree.children[i]
                val childFrom = currentFrom + currentTree.positions[i]
                val childTo = childFrom + child.length
                // Check if pos is strictly within this node (or on matching side)
                val enter = when {
                    childFrom == childTo -> childFrom == pos
                    side > 0 -> childFrom <= pos && childTo > pos
                    side < 0 -> childFrom < pos && childTo >= pos
                    else -> childFrom < pos && childTo > pos // side == 0: strictly inside
                }
                if (!enter) continue

                if (skipAnon && child.type == NodeType.none) {
                    stack.add(Frame(currentTree, i, currentFrom))
                    currentTree = child
                    currentFrom = childFrom
                    found = true
                    break
                }
                if (child.type != NodeType.none || !skipAnon) {
                    stack.add(Frame(currentTree, i, currentFrom))
                    currentTree = child
                    currentFrom = childFrom
                    found = true
                    break
                }
            }
            if (!found) {
                // If we stopped at an anonymous node in skip mode, pop back up
                if (skipAnon && currentTree.type == NodeType.none && stack.isNotEmpty()) {
                    val frame = stack.removeLast()
                    currentTree = frame.tree
                    currentFrom = frame.treeFrom
                }
                break
            }
        }
    }

    /** Move to the first child of the current node. Returns true if successful. */
    fun firstChild(): Boolean {
        val skipAnon = (mode and IterMode.IncludeAnonymous) == 0
        for (i in currentTree.children.indices) {
            val child = currentTree.children[i]
            val childFrom = currentFrom + currentTree.positions[i]
            if (skipAnon && child.type == NodeType.none) {
                // Enter anonymous nodes transparently
                stack.add(Frame(currentTree, i, currentFrom))
                currentTree = child
                currentFrom = childFrom
                return firstChild() || run {
                    parent()
                    false
                }
            }
            if (!skipAnon || child.type != NodeType.none) {
                stack.add(Frame(currentTree, i, currentFrom))
                currentTree = child
                currentFrom = childFrom
                return true
            }
        }
        return false
    }

    /** Move to the parent of the current node. Returns true if successful. */
    fun parent(): Boolean {
        if (stack.isEmpty()) return false
        val frame = stack.removeLast()
        currentTree = frame.tree
        currentFrom = frame.treeFrom
        // Skip anonymous parents when not in IncludeAnonymous mode
        val skipAnon = (mode and IterMode.IncludeAnonymous) == 0
        if (skipAnon && currentTree.type == NodeType.none && stack.isNotEmpty()) {
            return parent()
        }
        return true
    }

    /** Move to the next sibling. Returns true if successful. */
    fun nextSibling(): Boolean {
        if (stack.isEmpty()) return false
        val frame = stack.last()
        val skipAnon = (mode and IterMode.IncludeAnonymous) == 0
        for (i in (frame.childIndex + 1) until frame.tree.children.size) {
            val child = frame.tree.children[i]
            val childFrom = frame.treeFrom + frame.tree.positions[i]
            if (skipAnon && child.type == NodeType.none) {
                // Look inside anonymous nodes
                stack[stack.lastIndex] = frame.copy(childIndex = i)
                stack.add(Frame(frame.tree, i, frame.treeFrom))
                currentTree = child
                currentFrom = childFrom
                if (firstChild()) return true
                stack.removeLast()
                continue
            }
            if (!skipAnon || child.type != NodeType.none) {
                stack[stack.lastIndex] = frame.copy(childIndex = i)
                currentTree = child
                currentFrom = childFrom
                return true
            }
        }
        // If parent is anonymous, try its next sibling
        if (skipAnon && frame.tree.type == NodeType.none) {
            stack.removeLast()
            currentTree = frame.tree
            currentFrom = frame.treeFrom
            return nextSibling()
        }
        return false
    }

    /** Move to the previous sibling. Returns true if successful. */
    fun prevSibling(): Boolean {
        if (stack.isEmpty()) return false
        val frame = stack.last()
        val skipAnon = (mode and IterMode.IncludeAnonymous) == 0
        for (i in (frame.childIndex - 1) downTo 0) {
            val child = frame.tree.children[i]
            val childFrom = frame.treeFrom + frame.tree.positions[i]
            if (skipAnon && child.type == NodeType.none) {
                continue
            }
            if (!skipAnon || child.type != NodeType.none) {
                stack[stack.lastIndex] = frame.copy(childIndex = i)
                currentTree = child
                currentFrom = childFrom
                return true
            }
        }
        return false
    }

    /** Move depth-first to the next node. Returns true if there are more. */
    fun next(enter: Boolean = true): Boolean {
        if (enter && firstChild()) return true
        if (nextSibling()) return true
        // Move up and try siblings
        while (parent()) {
            if (nextSibling()) return true
        }
        return false
    }

    /** Move depth-first backward. Returns true if there are more. */
    fun prev(enter: Boolean = true): Boolean {
        if (enter && lastChild()) return true
        if (prevSibling()) return true
        while (parent()) {
            if (prevSibling()) return true
        }
        return false
    }

    private fun lastChild(): Boolean {
        val skipAnon = (mode and IterMode.IncludeAnonymous) == 0
        for (i in currentTree.children.indices.reversed()) {
            val child = currentTree.children[i]
            val childFrom = currentFrom + currentTree.positions[i]
            if (skipAnon && child.type == NodeType.none) continue
            if (!skipAnon || child.type != NodeType.none) {
                stack.add(Frame(currentTree, i, currentFrom))
                currentTree = child
                currentFrom = childFrom
                return true
            }
        }
        return false
    }

    /** Move to the first child that starts at or after [pos]. */
    fun childAfter(pos: Int): Boolean {
        val skipAnon = (mode and IterMode.IncludeAnonymous) == 0
        for (i in currentTree.children.indices) {
            val child = currentTree.children[i]
            val childFrom = currentFrom + currentTree.positions[i]
            val childTo = childFrom + child.length
            if (childTo <= pos) continue
            if (skipAnon && child.type == NodeType.none) continue
            stack.add(Frame(currentTree, i, currentFrom))
            currentTree = child
            currentFrom = childFrom
            return true
        }
        return false
    }

    /** Move to the last child that ends at or before [pos]. */
    fun childBefore(pos: Int): Boolean {
        val skipAnon = (mode and IterMode.IncludeAnonymous) == 0
        for (i in currentTree.children.indices.reversed()) {
            val child = currentTree.children[i]
            val childFrom = currentFrom + currentTree.positions[i]
            if (childFrom >= pos) continue
            if (skipAnon && child.type == NodeType.none) continue
            stack.add(Frame(currentTree, i, currentFrom))
            currentTree = child
            currentFrom = childFrom
            return true
        }
        return false
    }

    /**
     * Move cursor into child nodes (for entering from a position).
     */
    fun enter(pos: Int, side: Int, mode: Int = 0): Boolean {
        for (i in currentTree.children.indices) {
            val child = currentTree.children[i]
            val childFrom = currentFrom + currentTree.positions[i]
            val childTo = childFrom + child.length
            if (childFrom > pos || childTo < pos) continue
            if (childFrom == pos && side < 0) continue
            if (childTo == pos && side > 0) continue
            stack.add(Frame(currentTree, i, currentFrom))
            currentTree = child
            currentFrom = childFrom
            return true
        }
        return false
    }
}

// ---- Helper functions ----

private fun matchContextInner(node: SyntaxNodeRef, context: List<String>): Boolean {
    // context is innermost-first (reverse parent chain)
    // We need to check that the parents match
    if (node is TreeNode) {
        var current: SyntaxNode? = node.parent
        for (i in context.indices.reversed()) {
            if (current == null) return false
            val expected = context[i]
            if (expected.isNotEmpty() && !current.type.`is`(expected)) return false
            current = current.parent
        }
        return true
    }
    if (node is TreeCursor) {
        // For cursors, we check against the stack
        return matchCursorContext(node, context)
    }
    return false
}

private fun matchCursorContext(cursor: TreeCursor, context: List<String>): Boolean {
    // Build parent chain by walking up
    val node = cursor.node
    var current: SyntaxNode? = node.parent
    for (i in context.indices.reversed()) {
        if (current == null) return false
        val expected = context[i]
        if (expected.isNotEmpty() && !current.type.`is`(expected)) return false
        current = current.parent
    }
    return true
}

private fun appendTreeString(tree: Tree, sb: StringBuilder) {
    if (tree.type != NodeType.none) {
        sb.append(tree.type.name)
    }
    val namedChildren = tree.children.filter { it.type != NodeType.none }
    // Also consider children inside anonymous wrappers
    val allVisibleChildren = mutableListOf<Pair<Int, Tree>>()
    collectVisibleChildren(tree, 0, allVisibleChildren)
    if (allVisibleChildren.isNotEmpty()) {
        if (tree.type != NodeType.none) sb.append("(")
        var first = true
        for ((pos, child) in allVisibleChildren) {
            if (!first) sb.append(",")
            first = false
            appendTreeString(child, sb)
        }
        if (tree.type != NodeType.none) sb.append(")")
    }
}

private fun collectVisibleChildren(tree: Tree, basePos: Int, result: MutableList<Pair<Int, Tree>>) {
    for (i in tree.children.indices) {
        val child = tree.children[i]
        val childPos = basePos + tree.positions[i]
        if (child.type == NodeType.none) {
            collectVisibleChildren(child, childPos, result)
        } else {
            result.add(childPos to child)
        }
    }
}

private fun iterateInner(
    tree: Tree,
    treeFrom: Int,
    from: Int,
    to: Int,
    enter: (SyntaxNodeRef) -> Boolean?,
    leave: ((SyntaxNodeRef) -> Unit)?,
    mode: Int
) {
    if (tree.length == 0 && tree.children.isEmpty() && tree.type == NodeType.none) return
    val nodeFrom = treeFrom
    val nodeTo = treeFrom + tree.length
    if (nodeFrom > to || nodeTo < from) return

    val skipAnon = (mode and IterMode.IncludeAnonymous) == 0

    val ref = object : SyntaxNodeRef {
        override val from: Int get() = nodeFrom
        override val to: Int get() = nodeTo
        override val type: NodeType get() = tree.type
        override fun matchContext(context: List<String>): Boolean = false
    }

    val shouldEnter = if (skipAnon && tree.type == NodeType.none) {
        true // Always enter anonymous nodes
    } else {
        val result = enter(ref)
        result != false
    }

    if (shouldEnter) {
        for (i in tree.children.indices) {
            val child = tree.children[i]
            val childFrom = treeFrom + tree.positions[i]
            if (childFrom > to) break
            if (childFrom + child.length < from) continue
            iterateInner(child, childFrom, from, to, enter, leave, mode)
        }
    }

    if (!(skipAnon && tree.type == NodeType.none)) {
        leave?.invoke(ref)
    }
}

// ---- Tree.build implementation ----

/**
 * Build a tree from the flat buffer format used by Lezer.
 * Buffer format: groups of 4 ints (type, from, to, size).
 * Size is the number of ints (including the group itself) from the start
 * of the first child to the end of this group.
 */
private fun buildTree(spec: TreeBuildSpec): Tree {
    val buffer = spec.buffer
    val nodeSet = spec.nodeSet
    val topType = nodeSet.types.first { it.id == spec.topID }

    fun buildNodes(start: Int, end: Int, parentFrom: Int): List<Pair<Int, Tree>> {
        val result = mutableListOf<Pair<Int, Tree>>()
        var pos = end
        while (pos > start) {
            val typeId = buffer[pos - 4]
            val nodeFrom = buffer[pos - 3]
            val nodeTo = buffer[pos - 2]
            val size = buffer[pos - 1]

            val type = nodeSet.types.firstOrNull { it.id == typeId } ?: NodeType.none
            val childStart = pos - size
            val childEnd = pos - 4

            val childResults = if (childStart < childEnd) {
                buildNodes(childStart, childEnd, nodeFrom)
            } else {
                emptyList()
            }

            val children = childResults.map { it.second }
            val positions = childResults.map { it.first - nodeFrom }

            val tree = Tree(type, children, positions, nodeTo - nodeFrom)
            result.add(0, nodeFrom to tree)

            pos = childStart
        }
        return result
    }

    val topChildren = buildNodes(0, buffer.size, 0)
    return Tree(
        topType,
        topChildren.map { it.second },
        topChildren.map { it.first },
        if (topChildren.isEmpty()) 0 else topChildren.maxOf { it.first + it.second.length }
    )
}
